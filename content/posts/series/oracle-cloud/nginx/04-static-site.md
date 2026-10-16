---
title: "정적 사이트 제공하기 (root, index, try_files)"
slug: "oracle-cloud-nginx-static-site"
category: "development"
publishedAt: "2026-10-07"
summary: "메인 페이지를 서비스하는 root, index, try_files 세 줄이 주소를 실제 파일 경로로 어떻게 바꾸는지 하나씩 따라가며 이해합니다."
series: "oracle-cloud"
chapter: "nginx"
seriesOrder: 4
---
## 이 글에서 할 일

`https://example.com/`의 메인 페이지를 서버의 `/srv/site` 폴더에 있는 HTML, CSS, JS 파일로 서비스합니다. 서버에서 코드를 실행하지 않고 파일을 그대로 보내므로 **정적 사이트**라고 합니다. 이 글에서는 nginx가 주소를 파일 경로로 바꾸는 규칙을 이해합니다.

## 설정

`/etc/nginx/sites-available/example.com` (443 server 블록 일부)

```nginx
    root /srv/site;
    index index.html;

    location / {
        try_files $uri $uri/ =404;
    }
```

## root: 주소를 파일 경로로 바꾸는 기준

`root`는 "주소의 경로 앞에 붙일 폴더"입니다.

```text
요청 주소:  https://example.com/css/style.css
경로 부분:                     /css/style.css
root:      /srv/site
실제 파일:  /srv/site/css/style.css     ← root + 경로
```

규칙이 단순해서 폴더 구조가 곧 주소 구조가 됩니다.

## index: 폴더를 요청했을 때 보여 줄 파일

`https://example.com/`처럼 폴더(`/`)를 요청하면 nginx는 그 폴더의 `index` 파일을 찾습니다.

```text
요청: /           → /srv/site/ 폴더 → index.html → /srv/site/index.html
```

## try_files: 순서대로 찾아보기

`try_files`는 적힌 순서대로 파일을 찾아보고, 처음 존재하는 것을 응답합니다.

```nginx
try_files $uri $uri/ =404;
```

| 순서 | 시도 | `/about` 요청일 때 |
|---|---|---|
| 1 | `$uri` (파일) | `/srv/site/about` 파일이 있나? |
| 2 | `$uri/` (폴더) | `/srv/site/about/` 폴더가 있나? 있으면 그 안의 `index.html` |
| 3 | `=404` | 둘 다 없으면 404 Not Found |

`$uri`는 요청 경로를 담은 nginx **변수**입니다. nginx는 `$`로 시작하는 변수를 많이 제공합니다(`$host`, `$request_uri`, `$remote_addr` 등).

## 따라 하기

### 1단계. 폴더와 첫 파일 만들기

```bash
sudo mkdir -p /srv/site
sudo chown ubuntu:ubuntu /srv/site
echo '<h1>Hello</h1>' > /srv/site/index.html
```

`chown`으로 폴더 주인을 `ubuntu`로 바꿔 두면, 이후 파일을 올릴 때 `sudo`가 필요 없습니다.

### 2단계. 설정 적용

server 블록에 위 설정을 넣고 [안전하게 적용](/posts/oracle-cloud-nginx-apply-safely)합니다.

### 3단계. 확인

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://example.com/               # 200
curl -s -o /dev/null -w "%{http_code}\n" https://example.com/no-such-file   # 404
```

## 파일을 바꾸면 바로 반영됩니다

정적 파일은 nginx가 요청마다 디스크에서 읽습니다. 그래서 `/srv/site`의 파일을 고치면 **nginx를 다시 시작하지 않아도** 바로 반영됩니다. 반대로 nginx **설정 파일**을 고쳤을 때는 [reload](/posts/oracle-cloud-nginx-apply-safely)가 필요합니다.

## 권한 확인

nginx는 `www-data` 사용자로 실행됩니다([설정 파일 구조](/posts/oracle-cloud-nginx-config-structure) 참고). 그래서 서비스할 폴더와 파일을 `www-data`가 읽을 수 있어야 합니다.

```bash
ls -ld /srv /srv/site
```

`drwxr-xr-x`처럼 마지막 세 글자(기타 사용자)에 `r-x`가 있으면 `www-data`도 읽고 들어갈 수 있습니다. 경로 중간의 폴더 하나라도 권한이 없으면 브라우저에는 **403 Forbidden**이 나옵니다. 홈 폴더(`/home/ubuntu`)는 다른 사용자가 들어갈 수 없게 되어 있는 경우가 많아서, 사이트 파일은 홈 폴더가 아닌 `/srv` 아래에 둡니다.

## 정리

- `root` + 요청 경로 = 실제 파일 경로입니다.
- 폴더를 요청하면 `index` 파일을 보여 줍니다.
- `try_files $uri $uri/ =404;`는 "파일 → 폴더 → 404" 순서로 찾습니다.
- 정적 파일 변경은 바로 반영되고, 설정 변경은 reload가 필요합니다.
