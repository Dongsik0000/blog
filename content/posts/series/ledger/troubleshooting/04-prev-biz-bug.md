---
title: "다음 달 결제일이 이번 달로 당겨지는 경우"
slug: "ledger-troubleshooting-prev-biz-bug"
category: "development"
publishedAt: "2026-10-08"
summary: "직전 영업일 보정으로 다음 달 결제가 이번 달에 실행되어야 하는 사례와, 대상 월을 확장하고 회귀 테스트를 추가한 해결을 설명합니다."
series: "ledger"
chapter: "troubleshooting"
seriesOrder: 4
---
## 해결한 문제

고정 항목의 결제일을 직전 영업일로 보정하면 다음 달 결제가 이번 달로 당겨질 수 있습니다. 초기 자동 기록은 지난달과 이번 달분만 후보로 만들어 이런 결제를 놓쳤습니다. `2e66cf4` 커밋에서 다음 달분도 후보에 포함하고 이 사례의 테스트를 추가했습니다.

**회귀 테스트**는 해결한 문제가 다시 발생하지 않도록 같은 입력을 계속 확인하는 테스트입니다. 현재 저장소의 `RecurringGeneratorTest`에 이 사례가 들어 있습니다.

## 실제 날짜와 몇 월분인지는 다르다

2026년 11월 1일은 일요일입니다. 결제일이 매달 1일이고 직전 영업일 보정을 사용한다면 실제 결제일은 10월 30일이 됩니다.

10월 30일에 자동 기록할 때 이 결제는 **11월분**입니다. 실제 날짜가 10월이라고 해서 10월분으로 바꾸면 처리 기록의 월이 어긋납니다. Ledger의 `PayCycle.Due`는 대상 월과 실제 결제일을 함께 보관합니다.

## 현재 코드의 해결

`src/main/java/ledger/recurring/RecurringGenerator.java` (`candidates` 메서드)

```java
// 지난달·이번 달·다음 달분. 다음 달분은 PREV_BIZ 보정으로 이번 달로 당겨질 수 있다(예: 11/1 일요일 → 10/30)
private static List<PayCycle.Due> candidates(int day, String adjust, LocalDate today, Set<LocalDate> holidays) {
    YearMonth thisMonth = YearMonth.from(today);
    List<PayCycle.Due> dues = new ArrayList<>();
    for (YearMonth m : List.of(thisMonth.minusMonths(1), thisMonth, thisMonth.plusMonths(1))) {
        dues.add(new PayCycle.Due(m, PayCycle.dueDate(m, day, adjust, holidays)));
    }
    return dues;
}
```

다음 달까지 후보를 만들고, `duesUpTo`가 실제 결제일이 오늘 이하인 후보만 반환합니다. 따라서 다음 달의 미래 결제를 미리 모두 기록하는 것이 아니라, 보정으로 이미 결제일이 된 항목을 포함하는 방식입니다.

## 기존 테스트로 확인

`src/test/java/ledger/recurring/RecurringGeneratorTest.java` (해당 테스트)

```java
@Test
void prevBusinessDayFromNextMonthIsDueToday() {
    assertEquals(List.of(due(2026, 9, "2026-09-01"), due(2026, 10, "2026-10-01"), due(2026, 11, "2026-10-30")),
            RecurringGenerator.duesUpTo(1, "PREV_BIZ", LocalDate.parse("2026-10-30"), NONE));
}
```

기존 클래스에서 해당 메서드를 발췌했습니다. 기대하는 후보에 `11월분, 10월 30일`이 포함되는지 확인합니다. 다음 날 새 항목을 만들었을 때 이미 지난 11월분을 건너뛸 대상에 포함하는 테스트도 있습니다.

개발 PC의 Ledger 저장소 루트에서 실행합니다.

```bash
mvn test -Dtest=RecurringGeneratorTest
```

보고서에서 실패와 오류가 0인지 확인합니다. 전체 자동 기록의 중복 방지는 [고정 항목 글](/posts/ledger-domain-recurring)에서 이어서 읽습니다.

기록은 [해결 커밋](https://github.com/Dongsik0000/ledger/commit/2e66cf43cc7473c0ae30f3e27f6ccb16923cab67)과 현재 구현·테스트를 근거로 작성했습니다.
