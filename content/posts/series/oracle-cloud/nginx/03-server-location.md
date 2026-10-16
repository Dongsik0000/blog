---
title: "server 블록과 location 고르는 순서"
slug: "oracle-cloud-nginx-server-location"
category: "development"
publishedAt: "2026-10-07"
summary: "nginx가 요청 하나를 받았을 때 어떤 server 블록과 어떤 location을 고르는지, =, ^~, ~, 일반 접두사의 우선순위를 예시 설정으로 정리합니다."
series: "oracle-cloud"
chapter: "nginx"
seriesOrder: 3
---
## 이 글에서 할 일

nginx 설정을 고칠 때 가장 헷갈리는 것은 "이 요청이 **어느 블록**에서 처리되는가"입니다. 이 글에서는 nginx의 선택 규칙을 이해하고, 예시 설정에서 location이 어떤 순서로 선택되는지 따라가 봅니다.

## 1단계: server 블록 고르기

요청이 오면 nginx는 먼저 **포트**와 **도메인**(Host)으로 server 블록을 고릅니다.

HTTPS까지 적용한 사이트는 보통 server 블록이 세 개입니다([HTTP를 HTTPS로 보내기](/posts/oracle-cloud-domain-https-renewal-redirect) 참고).

| 블록 | listen | server_name | 하는 일 |
|---|---|---|---|
| A | 80 `default_server` | `_` | 도메인 없는 HTTP 요청 → `https://example.com`으로 이동 |
| B | 80 | `www.example.com example.com` | HTTP 요청 → 같은 도메인의 HTTPS로 이동 |
| C | 443 ssl | `www.example.com example.com` | **실제 서비스** |

- `http://example.com/` → 80번이고 이름이 맞으므로 **B**
- `http://공인IP/` → 80번인데 맞는 이름이 없으므로 `default_server`인 **A**
- `https://example.com/shop/` → 443번이므로 **C**

결국 실제 서비스는 모두 C 블록에서 처리됩니다. 이제 C 블록 안에서 location을 고릅니다.

## 2단계: location 고르기

location 앞에 붙는 기호에 따라 우선순위가 다릅니다.

| 표기 | 이름 | 뜻 |
|---|---|---|
| `location = /shop` | 정확히 일치 | 경로가 정확히 `/shop`일 때만. **가장 먼저** 확인 |
| `location ^~ /app/` | 우선 접두사 | `/app/`로 시작하면 선택하고 **정규식 검사를 건너뜀** |
| `location ~ ^/shop/login$` | 정규식 | 정규식에 맞으면 선택. 대소문자 구분 (`~*`는 구분 안 함) |
| `location /shop/` | 일반 접두사 | `/shop/`로 시작하면 후보 |

nginx의 실제 선택 순서는 다음과 같습니다.

```text
① = 정확히 일치하는 location이 있으면 → 바로 선택, 끝
② 일반 접두사와 ^~ 중에서 "가장 길게 일치하는 것"을 찾아 기억
   그것이 ^~ 이면 → 선택, 끝 (정규식 안 봄)
③ 정규식 location을 설정 파일에 적힌 순서대로 검사
   처음 맞는 정규식이 있으면 → 선택, 끝
④ 맞는 정규식이 없으면 → ②에서 기억한 접두사 location 선택
```

## 예시 설정으로 따라가기

[앞 글](/posts/oracle-cloud-nginx-what-is-nginx)의 구성을 C 블록에 넣으면 location은 다음과 같습니다. 각 location의 내용은 뒤의 글에서 하나씩 설명합니다.

```nginx
location ~ /\.(?!well-known/) { deny all; }       # 숨김 파일 차단
location = /app { return 301 /app/; }
location ^~ /app/ { alias /srv/react-app/dist/; ... }
location = /docs { return 301 /docs/; }
location /docs/ { alias /srv/docs/dist/; ... }
location = /shop { return 301 /shop/; }
location /shop/ {
    proxy_pass http://127.0.0.1:8080;
    ...
    location ~ ^/shop/login$ { limit_req ...; ... }
}
location /uploads/ { alias /srv/uploads/; ... }
location / { try_files $uri $uri/ =404; }
```

| 요청 경로 | 선택되는 location | 이유 |
|---|---|---|
| `/shop` | `= /shop` | ① 정확히 일치 |
| `/shop/products` | `/shop/` | ② 가장 긴 접두사, ③ 맞는 정규식 없음 |
| `/shop/login` | `/shop/` 안의 `~ ^/shop/login$` | 안쪽에 중첩된 정규식이 맞음 |
| `/app/diary/3` | `^~ /app/` | ② `^~`라서 정규식 검사 없이 확정 |
| `/.git/config` | `~ /\.(?!well-known/)` | ③ 정규식이 맞아 `deny all` |
| `/css/style.css` | `/` | ② 가장 짧지만 유일하게 맞는 접두사 |

### `/` location이 "나머지 전부"인 이유

모든 경로는 `/`로 시작하므로 `location /`는 항상 후보가 됩니다. 하지만 가장 짧은 접두사라서 다른 location이 모두 맞지 않을 때만 선택됩니다. 그래서 "기본값" 역할을 합니다.

### `^~`를 쓸 때 주의할 점

`^~ /app/`은 정규식 검사를 건너뜁니다. 그래서 `/app/` 아래에서는 **숨김 파일 차단 정규식도 적용되지 않습니다.** 연결한 폴더가 빌드 결과물만 담긴 `dist/`라면 숨김 파일이 없어 괜찮지만, 소스 코드 폴더처럼 `.git`이나 `.env`가 있을 수 있는 곳을 `^~`로 열면 그 파일들이 그대로 노출됩니다.

## 직접 확인하는 방법

어느 location이 응답했는지는 응답 코드로 짐작할 수 있습니다.

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://example.com/.git/config   # 403: 숨김 파일 차단
curl -s -o /dev/null -w "%{http_code}\n" https://example.com/shop          # 301: = /shop
curl -s -o /dev/null -w "%{http_code}\n" https://example.com/app/no/route  # 200: SPA 대체 응답
```

## 정리

- 먼저 **포트 + 도메인**으로 server 블록을 고르고, 그 안에서 location을 고릅니다.
- location 우선순위: **`=` → 가장 긴 접두사(`^~`면 확정) → 정규식(순서대로) → 기억한 접두사**
- `location /`는 아무것도 맞지 않을 때의 기본값입니다.
- `^~`는 정규식 기반 보안 규칙까지 건너뛰므로 신중히 씁니다.
