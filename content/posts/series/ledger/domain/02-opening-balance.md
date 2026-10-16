---
title: "이월을 저장하지 않고 계산하기 (시작 잔액)"
slug: "ledger-domain-opening-balance"
category: "development"
publishedAt: "2026-10-08"
summary: "지난 주기에서 남은 돈(이월)을 저장하지 않고 매번 거래로 계산하는 방법과, 기록을 중간부터 시작한 사람을 위한 시작 잔액(OpeningBalance)이 이월과 통계에 어떻게 반영되는지 설명합니다."
series: "ledger"
chapter: "domain"
seriesOrder: 2
---
## 이 글에서 할 일

시트 가계부에서 가장 귀찮았던 일은 달이 바뀔 때 남은 돈을 다음 달 시트로 옮겨 적는 것이었습니다. 그리고 지난달 기록 하나를 고치면 그 뒤 모든 달의 이월을 다시 고쳐야 했습니다. Ledger는 **이월을 저장하지 않습니다.** 필요할 때마다 거래로 계산합니다.

## 이월 = 주기 시작 전까지의 모든 거래 합

`dashboard.xml`

```xml
<select id="selectBalanceBefore" parameterType="map" resultType="long">
    SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0)
    FROM ledger_entry
    WHERE user_id = #{userId} AND entry_date &lt; #{date}
    <if test="from != null">AND entry_date &gt;= #{from}</if>
</select>
```

주기 시작일(`date`) **전날까지**의 수입은 더하고 지출은 빼면 그것이 이월입니다. 지난달 거래를 고치면 다음 조회에서 이월도 저절로 바뀝니다. 저장된 값이 없으니 어긋날 일도 없습니다.

`COALESCE(..., 0)`는 거래가 하나도 없을 때 `SUM`이 `NULL`을 돌려주는 것을 0으로 바꿉니다.

## 문제: 기록을 중간부터 시작했다면

가계부를 오늘부터 쓰기 시작하면, 통장에는 이미 돈이 있는데 Ledger의 거래는 0건입니다. 이월이 0으로 계산되어 실제와 맞지 않습니다. 그렇다고 과거 거래를 모두 입력할 수는 없습니다.

그래서 **시작 잔액**을 둡니다. "이 날(기준일)이 시작될 때 잔액이 얼마였다"를 한 번 적습니다.

```sql
-- 시작 잔액: 기준일이 시작될 때의 잔액. 잔액(이월·주기 잔액·누적)은 기준일 이전 거래를 빼고 이 금액부터 센다.
ALTER TABLE app_user
    ADD COLUMN opening_balance BIGINT NOT NULL DEFAULT 0 ...,
    ADD COLUMN opening_date DATE;
```

## OpeningBalance

`ledger/cycle/OpeningBalance.java`

```java
// 시작 잔액: 기준일(date)이 시작될 때의 잔액(amount). 기준일 이전 거래는 이미 그 안에 반영된 것으로 보고
// 잔액(이월·주기 잔액·누적)을 셀 때 빼며, 수입·지출 통계에는 그대로 남는다. date 가 없으면 모든 거래로 잔액을 센다.
public record OpeningBalance(long amount, LocalDate date) {

    // 잔액에 넣는 거래의 시작일(이날 포함). null 이면 처음부터
    public LocalDate countFrom() {
        return date;
    }

    // x 가 시작될 때의 잔액. entrySum = [countFrom, x) 거래의 수입 − 지출
    public long balanceAt(long entrySum) {
        return date == null ? entrySum : amount + entrySum;
    }

    // 기간(start~) 수입·지출을 잔액에 더할 때의 시작일: 기준일이 기간 안이면 기준일부터
    public LocalDate periodFrom(LocalDate start) {
        return date != null && date.isAfter(start) ? date : start;
    }
}
```

핵심 규칙은 하나입니다.

> **기준일 이전 거래는 이미 시작 잔액 안에 들어 있다고 본다.**

그래서 잔액을 셀 때 기준일 이전 거래는 빼고, 시작 잔액부터 셉니다.

```text
이월 = 시작 잔액 + (기준일 ~ 주기 시작 전날)의 거래 합
```

## 대시보드에서 쓰기

`DashboardApiController.summary` (일부)

```java
PayCycle.Cycle cycle = PayCycle.cycleOf(today, payDay, adjust, holidays);
// 시작 잔액이 있으면 기준일 이전 거래를 빼고 그 금액부터 센다(기준일이 이번 주기 안이면 이월로 본다)
OpeningBalance opening = OpeningBalance.of(setting);
long carryOver = opening.balanceAt(dashboardService.selectBalanceBefore(
        ParamUtil.map("userId", userId, "date", cycle.start(), "from", opening.countFrom())));
Map<String, Object> sum = dashboardService.selectPeriodSum(
        ParamUtil.map("userId", userId, "from", opening.periodFrom(cycle.start()), "to", cycle.end()));
long balance = carryOver + income - expense;
```

| 줄 | 하는 일 |
|---|---|
| `selectBalanceBefore(..., "from", countFrom())` | 기준일부터 주기 시작 전날까지의 거래 합. 기준일이 없으면 처음부터 |
| `balanceAt(...)` | 그 합에 시작 잔액을 더함 |
| `periodFrom(cycle.start())` | 이번 주기 수입과 지출을 셀 시작일. 기준일이 주기 중간이면 **기준일부터** |

### 기준일이 주기 중간인 경우

주기가 9/25 ~ 10/24이고, 10/1에 가계부를 시작해 시작 잔액 100만 원을 적었다고 합시다.

```text
이월        = 100만 원 + (10/1 ~ 9/24 거래 = 없음) = 100만 원
주기 수입·지출 = 10/1 ~ 10/24 거래만 (periodFrom이 10/1을 돌려줌)
주기 잔액    = 100만 원 + 10월 수입 − 10월 지출
```

9/25 ~ 9/30에 입력한 거래가 있더라도, 그것은 이미 100만 원 안에 들어 있다고 보므로 두 번 빼지 않습니다.

## 통계는 그대로

시작 잔액은 **잔액**에만 영향을 줍니다. 요약 화면의 월별 수입과 지출 통계에는 기준일 이전 거래도 그대로 나옵니다. 9월에 얼마 썼는지는 여전히 궁금한 정보이기 때문입니다. 요약 쿼리는 잔액에 더할 값만 따로 계산합니다.

```xml
<!-- 잔액에 더할 순액: 시작 잔액 기준일(openingDate) 이전 거래는 뺀다 -->
COALESCE(SUM(CASE WHEN <if test="openingDate != null">entry_date &gt;= #{openingDate} AND</if> TRUE
                  THEN CASE WHEN type = 'INCOME' THEN amount ELSE -amount END END), 0) AS "balanceNet"
```

## 정리

- 이월은 저장하지 않고 "주기 시작 전까지의 거래 합"으로 매번 계산합니다. 과거를 고쳐도 어긋나지 않습니다.
- 중간부터 쓰기 시작한 사람은 시작 잔액과 기준일을 적고, 기준일 이전 거래는 그 안에 들어 있다고 봅니다.
- `OpeningBalance` record가 "어디부터 세고, 무엇을 더할지"를 한곳에서 정합니다.
- 시작 잔액은 잔액에만 쓰고, 수입과 지출 통계에는 모든 거래를 남깁니다.
