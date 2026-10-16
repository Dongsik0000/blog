---
title: "오늘 쓸 수 있는 돈과 요약 통계"
slug: "ledger-domain-dashboard"
category: "development"
publishedAt: "2026-10-08"
summary: "대시보드의 이월, 주기 잔액, 아직 나가지 않은 고정 지출, 오늘 쓸 수 있는 돈 계산을 DashboardApiController로 따라가고, 요약 화면의 그룹 소계 표를 만드는 SummaryTables를 설명합니다."
series: "ledger"
chapter: "domain"
seriesOrder: 8
---
## 이 글에서 할 일

앞의 글들(주기, 이월, 결제일, 할부)이 모두 모이는 곳이 대시보드입니다. 특히 **오늘 쓸 수 있는 돈**은 이 가계부를 만든 이유 중 하나입니다. 이 숫자가 어떻게 계산되는지 따라갑니다.

## 계산식

```java
// 이월 + 주기 수입 − 주기 지출 = 주기 잔액, (주기 잔액 − 예정 고정지출) ÷ 남은 일수 = 오늘 쓸 수 있는 돈
```

```text
이월            = 이번 주기 시작 전까지의 잔액
주기 잔액        = 이월 + 이번 주기 수입 − 이번 주기 지출
예정 고정 지출   = 이번 주기에 아직 나가지 않은 고정 지출의 합
오늘 쓸 수 있는 돈 = (주기 잔액 − 예정 고정 지출) ÷ 남은 날 수
```

예를 들어 주기 잔액이 120만 원이고, 앞으로 월세 50만 원과 통신비 5만 원이 나갈 예정이며, 다음 월급까지 13일 남았다면:

```text
(1,200,000 − 550,000) ÷ 13 = 50,000원
```

"지금 통장에 120만 원 있다"가 아니라 "나갈 돈을 빼면 하루 5만 원씩 쓸 수 있다"를 보여 주는 것이 핵심입니다.

<!-- 📷 스크린샷: 대시보드의 오늘 쓸 수 있는 돈 -->

## 코드로 따라가기

`DashboardApiController.summary` (일부)

```java
LocalDate today = LocalDate.now(SEOUL);
PayCycle.Cycle cycle = PayCycle.cycleOf(today, payDay, adjust, holidays);       // ① 주기

OpeningBalance opening = OpeningBalance.of(setting);
long carryOver = opening.balanceAt(dashboardService.selectBalanceBefore(...));   // ② 이월
Map<String, Object> sum = dashboardService.selectPeriodSum(...);                 // ③ 주기 수입·지출
long balance = carryOver + income - expense;                                     // ④ 주기 잔액
long pending = pendingFixed(userId, cycle, today, holidays);                     // ⑤ 예정 고정 지출
long daysLeft = ChronoUnit.DAYS.between(today, cycle.end()) + 1;                 // ⑥ 남은 날

data.put("dailyBudget", Math.floorDiv(balance - pending, daysLeft));             // ⑦ 오늘 쓸 수 있는 돈
```

| 단계 | 글 |
|---|---|
| ① 주기 | [급여일 기준 주기](/posts/ledger-domain-pay-cycle) |
| ② 이월 | [이월과 시작 잔액](/posts/ledger-domain-opening-balance) |
| ⑤ 예정 고정 지출 | 아래 |

### 남은 날에 오늘을 포함

```java
long daysLeft = ChronoUnit.DAYS.between(today, cycle.end()) + 1;
```

`DAYS.between(오늘, 주기 끝)`은 오늘을 빼고 셉니다. 주기 마지막 날에는 0이 되어 0으로 나누는 오류가 납니다. `+ 1`로 오늘을 포함하면 마지막 날에도 1입니다.

### 음수일 때 내림: floorDiv

```java
Math.floorDiv(balance - pending, daysLeft)
```

예정 지출이 잔액보다 크면 결과가 음수입니다. Java의 `/`는 0쪽으로 자르므로 `-10 / 3 = -3`이지만, `floorDiv`는 아래로 내려 `-4`입니다. 쓸 수 있는 돈이 부족할 때 실제보다 덜 부족해 보이지 않게 **보수적으로** 내립니다.

## 예정 고정 지출

