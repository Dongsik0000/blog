---
title: "이 프로젝트에 쓰인 Java 문법"
slug: "ledger-java-maven-java-syntax"
category: "development"
publishedAt: "2026-10-08"
summary: "record, LocalDate와 YearMonth, 패턴 매칭 instanceof, 정적 유틸리티 클래스, Map과 람다처럼 Ledger 코드에 실제로 나오는 Java 17 문법을 코드와 함께 설명합니다."
series: "ledger"
chapter: "java-maven"
seriesOrder: 2
---
## 이 글에서 할 일

Ledger 코드를 읽다 보면 자주 나오는 Java 문법을 실제 코드로 정리합니다. 처음 보는 문법이 나오면 이 글로 돌아와 찾아보면 됩니다.

## record: 값을 담는 작은 클래스

`record`는 Java 16부터 정식으로 들어온 문법입니다. **값을 담기만 하는 클래스**를 한 줄로 만듭니다.

`ledger/cycle/PayCycle.java`

```java
// 주기: start ~ end (양 끝 포함)
public record Cycle(LocalDate start, LocalDate end) {
}
```

이 한 줄로 다음이 자동으로 만들어집니다.

| 자동 생성 | 예 |
|---|---|
| 생성자 | `new Cycle(start, end)` |
| 값 꺼내기 (getter) | `cycle.start()`, `cycle.end()` (`getStart()`가 아님) |
| `equals`, `hashCode` | 값이 같으면 같은 객체로 비교 |
| `toString` | `Cycle[start=2026-09-25, end=2026-10-24]` |

record의 값은 **바꿀 수 없습니다**(불변). 주기 같은 계산 결과는 만들어진 뒤 바뀔 일이 없으므로 record가 잘 맞습니다. 메서드를 추가할 수도 있습니다.

`ledger/cycle/OpeningBalance.java` (일부)

```java
public record OpeningBalance(long amount, LocalDate date) {

    public long balanceAt(long entrySum) {
        return date == null ? entrySum : amount + entrySum;
    }
}
```

## LocalDate와 YearMonth: 날짜 계산

가계부는 날짜 계산이 핵심입니다. Java 8부터 들어온 `java.time` 패키지를 씁니다.

| 타입 | 담는 것 | 예 |
|---|---|---|
| `LocalDate` | 날짜 (시간 없음) | `2026-10-07` |
| `YearMonth` | 연월 | `2026-10` |
| `DayOfWeek` | 요일 | `SATURDAY` |

```java
YearMonth month = YearMonth.of(2026, 2);
month.lengthOfMonth();                        // 28 (그 달의 일 수)
month.atDay(Math.min(31, month.lengthOfMonth())); // 2026-02-28 (31일이 없으면 말일)
LocalDate d = LocalDate.of(2026, 10, 7);
d.plusDays(1); d.minusDays(1);                // 하루 뒤, 하루 전 (새 객체를 돌려줌)
d.isAfter(other); d.isBefore(other);          // 비교
d.getDayOfWeek();                             // 요일
```

`LocalDate`도 불변이라 `plusDays`는 원래 값을 바꾸지 않고 **새 날짜를 돌려줍니다.** 그래서 `d = d.plusDays(1);`처럼 다시 담아야 합니다. 급여일 계산에서 "31일이 없는 달은 말일로" 같은 처리는 `lengthOfMonth()`로 합니다([주기 계산](/posts/ledger-domain-pay-cycle) 참고).

## 패턴 매칭 instanceof

Java 16부터 `instanceof` 검사와 형 변환을 한 번에 할 수 있습니다.

`ledger/cycle/OpeningBalance.java`

```java
Object d = setting.get("openingDate");
LocalDate date = d == null ? null
        : d instanceof java.sql.Date sql ? sql.toLocalDate()
        : d instanceof LocalDate local ? local
        : LocalDate.parse(d.toString());
```

`d instanceof java.sql.Date sql`은 "d가 `java.sql.Date`이면 그것을 `sql`이라는 이름으로 쓰겠다"는 뜻입니다. 예전에는 `if (d instanceof java.sql.Date) { java.sql.Date sql = (java.sql.Date) d; ... }`처럼 두 번 써야 했습니다.

DB에서 꺼낸 값이 경로에 따라 `java.sql.Date`, `LocalDate`, 문자열 중 하나로 올 수 있어서, 어떤 형태든 `LocalDate`로 맞추는 코드입니다. `? :`(삼항 연산자)를 이어 붙여 "이면 이것, 아니면 다음 검사"를 차례로 표현했습니다.

