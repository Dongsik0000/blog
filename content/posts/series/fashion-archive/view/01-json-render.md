---
title: "JSON으로 그리는 목록과 상세 화면"
slug: "fashion-archive-view-json-render"
category: "development"
publishedAt: "2026-10-08"
summary: "JSP는 빈 틀만 그리고 화면 JS가 API 응답으로 목록 카드와 상세를 채우는 구조, 화면 JS 모듈의 모양, App.ajax와 세션 만료 처리, textContent로만 값을 넣는 규칙을 list.js와 detail.js로 설명합니다."
series: "fashion-archive"
chapter: "view"
seriesOrder: 1
---
## 이 글에서 할 일

Fashion Archive의 목록과 상세 화면은 서버(JSP)가 데이터를 그리지 않습니다. JSP는 **빈 틀**을 보내고, 화면이 뜨면 JS가 API를 불러 받은 JSON으로 화면을 채웁니다(커밋 `66fd242`). 이 글에서는 그 흐름을 목록 화면으로 따라갑니다.

## 흐름

```text
1. GET /p1/c/summer
   → PhotoController: 뷰 이름 + currentSlug
   → Tiles: 레이아웃 + list.jsp (사진 칸은 비어 있음)
2. 브라우저: list.js 실행
   → POST /p1/api/photos/list  {slug: "summer"}
3. PhotoApiController → JSON {code: "00", data: [사진, ...]}
4. list.js: 카드를 만들어 그리드에 붙임
```

## JSP: 빈 틀과 data 속성

`WEB-INF/jsp/fashion/photo/list.jsp`

```jsp
<ul class="photo-grid" id="photoGrid" data-slug="<c:out value='${currentSlug}'/>"></ul>

<p class="empty" id="listEmpty" hidden>
    아직 올린 사진이 없어요.
    <c:if test="${not empty sessionScope.loginId}">
        <a href="<c:url value='/admin/photos/new'/>">첫 사진 올리기</a>
    </c:if>
</p>
```

- 그리드(`<ul>`)는 비어 있고, 지금 카테고리를 `data-slug`에 적어 둡니다. JS가 이 값을 읽어 API에 보냅니다.
- "사진 없음" 안내는 `hidden`으로 숨겨 두고, 결과가 비었을 때 JS가 보여 줍니다.

## 화면 JS 모듈

모든 화면 JS는 같은 모양입니다(커밋 `e34994f` "화면 JS 형식 통일").

```js
App.photoList = (function(){
    var m$ = { grid: document.getElementById('photoGrid'), ... },   // 화면 요소
        settings = { slug: ... },                                    // 상태
        url = { list: contextPath + '/api/photos/list' },            // 서버 주소

        init = function(){
            bindEvent();
            load();
        },
        ...
    return{ init: init };
}());

document.addEventListener('DOMContentLoaded', function(){
    App.photoList.init();
});
```

| 부분 | 뜻 |
|---|---|
| `(function(){ ... }())` | 바로 실행되는 함수. 안의 변수는 밖에서 보이지 않음 |
| `m$`, `settings`, `url` | 화면 요소, 상태, 주소를 맨 위에 모음 |
| `bindEvent` | 이벤트 연결을 한곳에 |
| `handleXxxResult` | 응답 처리 함수를 요청 함수와 나눔 |
| `return { init: init }` | 밖에는 `init` 하나만 공개 |

## 공통 Ajax: App.ajax

`resources/js/common/common.js`

```js
// App.ajax(url, data, opts): JSON 요청/응답. X-Requested-With 헤더는 인터셉터의 Ajax 판별 + CSRF 방어에 쓰인다.
App.ajax = function (url, data, opts) {
    opts = opts || {};
    var method = (opts.method || 'POST').toUpperCase();
    var fetchOpt = {
        method: method,
        headers: {
            'Content-Type': 'application/json; charset=UTF-8',
            'X-Requested-With': 'XMLHttpRequest'
        },
        credentials: 'same-origin'
    };
    ...
    return App._handle(fetch(url, fetchOpt), opts);
};
```

