---
title: "nginx로 /p3 경로 연결하기"
slug: "ledger-deploy-nginx"
category: "development"
publishedAt: "2026-10-08"
summary: "저장소의 nginx 설정을 서버에 포함해 HTTPS 요청을 내부 Tomcat으로 전달하고, 로그인 요청 제한과 실제 접속을 확인합니다."
series: "ledger"
chapter: "deploy"
seriesOrder: 4
---
## 이 글에서 할 일

Docker로 실행한 Ledger는 서버 내부 `127.0.0.1:8081`에서 요청을 받습니다. 외부의 HTTPS 요청은 nginx가 받아 `/p3/` 경로를 Tomcat으로 전달합니다. 이처럼 앞의 서버가 요청을 대신 받아 뒤의 앱에 전달하는 구성을 **리버스 프록시**라고 합니다.

이 글은 도메인의 HTTPS `server` 블록과 인증서가 준비된 서버를 기준으로 합니다. 공통 HTTPS 준비는 [도메인과 인증서 글](/posts/oracle-cloud-domain-https-certbot)에서 다룹니다.

## 1단계. 설정 파일 배치

서버의 Ledger 저장소 루트에서 실행합니다.

```bash
sudo install -d /etc/nginx/snippets
sudo install -m 644 deploy/nginx-ledger-limit.conf /etc/nginx/conf.d/ledger-limit.conf
sudo install -m 644 deploy/nginx-ledger.conf /etc/nginx/snippets/ledger.conf
sudoedit /etc/nginx/sites-available/devgear.kr
```

이 저장소의 배포 대상은 `devgear.kr` 설정 파일입니다. 다른 도메인의 서버라면 그 도메인을 서비스하는 기존 설정 파일을 편집합니다.

기존 **443 `server` 블록 안**에 다음 한 줄을 추가합니다. 이미 있다면 중복 추가하지 않습니다.

`deploy/nginx-ledger.conf`의 머리말에 명시된 포함 지시문

```nginx
include /etc/nginx/snippets/ledger.conf;
```

`/etc/nginx/conf.d/*.conf`가 `http` 블록에서 포함되는 서버 구성이어야 합니다. 요청 제한 영역인 `limit_req_zone`은 `server`나 `location` 안에 넣지 않습니다.

## 전달 경로 읽기

`deploy/nginx-ledger.conf` (`/p3/`의 전달 부분)

```nginx
location /p3/ {
    proxy_pass         http://127.0.0.1:8081;
    proxy_http_version 1.1;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_read_timeout 60s;
```

위 블록은 원본의 앞부분 발췌입니다. 직접 배치할 때는 저장소의 **전체 파일**을 사용합니다.

`proxy_pass` 주소 뒤에 `/`를 붙이지 않으므로 원래 요청의 `/p3/` 경로가 전달됩니다. Tomcat도 `p3.war`로 배포했으므로 같은 경로에서 받습니다. `X-Real-IP`는 앱의 로그인 제한이 클라이언트 주소를 확인하는 데 쓰입니다.

URI가 없는 `proxy_pass`의 전달 방식은 [nginx 공식 문서](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)에서 확인했습니다(2026-10-07).

## 로그인 요청 제한

`deploy/nginx-ledger-limit.conf`

```nginx
limit_req_zone $binary_remote_addr zone=ledger_auth:1m rate=10r/m;
```

원본 프록시 파일은 `/p3/auth/login`과 `/p3/auth/signup`에 `ledger_auth` 영역을 적용합니다. `burst=5 nodelay`로 순간 요청을 허용할 여유를 두고, 제한을 넘으면 HTTP 429를 반환합니다. 앱 내부의 [로그인 시도 제한](/posts/ledger-auth-login-limit)과 별도로 nginx에서도 요청을 제한합니다.

각 제한 지시문의 역할은 [nginx 요청 제한 문서](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html)에서 확인했습니다(2026-10-07).

원본에는 요청 본문 64KB 제한과 `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` 응답 헤더도 있습니다. 세부 지시문은 [nginx 요청 제한](/posts/oracle-cloud-nginx-rate-limit), [보안 설정](/posts/oracle-cloud-nginx-security) 글에서 다룹니다.

## 2단계. 문법 검사 후 적용

서버에서 실행합니다.

```bash
sudo nginx -t
```

문법 검사에 성공했을 때만 새 설정을 적용합니다.

```bash
sudo systemctl reload nginx
```

실패했다면 출력된 파일과 줄을 확인해 수정합니다. 기존 HTTPS `server` 블록과 인증서 설정을 새 Ledger 블록으로 교체하지 않습니다.

## 3단계. 외부 접속 확인

브라우저에서 `https://도메인/p3/`에 접속합니다. 로그인 화면이 열리고, 로그인 후 대시보드와 거래를 조회할 수 있어야 합니다. `/p3`로 접속하면 원본의 별도 `location`이 `/p3/`로 이동시킵니다.

nginx에서 502가 나오면 먼저 서버 내부 `http://127.0.0.1:8081/p3/manifest.json`의 응답과 앱 로그를 확인합니다. 내부 응답은 정상인데 외부 경로만 다르면 `include` 위치와 `proxy_pass`를 확인합니다.
