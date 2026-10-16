---
title: "통장과 이체 (기본 통장 잔액 계산)"
slug: "ledger-domain-accounts"
category: "development"
publishedAt: "2026-10-08"
summary: "통장별 잔액을 기준 잔액과 그 뒤의 거래로 계산하는 방법, 기본 통장 잔액을 가계부 잔액에서 다른 통장을 빼서 구해 합계가 항상 맞게 한 설계, 이체가 수입과 지출이 아닌 이유, 통장 설계를 한 번 바꾼 과정을 설명합니다."
series: "ledger"
chapter: "domain"
seriesOrder: 7
---
## 이 글에서 할 일

월급 통장, 생활비 통장, 비상금 통장처럼 돈은 여러 통장에 나뉘어 있습니다. Ledger는 거래마다 **어느 통장**에서 나갔는지 고를 수 있고, 통장별 잔액과 통장 사이 이체를 보여 줍니다. 통장 기능에는 "잔액을 어떻게 맞출까"라는 설계 문제가 있었습니다.

## 통장 잔액 = 기준 잔액 + 그 뒤의 기록

```sql
-- 통장(체크카드 연결)별 잔액과 통장 간 이체. 잔액은 저장하지 않고 기준 잔액 + 기준일 이후 기록으로 매번 계산한다.
CREATE TABLE bank_account (
    ...
    base_balance BIGINT    NOT NULL ...,
    base_date    DATE      NOT NULL,
    base_at      TIMESTAMP NOT NULL DEFAULT now(),
    ...
);
```

통장을 등록할 때 은행 앱에서 본 잔액을 **기준 잔액**으로 한 번 적습니다. 그 뒤로는 [이월](/posts/ledger-domain-opening-balance)처럼 저장하지 않고 계산합니다.

```text
통장 잔액 = 기준 잔액
         + 기준 뒤 이 통장의 거래 (수입 − 지출)
         + 이 통장으로 받은 이체
         − 이 통장에서 보낸 이체
```

### "기준 뒤"를 정확히 정하기: base_at

은행 앱 잔액을 오후 3시에 보고 적었다고 합시다. 오늘 오전에 쓴 커피값은 이미 은행 잔액에 반영되어 있습니다. 오늘 날짜 거래를 모두 빼면 커피값이 **두 번** 빠집니다.

```sql
COMMENT ON COLUMN bank_account.base_at IS '기준 잔액을 적은 시각. 기준 잔액·기준일을 바꿀 때만 갱신한다(은행 앱 잔액에 이미 들어간 오늘 거래를 두 번 빼지 않게)';
```

그래서 기준일의 거래는 **기준 잔액을 적은 시각(`base_at`) 이후에 기록한 것만** 더합니다.

`account.xml`

```xml
<sql id="afterBase">
    AND (${date} &gt; a.base_date OR (${date} = a.base_date AND ${alias}.created_at &gt; a.base_at))
    AND ${date} &lt;= #{to}
</sql>
```

| 거래 | 잔액에 반영? |
|---|---|
| 기준일 다음 날 이후 | 반영 |
| 기준일, 기준 잔액을 적기 **전에** 기록 | 반영 안 함 (은행 잔액에 이미 있음) |
| 기준일, 기준 잔액을 적은 **뒤에** 기록 | 반영 |

`${date}`, `${alias}`는 XML 안에서 정한 고정 값이라 안전합니다([파라미터](/posts/ledger-mybatis-params-results)).

## 기본 통장: 잔액을 적지 않는 통장

통장이 셋이면 셋의 잔액을 더한 값이 가계부 잔액과 같아야 자연스럽습니다. 그런데 통장마다 기준 잔액을 따로 적으면, 입력을 하나 빠뜨리거나 날짜가 어긋나 **합계가 가계부 잔액과 달라지기 쉽습니다.**

해결책은 통장 하나(보통 월급 통장)를 **기본 통장**으로 정하고, 그 잔액은 적지 않고 계산하는 것입니다.

```sql
-- 기본 통장은 잔액을 적지 않고 "가계부 잔액 − 다른 통장 잔액"으로 계산해, 통장 합계가 항상 가계부 잔액과 같다.
-- 통장을 고르지 않은 거래(account_id NULL)는 기본 통장에 들어간다.
```