```js
App._handle = function (fetchPromise, opts) {
    return fetchPromise
        .then(function (res) {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
        })
        .then(function (data) {
            if (data && data.sessionExpired) return App.sessionExpired();
            return data;
        })
        .catch(function (err) {
            if (typeof opts.onError === 'function') opts.onError(err);
            else App.error('오류', '요청 처리 중 문제가 발생했습니다.');
            throw err;
        });
};
```

| 처리 | 뜻 |
|---|---|
| `res.ok` 검사 | `fetch`는 HTTP 오류(403, 500)여도 실패로 보지 않으므로 직접 확인 |
| `sessionExpired` | [인터셉터](/posts/fashion-archive-spring-mvc-interceptor)가 보낸 세션 만료 응답이면 로그인 화면으로 |
| `catch` | 통신 오류는 공통 알림을 띄우고, 실패를 다시 던져 화면의 성공 처리가 실행되지 않게 함 |

JSON 요청(`App.ajax`, `App.post`)과 파일 요청([`App.upload`](/posts/fashion-archive-upload-multipart))이 같은 `App._handle`을 거칩니다.

### 세션 만료

```js
App.sessionExpired = function () {
    App.toast('로그인이 필요합니다. 로그인 화면으로 이동합니다.', 'error');
    setTimeout(function () {
        // location.pathname에는 컨텍스트 경로(/p1)가 들어 있고 login.js가 다시 붙이므로 빼고 넘긴다
        var path = location.pathname.substring(contextPath.length);
        window.location.href = contextPath + '/login?next=' + encodeURIComponent(path + location.search);
    }, 900);
    return new Promise(function () {});   // 체인 중단
};
```

안내를 잠깐 보여 준 뒤 로그인 화면으로 보내고, 로그인 후 지금 화면으로 돌아오도록 `next`를 붙입니다. 끝나지 않는 Promise를 돌려줘 뒤의 화면 그리기가 실행되지 않게 합니다.

## 카드 그리기: textContent만

`resources/js/app/photo/list.js` (일부)

```js
photos.forEach(function(p){
    var li = document.createElement('li'),
        a = document.createElement('a'),
        figure = document.createElement('figure'),
        img = document.createElement('img'),
        caption = document.createElement('figcaption'),
        title = document.createElement('span'),
        date = document.createElement('time');

    a.href = contextPath + '/photos/' + p.id;
    img.src = p.image_url;
    img.alt = p.title;
    img.loading = 'lazy';
    title.className = 'photo-title';
    title.textContent = p.title;
    date.className = 'photo-date';
    date.textContent = formatMonth(p.created_at);
    ...
    m$.grid.appendChild(li);
});
```

- 요소를 `createElement`로 만들고 사용자 값(제목)은 `textContent`로 넣습니다. 제목에 `<script>`가 들어 있어도 글자로 보입니다. 상세 화면 JS 주석도 같은 규칙을 적어 둡니다: "서버 값은 textContent/속성으로만 넣는다".
- `img.loading = 'lazy'`: 화면에 보일 때가 되어야 사진을 받습니다. 사진이 많은 그리드에서 처음 뜨는 속도가 빨라집니다.
- `img.alt = p.title`: 사진을 볼 수 없는 사용자(화면 낭독기)에게 제목을 알려 줍니다.

### 날짜

```js
/* created_at: Jackson이 Timestamp를 epoch millis(숫자)로 내려준다 */
formatMonth = function(value){
    if(!value) return '';
    var d = new Date(value),
        m = d.getMonth() + 1;
    return d.getFullYear() + '.' + (m < 10 ? '0' + m : m);
};
```

서버가 시각을 숫자로 보내므로 `new Date(숫자)`로 바꿔 `2026.09`처럼 표시합니다.

## 정리

- JSP는 빈 틀과 `data-` 속성만 그리고, 화면 JS가 API 응답으로 내용을 채웁니다.
- 화면 JS는 `App.xxx` 모듈 모양(`m$`, `settings`, `url`, `bindEvent`, `handleXxxResult`)으로 통일했습니다.
- `App.ajax`가 Ajax 헤더, HTTP 오류, 세션 만료, 통신 오류를 한곳에서 처리합니다.
- 사용자 값은 `textContent`와 속성으로만 넣습니다.
