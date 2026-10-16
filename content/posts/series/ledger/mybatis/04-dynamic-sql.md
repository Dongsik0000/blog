---
title: "동적 SQL (if, sql과 include, foreach, choose)"
slug: "ledger-mybatis-dynamic-sql"
category: "development"
publishedAt: "2026-10-08"
summary: "조건에 따라 SQL을 바꾸는 MyBatis 동적 SQL을 거래 내역 검색, 결제 기록 조회, 월급 주기별 통계 쿼리로 설명합니다."
series: "ledger"
chapter: "mybatis"
seriesOrder: 4
---
## 이 글에서 할 일

거래 내역 화면에는 기간, 구분(수입/지출), 카테고리, 결제수단, 검색어 필터가 있습니다. 사용자가 고른 필터만 `WHERE`에 넣어야 합니다. 경우마다 SQL을 따로 쓰면 수십 개가 됩니다. MyBatis의 **동적 SQL**로 하나의 SQL이 조건에 따라 모양을 바꾸게 합니다.

## if: 값이 있을 때만 조건 넣기

`ledger/entry/entry.xml`

```xml
<!-- 거래 조회 조건. from·to 가 없으면 그쪽 기간 제한 없음(전체 기간 검색). keyword 는 LikeUtil.contains 로 만든 패턴 -->
<sql id="entryWhere">
    WHERE e.user_id = #{userId}
    <if test="from != null">AND e.entry_date &gt;= #{from}</if>
    <if test="to != null">AND e.entry_date &lt;= #{to}</if>
    <if test="type != null">AND e.type = #{type}</if>
    <if test="categoryId != null">AND e.category_id = #{categoryId}</if>
    <if test="paymentMethodId != null">AND e.payment_method_id = #{paymentMethodId}</if>
    <if test="keyword != null">
      AND (e.title ILIKE #{keyword} ESCAPE '\' OR e.memo ILIKE #{keyword} ESCAPE '\')
    </if>
</sql>
```

`test` 안의 조건이 참이면 그 안의 글자가 SQL에 들어갑니다. 예를 들어 카테고리만 고르면 실제 SQL은 다음과 같습니다.

```sql
WHERE e.user_id = ? AND e.category_id = ?
```

`WHERE e.user_id = #{userId}`는 `if` 밖에 있어 **항상** 들어갑니다. 사용자 조건이 빠지는 경우가 아예 없게 만든 것입니다([사용자 조건](/posts/ledger-mybatis-user-scope)). 항상 들어가는 조건이 맨 앞에 있으니 뒤의 조건은 모두 `AND`로 시작하면 됩니다.

Controller는 "필터 없음"을 `null`로 넘깁니다. 빈 문자열(`""`)을 넘기면 `!= null`이 참이 되어 `category_id = ''` 같은 엉뚱한 조건이 붙으므로, 빈 값은 `null`로 바꿔 넘기는 것이 약속입니다.

## sql과 include: 조각 재사용

`entryWhere`는 `<sql>`로 이름을 붙인 **SQL 조각**입니다. 같은 조건을 목록 조회와 합계 조회에서 함께 씁니다.

```xml
<select id="selectEntryList" ...>
    SELECT <include refid="entryColumns"/>
    FROM ledger_entry e
    JOIN category c ON c.id = e.category_id
    ...
    <include refid="entryWhere"/>
    ORDER BY e.entry_date DESC, e.id DESC
    <if test="limit != null">LIMIT #{limit}</if>
</select>

<!-- 같은 조건의 건수·수입·지출 합계(목록이 잘려도 전체 기준) -->
<select id="selectEntrySum" ...>
    SELECT COUNT(*) AS "count", ...
    FROM ledger_entry e
    <include refid="entryWhere"/>
</select>
```

조건을 한 곳에 두었기 때문에, 필터를 하나 추가해도 목록과 합계가 어긋나지 않습니다. 목록은 최대 500건까지만 보내도(`limit`), 합계는 같은 조건의 **전체**를 계산합니다.

## foreach: 목록으로 IN 만들기

`ledger/recurring/recurring.xml`

```xml
WHERE i.user_id = #{userId}
  AND r.period_ym IN
<foreach collection="months" item="m" open="(" separator="," close=")">#{m}</foreach>
```

