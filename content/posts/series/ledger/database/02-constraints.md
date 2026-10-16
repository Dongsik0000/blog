---
title: "제약 조건으로 데이터 지키기"
slug: "ledger-database-constraints"
category: "development"
publishedAt: "2026-10-08"
summary: "PRIMARY KEY, FOREIGN KEY와 ON DELETE, UNIQUE, CHECK, 부분 유니크 인덱스를 Ledger 스키마에서 찾아보며, 앱 코드와 DB가 함께 데이터를 지키는 방법을 설명합니다."
series: "ledger"
chapter: "database"
seriesOrder: 2
---
## 이 글에서 할 일

Controller가 입력을 검사해도, 버그나 직접 실행한 SQL로 잘못된 데이터가 들어갈 수 있습니다. **제약 조건**은 DB가 직접 "이런 데이터는 받지 않는다"고 막는 마지막 방어선입니다.

## PRIMARY KEY: 한 줄을 구분하는 값

```sql
id BIGSERIAL PRIMARY KEY,
```

`BIGSERIAL`은 1, 2, 3처럼 자동으로 늘어나는 정수입니다. 기본 키는 중복될 수 없고 비어 있을 수 없습니다.

기본 키가 꼭 `id` 하나일 필요는 없습니다.

```sql
CREATE TABLE recurring_run (
    recurring_id BIGINT  NOT NULL REFERENCES recurring_item(id) ON DELETE CASCADE,
    period_ym    CHAR(7) NOT NULL,                       -- 'YYYY-MM'
    ...
    PRIMARY KEY (recurring_id, period_ym)
);
```

**두 컬럼을 합친 기본 키**입니다. "고정 항목 3번의 2026-10월분"은 한 줄만 있을 수 있습니다. 스케줄러가 어떤 이유로 두 번 돌아도, 두 번째 기록은 DB가 거부하므로 같은 달 거래가 두 번 생기지 않습니다. 매퍼는 이것을 `ON CONFLICT ... DO NOTHING`으로 받아 "이미 있으면 아무것도 안 함"으로 처리합니다.

```xml
INSERT INTO recurring_run (recurring_id, period_ym, entry_id, fx_rate, fx_as_of)
VALUES (...)
ON CONFLICT (recurring_id, period_ym) DO NOTHING
```

넣은 줄 수가 0이면 이미 처리된 달이라는 뜻입니다.

## FOREIGN KEY: 다른 테이블을 가리키는 값

```sql
category_id BIGINT NOT NULL REFERENCES category(id),
```

`category_id`에는 `category` 테이블에 **실제로 있는 id**만 넣을 수 있습니다. 그리고 거래가 쓰고 있는 카테고리는 지울 수 없습니다. 앱이 "쓰고 있으면 삭제 대신 숨김"으로 처리하는 이유이기도 합니다.

### ON DELETE: 가리키던 것이 지워지면

| 옵션 | 동작 | Ledger에서 |
|---|---|---|
| (없음) | 가리키는 행이 있으면 **삭제를 막음** | 거래 → 카테고리 |
| `ON DELETE CASCADE` | 함께 지움 | 고정 항목을 지우면 그 처리 기록(`recurring_run`)도 지움 |
| `ON DELETE SET NULL` | 가리키던 값을 NULL로 | 거래를 지우면 `recurring_run.entry_id`를 NULL로 |

마지막 줄이 재미있는 설계입니다.

```sql
-- 생성된 거래를 지워도 이 행은 남겨서 같은 달에 다시 만들지 않는다 (= 이번 달 건너뛰기).
entry_id BIGINT REFERENCES ledger_entry(id) ON DELETE SET NULL,
```

자동 기록된 월세 거래를 사용자가 지우면, 처리 기록은 남고 `entry_id`만 비워집니다. 스케줄러는 "그 달은 처리됨"으로 보고 다시 만들지 않습니다. 사용자가 "이번 달은 건너뛰기"를 따로 누르지 않아도, 거래를 지우는 것만으로 건너뛰기가 됩니다.

