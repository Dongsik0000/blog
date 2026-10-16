---
title: "결제일의 주말과 공휴일 보정"
slug: "ledger-domain-business-day"
category: "development"
publishedAt: "2026-10-08"
summary: "고정 항목 결제일이 쉬는 날일 때 그대로, 직전 평일, 다음 평일 중 고르는 dueDate 계산과, 보정으로 결제일이 앞뒤 주기로 넘어가는 경우를 놓치지 않게 주기 안의 결제일을 찾는 방법을 설명합니다."
series: "ledger"
chapter: "domain"
seriesOrder: 3
---
## 이 글에서 할 일

월세는 매달 5일, 카드값은 매달 14일처럼 고정 항목에는 결제일이 있습니다. 그런데 결제일이 주말이나 공휴일이면 실제로 돈이 나가는 날은 카드사나 은행마다 다릅니다. Ledger는 항목마다 세 가지 중에서 고르게 했습니다.

| 설정 | 뜻 | 예 (10/31 토요일) |
|---|---|---|
| `NONE` | 그대로 | 10/31 |
| `PREV_BIZ` | 직전 평일 | 10/30 (금) |
| `NEXT_BIZ` | 다음 평일 | 11/2 (월) |

## dueDate

`ledger/cycle/PayCycle.java`

```java
// 고정 항목 결제일. adjust: NONE 그대로, PREV_BIZ 직전 평일, NEXT_BIZ 다음 평일
public static LocalDate dueDate(YearMonth month, int day, String adjust, Set<LocalDate> holidays) {
    LocalDate d = month.atDay(Math.min(day, month.lengthOfMonth()));
    if ("PREV_BIZ".equals(adjust)) {
        return previousBusinessDay(d, holidays);
    }
    if ("NEXT_BIZ".equals(adjust)) {
        while (!isBusinessDay(d, holidays)) {
            d = d.plusDays(1);
        }
    }
    return d;
}
```

[급여일 계산](/posts/ledger-domain-pay-cycle)과 같은 도구(`previousBusinessDay`, `isBusinessDay`)를 씁니다. 31일 같은 날짜는 짧은 달에 말일로 바뀌는 것도 같습니다.

DB에서도 세 값만 허용합니다.

```sql
adjust VARCHAR(10) NOT NULL DEFAULT 'NONE' CHECK (adjust IN ('NONE', 'PREV_BIZ', 'NEXT_BIZ')),
```

## 결제일과 "몇 월분"을 함께 다루기

보정 때문에 **결제일의 달**과 **몇 월분인지**가 다를 수 있습니다.

- 11월 1일이 결제일인 항목, `PREV_BIZ`, 11/1이 일요일 → 실제 결제일 **10/30**. 하지만 이것은 **11월분**입니다.
- 10월 31일이 결제일인 항목, `NEXT_BIZ`, 10/31이 토요일 → 실제 결제일 **11/2**. 하지만 이것은 **10월분**입니다.

그래서 결제 한 번을 "몇 월분 + 실제 날짜" 짝으로 다룹니다.

```java
// 고정 항목 결제 한 번: 어느 달 분(month)이 실제로 언제(date) 결제되는지
public record Due(YearMonth month, LocalDate date) {
}
```

"이번 달 것을 이미 기록했나?"는 **몇 월분**(`month`)으로 확인하고, 거래 날짜는 **실제 날짜**(`date`)로 넣습니다. 둘을 섞으면 11월분을 10월에 기록한 뒤 11월에 또 기록하는 중복이 생깁니다.

## 주기 안의 결제일 찾기

대시보드의 "아직 나가지 않은 고정 지출"은 이번 주기 안에 결제일이 있는 항목을 찾아야 합니다.

```java
// 주기 안에 드는 결제일들. 보정으로 앞뒤 달로 넘어오는 경우를 위해 시작 월 전달부터 끝 월 다음 달까지 본다
public static List<Due> duesInCycle(Cycle cycle, int day, String adjust, Set<LocalDate> holidays) {
    List<Due> dues = new ArrayList<>();
    YearMonth last = YearMonth.from(cycle.end()).plusMonths(1);
    for (YearMonth m = YearMonth.from(cycle.start()).minusMonths(1); !m.isAfter(last); m = m.plusMonths(1)) {
        LocalDate date = dueDate(m, day, adjust, holidays);
        if (!date.isBefore(cycle.start()) && !date.isAfter(cycle.end())) {
            dues.add(new Due(m, date));
        }
    }
    return dues;
}
```

주기의 달만 보면 놓치는 경우가 있습니다. 주기가 9/25 ~ 10/24일 때, `NEXT_BIZ`로 9월분이 10월로 밀려 들어오거나, `PREV_BIZ`로 11월분이 10월로 당겨져 들어올 수 있습니다. 그래서 **시작 달의 전달부터 끝 달의 다음 달까지** 후보를 만들어 보고, 실제 날짜가 주기 안에 드는 것만 고릅니다. 후보를 넉넉히 보고 걸러내는 방식이 경우를 하나하나 따지는 것보다 빠뜨릴 위험이 적습니다.

`PayCycleTest`에는 "다음 평일 보정으로 결제일이 다음 주기로 넘어가는 경우"가 테스트로 있습니다.

```java
@Test
void nextBusinessDayCanFallIntoNextCycle() { ... }
```

## 실제로 놓쳤던 경우

처음 구현에서는 자동 기록이 "지난달과 이번 달" 결제일만 확인했습니다. 그래서 `PREV_BIZ`로 **다음 달분이 이번 달로 당겨진** 결제가 기록되지 않았습니다. 리뷰에서 발견해 후보에 다음 달을 추가했습니다.

```java
// 지난달·이번 달·다음 달분. 다음 달분은 PREV_BIZ 보정으로 이번 달로 당겨질 수 있다(예: 11/1 일요일 → 10/30)
private static List<PayCycle.Due> candidates(int day, String adjust, LocalDate today, Set<LocalDate> holidays) {
    YearMonth thisMonth = YearMonth.from(today);
    ...
    for (YearMonth m : List.of(thisMonth.minusMonths(1), thisMonth, thisMonth.plusMonths(1))) { ... }
}
```

자세한 이야기는 [겪은 문제와 해결](/posts/ledger-troubleshooting-prev-biz-bug)에 있습니다.

## 정리

- 고정 항목 결제일은 그대로, 직전 평일, 다음 평일 중에서 고르고 DB CHECK로도 값을 제한합니다.
- 보정 때문에 결제일의 달과 몇 월분이 다를 수 있어, `Due(month, date)` 짝으로 다룹니다.
- 주기 안의 결제일은 앞뒤 달까지 후보를 넉넉히 만들고 실제 날짜로 걸러 찾습니다.
