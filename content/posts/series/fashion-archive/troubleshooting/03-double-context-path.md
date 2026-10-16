---
title: "다시 로그인하면 /p1/p1/로 이동하던 문제"
slug: "fashion-archive-troubleshooting-double-context-path"
category: "development"
publishedAt: "2026-10-08"
summary: "세션이 만료된 뒤 다시 로그인하면 컨텍스트 경로가 두 번 붙어 /p1/p1/ 주소로 이동해 404가 나던 문제와, next 값에서 컨텍스트 경로를 빼서 넘기도록 화면 JS를 고친 해결을 설명합니다."
series: "fashion-archive"
chapter: "troubleshooting"
seriesOrder: 3
---
## 해결한 문제

화면을 열어 둔 채 시간이 지나 로그인 세션이 만료된 뒤, 버튼을 눌러 다시 로그인하면 `/p1/p1/photos/15`처럼 **`/p1`이 두 번 붙은 주소**로 이동해 404가 났습니다. 2026-09-29의 커밋 `3a37d9c`가 이 문제를 고쳤습니다.

> 로그인 만료 후 다시 로그인하면 /p1/p1/ 경로로 이동해 404가 나던 문제 수정: next에서 컨텍스트 경로 제외

## 로그인 후 돌아오는 흐름

로그인하지 않은 상태로 화면을 열거나 API를 부르면, 로그인 화면으로 보내면서 원래 가려던 곳을 `next`로 넘깁니다. 로그인 화면의 JS는 로그인에 성공하면 `contextPath + next`로 이동합니다.

`resources/js/app/login/login.js`

```js
location.href = contextPath + nextPath();
```

즉 **`next`에는 컨텍스트 경로(`/p1`)를 뺀 경로**가 들어 있어야 합니다.

`next`를 만드는 곳은 두 군데입니다.

| 상황 | `next`를 만드는 곳 |
|---|---|
| 로그인하지 않고 화면 주소로 들어옴 | 서버의 `LoginInterceptor` (redirect) |
| 화면을 보던 중 세션이 만료되어 Ajax가 `sessionExpired`를 받음 | 화면 JS의 `App.sessionExpired` |

## 원인

서버의 인터셉터는 컨텍스트 경로를 빼고 `next`를 만들었습니다.

```java
// 로그인 후 원래 가려던 화면으로 돌아오도록 경로를 넘긴다 (컨텍스트 경로 제외)
String next = request.getRequestURI().substring(request.getContextPath().length());
```

그런데 화면 JS는 브라우저 주소를 **그대로** 넘기고 있었습니다.

```js
// 수정 전
window.location.href = contextPath + '/login?next=' + encodeURIComponent(location.pathname + location.search);
```

`location.pathname`은 `/p1/photos/15`처럼 컨텍스트 경로를 포함합니다. 로그인 후 `contextPath + next` = `/p1` + `/p1/photos/15` = `/p1/p1/photos/15`가 됩니다.

인터셉터를 거치는 흐름은 이미 컨텍스트 경로를 빼고 있어서 문제가 없었고, 세션이 만료되어 화면 JS가 `next`를 만드는 흐름에서만 일어났습니다.

## 해결

`resources/js/common/common.js`

```diff
-        window.location.href = contextPath + '/login?next=' + encodeURIComponent(location.pathname + location.search);
+        // location.pathname에는 컨텍스트 경로(/p1)가 들어 있고 login.js가 다시 붙이므로 빼고 넘긴다
+        var path = location.pathname.substring(contextPath.length);
+        window.location.href = contextPath + '/login?next=' + encodeURIComponent(path + location.search);
```

화면 JS도 서버와 같은 규칙으로 컨텍스트 경로를 빼고 넘깁니다. `next`를 만드는 두 곳이 같은 규칙("컨텍스트 경로 제외")을 따르게 되었고, 주석으로 이유를 남겼습니다.

## 로컬에서는 드러나지 않을 수 있는 이유

컨텍스트 경로가 비어 있는 환경(`ROOT`로 배포)에서는 `contextPath`가 `""`라 두 번 붙어도 주소가 같습니다. 운영처럼 `/p1` 아래에서 실행해야 드러나는 문제입니다. 로컬도 운영과 같은 `/p1` 컨텍스트로 실행하면 이런 차이를 미리 볼 수 있습니다([로컬 실행](/posts/fashion-archive-intro-local-run)).

## 확인 방법

1. 사진 상세 화면(`/p1/photos/{id}`)을 연 채로 세션을 끝냅니다. 다른 탭에서 `/p1/logout`을 열면 됩니다.
2. 원래 탭에서 "편집" 같은 동작을 해 API를 부릅니다. "로그인이 필요합니다" 알림 뒤 로그인 화면으로 이동합니다.
3. 주소의 `next` 값이 `/photos/{id}`인지(`/p1`으로 시작하지 않는지) 확인합니다.
4. 로그인하면 `/p1/photos/{id}`로 돌아오는지 확인합니다.

기록은 [해결 커밋](https://github.com/Dongsik0000/fashion-archive/commit/3a37d9c8d71832313b6c0847df838e4828918927)과 현재 `common.js`, `LoginInterceptor.java`, `login.js`를 근거로 작성했습니다.
