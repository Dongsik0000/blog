---
title: "테이블 설계 (사진, 카테고리 다대다, 제품 링크)"
slug: "fashion-archive-database-schema"
category: "development"
publishedAt: "2026-10-08"
summary: "users, category, photo, photo_category, product_link 다섯 테이블의 관계와, 사진 하나가 여러 카테고리에 속하는 다대다 관계를 연결 테이블로 푼 방법, ON DELETE CASCADE와 인덱스를 schema.sql로 설명합니다."
series: "fashion-archive"
chapter: "database"
seriesOrder: 1
---
## 이 글에서 할 일

Fashion Archive의 테이블은 다섯 개입니다. 테이블 정의는 `src/main/resources/schema.sql` 한 파일에 있습니다. 이 글에서는 테이블 사이의 관계, 특히 **사진 하나가 여러 카테고리에 속하는 다대다 관계**를 봅니다.

## 관계도

```text
users ─┬─< photo ─┬─< photo_category >─ category
       │          │
       │          └─< product_link
       │
  (owner_id)

──<  : 하나가 여러 개를 가짐 (1:N)
>─   : 여러 개가 하나를 가리킴
```

| 테이블 | 역할 |
|---|---|
| `users` | 계정. 아이디, BCrypt 해시, 역할(`ADMIN`, `USER`) |
| `category` | 카테고리 6개 (봄, 여름, 가을, 겨울, 악세사리, 신발) |
| `photo` | 사진. 주인(`owner_id`), 제목, 메모, 사진 주소 |
| `photo_category` | 사진과 카테고리의 연결 |
| `product_link` | 사진 속 아이템과 비슷한 제품 링크 |

## users

```sql
CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  login_id      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'ADMIN'
);
```

- `login_id`는 `UNIQUE`라 같은 아이디로 두 번 가입할 수 없습니다. 가입 API는 이 제약이 내는 오류(`DuplicateKeyException`)를 받아 "이미 사용 중인 아이디"로 안내합니다.
- `password_hash`에는 비밀번호 원문이 아니라 BCrypt 해시가 들어갑니다.
- `role`의 기본값은 `ADMIN`이고, 회원가입 SQL은 `USER`를 넣습니다. 처음 만든 관리자 계정과 가입한 계정을 구분합니다.

## category

```sql
CREATE TABLE category (
  id         SMALLSERIAL PRIMARY KEY,
  slug       VARCHAR(30) NOT NULL UNIQUE,
  name       VARCHAR(30) NOT NULL,
  sort_order SMALLINT    NOT NULL
);
INSERT INTO category (slug, name, sort_order) VALUES
  ('spring','봄',1), ('summer','여름',2), ('autumn','가을',3), ('winter','겨울',4),
  ('accessory','악세사리',5), ('shoes','신발',6);
```

| 컬럼 | 쓰임 |
|---|---|
| `slug` | 주소에 쓰는 영문 이름 (`/c/summer`). 한글 이름 대신 주소용 이름을 따로 둠 |
| `name` | 화면에 보이는 이름 |
| `sort_order` | 메뉴 순서 |

몇 개 안 되고 거의 바뀌지 않으므로 `SMALLSERIAL`(작은 정수)을 씁니다. 화면에서 카테고리를 관리하는 기능은 없고, 스키마 파일의 `INSERT`로 넣습니다.

## photo

```sql
CREATE TABLE photo (
  id         BIGSERIAL PRIMARY KEY,
  owner_id   BIGINT       NOT NULL REFERENCES users(id),
  title      VARCHAR(100) NOT NULL,
  memo       TEXT,
  image_url  TEXT         NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

- `owner_id`: 사진의 주인. 모든 사진 조회와 수정에 이 조건을 겁니다([본인 사진만](/posts/fashion-archive-database-owner-scope)).
- `image_url`: 사진 파일이 아니라 **공개 주소**를 저장합니다. 파일은 서버 디스크에 있습니다([디스크 저장](/posts/fashion-archive-upload-local-storage)).
- `created_at TIMESTAMPTZ`: 시간대 정보를 가진 시각. 목록을 최신순으로 정렬하고 "2026.09"처럼 표시합니다.

## 다대다: photo_category

사진 한 장이 "여름"이면서 "신발"일 수 있고, "여름" 카테고리에는 사진이 여러 장 있습니다. 이것이 **다대다** 관계입니다. 관계형 DB는 다대다를 직접 표현하지 못하므로 **연결 테이블**을 둡니다.

```sql
CREATE TABLE photo_category (
  photo_id    BIGINT   NOT NULL REFERENCES photo(id) ON DELETE CASCADE,
  category_id SMALLINT NOT NULL REFERENCES category(id),
  PRIMARY KEY (photo_id, category_id)
);
CREATE INDEX ON photo_category (category_id);
```

| photo_id | category_id | 뜻 |
|---|---|---|
| 15 | 2 | 사진 15는 여름 |
| 15 | 6 | 사진 15는 신발 |
| 16 | 2 | 사진 16은 여름 |

| 설계 | 이유 |
|---|---|
| `PRIMARY KEY (photo_id, category_id)` | 같은 사진에 같은 카테고리를 두 번 연결할 수 없음 |
| `ON DELETE CASCADE` (photo_id) | 사진을 지우면 연결도 함께 지워짐 |
| `INDEX (category_id)` | "여름 카테고리의 사진" 조회용. 기본 키는 `photo_id`가 앞이라 카테고리로 찾을 때는 쓰기 어려워 따로 둠 |

설계 문서의 결정 표에도 "사진 1장이 여러 카테고리에 속할 수 있다 (다대다)"로 적혀 있습니다. 저장과 조회는 [다대다 글](/posts/fashion-archive-database-many-to-many)에서 봅니다.

## product_link

```sql
CREATE TABLE product_link (
  id         BIGSERIAL PRIMARY KEY,
  photo_id   BIGINT      NOT NULL REFERENCES photo(id) ON DELETE CASCADE,
  item_label VARCHAR(30) NOT NULL,
  url        TEXT        NOT NULL,
  title      VARCHAR(100),
  note       TEXT,                                -- 왜 이 제품인지 (선택)
  sort_order INT         NOT NULL DEFAULT 0
);
CREATE INDEX ON product_link (photo_id, item_label, sort_order);
```

| 컬럼 | 쓰임 |
|---|---|
| `item_label` | 아이템 이름 ("아우터", "바지"). 화면은 이 값으로 링크를 묶어 보여 줌 |
| `url` | 제품 주소. `http`, `https`만 저장 ([주소 검사](/posts/fashion-archive-auth-csrf-url)) |
| `title`, `note` | 제품 이름, 왜 이 제품인지 (둘 다 선택) |
| `sort_order` | 같은 사진 안에서 등록 순서 |

아이템 이름을 별도 테이블로 나누지 않고 링크의 컬럼으로 두었습니다. "아우터" 아래 링크가 여러 개면 같은 `item_label`을 가진 행이 여러 개입니다. 인덱스 `(photo_id, item_label, sort_order)`는 "이 사진의 링크를 순서대로" 조회하는 모양과 같습니다.

사진을 지우면 링크도 `CASCADE`로 함께 지워집니다.

## 정리

- 테이블은 계정, 카테고리, 사진, 사진-카테고리 연결, 제품 링크 다섯 개입니다.
- 다대다(사진 ↔ 카테고리)는 연결 테이블 `photo_category`와 복합 기본 키로 표현합니다.
- 사진을 지우면 카테고리 연결과 제품 링크가 `ON DELETE CASCADE`로 함께 지워집니다.
- DB에는 사진 파일이 아니라 공개 주소를 저장합니다.
