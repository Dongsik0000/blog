---
title: "502, 504, 403, 404가 날 때 확인 순서"
slug: "oracle-cloud-ops-troubleshooting"
category: "development"
publishedAt: "2026-10-07"
summary: "사이트가 오류 페이지를 보여 줄 때 응답 코드별로 무엇을 의심하고 어떤 순서로 확인하는지, 가장 흔한 502 Bad Gateway를 중심으로 정리합니다."
series: "oracle-cloud"
chapter: "ops"
seriesOrder: 2
---
## 이 글에서 할 일

사이트에 오류가 났을 때 무작정 재시작하면 원인을 놓칩니다. **응답 코드**는 어디서 문제가 생겼는지 알려 주는 가장 좋은 단서입니다. 이 글에서는 코드별로 확인 순서를 정리합니다.

## 먼저: 응답 코드 확인

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://example.com/shop/
```

| 코드 | 누가 낸 오류 | 뜻 |
|---|---|---|
| 502 Bad Gateway | nginx | 뒤쪽 앱에 **연결하지 못함** |
| 504 Gateway Timeout | nginx | 뒤쪽 앱에 연결했지만 **응답이 너무 늦음** |
| 403 Forbidden | nginx | 파일 권한이 없거나 `deny` 규칙에 걸림 |
| 404 Not Found | nginx 또는 앱 | 파일이나 경로가 없음 |
| 413 Request Entity Too Large | nginx | 요청이 `client_max_body_size`보다 큼 |
| 500 Internal Server Error | 대부분 앱 | 앱 코드에서 예외 발생 |
| 응답 없음 (timeout) | | 요청이 서버에 도착하지 못함. DNS, 보안 목록, iptables |

## 502 Bad Gateway

가장 자주 보게 되는 오류입니다. nginx는 살아 있는데, `proxy_pass`로 넘길 앱(Tomcat)에 연결이 안 되는 상태입니다.

### 1단계. nginx 오류 로그

```bash
sudo tail -20 /var/log/nginx/error.log
```

```text
connect() failed (111: Connection refused) while connecting to upstream, ... upstream: "http://127.0.0.1:8080/shop/"
```

`upstream` 뒤의 주소가 nginx가 연결하려던 곳입니다. `Connection refused`는 "그 주소에서 기다리는 프로그램이 없다"는 뜻입니다.

### 2단계. 앱 컨테이너가 떠 있나

```bash
docker ps -a --filter name=myapp --format '{{.Names}} {{.Status}}'
```

| 결과 | 의미 | 다음 행동 |
|---|---|---|
| `Up 10 seconds (health: starting)` | 방금 시작함. Tomcat이 뜨는 중 | 1~2분 기다린 뒤 다시 확인. **배포 직후의 502는 대부분 이것** |
| `Up 3 days (healthy)` | 정상 실행 중 | 3단계로 |
| `Restarting` 또는 재시작 반복 | 앱이 계속 죽음 | 4단계 로그 확인 |
| `Exited (1) ...` | 멈춤 | 4단계 로그 확인 후 `docker compose up -d` |
| 아무것도 안 나옴 | 컨테이너가 없음 | 앱 폴더에서 `docker compose up -d --build` |

### 3단계. 포트를 듣고 있나

```bash
sudo ss -ltnp | grep ':8080 '
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/shop/
```

- `ss`에 줄이 없으면 앱이 그 포트에서 듣지 않습니다. nginx의 `proxy_pass` 포트와 Tomcat 포트가 같은지 확인합니다.
- 서버 안의 `curl`은 되는데 nginx를 거치면 502라면, nginx 설정의 주소나 포트가 틀렸을 가능성이 큽니다.

### 4단계. 앱 로그

```bash
docker logs myapp --tail 100
```

시작하다 죽는 흔한 원인은 다음과 같습니다.

| 로그에 보이는 것 | 원인 |
|---|---|
| `password authentication failed` | 비밀값 파일의 DB 비밀번호가 틀림 |
| `Connection refused ... 5432` | PostgreSQL이 꺼져 있거나, 컨테이너가 host 네트워크가 아님 |
| `Address already in use` | 다른 프로그램이 같은 포트를 씀 (host 네트워크에서 앱끼리 포트 충돌) |
| `OutOfMemoryError` 또는 로그 없이 종료 | [메모리 상한](/posts/oracle-cloud-docker-memory) 부족 |

### 경로는 있는데 앱이 없는 경우

nginx에 location을 만들어 둔 채 앱을 지우거나 옮기면, 그 경로는 **계속 502**를 냅니다. 앱을 없앨 때는 nginx의 location과 include 줄도 함께 지우고, 배포 키와 이미지도 정리합니다([배포 키](/posts/oracle-cloud-deploy-deploy-key), [이미지 정리](/posts/oracle-cloud-docker-cleanup)).

## 504 Gateway Timeout

앱이 `proxy_read_timeout`(예: 60초) 안에 응답하지 않았습니다.

- 앱 로그에서 그 시각에 오래 걸린 요청(느린 쿼리, 외부 API 대기)을 찾습니다.
- 데이터베이스 잠금이나 연결 부족이 흔한 원인입니다. PostgreSQL 로그를 함께 봅니다.
- 타임아웃을 늘리는 것은 마지막 수단입니다. 느린 원인을 먼저 찾습니다.

## 403 Forbidden

```bash
sudo tail -20 /var/log/nginx/error.log
```

| 오류 로그 | 원인 |
|---|---|
| `Permission denied` | nginx(`www-data`)가 파일이나 경로 중간 폴더를 읽을 수 없음. `ls -ld`로 경로의 폴더마다 권한 확인 |
| `access forbidden by rule` | `deny` 규칙(예: [숨김 파일 차단](/posts/oracle-cloud-nginx-security))에 걸림. 의도한 차단인지 확인 |
| `directory index ... is forbidden` | 폴더를 요청했는데 `index.html`이 없음 |

## 404 Not Found

- 정적 사이트라면 `alias`, `root` 경로와 끝 슬래시를 확인합니다([alias 규칙](/posts/oracle-cloud-nginx-spa-subpath)).
- React 앱의 새로고침 404라면 `try_files`의 SPA 대체 응답이 빠졌습니다.
- 페이지는 뜨는데 CSS, JS만 404라면 앱 빌드의 `base` 경로가 틀렸습니다.
- 앱이 낸 404라면(응답 본문이 Tomcat이나 Spring 오류 화면) 앱의 URL 매핑을 확인합니다.

## 응답이 아예 없을 때

요청이 서버에 도착하지 않는 경우입니다. 다음 순서로 봅니다.

1. `dig example.com +short`: DNS가 서버 IP를 가리키나
2. 콘솔의 인스턴스 상태가 `RUNNING`인가
3. 보안 목록과 iptables에 80, 443이 열려 있나 ([두 겹의 방화벽](/posts/oracle-cloud-firewall-two-layers))
4. `systemctl status nginx`: nginx가 실행 중인가

## 정리

- 응답 코드로 문제 위치를 먼저 좁힙니다.
- 502는 **nginx 오류 로그 → 컨테이너 상태 → 포트 → 앱 로그** 순서로 봅니다. 배포 직후라면 1~2분 기다려 봅니다.
- 403은 권한이나 `deny` 규칙, 404는 경로와 `base` 설정을 봅니다.
- 앱을 없앨 때는 nginx 설정, 배포 키, 이미지도 함께 정리합니다.
