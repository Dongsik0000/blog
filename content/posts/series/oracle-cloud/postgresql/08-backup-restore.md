---
title: "매일 자동 백업하고 복원하기"
slug: "oracle-cloud-postgresql-backup-restore"
category: "development"
publishedAt: "2026-10-07"
summary: "pg_dump로 데이터베이스를 파일로 백업하는 스크립트를 만들고 systemd timer로 매일 실행하는 방법, pg_restore로 되살리는 방법, 백업 구성 점검표를 정리합니다."
series: "oracle-cloud"
chapter: "postgresql"
seriesOrder: 8
---
## 이 글에서 할 일

데이터베이스는 실수로 지운 데이터, 잘못된 마이그레이션, 디스크 고장으로 언제든 망가질 수 있습니다. 이 글에서는 데이터베이스를 **매일 새벽 자동으로 백업**하는 구성을 만들고, 백업에서 데이터를 되살리는 방법을 익힙니다. 예시의 데이터베이스 이름은 `myapp`입니다.

## pg_dump: 데이터베이스를 파일로

`pg_dump`는 데이터베이스의 구조(테이블 정의)와 데이터를 **한 파일로** 내보냅니다. 실행 중인 데이터베이스에서 바로 떠도 일관된 시점의 내용이 담깁니다.

```bash
sudo -u postgres pg_dump -Fc myapp > myapp.dump
```

- `-Fc`: **custom 형식**. 압축되어 작고, 복원할 때 원하는 테이블만 골라 되살릴 수 있습니다. 이 형식은 `pg_restore`로 복원합니다.
- 옵션 없이 쓰면 사람이 읽을 수 있는 SQL 텍스트로 나오고, `psql -f`로 복원합니다.

## 백업 스크립트 만들기

앱 저장소에 `deploy/backup.sh`로 두면 버전 관리가 됩니다.

```bash
#!/usr/bin/env bash
# 매일 pg_dump 백업. 30일 보관.
set -euo pipefail
umask 077   # 덤프에 회원 정보 등이 그대로 있으므로 root만 읽게

BACKUP_DIR=/var/backups/myapp
mkdir -p "$BACKUP_DIR"

FILE="$BACKUP_DIR/myapp-$(date +%F-%H%M%S).dump"
trap 'rm -f "$FILE.tmp"' EXIT   # 실패하면 쓰다 만 임시 파일을 지움

sudo -u postgres pg_dump -Fc myapp > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"
find "$BACKUP_DIR" -name 'myapp-*.dump' -mtime +30 -delete
```

| 줄 | 뜻 |
|---|---|
| `set -euo pipefail` | 오류가 나면 즉시 멈춤. 백업이 실패했는데 성공한 것처럼 넘어가지 않게 합니다 (자세한 뜻은 [배포 스크립트](/posts/oracle-cloud-deploy-deploy-script)에서) |
| `umask 077` | 새 파일을 root만 읽을 수 있게 만듦 ([권한 글](/posts/oracle-cloud-linux-users-permissions) 참고) |
| `FILE=...myapp-$(date +%F-%H%M%S).dump` | 날짜와 시각이 들어간 파일 이름 (예: `myapp-2026-10-07-041000.dump`). 같은 날 다시 실행해도 기존 백업과 이름이 겹치지 않습니다 |
| `trap 'rm -f "$FILE.tmp"' EXIT` | 스크립트가 끝날 때 임시 파일이 남아 있으면 지움. 덤프가 실패했을 때 쓰다 만 파일이 쌓이지 않게 합니다 |
| `pg_dump -Fc myapp > "$FILE.tmp"` | 먼저 **임시 파일**에 백업 |
| `mv "$FILE.tmp" "$FILE"` | 덤프가 성공했을 때만 최종 이름으로 바꿈. 실패하면 `set -e` 때문에 이 줄까지 오지 않습니다 |
| `find ... -mtime +30 -delete` | 30일보다 오래된 백업 삭제. 디스크가 백업으로 가득 차지 않게 합니다 |

### 왜 임시 파일에 먼저 쓰는가

셸은 `> 파일`을 만나면 명령을 실행하기 **전에** 그 파일을 먼저 비웁니다. 날짜만 넣은 이름(`myapp-2026-10-07.dump`)에 바로 쓰면, 같은 날 다시 실행했을 때 아침의 정상 백업이 먼저 비워집니다. 이어서 `pg_dump`가 실패하면 정상 백업도, 새 백업도 없는 상태가 됩니다. `set -e`는 실패한 뒤에 멈출 뿐이라 이미 비워진 파일을 되살리지 못합니다.

