---
title: "공통 예외 처리와 오류 화면"
slug: "ledger-spring-exception-handling"
category: "development"
publishedAt: "2026-10-08"
summary: "Controller 밖으로 나온 예외를 한곳에서 받는 @ControllerAdvice, 예외 종류별 HTTP 상태 결정, Ajax와 화면 요청에 맞춘 응답, Tomcat 기본 오류 화면을 숨기는 방법을 설명합니다."
series: "ledger"
chapter: "spring"
seriesOrder: 9
---
## 이 글에서 할 일

예외(오류)가 나면 사용자는 무엇을 보게 될까요? 아무 처리를 하지 않으면 Tomcat의 기본 오류 화면이 뜨고, 거기에는 Java 스택 트레이스와 Tomcat 버전이 찍혀 있습니다. 이 글에서는 Ledger가 예외를 어떻게 한곳에서 처리하는지 봅니다.

## 원칙: Controller는 try/catch를 쓰지 않는다

```java
// 컨트롤러는 try/catch 를 쓰지 않는다 — 예외가 메서드 밖으로 나가야 @Transactional 이 롤백한다.
```

Controller에서 예외를 잡아 정상 응답을 돌려주면 두 가지 문제가 생깁니다.

1. `@Transactional`은 메서드 밖으로 예외가 나가야 롤백합니다. 안에서 잡아 버리면 **절반만 저장된 채로 확정**될 수 있습니다([트랜잭션](/posts/ledger-spring-transaction)).
2. 메서드마다 같은 try/catch가 반복됩니다.

그래서 예외는 그대로 던지고, 처리는 한 곳에 모읍니다.

### 하나의 예외

거래 저장과 삭제(`EntryApiController`)에는 try/catch가 있습니다.

```java
try {
    if (id == null) {
        entryService.insertEntry(entry);
    } else if (entryService.updateEntry(entry) == 0) {
        return Response.of(Constants.NOT_FOUND);
    }
} catch (IllegalStateException e) {
    return Response.invalid("자산 잔액을 반영할 수 없어요. 연결된 자산과 금액을 확인해 주세요.");
}
```

이 Controller 메서드에는 `@Transactional`이 없고, 트랜잭션은 `EntryServiceImpl.insertEntry` 자신이 가집니다([트랜잭션](/posts/ledger-spring-transaction)). 예외가 Service 밖으로 나오는 순간 그 트랜잭션은 **이미 롤백**된 상태입니다. 그래서 Controller가 잡아도 반쪽 저장이 남지 않고, "적금 자산 잔액이 범위를 벗어남"처럼 **사용자가 고칠 수 있는 이유**를 친절한 문장으로 알려 줄 수 있습니다. 규칙의 목적(롤백 보장)을 지키는 범위 안의 예외입니다.

## @ControllerAdvice: 모든 Controller의 예외를 한곳에서

`ledger/cmmn/controller/CmmnExceptionHandler.java`

```java
// 컨트롤러 밖으로 나온 예외를 한곳에서 처리한다. Ajax 는 {"code":"99"} JSON, 화면은 error.jsp.
@ControllerAdvice
public class CmmnExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(CmmnExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ModelAndView handle(Exception e, HttpServletRequest request, HttpServletResponse response) {
        int status = statusOf(e);
        if (status >= 500) {
            logger.error("요청 처리 오류: {} {}", request.getMethod(), request.getRequestURI(), e);
        } else {
            logger.warn("잘못된 요청: {} {} ({})", request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
        }
        response.setStatus(status);

        if (RequestUtil.isAjax(request)) {
            ModelAndView mav = new ModelAndView("jsonView");
            mav.addObject("code", Constants.FAIL);
            mav.addObject("message", status >= 500 ? "처리 중 오류가 발생했습니다." : "잘못된 요청입니다.");
            return mav;
        }
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status); // error.jsp 가 상태 코드를 표시
        return new ModelAndView("error");
    }
}
```

