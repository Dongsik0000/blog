---
title: "로그는 어디서 보나"
slug: "oracle-cloud-ops-logs"
category: "development"
publishedAt: "2026-10-07"
summary: "문제가 생겼을 때 볼 로그의 위치를 nginx, Docker 컨테이너, systemd 서비스, PostgreSQL 별로 정리하고, Docker 로그가 디스크를 채우지 않게 크기를 제한하는 방법을 설명합니다."
series: "oracle-cloud"
chapter: "ops"
seriesOrder: 1
---
## 이 글에서 할 일

서버에 문제가 생기면 가장 먼저 할 일은 **로그 읽기**입니다. 로그는 프로그램마다 다른 곳에 남습니다. 이 글에서는 어디를 봐야 하는지 지도를 만듭니다.

## 로그 지도

```text
브라우저 요청
   │
   ▼
nginx ─────────────▶ /var/log/nginx/access.log, error.log
   │
   ▼
Tomcat 컨테이너 ───▶ docker logs myapp
   │
   ▼
PostgreSQL ────────▶ /var/log/postgresql/postgresql-16-main.log

그 밖의 서비스, 예약 작업 ──▶ journalctl -u 서비스이름
```

요청이 지나가는 순서대로 앞에서부터 봅니다. nginx에 요청이 아예 없으면 그 앞(DNS, 방화벽)이 문제이고, nginx는 받았는데 오류라면 뒤쪽을 봅니다.

## nginx

| 파일 | 내용 |
|---|---|
| `/var/log/nginx/access.log` | 요청 한 줄씩: 방문자 IP, 시간, 경로, 응답 코드, 크기 |
| `/var/log/nginx/error.log` | 설정 오류, 뒤쪽 앱 연결 실패, 권한 문제 |

```bash
sudo tail -f /var/log/nginx/access.log                    # 실시간
sudo tail -50 /var/log/nginx/error.log                    # 최근 오류
sudo grep ' 50[234] ' /var/log/nginx/access.log | tail    # 502, 503, 504 응답만
```

access.log 한 줄은 다음과 같이 읽습니다.

```text
203.0.113.5 - - [07/Oct/2026:14:02:11 +0900] "GET /shop/ HTTP/2.0" 302 0 "-" "Mozilla/5.0 ..."
│                │                             │                     │   │
방문자 IP         시간                           요청(메서드 경로)      코드 크기
```

## Docker 컨테이너 (앱)

Tomcat과 Spring이 출력하는 로그(시작 메시지, 예외 스택 트레이스)는 컨테이너 로그로 모입니다.

```bash
docker logs myapp --tail 100            # 최근 100줄
docker logs myapp -f                    # 실시간
docker logs myapp --since 10m           # 최근 10분
docker logs myapp 2>&1 | grep -i exception | tail
```

compose 폴더 안이라면 `docker compose logs -f app`도 같습니다. `2>&1`은 오류 출력도 함께 grep하기 위해 붙입니다. 앱 로그 중 상당수가 오류 출력(stderr)으로 나오기 때문입니다.

**컨테이너를 지우면 로그도 함께 사라집니다.** 배포하면서 컨테이너를 새로 만들면 이전 컨테이너의 로그는 볼 수 없습니다. 배포 직후 오류를 조사한다면 이 점을 기억합니다.

### Docker 로그가 디스크를 채우지 않게

Docker의 기본 로그 방식(`json-file`)은 **크기 제한이 없습니다.** 오래 켜 둔 컨테이너가 로그를 많이 쓰면 디스크가 가득 찰 수 있습니다. Docker 공식 문서는 `/etc/docker/daemon.json`에서 크기를 제한하는 방법을 안내합니다.

`/etc/docker/daemon.json`

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
```

컨테이너마다 10MB짜리 파일 최대 3개, 즉 30MB까지만 남깁니다.

```bash
sudo systemctl restart docker
```

Docker를 재시작하면 **실행 중인 모든 컨테이너가 잠시 멈춥니다**(`restart: unless-stopped`면 다시 뜹니다). 그리고 이 설정은 **새로 만드는 컨테이너부터** 적용되므로, 기존 컨테이너는 다음 배포 때 다시 만들어지면서 적용됩니다.

## systemd 서비스와 예약 작업

nginx, PostgreSQL, Docker 자체, 그리고 [백업 timer](/posts/oracle-cloud-postgresql-backup-restore) 같은 예약 작업은 journalctl로 봅니다.

```bash
journalctl -u nginx --since "1 hour ago" --no-pager
journalctl -u docker -n 50 --no-pager
journalctl -u myapp-backup.service -n 20 --no-pager
```

## PostgreSQL

```bash
sudo tail -50 /var/log/postgresql/postgresql-16-main.log
```

접속 실패(`password authentication failed`), 권한 오류(`permission denied for table`), 느린 쿼리 경고 같은 내용이 남습니다. 앱 로그에 데이터베이스 오류가 보이면 같은 시각의 이 로그를 함께 봅니다.

## 시간을 맞춰 보기

여러 로그를 오가며 볼 때는 **같은 시각**을 찾는 것이 핵심입니다. [서버 시간대](/posts/oracle-cloud-instance-first-setup)를 `Asia/Seoul`로 맞춰 두면 대부분의 로그가 한국 시간으로 남습니다. 컨테이너 안의 앱은 [Dockerfile의 TZ 설정](/posts/oracle-cloud-docker-dockerfile)을 따릅니다.

## 정리

| 무엇을 | 어디서 |
|---|---|
| 요청과 응답 코드 | `/var/log/nginx/access.log` |
| nginx 오류, 앱 연결 실패 | `/var/log/nginx/error.log` |
| 앱 예외, 시작 실패 | `docker logs 컨테이너` |
| 서비스, 예약 작업 | `journalctl -u 이름` |
| 데이터베이스 | `/var/log/postgresql/` |

- Docker 로그는 기본으로 크기 제한이 없으므로 `daemon.json`에서 제한합니다.
