---
title: "공휴일 API와 안전한 XML 해석"
slug: "ledger-external-api-holiday-api"
category: "development"
publishedAt: "2026-10-08"
summary: "공공데이터포털 특일정보 API로 연도별 공휴일을 가져오는 HolidayApiClient를 읽으며 Java HttpClient의 시간 제한, API 키를 로그에 남기지 않는 방법, XXE 공격을 막는 XML 해석 설정을 설명합니다."
series: "ledger"
chapter: "external-api"
seriesOrder: 1
---
## 이 글에서 할 일

급여일과 결제일 보정에는 공휴일 목록이 필요합니다. 설날과 추석, 대체공휴일은 해마다 날짜가 바뀌므로 직접 입력하기 번거롭습니다. Ledger는 **공공데이터포털의 한국천문연구원 특일정보 API**로 연도별 공휴일을 가져옵니다. 설정 화면의 "가져오기" 버튼을 누르면 실행됩니다.

외부 API를 부를 때는 세 가지를 신경 써야 합니다.

1. 상대 서버가 느리거나 응답하지 않을 때
2. API 키가 새지 않게
3. 받은 데이터를 믿지 않기

## API 개요

`ledger/holiday/HolidayApiClient.java`

```java
// 공공데이터포털 한국천문연구원 특일정보 getRestDeInfo(공휴일) 호출.
// 명세: https://www.data.go.kr/data/15012690/openapi.do (2026-09-26 확인: solMonth 선택, 응답 XML)
// 키는 서버에서만 쓰고 로그·응답에 남기지 않는다.
@Component
public class HolidayApiClient {
    private static final String URL = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";
```

명세를 확인한 날짜를 주석에 남겼습니다. 외부 API는 바뀔 수 있으므로, 나중에 동작이 이상하면 "언제 기준의 명세로 만들었는지"부터 확인할 수 있습니다.

| 요청 값 | 뜻 |
|---|---|
| `ServiceKey` | 포털에서 받은 인증키 |
| `solYear` | 연도 |
| `numOfRows=100` | 한 해 공휴일을 한 번에 받을 만큼 |

응답은 **XML**입니다.

## 1. 시간 제한

```java
private static final long TOTAL_TIMEOUT_SECONDS = 15;

private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
```

```java
HttpRequest request = HttpRequest.newBuilder(URI.create(URL + "?ServiceKey=" + serviceKey() + ...))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();
// request.timeout 은 응답 헤더까지만 적용되므로 본문 수신까지 포함한 전체 시간을 따로 제한한다
CompletableFuture<HttpResponse<String>> future =
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
HttpResponse<String> response;
try {
    response = future.get(TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    future.cancel(true);
    logger.warn("공휴일 API 응답 시간 초과 (year={})", year);
    return null;
}
```

| 시간 제한 | 막는 것 |
|---|---|
| `connectTimeout(5초)` | 상대 서버에 연결이 안 될 때 |
| `request.timeout(10초)` | 응답 **헤더**가 늦게 올 때 |
| `future.get(15초)` | 헤더는 왔는데 **본문**을 아주 천천히 보낼 때 |

주석처럼 `request.timeout`은 응답 헤더까지만 적용됩니다. 상대가 본문을 한 글자씩 천천히 보내면 요청이 끝나지 않고 Tomcat 스레드를 붙잡습니다. 비동기로 보내고(`sendAsync`) 전체 시간을 따로 제한합니다(커밋 "공휴일 API 전체 시간 제한").

`followRedirects(NEVER)`: 다른 주소로 이동시키는 응답을 따라가지 않습니다. 예상하지 못한 곳으로 키가 담긴 요청이 가는 것을 막습니다.

## 2. API 키가 새지 않게

API 키는 요청 주소(`?ServiceKey=...`)에 들어갑니다. 그런데 Java의 많은 예외 메시지에는 **요청 주소가 그대로** 들어 있습니다. 예외를 그대로 로그에 남기면 키가 로그 파일에 기록됩니다.

```java
} catch (ExecutionException e) {
    // 원인 메시지에 요청 URL(키 포함)이 들어갈 수 있어 종류만 남긴다
    logger.warn("공휴일 API 호출 실패 (year={}): {}", year, e.getCause().getClass().getSimpleName());
    return null;
}
...
} catch (Exception e) {
    // 그 밖의 예외 메시지에는 요청 URL(키 포함)이 들어갈 수 있어 종류만 남긴다
    logger.warn("공휴일 API 호출 실패 (year={}): {}", year, e.getClass().getSimpleName());
    return null;
}
```

예외 메시지 대신 **예외 종류의 이름**(`ConnectException` 등)만 남깁니다. 원인을 짐작하기에는 충분하고 키는 남지 않습니다. 키는 화면 응답에도 넣지 않고, 키가 설정되어 있는지 여부만 알려 줍니다(`isConfigured`). 키가 없으면 화면의 가져오기 버튼이 비활성화됩니다.

