---
title: "CSRF, SameSite, 제품 링크 주소 검사"
slug: "fashion-archive-auth-csrf-url"
category: "development"
publishedAt: "2026-10-08"
summary: "Ajax 헤더 검사와 SameSite=Lax 쿠키로 CSRF를 막는 구성, Dockerfile이 context.xml을 /p1 컨텍스트 설정으로 복사하는 이유, javascript: 주소를 막는 requireHttpUrl, XSS를 막는 textContent 규칙을 정리합니다."
series: "fashion-archive"
chapter: "auth"
seriesOrder: 4
---
## 이 글에서 할 일

Fashion Archive의 보안 장치 중 **다른 사이트에서 오는 공격**을 막는 것들을 모아 정리합니다.

| 공격 | 방어 |
|---|---|
| CSRF (다른 사이트가 로그인한 사용자 이름으로 요청) | Ajax 헤더 검사 + `SameSite=Lax` 쿠키 |
| 위험한 링크 주소 (`javascript:`) | 저장할 때 `http`, `https`만 허용 |
| XSS (사용자 값이 스크립트로 실행) | 화면에 `textContent`로만 넣기 |
| 사진으로 위장한 파일 | 파일 내용으로 형식 판별 + `nosniff` |

## CSRF

**CSRF**(사이트 간 요청 위조)는 사용자가 로그인한 상태로 다른 사이트에 들어갔을 때, 그 사이트가 몰래 우리 서버로 요청을 보내게 하는 공격입니다. 브라우저가 우리 사이트의 쿠키를 자동으로 붙이므로, 서버는 사용자가 직접 보낸 요청으로 착각합니다.

```html
<!-- 공격자 사이트에 숨겨진 폼 -->
<form action="https://도메인/p1/api/admin/photos/15/delete" method="POST"></form>
<script>document.forms[0].submit()</script>
```

### 방어 1. API는 Ajax 헤더가 있어야

```java
// 교차 출처 폼 전송은 이 헤더를 붙일 수 없다 → CSRF 방어
if (api && !ajax) {
    response.sendError(HttpServletResponse.SC_FORBIDDEN);
    return false;
}
```

[인터셉터](/posts/fashion-archive-spring-mvc-interceptor)는 `/api/` 요청에 `X-Requested-With: XMLHttpRequest` 헤더가 없으면 403으로 거절합니다. HTML 폼은 이 헤더를 붙일 수 없고, 다른 사이트의 JavaScript가 붙이려면 브라우저가 먼저 서버에 허락을 묻는(CORS 사전 요청) 단계를 거치는데 서버는 다른 출처를 허락하지 않습니다.

로그인 API와 가입 API는 로그인 전에 열려야 해서 인터셉터 매핑에서 빠져 있습니다.

### 방어 2. SameSite=Lax 쿠키

`src/main/webapp/META-INF/context.xml`

```xml
<Context>
    <CookieProcessor sameSiteCookies="lax" />
</Context>
```

Tomcat이 이 앱의 쿠키(세션 쿠키 포함)에 `SameSite=Lax`를 붙입니다. 브라우저는 **다른 사이트에서 시작된 POST 요청에는 이 쿠키를 보내지 않습니다.** 위의 숨겨진 폼이 전송되어도 세션 쿠키가 없으니 로그인하지 않은 요청이 됩니다. 다른 사이트의 링크를 눌러 들어오는 GET에는 쿠키가 붙어 로그인이 유지됩니다(커밋 `1714cde` "login interceptor and samesite cookie").

### context.xml을 p1.xml로 복사하는 이유

`Dockerfile`

```dockerfile
# SameSite 쿠키 설정을 /p1 컨텍스트에 적용 (nginx가 /p1/ 하위 경로로 리버스 프록시하므로 ROOT가 아닌 p1 컨텍스트로 배포한다)
COPY src/main/webapp/META-INF/context.xml /usr/local/tomcat/conf/Catalina/localhost/p1.xml
...
COPY target/fashion-archive.war /usr/local/tomcat/webapps/p1.war
```

Tomcat은 `conf/Catalina/localhost/{컨텍스트 이름}.xml` 파일을 그 웹 앱의 설정으로 읽습니다. 주석대로 war를 `ROOT`가 아니라 `p1.war`로 배포하므로 컨텍스트 이름은 `p1`이고, 설정 파일 이름도 `p1.xml`로 맞춥니다.

## 제품 링크 주소 검사

제품 링크는 사용자가 입력한 주소를 `<a href>`로 그대로 씁니다. 그런데 `href`에 `javascript:alert(1)`이 들어가면, 누르는 순간 그 코드가 이 사이트에서 실행됩니다.

`Validation.requireHttpUrl`

```java
public static String requireHttpUrl(Object value) {
    String s = requireText(value, "URL", 2000);
    try {
        URI uri = new URI(s);
        String scheme = uri.getScheme();
        boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!http || uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("http:// 또는 https://로 시작하는 URL을 입력해주세요.");
        }
        return s;
    } catch (URISyntaxException e) {
        throw new IllegalArgumentException("올바른 URL이 아닙니다.");
    }
}
```

| 주소 | 결과 |
|---|---|
| `https://www.musinsa.com/...` | 통과 |
| `javascript:alert(1)` | scheme이 `javascript`라 거절 |
| `ftp://example.com/a` | `http`, `https`가 아니라 거절 |
| `musinsa.com` | scheme이 없어 거절 |
| `http://` | 호스트가 없어 거절 |

"`javascript:`를 막는다"는 금지 목록이 아니라 **"`http`, `https`만 허락한다"는 허용 목록**입니다. `JavaScript:`, `data:`처럼 변형된 위험 주소를 일일이 막을 필요가 없습니다. 위 다섯 경우가 `ValidationTest`에 그대로 있습니다. API 주석도 "javascript: 같은 URL은 여기서 걸러진다"고 적어 둡니다.

## XSS: textContent로만

```js
/* ---------- 렌더링. 서버 값은 textContent/속성으로만 넣는다 ---------- */
m$.title.textContent = photo.title;
m$.memo.textContent = photo.memo || '';
```

사진 제목, 메모, 링크 이름 같은 사용자 값은 `innerHTML`이 아니라 `textContent`로 넣습니다. 값에 `<script>`가 있어도 글자로 보입니다. JSP에서 출력하는 값은 `<c:out>`으로 이스케이프합니다.

## 사진 파일

올린 파일은 [내용으로 형식을 판별](/posts/fashion-archive-upload-file-type)하고, nginx가 `/uploads/` 응답에 `X-Content-Type-Options: nosniff`를 붙여 브라우저가 파일 종류를 추측해 실행하지 않게 합니다([nginx 연결](/posts/fashion-archive-deploy-nginx)).

## 정리

- CSRF는 `/api/` 요청의 Ajax 헤더 검사와 `SameSite=Lax` 쿠키로 이중으로 막습니다.
- `SameSite` 설정은 Tomcat의 `p1` 컨텍스트 설정 파일로 복사해 적용합니다.
- 제품 링크는 `http`, `https`만 허용하는 목록 방식으로 `javascript:` 주소를 막습니다.
- 사용자 값은 `textContent`와 `<c:out>`으로만 화면에 넣습니다.