```java
// 예정 고정지출: 활성 지출 고정 항목의 이번 주기 결제일 중 오늘 이후(오늘 포함)이고 아직 처리(run)되지 않은 것의 합.
// 할부는 기간 안의 달만, 그 회차 금액으로
private long pendingFixed(long userId, PayCycle.Cycle cycle, LocalDate today, Set<LocalDate> holidays) {
    List<Map<String, Object>> items = dashboardService.selectActiveExpenseItems(userId);
    ...
    for (Map<String, Object> item : items) {
        List<PayCycle.Due> dues = PayCycle.duesInCycle(cycle, ...);                  // 이번 주기 안의 결제일
        dues.removeIf(d -> Installment.amountFor(item, d.month()) == null);         // 할부 기간 밖 제외
        ...
    }
    Set<String> runs = new HashSet<>(dashboardService.selectRunKeys(...));         // 이미 처리된 "항목|월"
    for (...) {
        if (!due.date().isBefore(today) && !runs.contains(id + "|" + due.month())) {
            pending += Installment.amountFor(item, due.month());
        }
    }
    return pending;
}
```

- **오늘 이후(오늘 포함)** 결제일만 셉니다. 지난 결제는 이미 거래로 기록되어 주기 지출에 들어 있기 때문입니다.
- **처리 기록이 있는 달은 뺍니다.** 오늘이 결제일이고 이미 자동 기록되었다면, 지출에도 들어가고 예정에도 들어가 두 번 빠지는 것을 막습니다.
- 처리 기록을 항목마다 조회하지 않고, 필요한 달을 모아 **한 번에** 조회합니다(`months`로 `IN` 조건). 항목이 20개면 쿼리 20번이 1번이 됩니다.

## 총 잔액

```java
long assetTotal = dashboardService.selectAssetTotal(userId);
data.put("totalBalance", balance + assetTotal);
```

가계부 잔액에 적금, 주식 같은 [자산](/posts/ledger-external-api-market-api) 합계를 더해 전체 재산을 보여 줍니다.

## 요약 화면의 표: SummaryTables

요약 화면에는 "카테고리 × 월" 지출 표가 있습니다. 같은 그룹(예: 식비 = 식비, 배달, 카페, 장보기)은 묶어서 **소계** 줄을 넣습니다.

`ledger/summary/SummaryTables.java`

```java
// 요약 화면의 월별 표(카테고리·결제수단 × 월)를 만드는 순수 계산. DB 결과(Map)를 받아 행 목록을 돌려준다.
public final class SummaryTables {

    // kind: item(항목) / subtotal(그룹 소계) / total(합계)
    public record Row(String label, List<Long> values, long total, String kind) {
    }

    // 같은 그룹은 첫 멤버 자리에 모으고, 표시되는 멤버가 2개 이상이면 소계 행. 비활성은 기록이 있을 때만 표시
    public static List<Row> categoryRows(List<Map<String, Object>> categories, List<Map<String, Object>> amounts, List<String> months) { ... }
}
```

| 규칙 | 이유 |
|---|---|
| 같은 그룹은 그 그룹의 첫 카테고리 자리에 모음 | 설정 화면에서 정한 순서를 지킴 |
| 멤버가 2개 이상일 때만 소계 | 멤버가 하나면 소계가 그 줄과 같아 의미 없음 |
| 숨긴(비활성) 카테고리는 기록이 있을 때만 | 지금은 안 쓰지만 과거에 쓴 금액은 통계에 보여야 함 |

SQL은 "카테고리 × 월 금액"만 집계하고, 그룹 묶기와 소계 같은 **표 모양 만들기**는 Java의 순수 함수가 합니다. SQL로 소계 줄까지 만들면 쿼리가 복잡해지고 테스트하기 어렵습니다. 이 클래스는 `SummaryTablesTest`로 검사합니다.

## 정리

- 오늘 쓸 수 있는 돈 = (주기 잔액 − 예정 고정 지출) ÷ 남은 날(오늘 포함)이고, 음수는 `floorDiv`로 보수적으로 내립니다.
- 예정 고정 지출은 이번 주기 안, 오늘 이후, 아직 처리되지 않은 결제만 더하고, 처리 기록은 한 번에 조회합니다.
- 요약 표의 그룹 묶기와 소계는 SQL이 아닌 순수 Java 함수(`SummaryTables`)로 만들어 테스트합니다.
