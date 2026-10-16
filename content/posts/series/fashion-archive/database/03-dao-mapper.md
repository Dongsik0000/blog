---
title: "DAO와 매퍼 XML"
slug: "fashion-archive-database-dao-mapper"
category: "development"
publishedAt: "2026-10-08"
summary: "PhotoDAO와 photo.xml을 나란히 읽으며 NS 상수와 SQL id의 짝, 파라미터 Map 만들기, selectOne과 selectList와 영향 행 수의 쓰임, useGeneratedKeys로 새 id 받기, Service가 DAO를 그대로 부르는 구조를 설명합니다."
series: "fashion-archive"
chapter: "database"
seriesOrder: 3
---
## 이 글에서 할 일

SQL은 매퍼 XML에, SQL을 부르는 Java 코드는 DAO에 있습니다. 둘이 어떻게 짝을 이루는지 사진 DAO로 봅니다.

## 짝: NS 상수 + SQL id

`com/fashion/photo/dao/PhotoDAO.java`

```java
@Repository
public class PhotoDAO {

    // photo.xml의 namespace + "." — 뒤에 쿼리 id를 붙여 호출한다
    private static final String NS = "com.fashion.photo.dao.PhotoDAO.";

    @Resource(name = "sqlSession")
    private SqlSessionTemplate sqlSession;

    // 사진 1장. ownerId의 사진이 아니면 null
    public Map<String, Object> selectPhoto(long id, long ownerId) {
        return sqlSession.selectOne(NS + "selectPhoto", idAndOwner(id, ownerId));
    }
}
```

`src/main/resources/sqlmap/mappers/fashion/photo/photo.xml`

```xml
<mapper namespace="com.fashion.photo.dao.PhotoDAO">

    <!-- 사진 1장. 다른 사용자의 사진이면 결과 없음 -->
    <select id="selectPhoto" parameterType="java.util.HashMap" resultType="java.util.HashMap">
        SELECT id, owner_id, title, memo, image_url, created_at
        FROM photo
        WHERE id = #{id}
        AND owner_id = #{ownerId}
    </select>
```

```text
"com.fashion.photo.dao.PhotoDAO." + "selectPhoto"
 └────── 매퍼 namespace + "." ──┘   └─ SQL id ─┘
```

MyBatis는 이 전체 이름으로 SQL을 찾습니다. namespace는 DAO 클래스 이름, id는 DAO 메서드 이름과 같게 맞춥니다. `NS` 상수 끝에 점(`.`)까지 넣어 두어 호출할 때는 `NS + "id"`만 씁니다.

## 파라미터 Map 만들기

DAO 메서드는 **타입이 있는 파라미터**(`long id, long ownerId`)를 받고, 안에서 Map으로 바꿔 매퍼에 넘깁니다.

```java
public List<Map<String, Object>> selectPhotoList(long ownerId, String slug) {
    Map<String, Object> param = new HashMap<String, Object>();
    param.put("ownerId", ownerId);
    param.put("slug", slug);
    return sqlSession.selectList(NS + "selectPhotoList", param);
}
```

DAO를 부르는 쪽은 `selectPhotoList(ownerId, slug)`처럼 필요한 값이 무엇인지 메서드 모양으로 알 수 있습니다. Map의 키 이름(`"ownerId"`)과 매퍼의 `#{ownerId}`는 DAO 안에서만 맞추면 됩니다.

## SqlSessionTemplate 메서드의 쓰임

| 메서드 | 돌려주는 것 | 이 프로젝트에서 |
|---|---|---|
| `selectOne` | 한 줄. 없으면 `null` | `selectPhoto`: `null`이면 없는 사진이거나 남의 사진 |
| `selectList` | 여러 줄 (`List`) | 사진 목록, 카테고리 id 목록, 링크 목록 |
| `update`, `delete` | 바뀐 줄 수 | `0`이면 대상이 없거나 남의 것 |
| `insert` | 넣은 줄 수 | 새 id는 아래 `useGeneratedKeys`로 받음 |

```java
// 영향 행 수 (0이면 없는 id이거나 다른 사용자의 사진)
public int updatePhoto(Map<String, Object> param) {
    return sqlSession.update(NS + "updatePhoto", param);
}
```

API는 이 줄 수로 성공 여부를 판단합니다.

```java
if (photoService.updatePhoto(photo) == 0) {
    return false;   // "사진을 찾을 수 없습니다."
}
```

## 새 id 받기: useGeneratedKeys

```xml
<!-- 사진 등록. 생성된 id는 파라미터 Map의 "id" 키로 돌아온다 -->
<insert id="insertPhoto" parameterType="java.util.HashMap" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
    INSERT INTO photo (owner_id, title, memo, image_url)
    VALUES (#{ownerId}, #{title}, #{memo}, #{imageUrl})
</insert>
```

| 속성 | 뜻 |
|---|---|
| `useGeneratedKeys="true"` | DB가 만든 키(`BIGSERIAL` id)를 받아 옴 |
| `keyColumn="id"` | 받아 올 컬럼 |
| `keyProperty="id"` | 넘긴 Map의 `"id"` 키에 넣음 |

```java
photoService.insertPhoto(photo);
long newId = ((Number) photo.get("id")).longValue();
photoService.insertPhotoCategories(newId, ids);
```

INSERT가 끝나면 **넘겨준 Map에 id가 채워집니다.** 그 id로 바로 카테고리 연결을 넣습니다. DB가 돌려준 숫자의 타입이 `Long`이나 `Integer`일 수 있어 `Number`로 받아 `longValue()`로 바꿉니다.

## Service는 DAO를 그대로

`com/fashion/photo/service/impl/PhotoServiceImpl.java` (일부)

```java
@Service("photoService")
public class PhotoServiceImpl implements PhotoService {

    @Autowired
    private PhotoDAO photoDAO;

    @Autowired
    private LinkDAO linkDAO;

    @Override
    public Map<String, Object> selectPhoto(long id, long ownerId) {
        return photoDAO.selectPhoto(id, ownerId);
    }

    @Override
    public List<Map<String, Object>> selectLinkList(long photoId) {
        return linkDAO.selectLinkList(photoId);
    }
}
```

`PhotoService` 하나가 사진(`PhotoDAO`)과 링크(`LinkDAO`) DAO를 함께 감쌉니다. 컨트롤러는 `photoService` 하나만 알면 됩니다. 입력 검증과 업무 판단은 API 컨트롤러가 맡고, Service는 DAO 호출을 그대로 넘깁니다. 설계 문서는 로그인 항목에서 "판정 로직은 `CmmnApiController`에, Service는 DAO 위임만"으로 정했고, 사진 API도 같은 방식입니다.

## 정리

- 매퍼 namespace는 DAO 클래스 이름이고, DAO는 `NS + "SQL id"`로 SQL을 부릅니다.
- DAO 메서드는 타입이 있는 파라미터를 받아 안에서 Map으로 바꿉니다.
- `selectOne`의 `null`과 `update`, `delete`의 0으로 "없음 또는 남의 것"을 판단합니다.
- `useGeneratedKeys`로 새 id를 파라미터 Map에 받아 바로 다음 INSERT에 씁니다.
