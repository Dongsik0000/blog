---
title: "고정 항목 자동 기록과 중복 방지"
slug: "ledger-domain-recurring"
category: "development"
publishedAt: "2026-10-08"
summary: "월세나 월급 같은 고정 항목을 결제일에 거래로 자동 기록하는 RecurringGenerator를 읽으며, 처리 기록(recurring_run)을 먼저 넣어 같은 달이 두 번 기록되지 않게 하는 방법과 지난 결제일 건너뛰기, 거래를 지우면 그 달을 건너뛰는 설계를 설명합니다."
series: "ledger"
chapter: "domain"
seriesOrder: 4
---
## 이 글에서 할 일

고정 항목을 등록하면 결제일에 거래가 자동으로 생깁니다. 이 기능에서 가장 중요한 것은 **같은 달 거래가 두 번 생기지 않는 것**입니다. 서버가 하루에 여러 번 확인하고, 앱이 재시작될 때마다 다시 확인하기 때문입니다.

## 언제 기록하나

| 시점 | 호출하는 곳 |
|---|---|
| 매일 00:05 | `RecurringScheduler` ([스케줄러](/posts/ledger-domain-scheduler)) |
| 앱이 시작될 때 | 같은 스케줄러. 꺼져 있던 동안의 결제일을 따라잡기 |
| 고정 항목을 저장한 직후 | `RecurringApiController`. 오늘이 결제일이면 바로 기록 |

세 곳 모두 `RecurringGenerator`를 부릅니다. 그래서 같은 결제를 여러 번 처리하려 해도 결과가 한 번만 나와야 합니다.

## 처리 기록 테이블

```sql
-- 고정 항목의 월별 생성 기록. (recurring_id, period_ym) 유일 → 스케줄러가 여러 번 돌아도 중복 생성 안 됨.
-- 생성된 거래를 지워도 이 행은 남겨서 같은 달에 다시 만들지 않는다 (= 이번 달 건너뛰기).
CREATE TABLE recurring_run (
    recurring_id BIGINT    NOT NULL REFERENCES recurring_item(id) ON DELETE CASCADE,
    period_ym    CHAR(7)   NOT NULL,                       -- 'YYYY-MM'
    entry_id     BIGINT    REFERENCES ledger_entry(id) ON DELETE SET NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (recurring_id, period_ym)
);
```

"항목 3번의 2026-10월분을 처리했다"를 한 줄로 남깁니다. 기본 키가 `(항목, 월)`이라 같은 줄을 두 번 넣을 수 없습니다.

| `entry_id` | 뜻 |
|---|---|
| 거래 id | 기록함 |
| `NULL` | 건너뜀 (기록하지 않기로 함, 또는 기록한 거래를 사용자가 지움) |

## 한 건 처리하기: 기록부터 넣는다

`ledger/recurring/RecurringGenerator.java`

```java
// 한 트랜잭션: 처리 기록 → 거래 → 기록에 거래 연결. 기록이 이미 있으면(처리됨·건너뜀) 아무것도 하지 않는다
private boolean generateOne(Map<String, Object> item, PayCycle.Due due, long amount,
                            MarketDataClient.FxQuote fx) {
    String periodYm = due.month().toString();
    if (recurringService.insertRun(ParamUtil.map(
            "recurringId", item.get("id"), "periodYm", periodYm, "entryId", null, ...)) == 0) {
        return false;
    }
    Map<String, Object> entry = ParamUtil.map("userId", item.get("userId"), "entryDate", due.date(), ...);
    entryService.insertEntry(entry);
    recurringService.updateRunEntry(ParamUtil.map("recurringId", item.get("id"), "periodYm", periodYm, "entryId", entry.get("id")));
    return true;
}
```

```xml
INSERT INTO recurring_run (recurring_id, period_ym, entry_id, fx_rate, fx_as_of)
VALUES (...)
ON CONFLICT (recurring_id, period_ym) DO NOTHING
```

순서가 핵심입니다.

```text
1. 처리 기록을 먼저 넣는다 (ON CONFLICT DO NOTHING)
   └ 넣은 줄 수가 0 → 이미 누가 처리함 → 그만
2. 거래를 넣는다
3. 처리 기록에 거래 id를 적는다
(1~3은 한 트랜잭션. 중간에 실패하면 모두 취소)
```

