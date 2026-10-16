---
title: "보안 헤더와 숨김 파일 차단"
slug: "oracle-cloud-nginx-security"
category: "development"
publishedAt: "2026-10-07"
summary: "nginx에서 일괄로 적용하는 보안 장치인 보안 응답 헤더, 숨김 파일(.git, .env) 차단, 버전 숨기기를 설정하는 방법을 설명합니다."
series: "oracle-cloud"
chapter: "nginx"
seriesOrder: 9
---
## 이 글에서 할 일

앱 코드를 고치지 않고도 nginx에서 적용할 수 있는 기본 보안 장치 세 가지를 정리합니다.

1. 보안 응답 헤더
2. 숨김 파일 차단
3. 서버 버전 숨기기

## 1. 보안 응답 헤더

앱의 location에 응답마다 헤더 세 개를 추가하는 줄을 넣습니다.

`/etc/nginx/snippets/shop.conf` (일부)

```nginx
location /shop/ {
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header Referrer-Policy "same-origin" always;
    ...
}
```

| 헤더 | 막는 것 |
|---|---|
| `X-Content-Type-Options: nosniff` | 브라우저가 파일 내용을 보고 종류를 **추측**하지 않게 합니다. 이미지로 올린 파일이 스크립트로 실행되는 공격을 막습니다 |
| `X-Frame-Options: SAMEORIGIN` | 다른 사이트가 이 페이지를 `<iframe>`으로 몰래 띄우지 못하게 합니다. 투명한 버튼을 겹쳐 클릭을 유도하는 **클릭재킹**을 막습니다 |
| `Referrer-Policy: same-origin` | 다른 사이트로 이동할 때 이전 페이지 주소(Referer)를 보내지 않습니다. 주소에 담긴 정보가 외부로 새지 않게 합니다 |

### `always`를 붙이는 이유

`add_header`는 기본적으로 **성공 응답(200, 301 등)에만** 헤더를 붙입니다. 오류 응답(404, 500)에도 보호가 필요하므로 `always`를 붙입니다.

### add_header 상속의 함정

`add_header`는 바깥 블록에서 안쪽 블록으로 상속되지만, **안쪽 블록에 `add_header`가 하나라도 있으면 바깥의 것은 모두 무시됩니다.**

```nginx
location /shop/ {
    add_header X-Frame-Options "SAMEORIGIN" always;   # 바깥
    location ~ ^/shop/login$ {
        add_header X-Extra "1";   # 이 줄이 있으면 바깥의 X-Frame-Options는 사라짐
    }
}
```

[속도 제한 글](/posts/oracle-cloud-nginx-rate-limit)의 안쪽 로그인 location처럼 `add_header`가 없으면 바깥 헤더가 그대로 상속됩니다. 안쪽에 헤더를 하나 추가해야 한다면, 바깥 헤더도 함께 다시 적어야 합니다.

### 확인하기

```bash
curl -sI https://example.com/shop/ | grep -iE 'x-content-type|x-frame|referrer'
```

```text
x-content-type-options: nosniff
x-frame-options: SAMEORIGIN
referrer-policy: same-origin
```

## 2. 숨김 파일 차단

리눅스에서 이름이 점(`.`)으로 시작하는 파일은 숨김 파일입니다. `.git`(소스 전체 이력), `.env`(비밀값) 같은 민감한 파일이 대부분 숨김 파일입니다. 실수로 서비스 폴더에 들어가더라도 밖에서 열 수 없게 막습니다.

`/etc/nginx/snippets/deny-private.conf`

```nginx
# 점(.)으로 시작하는 경로(.git, .env 등)는 공개하지 않는다. 인증서 발급용 /.well-known/ 은 예외.
location ~ /\.(?!well-known/) {
    deny all;
}
```

정규식을 풀어 보면 다음과 같습니다.

| 부분 | 뜻 |
|---|---|
| `/\.` | 경로 안에 "슬래시 바로 뒤에 점"이 있음. 즉 점으로 시작하는 파일이나 폴더 |
| `(?!well-known/)` | 단, 그 뒤가 `well-known/`이면 제외 |
| `deny all;` | 모두 거부 (403 Forbidden) |

`/.well-known/`을 예외로 둔 이유는 Let's Encrypt가 인증서를 발급할 때 이 경로로 도메인 소유를 확인하기 때문입니다.

이 파일을 443 server 블록에서 include합니다. 정규식 location이라 설정 파일 안의 위치와 상관없이 접두사 location보다 먼저 검사됩니다(`^~` 제외).

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://example.com/.git/config    # 403
```

[location 고르는 순서](/posts/oracle-cloud-nginx-server-location)에서 본 것처럼, `^~`로 선언한 location 안에서는 이 정규식이 적용되지 않습니다. 그런 경로가 가리키는 폴더에는 숨김 파일을 두지 않습니다.

## 3. 서버 버전 숨기기

`/etc/nginx/conf.d/server-tokens.conf`

```nginx
server_tokens off;
```

응답 헤더와 오류 페이지에서 nginx 버전 번호를 지웁니다.

```bash
curl -sI https://example.com/ | grep -i '^server'
```

```text
server: nginx
```

버전을 숨긴다고 취약점이 사라지지는 않지만, 특정 버전을 노리는 자동 공격의 표적이 되기 어려워집니다. 근본 대책은 [자동 보안 업데이트](/posts/oracle-cloud-instance-first-setup)로 nginx를 최신으로 유지하는 것입니다.

## 정리

- `add_header ... always`로 오류 응답에도 보안 헤더를 붙입니다.
- 안쪽 블록에 `add_header`를 쓰면 바깥 헤더가 사라지므로 함께 다시 적습니다.
- 점으로 시작하는 경로는 `/.well-known/`만 빼고 모두 차단합니다.
- `server_tokens off;`로 버전을 숨기고, 업데이트로 실제 취약점을 막습니다.