| 부분 | 뜻 |
|---|---|
| `@ControllerAdvice` | 모든 Controller에 공통으로 적용되는 클래스 |
| `@ExceptionHandler(Exception.class)` | Controller에서 나온 모든 예외를 이 메서드가 받음 |
| 로그 수준 구분 | 500 이상은 서버 문제라 `error`와 스택 트레이스, 그 밖은 사용자 요청 문제라 `warn`과 예외 이름만 |
| Ajax면 `jsonView` | `{"code":"99","message":"..."}` JSON. 화면 JS가 공통 오류 창을 띄움 |
| 화면이면 `error` | `/WEB-INF/jsp/ledger/error.jsp` |

사용자에게는 "처리 중 오류가 발생했습니다"처럼 일반적인 문장만 보여 주고, 자세한 원인은 서버 로그에만 남깁니다. 오류 메시지에 SQL이나 클래스 이름이 노출되면 공격자에게 내부 구조를 알려 주는 셈이기 때문입니다.

## 예외 종류별 상태 코드

```java
// Spring MVC 표준 예외(경로 없음 404, 메서드 불일치 405 …)는 그 상태, 읽을 수 없는 본문은 400, 나머지 500
private int statusOf(Exception e) {
    if (e instanceof ErrorResponse errorResponse) {
        return errorResponse.getStatusCode().value();
    }
    if (e instanceof HttpMessageNotReadableException) {
        return HttpStatus.BAD_REQUEST.value();
    }
    return HttpStatus.INTERNAL_SERVER_ERROR.value();
}
```

| 예외 | 상태 | 예 |
|---|---|---|
| `ErrorResponse`를 구현한 Spring 예외 | 예외가 가진 상태 | 없는 주소(404), GET만 받는 곳에 POST(405) |
| `HttpMessageNotReadableException` | 400 | 요청 본문이 깨진 JSON |
| 그 밖의 모든 예외 | 500 | 코드 버그, DB 오류 |

모든 예외를 500으로 처리하면 "사용자가 잘못 보낸 요청"까지 서버 오류로 기록되어, 진짜 서버 문제를 찾기 어려워집니다.

## web.xml의 오류 화면

Controller까지 오지 않은 오류(예: Tomcat이 직접 내는 오류)는 `@ControllerAdvice`가 받지 못합니다. 이것은 `web.xml`의 `error-page`가 처리합니다.

```xml
<error-page>
    <error-code>404</error-code>
    <location>/WEB-INF/jsp/ledger/error.jsp</location>
</error-page>
<!-- 그 밖의 오류(400, 403, 405 …)도 Tomcat 기본 화면(서버 버전 노출) 대신 같은 화면 -->
<error-page>
    <location>/WEB-INF/jsp/ledger/error.jsp</location>
</error-page>
```

두 경로 모두 같은 `error.jsp`를 보여 주므로, 사용자는 어디서 난 오류든 같은 안내 화면을 봅니다.

### error.jsp의 함정

`error.jsp` 맨 위에는 이런 주석이 있습니다.

```jsp
<%-- isErrorPage 를 쓰지 않는다: 켜면 Spring 이 남긴 예외 속성 때문에 JSP 가 상태를 500 으로 덮어써 404/400 이 500 이 된다 --%>
```

JSP의 `isErrorPage="true"`는 오류 화면용 JSP라는 표시인데, 이것을 켰더니 404가 500으로 바뀌는 문제가 있었습니다. 원인과 해결 과정은 [겪은 문제와 해결](/posts/ledger-troubleshooting-error-page-500)에서 다룹니다.

## 정리

- Controller는 try/catch 없이 예외를 던지고, `@ControllerAdvice`의 `CmmnExceptionHandler`가 모두 받습니다.
- Ajax는 `{"code":"99"}` JSON, 화면은 `error.jsp`로 응답합니다.
- Spring 표준 예외는 그 상태(404, 405), 깨진 본문은 400, 나머지는 500으로 나눠 로그 수준도 다르게 남깁니다.
- 사용자에게는 일반적인 문장만, 자세한 원인은 로그에만 남깁니다.