Java에서 `"months"`에 `["2026-09", "2026-10"]` 목록을 넣으면 다음 SQL이 됩니다.

```sql
AND r.period_ym IN (?, ?)
```

| 속성 | 뜻 |
|---|---|
| `collection` | 반복할 목록 (파라미터 Map의 키) |
| `item` | 반복 중 하나를 부를 이름 |
| `open`, `close` | 앞뒤에 붙일 글자 |
| `separator` | 사이에 넣을 글자 |

값마다 `#{m}`으로 넣으므로 안전합니다. 단, 목록이 비면 `IN ()`이 되어 SQL 오류가 납니다. 그래서 Java 쪽에서 목록이 비었으면 이 SQL을 부르지 않습니다(`RecurringGenerator`의 `if (!dueDates.isEmpty())`).

## choose: 여러 갈래 중 하나

요약 화면은 "달력 월"과 "월급 주기" 두 가지로 묶어 볼 수 있습니다.

`ledger/summary/summary.xml`

```xml
<!-- 묶음 키("YYYY-MM"): 달력 월, 또는 buckets(key·from·to, 주기 보기)가 있으면 그 기간에 드는 주기의 키.
     바인드 변수가 든 식은 GROUP BY 에 다시 쓸 수 없어 각 쿼리는 하위 쿼리에서 키를 만든 뒤 묶는다 -->
<sql id="bucketKey">
    <choose>
        <when test="buckets != null">
            CASE<foreach collection="buckets" item="b"> WHEN entry_date BETWEEN #{b.from} AND #{b.to} THEN #{b.key}</foreach> END
        </when>
        <otherwise>to_char(entry_date, 'YYYY-MM')</otherwise>
    </choose>
</sql>
```

`choose`는 Java의 `if / else if / else`입니다. `when` 중 처음 참인 것 하나, 없으면 `otherwise`를 씁니다.

- **달력 월**: 날짜를 `YYYY-MM` 글자로 바꿔 묶습니다.
- **월급 주기**: 주기마다 날짜 범위가 다르므로(예: 9/25~10/24), Java가 계산한 주기 목록(`buckets`)으로 `CASE WHEN`을 만듭니다.

```sql
CASE WHEN entry_date BETWEEN ? AND ? THEN ?
     WHEN entry_date BETWEEN ? AND ? THEN ? END
```

주기의 시작일은 주말과 공휴일 보정 때문에 SQL로 계산하기 어렵습니다. 그래서 **날짜 계산은 Java(`PayCycle`)가, 묶기는 SQL이** 하도록 나눴습니다.

### 하위 쿼리로 감싸는 이유

```xml
SELECT month AS "month", SUM(...) ...
FROM (
    SELECT <include refid="bucketKey"/> AS month, entry_date, type, amount
    FROM ledger_entry
    WHERE user_id = #{userId} AND entry_date BETWEEN #{from} AND #{to}
) t
GROUP BY month
```

주석대로, `?`가 들어간 식은 `GROUP BY`에 똑같이 다시 쓸 수 없습니다(PostgreSQL은 값이 다른 별개의 식으로 봅니다). 그래서 안쪽 쿼리에서 키(`month`)를 먼저 만들고, 바깥에서 그 이름으로 묶습니다.

## 실행된 SQL 확인하기

동적 SQL은 실제로 어떤 SQL이 됐는지 봐야 확실합니다. 로컬에서는 로그 수준이 DEBUG라 실행된 SQL과 값이 로그에 찍힙니다.

```text
==>  Preparing: SELECT ... WHERE e.user_id = ? AND e.category_id = ? ORDER BY ...
==> Parameters: 1(Long), 5(Long)
```

## 정리

- `if`로 값이 있는 조건만 넣고, 사용자 조건은 `if` 밖에 두어 항상 들어가게 합니다.
- `sql` + `include`로 조건과 컬럼을 재사용해 목록과 합계가 어긋나지 않게 합니다.
- `foreach`로 `IN (...)`을 만들고, 빈 목록은 Java에서 미리 거릅니다.
- `choose`로 달력 월과 월급 주기 묶음을 하나의 SQL에서 처리합니다.
