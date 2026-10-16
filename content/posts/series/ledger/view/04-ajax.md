---
title: "fetch로 만든 공통 Ajax 함수"
slug: "ledger-view-ajax"
category: "development"
publishedAt: "2026-10-08"
summary: "jQuery 대신 브라우저 기본 fetch로 만든 App.ajax가 요청 헤더, JSON 변환, HTTP 오류, 세션 만료, 429 안내를 한곳에서 처리하는 방법과 결과 코드 분기 App.result를 설명합니다."
series: "ledger"
chapter: "view"
seriesOrder: 4
---
## 이 글에서 할 일

Ledger의 모든 서버 호출은 `App.ajax` 하나를 거칩니다. 공통 처리를 한곳에 모으면 화면 코드는 "무엇을 보내고, 성공하면 무엇을 그릴지"만 쓰면 됩니다.

## jQuery를 쓰지 않는 이유

프로젝트 골격에는 jQuery 1.11.3과 jQuery UI 파일이 들어 있었지만, 커밋 "화면: ... jQuery 제거"에서 걷어냈습니다. Ajax는 처음부터 `fetch`로 만들었기 때문에 jQuery가 필요 없었습니다. 코드 주석이 이유를 남깁니다.

```js
// jQuery 는 쓰지 않는다(알려진 취약점, 제거함).
```

jQuery 1.x에는 알려진 보안 취약점이 있고, 지금 브라우저는 jQuery가 하던 일 대부분을 기본 기능(`fetch`, `querySelector`, `classList`)으로 제공합니다. 라이브러리 하나를 덜 관리하고, 파일 크기도 줄어듭니다.

## App.ajax

`resources/js/common/common.js`

```js
App.ajax = function (url, data, opts) {
    opts = opts || {};
    var method = (opts.method || 'POST').toUpperCase();
    var fetchOpt = {
        method: method,
        headers: {
            'Content-Type': 'application/json; charset=UTF-8',
            'X-Requested-With': 'XMLHttpRequest'   // 인터셉터가 Ajax로 인식하게
        },
        credentials: 'same-origin'
    };

    if (method === 'GET') {
        if (data) url += (url.indexOf('?') === -1 ? '?' : '&') + new URLSearchParams(data).toString();
    } else {
        fetchOpt.body = JSON.stringify(data || {});
    }

    return fetch(url, fetchOpt)
        .then(function (res) {
            if (!res.ok) {
                var err = new Error('HTTP ' + res.status);
                err.status = res.status;
                throw err;
            }
            return res.json();
        })
        .then(function(data){
            if(data && data.sessionExpired) return App.sessionExpired();
            return data;
        })
        .catch(function (err) {
            if (typeof opts.onError === 'function') {
                opts.onError(err);
            } else if (typeof window._error === 'function') {
                // 429: nginx 요청 속도 제한(로그인·가입 시도 등)
                if (err.status === 429) window._error('잠시 후 다시 시도해 주세요', '짧은 시간에 요청이 너무 많았어요. 1분쯤 뒤에 다시 시도해 주세요.');
                else window._error('오류', '요청 처리 중 문제가 발생했습니다.');
            }
            throw err;
        });
};
```

### 요청 만들기

| 부분 | 왜 |
|---|---|
| `Content-Type: application/json` | 서버의 `@RequestBody`가 JSON으로 읽음. [CSRF 인터셉터](/posts/ledger-spring-interceptor)도 이 값을 검사 |
| `X-Requested-With: XMLHttpRequest` | 서버가 Ajax 요청으로 알아봄. 세션 만료와 오류 때 JSON으로 답하게 함 |
| `credentials: 'same-origin'` | 같은 사이트 요청에 세션 쿠키를 붙임 |
| `JSON.stringify(data)` | 객체를 JSON 글자로 |

### 응답 처리: Promise 체인

`fetch`는 **Promise**를 돌려줍니다. Promise는 "나중에 올 결과"를 담은 객체이고, `.then`으로 결과가 왔을 때 할 일을 이어 붙입니다.

