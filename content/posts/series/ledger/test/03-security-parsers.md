---
title: "시간 제어와 응답 해석 테스트"
slug: "ledger-test-security-parsers"
category: "development"
publishedAt: "2026-10-08"
summary: "로그인 제한을 기다리지 않고 검사하는 시계 제어와, 공휴일 XML·시세 JSON·CSV를 고정 입력으로 검사하는 기존 테스트를 설명합니다."
series: "ledger"
chapter: "test"
seriesOrder: 3
---
## 이 글에서 할 일

로그인 차단은 시간이 지나면 풀리고, 외부 API 응답은 정상·오류 형식이 다릅니다. Ledger는 테스트에서 시간을 직접 정하고 응답 문자열을 입력해 이 규칙들을 반복해서 확인합니다. 실제 API 키와 사용자의 거래 데이터는 테스트 입력으로 쓰지 않습니다.

## 시간을 기다리지 않고 바꾸기

`src/test/java/ledger/cmmn/util/LoginAttemptLimiterTest.java` (준비와 차단 만료 테스트)

```java
private final long[] now = {1_000_000L};
private LoginAttemptLimiter limiter;

@BeforeEach
void setUp() {
    limiter = new LoginAttemptLimiter(() -> now[0], 100);
}

private int acquire(LoginAttemptLimiter target, String key, int times) {
    int allowed = 0;
    for (int i = 0; i < times; i++) {
        if (target.tryAcquire(key)) {
            allowed++;
        }
    }
    return allowed;
}

@Test
void blockExpiresAfterFiveMinutes() {
    acquire(limiter, "a", 5);
    now[0] += LoginAttemptLimiter.BLOCK_MILLIS - 1;
    assertFalse(limiter.tryAcquire("a"));
    now[0] += 1;
    assertEquals(5, acquire(limiter, "a", 5));
    assertFalse(limiter.tryAcquire("a"));
}
```

기존 클래스의 관련 부분을 발췌했습니다. 운영 생성자는 실제 시계를 사용하지만, 테스트는 `now[0]`을 반환하는 함수를 넣습니다. 차단 종료 1밀리초 전에는 거절하고, 종료 시각에는 다시 다섯 번 허용하는지 확인합니다.

같은 클래스의 동시 요청 테스트는 여러 스레드를 같은 출발 신호로 실행하고 허용된 시도가 다섯 번인지 확인합니다. 작은 기록 상한을 넣는 테스트는 상한에 도달했을 때 새 키를 거절하는지 확인합니다.

## 외부 API는 문자열 응답으로 해석 검사

`src/test/java/ledger/holiday/HolidayXmlParserTest.java`는 XML 문자열을 직접 만들어 파서에 전달합니다. 실제 공공데이터포털에 요청하지 않고 다음을 확인합니다.

- 공휴일 표시가 `Y`인 항목만 결과에 포함합니다.
- 빈 목록은 빈 결과로 읽습니다.
- API 오류 코드와 포털 게이트웨이 오류 형식은 예외로 처리합니다.
- DOCTYPE이 들어 있는 XML과 잘못된 날짜 형식을 거절합니다.

`src/test/java/ledger/market/MarketDataClientTest.java`도 고정 JSON 입력을 사용합니다. 미국·USD 주식과 ETF만 남기고, 중복 종목과 형식이 맞지 않는 종목 코드를 제거하는지 확인합니다. 이 검사는 외부 통신 상태나 키의 이용권을 검사하는 테스트와는 별개입니다.

## CSV의 특수문자와 수식 접두 문자

`src/test/java/ledger/cmmn/util/CsvUtilTest.java` (CSV 필드 형식 테스트)

```java
@Test
void quotesWhenNeeded() {
    assertEquals("\"a,b\"", CsvUtil.cell("a,b"));
    assertEquals("\"say \"\"hi\"\"\"", CsvUtil.cell("say \"hi\""));
    assertEquals("\"line1\nline2\"", CsvUtil.cell("line1\nline2"));
}
```

쉼표가 있는 내용은 CSV의 다른 열로 갈라지지 않도록 따옴표로 감쌉니다. 내용 자체의 따옴표는 두 번 쓰고 줄바꿈도 한 필드 안에 보존합니다.

같은 클래스는 `=`, `+`, `-`, `@`, 탭으로 시작하는 값에 작은따옴표를 붙이는 결과도 확인합니다. 구현과 목적은 [CSV 수식 주입 방어 글](/posts/ledger-auth-csv-injection)에서 설명합니다.

## 실행과 확인

개발 PC의 Ledger 저장소 루트에서 실행합니다.

```bash
mvn test -Dtest=LoginAttemptLimiterTest,HolidayXmlParserTest,MarketDataClientTest,CsvUtilTest
```

보고서에서 실패와 오류가 0인지 확인합니다. 실제 서비스의 로그인과 API 조회는 운영 환경의 설정값을 포함한 별도 흐름으로 확인하고, 이 테스트 결과를 그 실행 결과로 대신하지 않습니다.
