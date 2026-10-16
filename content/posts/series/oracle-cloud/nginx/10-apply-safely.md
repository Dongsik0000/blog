---
title: "설정을 안전하게 적용하고 되돌리기"
slug: "oracle-cloud-nginx-apply-safely"
category: "development"
publishedAt: "2026-10-07"
summary: "백업, 수정, nginx -t 검사, reload, 결과 확인, 되돌리기로 이어지는 nginx 설정 변경 절차와 로그 보는 법을 정리합니다."
series: "oracle-cloud"
chapter: "nginx"
seriesOrder: 10
---
## 이 글에서 할 일

nginx 설정을 잘못 고치면 서버의 **모든 사이트**가 한꺼번에 멈출 수 있습니다. 이 글에서는 실수해도 바로 되돌릴 수 있는 변경 절차를 익힙니다.

## 변경 절차 5단계

### 1단계. 백업

```bash
sudo cp /etc/nginx/sites-available/example.com ~/example.com.nginx.bak-$(date +%F)
```

`$(date +%F)`는 오늘 날짜(예: `2026-10-07`)로 바뀝니다. 백업 파일은 **`sites-enabled/` 폴더에 두지 않습니다.** 그 폴더의 파일은 모두 설정으로 읽혀 중복 오류가 납니다. 홈 폴더처럼 nginx가 읽지 않는 곳에 둡니다.

### 2단계. 수정

```bash
sudo nano /etc/nginx/sites-available/example.com
```

### 3단계. 문법 검사

```bash
sudo nginx -t
```

```text
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

`-t`는 설정을 **적용하지 않고 검사만** 합니다. 오류가 있으면 파일 이름과 줄 번호를 알려 줍니다. 이 단계에서 실패하면 적용하지 않고 고칩니다.

### 4단계. 적용 (reload)

```bash
sudo systemctl reload nginx
```

| 방법 | 동작 | 접속 끊김 |
|---|---|---|
| `reload` | 새 설정으로 새 작업 프로세스를 띄우고, 기존 프로세스는 처리 중인 요청을 마친 뒤 종료 | **없음** |
| `restart` | nginx 전체를 껐다가 켬 | 잠깐 있음 |

설정 변경은 `reload`로 충분합니다. 그리고 `reload`는 새 설정에 문제가 있으면 **기존 설정으로 계속 동작**하므로 더 안전합니다. 그래도 3단계 검사를 먼저 하는 습관을 들입니다.

### 5단계. 결과 확인

바꾼 경로와 **바꾸지 않은 경로**를 모두 확인합니다. 다른 사이트가 영향을 받지 않았는지 보는 것이 중요합니다. 서비스 중인 경로를 목록으로 만들어 두고 매번 같은 명령으로 확인합니다.

```bash
for u in / /docs/ /app/ /shop/; do
  printf "%-10s " "$u"
  curl -s -o /dev/null -w "%{http_code}\n" "https://example.com$u"
done
```

```text
/          200
/docs/     200
/app/      200
/shop/     302    ← 로그인 화면으로 이동 (정상)
```

`302`는 로그인이 필요한 앱이 로그인 페이지로 보내는 정상 응답입니다. 각 경로의 정상 응답 코드를 알아 두고, `502`, `404`처럼 예상과 다른 값이 있으면 아래 방법으로 되돌립니다.

## 되돌리기

```bash
sudo cp ~/example.com.nginx.bak-2026-10-07 /etc/nginx/sites-available/example.com
sudo nginx -t && sudo systemctl reload nginx
```

`&&`는 "앞 명령이 성공했을 때만 뒤 명령을 실행"한다는 뜻입니다. 검사가 실패하면 reload하지 않습니다.

## 로그 보기

### 파일 위치

| 로그 | 경로 | 내용 |
|---|---|---|
| 접근 로그 | `/var/log/nginx/access.log` | 모든 요청 한 줄씩 (IP, 시간, 경로, 응답 코드) |
| 오류 로그 | `/var/log/nginx/error.log` | 설정 오류, 뒤쪽 서버 연결 실패 등 |

```bash
sudo tail -f /var/log/nginx/access.log                  # 실시간 요청 보기
sudo tail -50 /var/log/nginx/error.log                  # 최근 오류
sudo grep ' 502 ' /var/log/nginx/access.log | tail       # 502 응답만
```

### 오래된 로그는 자동 정리

```bash
ls /var/log/nginx
```

```text
access.log
access.log.1
access.log.2.gz
...
```

`logrotate`가 주기적으로 로그 파일을 바꾸고(`access.log.1`), 오래된 것은 압축하고(`.gz`), 일정 개수가 넘으면 지웁니다. 디스크가 로그로 가득 차지 않게 하는 장치로, nginx를 apt로 설치하면 함께 설정됩니다.

## 정리

- **백업 → 수정 → `nginx -t` → `reload` → 확인** 순서를 지킵니다.
- 백업은 `sites-enabled/` 밖에 둡니다.
- 확인할 때는 바꾸지 않은 다른 사이트까지 응답 코드를 봅니다.
- 문제가 있으면 백업으로 복구하고 다시 `nginx -t && reload` 합니다.
- 원인을 찾을 때는 `error.log`를 먼저 봅니다.
