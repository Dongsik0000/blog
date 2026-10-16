---
title: "다대다 저장과 조회 (foreach, EXISTS, string_agg)"
slug: "fashion-archive-database-many-to-many"
category: "development"
publishedAt: "2026-10-08"
summary: "사진의 카테고리 여러 개를 foreach 한 번으로 넣는 방법, 수정할 때 지우고 다시 넣는 방식, 카테고리로 거르는 EXISTS 조건, 목록 한 줄에 카테고리를 쉼표로 붙이는 string_agg를 photo.xml로 설명합니다."
series: "fashion-archive"
chapter: "database"
seriesOrder: 4
---
## 이 글에서 할 일

사진과 카테고리는 [다대다 관계](/posts/fashion-archive-database-schema)이고, 연결 테이블 `photo_category`에 저장합니다. 이 글에서는 연결을 넣고, 고치고, 조회하는 SQL을 봅니다.

## 넣기: foreach로 여러 줄 한 번에

`photo.xml`

```xml
<insert id="insertPhotoCategories" parameterType="java.util.HashMap">
    INSERT INTO photo_category (photo_id, category_id)
    VALUES
    <foreach collection="categoryIds" item="categoryId" separator=",">
        (#{photoId}, #{categoryId})
    </foreach>
</insert>
```

사진 15에 카테고리 `[2, 6]`을 넣으면 SQL이 이렇게 만들어집니다.

```sql
INSERT INTO photo_category (photo_id, category_id)
VALUES (?, ?) , (?, ?)
-- 값: 15, 2, 15, 6
```

| 속성 | 뜻 |
|---|---|
| `collection="categoryIds"` | 반복할 목록 (파라미터 Map의 키) |
| `item="categoryId"` | 반복 중 하나를 부를 이름 |
| `separator=","` | 사이에 넣을 글자 |

카테고리가 몇 개든 INSERT 한 번으로 끝납니다. 목록이 비면 `VALUES` 뒤가 비어 SQL 오류가 나므로, API가 "카테고리를 하나 이상" 검사를 먼저 합니다([입력 검증](/posts/fashion-archive-spring-mvc-validation)).

## 고치기: 지우고 다시 넣기

사진의 카테고리를 `[2, 6]`에서 `[2, 3]`으로 바꾸려면, 어떤 것이 빠지고 들어왔는지 비교하는 대신 **모두 지우고 새로 넣습니다.**

```java
boolean found = transactionTemplate.execute(status -> {
    if (photoService.updatePhoto(photo) == 0) {
        return false;
    }
    photoService.deletePhotoCategories(id);
    photoService.insertPhotoCategories(id, ids);
    return true;
});
```

```xml
<delete id="deletePhotoCategories" parameterType="java.lang.Long">
    DELETE FROM photo_category WHERE photo_id = #{photoId}
</delete>
```

1. 사진 정보를 고칩니다. 0줄이면 남의 사진이라 거기서 멈춥니다.
2. 그 사진의 연결을 모두 지웁니다.
3. 새 목록을 넣습니다.

세 단계를 `TransactionTemplate`으로 묶었으므로, 3단계에서 실패하면 1, 2단계도 취소되어 **카테고리가 하나도 없는 사진**이 남지 않습니다. 연결이 몇 줄뿐이라 비교 코드를 쓰는 것보다 단순합니다.

남의 사진인지는 1단계의 `updatePhoto`(`owner_id` 조건)가 확인하므로, 2, 3단계는 1단계를 통과한 경우에만 실행됩니다.

## 조회 1: 카테고리로 거르기 (EXISTS)

`/c/summer` 화면은 여름 카테고리에 속한 내 사진만 보여 줍니다.

```xml
<select id="selectPhotoList" parameterType="java.util.HashMap" resultType="java.util.HashMap">
    SELECT p.id, p.title, p.image_url, p.created_at, ...
    FROM photo p
    WHERE p.owner_id = #{ownerId}
    <if test="slug != null">
    AND EXISTS (
        SELECT 1
        FROM photo_category pc
        JOIN category c ON c.id = pc.category_id
        WHERE pc.photo_id = p.id
        AND c.slug = #{slug})
    </if>
    ORDER BY p.created_at DESC, p.id DESC
</select>
```

`EXISTS (...)`는 "괄호 안 조회에 한 줄이라도 있으면 참"입니다. 사진마다 "이 사진이 `summer` 카테고리에 연결되어 있는가"를 확인합니다.

**JOIN 대신 EXISTS를 쓰는 이유**: `photo`에 `photo_category`를 JOIN해서 거르면, 사진이 여러 카테고리에 속할 때 결과에 같은 사진이 여러 번 나올 수 있습니다. EXISTS는 사진 행을 늘리지 않고 조건만 확인하므로 사진이 한 번씩만 나옵니다.

`slug`가 없으면(`/` 홈) `<if>`가 빠져 전체 사진이 나옵니다. 같은 SQL 하나로 홈과 카테고리 화면을 처리합니다.

## 조회 2: 카테고리를 한 칸에 붙이기 (string_agg)

목록 카드에는 사진이 속한 카테고리를 색 점으로 표시합니다. 사진마다 카테고리 목록이 필요합니다.

```xml
SELECT p.id, p.title, p.image_url, p.created_at,
       (SELECT string_agg(c.slug, ',' ORDER BY c.sort_order)
          FROM photo_category pc
          JOIN category c ON c.id = pc.category_id
         WHERE pc.photo_id = p.id) AS category_slugs
FROM photo p
```

`string_agg(값, 구분자 ORDER BY ...)`는 PostgreSQL의 집계 함수로, 여러 줄의 값을 **글자 하나로 이어 붙입니다.**

| id | title | category_slugs |
|---|---|---|
| 15 | 여름 샌들 코디 | `summer,shoes` |
| 16 | 린넨 셔츠 | `summer` |

사진 목록을 가져온 뒤 사진마다 카테고리를 다시 조회하면 사진 수만큼 쿼리가 늘어납니다. 목록 쿼리 안의 하위 쿼리로 한 번에 가져옵니다. `ORDER BY c.sort_order`로 메뉴 순서(봄 → 신발)대로 붙입니다.

화면 JS는 이 글자를 쉼표로 나눠 점을 찍습니다.

```js
/* "summer,shoes" → 계절색 점. 홈처럼 여러 계절이 섞인 목록에서 카드만 보고 구분하기 위해 */
seasonDots = function(slugs){
    var wrap = document.createElement('span');
    wrap.className = 'season-dots';
    (slugs ? slugs.split(',') : []).forEach(function(slug){ ... });
    return wrap;
},
```

## 상세 화면의 카테고리 id

```xml
<!-- 사진이 속한 카테고리 id 목록 -->
<select id="selectPhotoCategoryIds" parameterType="java.lang.Long" resultType="java.lang.Integer">
    SELECT category_id
    FROM photo_category
    WHERE photo_id = #{photoId}
</select>
```

`resultType`이 `Integer`라 결과는 `[2, 6]` 같은 숫자 목록입니다. 수정 화면은 이 목록으로 체크박스를 체크해 둡니다.

## 정리

- 카테고리 연결은 `foreach`로 여러 줄을 INSERT 한 번에 넣습니다.
- 수정은 연결을 모두 지우고 다시 넣되, 사진 수정과 함께 한 트랜잭션으로 묶습니다.
- 카테고리 거르기는 `EXISTS`로 사진이 중복되지 않게 하고, 카테고리 표시는 `string_agg` 하위 쿼리로 한 번에 가져옵니다.
