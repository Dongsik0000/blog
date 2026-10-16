---
title: "컨트롤러 작성법과 공통 응답 형식"
slug: "ledger-spring-controller"
category: "development"
publishedAt: "2026-10-08"
summary: "화면 메서드와 Ajax 메서드로 나뉜 Controller의 모양, 요청 값을 꺼내 검사하는 ParamUtil, 모든 Ajax 응답을 같은 모양으로 만드는 Response와 결과 코드를 설명합니다."
series: "ledger"
chapter: "spring"
seriesOrder: 5
---
## 이 글에서 할 일

Ledger의 Controller 9개는 모두 같은 모양으로 작성했습니다. 하나를 읽으면 나머지도 읽을 수 있게 하려는 것입니다. 이 글에서는 그 모양을 `SettingsApiController`로 설명합니다.

## 한 Controller = 화면 하나 + Ajax 여러 개

```java
@Controller
@RequestMapping("/ledger/settings")
public class SettingsApiController {

    @GetMapping
    public String settings() {
        return "settings/settingsMain";               // 화면: JSP 이름
    }

    @ResponseBody
    @PostMapping("/load")
    public Response load(HttpSession session) { ... }  // Ajax: JSON

    @ResponseBody
    @PostMapping("/cycle/save")
    public Response saveCycle(@RequestBody HashMap<String, Object> param, HttpSession session) { ... }
}
```

| 종류 | 방식 | 반환 |
|---|---|---|
| 화면 | `GET /ledger/settings` | JSP 이름. HTML 틀만 그림 |
| 데이터 조회와 저장 | `POST /ledger/settings/xxx` | `Response` (JSON) |

화면은 데이터 없이 빈 틀만 보내고, 화면이 뜨면 JS가 `load`를 호출해 데이터를 받아 그립니다. 조회도 POST로 통일했습니다. 모든 데이터 요청이 [POST + JSON 검사 인터셉터](/posts/ledger-spring-interceptor)를 거치게 하려는 것입니다. 예외는 CSV 파일 다운로드 하나로, 브라우저가 파일로 받아야 해서 GET입니다.

## 메서드 하나의 순서

`saveCycle`을 보면 모든 Ajax 메서드가 같은 순서를 따릅니다.

```java
@ResponseBody
@PostMapping("/cycle/save")
public Response saveCycle(@RequestBody HashMap<String, Object> param, HttpSession session) {
    // 1. 값 꺼내기
    Integer payDay = ParamUtil.integer(param, "payDay");
    Boolean adjust = ParamUtil.bool(param, "payDayAdjust");
    // 2. 검사: 틀리면 바로 입력 오류 응답
    if (payDay == null || payDay < 1 || payDay > 31 || adjust == null) {
        return Response.invalid("주기 시작일은 1~31일 중에서 골라 주세요.");
    }
    // 3. 실행: 사용자 id는 세션에서
    settingsService.updateUserSetting(ParamUtil.map(
            "userId", SessionUtil.getUserId(session), "payDay", payDay, "payDayAdjust", adjust));
    // 4. 응답
    return Response.of(Constants.SUCCESS);
}
```

### 1. 값 꺼내기: ParamUtil

`@RequestBody HashMap<String, Object> param`에는 화면이 보낸 JSON이 그대로 들어 있습니다. 값의 타입은 보낸 쪽에 따라 숫자일 수도 문자열일 수도 있어서, `ParamUtil`이 원하는 타입으로 바꿔 줍니다.

| 메서드 | 결과 | 형식이 틀리면 |
|---|---|---|
| `str(param, key)` | 앞뒤 공백을 뺀 문자열 | `""` |
| `raw(param, key)` | 공백을 유지한 문자열 (비밀번호) | `""` |
| `lng`, `integer` | 숫자. 금액의 쉼표 허용 | `null` |
| `date` | `yyyy-MM-dd` 날짜. 2월 30일 같은 없는 날짜도 거름 | `null` |
| `month` | `yyyy-MM` | `null` |
| `bool` | `true`, `false` | `null` |

