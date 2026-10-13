// 전역 네임스페이스: 화면별 js는 App.xxx = (function(){ ... return {init:...}; }()); 형태로 등록한다.
// contextPath 는 layout.tag 의 <script>var contextPath = "...";</script> 에서 온다.
var App = window.App || {};

// 결과 코드. Constants.java 와 같은 값을 유지한다.
App.CODE = {
    SUCCESS: '00',
    FAIL: '99',
    INVALID: '90',
    NOT_FOUND: '91',
    PASSWORD_MISMATCH: '92',
    LIMITED: '93',
    DUPLICATE: '94',
    UNAUTHORIZED: '95'
};

// App.ajax(url, data, opts): 서버 공통 호출. fetch 기반, JSON 요청/응답 고정. opts.method 기본 POST.
// HTTP 오류(4xx·5xx)면 Error(err.status)를 던진다. 블로그에는 공통 모달이 없어서 오류 문구는 부른 쪽이 보여 준다.
//
// 사용 예)
//   App.post(contextPath + '/comment/list', { slug: 'my-post' })
//       .then(function(res){ if (res.code === App.CODE.SUCCESS) { ... } })
//       .catch(function(){ /* 네트워크·서버 오류 */ });
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

    if (method === 'GET') {
        if (data) url += (url.indexOf('?') === -1 ? '?' : '&') + new URLSearchParams(data).toString();
    } else {
        fetchOpt.body = JSON.stringify(data || {});
    }

    return fetch(url, fetchOpt).then(function (res) {
        if (!res.ok) {
            var err = new Error('HTTP ' + res.status);
            err.status = res.status;
            throw err;
        }
        return res.json();
    });
};

// App.get / App.post: App.ajax 의 GET/POST 별칭
App.get = function (url, data, opts) {
    return App.ajax(url, data, Object.assign({}, opts, {method: 'GET'}));
};
App.post = function (url, data, opts) {
    return App.ajax(url, data, Object.assign({}, opts, {method: 'POST'}));
};

// App.upload(url, file): 파일 하나를 multipart(필드 이름 file)로 올린다. Content-Type 은 브라우저가 boundary 와 함께 정한다.
// 파일이 서버 한도를 넘으면 HTTP 413 Error 를 던진다.
App.upload = function (url, file) {
    var form = new FormData();
    form.append('file', file);
    return fetch(url, {
        method: 'POST',
        headers: {'X-Requested-With': 'XMLHttpRequest'},
        credentials: 'same-origin',
        body: form
    }).then(function (res) {
        if (!res.ok) {
            var err = new Error('HTTP ' + res.status);
            err.status = res.status;
            throw err;
        }
        return res.json();
    });
};

// App.logout(): 관리자 로그아웃 후 홈으로 (요청이 실패해도 이동한다. 세션은 4시간 뒤 스스로 끝난다)
App.logout = function () {
    return App.post(contextPath + '/auth/logout', {})
        .catch(function () {})
        .then(function () {
            window.location.href = contextPath + '/';
        });
};

// App.isEmpty(v): null/undefined/공백 문자열/빈 배열 여부
App.isEmpty = function (v) {
    if (v === null || v === undefined) return true;
    if (typeof v === 'string') return v.trim() === '';
    if (Array.isArray(v)) return v.length === 0;
    return false;
};
