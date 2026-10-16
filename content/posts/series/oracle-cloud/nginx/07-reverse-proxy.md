---
title: "리버스 프록시로 Tomcat 연결하기"
slug: "oracle-cloud-nginx-reverse-proxy"
category: "development"
publishedAt: "2026-10-07"
summary: "Java 앱을 /shop/ 경로로 연결하는 설정을 한 줄씩 읽으며 proxy_pass 경로 규칙, 전달 헤더의 의미, 요청 크기 제한, 업로드 파일을 nginx가 직접 보내는 방법을 설명합니다."
series: "oracle-cloud"
chapter: "nginx"
seriesOrder: 7
---
## 이 글에서 할 일

로그인과 데이터베이스가 있는 Java 앱은 Tomcat이 실행합니다. 이 글에서는 nginx가 받은 요청을 Tomcat으로 넘기는 **리버스 프록시** 설정을 만듭니다. Tomcat은 같은 서버의 `127.0.0.1:8080`에서 실행 중이라고 가정합니다.

## 전체 구조

```text
브라우저 ──https──▶ nginx :443 ──http──▶ Tomcat 127.0.0.1:8080
```

nginx와 Tomcat 사이는 같은 서버 안이라 HTTPS가 필요 없습니다. 암호화는 nginx에서 끝납니다(**HTTPS 종료**).

## 설정

`/etc/nginx/snippets/shop.conf`

```nginx
location = /shop {
    return 301 /shop/;
}
location /shop/ {
    proxy_pass         http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_read_timeout 60s;
    client_max_body_size 12m;
}
location /uploads/ {
    alias /srv/uploads/;
    add_header X-Content-Type-Options nosniff always;
    expires 7d;
}
```

## proxy_pass: 어디로 넘기나

```nginx
proxy_pass http://127.0.0.1:8080;
```

요청을 같은 서버의 8080 포트(Tomcat)로 넘깁니다.

여기서 아주 중요한 규칙이 있습니다. **`proxy_pass` 주소 뒤에 경로가 있느냐 없느냐**에 따라 Tomcat이 받는 경로가 달라집니다.

| 설정 | 브라우저 요청 | Tomcat이 받는 경로 |
|---|---|---|
| `proxy_pass http://127.0.0.1:8080;` (경로 없음) | `/shop/login` | `/shop/login` (그대로) |
| `proxy_pass http://127.0.0.1:8080/;` (경로 `/` 있음) | `/shop/login` | `/login` (location 부분 `/shop/`이 `/`로 바뀜) |

이 글은 경로 없이 넘기므로 Tomcat은 `/shop/...`을 그대로 받습니다. 그래서 Tomcat 쪽에서도 앱이 `/shop` 경로에 배포되어 있어야 합니다. Tomcat은 `webapps/` 폴더의 war 파일 이름을 경로로 씁니다.

| war 파일 이름 | 앱 경로 |
|---|---|
| `webapps/ROOT.war` | `/` |
| `webapps/shop.war` | `/shop` |

그래서 Docker 이미지를 만들 때 war 파일을 `shop.war`라는 이름으로 넣습니다.

```dockerfile
COPY target/myapp.war /usr/local/tomcat/webapps/shop.war
```

nginx 경로와 Tomcat 경로를 같게 맞추면 앱이 만드는 링크와 쿠키 경로가 어긋나지 않습니다. 경로를 서로 다르게 두면(예: `proxy_pass .../`로 `/shop/`을 떼고 `ROOT.war`로 배포) 앱이 `/login`으로 리다이렉트할 때 브라우저가 `/shop/`을 잃어버리는 문제가 생기기 쉽습니다.

## proxy_set_header: Tomcat에게 원래 요청 정보 알려 주기

nginx가 대신 요청하면 Tomcat 입장에서는 "요청한 사람 = nginx(127.0.0.1)"가 됩니다. 원래 정보를 헤더에 담아 함께 넘깁니다.

| 헤더 | 값 | Tomcat(앱)이 알게 되는 것 |
|---|---|---|
| `Host` | `$host` | 사용자가 접속한 도메인 (`example.com`) |
| `X-Real-IP` | `$remote_addr` | 실제 방문자 IP |
| `X-Forwarded-For` | `$proxy_add_x_forwarded_for` | 거쳐 온 IP 목록 (기존 값 + 방문자 IP) |
| `X-Forwarded-Proto` | `$scheme` | 원래 요청이 `https`였다는 사실 |

