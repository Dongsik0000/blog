---
title: "모든 쿼리에 사용자 조건 걸기"
slug: "ledger-mybatis-user-scope"
category: "development"
publishedAt: "2026-10-08"
summary: "한 DB에 여러 사용자의 가계부가 함께 있을 때 다른 사람의 기록에 접근하지 못하게 하는 원칙, 세션의 userId만 쓰는 이유, 조회와 수정과 삭제에 user_id 조건을 거는 방법을 정리합니다."
series: "ledger"
chapter: "mybatis"
seriesOrder: 6
---
## 이 글에서 할 일

Ledger는 여러 사용자가 가입할 수 있고, 모든 사용자의 거래가 같은 `ledger_entry` 테이블에 있습니다. 거래 15번을 지우는 요청이 왔을 때, 그 거래가 **요청한 사람의 것인지** 확인하지 않으면 남의 기록을 지울 수 있습니다. 이 문제를 **IDOR**(Insecure Direct Object Reference, 안전하지 않은 직접 객체 참조)라고 부릅니다. 이 글에서는 Ledger가 이것을 막는 규칙을 봅니다.

## 규칙 1. 모든 SQL에 `user_id = #{userId}`

```xml
<select id="selectEntry" parameterType="map" resultType="java.util.HashMap">
    ...
    WHERE e.id = #{id} AND e.user_id = #{userId}
</select>

<update id="updateEntry" parameterType="map">
    UPDATE ledger_entry SET ...
    WHERE id = #{id} AND user_id = #{userId}
</update>

<delete id="deleteEntry" parameterType="map">
    DELETE FROM ledger_entry WHERE id = #{id} AND user_id = #{userId}
</delete>
```

id만으로 찾지 않고 **id와 사용자를 함께** 조건으로 겁니다. 다른 사람의 거래 id를 보내면 조건에 맞는 행이 없으므로 조회는 `null`, 수정과 삭제는 0줄이 됩니다. 확인 코드를 따로 쓰지 않아도 SQL 자체가 막습니다.

목록 조회의 조건 조각에서도 `user_id`는 `if` 밖에 있어 빠질 수 없습니다([동적 SQL](/posts/ledger-mybatis-dynamic-sql)).

```xml
<sql id="entryWhere">
    WHERE e.user_id = #{userId}
    <if test="from != null">...</if>
    ...
</sql>
```

## 규칙 2. userId는 항상 세션에서

```java
long userId = SessionUtil.getUserId(session);
...
settingsService.updateUserSetting(ParamUtil.map("userId", userId, "payDay", payDay, ...));
```

`userId`를 요청 본문에서 받으면, 사용자가 개발자 도구로 값을 바꿔 보낼 수 있습니다. 세션의 값은 로그인할 때 서버가 넣은 것이라 사용자가 바꿀 수 없습니다.

그리고 요청 본문을 DAO에 **그대로 넘기지 않습니다.**

```java
// DAO 에 넘길 파라미터 맵. Map.of 와 달리 null 값을 허용한다(선택 칸).
// 요청 본문을 그대로 넘기지 않고 이것으로 필요한 값만 담는다(클라이언트가 userId 등을 끼워 넣지 못하게).
public static Map<String, Object> map(Object... keyValues) { ... }
```

`param`을 그대로 넘기면 본문에 `"userId": 1`을 끼워 넣었을 때 매퍼의 `#{userId}`가 그 값을 쓸 수 있습니다. 검사한 값만 새 Map에 담아 넘기면 이런 끼워 넣기가 통하지 않습니다.

## 규칙 3. 연결된 것도 내 것인지 확인

거래를 저장할 때는 카테고리, 결제수단, 통장 id도 함께 옵니다. 거래가 내 것이어도 **남의 카테고리 id**를 붙일 수 있다면 문제입니다. 그래서 저장 전에 참조하는 id들도 내 것인지 확인합니다.

`EntryApiController.save` (일부)

```java
// 카테고리·결제수단은 같은 사용자 것이어야 하고, 카테고리는 거래 구분과 같아야 한다
Map<String, Object> category = settingsService.selectCategory(ParamUtil.map("id", categoryId, "userId", userId));
if (category == null || !type.equals(category.get("type"))) {
    return Response.invalid("카테고리를 다시 골라 주세요.");
}
if (paymentMethodId != null
        && settingsService.selectPayment(ParamUtil.map("id", paymentMethodId, "userId", userId)) == null) {
    return Response.invalid("결제수단을 다시 골라 주세요.");
}
```

통장과 적금 자산 id도 같은 방식으로 확인합니다. 테이블 관계에서 이것을 강제하는 방법은 [제약 조건](/posts/ledger-database-constraints)에서 봅니다.

## 규칙 4. "남의 것"과 "없는 것"을 구분하지 않기

```java
Map<String, Object> saved = settingsService.selectCategory(key);
if (saved == null) {
    return Response.of(Constants.NOT_FOUND);
}
```

```java
public static final String NOT_FOUND = "91"; // 대상 없음(다른 사용자 것 포함, 구분하지 않음)
```

"다른 사람의 것입니다"라고 따로 알려 주면, id를 하나씩 바꿔 보내며 "어떤 id가 존재하는지"를 알아낼 수 있습니다. 둘 다 "없음"으로 응답하면 아무 정보도 새지 않습니다.

## 수정 전에 잠그기: FOR UPDATE

```xml
<select id="selectEntryForUpdate" parameterType="map" resultType="java.util.HashMap">
    SELECT id, amount, transfer_asset_id AS "transferAssetId"
    FROM ledger_entry WHERE id = #{id} AND user_id = #{userId} FOR UPDATE
</select>
```

거래를 고칠 때 이전 금액을 읽어 자산 잔액 차이를 계산합니다. `FOR UPDATE`는 읽은 행을 **트랜잭션이 끝날 때까지 잠가**, 그사이 다른 요청이 같은 거래를 바꾸지 못하게 합니다. 같은 거래를 두 창에서 동시에 고쳐도 자산 잔액이 어긋나지 않습니다. 여기에도 `user_id` 조건이 있습니다.

## 정리

- 모든 조회, 수정, 삭제 SQL에 `user_id = #{userId}`를 겁니다.
- `userId`는 세션에서만 꺼내고, 요청 본문은 DAO에 그대로 넘기지 않습니다.
- 남의 것과 없는 것은 같은 응답(`91`)으로 돌려줘 정보를 흘리지 않습니다.
- 동시 수정이 문제 되는 곳은 `FOR UPDATE`로 잠급니다.
