---
title: "nginx 설치하고 첫 페이지 띄우기"
slug: "oracle-cloud-domain-https-install-nginx"
category: "development"
publishedAt: "2026-10-07"
summary: "apt로 nginx를 설치하고, 서비스 상태와 포트를 확인한 뒤 브라우저에서 첫 페이지가 뜨는 것까지 확인합니다."
series: "oracle-cloud"
chapter: "domain-https"
seriesOrder: 2
---
## 이 글에서 할 일

웹 서버 **nginx**(엔진엑스라고 읽습니다)를 설치합니다. nginx가 무엇이고 왜 쓰는지는 [nginx 장](/posts/oracle-cloud-nginx-what-is-nginx)에서 자세히 다루고, 여기서는 설치하고 접속되는 것까지만 확인합니다.

## 따라 하기

### 1단계. 설치

```bash
sudo apt update
sudo apt install -y nginx
```

설치가 끝나면 nginx가 자동으로 실행되고, 부팅 때 자동으로 켜지게 등록됩니다.

### 2단계. 서비스 상태 확인

```bash
systemctl status nginx --no-pager | head -3
```

```text
● nginx.service - A high performance web server and a reverse proxy server
     Loaded: loaded (/usr/lib/systemd/system/nginx.service; enabled; preset: enabled)
     Active: active (running) ...
```

`enabled`(부팅 때 자동 실행)와 `active (running)`(실행 중)이 보이면 정상입니다.

### 3단계. 포트 확인

```bash
sudo ss -ltnp | grep nginx
```

```text
0.0.0.0:80  ... "nginx"
[::]:80     ... "nginx"
```

nginx가 80번 포트에서 요청을 기다리고 있습니다. `[::]`는 IPv6 주소에서도 듣는다는 뜻입니다.

### 4단계. 서버 안에서 접속해 보기

```bash
curl -I http://127.0.0.1
```

```text
HTTP/1.1 200 OK
Server: nginx/1.24.0 (Ubuntu)
...
```

`200 OK`면 nginx가 응답하고 있습니다. `-I`는 본문 없이 응답 헤더만 보는 옵션입니다.

### 5단계. 브라우저에서 접속하기

내 PC의 브라우저에서 `http://example.com`(또는 `http://공인IP`)로 접속합니다. "Welcome to nginx!" 페이지가 보이면 성공입니다.

<!-- 📷 스크린샷: Welcome to nginx! 페이지 -->

## 안 될 때 확인 순서

서버 안의 `curl`은 되는데 브라우저에서 안 된다면, 서버까지 오는 길이 막힌 것입니다.

| 순서 | 확인 | 방법 |
|---|---|---|
| 1 | 보안 목록에 80이 열려 있나 | [보안 목록](/posts/oracle-cloud-network-security-list) |
| 2 | iptables에 80이 열려 있나 | `sudo iptables -S INPUT \| grep 'dport 80'` |
| 3 | DNS가 서버 IP를 가리키나 | `dig example.com +short` |

## 버전 정보 숨기기

4단계 결과에 `Server: nginx/1.24.0 (Ubuntu)`처럼 **버전이 그대로 보입니다.** 공격자는 버전을 보고 알려진 취약점을 찾습니다. 그래서 버전 표시를 끕니다.

`/etc/nginx/conf.d/server-tokens.conf`

```nginx
server_tokens off;
```

`/etc/nginx/conf.d/` 폴더의 `.conf` 파일은 nginx 전체 설정에 자동으로 포함됩니다. 적용 후에는 `Server: nginx`만 보입니다. 설정을 적용하는 방법은 [안전하게 적용하기](/posts/oracle-cloud-nginx-apply-safely)에서 다룹니다.

## 정리

- `sudo apt install nginx`로 설치하면 바로 실행되고 부팅 때 자동 실행됩니다.
- `curl -I http://127.0.0.1`로 서버 안에서 먼저 확인하고, 그다음 브라우저로 확인합니다.
- 서버 안에서는 되는데 밖에서 안 되면 **보안 목록, iptables, DNS** 순으로 봅니다.
- `server_tokens off;`로 버전 정보를 숨깁니다.
