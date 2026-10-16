---
title: "세션 관리 (세션 ID 교체, HttpOnly, 로그아웃)"
slug: "ledger-auth-session"
category: "development"
publishedAt: "2026-10-08"
summary: "로그인 상태를 서버 세션으로 유지하는 방법, 로그인과 비밀번호 변경 때 세션 ID를 바꿔 세션 고정 공격을 막는 이유, 쿠키 설정과 POST 로그아웃을 설명합니다."
series: "ledger"
chapter: "auth"
seriesOrder: 2
---
## 이 글에서 할 일

HTTP는 요청마다 따로입니다. 서버는 방금 로그인한 사람과 다음 요청을 보낸 사람이 같은지 모릅니다. **세션**이 그 연결을 이어 줍니다. Ledger의 세션 코드는 `SessionUtil` 하나에 모여 있습니다.

## 세션의 원리

```text
1. 로그인 성공 → 서버: 세션 저장소에 {userId: 3} 저장, 세션 ID "A1B2..." 발급
2. 응답 쿠키:  JSESSIONID=A1B2...
3. 다음 요청부터 브라우저가 쿠키를 자동으로 붙임
4. 서버: 쿠키의 세션 ID로 세션을 찾아 userId 3을 꺼냄
```

세션 데이터(누구인지)는 **서버**에 있고, 브라우저는 세션 ID라는 열쇠만 가집니다. 세션 저장과 쿠키 발급은 Tomcat이 해 줍니다.

## 로그인: 세션에 사용자 넣기

`ledger/cmmn/util/SessionUtil.java`

```java
// 로그인 성공 시 세션 ID 를 새로 발급한다(세션 고정 공격 방지). 기존 속성은 유지된다.
public static void login(HttpServletRequest request, Long userId, String username) {
    request.getSession(true);
    request.changeSessionId();
    HttpSession session = request.getSession(false);
    session.setAttribute(Constants.SESSION_USER_ID, userId);
    session.setAttribute(Constants.SESSION_USERNAME, username);
}
```

```java
// 세션 키
public static final String SESSION_USER_ID  = "userId";   // Long, app_user.id — 모든 조회의 격리 기준
```

`userId`는 이후 모든 조회의 기준이 됩니다([사용자 조건](/posts/ledger-mybatis-user-scope)). [로그인 인터셉터](/posts/ledger-spring-interceptor)는 이 값이 있는지로 로그인 여부를 판단합니다.

## 세션 고정 공격과 changeSessionId

**세션 고정(Session Fixation)** 공격은 다음과 같습니다.

```text
1. 공격자가 사이트에 접속해 세션 ID "X"를 받음 (로그인 전)
2. 피해자의 브라우저가 세션 ID "X"를 쓰게 만듦 (주소나 쿠키 주입)
3. 피해자가 로그인 → 서버가 세션 "X"에 피해자 userId를 넣음
4. 공격자는 이미 아는 "X"로 피해자 계정에 들어감
```

로그인하는 순간 `request.changeSessionId()`로 **세션 ID를 새로 바꾸면**, 공격자가 알던 "X"는 쓸모가 없어집니다. 세션 안의 값은 그대로 옮겨집니다.

비밀번호를 바꿀 때도 세션 ID를 교체합니다.

```java
authService.updatePassword(ParamUtil.map("userId", userId, "passwordHash", passwordEncoder.encode(next)));
request.changeSessionId();
```

## 쿠키와 세션 설정

`web.xml`

```xml
<session-config>
    <session-timeout>1440</session-timeout>
    <!-- Session ID URL 노출 차단 -->
    <tracking-mode>COOKIE</tracking-mode>
    <cookie-config>
        <http-only>true</http-only>
    </cookie-config>
</session-config>
```

| 설정 | 막는 것 |
|---|---|
| `tracking-mode COOKIE` | 세션 ID가 주소에 붙는 것(`;jsessionid=...`). 주소는 화면 공유, 브라우저 기록, Referer로 쉽게 새고, 세션 고정의 통로가 됨 |
| `http-only` | JavaScript의 `document.cookie`로 세션 쿠키를 읽는 것. XSS가 생겨도 세션 ID를 훔치기 어렵게 |
| `session-timeout 1440` | 1440분(24시간) 동안 요청이 없으면 세션 만료 |

### SameSite=Lax

`src/main/webapp/META-INF/context.xml`

```xml
<Context>
    <CookieProcessor sameSiteCookies="lax" />
</Context>
```

`META-INF/context.xml`은 Tomcat이 이 웹 앱에 적용하는 설정입니다. 세션 쿠키에 `SameSite=Lax`를 붙여, **다른 사이트에서 시작된 POST 요청에는 쿠키를 보내지 않게** 합니다. 다른 사이트의 링크를 눌러 들어오는 GET에는 쿠키가 붙어 로그인이 유지됩니다. [CSRF 인터셉터](/posts/ledger-spring-interceptor)와 함께 CSRF를 이중으로 막습니다.

### 설정 파일에서 확인할 수 있는 범위

`web.xml`에는 `HttpOnly`와 1440분의 세션 만료 시간이 명시되어 있습니다. `META-INF/context.xml`에는 `SameSite=Lax`가 명시되어 있습니다. `Secure` 속성은 이 두 파일에 명시되어 있지 않으므로, 위 설정만으로 실제 응답 쿠키에 `Secure`가 붙는다고 설명할 수는 없습니다.

운영 응답의 쿠키 속성은 브라우저 개발자 도구에서 로그인 응답의 `Set-Cookie`와 저장된 `JSESSIONID`를 확인합니다. 쿠키 값을 복사하거나 공유할 필요는 없으며, 속성 이름만 확인합니다. `SameSite` 설정의 의미는 [Tomcat 10.1 공식 문서](https://tomcat.apache.org/tomcat-10.1-doc/config/cookie-processor.html)에서 확인했습니다(2026-10-07).

## 로그아웃은 POST로

```java
@ResponseBody
@PostMapping("/auth/logout")
public Response logout(HttpServletRequest request) {
    SessionUtil.logout(request);
    return Response.of(Constants.SUCCESS);
}
```

```java
public static void logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
        session.invalidate();
    }
}
```

- `session.invalidate()`: 서버의 세션을 완전히 지웁니다. 쿠키만 지우는 것과 달리, 세션 ID가 새어 있어도 더는 쓸 수 없습니다.
- `getSession(false)`: 세션이 없으면 새로 만들지 않습니다.
- **GET이 아니라 POST**: 로그아웃이 GET이면 다른 사이트가 `<img src="/p3/auth/logout">` 한 줄로 사용자를 로그아웃시킬 수 있습니다. 상태를 바꾸는 요청은 POST로 만들고 [CSRF 검사](/posts/ledger-spring-interceptor)를 거치게 합니다(커밋 "로그아웃 POST 버튼").

## 정리

- 세션은 서버에 사용자 정보를 두고, 브라우저는 세션 ID 쿠키만 가집니다.
- 로그인과 비밀번호 변경 때 `changeSessionId()`로 세션 ID를 바꿔 세션 고정을 막습니다.
- 세션 ID는 쿠키로만, `HttpOnly`로 JS에서 못 읽게 합니다.
- 로그아웃은 POST로 받고 `invalidate()`로 서버 세션을 지웁니다.