**형식이 틀리면 예외 대신** `null`을 돌려주는 것이 핵심입니다. Controller는 `null`인지만 보고 "입력 오류" 응답을 보내면 되므로, 사용자가 이상한 값을 보내도 500 오류가 나지 않습니다.

### 2. 검사

Controller가 모든 입력을 검사합니다. 길이 제한은 DB 컬럼 크기와 맞춰 상수로 둡니다.

```java
private static final int NAME_MAX = 50;    // category.name, payment_method.name VARCHAR(50)
```

DB에 넣기 전에 걸러야, 너무 긴 값이 DB 오류(500)가 되는 대신 친절한 안내가 됩니다.

### 3. 실행: 필요한 값만 담아 넘기기

```java
settingsService.updateUserSetting(ParamUtil.map(
        "userId", SessionUtil.getUserId(session), "payDay", payDay, "payDayAdjust", adjust));
```

요청 본문 `param`을 DAO에 그대로 넘기지 않고, `ParamUtil.map`으로 **검사한 값만** 새로 담습니다. 특히 `userId`는 항상 **세션**에서 꺼냅니다. 요청 본문에 `userId`를 끼워 넣어 다른 사람의 데이터를 바꾸려는 시도를 막는 장치입니다([사용자 조건](/posts/ledger-mybatis-user-scope) 참고).

### 4. 응답: Response

## 공통 응답 형식: Response

`ledger/cmmn/util/Response.java`

```java
// Ajax 공통 응답 포맷 (@ResponseBody 로 그대로 반환)
public class Response {

    private final String code;
    private final String message;
    private final Object data;

    public static Response of(String code) { ... }
    public static Response of(String code, Object data) { ... }
    public static Response of(String code, String message, Object data) { ... }

    // 입력 오류(90). message 는 화면에 그대로 보인다
    public static Response invalid(String message) {
        return new Response(Constants.INVALID, message, null);
    }
}
```

모든 Ajax 응답이 `{code, message, data}` 세 칸입니다. 화면 JS는 `code`만 보고 성공과 실패를 나눕니다.

`new Response(...)` 대신 `Response.of(...)`, `Response.invalid(...)` 같은 **정적 팩토리 메서드**를 씁니다. 이름만 보고 어떤 응답인지 알 수 있고, 생성자는 `private`이라 정해진 방법으로만 만들 수 있습니다.

### 결과 코드

`ledger/cmmn/util/Constants.java` (일부)

| 코드 | 뜻 |
|---|---|
| `00` | 성공 |
| `01` | 로그인 실패 (계정 없음과 비밀번호 오류를 구분하지 않음) |
| `90` | 입력 오류. `message`를 화면에 그대로 보여 줌 |
| `91` | 대상 없음 (다른 사용자 것도 "없음"으로) |
| `92` | 이름 중복 |
| `95` | 사용 중이라 삭제 불가 |
| `99` | 그 밖의 실패 |

HTTP 상태 코드(200, 400)가 아니라 본문의 `code`로 결과를 나눕니다. 입력 오류도 HTTP 200으로 보내고 `code: "90"`으로 알립니다. 화면 JS는 `App.CODE`에 같은 값을 두고 비교합니다([공통 Ajax 함수](/posts/ledger-view-ajax)).

`91`이 "다른 사용자 것"과 "정말 없는 것"을 구분하지 않는 이유는, 구분하면 "이 id의 데이터가 다른 누군가에게 있다"는 정보가 새기 때문입니다.

## 정리

- Controller는 화면 GET 하나와 데이터 POST 여러 개로 이루어집니다.
- Ajax 메서드는 **꺼내기 → 검사 → 실행 → 응답** 순서입니다.
- `ParamUtil`은 형식이 틀리면 `null`을 돌려주고, Controller가 입력 오류로 응답합니다.
- `userId`는 항상 세션에서 꺼내고, DAO에는 검사한 값만 담아 넘깁니다.
- 모든 Ajax 응답은 `Response{code, message, data}`이고, 결과는 `code`로 나눕니다.