```text
기본 통장 잔액 = 가계부 잔액 − (다른 통장들의 잔액 합)
통장 합계     = 기본 통장 + 다른 통장들 = 가계부 잔액   (항상)
```

| 규칙 | 구현 |
|---|---|
| 기본 통장은 사용자당 하나 | 부분 유니크 인덱스 `ON bank_account (user_id) WHERE is_default` |
| 통장을 고르지 않은 거래는 기본 통장 | `account_id`가 `NULL`이면 기본 통장으로 봄 |
| 기본 통장 잔액은 화면에서 계산 | 가계부 잔액(대시보드 응답)과 통장 목록 두 응답이 다 와야 그림 |

`account.js`

```js
/* 기본 통장 잔액은 대시보드의 가계부 잔액에서 구하므로 두 응답이 모두 와야 그린다 */
...
/* 기본 통장 = 가계부 잔액 − 다른 통장 잔액. 그래서 통장 합계가 가계부 잔액과 같다 */
```

"합계가 맞는지 검사"하는 대신, **맞을 수밖에 없는 구조**로 만든 것입니다.

## 이체는 수입도 지출도 아니다

```sql
-- 이체는 수입·지출이 아니므로 ledger_entry 에 넣지 않는다(가계부 잔액·통계에 영향 없음).
CREATE TABLE account_transfer (
    ...
    from_account_id BIGINT NOT NULL REFERENCES bank_account(id),
    to_account_id   BIGINT NOT NULL REFERENCES bank_account(id),
    amount          BIGINT NOT NULL CHECK (amount > 0),
    CHECK (from_account_id <> to_account_id)
);
```

월급 통장에서 생활비 통장으로 50만 원을 옮겨도 내 돈은 그대로입니다. 이것을 거래(`ledger_entry`)로 넣으면 지출 50만 원, 수입 50만 원이 생겨 통계가 부풀려집니다. 그래서 이체는 **별도 테이블**에 두고, 통장 잔액 계산에만 씁니다.

월급날 생활비 통장으로 옮기는 정기 이체도 있습니다. 구조는 [고정 항목](/posts/ledger-domain-recurring)과 같습니다(`recurring_transfer` + 월별 처리 기록 `recurring_transfer_run`, 같은 결제일 보정).

```java
// 이체일 계산·처리 기록 방식은 고정 항목(RecurringGenerator)과 같다: 지난달·이번 달 이체일, 처리 기록을 먼저 넣어 중복 방지.
@Component
public class TransferGenerator { ... }
```

## 설계를 한 번 바꾼 이야기

처음(`008_bank_account.sql`)에는 통장을 **결제수단에 연결**했습니다. "체크카드 A로 결제한 지출은 통장 A에서 빠진다"는 방식입니다. 그런데 결제수단(체크카드, 계좌이체)과 돈이 빠지는 통장은 1:1이 아닌 경우가 많았습니다. 계좌이체는 어느 통장에서든 할 수 있으니까요.

그래서 `009_default_account.sql`에서 통장을 결제수단과 떼어 **거래에서 통장을 직접 고르게** 바꾸고, 기본 통장 개념을 넣었습니다.

```sql
-- 통장은 결제수단과 떼어 거래·고정 항목이 직접 고른다(결제수단은 체크카드·계좌이체 같은 결제 방식으로 남는다).
ALTER TABLE bank_account
    DROP CONSTRAINT IF EXISTS bank_account_default_bank,
    DROP COLUMN IF EXISTS payment_method_id,
    DROP COLUMN IF EXISTS kind,
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;
```

이 파일을 여러 상태의 DB에서 안전하게 실행하게 만든 방법은 [스키마 변경 파일](/posts/ledger-database-migrations)에서 다뤘습니다.

## 정리

- 통장 잔액은 기준 잔액에 그 뒤의 거래와 이체를 더해 계산하고, 기준일 당일은 `base_at` 이후 기록만 더합니다.
- 기본 통장 잔액은 "가계부 잔액 − 다른 통장"으로 계산해 통장 합계가 언제나 가계부 잔액과 같습니다.
- 이체는 수입과 지출이 아니므로 거래 테이블과 분리해 통계를 부풀리지 않습니다.
- 통장을 결제수단에 묶던 첫 설계를, 거래에서 통장을 직접 고르는 방식으로 바꿨습니다.
