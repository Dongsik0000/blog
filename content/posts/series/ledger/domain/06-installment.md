---
title: "카드 할부를 회차별로 기록하기"
slug: "ledger-domain-installment"
category: "development"
publishedAt: "2026-10-08"
summary: "총액, 개월 수, 첫 결제월만 넣으면 매달 \"노트북 (1/3)\"처럼 회차별 금액을 기록하는 Installment 계산, 나누어떨어지지 않는 금액의 처리, 마지막 회차 뒤 항목 정리를 설명합니다."
series: "ledger"
chapter: "domain"
seriesOrder: 6
---
## 이 글에서 할 일

90만 원짜리 노트북을 3개월 할부로 사면 매달 30만 원이 카드값에 붙습니다. 이것을 고정 항목으로 세 번 따로 넣기는 번거롭습니다. Ledger는 **총액, 개월 수, 첫 결제월**만 넣으면 매달 결제일에 회차별로 기록하고, 끝나면 항목을 정리합니다.

## 저장하는 값

```sql
ALTER TABLE recurring_item
    ADD COLUMN installment_total  BIGINT,
    ADD COLUMN installment_months SMALLINT,
    ADD COLUMN installment_start  CHAR(7),
    ADD CONSTRAINT recurring_item_installment_check CHECK (...);
```

| 컬럼 | 예 |
|---|---|
| `installment_total` | 1,000,000 |
| `installment_months` | 3 (2~60) |
| `installment_start` | `2026-10` |

일반 고정 항목은 세 컬럼이 모두 비어 있습니다. 셋이 함께 있거나 함께 없어야 한다는 규칙은 [CHECK 제약](/posts/ledger-database-constraints)이 지킵니다.

## Installment: 순수 계산

`ledger/recurring/Installment.java`

```java
// 할부 고정 항목 계산. 총액을 개월 수로 나눠 첫 결제월부터 매달 한 회차씩(무이자). 나머지(원 단위)는 1회차에.
public final class Installment {

    // 2~n회차 금액(목록·월 고정지출 표시용으로 recurring_item.amount 에 저장)
    public static long baseAmount(long total, int months) {
        return total / months;
    }

    public static long amountOf(long total, int months, int round) {
        long base = baseAmount(total, months);
        return round == 1 ? total - base * (months - 1) : base;
    }

    // period 가 몇 회차인지(첫 결제월 = 1). 기간 밖이면 0
    public static int roundOf(YearMonth start, int months, YearMonth period) {
        long round = start.until(period, ChronoUnit.MONTHS) + 1;
        return round >= 1 && round <= months ? (int) round : 0;
    }
}
```

### 나누어떨어지지 않는 금액

100만 원을 3개월로 나누면 333,333.33...원입니다. 원 단위에 소수는 없으므로 어딘가에 나머지를 붙여야 합니다.

```text
base       = 1,000,000 / 3 = 333,333 (정수 나눗셈, 소수 버림)
1회차      = 1,000,000 − 333,333 × 2 = 333,334
2, 3회차   = 333,333
합계       = 333,334 + 333,333 + 333,333 = 1,000,000 ✓
```

나머지를 **1회차에** 붙이면 합계가 언제나 총액과 정확히 같습니다. 회차마다 반올림하면 합계가 1~2원 어긋날 수 있습니다. 주석에 "나머지는 1회차에"라고 규칙을 남겼습니다.

### 몇 회차인가

```java
long round = start.until(period, ChronoUnit.MONTHS) + 1;
```

`YearMonth.until`은 두 달 사이의 개월 수를 셉니다. 첫 결제월 2026-10에서 2026-12까지는 2개월이므로 3회차입니다. 범위(1 ~ 개월 수) 밖이면 0을 돌려줘 "이 달은 할부 기간이 아님"을 나타냅니다.

### 그 달 결제 금액

```java
// 그 달 결제 금액. 일반 항목은 amount, 할부는 회차 금액, 할부 기간 밖이면 null
public static Long amountFor(Map<String, Object> item, YearMonth period) {
    if (!isInstallment(item)) {
        return ((Number) item.get("amount")).longValue();
    }
    int months = months(item);
    int round = roundOf(start(item), months, period);
    return round == 0 ? null : amountOf(total(item), months, round);
}
```

일반 항목과 할부 항목을 같은 함수로 다룹니다. 자동 기록, 대시보드의 예정 고정 지출, 다음 결제일 표시가 모두 이 함수 하나로 금액을 구합니다. 기간 밖이면 `null`을 돌려주고, 쓰는 쪽은 그 달을 건너뜁니다.

```java
Long amount = Installment.amountFor(item, due.month());
if (amount == null) {
    continue;   // 할부 기간 밖
}
```

## 거래 이름: "노트북 (1/3)"

`RecurringGenerator.title`

```java
// 할부 거래는 "이름 (1/3)". 거래 내용은 100자까지라 이름을 줄인다
static String title(Map<String, Object> item, YearMonth period) {
    String name = (String) item.get("name");
    if (!Installment.isInstallment(item)) {
        return name;
    }
    int months = Installment.months(item);
    String suffix = " (" + Installment.roundOf(Installment.start(item), months, period) + "/" + months + ")";
    return name.length() + suffix.length() > TITLE_MAX ? name.substring(0, TITLE_MAX - suffix.length()) + suffix : name + suffix;
}
```

항목 이름이 길어서 회차 표시를 붙이면 거래 내용 칸(100자)을 넘는 경우, 이름을 줄이고 회차 표시는 지킵니다. 회차 정보가 더 중요하기 때문입니다.

## 마지막 회차 뒤 정리

```java
// 할부는 마지막 회차 결제일이 지나면 항목을 지운다(기록된 거래는 남는다)
if (isFinished(item, today, holidays)) {
    recurringService.deleteItem(ParamUtil.map("id", item.get("id"), "userId", item.get("userId")));
}
```

다 갚은 할부가 고정 항목 목록에 계속 남아 있으면 헷갈립니다. 마지막 회차 결제일이 오면 항목을 지웁니다. 처리 기록(`recurring_run`)은 FK `ON DELETE CASCADE`로 함께 지워지고, 이미 기록된 거래는 고정 항목을 가리키지 않으므로 **그대로 남습니다**. 지출 기록은 사라지면 안 되기 때문입니다.

## 테스트

`InstallmentTest`가 나머지 배분, 회차 계산, 기간 밖 처리를 검사합니다. 금액 계산은 1원이라도 틀리면 안 되는 부분이라 테스트로 고정했습니다.

## 정리

- 할부는 총액, 개월 수, 첫 결제월 세 값으로 저장하고, 셋이 함께 있어야 한다는 규칙은 DB가 지킵니다.
- 나머지는 1회차에 붙여 합계가 항상 총액과 같게 합니다.
- `amountFor`가 일반 항목과 할부를 한 함수로 다루고, 기간 밖은 `null`로 알립니다.
- 마지막 회차가 지나면 항목을 지우되 기록된 거래는 남깁니다.
