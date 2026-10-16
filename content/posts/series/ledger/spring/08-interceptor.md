---
title: "인터셉터로 로그인 확인과 CSRF 막기"
slug: "ledger-spring-interceptor"
category: "development"
publishedAt: "2026-10-08"
summary: "Controller 앞에서 모든 요청을 검사하는 HandlerInterceptor 두 개, POST는 Ajax와 JSON만 받는 ApiRequestInterceptor와 로그인을 확인하는 LoginPageInterceptor를 한 줄씩 읽습니다."
series: "ledger"
chapter: "spring"
seriesOrder: 8
---
## 이 글에서 할 일

Controller 메서드마다 "로그인했나?"를 검사하면 빠뜨리기 쉽습니다. **인터셉터**는 Controller가 실행되기 **직전에** 정해진 주소의 요청을 모두 가로채 검사합니다. 한 곳에 검사를 두면 새 Controller를 만들어도 자동으로 보호됩니다.

## 등록

`dispatcher-servlet.xml`

```xml
<mvc:interceptors>
    <!-- POST 는 Ajax(JSON) 만: CSRF 차단 -->
    <mvc:interceptor>
        <mvc:mapping path="/auth/**"/>
        <mvc:mapping path="/ledger/**"/>
        <bean class="ledger.interceptor.ApiRequestInterceptor"/>
    </mvc:interceptor>
    <!-- 로그인 확인: /ledger/** 는 세션에 userId 가 있어야 접근 가능 -->
    <mvc:interceptor>
        <mvc:mapping path="/ledger/**"/>
        <bean class="ledger.interceptor.LoginPageInterceptor"/>
    </mvc:interceptor>
</mvc:interceptors>
```

| 주소 | 거치는 인터셉터 |
|---|---|
| `/login`, `/signup` (화면) | 없음 |
| `/auth/login`, `/auth/signup` (Ajax) | ApiRequest |
| `/ledger/**` (로그인 후 모든 것) | ApiRequest → LoginPage |
| `/resources/**` (CSS, JS) | 없음 |

주소 설계가 곧 보안 설계입니다. 로그인이 필요한 기능은 모두 `/ledger/` 아래에 두었기 때문에, 새 기능도 그 아래에 만들기만 하면 로그인 검사를 받습니다.

## HandlerInterceptor의 preHandle

인터셉터는 `HandlerInterceptor`를 구현하고 `preHandle`을 씁니다.

- `true`를 돌려주면 다음 인터셉터나 Controller로 진행
- `false`를 돌려주면 **여기서 끝**. 인터셉터가 직접 응답을 써야 함

## LoginPageInterceptor: 로그인 확인

`ledger/interceptor/LoginPageInterceptor.java`

```java
// /ledger/** 요청은 세션에 userId 가 있어야 통과. Ajax 는 JSON 으로, 화면 요청은 /login 으로 보낸다.
public class LoginPageInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (SessionUtil.getUserId(request.getSession()) != null) {
            return true;
        }

        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"sessionExpired\": true}");
        } else {
            response.sendRedirect(request.getContextPath() + "/login");
        }
        return false;
    }
}
```

1. 세션에 `userId`가 있으면 통과합니다. 로그인할 때 넣은 값입니다([세션 관리](/posts/ledger-auth-session)).
2. 없을 때 응답을 **요청 종류에 따라** 다르게 합니다.
   - **화면 요청**: `/p3/login`으로 이동(redirect)시킵니다.
   - **Ajax 요청**: redirect하면 JS가 로그인 화면 HTML을 데이터로 받게 되어 엉망이 됩니다. 그래서 `{"sessionExpired": true}`라는 JSON을 보내고, 화면의 공통 Ajax 함수가 이것을 보고 "세션이 만료되었습니다" 안내 후 로그인 화면으로 보냅니다.

Ajax인지는 요청 헤더 `X-Requested-With: XMLHttpRequest`로 구분합니다. 화면의 [공통 Ajax 함수](/posts/ledger-view-ajax)가 모든 요청에 이 헤더를 붙입니다.

## ApiRequestInterceptor: POST는 Ajax와 JSON만

`ledger/interceptor/ApiRequestInterceptor.java`

```java
// /auth/**, /ledger/** 의 POST 는 Ajax(JSON) 요청만 받는다 — 다른 사이트의 폼 전송(CSRF) 차단.
// 다른 출처에서 이 헤더나 JSON 본문을 보내려면 브라우저가 CORS 사전 요청을 하는데, 서버는 CORS 를 허용하지 않는다.
public class ApiRequestInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String contentType = request.getContentType();
        boolean json = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("application/json");
        if (RequestUtil.isAjax(request) && json) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"99\",\"message\":\"허용되지 않는 요청입니다.\"}");
        return false;
    }
}
```

### CSRF란

**CSRF**(Cross-Site Request Forgery, 사이트 간 요청 위조)는 사용자가 로그인한 상태에서 다른 사이트를 방문했을 때, 그 사이트가 몰래 우리 서버로 요청을 보내게 만드는 공격입니다. 브라우저는 요청에 우리 사이트의 쿠키(세션)를 자동으로 붙이므로, 서버는 사용자가 직접 보낸 요청으로 착각합니다.

```html
<!-- 공격자 사이트에 숨겨진 폼 -->
<form action="https://도메인/p3/ledger/entry/delete" method="POST">
  <input name="id" value="1">
</form>
<script>document.forms[0].submit()</script>
```

### 왜 "Ajax + JSON만"으로 막히나

- HTML 폼은 `Content-Type`을 `application/json`으로 보낼 수 없고, `X-Requested-With` 같은 사용자 정의 헤더도 붙일 수 없습니다.
- 다른 사이트의 JavaScript가 이런 헤더나 JSON 본문을 붙여 보내려면, 브라우저가 먼저 우리 서버에 "보내도 되나요?"라고 묻는 **CORS 사전 요청**(preflight)을 합니다. Ledger는 다른 출처를 허용하는 CORS 설정이 없으므로 브라우저가 본 요청을 보내지 않습니다.

그래서 데이터를 바꾸는 요청을 모두 **POST + JSON + Ajax 헤더**로 통일하고, 그렇지 않은 POST는 403으로 거부합니다. 화면을 그리는 GET 요청은 데이터를 바꾸지 않으므로 검사하지 않습니다. CSRF 토큰을 쓰는 방법도 있지만, Ajax만 쓰는 앱에서는 이 방법이 간단합니다. 보안 정리는 [CSRF와 XSS](/posts/ledger-auth-csrf-xss)에서 다시 다룹니다.

## 정리

- 인터셉터는 Controller 직전에 정해진 주소의 요청을 모두 검사합니다. `false`를 돌려주면 Controller까지 가지 않습니다.
- `LoginPageInterceptor`는 `/ledger/**`의 로그인을 확인하고, 화면은 로그인 페이지로, Ajax는 `sessionExpired` JSON으로 응답합니다.
- `ApiRequestInterceptor`는 POST를 Ajax + JSON으로만 받아 CSRF를 막습니다.
- 로그인이 필요한 기능을 모두 `/ledger/` 아래에 두어 빠짐없이 보호합니다.
