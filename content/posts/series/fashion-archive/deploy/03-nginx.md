---
title: "nginx로 /p1/을 연결하고 사진을 직접 보내기"
slug: "fashion-archive-deploy-nginx"
category: "development"
publishedAt: "2026-10-08"
summary: "저장소의 deploy/nginx-fashion-archive.conf를 읽으며 /p1을 /p1/로 보내는 설정, 경로를 바꾸지 않는 proxy_pass와 전달 헤더, 업로드 크기에 맞춘 client_max_body_size, /uploads/를 Tomcat 없이 디스크에서 바로 보내는 alias와 nosniff, 캐시 설정을 설명합니다."
series: "fashion-archive"
chapter: "deploy"
seriesOrder: 3
---
## 이 글에서 할 일

외부 요청은 Tomcat이 아니라 nginx가 받습니다. nginx는 `/p1/` 요청을 Tomcat으로 넘기고, `/uploads/` 사진 요청은 Tomcat을 거치지 않고 디스크에서 직접 보냅니다. 설정 원본은 저장소의 `deploy/nginx-fashion-archive.conf`이고, 서버의 nginx 사이트 설정(443 server 블록)에 넣어 씁니다.

nginx 자체의 설치와 HTTPS, 설정 적용 절차는 [Oracle Cloud 시리즈의 nginx 장](/posts/oracle-cloud-nginx-what-is-nginx)에 있습니다.

## 전체

`deploy/nginx-fashion-archive.conf`

```nginx
location = /p1 {
    return 301 /p1/;
}

location /p1/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 12m;
}

location /uploads/ {
    alias /srv/fashion-uploads/;
    add_header X-Content-Type-Options nosniff always;
    expires 7d;
}
```

## 1. /p1 → /p1/

```nginx
location = /p1 {
    return 301 /p1/;
}
```

끝 슬래시 없는 `/p1`로 들어오면 `/p1/`로 영구 이동시킵니다. 브라우저는 상대 경로를 마지막 슬래시 기준으로 풀기 때문에, 주소를 한 가지로 맞춰 둡니다.

## 2. /p1/ → Tomcat

```nginx
location /p1/ {
    proxy_pass http://127.0.0.1:8080;
    ...
}
```

`proxy_pass` 주소 뒤에 경로가 없으므로, 요청 경로를 **바꾸지 않고 그대로** 넘깁니다.

```text
브라우저:  https://도메인/p1/photos/15
Tomcat:   http://127.0.0.1:8080/p1/photos/15
```

Tomcat 안에서도 앱이 `/p1` 컨텍스트에 있으므로([Dockerfile](/posts/fashion-archive-deploy-dockerfile)) 주소가 그대로 맞습니다. 앱이 만드는 링크와 redirect(`/p1/login?next=...`)도 바꿀 필요가 없습니다.

### 전달 헤더

| 헤더 | Tomcat(앱)이 알게 되는 것 |
|---|---|
| `Host $host` | 사용자가 접속한 도메인 |
| `X-Real-IP $remote_addr` | 실제 방문자 IP. 앱의 `RequestUtil`이 가입 시도 제한에 씀 ([가입](/posts/fashion-archive-auth-signup)) |
| `X-Forwarded-For` | 거쳐 온 IP 목록 |
| `X-Forwarded-Proto $scheme` | 원래 요청이 `https`였다는 사실 |

`X-Real-IP`는 nginx가 직접 넣는 값이고, 앱은 요청이 `127.0.0.1`(nginx)에서 왔을 때만 이 헤더를 믿습니다.

### 업로드 크기

```nginx
client_max_body_size 12m;
```

nginx의 기본 요청 본문 한도는 1MB라, 이 줄이 없으면 사진 대부분이 413 오류로 거절됩니다. `web.xml`의 `max-request-size`(12MB)와 같게 맞췄습니다([multipart](/posts/fashion-archive-upload-multipart)).

## 3. /uploads/ → 디스크에서 직접

```nginx
location /uploads/ {
    alias /srv/fashion-uploads/;
    add_header X-Content-Type-Options nosniff always;
    expires 7d;
}
```

DB의 `image_url`은 `https://도메인/uploads/{아이디}/{파일}`입니다. 이 요청은 Tomcat으로 가지 않고 nginx가 디스크 파일을 바로 보냅니다.

| 줄 | 뜻 |
|---|---|
| `alias /srv/fashion-uploads/` | `/uploads/a/b.jpg` → `/srv/fashion-uploads/a/b.jpg`. location 부분을 폴더로 바꿔치기 |
| `X-Content-Type-Options nosniff` | 브라우저가 파일 내용을 보고 종류를 추측하지 않음. 사진으로 위장한 HTML이 실행되지 않게 함 |
| `always` | 오류 응답(404 등)에도 헤더를 붙임 |
| `expires 7d` | 브라우저가 7일 동안 캐시. 사진 파일 이름은 UUID라 내용이 바뀌지 않으므로 오래 캐시해도 됨 |

`alias` 경로와 location 경로의 끝 슬래시를 맞춥니다(`/uploads/`와 `/srv/fashion-uploads/`). `alias`와 `root`의 차이는 [Oracle Cloud 시리즈의 하위 경로 글](/posts/oracle-cloud-nginx-spa-subpath)에 있습니다.

### Tomcat을 거치지 않는 이유

- 사진은 정적 파일이라 Java 코드가 할 일이 없습니다. nginx가 보내는 쪽이 빠르고 Tomcat 메모리를 쓰지 않습니다.
- 파일을 쓰는 것은 Tomcat(컨테이너), 읽는 것은 nginx(`www-data` 사용자)입니다. 그래서 앱이 저장할 때 폴더 755, 파일 644 권한을 직접 지정합니다([디스크 저장](/posts/fashion-archive-upload-local-storage)).

`/uploads/`는 로그인 확인을 거치지 않습니다. 사진 주소를 아는 사람은 사진을 볼 수 있습니다. 주소의 파일 이름이 무작위 UUID라 주소를 모르면 찾을 수 없고, 사진 목록과 상세 화면은 로그인한 본인에게만 보입니다.

## 확인

```bash
curl -sI https://도메인/p1 | grep -E '^HTTP|^Location'
curl -sI https://도메인/p1/ | grep -E '^HTTP|^Location'
```

| 요청 | 기대 |
|---|---|
| `/p1` | `301`, `Location: .../p1/` |
| `/p1/` (로그인 전) | `302`, `Location: .../p1/login?next=...` |

nginx 설정을 바꾸는 절차(백업, `nginx -t`, reload)는 [안전하게 적용하기](/posts/oracle-cloud-nginx-apply-safely)를 따릅니다.

## 정리

- `/p1`은 `/p1/`로 보내고, `/p1/`은 경로를 바꾸지 않고 `127.0.0.1:8080`의 Tomcat으로 넘깁니다.
- 전달 헤더로 실제 방문자 IP와 HTTPS 여부를 앱에 알리고, 업로드를 위해 본문 한도를 12MB로 올립니다.
- `/uploads/`는 `alias`로 디스크에서 직접 보내고, `nosniff`와 7일 캐시를 붙입니다.
