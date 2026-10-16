---
title: "화면별 JavaScript 구조"
slug: "ledger-view-page-js"
category: "development"
publishedAt: "2026-10-08"
summary: "모든 화면 JS가 따르는 App.xxx 모듈 패턴, 요소를 모아 두는 m$, 상태 settings, 주소 url, 이벤트 등록 bindEvent, 응답 처리 handleXxxResult의 역할을 settings.js로 설명합니다."
series: "ledger"
chapter: "view"
seriesOrder: 3
---
## 이 글에서 할 일

Ledger의 화면 JS 11개는 모두 같은 모양입니다. 커밋 "화면 JS 형식 통일: 이벤트 등록을 bindEvent로 분리, 응답 처리를 handleXxxResult로 분리"에서 맞췄습니다. 모양이 같으면 처음 여는 파일도 어디에 무엇이 있는지 바로 찾을 수 있습니다.

## 전체 모양

`resources/js/app/settings/settings.js` (뼈대)

```js
App.settings = (function(){
    var m$ = {                       // 1. 화면 요소
            payDay: document.getElementById('payDay'),
            cycleSave: document.getElementById('cycleSave'),
            ...
        },

        settings = {                 // 2. 화면 상태
            submitting: false,
            cycleDirty: false,
            ...
        },

        url = {                      // 3. 서버 주소
            load: contextPath + '/ledger/settings/load',
            cycleSave: contextPath + '/ledger/settings/cycle/save',
            ...
        },

        init = function(){           // 4. 시작
            bindEvent();
            load();
        },

        bindEvent = function(){      // 5. 이벤트 등록
            m$.cycleSave.addEventListener('click', saveCycle);
            ...
        },

        saveCycle = function(){ ... },          // 6. 동작
        handleCycleResult = function(res){ ... }; // 7. 응답 처리

    return{
        init: init                   // 밖에 공개하는 것은 init 하나
    };
}());

document.addEventListener('DOMContentLoaded', function(){
    App.settings.init();
});
```

## 모듈 패턴: 즉시 실행 함수

```js
App.settings = (function(){ ... return { init: init }; }());
```

`(function(){ ... }())`는 **정의하자마자 실행되는 함수**입니다. 안에서 `var`로 만든 변수와 함수는 밖에서 보이지 않습니다. `return`한 객체만 `App.settings`에 담깁니다.

- 화면 JS 여러 개가 같은 페이지에 있어도 이름(`url`, `load`)이 서로 부딪히지 않습니다.
- 밖에 공개하는 것은 `init` 하나뿐이라, 다른 코드가 내부 상태를 함부로 바꾸지 못합니다.
- 모든 화면이 `App` 객체 하나 아래에 들어가 전역 변수가 늘지 않습니다.

```js
// 전역 네임스페이스: 화면별 js는 App.xxx = (function(){ ... return {init:...}; })(); 형태로 등록한다.
var App = window.App || {};
```

## 1. m$: 화면 요소를 한곳에

```js
var m$ = {
    payDay: document.getElementById('payDay'),
    cycleSave: document.getElementById('cycleSave'),
    ...
};
```

요소를 처음에 한 번 찾아 이름을 붙여 둡니다. 코드 곳곳에서 `document.getElementById`를 반복하지 않고, JSP에서 id를 바꾸면 이 목록 한 곳만 고칩니다.

## 2. settings: 화면 상태

```js
settings = {
    submitting: false,
    /* 아직 저장하지 않은 행 입력("c12"·"p3" → 값). 한 행을 저장하면 목록 전체를 다시 그리므로 다른 행의 입력을 되살린다 */
    drafts: {},
    cycleDirty: false,
    openingDirty: false,
    cycle: null
}
```

"저장 중인가", "고쳤는데 아직 저장 안 했나"처럼 화면이 기억해야 할 값입니다. 예를 들어 `submitting`은 저장 버튼을 두 번 빨리 눌러 같은 요청이 두 번 가는 것을 막습니다.

## 3. url: 서버 주소

모든 Ajax 주소를 한곳에 둡니다. 이 객체만 보면 이 화면이 서버의 어떤 기능을 쓰는지 알 수 있고, Controller의 `@RequestMapping`과 1:1로 맞춰 볼 수 있습니다.

## 4~5. init과 bindEvent

```js
init = function(){
    bindEvent();
    applyExportRange('month');
    load();
},

bindEvent = function(){
    m$.cycleSave.addEventListener('click', saveCycle);
    m$.openingSave.addEventListener('click', saveOpening);
    ...
},
```

`init`은 순서만 적고, 이벤트 연결은 모두 `bindEvent`에 모읍니다. "이 버튼을 누르면 무엇이 실행되나?"를 찾을 때 `bindEvent` 하나만 보면 됩니다.

## 6~7. 요청과 응답 처리 분리

```js
loadCycle = function(){
    App.post(url.summary)
        .then(handleCycleResult)
        .catch(function(){});
},

handleCycleResult = function(res){
    App.result(res, { ok: function(){
        settings.cycle = {start: parseDay(res.data.cycleStart), end: parseDay(res.data.cycleEnd)};
        renderCycle();
    } });
},
```

- 요청을 보내는 함수와 응답을 처리하는 함수(`handleXxxResult`)를 나눕니다. 요청 함수는 짧아지고, 응답 처리는 이름으로 찾을 수 있습니다.
- `App.result`가 결과 코드별 공통 처리를 합니다. 성공(`00`)이면 `ok`를 실행하고, 입력 오류(`90`)면 서버 문장을 띄우고, 없음(`91`)이면 "찾을 수 없어요"를 띄웁니다([공통 Ajax 함수](/posts/ledger-view-ajax)).
- `.catch(function(){})`: 통신 오류는 `App.ajax`가 이미 안내 창을 띄웠으므로 여기서는 조용히 끝냅니다.

## 화면을 그릴 때: App.h

목록을 그릴 때는 HTML 문자열을 이어 붙이지 않고 `App.h`로 요소를 만듭니다.

`summary.js` (일부)

```js
App.h('tr', {className: r.kind === 'item' ? '' : 'subtotal'}, [
    App.h('th', {attrs: {scope: 'row'}, text: r.label})
])
```

```js
// App.h(tag, props, children): DOM 요소를 안전하게 만든다. 문자열 자식은 텍스트 노드(HTML 해석 없음).
```

`text`와 문자열 자식은 `textContent`로 들어가 HTML로 해석되지 않습니다. 사용자가 카테고리 이름에 `<img onerror=...>`를 넣어도 글자로 보일 뿐입니다. XSS 방어가 함수 안에 들어 있어서, 화면 코드를 쓰는 사람이 매번 신경 쓰지 않아도 됩니다.

## 정리

- 화면 JS는 `App.xxx = (function(){ ... return {init}; }())` 모듈로 만들어 이름 충돌과 전역 변수를 막습니다.
- `m$`(요소), `settings`(상태), `url`(주소)을 맨 위에 모읍니다.
- `bindEvent`에 이벤트를, `handleXxxResult`에 응답 처리를 모아 찾기 쉽게 합니다.
- 화면은 `App.h`로 그려 사용자 값이 HTML로 해석되지 않게 합니다.