"이미 처리했나?"를 SELECT로 먼저 확인하는 방식은 두 요청이 동시에 확인하면 둘 다 "아직"을 보고 둘 다 기록합니다. **넣기 자체를 확인으로** 쓰면 DB의 기본 키가 둘 중 하나만 성공시키므로 동시에 돌아도 안전합니다.

트랜잭션은 결제 한 건마다 `TransactionTemplate`으로 겁니다([트랜잭션](/posts/ledger-spring-transaction)).

## 어느 결제일을 처리하나

```java
// 지난달·이번 달 결제일 중 today 이하(생성 대상)
public static List<PayCycle.Due> duesUpTo(int day, String adjust, LocalDate today, Set<LocalDate> holidays) { ... }
```

오늘까지 결제일이 온 것 중 **지난달, 이번 달, 다음 달분**을 후보로 봅니다(다음 달분은 [보정으로 당겨진](/posts/ledger-domain-business-day) 경우). 서버가 며칠 꺼져 있었어도 다시 켜질 때 지난 결제일을 채웁니다. 처리 기록이 있는 달은 건너뜁니다.

## 지난 결제일은 건너뛰기

고정 항목을 오늘 새로 만들었는데 결제일(5일)이 이미 지났다면, 이번 달 5일분을 지금 기록해야 할까요? 사용자는 그 거래를 이미 손으로 넣었을 가능성이 큽니다. 그래서 **만들기 전의 지난 결제일은 "건너뜀"으로 표시**합니다.

```java
// 신규·결제일 변경·재활성화 직후: 이미 지난 결제일은 건너뜀(entry_id 없는 기록)으로 둔다.
public void skipPassed(Map<String, Object> item, LocalDate today) {
    for (PayCycle.Due due : duesBefore(day(item), (String) item.get("adjust"), today, holidays(today))) {
        ...
        recurringService.insertRun(ParamUtil.map(
                "recurringId", item.get("id"), "periodYm", due.month().toString(), "entryId", null));
    }
}
```

`entry_id`가 `NULL`인 처리 기록을 넣어 두면, 이후 스케줄러는 그 달을 "처리됨"으로 보고 넘어갑니다. 오늘이 결제일이면 `duesBefore`(오늘보다 **앞선** 것만)에 들지 않으므로 오늘 기록됩니다.

## 거래를 지우면 그 달은 건너뛰기

자동 기록된 거래를 사용자가 지우면 어떻게 될까요?

```sql
entry_id BIGINT REFERENCES ledger_entry(id) ON DELETE SET NULL,
```

FK의 `ON DELETE SET NULL` 때문에 처리 기록은 남고 `entry_id`만 비워집니다. 스케줄러는 그 달을 다시 만들지 않습니다. "이번 달은 안 냈어"를 거래 삭제 한 번으로 표현할 수 있습니다. 별도의 "건너뛰기" 버튼이나 코드 없이 DB 제약으로 해결한 부분입니다.

## 한 항목의 실패가 다른 항목을 막지 않게

```java
// 스케줄러: 모든 사용자의 활성 항목. 한 항목이 실패해도 로그만 남기고 다음 항목을 계속한다
public int generateAll(LocalDate today) {
    ...
    for (Map<String, Object> item : recurringService.selectActiveItems()) {
        try {
            created += generate(item, today, holidays);
        } catch (RuntimeException e) {
            logger.error("고정 항목 자동 기록 실패: id={}", item.get("id"), e);
        }
    }
    return created;
}
```

달러 결제 항목이 환율 API 오류로 실패해도, 다른 사용자의 월세 기록은 계속 진행됩니다.

## 정리

- 고정 항목은 매일 00:05, 앱 시작, 저장 직후 세 곳에서 같은 `RecurringGenerator`로 기록합니다.
- `recurring_run`의 기본 키 `(항목, 월)`과 "기록 먼저 넣기"로 동시에 돌아도 한 달에 한 번만 기록합니다.
- 만들기 전에 지난 결제일은 건너뜀으로 표시하고, 기록된 거래를 지우면 FK `SET NULL`로 그 달을 건너뜁니다.
- 한 항목의 실패는 로그만 남기고 다음 항목을 계속합니다.
