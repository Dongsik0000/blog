---
title: "하위 경로에 React 앱 배포하기 (alias와 SPA 대체 응답)"
slug: "oracle-cloud-nginx-spa-subpath"
category: "development"
publishedAt: "2026-10-07"
summary: "React 앱을 /app/ 아래에 배포하는 설정을 만들며 root와 alias의 차이, 새로고침하면 404가 나는 SPA 문제를 try_files로 해결하는 원리, 앱 쪽 base 설정을 설명합니다."
series: "oracle-cloud"
chapter: "nginx"
seriesOrder: 5
---
## 이 글에서 할 일

React로 만든 앱을 `https://example.com/app/`에서 서비스합니다. 이 글에서는 이 설정의 네 가지 핵심을 이해합니다.

1. `root` 대신 `alias`를 쓰는 이유
2. SPA(Single Page Application)에서 새로고침하면 404가 나는 문제
3. `/app`(슬래시 없음)을 `/app/`으로 보내는 이유
4. 앱도 자기가 `/app/` 아래에 있다는 것을 알아야 하는 이유

## 설정

`/etc/nginx/snippets/react-app.conf`

```nginx
location = /app {
    return 301 /app/;
}
location ^~ /app/ {
    alias /srv/react-app/dist/;
    index index.html;
    try_files $uri $uri/ /app/index.html;
}
```

## 1. root와 alias의 차이

React 앱을 빌드하면 결과물이 `dist/` 폴더에 생깁니다. 이 폴더를 `/app/` 주소에 연결해야 합니다.

`root`를 쓰면 **주소 경로 전체**가 폴더 뒤에 붙습니다.

```text
root /srv/react-app/dist;   일 때
요청 /app/index.html  →  /srv/react-app/dist/app/index.html   ✗ (app 폴더는 없음)
```

`alias`를 쓰면 **location에 맞은 부분(`/app/`)을 alias 경로로 바꿔치기**합니다.

```text
alias /srv/react-app/dist/;   일 때
요청 /app/index.html  →  /srv/react-app/dist/index.html       ✓
```

| | root | alias |
|---|---|---|
| 동작 | root + 전체 경로 | location 부분을 alias로 교체 |
| 어울리는 곳 | 주소 구조와 폴더 구조가 같을 때 | 주소의 하위 경로를 다른 폴더에 연결할 때 |

`alias`를 쓸 때는 **location 경로와 alias 경로의 끝 슬래시를 맞춥니다**(`/app/`과 `.../dist/`). 하나만 슬래시가 있으면 경로가 어긋납니다.

## 2. SPA와 새로고침 404

### SPA가 페이지를 바꾸는 방법

React 앱은 HTML 파일이 `index.html` **하나뿐**입니다. `/app/new`, `/app/diary/3` 같은 "페이지"는 실제 파일이 아니라, 브라우저 안의 JavaScript(React Router)가 주소를 보고 화면을 바꿔 그린 것입니다.

```text
dist/
├── index.html        ← 유일한 HTML
└── assets/           ← JS, CSS, 이미지
```

### 문제: 새로고침하거나 주소를 직접 입력하면

앱 안에서 링크를 누를 때는 문제가 없습니다. 하지만 `/app/diary/3`에서 **새로고침**하면 브라우저가 서버에 `/app/diary/3`을 요청합니다. 서버에는 그런 파일이 없으므로 404가 납니다.

### 해결: 없으면 index.html을 준다

```nginx
try_files $uri $uri/ /app/index.html;
```

| 순서 | `/app/assets/index-abc.js` 요청 | `/app/diary/3` 요청 |
|---|---|---|
| `$uri` | 파일 있음 → 응답 | 파일 없음 |
| `$uri/` | | 폴더 없음 |
| `/app/index.html` | | **index.html 응답** → React가 주소를 보고 일기 3번 화면을 그림 |

마지막 항목이 `=404` 대신 `/app/index.html`인 것이 [정적 사이트](/posts/oracle-cloud-nginx-static-site)와의 차이입니다. 이것을 **SPA 대체 응답**(fallback)이라고 합니다.

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://example.com/app/no/such/route   # 200 (index.html)
```

이 방식에서는 정말 없는 주소도 200으로 응답합니다. 그래서 "없는 페이지" 화면은 React 앱 안에서 직접 만들어야 합니다(React Router의 `path="*"` 경로).

## 3. `/app`을 `/app/`으로 보내는 이유

```nginx
location = /app {
    return 301 /app/;
}
```

브라우저는 상대 경로를 **마지막 슬래시 기준**으로 해석합니다. `/app`에서 `assets/main.js`를 불러오면 `/assets/main.js`가 되고, `/app/`에서 불러오면 `/app/assets/main.js`가 됩니다. 슬래시가 없으면 파일을 엉뚱한 곳에서 찾게 되므로, 슬래시 있는 주소로 통일합니다.

## 4. 앱 쪽 설정: base 경로

nginx만 설정해서는 끝나지 않습니다. React 앱은 기본적으로 자기가 사이트 맨 위(`/`)에 있다고 가정하고 빌드됩니다. 그러면 `index.html` 안의 자원 경로가 `/assets/...`가 되어, `/app/` 아래에 올리면 JS와 CSS를 찾지 못하고 빈 화면이 나옵니다.

Vite로 만든 앱이라면 빌드할 때 base 경로를 알려 줍니다.

```bash
npm run build -- --base=/app/
```

또는 `vite.config.js`에 적어 두면 매번 옵션을 붙이지 않아도 됩니다.

```js
export default defineConfig({
  base: "/app/",
  plugins: [react()],
});
```

빌드 결과를 확인하면 자원 경로가 `/app/`으로 시작해야 합니다.

```bash
grep -o 'src="[^"]*"' dist/index.html
```

```text
src="/app/assets/index-BOu5q-K3.js"
```

React Router도 같은 경로를 따르도록 `basename`을 지정합니다.

```jsx
<BrowserRouter basename={import.meta.env.BASE_URL}>
```

`import.meta.env.BASE_URL`은 Vite가 빌드할 때 정한 base 경로(`/app/`)입니다. 이렇게 하면 base를 바꿀 때 한 곳만 고치면 됩니다.

## `^~`를 쓴 이유와 주의점

`^~ /app/`은 "이 접두사가 맞으면 정규식 location을 검사하지 않는다"는 뜻입니다. 정규식 location이 `/app/` 요청을 가로채지 못하게 합니다. 대신 숨김 파일 차단 정규식도 건너뛰므로, 연결된 폴더(`dist/`)에 숨김 파일이 없어야 합니다. 빌드 결과물만 담긴 폴더라면 문제가 없습니다. 자세한 우선순위는 [location 고르는 순서](/posts/oracle-cloud-nginx-server-location)에 있습니다.

## 정리

- 하위 경로를 다른 폴더에 연결할 때는 `alias`를 쓰고, 끝 슬래시를 맞춥니다.
- SPA는 `try_files ... /app/index.html;`로 없는 경로에 `index.html`을 돌려줘야 새로고침 404가 나지 않습니다.
- `/app`은 `/app/`으로 301 이동시켜 상대 경로가 어긋나지 않게 합니다.
- 앱도 `base`와 `basename`으로 하위 경로를 알아야 합니다.
