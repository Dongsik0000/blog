---
title: "systemd로 서비스와 예약 작업 관리하기"
slug: "oracle-cloud-linux-systemd"
category: "development"
publishedAt: "2026-10-07"
summary: "nginx, PostgreSQL, Docker가 서버 부팅과 함께 켜지는 원리, systemctl과 journalctl 사용법, 매일 새벽 백업을 실행하는 timer 만드는 법을 설명합니다."
series: "oracle-cloud"
chapter: "linux"
seriesOrder: 4
---
## 이 글에서 할 일

서버는 사람이 로그인하지 않아도 프로그램이 계속 돌아가야 합니다. 재부팅되면 자동으로 다시 켜져야 하고, 정해진 시간에 백업 같은 작업도 실행해야 합니다. 이 일을 맡는 것이 **systemd**입니다.

## 서비스(service)란

계속 실행되는 프로그램을 **서비스**라고 부릅니다(Windows의 "서비스"와 같은 개념). 이 시리즈에서 설치하는 주요 서비스는 다음과 같습니다.

| 서비스 | 하는 일 |
|---|---|
| `nginx.service` | 웹 서버 |
| `postgresql@16-main.service` | PostgreSQL 16 데이터베이스 |
| `docker.service` | Docker. 그 안에서 앱 컨테이너가 돌아감 |

## systemctl: 서비스 다루기

```bash
systemctl status nginx            # 상태 보기
sudo systemctl start nginx        # 켜기
sudo systemctl stop nginx         # 끄기
sudo systemctl restart nginx      # 껐다 켜기 (잠깐 끊김)
sudo systemctl reload nginx       # 설정만 다시 읽기 (끊김 없음, 지원하는 서비스만)
sudo systemctl enable nginx       # 부팅 때 자동으로 켜지게
sudo systemctl disable nginx      # 부팅 때 켜지지 않게
```

`status`의 결과는 다음과 같은 형태입니다.

```text
● nginx.service - A high performance web server and a reverse proxy server
     Loaded: loaded (/usr/lib/systemd/system/nginx.service; enabled; preset: enabled)
     Active: active (running) since Tue 2026-09-29 14:39:19 KST; 1 week 0 days ago
```

| 부분 | 뜻 |
|---|---|
| `enabled` | 부팅할 때 자동으로 켜집니다 |
| `active (running)` | 지금 실행 중입니다 |
| `since ...` | 언제부터 실행 중인지 |

`start`와 `enable`은 다릅니다. `start`는 **지금** 켜는 것이고, `enable`은 **다음 부팅부터** 자동으로 켜지게 하는 것입니다. 새 서비스를 설치하면 보통 둘 다 합니다.

```bash
sudo systemctl enable --now 서비스이름    # enable과 start를 한 번에
```

## journalctl: 서비스 로그 보기

systemd는 서비스의 출력을 한곳에 모아 둡니다. 서비스가 이상하면 가장 먼저 볼 곳입니다.

```bash
journalctl -u nginx --no-pager -n 50     # nginx 로그 최근 50줄
journalctl -u nginx -f                    # 실시간으로 계속 보기
journalctl -u nginx --since "1 hour ago"  # 최근 1시간
```

- `-u`: 어떤 서비스(unit)의 로그인지
- `-n`: 줄 수
- `--no-pager`: 한 화면씩 끊지 않고 바로 출력

## timer: 예약 작업

"매일 새벽 4시 10분에 데이터베이스 백업"처럼 정해진 시간에 실행할 작업은 **timer**로 만듭니다. 예전에는 cron을 많이 썼지만, timer는 로그가 journalctl에 함께 남고, 서버가 꺼져 있어 놓친 작업을 다시 실행하는 기능이 있습니다.

timer는 두 파일이 짝을 이룹니다.

- `이름.service`: **무엇을** 실행할지
- `이름.timer`: **언제** 실행할지

### 예: 매일 데이터베이스 백업

`myapp`이라는 앱의 백업 스크립트를 `/usr/local/bin/myapp-backup`에 두었다고 하겠습니다. 스크립트 작성법은 [백업과 복구](/posts/oracle-cloud-postgresql-backup-restore)에서 다룹니다.

`/etc/systemd/system/myapp-backup.service`

```ini
[Unit]
Description=myapp PostgreSQL backup (pg_dump)

[Service]
Type=oneshot
ExecStart=/usr/local/bin/myapp-backup
```

- `Type=oneshot`: 계속 실행되는 서비스가 아니라, 한 번 실행하고 끝나는 작업이라는 뜻입니다.
- `ExecStart`: 실행할 프로그램입니다.

`/etc/systemd/system/myapp-backup.timer`

```ini
[Unit]
Description=Daily myapp backup at 04:10 KST

[Timer]
OnCalendar=*-*-* 04:10:00 Asia/Seoul
Persistent=true

[Install]
WantedBy=timers.target
```

| 줄 | 뜻 |
|---|---|
| `OnCalendar=*-*-* 04:10:00 Asia/Seoul` | 매년-매월-매일 04:10:00, 한국 시간 기준 |
| `Persistent=true` | 그 시각에 서버가 꺼져 있었다면, 다음에 켜질 때 바로 한 번 실행 |
| `WantedBy=timers.target` | `enable`했을 때 부팅과 함께 timer가 동작하게 연결 |

timer를 등록하는 순서는 다음과 같습니다.

```bash
sudo systemctl daemon-reload                    # 새 파일을 systemd에 알림
sudo systemctl enable --now myapp-backup.timer  # timer를 켜고 부팅 때도 켜지게
```

등록하기 전에 service를 한 번 직접 실행해서 스크립트가 잘 도는지 확인해 두면 좋습니다.

```bash
sudo systemctl start myapp-backup.service
```

### 예약 상태 확인

```bash
systemctl list-timers myapp-backup.timer certbot.timer --no-pager
```

```text
NEXT                        LEFT LAST                           PASSED UNIT               ACTIVATES
Thu 2026-10-08 04:10:00 KST  14h Wed 2026-10-07 04:10:28 KST    9h ago myapp-backup.timer myapp-backup.service
Thu 2026-10-08 08:47:50 KST  18h Wed 2026-10-07 13:54:27 KST 14min ago certbot.timer      certbot.service
```

`NEXT`는 다음 실행 시각, `LAST`는 마지막 실행 시각입니다. certbot.timer는 HTTPS 인증서를 자동 갱신하는 timer로, certbot을 apt로 설치하면 함께 만들어집니다.

마지막 실행이 성공했는지는 로그로 확인합니다.

```bash
journalctl -u myapp-backup.service --no-pager -n 3
```

성공했다면 마지막 줄이 다음과 같습니다.

```text
... systemd[1]: myapp-backup.service: Deactivated successfully.
... systemd[1]: Finished myapp-backup.service - myapp PostgreSQL backup (pg_dump).
```

실패했다면 `Failed with result 'exit-code'` 같은 줄과 함께 스크립트가 낸 오류 메시지가 보입니다.

## 정리

- 계속 돌아가야 하는 프로그램은 systemd **서비스**로 관리합니다.
- `start`는 지금 켜기, `enable`은 부팅 때 자동 켜기입니다.
- 서비스에 문제가 있으면 `journalctl -u 서비스이름`부터 봅니다.
- 정해진 시간의 작업은 **service + timer** 짝으로 만듭니다. `Persistent=true`면 놓친 작업도 실행됩니다.
