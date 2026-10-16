---
title: "CSRF와 XSS 방어 정리"
slug: "ledger-auth-csrf-xss"
category: "development"
publishedAt: "2026-10-08"
summary: "웹 앱의 대표적인 두 공격인 CSRF와 XSS가 무엇인지, Ledger가 Ajax와 JSON만 받는 인터셉터, textContent와 App.h, c:out, HttpOnly로 어떻게 겹겹이 막는지 한곳에 정리합니다."
series: "ledger"
chapter: "auth"
seriesOrder: 4
---
## 이 글에서 할 일

앞의 여러 글에 흩어진 보안 장치 중 **CSRF**와 **XSS** 방어를 한곳에 모아 정리합니다. 면접에서 "보안은 어떻게 신경 썼나요?"라는 질문에 답할 수 있게 하는 것이 목표입니다.

## 두 공격 한 줄 요약

| 공격 | 한 줄 요약 | 피해 |
|---|---|---|
| CSRF (사이트 간 요청 위조) | 다른 사이트가 **로그인한 사용자 이름으로** 우리 서버에 요청을 보내게 함 | 거래 삭제, 설정 변경 |
| XSS (교차 사이트 스크립팅) | 공격자의 **스크립트가 우리 화면에서 실행**되게 함 | 화면 조작, 사용자 몰래 요청 |

## CSRF 방어

### 원리

브라우저는 요청을 보낼 때 그 사이트의 쿠키를 자동으로 붙입니다. 공격자 사이트에 숨긴 폼이 우리 서버로 POST를 보내면, 사용자의 세션 쿠키가 함께 가서 서버는 정상 요청으로 착각합니다.

### Ledger의 방어

| 장치 | 위치 | 설명 |
|---|---|---|
| POST는 Ajax + JSON만 | `ApiRequestInterceptor` | HTML 폼은 JSON 본문과 `X-Requested-With` 헤더를 보낼 수 없음. 다른 출처의 JS가 보내려면 CORS 사전 요청이 필요한데 서버가 허용하지 않음 |
| 상태를 바꾸는 요청은 모두 POST | Controller 설계 | 로그아웃까지 POST. GET은 화면과 CSV 다운로드처럼 읽기만 |
| 세션 쿠키 `SameSite=Lax` | `META-INF/context.xml` | 다른 사이트에서 보낸 POST에는 세션 쿠키가 붙지 않음 ([세션](/posts/ledger-auth-session)) |

자세한 코드는 [인터셉터 글](/posts/ledger-spring-interceptor)에 있습니다.

## XSS 방어

### 원리

사용자가 거래 내용에 `<img src=x onerror="...">`를 넣었다고 해 봅시다. 화면이 이 값을 HTML로 넣으면(`innerHTML`) 브라우저가 태그로 해석해 스크립트를 실행합니다. 가계부는 혼자 쓰는 앱이지만, 같은 방식으로 다른 사람의 데이터가 섞이는 기능이 생기면 바로 위험해집니다.

### Ledger의 방어: 글자는 글자로

Ledger의 화면은 대부분 JS가 Ajax로 받은 데이터로 그립니다([JSP와 JSTL](/posts/ledger-view-jsp-jstl)). 그래서 방어의 중심은 JS입니다. README의 규칙입니다.

> 사용자 값은 `textContent`/`App.escape` 로만 HTML 에 넣는다.

| 장치 | 위치 | 설명 |
|---|---|---|
| `textContent` | 화면 JS | 값을 글자로만 넣음. HTML로 해석하지 않음 |
| `App.h(tag, {text: 값})` | `common.js` | 요소를 만들며 `textContent`로 값을 넣는 도우미. 화면 코드가 매번 신경 쓰지 않아도 안전 |
| `App.escape(값)` | `common.js` | HTML 문자열이 꼭 필요할 때 `<`, `>`, `&`, 따옴표를 바꿈 |
| `<c:out>` | JSP | 서버에서 출력하는 값(화면 제목 등)을 이스케이프 |
| 알림 창 | `modal.js` | 제목과 설명을 `textContent`로 넣음. 서버가 보낸 오류 문장도 안전 |

```js
// App.h(tag, props, children): DOM 요소를 안전하게 만든다. 문자열 자식은 텍스트 노드(HTML 해석 없음).
App.h('td', {text: r.title})
```

### XSS가 생겨도 피해를 줄이는 장치

| 장치 | 설명 |
|---|---|
| 세션 쿠키 `HttpOnly` | 스크립트가 `document.cookie`로 세션 ID를 읽어 가지 못함 ([세션](/posts/ledger-auth-session)) |
| `X-Content-Type-Options: nosniff` | nginx가 붙이는 헤더. 브라우저가 파일 종류를 추측해 스크립트로 실행하지 않음 |
| `X-Frame-Options: SAMEORIGIN` | 다른 사이트가 우리 화면을 iframe으로 띄워 클릭을 가로채지 못함 |

nginx 헤더는 [Oracle Cloud 시리즈의 보안 헤더 글](/posts/oracle-cloud-nginx-security)에 있습니다.

## 그 밖의 보안 장치 한눈에

| 위협 | 방어 | 글 |
|---|---|---|
| SQL 주입 | 모든 값 `#{}` | [파라미터와 결과](/posts/ledger-mybatis-params-results) |
| 남의 데이터 접근 (IDOR) | 모든 SQL에 `user_id`, userId는 세션에서만 | [사용자 조건](/posts/ledger-mybatis-user-scope) |
| 비밀번호 유출 | BCrypt 해시 | [가입과 로그인](/posts/ledger-auth-signup-login) |
| 비밀번호 대입 | 5회 실패 5분 차단 + nginx 속도 제한 | [로그인 시도 제한](/posts/ledger-auth-login-limit) |
| 세션 고정 | 로그인 시 세션 ID 교체 | [세션](/posts/ledger-auth-session) |
| 내부 정보 노출 | 공통 오류 화면, 일반적인 오류 문장 | [예외 처리](/posts/ledger-spring-exception-handling) |
| 스프레드시트 수식 주입 | CSV 셀 앞에 `'` | [CSV 수식 주입](/posts/ledger-auth-csv-injection) |
| XXE (XML 외부 개체) | 공휴일 XML 해석 시 외부 개체 차단 | [공휴일 API](/posts/ledger-external-api-holiday-api) |
| 비밀값 유출 | 저장소 밖 `app.env`, 운영 로그 INFO | [설정값과 환경 변수](/posts/ledger-spring-properties) |

## 정리

- CSRF는 "POST는 Ajax + JSON만" 인터셉터와 "상태 변경은 모두 POST" 설계로 막습니다.
- XSS는 사용자 값을 `textContent`(`App.h`)로만 넣는 규칙으로 막고, `HttpOnly`와 보안 헤더로 피해를 줄입니다.
- 그 밖의 위협도 장치별로 정리해 두면 설명하기 쉽습니다.
