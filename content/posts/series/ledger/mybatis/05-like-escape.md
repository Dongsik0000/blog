---
title: "LIKE 검색어 이스케이프"
slug: "ledger-mybatis-like-escape"
category: "development"
publishedAt: "2026-10-08"
summary: "검색어에 %, _, \\ 같은 특수 문자가 들어왔을 때 LIKE 검색이 엉뚱하게 동작하는 문제와, LikeUtil과 ESCAPE 절로 글자 그대로 찾게 하는 방법을 설명합니다."
series: "ledger"
chapter: "mybatis"
seriesOrder: 5
---
## 이 글에서 할 일

거래 내역에서 "100%"를 검색하면 무엇이 나와야 할까요? 당연히 "100%"가 들어간 거래입니다. 그런데 아무 처리 없이 LIKE 검색을 만들면 **모든 거래**가 나옵니다. 이 글에서는 그 이유와 해결 방법을 봅니다.

## LIKE의 특수 문자

SQL의 `LIKE`(PostgreSQL의 `ILIKE`는 대소문자 무시)는 두 글자를 특별하게 씁니다.

| 문자 | 뜻 |
|---|---|
| `%` | 아무 글자 0개 이상 |
| `_` | 아무 글자 1개 |

"커피"가 들어간 것을 찾으려면 `'%커피%'`로 씁니다.

### 문제

사용자가 `%`를 검색하면 패턴이 `'%%%'`가 되어 **모든 행**이 맞습니다. `_`를 검색하면 "아무 글자 하나 이상"이 되어 역시 거의 다 맞습니다. 사용자는 글자 `%`를 찾고 싶었는데, SQL은 그것을 와일드카드로 읽은 것입니다.

`#{}`로 넣었으니 SQL 주입은 아니지만, **검색 결과가 틀립니다.**

## 해결: 이스케이프 + ESCAPE 절

`ledger/cmmn/util/LikeUtil.java`

```java
// LIKE/ILIKE 부분 일치 패턴. 사용자가 넣은 % _ \ 는 글자로 찾도록 이스케이프한다.
// 매퍼에서는 반드시 ESCAPE '\' 와 함께 쓴다.
public class LikeUtil {

    // "커피" -> "%커피%". 빈 값은 null(조건 생략)
    public static String contains(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
```

1. 사용자 글자 중 `\`, `%`, `_` 앞에 `\`를 붙입니다. `\%`는 "와일드카드가 아니라 글자 %"라는 뜻이 됩니다.
2. 앞뒤에 진짜 와일드카드 `%`를 붙입니다.
3. 빈 값은 `null`을 돌려줘 매퍼의 `<if test="keyword != null">`가 조건 자체를 빼게 합니다.

`\`를 **가장 먼저** 바꾸는 이유가 중요합니다. 순서를 바꿔 `%`를 먼저 `\%`로 만들고 나서 `\`를 `\\`로 바꾸면, 방금 붙인 `\`까지 두 번 바뀌어 `\\%`가 됩니다.

| 사용자 입력 | 만들어진 패턴 | 찾는 것 |
|---|---|---|
| `커피` | `%커피%` | "커피"가 들어간 것 |
| `100%` | `%100\%%` | "100%"가 들어간 것 |
| `a_b` | `%a\_b%` | "a_b"가 들어간 것 |

매퍼에서는 어떤 글자를 이스케이프 문자로 쓰는지 `ESCAPE` 절로 알려 줍니다.

`ledger/entry/entry.xml`

```xml
<if test="keyword != null">
  AND (e.title ILIKE #{keyword} ESCAPE '\' OR e.memo ILIKE #{keyword} ESCAPE '\')
</if>
```

## 테스트

`src/test/java/ledger/cmmn/util/LikeUtilTest.java`가 이 변환을 검사합니다. 특수 문자 처리는 화면에서 눈으로 확인하기 어려워 테스트로 결과를 고정했습니다.

## 패턴을 Java에서 만드는 이유

SQL에서 `'%' || #{keyword} || '%'`로 앞뒤 `%`를 붙일 수도 있지만, 그 방식은 사용자가 넣은 `%`와 `_`를 그대로 와일드카드로 씁니다. Ledger는 이스케이프와 앞뒤 `%`를 `LikeUtil.contains` 한 곳에서 처리하고, 매퍼는 완성된 패턴을 `#{keyword}`로 받아 `ESCAPE '\'`와 함께 씁니다. 검색 규칙이 바뀌어도 고칠 곳이 한 곳입니다.

## 정리

- LIKE에서 `%`와 `_`는 와일드카드라, 사용자가 그 글자를 검색하면 결과가 틀어집니다.
- `LikeUtil.contains`가 `\`, `%`, `_`를 이스케이프하고(`\`부터) 앞뒤에 `%`를 붙입니다.
- 매퍼는 반드시 `ESCAPE '\'`와 함께 씁니다.