그래서 이름이 겹치지 않게 시각까지 넣고, 임시 파일에 다 쓴 뒤 성공했을 때만 이름을 바꿉니다. 그러면 백업 폴더의 `.dump` 파일은 **끝까지 성공한 백업**만 남습니다.

### 설치

```bash
sudo install -m 750 deploy/backup.sh /usr/local/bin/myapp-backup
```

`install`은 복사와 권한 설정을 한 번에 합니다. `750`은 root만 쓰고 실행하며, 그룹은 읽기·실행, 나머지는 아무것도 못 하게 합니다.

## 매일 실행: systemd timer

[systemd 글](/posts/oracle-cloud-linux-systemd)의 예시 그대로 `myapp-backup.service`와 `myapp-backup.timer`를 만들어 **매일 04:10(한국 시간)** 실행합니다. `Persistent=true`라서 그 시각에 서버가 꺼져 있었다면 다음에 켜질 때 실행합니다.

### 백업이 잘 되고 있는지 확인

```bash
systemctl list-timers myapp-backup.timer --no-pager     # 마지막 실행, 다음 실행
journalctl -u myapp-backup.service --no-pager -n 3      # 마지막 실행 결과
sudo ls -la /var/backups/myapp | tail -3                 # 최근 백업 파일
```

백업 파일이 하루에 하나씩 늘고, `.tmp` 파일이 남아 있지 않고, 30일보다 오래된 파일은 없어야 정상입니다.

## 복원하기

**복원은 기존 데이터를 덮어쓸 수 있습니다.** 운영 데이터베이스에 바로 하지 말고, 먼저 **새 데이터베이스에 복원해서 확인**합니다.

```bash
# 1. 확인용 빈 데이터베이스 만들기
sudo -u postgres createdb myapp_restore_test

# 2. 백업 파일 복원
sudo cat /var/backups/myapp/myapp-2026-10-07-041000.dump | sudo -u postgres pg_restore -d myapp_restore_test

# 3. 데이터 확인 (테이블 이름은 앱에 맞게)
sudo -u postgres psql -d myapp_restore_test -Atc "select count(*) from app_user"

# 4. 확인이 끝나면 삭제
sudo -u postgres dropdb myapp_restore_test
```

2번에서 파일을 `sudo cat`으로 넘기는 이유는, 백업 파일은 root만 읽을 수 있고(`umask 077`) `pg_restore`는 `postgres` 사용자로 실행되기 때문입니다.

**백업은 복원해 봐야 의미가 있습니다.** 한 달에 한 번 정도 이 절차로 복원 시험을 하는 것을 권합니다.

## 마이그레이션 전 수동 백업

테이블 구조를 바꾸는 SQL을 실행하기 전에는 손으로 한 번 더 백업합니다.

```bash
sudo -u postgres pg_dump -Fc myapp | sudo tee /var/backups/myapp/manual/before-add-column-$(date +%F).dump > /dev/null
```

이때 **자동 삭제 패턴(`myapp-*.dump`)과 겹치지 않는 이름이나 폴더**에 둡니다. 같은 패턴이면 30일 뒤 자동으로 지워집니다. `manual` 폴더는 미리 `sudo mkdir -p -m 700`으로 만들어 둡니다.

## 백업 구성 점검표

| 점검 항목 | 확인할 것 |
|---|---|
| 모든 데이터베이스 | 앱이 여러 개라면 데이터베이스마다 백업이 있는가 |
| 데이터베이스 밖의 파일 | 사용자가 올린 이미지 같은 파일 폴더도 백업하는가 (`tar`로 압축) |
| 보관 위치 | 같은 서버에만 있으면 서버를 잃을 때 함께 사라짐. 다른 곳(Object Storage, 내 PC)에도 복사하는가 |
| 보관 기간 | 오래 보관할 백업이 자동 삭제 대상에 들어 있지 않은가 |
| 복원 시험 | 마지막으로 복원해 본 날짜가 언제인가 |

## 정리

- `pg_dump -Fc`로 데이터베이스를 압축된 파일 하나로 백업합니다.
- 스크립트는 `set -euo pipefail`, `umask 077`, 시각까지 넣은 파일명, 임시 파일, 보관 기간으로 안전하게 만듭니다.
- 매일 실행은 systemd timer가 맡습니다.
- 복원은 **새 데이터베이스에 먼저** 해서 확인하고, 주기적으로 복원 시험을 합니다.
- 백업은 다른 장소에도 두어야 서버를 잃었을 때 살아남습니다.
