---
title: "앱 전용 데이터베이스와 계정 만들기"
slug: "oracle-cloud-postgresql-app-database"
category: "development"
publishedAt: "2026-10-07"
summary: "앱마다 데이터베이스와 로그인 계정을 만드는 SQL을 실행하고, 앱 계정을 주인으로 두는 방식과 데이터 조작 권한만 주는 방식을 비교합니다."
series: "oracle-cloud"
chapter: "postgresql"
seriesOrder: 5
---
## 이 글에서 할 일

새 앱을 서버에 올릴 때마다 하는 일입니다. 앱 전용 **롤**(계정)과 **데이터베이스**를 만들고, 앱이 필요한 만큼만 권한을 줍니다. 권한을 주는 대표적인 방식 두 가지를 비교합니다. 예시의 앱 이름은 `myapp`입니다.

모든 SQL은 관리자로 접속해 실행합니다.

```bash
sudo -u postgres psql
```

## 방식 1. 앱 계정이 데이터베이스의 주인

```sql
CREATE ROLE myapp_user LOGIN PASSWORD '<비밀번호>';
CREATE DATABASE myapp OWNER myapp_user ENCODING 'UTF8';
```

| 줄 | 뜻 |
|---|---|
| `CREATE ROLE myapp_user LOGIN PASSWORD '...'` | 로그인할 수 있는 롤을 만들고 비밀번호를 정합니다 |
| `CREATE DATABASE myapp OWNER myapp_user` | `myapp` 데이터베이스를 만들고 **주인을 myapp_user로** 정합니다 |
| `ENCODING 'UTF8'` | 한글을 저장하려면 UTF8이어야 합니다 |

주인이므로 `myapp_user`는 이 데이터베이스 안에서 테이블을 만들고, 고치고, 지울 수 있습니다. 그래서 테이블을 만드는 SQL도 앱 계정으로 실행합니다.

```bash
psql -h 127.0.0.1 -U myapp_user -d myapp -f schema.sql
```

**장점**: 단순합니다. 테이블 구조를 바꾸는 SQL(마이그레이션)도 앱 계정 하나로 실행합니다.
**단점**: 앱 코드에 SQL 주입 같은 취약점이 있으면 공격자가 `DROP TABLE`까지 할 수 있습니다.

## 방식 2. 데이터 조작 권한만 주기

데이터베이스와 테이블은 관리자(`postgres`)가 만들고 소유합니다. 앱 계정에는 **데이터를 읽고 쓰는 권한만** 줍니다.

```sql
CREATE ROLE myapp_user LOGIN PASSWORD '<비밀번호>';
CREATE DATABASE myapp ENCODING 'UTF8';               -- 주인은 실행한 postgres
\c myapp
\i schema.sql                                         -- 관리자로 테이블 생성
GRANT USAGE ON SCHEMA public TO myapp_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO myapp_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO myapp_user;
```

| 줄 | 뜻 |
|---|---|
| `\i schema.sql` | psql에서 파일의 SQL을 실행 (관리자 권한으로 테이블 생성) |
| `GRANT USAGE ON SCHEMA public` | `public` 스키마 안의 것을 쓸 수 있게 함 (만들 수는 없음) |
| `GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES` | **지금 있는** 모든 테이블에 데이터 조작 권한 |
| `GRANT USAGE, SELECT ON ALL SEQUENCES` | 자동 증가 번호(`id`)를 쓸 수 있게 함. 이 권한이 없으면 INSERT가 실패합니다 |

**장점**: 앱 코드가 뚫려도 테이블 구조를 바꾸거나 지울 수 없습니다.
**단점**: `ON ALL TABLES`는 **실행한 시점에 있던 테이블에만** 적용됩니다. 테이블을 새로 만들면 권한을 다시 줘야 합니다.

이 단점은 **기본 권한**을 정해 두면 줄일 수 있습니다. 관리자가 앞으로 만드는 테이블과 시퀀스에 자동으로 권한이 붙습니다.

```sql
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO myapp_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO myapp_user;
```

이 설정은 **이 명령을 실행한 롤이 만드는 객체**에만 적용됩니다. 관리자로 실행했다면 이후에도 관리자로 테이블을 만들어야 합니다.

## 권한 확인하기

앱 계정에 어떤 권한이 있는지 확인합니다.

```bash
sudo -u postgres psql -d myapp -Atc \
  "select privilege_type from information_schema.role_table_grants
   where table_schema='public' and grantee='myapp_user' group by 1 order by 1"
```

방식 2라면 다음 네 줄이 나와야 합니다.

```text
DELETE
INSERT
SELECT
UPDATE
```

테이블을 만들 권한이 없는지도 확인합니다.

```bash
sudo -u postgres psql -d myapp -Atc "select has_schema_privilege('myapp_user','public','CREATE')"
```

`f`(false)가 나오면 만들 수 없는 상태입니다. PostgreSQL 15부터는 `public` 스키마에 일반 사용자가 테이블을 만들 수 없는 것이 기본값입니다.

## 어느 방식을 쓸까

| 상황 | 추천 |
|---|---|
| 혼자 만드는 작은 앱, 구조가 자주 바뀜 | 방식 1 (단순함) |
| 공개 서비스, 보안이 중요함 | 방식 2 (최소 권한) |

## 비밀번호 관리

- 비밀번호는 SQL 파일에 적어 저장소에 올리지 않습니다. 저장소에는 `'<비밀번호>'`처럼 자리만 남깁니다.
- 앱은 서버의 비밀값 파일(예: `/etc/myapp/app.env`)에서 비밀번호를 읽습니다([compose 글](/posts/oracle-cloud-docker-compose) 참고).
- 비밀번호를 바꿀 때는 `ALTER ROLE myapp_user PASSWORD '<새 비밀번호>';` 후 비밀값 파일도 함께 고치고 앱을 다시 띄웁니다.
- 슈퍼유저 권한을 가진 관리용 계정은 앱 설정에 두지 않고, 꼭 필요할 때만 씁니다.

## 정리

- 앱마다 `CREATE ROLE ... LOGIN PASSWORD`와 `CREATE DATABASE ... ENCODING 'UTF8'`로 전용 계정과 DB를 만듭니다.
- **방식 1**: 앱 계정을 DB 주인으로. 단순하지만 권한이 큽니다.
- **방식 2**: 관리자가 주인, 앱에는 SELECT/INSERT/UPDATE/DELETE와 시퀀스 권한만. 안전하지만 새 테이블 권한을 챙겨야 하며, `ALTER DEFAULT PRIVILEGES`로 줄일 수 있습니다.
- 비밀번호는 저장소에 남기지 않고 서버의 비밀값 파일에만 둡니다.
