---
title: "로그인 인터셉터 (로그인 확인, API 요청 검사, 원래 화면으로 돌아가기)"
slug: "fashion-archive-spring-mvc-interceptor"
category: "development"
publishedAt: "2026-10-08"
summary: "거의 모든 요청을 지나는 LoginInterceptor가 /api 요청의 Ajax 헤더를 검사해 CSRF를 막고, 로그인을 확인하고, 로그인 후 원래 가려던 화면으로 돌아오도록 next 경로를 넘기는 과정을 코드로 따라갑니다."
series: "fashion-archive"
chapter: "spring-mvc"
seriesOrder: 5
---
## 이 글에서 할 일

[dispatcher-servlet.xml](/posts/fashion-archive-spring-mvc-dispatcher-servlet)에서 `LoginInterceptor`를 로그인 전 화면과 정적 파일을 뺀 **모든 요청**에 걸었습니다. 이 인터셉터 하나가 세 가지 일을 합니다.

1. `/api/` 요청이 Ajax인지 확인 (CSRF 방어)
2. 로그인했는지 확인
3. 로그인하지 않았으면 요청 종류에 맞게 응답

## 전체 코드

`com/fashion/cmmn/interceptor/LoginInterceptor.java`

```java
// 로그인·가입·정적 파일을 뺀 전체 경로에 매핑(dispatcher-servlet.xml). 로그인 여부와 Ajax 여부를 판별한다.
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        boolean ajax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        boolean api = request.getRequestURI().startsWith(request.getContextPath() + "/api/");

        // 교차 출처 폼 전송은 이 헤더를 붙일 수 없다 → CSRF 방어
        if (api && !ajax) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(Constants.SESSION_LOGIN_ID) != null) {
            return true;
        }

        if (ajax) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"sessionExpired\":true}");
        } else {
            // 로그인 후 원래 가려던 화면으로 돌아오도록 경로를 넘긴다 (컨텍스트 경로 제외)
            String next = request.getRequestURI().substring(request.getContextPath().length());
            response.sendRedirect(request.getContextPath() + "/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
        }
        return false;
    }
}
```

`preHandle`은 컨트롤러가 실행되기 **직전에** 호출됩니다. `true`를 돌려주면 컨트롤러로 진행하고, `false`면 여기서 끝나며 인터셉터가 직접 응답을 씁니다.

## 1. API 요청은 Ajax만

```java
boolean ajax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
boolean api = request.getRequestURI().startsWith(request.getContextPath() + "/api/");

if (api && !ajax) {
    response.sendError(HttpServletResponse.SC_FORBIDDEN);
    return false;
}
```

- `request.getRequestURI()`는 `/p1/api/photos/list`처럼 **컨텍스트 경로를 포함한** 주소입니다. 그래서 `getContextPath() + "/api/"`로 비교합니다.
- 화면 JS의 공통 함수(`App.ajax`, `App.upload`)는 모든 요청에 `X-Requested-With: XMLHttpRequest`를 붙입니다.
- 다른 사이트의 HTML 폼은 이 헤더를 붙일 수 없습니다. 그래서 헤더가 없는 API 요청을 403으로 거절하면, 다른 사이트가 로그인한 사용자 이름으로 사진을 지우게 만드는 **CSRF**가 막힙니다([CSRF와 SameSite](/posts/fashion-archive-auth-csrf-url)).

이 검사를 로그인 확인보다 **먼저** 합니다. 로그인한 사용자의 요청이라도 헤더가 없으면 거절해야 하기 때문입니다.

## 2. 로그인 확인

```java
HttpSession session = request.getSession(false);
if (session != null && session.getAttribute(Constants.SESSION_LOGIN_ID) != null) {
    return true;
}
```

- `getSession(false)`: 세션이 없으면 **새로 만들지 않고** `null`을 돌려줍니다. 로그인하지 않은 방문자마다 빈 세션이 쌓이지 않습니다.
- 로그인할 때 세션에 넣은 `loginId`가 있으면 통과입니다([세션 로그인](/posts/fashion-archive-auth-session-login)).

## 3. 로그인하지 않았을 때

요청 종류에 따라 다르게 답합니다.

| 요청 | 응답 | 화면 동작 |
|---|---|---|
| Ajax | `{"sessionExpired":true}` JSON | 공통 JS가 안내를 띄우고 로그인 화면으로 이동 |
| 화면 | `/p1/login?next=/photos/15`로 redirect | 로그인 후 `/photos/15`로 돌아옴 |

Ajax 요청에 redirect를 보내면 JS가 로그인 화면 HTML을 데이터로 받게 되므로, JSON으로 "세션 만료"를 알립니다.

### next: 원래 가려던 곳

```java
// 로그인 후 원래 가려던 화면으로 돌아오도록 경로를 넘긴다 (컨텍스트 경로 제외)
String next = request.getRequestURI().substring(request.getContextPath().length());
response.sendRedirect(request.getContextPath() + "/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
```

`next`에는 컨텍스트 경로(`/p1`)를 **뺀** 경로를 넣습니다. 로그인 화면의 JS가 이동할 때 `contextPath`를 다시 붙이기 때문입니다. 이 규칙을 화면 JS 쪽에서 맞추지 않아 `/p1/p1/`로 이동하던 문제가 있었습니다([겪은 문제](/posts/fashion-archive-troubleshooting-double-context-path)).

로그인 화면은 `next`가 이 사이트 안의 경로일 때만 따릅니다.

`resources/js/app/login/login.js`

```js
/* ?next=/photos/4 처럼 이 사이트 안의 경로만 허용한다 ("//evil.com" 같은 외부 이동 차단) */
nextPath = function(){
    var next = new URLSearchParams(location.search).get('next') || '';
    return /^\/(?!\/)/.test(next) ? next : '/';
},
```

정규식 `^\/(?!\/)`는 "`/`로 시작하되 바로 뒤에 `/`가 오지 않는다"는 뜻입니다. `//evil.com`은 브라우저가 다른 사이트 주소로 해석하므로, 이것을 막아 로그인 화면이 공격자 사이트로 보내는 **오픈 리다이렉트**를 막습니다.

## 정리

- `LoginInterceptor` 하나가 거의 모든 요청에서 API 헤더 검사와 로그인 확인을 합니다.
- `/api/` 요청은 `X-Requested-With` 헤더가 없으면 403으로 거절해 CSRF를 막습니다.
- 로그인하지 않았으면 Ajax는 `sessionExpired` JSON, 화면은 `next`를 붙인 로그인 화면으로 보냅니다.
- `next`는 컨텍스트 경로를 뺀 사이트 안 경로만 쓰고, 외부 주소로는 이동하지 않습니다.