적금 자산 연결도 같은 방식입니다.

```sql
-- 지출 거래와 현금성 자산의 연결. 삭제된 자산은 연결만 해제하고 거래 기록은 보존한다.
ALTER TABLE ledger_entry ADD COLUMN transfer_asset_id BIGINT REFERENCES asset(id) ON DELETE SET NULL;
```

## UNIQUE: 중복 금지

```sql
UNIQUE (user_id, name, type)    -- category
UNIQUE (user_id, name)          -- payment_method
```

**사용자별로** 같은 이름의 카테고리를 막습니다. `name`만 UNIQUE로 두면 다른 사용자끼리도 이름이 겹칠 수 없게 되어 틀립니다. 카테고리는 수입 "기타"와 지출 "기타"가 따로 있을 수 있게 `type`도 넣었습니다.

앱도 저장 전에 같은 이름을 검사해 "같은 이름의 카테고리가 있어요"라고 알려 줍니다. DB 제약은 그 검사가 빠졌거나 두 요청이 동시에 들어왔을 때를 대비합니다.

### 부분 유니크 인덱스: 조건부 중복 금지

```sql
CREATE UNIQUE INDEX IF NOT EXISTS bank_account_one_default ON bank_account (user_id) WHERE is_default;
```

"사용자당 **기본 통장은 하나**"라는 규칙입니다. `WHERE is_default`가 붙어 기본 통장인 행에만 유니크가 걸립니다. 일반 통장은 여러 개 있어도 됩니다.

## CHECK: 값의 범위와 규칙

```sql
pay_day SMALLINT NOT NULL DEFAULT 1 CHECK (pay_day BETWEEN 1 AND 31),
type    VARCHAR(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
amount  BIGINT NOT NULL CHECK (amount > 0),
CHECK (from_account_id <> to_account_id)   -- 같은 통장으로 이체 금지
```

여러 컬럼이 함께 맞아야 하는 규칙도 CHECK로 표현합니다.

`004_recurring_installment.sql`

```sql
ADD CONSTRAINT recurring_item_installment_check CHECK (
    (installment_total IS NULL AND installment_months IS NULL AND installment_start IS NULL)
    OR (installment_months BETWEEN 2 AND 60
        AND installment_total >= installment_months
        AND installment_start ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'
        AND type = 'EXPENSE')
);
```

읽어 보면 "할부 정보는 **셋 다 없거나**, 셋 다 있고 다음을 만족해야 한다"입니다.

- 개월 수 2~60
- 총액이 개월 수 이상 (한 달에 최소 1원)
- 첫 결제월이 `YYYY-MM` 형식 (`~`는 PostgreSQL 정규식 일치)
- 지출일 것 (수입 할부는 없음)

"총액만 있고 개월 수가 없는" 반쪽 데이터를 DB가 원천적으로 막습니다.

## 앱 검사와 DB 제약의 역할 나누기

| | 앱(Controller) 검사 | DB 제약 |
|---|---|---|
| 목적 | 사용자에게 친절한 안내 | 어떤 경로로도 잘못된 데이터가 못 들어오게 |
| 실패하면 | `code: 90` + 안내 문장 | SQL 예외 → 500 |
| 동시 요청 | 놓칠 수 있음 | 막음 |

둘 다 둡니다. 앱 검사가 먼저 걸러서 사용자는 친절한 안내를 보고, DB 제약은 앱 검사를 빠져나간 경우의 안전망입니다.

## 정리

- 복합 기본 키 `(recurring_id, period_ym)`로 같은 달 중복 기록을 막습니다.
- FK의 `ON DELETE`로 삭제 시 동작을 정합니다. `SET NULL`로 "거래를 지우면 건너뛰기"를 구현했습니다.
- UNIQUE는 사용자별로, 부분 유니크 인덱스로 "기본 통장은 하나"를 강제합니다.
- CHECK로 값 범위와 여러 컬럼의 규칙을 DB가 직접 지킵니다.