## 정적 유틸리티 클래스

객체를 만들 필요 없이 기능만 모아 둔 클래스입니다.

`ledger/cmmn/util/ParamUtil.java` (일부)

```java
public class ParamUtil {

    private static final Pattern INTEGER = Pattern.compile("-?\\d{1,18}");

    private ParamUtil() {
    }

    public static Long lng(Map<String, Object> param, String key) {
        String s = str(param, key).replace(",", "");
        return INTEGER.matcher(s).matches() ? Long.valueOf(s) : null;
    }
}
```

| 부분 | 뜻 |
|---|---|
| `static` 메서드 | 객체 없이 `ParamUtil.lng(param, "amount")`로 바로 호출 |
| `private ParamUtil()` | 생성자를 숨겨 `new ParamUtil()`을 못 하게 함. 객체를 만들 이유가 없다는 표시 |
| `static final Pattern` | 정규식을 한 번만 컴파일해 재사용 (호출할 때마다 컴파일하면 느림) |

`PayCycle`은 여기에 `final class`까지 붙여 상속도 막았습니다.

## 래퍼 타입과 null

```java
Long id = ParamUtil.lng(param, "id");          // Long: null 가능
long userId = SessionUtil.getUserId(session);  // Long을 long에 담음 (자동 언박싱)
```

`long`, `int`, `boolean`은 **기본 타입**이라 null이 될 수 없고, `Long`, `Integer`, `Boolean`은 **래퍼 타입**이라 null이 될 수 있습니다. Ledger는 이 차이를 일부러 씁니다. 입력값 파서는 형식이 틀리면 `null`을 돌려주고, Controller가 `null`이면 "입력 오류"로 응답합니다.

`SessionUtil.getUserId`는 세션에 값이 없으면 `null`일 수 있는 `Long`을 돌려줍니다. 로그인 확인용 인터셉터는 이 값이 `null`인지 검사하고, Controller는 인터셉터를 통과한 뒤라 반드시 값이 있으므로 `long`에 바로 담습니다. 만약 `null`인 `Long`을 `long`에 넣으면 `NullPointerException`이 납니다(자동 언박싱). 인터셉터가 그 앞을 지켜 주는 구조입니다.

## Map, List, Set

```java
Map<String, Object> m = new HashMap<>();   // 이름 → 값
List<Long> ids = new ArrayList<>();         // 순서 있는 목록
Set<LocalDate> holidays = new HashSet<>();  // 중복 없는 모음, 포함 여부 확인이 빠름
Set<String> TYPES = Set.of("EXPENSE", "INCOME");  // 바꿀 수 없는 Set
```

- `<String, Object>`는 **제네릭**입니다. "키는 String, 값은 아무 객체"라는 타입 정보입니다.
- `new HashMap<>()`의 빈 `<>`는 왼쪽 타입에서 추론합니다(다이아몬드 연산자).
- 공휴일은 "이 날짜가 공휴일인가?"를 자주 묻기 때문에 `Set`에 담습니다.

## 람다

함수를 값처럼 넘기는 문법입니다. `파라미터 -> 결과` 형태입니다.

`ledger/recurring/controller/RecurringApiController.java`

```java
dues.removeIf(d -> Installment.amountFor(item, d.month()) == null);   // 할부 기간 밖
dues.forEach(d -> months.add(d.month().toString()));
```

- `removeIf(d -> 조건)`: 목록에서 조건이 참인 것을 지웁니다.
- `forEach(d -> 동작)`: 하나씩 꺼내 동작을 실행합니다.

`ledger/recurring/RecurringGenerator.java`

```java
List<String> months = dueDates.stream().map(d -> d.month().toString()).toList();
```

`stream()`은 목록을 흐름으로 바꿔 `map`(변환), `filter`(걸러내기) 같은 처리를 이어 붙이고 `toList()`로 다시 목록을 만듭니다.

## 정리

| 문법 | 이 프로젝트에서 |
|---|---|
| `record` | 주기, 결제일, 시작 잔액 같은 계산 결과 |
| `LocalDate`, `YearMonth` | 모든 날짜 계산 |
| 패턴 매칭 `instanceof` | 여러 형태로 오는 값을 한 타입으로 맞추기 |
| 정적 유틸리티 클래스 | 입력값 파서, 공통 함수 |
| 래퍼 타입의 null | "값 없음, 형식 오류"를 표현 |
| 람다, stream | 목록 걸러내기와 변환 |
