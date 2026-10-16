---
title: "DAO와 매퍼 XML의 연결"
slug: "ledger-mybatis-dao-mapper"
category: "development"
publishedAt: "2026-10-08"
summary: "DAO의 SQL_PATH와 매퍼 XML의 namespace, 메서드 이름과 SQL id가 어떻게 짝을 이루는지, selectOne, selectList, insert, update, delete가 무엇을 돌려주는지 설명합니다."
series: "ledger"
chapter: "mybatis"
seriesOrder: 2
---
## 이 글에서 할 일

DAO의 메서드 하나와 매퍼 XML의 SQL 하나가 어떻게 연결되는지 봅니다. 이 연결 규칙만 알면, Java 코드에서 SQL을 찾아가거나 그 반대로 가는 것이 쉬워집니다.

## 짝 맞추기: namespace + id

`ledger/settings/dao/SettingsDAO.java`

```java
@Repository("settingsDAO")
public class SettingsDAO {

    @Resource(name = "sqlSession-ledger-postgre")
    private SqlSessionTemplate sqlSession;

    public static final String SQL_PATH = "ledger.settings.dao.SettingsDAO";

    public Map<String, Object> selectUserSetting(long userId) {
        return sqlSession.selectOne(SQL_PATH + ".selectUserSetting", userId);
    }
}
```

`src/main/resources/sqlmap/mappers/ledger/settings/settings.xml`

```xml
<mapper namespace="ledger.settings.dao.SettingsDAO">

    <select id="selectUserSetting" parameterType="long" resultType="java.util.HashMap">
        SELECT pay_day AS "payDay", pay_day_adjust AS "payDayAdjust",
               opening_balance AS "openingBalance", opening_date AS "openingDate"
        FROM app_user
        WHERE id = #{userId}
    </select>
</mapper>
```

```text
"ledger.settings.dao.SettingsDAO" + "." + "selectUserSetting"
 └──────── 매퍼 namespace ───────┘       └─ SQL id ─┘
```

MyBatis는 이 전체 이름으로 SQL을 찾습니다. Ledger는 두 가지 규칙을 지킵니다.

1. **namespace = DAO 클래스의 전체 이름**
2. **SQL id = DAO 메서드 이름**

그래서 DAO 메서드를 보면 XML의 어느 SQL인지 바로 알 수 있습니다. IntelliJ에서 id로 검색하면 양쪽을 오갈 수 있습니다.

연결은 문자열이라 이름이 다르면 컴파일 단계가 아니라 **그 SQL을 실행할 때** `Mapped Statements collection does not contain value for ...` 오류가 납니다. 그래서 Ledger는 DAO마다 `SQL_PATH` 상수를 하나 두고, 모든 메서드가 `SQL_PATH + ".메서드이름"` 한 가지 모양으로만 SQL을 부르게 했습니다. 이름을 적는 곳이 메서드마다 한 군데뿐이라 짝이 어긋난 곳을 찾기 쉽습니다.

## SqlSessionTemplate 메서드

| 메서드 | 돌려주는 것 | 결과가 없으면 | 결과가 둘 이상이면 |
|---|---|---|---|
| `selectOne(id, param)` | 한 줄 (Map, 숫자 등) | `null` | **예외** |
| `selectList(id, param)` | 여러 줄 (`List`) | 빈 목록 | 목록 |
| `insert(id, param)` | 넣은 줄 수 (`int`) | | |
| `update(id, param)` | 바뀐 줄 수 (`int`) | `0` | |
| `delete(id, param)` | 지운 줄 수 (`int`) | `0` | |

### 바뀐 줄 수를 쓰는 곳

`update`, `delete`가 돌려주는 줄 수는 "정말 바뀌었는지" 확인하는 데 씁니다.

`ledger/entry/service/impl/EntryServiceImpl.java` (일부)

```java
if (assetDAO.adjustCashAsset(ParamUtil.map("id", delta.assetId(), "userId", param.get("userId"),
        "delta", delta.amount())) != 1) {
    throw new IllegalStateException("연결된 자산을 찾을 수 없거나 잔액이 허용 범위를 벗어납니다");
}
```

`WHERE` 조건(내 자산인지, 잔액 범위 안인지)에 맞지 않으면 0줄이 바뀝니다. 1이 아니면 예외를 던져 트랜잭션 전체를 롤백합니다. "조건을 SQL에 넣고, 결과 줄 수로 성공을 판단"하는 방식은 동시에 두 요청이 들어와도 안전합니다.

## insert 후 새 id 받기

`ledger/entry/entry.xml`

```xml
<insert id="insertEntry" parameterType="map" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
    INSERT INTO ledger_entry (user_id, entry_date, type, ...)
    VALUES (#{userId}, #{entryDate}, #{type}, ...)
</insert>
```

| 속성 | 뜻 |
|---|---|
| `useGeneratedKeys="true"` | DB가 자동으로 만든 키(id)를 받아 옴 |
| `keyColumn="id"` | 받아 올 컬럼 |
| `keyProperty="id"` | 파라미터 Map의 `"id"` 키에 넣음 |

실행이 끝나면 **넘겨준 Map에 `id`가 채워집니다.** 고정 항목 자동 기록은 이 id로 처리 기록과 거래를 연결합니다.

```java
entryService.insertEntry(entry);
recurringService.updateRunEntry(ParamUtil.map(..., "entryId", entry.get("id")));
```

## 정리

- 매퍼 namespace는 DAO 클래스 전체 이름, SQL id는 DAO 메서드 이름으로 맞춥니다.
- `selectOne`은 둘 이상이면 예외, `selectList`는 빈 목록, `insert/update/delete`는 바뀐 줄 수를 돌려줍니다.
- 바뀐 줄 수로 성공 여부를 판단하고, 아니면 예외로 롤백합니다.
- `useGeneratedKeys`로 새로 만든 id를 파라미터 Map에 받아 옵니다.
