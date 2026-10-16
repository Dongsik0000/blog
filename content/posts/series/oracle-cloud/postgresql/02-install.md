---
title: "PostgreSQL 설치하고 서비스 확인하기"
slug: "oracle-cloud-postgresql-install"
category: "development"
publishedAt: "2026-10-07"
summary: "Ubuntu 저장소에서 PostgreSQL 16을 설치하고, 서비스 이름과 설정 파일 위치, 데이터가 저장되는 곳을 확인합니다."
series: "oracle-cloud"
chapter: "postgresql"
seriesOrder: 2
---
## 이 글에서 할 일

Ubuntu 24.04의 기본 저장소에서 PostgreSQL을 설치하고, 설치된 것들이 어디에 있는지 확인합니다.

## 설치

```bash
sudo apt update
sudo apt install -y postgresql
```

`postgresql`은 Ubuntu 버전에 맞는 PostgreSQL을 설치해 주는 묶음 패키지입니다. Ubuntu 24.04에서는 **PostgreSQL 16**이 설치됩니다.

```bash
psql --version
dpkg -l postgresql-16 | awk '/^ii/{print $2, $3}'
```

다음과 같은 형태로 나옵니다. 끝자리(부 버전)는 설치 시점에 따라 다릅니다.

```text
psql (PostgreSQL) 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)
postgresql-16 16.15-0ubuntu0.24.04.1
```

## 서비스 확인

설치하면 바로 실행되고, 부팅 때 자동으로 켜집니다.

```bash
systemctl status postgresql@16-main --no-pager | head -3
```

서비스 이름이 `postgresql@16-main`인 이유는 Ubuntu가 **버전(16)과 클러스터 이름**(main)으로 PostgreSQL을 관리하기 때문입니다. 한 서버에 여러 버전을 함께 설치할 수 있도록 만든 구조입니다.

```bash
pg_lsclusters
```

이 명령은 버전, 클러스터 이름, 포트, 상태, 데이터 폴더를 한 줄로 보여 줍니다.

## 파일 위치

| 무엇 | 경로 |
|---|---|
| 주 설정 | `/etc/postgresql/16/main/postgresql.conf` |
| 접속 허용 규칙 | `/etc/postgresql/16/main/pg_hba.conf` |
| 실제 데이터 | `/var/lib/postgresql/16/main/` |
| 로그 | `/var/log/postgresql/` |

데이터 폴더는 `postgres` 사용자만 접근할 수 있습니다. 데이터는 이 폴더의 파일을 직접 복사하지 않고 반드시 `pg_dump` 같은 도구로 백업합니다([백업과 복원](/posts/oracle-cloud-postgresql-backup-restore) 참고).

## 포트 확인

```bash
sudo ss -ltnp | grep 5432
```

```text
127.0.0.1:5432  ... "postgres"
```

PostgreSQL의 기본 포트는 **5432**이고, 기본 설정에서는 `127.0.0.1`(서버 자신)에서만 듣습니다. 외부에서는 접속할 수 없습니다. 이유와 설정은 [외부에 열지 않기](/posts/oracle-cloud-postgresql-network-access)에서 다룹니다.

## 메모리 설정 한 가지

```bash
sudo grep -nE '^shared_buffers|^max_connections' /etc/postgresql/16/main/postgresql.conf
```

```text
max_connections = 100
shared_buffers = 128MB
```

(실제 출력에는 앞에 줄 번호가 붙습니다.)

- `shared_buffers`: PostgreSQL이 자주 쓰는 데이터를 메모리에 담아 두는 공간. 기본값 128MB를 그대로 씁니다.
- `max_connections`: 동시에 접속할 수 있는 연결 수

개인 프로젝트처럼 데이터가 수십 MB 이하라면 기본값으로 시작해도 됩니다. 데이터베이스 크기는 다음 명령으로 확인합니다(`myapp`은 확인할 데이터베이스 이름).

```bash
sudo -u postgres psql -Atc "select pg_size_pretty(pg_database_size('myapp'))"
```

## 정리

- `sudo apt install postgresql`로 설치하면 Ubuntu 24.04에서는 PostgreSQL 16이 설치됩니다.
- 서비스 이름은 `postgresql@16-main`(버전 + 클러스터)입니다.
- 설정은 `/etc/postgresql/16/main/`, 데이터는 `/var/lib/postgresql/16/main/`에 있습니다.
- 기본 포트 5432는 기본 설정에서 `127.0.0.1`에만 열립니다.
