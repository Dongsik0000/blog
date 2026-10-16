---
title: "psql로 접속하고 둘러보기"
slug: "oracle-cloud-postgresql-psql-basics"
category: "development"
publishedAt: "2026-10-07"
summary: "PostgreSQL 기본 클라이언트 psql로 접속하는 방법과, 데이터베이스와 테이블을 둘러보는 백슬래시 명령을 정리합니다."
series: "oracle-cloud"
chapter: "postgresql"
seriesOrder: 3
---
## 이 글에서 할 일

PostgreSQL에 접속해 데이터를 보는 가장 기본적인 도구는 **psql**입니다. 터미널에서 SQL을 입력하는 프로그램입니다. 이 글에서는 접속 방법과 자주 쓰는 명령을 익힙니다.

## 관리자로 접속하기

설치 직후에는 `postgres`라는 관리자 롤만 있습니다. 리눅스에도 같은 이름의 `postgres` 사용자가 만들어집니다.

```bash
sudo -u postgres psql
```

- `sudo -u postgres`: 리눅스 사용자 `postgres`로 실행합니다.
- 비밀번호를 묻지 않는 이유는 **peer 인증** 때문입니다. "리눅스 사용자 이름과 DB 롤 이름이 같으면 통과"시키는 방식입니다. 자세한 내용은 [다음 글](/posts/oracle-cloud-postgresql-authentication)에서 다룹니다.

접속하면 프롬프트가 바뀝니다.

```text
postgres=#
```

`postgres`는 지금 접속한 데이터베이스 이름이고, `#`은 슈퍼유저라는 표시입니다(일반 사용자는 `>`).

## 백슬래시 명령

psql에는 SQL이 아닌 `\`로 시작하는 편의 명령이 있습니다.

| 명령 | 하는 일 |
|---|---|
| `\l` | 데이터베이스 목록 |
| `\c myapp` | `myapp` 데이터베이스로 이동 |
| `\dt` | 현재 데이터베이스의 테이블 목록 |
| `\d category` | `category` 테이블의 구조(열, 타입, 제약) |
| `\du` | 롤(사용자) 목록과 권한 |
| `\x` | 결과를 세로로 보기 (열이 많을 때 편함) |
| `\q` | 종료 |
| `\?` | 백슬래시 명령 도움말 |

## SQL 실행

SQL은 **세미콜론**(`;`)으로 끝내야 실행됩니다. 세미콜론 없이 Enter를 누르면 다음 줄로 이어서 입력하는 상태가 됩니다.

```sql
SELECT count(*) FROM category;
```

## 한 줄로 실행하기

psql에 들어가지 않고 명령 하나만 실행할 수도 있습니다. 스크립트나 확인 작업에 유용합니다.

```bash
sudo -u postgres psql -Atc "select datname from pg_database where not datistemplate"
```

```text
postgres
myapp
```

관리용 기본 데이터베이스 `postgres`와, 직접 만든 데이터베이스가 한 줄씩 나옵니다.

| 옵션 | 뜻 |
|---|---|
| `-c "SQL"` | 이 SQL을 실행하고 종료 |
| `-A` | 표 테두리 없이 출력 |
| `-t` | 제목 줄 없이 값만 출력 |
| `-d myapp` | 접속할 데이터베이스 |
| `-f 파일.sql` | 파일에 적힌 SQL 실행 |

## 앱 계정으로 접속하기

앱 계정은 비밀번호로 접속합니다. `-h 127.0.0.1`을 붙이면 TCP로 접속하므로 비밀번호 인증을 거칩니다.

```bash
psql -h 127.0.0.1 -U myapp_user -d myapp
```

비밀번호를 물으면 [계정을 만들 때](/posts/oracle-cloud-postgresql-app-database) 정한 값을 입력합니다. 비밀번호를 명령줄에 직접 적으면 셸 기록(history)에 남으므로, 묻는 창에 입력합니다.

## 실수를 줄이는 습관

운영 데이터베이스에서 `UPDATE`나 `DELETE`를 할 때는 **트랜잭션**으로 감싸고, 결과를 확인한 뒤 확정합니다.

```sql
BEGIN;                                   -- 트랜잭션 시작
UPDATE category SET active = false WHERE id = 10;
SELECT id, name, active FROM category WHERE id = 10;   -- 결과 확인
ROLLBACK;                                -- 취소 (맞으면 COMMIT; 으로 확정)
```

`WHERE`를 빠뜨리면 테이블 전체가 바뀝니다. `BEGIN`으로 시작해 두면 `ROLLBACK`으로 되돌릴 수 있습니다.

## 정리

- 관리자 접속은 `sudo -u postgres psql`, 앱 계정 접속은 `psql -h 127.0.0.1 -U 계정 -d DB`입니다.
- `\l`, `\c`, `\dt`, `\d 테이블`, `\du`, `\q`를 기억합니다.
- SQL은 세미콜론으로 끝냅니다.
- 운영 데이터를 바꿀 때는 `BEGIN` → 확인 → `COMMIT` 또는 `ROLLBACK`입니다.
