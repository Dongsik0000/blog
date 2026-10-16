---
title: "급여일 기준 주기 계산"
slug: "ledger-domain-pay-cycle"
category: "development"
publishedAt: "2026-10-07"
summary: "한 달을 1일이 아니라 급여일부터 세는 주기 계산을 PayCycle 클래스로 따라갑니다. 31일이 없는 달, 주말과 공휴일로 급여일이 앞당겨져 전달로 넘어가는 경우까지 어떻게 처리했는지 설명합니다."
series: "ledger"
chapter: "domain"
seriesOrder: 1
---
## 이 글에서 할 일

Ledger의 모든 숫자(이월, 이번 주기 잔액, 오늘 쓸 수 있는 돈, 주기별 통계)는 **주기**를 기준으로 계산합니다. 주기는 "급여일부터 다음 급여일 전날까지"입니다. 급여일이 25일이면 9월 25일부터 10월 24일까지가 한 주기입니다.

말로는 간단하지만, 실제 달력에는 예외가 많습니다. 이 글에서는 그 예외를 `PayCycle` 클래스가 어떻게 처리하는지 봅니다.

![설정 화면의 기록 주기와 시작 잔액. 주기 시작일 매월 1일, 주말·공휴일이면 직전 평일로 옮기는 스위치, 이번 주기 10월 1일부터 31일까지가 보인다](/uploads/capture-ledger-12-settings.jpg)

## 순수 계산 클래스

`ledger/cycle/PayCycle.java`

```java
// 주기(급여일 기준)와 고정 항목 결제일 계산. DB 없이 날짜만 다루는 순수 계산.
public final class PayCycle {

    // 주기: start ~ end (양 끝 포함)
    public record Cycle(LocalDate start, LocalDate end) {
    }
    ...
}
```

이 클래스는 DB도, Spring도 모릅니다. 날짜와 급여일, 공휴일 목록을 받아 결과만 돌려줍니다. 그래서 대시보드, 거래 내역, 요약, 고정 항목 어디서든 같은 계산을 쓰고, [테스트](/posts/ledger-test-pure-functions)도 쉽습니다.

## 1단계. 그 달의 급여일

```java
// 그 달의 급여일. 짧은 달은 말일로. adjust 면 토·일·공휴일인 동안 하루씩 앞으로(전달로 넘어갈 수 있다)
public static LocalDate payDate(YearMonth month, int payDay, boolean adjust, Set<LocalDate> holidays) {
    LocalDate d = month.atDay(Math.min(payDay, month.lengthOfMonth()));
    return adjust ? previousBusinessDay(d, holidays) : d;
}
```

### 예외 1. 31일이 없는 달

급여일을 31일로 정하면 2월에는 31일이 없습니다. `Math.min(payDay, month.lengthOfMonth())`로 **그 달의 말일**을 씁니다.

| 달 | 급여일 31일의 실제 날짜 |
|---|---|
| 2026년 2월 | 2월 28일 |
| 2028년 2월 (윤년) | 2월 29일 |
| 2026년 4월 | 4월 30일 |

### 예외 2. 급여일이 주말이나 공휴일

대부분의 회사는 급여일이 쉬는 날이면 **그 전 평일**에 줍니다. 설정에서 이 보정을 켜면(`adjust`) 쉬는 날인 동안 하루씩 앞으로 갑니다.

```java
private static LocalDate previousBusinessDay(LocalDate d, Set<LocalDate> holidays) {
    while (!isBusinessDay(d, holidays)) {
        d = d.minusDays(1);
    }
    return d;
}

private static boolean isBusinessDay(LocalDate d, Set<LocalDate> holidays) {
    DayOfWeek w = d.getDayOfWeek();
    return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && !holidays.contains(d);
}
```

| 상황 | 결과 |
|---|---|
| 2026-10-25(일)이 급여일 | 10-24(토)도 쉬는 날 → **10-23(금)** |
| 거기에 10-23이 공휴일이라면 | **10-22(목)** |

### 예외 3. 앞당겨져 전달로 넘어감

급여일이 1일이고 보정을 켜면, 2026-11-01(일)은 10-31(토)을 지나 **10-30**(금)이 됩니다. "11월 급여일"이 10월에 있는 것입니다. 이 경우가 다음 단계를 어렵게 만듭니다.

## 2단계. 오늘이 속한 주기