### 두 가지 형태의 키

```java
// 포털은 "Encoding"(이미 URL 인코딩됨)·"Decoding" 두 형태의 키를 준다. % 가 있으면 인코딩된 키로 본다
private String serviceKey() {
    String key = apiKey.trim();
    return key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
}
```

공공데이터포털은 같은 키를 이미 URL 인코딩된 것과 아닌 것 두 가지로 보여 줍니다. 어느 쪽을 설정에 넣어도 동작하게 했습니다. 인코딩된 키를 한 번 더 인코딩하면 인증에 실패합니다.

## 3. 받은 데이터를 믿지 않기: XXE

`ledger/holiday/HolidayXmlParser.java`

```java
// 공공데이터포털 특일정보(getRestDeInfo) XML 응답 → 공휴일 목록(isHoliday=Y 만).
// 외부에서 받은 XML 이므로 DTD·외부 엔티티를 막고(XXE 방지) 해석한다. 실패는 IllegalStateException.
private static Document read(String xml) {
    try {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        ...
```

### XXE란

XML에는 **외부 엔티티**라는 기능이 있어서, 문서 안에서 다른 파일이나 주소의 내용을 끌어올 수 있습니다.

```xml
<!DOCTYPE x [ <!ENTITY secret SYSTEM "file:///etc/passwd"> ]>
<response>&secret;</response>
```

Java의 기본 XML 파서는 이것을 그대로 처리합니다. 만약 응답이 조작되면, 파서가 **서버의 파일을 읽어** 결과에 넣거나 내부 주소로 요청을 보낼 수 있습니다. 이것이 **XXE(XML External Entity)** 공격입니다. 공공기관 API라도 받은 데이터는 믿지 않는 것이 원칙입니다.

| 설정 | 막는 것 |
|---|---|
| `disallow-doctype-decl` | `<!DOCTYPE>` 선언 자체를 거부. 가장 확실한 차단 |
| `FEATURE_SECURE_PROCESSING` | 처리 한도와 보안 제한 켜기 |
| `ACCESS_EXTERNAL_DTD`, `ACCESS_EXTERNAL_SCHEMA` = `""` | 외부 DTD, 스키마 접근 금지 |
| `setXIncludeAware(false)`, `setExpandEntityReferences(false)` | 다른 문서 포함, 엔티티 펼치기 끄기 |

이 API 응답은 DOCTYPE을 쓰지 않으므로, 막아도 정상 응답에는 영향이 없습니다.

### 응답 검사

```java
Element root = doc.getDocumentElement();
if (!"response".equals(root.getTagName())) {
    // 키 미등록 등 포털 게이트웨이 오류(OpenAPI_ServiceResponse)
    throw new IllegalStateException("공휴일 API 오류 응답: " + text(root, "returnAuthMsg"));
}
String resultCode = text(root, "resultCode");
if (!"00".equals(resultCode)) {
    throw new IllegalStateException("공휴일 API 결과 코드: " + resultCode);
}
```

공공데이터포털은 키가 잘못되었을 때 HTTP 200으로 **다른 모양의 XML**을 돌려줍니다. 상태 코드만 보면 성공으로 착각합니다. 맨 바깥 태그와 결과 코드까지 확인합니다. `isHoliday=Y`인 항목만 공휴일로 씁니다(특일 중에는 공휴일이 아닌 기념일도 있음).

## 저장

가져온 공휴일은 `holiday` 테이블에 넣되, 같은 날짜가 이미 있으면 이름만 갱신합니다(`upsertHoliday`). 여러 번 가져와도 중복되지 않습니다. 첫해(2026년) 공휴일은 `db/002_seed_holiday_2026.sql`로 미리 넣어 두었고, 설날과 추석과 대체공휴일은 API로 확인해 보정하도록 주석에 남겼습니다.

## 테스트

`HolidayXmlParserTest`가 정상 응답, 오류 응답, `isHoliday=N` 걸러내기, DOCTYPE 거부를 검사합니다. API를 실제로 부르지 않고 XML 문자열만으로 테스트할 수 있도록, 호출(`HolidayApiClient`)과 해석(`HolidayXmlParser`)을 나눴습니다.

## 정리

- 연결, 헤더, 본문 전체에 각각 시간 제한을 걸어 느린 외부 서버가 앱을 붙잡지 않게 합니다.
- 예외 메시지에 요청 주소(키)가 들어갈 수 있어, 로그에는 예외 종류만 남깁니다.
- 외부 XML은 DOCTYPE과 외부 엔티티를 막아 XXE를 차단하고, 결과 코드까지 확인합니다.
- 호출과 해석을 나눠, 해석은 문자열만으로 테스트합니다.
