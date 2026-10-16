---
title: "certbot으로 무료 HTTPS 인증서 받기"
slug: "oracle-cloud-domain-https-certbot"
category: "development"
publishedAt: "2026-10-07"
summary: "HTTPS가 왜 필요한지, Let's Encrypt 인증서를 certbot으로 발급받아 nginx에 적용하는 과정과 certbot이 설정 파일에 무엇을 추가하는지 확인합니다."
series: "oracle-cloud"
chapter: "domain-https"
seriesOrder: 3
---
## 이 글에서 할 일

주소창에 자물쇠가 보이는 **HTTPS**를 적용합니다. 무료 인증 기관인 **Let's Encrypt**의 인증서를 **certbot**이라는 도구로 받습니다.

## HTTPS가 필요한 이유

HTTP는 데이터를 **평문**으로 보냅니다. 같은 와이파이에 있는 사람이 로그인 비밀번호를 그대로 볼 수 있습니다. HTTPS는 데이터를 **암호화**하고, 접속한 서버가 진짜 `example.com`인지 **인증서**로 증명합니다.

또 요즘 브라우저는 HTTP 사이트에 "주의 요함" 경고를 띄웁니다. 로그인 기능이 있는 서비스라면 HTTPS가 필수입니다.

## 인증서와 Let's Encrypt

- **인증서**: "이 서버는 `example.com`의 진짜 주인이 운영한다"는 증명서입니다. 신뢰받는 **인증 기관**(CA)이 발급합니다.
- **Let's Encrypt**: 무료로 인증서를 발급하는 인증 기관입니다. 인증서 유효 기간이 짧아서(약 90일) **자동 갱신**이 필수입니다.
- **certbot**: Let's Encrypt 인증서를 받고 갱신하는 프로그램입니다.

## 따라 하기

### 1단계. certbot 설치

certbot 공식 안내는 snap 설치를 권장합니다. 이 시리즈는 다른 프로그램과 관리 방식을 맞추려고 Ubuntu 저장소의 apt 패키지로 설치합니다. 둘 다 동작합니다.

```bash
sudo apt install -y certbot python3-certbot-nginx
```

- `python3-certbot-nginx`: certbot이 nginx 설정을 직접 읽고 고칠 수 있게 하는 플러그인입니다.

### 2단계. 발급 전 확인

- DNS가 이 서버를 가리키는가? (`dig example.com +short`)
- 보안 목록과 iptables에서 **80과 443**이 열려 있는가?
- nginx 설정에 `server_name example.com www.example.com;`가 있는 server 블록이 있는가?

Let's Encrypt는 발급 전에 "이 도메인으로 접속하면 정말 이 서버가 응답하는가"를 **80번 포트로** 확인합니다. 80이 막혀 있으면 발급이 실패합니다.

### 3단계. 발급

```bash
sudo certbot --nginx -d example.com -d www.example.com
```

- `--nginx`: nginx 플러그인을 써서 인증과 설정 변경을 모두 자동으로 합니다.
- `-d`: 인증서에 넣을 도메인. 여러 개를 넣을 수 있습니다.

처음 실행하면 이메일 주소(만료 알림용)와 약관 동의를 묻습니다. 성공하면 인증서가 `/etc/letsencrypt/live/example.com/`에 저장됩니다.

nginx 설정을 직접 고치고 싶다면 인증서만 받는 방법도 있습니다.

```bash
sudo certbot certonly --nginx -d example.com -d www.example.com
```

## certbot이 바꾼 설정 읽기

`--nginx`로 발급하면 certbot이 nginx 설정에 몇 줄을 추가합니다. 설정 파일을 열어 보면 `# managed by Certbot` 주석이 붙은 줄이 그것입니다.

`/etc/nginx/sites-available/example.com` (일부)

```nginx
server {
    listen 443 ssl http2; # managed by Certbot
    listen [::]:443 ssl http2 ipv6only=on; # managed by Certbot
    server_name www.example.com example.com;

    ssl_certificate /etc/letsencrypt/live/example.com/fullchain.pem; # managed by Certbot
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem; # managed by Certbot
    include /etc/letsencrypt/options-ssl-nginx.conf; # managed by Certbot
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem; # managed by Certbot
    ...
}
```

| 줄 | 뜻 |
|---|---|
| `listen 443 ssl http2` | 443번에서 HTTPS(그리고 더 빠른 HTTP/2)로 받음 |
| `ssl_certificate` | 인증서(서버 인증서 + 중간 인증서 묶음) |
| `ssl_certificate_key` | 인증서의 개인 키. **절대 외부로 내보내지 않습니다** |
| `options-ssl-nginx.conf` | certbot이 권장하는 암호화 설정 묶음 |
| `ssl_dhparam` | 키 교환에 쓰는 매개변수 |

## 확인하기

```bash
sudo certbot certificates
```

```text
    Domains: example.com www.example.com
    Expiry Date: 2026-12-27 00:11:46+00:00 (VALID: 80 days)
```

브라우저에서 `https://example.com`으로 접속해 자물쇠가 보이면 성공입니다.

<!-- 📷 스크린샷: 브라우저 주소창의 자물쇠와 인증서 정보 -->

## 정리

- HTTPS는 데이터를 암호화하고 서버가 진짜인지 증명합니다.
- `sudo certbot --nginx -d 도메인`으로 발급과 nginx 설정을 한 번에 합니다.
- 발급하려면 DNS 연결과 **80번 포트 개방**이 먼저 되어 있어야 합니다.
- `# managed by Certbot` 줄은 certbot이 관리하는 설정입니다.
