---
title: "날짜와 금액 계산의 경계 사례 읽기"
slug: "ledger-test-pure-functions"
category: "development"
publishedAt: "2026-10-08"
summary: "기존 주기·할부·자산 증감 테스트를 읽으며, 월말과 윤년, 회차별 나머지, 거래 수정 시 차액을 어떻게 확인하는지 설명합니다."
series: "ledger"
chapter: "test"
seriesOrder: 2
---
## 이 글에서 할 일

가계부 계산은 평소에 맞더라도 달이 짧거나 거래를 수정하는 순간 어긋날 수 있습니다. Ledger의 기존 테스트는 이런 **경계 사례**, 즉 규칙이 바뀌는 지점의 입력을 함께 확인합니다.

날짜와 금액 계산은 입력을 받아 결과를 돌려주는 클래스에 두었습니다. 테스트에서는 웹 서버나 DB를 띄우지 않고 메서드에 입력을 넣습니다.

## 월말과 윤년

`src/test/java/ledger/cycle/PayCycleTest.java` (도우미와 월말 테스트)

```java
private static final Set<LocalDate> NONE = Set.of();

private static LocalDate d(String s) {
    return LocalDate.parse(s);
}

@Test
void payDateClampsToMonthEnd() {
    assertEquals(d("2026-02-28"), PayCycle.payDate(YearMonth.of(2026, 2), 31, false, NONE));
    assertEquals(d("2028-02-29"), PayCycle.payDate(YearMonth.of(2028, 2), 31, false, NONE));
    assertEquals(d("2026-04-30"), PayCycle.payDate(YearMonth.of(2026, 4), 31, false, NONE));
}
```

기존 클래스에서 관련 부분만 발췌했습니다. `assertEquals`의 첫 인자는 기대값이며, 둘째 인자는 실제 계산 결과입니다. 시작일을 31일로 정해도 그 날짜가 없는 달에는 말일을 반환하는지 확인합니다.

평년 2월, 윤년 2월, 30일까지 있는 달을 각각 확인하므로, 한 달의 결과만 맞는 계산을 걸러낼 수 있습니다. 다른 테스트는 휴일 보정으로 급여일이 전달로 이동한 경우와 주기 시작일 전날·당일도 확인합니다.

## 할부의 합계가 총액과 같은가

`src/test/java/ledger/recurring/InstallmentTest.java` (나머지 금액 테스트)

```java
@Test
void amountOfRemainderGoesToFirstRound() {
    assertEquals(33_334, Installment.amountOf(100_000, 3, 1));
    assertEquals(33_333, Installment.amountOf(100_000, 3, 2));
    assertEquals(33_333, Installment.amountOf(100_000, 3, 3));
    assertEquals(33_333, Installment.baseAmount(100_000, 3));
}
```

10만 원을 세 번으로 나누면 원 단위 나머지가 생깁니다. Ledger는 첫 회차에 나머지를 붙여 `33,334 + 33,333 + 33,333 = 100,000`을 유지합니다. 테스트도 이 규칙의 결과를 회차별로 확인합니다.

다른 테스트는 11월에 시작한 세 번의 결제가 다음 해 1월까지 이어지는지, 할부 기간 밖에서는 금액을 반환하지 않는지 확인합니다. 구현은 [할부 글](/posts/ledger-domain-installment)에서 설명합니다.

## 자산 이체 거래 수정은 차액만 반영

`src/test/java/ledger/entry/TransferAdjustmentTest.java` (원본 클래스)

```java
package ledger.entry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferAdjustmentTest {
    @Test
    void creditsNewDepositAndReversesDeletedDeposit() {
        assertEquals(List.of(new TransferAdjustment.Delta(3, 500_000)),
                TransferAdjustment.between(null, 0, 3L, 500_000));
        assertEquals(List.of(new TransferAdjustment.Delta(3, -500_000)),
                TransferAdjustment.between(3L, 500_000, null, 0));
    }

    @Test
    void editingAmountOnlyChangesDifference() {
        assertEquals(List.of(new TransferAdjustment.Delta(3, 100_000)),
                TransferAdjustment.between(3L, 500_000, 3L, 600_000));
        assertEquals(List.of(), TransferAdjustment.between(3L, 500_000, 3L, 500_000));
    }

    @Test
    void movingDepositBetweenAssetsReversesOldAndCreditsNew() {
        assertEquals(List.of(new TransferAdjustment.Delta(3, -500_000), new TransferAdjustment.Delta(4, 600_000)),
                TransferAdjustment.between(3L, 500_000, 4L, 600_000));
    }
}
```

50만 원 거래를 60만 원으로 고치면 연결 자산에는 10만 원만 더합니다. 이체 대상을 바꾸면 기존 자산에서 옛 금액을 빼고 새 자산에 새 금액을 더합니다. 같은 입력으로 다시 저장하면 증감 목록이 비어야 합니다.

이 테스트는 증감 계산 결과를 확인합니다. 계산한 증감을 DB 거래와 함께 적용하는 트랜잭션은 [트랜잭션 글](/posts/ledger-spring-transaction)의 호출 흐름으로 이어서 읽습니다.

## 실행과 확인

개발 PC의 Ledger 저장소 루트에서 실행합니다.

```bash
mvn test -Dtest=PayCycleTest,OpeningBalanceTest,InstallmentTest,TransferAdjustmentTest
```

실패와 오류가 0인지 확인합니다. 실패했다면 테스트의 입력 날짜와 기대값을 먼저 읽고, 해당 계산 함수의 분기를 따라갑니다.
