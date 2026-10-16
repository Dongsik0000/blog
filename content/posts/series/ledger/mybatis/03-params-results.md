---
title: "파라미터와 결과 (#{}와 ${}, Map, 컬럼 별칭)"
slug: "ledger-mybatis-params-results"
category: "development"
publishedAt: "2026-10-08"
summary: "#{}가 안전한 이유와 ${}를 써도 되는 경우, parameterType과 resultType, HashMap 결과에서 컬럼 별칭을 큰따옴표로 감싸는 이유, XML에서 부등호를 쓰는 법을 정리합니다."
series: "ledger"
chapter: "mybatis"
seriesOrder: 3
---
## 이 글에서 할 일

매퍼 XML의 SQL 한 줄에는 작은 약속이 많이 숨어 있습니다. Ledger의 SQL을 읽으며 하나씩 정리합니다.

## #{}: 값을 안전하게 넣기

```xml
<select id="selectEntry" parameterType="map" resultType="java.util.HashMap">
    SELECT ...
    FROM ledger_entry e
    WHERE e.id = #{id} AND e.user_id = #{userId}
</select>
```

`#{id}`는 MyBatis가 **`?` 자리표시자**로 바꾸고, 값은 따로 보냅니다(JDBC `PreparedStatement`).

```text
SQL:  ... WHERE e.id = ? AND e.user_id = ?
값:   [15, 3]
```

값이 SQL 문장에 섞이지 않으므로, 사용자가 `1 OR 1=1` 같은 글자를 보내도 그냥 값일 뿐 SQL로 실행되지 않습니다. **SQL 주입(SQL Injection)을 막는 기본 방법**입니다. Ledger의 사용자 입력은 모두 `#{}`로 넣습니다.

## ${}: 글자를 그대로 끼워 넣기

`${}`는 값을 SQL 문장에 **그대로 붙여 넣습니다.** 사용자 입력에 쓰면 SQL 주입이 됩니다. Ledger에서 `${}`가 나오는 곳은 한 곳뿐입니다.

`ledger/account/account.xml`

```xml
<sql id="afterBase">
    AND (${date} &gt; a.base_date OR (${date} = a.base_date AND ${alias}.created_at &gt; a.base_at))
    AND ${date} &lt;= #{to}
</sql>

...
<include refid="afterBase">
    <property name="alias" value="e"/>
    <property name="date" value="e.entry_date"/>
</include>
```

여기의 `${date}`, `${alias}`는 요청 값이 아니라 **XML 안의 `<property>` 값**입니다. `include`할 때 정해진 글자(`e.entry_date`)로 바뀌고, 사용자는 이 값을 바꿀 수 없습니다. 같은 SQL 조각을 테이블 별칭만 바꿔 세 번 쓰기 위한 것이라 안전합니다.

| | `#{}` | `${}` |
|---|---|---|
| 동작 | `?` + 값 따로 전송 | 글자 그대로 붙여 넣기 |
| 사용자 입력 | 안전 | **위험 (SQL 주입)** |
| 쓰는 곳 | 모든 값 | 컬럼 이름, 테이블 별칭처럼 값이 아닌 SQL 조각. 그것도 고정된 값일 때만 |

## parameterType과 resultType

```xml
<select id="selectUserSetting" parameterType="long" resultType="java.util.HashMap">
```

| 속성 | 뜻 | Ledger에서 |
|---|---|---|
| `parameterType` | 넘겨받는 값의 타입 | 대부분 `map`, 사용자 id 하나면 `long` |
| `resultType` | 한 줄을 담을 타입 | 대부분 `java.util.HashMap`, 개수는 `int` |

`parameterType="long"`처럼 값이 하나면 `#{}` 안의 이름은 아무거나 써도 그 값이 들어갑니다. Map이면 `#{userId}`처럼 **Map의 키 이름**과 같아야 합니다.

## HashMap 결과와 컬럼 별칭

결과를 `HashMap`으로 받으면 **컬럼 이름이 그대로 키**가 됩니다. 그런데 화면 JS는 `payDay`처럼 camelCase를 씁니다. 그래서 SQL에서 별칭을 붙입니다.

```xml
SELECT pay_day AS "payDay", pay_day_adjust AS "payDayAdjust"
```

### 왜 큰따옴표인가

PostgreSQL은 따옴표 없는 이름을 **모두 소문자**로 바꿉니다.

| SQL | 결과 키 |
|---|---|
| `pay_day AS payDay` | `payday` (소문자로 바뀜) |
| `pay_day AS "payDay"` | `payDay` (그대로) |

### mapUnderscoreToCamelCase가 있는데도?

[설정](/posts/ledger-mybatis-mybatis-setup)에 `mapUnderscoreToCamelCase`가 켜져 있지만, 이 설정은 결과를 **객체의 속성**에 담을 때만 이름을 바꿉니다. Map에는 적용되지 않습니다. 실제로 로그인 코드에 그 흔적이 있습니다.

`AuthApiController.java`

```java
// resultType=HashMap 이라 키는 컬럼명 그대로(password_hash)
String hash = user == null ? dummyHash : (String) user.get("password_hash");
```

별칭을 붙이지 않은 쿼리는 키가 `password_hash` 그대로입니다.

### 날짜는 문자열로

```xml
to_char(e.entry_date, 'YYYY-MM-DD') AS "entryDate"
```

날짜를 Java 날짜 객체로 꺼내 JSON으로 바꾸면 형식이 설정에 따라 달라질 수 있습니다. SQL에서 `YYYY-MM-DD` 문자열로 만들어 보내면 화면 JS가 항상 같은 형식을 받습니다.

## XML에서 부등호 쓰기

매퍼는 XML이라 `<`가 태그 시작으로 읽힙니다.

```xml
<if test="from != null">AND e.entry_date &gt;= #{from}</if>
<if test="to != null">AND e.entry_date &lt;= #{to}</if>
```

| 쓰고 싶은 것 | XML에서 |
|---|---|
| `<` | `&lt;` |
| `>` | `&gt;` |
| `&` | `&amp;` |

또는 `<![CDATA[ e.entry_date <= #{to} ]]>`로 감싸면 그 안은 글자 그대로 읽힙니다.

## 정리

- 사용자 값은 항상 `#{}`로 넣어 SQL 주입을 막습니다.
- `${}`는 XML 안에서 정한 고정 조각(별칭, 컬럼)에만 씁니다.
- HashMap 결과는 컬럼 이름이 키이므로 `AS "camelCase"`처럼 큰따옴표 별칭을 붙입니다.
- XML에서는 `<`, `>`를 `&lt;`, `&gt;`로 씁니다.
