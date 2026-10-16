---
title: "번호 붙인 SQL 파일로 스키마 바꾸기"
slug: "ledger-database-migrations"
category: "development"
publishedAt: "2026-10-08"
summary: "db 폴더의 000부터 009까지 SQL 파일이 각각 무엇을 바꿨는지, 이미 데이터가 있는 운영 DB에 안전하게 적용하는 순서와 IF NOT EXISTS로 다시 실행해도 안전하게 만드는 방법을 정리합니다."
series: "ledger"
chapter: "database"
seriesOrder: 3
---
## 이 글에서 할 일

앱을 만들다 보면 테이블에 컬럼을 추가하거나 새 테이블이 필요해집니다. 이미 데이터가 쌓인 운영 DB는 지우고 다시 만들 수 없으므로, **바뀐 부분만** 적용해야 합니다. Ledger는 바뀐 부분을 번호 붙인 SQL 파일로 남겼습니다.

## 파일 목록

| 파일 | 바꾼 것 | 기능 |
|---|---|---|
| `000_create_db.sql` | 앱 계정과 DB 생성 (관리자로 한 번) | |
| `001_schema.sql` | 첫 테이블 8개, 인덱스 | 기본 가계부 |
| `002_seed_holiday_2026.sql` | 2026년 공휴일 데이터 | 결제일 보정 |
| `003_comments.sql` | 테이블과 컬럼 주석 | [주석](/posts/ledger-database-comments) |
| `004_recurring_installment.sql` | `recurring_item`에 할부 컬럼과 CHECK | 카드 할부 |
| `005_opening_balance.sql` | `app_user`에 시작 잔액 | 시작 잔액 |
| `006_market_valuation.sql` | 달러 결제, 주식 컬럼 | 외화, 주식 평가 |
| `007_asset_transfers.sql` | 거래와 적금 자산 연결 | 적금 자동 이체 |
| `008_bank_account.sql` | 통장, 이체, 정기 이체 테이블 | 통장 |
| `009_default_account.sql` | 기본 통장, 거래의 통장 컬럼 | 기본 통장 |

**번호 = 적용 순서**입니다. 004는 001에서 만든 `recurring_item`이 있어야 실행되고, 009는 008의 `bank_account`가 있어야 실행됩니다.

## 파일 하나의 모양

```sql
-- 시작 잔액: 기준일이 시작될 때의 잔액. 잔액(이월·주기 잔액·누적)은 기준일 이전 거래를 빼고 이 금액부터 센다.
-- 적용: psql -h 127.0.0.1 -U ledger_app -d ledger -f db/005_opening_balance.sql

ALTER TABLE app_user
    ADD COLUMN opening_balance BIGINT NOT NULL DEFAULT 0
        CHECK (opening_balance BETWEEN -99999999999 AND 99999999999),
    ADD COLUMN opening_date DATE;

COMMENT ON COLUMN app_user.opening_balance IS '시작 잔액(원, 음수 가능). opening_date 가 없으면 쓰지 않는다';
```

모든 파일이 같은 세 부분으로 되어 있습니다.

1. **무엇을 왜 바꾸는지** 주석
2. **적용 명령** 주석. 누가 언제 열어도 그대로 따라 할 수 있게
3. 변경 SQL과 새 컬럼의 주석

### 기존 행이 있는 테이블에 NOT NULL 컬럼 추가

`opening_balance`는 `NOT NULL`인데, 이미 있는 사용자 행에는 값이 없습니다. `DEFAULT 0`을 함께 주면 기존 행에 0이 채워지므로 오류 없이 추가됩니다. 기본값 없이 `NOT NULL` 컬럼을 추가하면 기존 행 때문에 실패합니다.

## 다시 실행해도 안전하게: IF NOT EXISTS

`009_default_account.sql`

```sql
-- 이 파일의 이전 초안(is_default·신용카드 제약만 추가)을 적용한 DB에서도 그대로 실행되도록 IF [NOT] EXISTS 를 쓴다.
ALTER TABLE bank_account
    DROP CONSTRAINT IF EXISTS bank_account_default_bank,
    DROP COLUMN IF EXISTS payment_method_id,
    DROP COLUMN IF EXISTS kind,
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS bank_account_one_default ON bank_account (user_id) WHERE is_default;
```

통장 기능은 설계가 한 번 바뀌었습니다. 처음에는 통장을 결제수단(체크카드)에 연결했다가, 통장과 결제수단을 떼어 거래에서 통장을 직접 고르게 바꿨습니다. 009는 **초안을 적용한 DB와 적용하지 않은 DB 모두에서** 같은 결과가 나와야 했습니다. `IF EXISTS`, `IF NOT EXISTS`는 "있으면/없으면 할 때만" 실행해, 어느 상태에서 실행해도 오류 없이 같은 결과를 만듭니다. 이런 성질을 **멱등성**(여러 번 해도 결과가 같음)이라고 합니다.

## 운영 DB에 적용하는 순서

README에 적어 둔 원칙입니다.

> 기존 서버를 올릴 때는 **새 WAR를 배포하기 전에** `db/006_market_valuation.sql`과 `db/007_asset_transfers.sql`을 순서대로 적용

순서가 중요한 이유는 다음과 같습니다.

```text
✗ 앱 먼저 배포 → 새 코드가 없는 컬럼(transfer_asset_id)을 SELECT → 500 오류
✓ SQL 먼저 적용 → 옛 코드는 새 컬럼을 무시하고 정상 동작 → 앱 배포 → 새 컬럼 사용
```

**컬럼 추가는 SQL을 먼저**, 반대로 컬럼 삭제는 그 컬럼을 쓰지 않는 앱을 먼저 배포하고 SQL을 나중에 합니다. 그래야 배포하는 동안 옛 버전과 새 버전 어느 쪽도 깨지지 않습니다.

적용 전에는 반드시 백업합니다.

```bash
sudo -u postgres pg_dump -Fc ledger > ledger-before-xxx.dump
```

백업과 복구 방법은 [Oracle Cloud 시리즈의 백업 글](/posts/oracle-cloud-postgresql-backup-restore)에 있습니다.

## 새 DB와 기존 DB 구분하기

새 DB에는 `000`부터 `009`까지 순서대로 적용합니다. 완성된 앱의 조회가 뒤 번호에서 추가한 컬럼도 사용하므로, 첫 스키마만 적용하고 실행을 마치면 안 됩니다. 전체 명령은 [로컬 실행 글](/posts/ledger-intro-local-run)에 있습니다.

기존 DB에는 이미 적용한 파일을 다시 실행하지 않습니다. Ledger의 배포 스크립트는 SQL을 자동 적용하지 않으므로, 서버의 적용 기록과 실제 테이블·컬럼을 확인해 필요한 파일만 실행합니다. `009`의 `IF [NOT] EXISTS` 사용은 해당 파일의 처리 방식이며, 모든 SQL 파일을 반복 실행해도 된다는 뜻은 아닙니다.

## 정리

- 스키마 변경은 번호 붙인 SQL 파일로 남기고, 번호 순서대로 적용합니다.
- 파일마다 이유와 적용 명령을 주석으로 적습니다.
- 설계가 바뀐 `009`는 `IF [NOT] EXISTS`로 초안을 적용한 DB와 아닌 DB에서 같은 결과가 나오게 했습니다. 이미 적용한 파일은 다시 실행하지 않습니다.
- 컬럼 추가는 SQL 먼저, 앱 배포는 나중에. 적용 전에는 백업합니다.
