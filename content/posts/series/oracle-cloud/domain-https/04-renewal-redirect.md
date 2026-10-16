---
title: "인증서 자동 갱신과 HTTP를 HTTPS로 보내기"
slug: "oracle-cloud-domain-https-renewal-redirect"
category: "development"
publishedAt: "2026-10-07"
summary: "90일짜리 인증서가 자동으로 갱신되는 원리를 확인하고, HTTP로 들어온 요청과 IP로 직접 들어온 요청을 HTTPS 도메인으로 보내는 설정을 읽습니다."
series: "oracle-cloud"
chapter: "domain-https"
seriesOrder: 4
---
## 이 글에서 할 일

1. 인증서가 만료되기 전에 자동으로 갱신되는지 확인합니다.
2. `http://`로 들어온 방문자를 `https://`로 자동 이동시킵니다.

## 1. 자동 갱신

### 갱신을 맡는 timer

certbot을 apt로 설치하면 **certbot.timer**가 함께 설치되어 갱신을 자동으로 시도합니다.

```bash
systemctl cat certbot.timer | grep -E 'OnCalendar|RandomizedDelay'
```

```text
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=43200
```

| 줄 | 뜻 |
|---|---|
| `OnCalendar=*-*-* 00,12:00:00` | 매일 0시와 12시 |
| `RandomizedDelaySec=43200` | 그 시각에서 최대 43200초(12시간) 사이 임의로 늦춰 실행 |

시간을 임의로 흩뜨리는 이유는, 전 세계 서버가 같은 시각에 Let's Encrypt로 몰리지 않게 하기 위해서입니다. timer가 실행되어도 certbot은 **만료가 가까운 인증서만** 실제로 갱신합니다.

timer의 상태는 [systemd 글](/posts/oracle-cloud-linux-systemd)에서 본 `systemctl list-timers`로 확인합니다.

### 갱신 방법이 기록된 파일

처음 발급할 때 쓴 방법이 저장되어 있어서, 갱신도 같은 방법으로 합니다.

```bash
sudo grep -E '^(authenticator|installer) ' /etc/letsencrypt/renewal/example.com.conf
```

```text
authenticator = nginx
installer = nginx
```

### 갱신이 정말 되는지 미리 시험하기

certbot 공식 안내의 명령입니다. 실제 인증서는 바꾸지 않고 갱신 과정만 시험합니다.

```bash
sudo certbot renew --dry-run
```

마지막에 성공 메시지가 나오면 **지금 갱신하는 데 필요한 조건**(80번 포트, DNS, nginx 설정)이 갖춰져 있다는 뜻입니다. 실패한다면 대부분 **80번 포트가 막혔거나** DNS가 바뀐 경우입니다.

dry-run은 지금 한 번 시험한 결과일 뿐입니다. timer가 실제로 돌고 있는지, 지난 갱신 시도가 성공했는지는 따로 확인합니다.

```bash
systemctl list-timers certbot.timer --no-pager           # 마지막 실행, 다음 실행 시각
sudo journalctl -u certbot.service -n 20 --no-pager      # 지난 실행의 결과
sudo certbot certificates                                # 남은 유효 기간
```

## 2. HTTP를 HTTPS로 보내기

HTTP 요청을 HTTPS로 보내려면 80번 포트용 server 블록을 두 개 둡니다. certbot이 자동으로 만들어 주기도 하지만, 직접 쓰면 다음과 같습니다.

`/etc/nginx/sites-available/example.com` (앞부분)

```nginx
# 도메인이 아닌 HTTP 요청(IP 직접 접속 등)은 https 도메인으로 보낸다
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    return 301 https://example.com$request_uri;
}

server {
    listen 80;
    listen [::]:80;
    server_name www.example.com example.com;

    return 301 https://$host$request_uri;
}
```

### 첫 번째 블록: 이름 없는 요청

- `default_server`: 다른 server 블록의 `server_name`에 해당하지 않는 요청을 모두 이 블록이 받습니다. 예를 들어 `http://공인IP`처럼 IP로 직접 들어온 요청입니다.
- `server_name _;`: "어떤 이름에도 해당하지 않는다"는 관례적인 표기입니다.
- `return 301 https://example.com$request_uri;`: 정식 HTTPS 도메인으로 **영구 이동**(301)시킵니다. `$request_uri`는 요청한 경로(예: `/app/login`)라서 경로를 유지한 채 이동합니다.

### 두 번째 블록: 도메인으로 들어온 HTTP 요청

- `server_name www.example.com example.com;`: 두 도메인으로 들어온 HTTP 요청
- `return 301 https://$host$request_uri;`: 같은 도메인의 HTTPS로 이동. `$host`는 요청한 도메인 이름 그대로입니다.

첫 번째 블록은 `default_server`라서, [nginx 설치](/posts/oracle-cloud-domain-https-install-nginx) 때부터 켜져 있던 기본 사이트(`default`, 이것도 `default_server`)와 겹칩니다. 둘 다 켜져 있으면 `nginx -t`에서 "duplicate default server" 오류가 나므로, 이 설정을 적용하기 전에 기본 사이트 링크를 끕니다.

```bash
sudo rm /etc/nginx/sites-enabled/default   # 링크만 지움. 원본은 sites-available 에 남음
sudo nginx -t && sudo systemctl reload nginx
```

### 확인하기

```bash
curl -sI http://example.com/app/ | grep -E '^HTTP|^Location'
```

```text
HTTP/1.1 301 Moved Permanently
Location: https://example.com/app/
```

`Location`이 `https://`로 시작하면 정상입니다.

## 정리

- certbot.timer가 하루 두 번(임의 지연 포함) 갱신을 시도하고, 만료가 가까울 때만 실제로 갱신합니다.
- `sudo certbot renew --dry-run`으로 갱신이 되는지 미리 시험합니다.
- 80번 포트 server 블록에서 `return 301 https://...`로 모든 HTTP 요청을 HTTPS로 보냅니다.
- 갱신에도 80번 포트가 쓰이므로, HTTPS를 적용한 뒤에도 **80번은 닫지 않습니다.**
