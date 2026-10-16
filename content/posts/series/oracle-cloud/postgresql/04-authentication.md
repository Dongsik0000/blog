---
title: "접속 인증 이해하기 (pg_hba.conf)"
slug: "oracle-cloud-postgresql-authentication"
category: "development"
publishedAt: "2026-10-07"
summary: "누가 어디서 어떤 방식으로 접속할 수 있는지 정하는 pg_hba.conf를 한 줄씩 읽고, peer와 scram-sha-256 인증의 차이를 설명합니다."
series: "oracle-cloud"
chapter: "postgresql"
seriesOrder: 4
---
## 이 글에서 할 일

[앞 글](/posts/oracle-cloud-postgresql-psql-basics)에서 `sudo -u postgres psql`은 비밀번호 없이 접속되고, `psql -h 127.0.0.1 -U myapp_user`는 비밀번호를 물었습니다. 이 차이를 만드는 파일이 **pg_hba.conf**입니다. HBA는 Host-Based Authentication(접속 위치 기반 인증)의 줄임말입니다.

## 기본 pg_hba.conf

```bash
sudo grep -vE '^\s*#|^\s*$' /etc/postgresql/16/main/pg_hba.conf
```

```text
local   all             postgres                                peer
local   all             all                                     peer
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256
local   replication     all                                     peer
host    replication     all             127.0.0.1/32            scram-sha-256
host    replication     all             ::1/128                 scram-sha-256
```

Ubuntu에서 PostgreSQL을 설치하면 위와 같은 기본 규칙이 만들어집니다. 대부분은 이 기본값 그대로 쓰면 됩니다.

## 한 줄의 구조

```text
host    all        all     127.0.0.1/32    scram-sha-256
│       │          │       │               └─ 인증 방법
│       │          │       └─ 접속해 온 주소 (local에는 없음)
│       │          └─ 사용자(롤)
│       └─ 데이터베이스
└─ 접속 방식
```

| 접속 방식 | 뜻 |
|---|---|
| `local` | 같은 서버 안에서 **유닉스 소켓**(파일처럼 생긴 통신 통로)으로 접속. `psql`에 `-h`를 안 붙이면 이 방식 |
| `host` | **TCP/IP**로 접속. `-h 127.0.0.1`처럼 주소를 주거나, 앱이 JDBC로 접속할 때 이 방식 |

PostgreSQL은 접속 요청이 오면 **위에서부터** 맞는 줄을 찾고, 처음 맞는 줄의 인증 방법을 씁니다.

## 두 가지 인증 방법

### peer: 리눅스 사용자 이름으로 확인

`local` 접속에 쓰입니다. "지금 접속을 요청한 **리눅스 사용자 이름**과 **DB 롤 이름**이 같으면 통과"입니다.

```bash
sudo -u postgres psql      # 리눅스 사용자 postgres → DB 롤 postgres. 통과
psql -U postgres           # 리눅스 사용자 ubuntu → DB 롤 postgres. 거부 (Peer authentication failed)
```

서버에 SSH로 들어와 `sudo`까지 쓸 수 있는 사람만 관리자로 접속할 수 있으므로, 별도의 DB 비밀번호 없이도 안전합니다.

### scram-sha-256: 비밀번호로 확인

`host` 접속에 쓰입니다. 비밀번호를 확인하되, **비밀번호 자체를 네트워크로 보내지 않는** 안전한 방식(SCRAM)입니다. 서버에도 비밀번호 원문이 아니라 검증용 값만 저장됩니다.

앱이 다음 주소로 접속하면 이 줄에 해당합니다.

```text
jdbc:postgresql://127.0.0.1:5432/myapp
```

`127.0.0.1/32`는 "정확히 127.0.0.1 하나"입니다(`/32`는 32비트 전부 고정). 그리고 `::1/128`은 IPv6의 127.0.0.1입니다.

## 외부 접속은 왜 줄이 없나

pg_hba.conf에는 `127.0.0.1`과 `::1`(서버 자신)만 있습니다. 다른 주소에서 오는 접속은 **맞는 줄이 없어서 거부**됩니다. 그 전에 PostgreSQL이 아예 `127.0.0.1`에서만 듣고 있어서 외부 요청은 도착하지도 않습니다. 이중으로 막혀 있는 셈입니다.

## 이 파일을 고쳤다면

pg_hba.conf를 바꾼 뒤에는 설정을 다시 읽게 해야 합니다.

```bash
sudo systemctl reload postgresql@16-main
```

`reload`는 접속을 끊지 않고 설정만 다시 읽습니다.

## 정리

- pg_hba.conf는 **접속 방식, DB, 사용자, 주소, 인증 방법**을 한 줄에 적고, 위에서부터 처음 맞는 줄을 씁니다.
- `local` + `peer`: 리눅스 사용자와 DB 롤 이름이 같으면 통과. 관리자 접속에 씁니다.
- `host` + `scram-sha-256`: 안전한 비밀번호 인증. 앱 접속에 씁니다.
- 기본 구성은 서버 자신(`127.0.0.1`, `::1`)에서 오는 접속만 허용합니다.
