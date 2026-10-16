---
title: "테이블과 컬럼 주석"
slug: "ledger-database-comments"
category: "development"
publishedAt: "2026-10-08"
summary: "COMMENT ON으로 테이블과 컬럼에 설명을 붙여 DB 자체를 문서로 만드는 방법과, DBeaver와 psql에서 주석을 확인하는 법을 설명합니다."
series: "ledger"
chapter: "database"
seriesOrder: 4
---
## 이 글에서 할 일

몇 달 뒤 DB를 열었을 때 `pay_day_adjust`가 무엇인지, `period_ym`이 어떤 형식인지 기억나지 않을 수 있습니다. 설명을 별도 문서에 두면 DB와 문서가 따로 놉니다. PostgreSQL은 **테이블과 컬럼에 직접 설명을 붙이는** 기능이 있습니다.

## COMMENT ON

`db/003_comments.sql` (일부)

```sql
-- 테이블/컬럼 주석. DBeaver 에서 테이블을 열면 Description 열에 표시된다.

COMMENT ON TABLE app_user IS '사용자 계정. 모든 데이터는 이 id 로 격리된다';
COMMENT ON COLUMN app_user.password_hash  IS 'BCrypt 해시 (평문 저장 안 함)';
COMMENT ON COLUMN app_user.pay_day        IS '주기 시작일 1~31. 급여일 기준으로 요약/잔액/일일예산을 계산한다. 1이면 달력 월과 동일';
COMMENT ON COLUMN app_user.pay_day_adjust IS 'true 면 주기 시작일이 주말/공휴일일 때 직전 평일로 당긴다';

COMMENT ON TABLE category IS '수입/지출 분류. 이름을 바꿔도 거래는 id 로 참조하므로 과거 데이터가 어긋나지 않는다';
```

| 문법 | 붙는 곳 |
|---|---|
| `COMMENT ON TABLE 테이블 IS '설명'` | 테이블 |
| `COMMENT ON COLUMN 테이블.컬럼 IS '설명'` | 컬럼 |

주석은 데이터에 영향을 주지 않으므로 운영 DB에 언제 적용해도 안전합니다.

## 무엇을 적나

좋은 주석은 컬럼 이름만으로 알 수 없는 것을 적습니다.

| 적을 것 | 예 |
|---|---|
| 값의 형식과 범위 | `'주기 시작일 1~31'`, `'YYYY-MM'` |
| 특별한 값의 뜻 | `'1이면 달력 월과 동일'`, `'NULL 이면 기본 통장'` |
| 왜 이렇게 했나 | `'이름을 바꿔도 거래는 id 로 참조하므로 과거 데이터가 어긋나지 않는다'` |
| 보안상 주의 | `'BCrypt 해시 (평문 저장 안 함)'` |

반대로 `id IS 'id'`처럼 이름을 반복하는 주석은 적지 않습니다.

## 새 컬럼에는 같은 파일에서 주석까지

003 이후의 스키마 변경 파일은 컬럼을 추가하면서 **같은 파일 안에서** 주석도 붙입니다.

`db/009_default_account.sql` (일부)

```sql
COMMENT ON COLUMN bank_account.is_default IS '기본 통장(사용자당 하나). 기준 잔액·기준일은 쓰지 않는다';
COMMENT ON COLUMN ledger_entry.account_id IS '돈이 들어오고 나간 통장. NULL 이면 기본 통장';
```

주석을 나중에 따로 달려고 미루면 잊어버립니다. 컬럼을 만드는 순간 설명도 함께 남깁니다.

## 주석 확인하기

### psql

```sql
\d+ app_user
```

`\d+`는 컬럼 목록에 `Description` 열을 함께 보여 줍니다.

### DBeaver

테이블을 열고 **Columns** 탭을 보면 `Description` 열에 주석이 보입니다. 접속 방법은 [Oracle Cloud 시리즈의 DBeaver 글](/posts/oracle-cloud-postgresql-dbeaver)에 있습니다.

<!-- 📷 스크린샷: DBeaver Columns 탭의 Description -->

## 정리

- `COMMENT ON TABLE/COLUMN ... IS '...'`로 DB에 직접 설명을 붙입니다.
- 형식, 특별한 값의 뜻, 설계 이유처럼 이름만으로 알 수 없는 것을 적습니다.
- 새 컬럼은 추가하는 SQL 파일에서 주석까지 함께 붙입니다.
- psql은 `\d+`, DBeaver는 Columns 탭에서 확인합니다.