`X-Forwarded-Proto`가 특히 중요합니다. Tomcat은 nginx로부터 `http`로 요청을 받기 때문에, 이 헤더가 없으면 앱이 "지금 http로 접속했다"고 잘못 알고 리다이렉트 주소를 `http://`로 만들 수 있습니다.

## 그 밖의 줄

| 줄 | 뜻 |
|---|---|
| `proxy_http_version 1.1;` | Tomcat과 HTTP/1.1로 통신 (연결 재사용 등) |
| `proxy_read_timeout 60s;` | Tomcat이 60초 안에 응답하지 않으면 끊고 504 오류 |
| `client_max_body_size 12m;` | 요청 본문 최대 12MB. 넘으면 413 오류 |

`client_max_body_size`는 앱 성격에 맞게 정합니다. 사진 업로드가 있으면 사진 크기보다 넉넉하게, JSON만 주고받는 앱이면 `64k`처럼 작게 잡아 큰 요청으로 서버를 괴롭히는 공격을 막습니다. 기본값은 1MB라서, 업로드 기능이 있는데 이 줄을 빠뜨리면 큰 사진을 올릴 때 413 오류가 납니다.

## 업로드 파일은 nginx가 직접

사용자가 사진을 올리면 Tomcat이 서버 디스크에 저장합니다. 앱을 Docker로 실행한다면 서버의 `/srv/uploads` 폴더를 컨테이너 안에 연결해 둡니다([compose 글](/posts/oracle-cloud-docker-compose) 참고).

저장된 사진을 **보여 줄 때**는 Tomcat을 거치지 않고 nginx가 `/uploads/` 경로로 직접 보냅니다. Java를 거치지 않으니 빠르고 Tomcat의 부담도 줄어듭니다.

| 줄 | 뜻 |
|---|---|
| `alias /srv/uploads/;` | `/uploads/a.jpg` → `/srv/uploads/a.jpg` |
| `add_header X-Content-Type-Options nosniff always;` | 브라우저가 파일 종류를 추측하지 않게 함. 이미지로 위장한 스크립트 실행을 막습니다 |
| `expires 7d;` | 브라우저가 7일 동안 캐시. 같은 사진을 다시 받지 않아 빠릅니다 |

`/srv/uploads`는 nginx(`www-data`)가 읽을 수 있어야 합니다.

## Tomcat을 외부에서 안 보이게

Tomcat은 `127.0.0.1`에서만 듣도록 설정합니다(방법은 [compose 글](/posts/oracle-cloud-docker-compose)에서 다룹니다). 제대로 되었는지 확인합니다.

```bash
sudo ss -ltnp | grep java
```

```text
[::ffff:127.0.0.1]:8080  ... "java"
```

주소가 `127.0.0.1`이면 외부에서 `공인IP:8080`으로는 접속할 수 없고, 반드시 nginx의 HTTPS를 거쳐야 합니다. `0.0.0.0:8080`이나 `*:8080`이 보이면 모든 주소에서 듣고 있는 것이므로 설정을 고칩니다.

## 확인하기

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/shop/   # Tomcat 직접 (서버 안에서)
curl -s -o /dev/null -w "%{http_code}\n" https://example.com/shop/     # nginx를 거쳐서
```

두 결과가 같으면 프록시가 정상입니다. 로그인이 필요한 앱이라면 둘 다 `302`(로그인 화면으로 이동)가 나올 수 있습니다. 서버 안에서는 되는데 nginx를 거치면 **502 Bad Gateway**가 난다면 nginx가 Tomcat에 닿지 못하는 것입니다. [502 대처](/posts/oracle-cloud-ops-troubleshooting)에서 확인 순서를 다룹니다.

## 정리

- `proxy_pass`에 경로가 없으면 요청 경로를 그대로 넘깁니다. nginx 경로(`/shop/`)와 Tomcat 배포 경로(`shop.war`)를 맞춥니다.
- `X-Forwarded-*` 헤더로 원래 방문자 IP와 HTTPS 여부를 앱에 알려 줍니다.
- 요청 크기 제한은 앱 성격에 맞게 정합니다.
- 업로드 파일은 nginx가 직접 보내 Tomcat의 부담을 줄입니다.