```text
fetch ─▶ then: HTTP 오류인가? (fetch는 404, 500이어도 실패로 보지 않음) ─▶ JSON으로 변환
      ─▶ then: 세션 만료인가? ─▶ 아니면 데이터를 다음으로
      ─▶ catch: 어디서든 실패하면 공통 오류 창
```

- **`res.ok` 검사**: `fetch`는 서버가 500을 보내도 "응답은 왔다"며 성공으로 처리합니다. 그래서 상태 코드를 직접 확인해 실패로 바꿉니다.
- **세션 만료**: 로그인 인터셉터가 보낸 `{"sessionExpired": true}`를 보면 "다시 로그인해 주세요" 창을 띄우고 로그인 화면으로 보냅니다.
- **429**: nginx의 [로그인 시도 속도 제한](/posts/oracle-cloud-nginx-rate-limit)에 걸렸을 때 일반 오류 대신 "1분쯤 뒤에 다시"라고 안내합니다.
- **`throw err`**: 공통 안내를 띄운 뒤에도 실패를 다시 던져, 화면 코드의 `.then`(성공 처리)이 실행되지 않게 합니다.

### 세션 만료 뒤 체인 멈추기

```js
App.sessionExpired = function(){
    _error('로그인 세션시간이 초과되었습니다.', '다시 로그인 부탁드립니다.', function(){
        window.location.href = contextPath + '/login';
    });
    return new Promise(function(){});   // 체인 중단 - 뒤의 .then(render)를 막는다
};
```

**영원히 끝나지 않는 Promise**를 돌려줍니다. 뒤의 `.then`은 결과를 기다리기만 하고 실행되지 않으므로, 데이터가 없는데 화면을 그리다 오류가 나는 일이 없습니다.

## App.CODE와 App.result: 결과 코드 분기

서버의 `Constants`와 같은 값을 JS에도 둡니다.

```js
// 서버 결과 코드. Java ledger.cmmn.util.Constants 와 같은 값을 유지한다.
App.CODE = {
    SUCCESS: '00',
    FAIL: '99',
    INVALID: '90',
    NOT_FOUND: '91',
    ...
};
```

```js
// App.result(res, handlers): 결과 코드 분기 공통. handlers.ok 는 성공, handlers['95'] 처럼 코드별 처리.
// 처리하지 않은 90·92 는 서버 message, 91 은 "찾을 수 없어요", 나머지는 App.fail.
App.result = function (res, handlers) { ... };
```

예를 들어 "사용 중이라 삭제할 수 없음(95)"만 따로 다루고 싶다면 다음처럼 씁니다.

```js
App.post(url.categoryDelete, {id: id})
    .then(function(res){
        App.result(res, {
            ok: reload,
            '95': function(r){ _error('삭제할 수 없어요', r.message); }   // App.CODE.IN_USE
        });
    })
    .catch(function(){});
```

`settings.js`는 이 처리를 `handlers[App.CODE.IN_USE] = opts.inUse;`처럼 공통 저장 함수 안에서 붙입니다.

특별히 다룰 코드만 적고, 나머지는 공통 처리에 맡깁니다.

### App.guard: 그리다 난 오류도 알리기

```js
// App.guard(fn, res): 결과 처리(화면 그리기) 중 예외를 조용히 삼키지 않고 알린다.
```

성공 응답을 받아 화면을 그리다가 JS 오류가 나면, 아무 안내 없이 빈 화면으로 멈출 수 있습니다. `App.result`는 성공 처리 함수를 `App.guard`로 감싸 실행하고, 오류가 나면 "새로고침해 주세요. 배포 직후라면 화면과 서버 버전이 다를 수 있어요" 같은 안내를 띄웁니다.

## 정리

- `App.ajax`는 `fetch`로 JSON 요청을 보내고, HTTP 오류, 세션 만료, 429를 한곳에서 처리합니다.
- jQuery는 알려진 취약점과 불필요함 때문에 걷어냈습니다.
- 서버와 같은 결과 코드를 `App.CODE`에 두고, `App.result`로 성공과 코드별 처리를 나눕니다.
- 세션 만료는 끝나지 않는 Promise로 체인을 멈추고, 그리기 오류는 `App.guard`가 알립니다.
