---
title: "하위 경로에 정적 사이트 배포하기 (/docs/)"
slug: "oracle-cloud-nginx-static-subpath"
category: "development"
publishedAt: "2026-10-07"
summary: "Astro나 Hugo처럼 페이지마다 HTML을 만드는 정적 사이트를 /docs/ 아래에 연결하는 설정과, SPA와의 차이, root를 바꿀 때 기존 사이트가 사라지는 함정을 정리합니다."
series: "oracle-cloud"
chapter: "nginx"
seriesOrder: 6
---
## 이 글에서 할 일

Astro, Hugo, Jekyll 같은 정적 사이트 생성기로 만든 사이트를 `https://example.com/docs/`에서 서비스합니다. React 앱과 달리 **페이지마다 HTML 파일이 따로** 있어서 설정이 조금 다릅니다.

## 설정

`/etc/nginx/sites-available/example.com` (443 server 블록 일부)

```nginx
    location = /docs { return 301 /docs/; }
    location /docs/ {
        alias /srv/docs/dist/;
        index index.html;
    }
```

`alias`와 `/docs` → `/docs/` 이동은 [React 앱 글](/posts/oracle-cloud-nginx-spa-subpath)과 같은 이유로 씁니다.

## SPA와 무엇이 다른가

정적 사이트 생성기는 빌드할 때 페이지마다 폴더와 `index.html`을 만듭니다.

```text
dist/
├── index.html                        → /docs/
├── guide/index.html                  → /docs/guide/
└── guide/install/index.html          → /docs/guide/install/
```

모든 주소에 실제 파일이 있으므로 SPA처럼 "없으면 index.html"이 필요 없습니다. 오히려 없는 주소는 **404를 돌려주는 것이 맞습니다.** 그래서 `try_files`의 대체 응답을 쓰지 않습니다.

| | React 앱 `/app/` | 정적 사이트 `/docs/` |
|---|---|---|
| HTML 파일 | `index.html` 하나 | 페이지마다 하나 |
| 화면 전환 | 브라우저 JS가 처리 | 서버의 HTML 파일 |
| 없는 주소 | `index.html`(200) | 404 |

### 슬래시 없는 주소의 처리

`/docs/guide`(끝 슬래시 없음)로 요청하면, nginx는 같은 이름의 **폴더**가 있음을 알고 자동으로 `/docs/guide/`로 301 이동시킵니다. 그다음 `index` 지시어에 따라 그 폴더의 `index.html`을 응답합니다.

```bash
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" https://example.com/docs/guide
```

```text
301 https://example.com/docs/guide/
```

## 사이트 쪽도 하위 경로를 알아야 합니다

빌드된 HTML 안의 링크와 CSS 경로가 `/docs/`로 시작해야 합니다. 그렇지 않으면 `/guide/`처럼 루트 기준 주소를 찾다가 404가 납니다. 도구마다 설정 이름이 다릅니다.

| 도구 | 설정 |
|---|---|
| Astro | `astro.config.mjs`의 `base: "/docs"` |
| Vite | `vite.config.js`의 `base: "/docs/"` |
| Hugo | `baseURL = "https://example.com/docs/"` |

빌드한 뒤 `dist/index.html`을 열어 CSS, JS 경로가 `/docs/`로 시작하는지 확인합니다.

## 주의: 루트를 바꾸면 기존 사이트가 사라진다

새 사이트를 맨 위 주소(`/`)에 두고 싶어서 `root`를 바꾸는 경우가 있습니다.

```nginx
    root /srv/docs/dist;   # 기존: root /srv/site;
```

설정 자체는 문제없이 적용되지만, 그 순간 **기존 메인 사이트(`/srv/site`)로 가는 주소가 하나도 남지 않습니다.** 파일은 디스크에 그대로 있어도 어떤 주소로도 접근할 수 없게 됩니다.

- `root`나 `location /`를 바꾸는 것은 **"지금 그 주소에서 보이는 모든 것"을 바꾸는 일**입니다.
- 바꾸기 전에 "지금 이 경로로 무엇이 서비스되고 있고, 바꾼 뒤 그것은 어디로 가는가"를 확인합니다.
- 기존 사이트를 유지하려면 새 사이트를 이 글처럼 하위 경로로 추가합니다.
- 설정 파일을 백업해 두면 바로 되돌릴 수 있습니다. [안전하게 적용하기](/posts/oracle-cloud-nginx-apply-safely)의 절차를 지킵니다.

## 정리

- 페이지마다 HTML이 있는 정적 사이트는 `alias` + `index`만으로 충분하고, 없는 주소는 404가 맞습니다.
- 사이트 빌드 설정도 하위 경로(`base`)를 알아야 합니다.
- `root`를 바꾸기 전에 그 주소에서 지금 무엇이 서비스되는지 확인합니다.
