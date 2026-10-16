---
title: "systemd 타이머로 매일 DB 백업하기"
slug: "ledger-deploy-backup"
category: "development"
publishedAt: "2026-10-08"
summary: "완성된 백업 스크립트와 서비스·타이머 파일을 서버에 설치하고, 한국 시간 새벽 4시 10분 실행과 백업 파일 권한을 확인합니다."
series: "ledger"
chapter: "deploy"
seriesOrder: 6
---
## 이 글에서 할 일

거래와 사용자 설정은 PostgreSQL에 저장됩니다. Ledger는 `pg_dump`로 DB를 백업하는 스크립트와 매일 실행할 systemd 타이머를 갖추고 있습니다. 서버에 이 파일을 등록하고 예약 상태와 결과를 확인합니다.

**systemd 타이머**는 정해진 시각에 서비스를 실행하는 Linux의 예약 실행 기능입니다. PostgreSQL은 호스트 서버에서 실행하며, 이 글은 해당 서버의 `postgres` 계정으로 백업할 수 있는 환경을 기준으로 합니다.

## 백업 스크립트

`deploy/backup.sh`

```bash
#!/usr/bin/env bash
# 매일 pg_dump 백업. 30일 보관.
#   sudo install -m 750 deploy/backup.sh /usr/local/bin/ledger-backup
#   매일 04:10 실행은 deploy/ledger-backup.service·timer (systemd, 서버에 crontab 없음)
set -euo pipefail
umask 077   # 덤프에 비밀번호 해시·거래 내역이 있으므로 root 만 읽게

BACKUP_DIR=/var/backups/ledger
mkdir -p "$BACKUP_DIR"

# 시각까지 넣어 같은 날 다시 실행해도 기존 백업을 덮어쓰지 않는다.
# 임시 파일에 다 쓴 뒤 성공했을 때만 이름을 바꾸므로 .dump 는 끝까지 성공한 백업만 남는다
FILE="$BACKUP_DIR/ledger-$(date +%F-%H%M%S).dump"
trap 'rm -f "$FILE.tmp"' EXIT

sudo -u postgres pg_dump -Fc ledger > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"
find "$BACKUP_DIR" -name 'ledger-*.dump' -mtime +30 -delete
```

`pg_dump -Fc`는 나중에 `pg_restore`로 읽을 수 있는 PostgreSQL 백업 파일을 만듭니다. 결과는 날짜와 시각이 들어간 이름(예: `ledger-2026-10-16-041000.dump`)으로 `/var/backups/ledger`에 저장합니다. 끝의 명령은 이 디렉터리에서 `-mtime +30` 조건에 해당하는 오래된 백업 파일을 삭제합니다.

덤프를 바로 최종 파일에 쓰지 않고 `.tmp` 파일에 먼저 쓰는 이유는, 셸의 `>`가 명령을 실행하기 **전에** 파일을 비우기 때문입니다. 날짜만 넣은 이름에 바로 쓰면, 같은 날 다시 실행했다가 `pg_dump`가 실패했을 때 그날 아침의 정상 백업까지 빈 파일이 됩니다. 지금은 덤프가 성공해야 `mv`로 이름이 바뀌고, 실패하면 `set -e`로 멈춘 뒤 `trap`이 임시 파일을 지웁니다.

`umask 077`은 새 파일을 만든 사용자 외에는 읽지 못하게 하는 기본 권한입니다. 스크립트를 실행하는 root가 파일을 만들고, DB를 읽는 `pg_dump`만 `postgres` 계정으로 실행합니다.

## 서비스와 타이머

`deploy/ledger-backup.service`

```ini
[Unit]
Description=ledger PostgreSQL backup (pg_dump)

[Service]
Type=oneshot
ExecStart=/usr/local/bin/ledger-backup
```

`deploy/ledger-backup.timer`

```ini
[Unit]
Description=Daily ledger backup at 04:10 KST

[Timer]
OnCalendar=*-*-* 04:10:00 Asia/Seoul
Persistent=true

[Install]
WantedBy=timers.target
```

서비스는 백업을 한 번 실행하고 끝냅니다. 타이머는 한국 시간 매일 04:10에 서비스를 호출합니다. `Persistent=true`는 서버가 꺼져 예약 시각을 놓쳤을 때 다시 활성화된 뒤 실행을 따라잡게 합니다.

`Persistent`의 의미는 [systemd 공식 저장소의 타이머 문서](https://github.com/systemd/systemd/blob/v255/man/systemd.timer.xml)에서 확인했습니다(2026-10-07).

## 1단계. 서버에 설치

서버의 Ledger 저장소 루트에서 실행합니다. 같은 이름의 기존 파일이 있다면 현재 운영 설정과 비교한 뒤 적용합니다.

```bash
sudo install -m 750 deploy/backup.sh /usr/local/bin/ledger-backup
sudo install -m 644 deploy/ledger-backup.service /etc/systemd/system/ledger-backup.service
sudo install -m 644 deploy/ledger-backup.timer /etc/systemd/system/ledger-backup.timer
sudo systemctl daemon-reload
sudo systemctl enable --now ledger-backup.timer
```

`daemon-reload`로 단위 파일을 다시 읽고, 타이머를 활성화합니다. `enable`은 재부팅 후에도 예약을 유지하게 합니다.

## 2단계. 예약과 백업 결과 확인

서버에서 실행합니다.

```bash
systemctl list-timers ledger-backup.timer
sudo systemctl start ledger-backup.service
sudo journalctl -u ledger-backup.service -n 30 --no-pager
sudo ls -lh /var/backups/ledger
```

예약 목록에서 다음 실행 시각을 확인합니다. `start`는 기다리지 않고 실제 백업을 한 번 수행하며, 스크립트의 오래된 파일 정리도 함께 수행합니다. 로그에서 성공 여부를 보고 당일 백업 파일이 생성되었는지 확인합니다.

가장 최근 백업 파일의 내부 목록만 읽어 형식을 확인하려면 다음을 실행합니다. DB에 복원하는 명령은 아닙니다.

```bash
sudo sh -c 'pg_restore --list "$(ls -t /var/backups/ledger/ledger-*.dump | head -1)"'
```

테이블과 데이터 항목 목록이 보여야 합니다. 이 목록 확인과 실제 복원 성공은 서로 다른 확인이며, 복원 절차는 [PostgreSQL 백업과 복구 글](/posts/oracle-cloud-postgresql-backup-restore)에서 다룹니다.

백업 형식과 목록 확인 옵션은 [PostgreSQL 16의 pg_dump](https://www.postgresql.org/docs/16/app-pgdump.html), [pg_restore 문서](https://www.postgresql.org/docs/16/app-pgrestore.html)에서 확인했습니다(2026-10-07).