```java
// today 가 속한 주기. 급여일이 today 이하인 가장 늦은 기준 월부터, 다음 기준 월 급여일 전날까지.
// 보정으로 다음 달 급여일이 이번 달로 당겨질 수 있어 다음 달부터 확인한다.
public static Cycle cycleOf(LocalDate today, int payDay, boolean adjust, Set<LocalDate> holidays) {
    YearMonth base = YearMonth.from(today).plusMonths(1);
    while (payDate(base, payDay, adjust, holidays).isAfter(today)) {
        base = base.minusMonths(1);
    }
    LocalDate start = payDate(base, payDay, adjust, holidays);
    LocalDate end = payDate(base.plusMonths(1), payDay, adjust, holidays).minusDays(1);
    return new Cycle(start, end);
}
```

"오늘 이전의 가장 늦은 급여일"을 찾으면 그것이 주기 시작입니다. 그런데 예외 3 때문에 **다음 달의 급여일이 오늘보다 앞일 수 있습니다.** 그래서 이번 달이 아니라 **다음 달부터 거꾸로** 확인합니다.

10월 31일(토), 급여일 1일, 보정 켬으로 따라가 봅니다.

```text
base = 11월 → 11월 급여일 = 10/30 → 10/31보다 늦지 않음 → 멈춤
start = 10/30
end   = 12월 급여일(12/1, 화) 전날 = 11/30
주기  = 10/30 ~ 11/30
```

이번 달(10월)부터 확인했다면 10월 급여일(10/1)을 시작으로 잡아 **10/1 ~ 10/29**라는 틀린 주기가 나옵니다. 실제로 10/30에 월급이 들어왔으니 10/31은 새 주기여야 합니다.

| 오늘 | 급여일 | 보정 | 주기 |
|---|---|---|---|
| 2026-10-15 | 1일 | 끔 | 10/01 ~ 10/31 (달력 월과 같음) |
| 2026-10-15 | 25일 | 끔 | 09/25 ~ 10/24 |
| 2026-03-05 | 31일 | 끔 | 02/28 ~ 03/30 |
| 2026-10-23 | 25일 | 켬 | 10/23 ~ 11/24 (앞당겨진 시작일 포함) |
| 2026-10-31 | 1일 | 켬 | 10/30 ~ 11/30 (다음 달 급여일이 전달로) |

모두 `PayCycleTest`에 그대로 있는 사례입니다.

## 3단계. "몇 월 주기"인가

요약 화면은 주기를 "10월 주기"처럼 부릅니다. 10/30에 시작한 주기는 시작일은 10월이지만 **11월 급여**로 시작한 주기라 "11월 주기"입니다.

```java
// 주기의 기준 월(그 주기가 "몇 월 주기"인지). 보정으로 시작일이 전달로 당겨질 수 있어 다음 달도 확인한다
public static YearMonth baseMonth(Cycle cycle, int payDay, boolean adjust, Set<LocalDate> holidays) {
    YearMonth m = YearMonth.from(cycle.start());
    return payDate(m, payDay, adjust, holidays).equals(cycle.start()) ? m : m.plusMonths(1);
}
```

## 공휴일은 어디서 오나

`holidays`는 DB의 `holiday` 테이블에서 필요한 기간만 꺼낸 것입니다. 보정이 앞뒤 달로 넘어갈 수 있어서 넉넉히 꺼냅니다.

```java
// 주기·결제일 보정이 앞뒤 달로 넘어갈 수 있어 넉넉히 조회
Set<LocalDate> holidays = new HashSet<>(holidayService.selectHolidayDates(ParamUtil.map(
        "from", today.minusMonths(3).withDayOfMonth(1), "to", today.plusMonths(3))));
```

공휴일을 채우는 방법은 [공휴일 API](/posts/ledger-external-api-holiday-api)에서 다룹니다. 등록된 공휴일이 없는 해는 주말만 보정되므로, 거래 내역 화면은 그 사실을 알려 줍니다(`holidayMissing`).

## 정리

- 주기는 급여일부터 다음 급여일 전날까지이고, 계산은 DB와 분리된 `PayCycle`이 맡습니다.
- 31일이 없는 달은 말일, 쉬는 날은 직전 평일로 보정합니다.
- 보정으로 다음 달 급여일이 전달로 넘어올 수 있어, 오늘의 주기는 **다음 달부터 거꾸로** 찾습니다.
- 경계 사례는 모두 단위 테스트로 고정했습니다.
