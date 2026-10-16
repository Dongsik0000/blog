---
title: "표준프레임워크 관례를 따른 구조"
slug: "fashion-archive-egovframe-conventions"
category: "development"
publishedAt: "2026-10-08"
summary: "cmmn 공통 패키지, service와 service/impl, @Resource로 이름을 지정한 주입, DAO의 namespace 상수, egovProps/globals.properties, Response와 Constants 같은 표준프레임워크 프로젝트의 관례가 Fashion Archive 코드에서 어떻게 쓰이는지 정리합니다."
series: "fashion-archive"
chapter: "egovframe"
seriesOrder: 4
---
## 이 글에서 할 일

표준프레임워크 프로젝트는 라이브러리뿐 아니라 **코드를 어디에 어떤 이름으로 두는지**도 비슷합니다. Fashion Archive도 그 관례를 따릅니다. 이 글에서는 관례를 하나씩 실제 코드와 연결합니다.

## 1. 기능 패키지 안의 계층

```text
com/fashion/photo/
├── controller/PhotoController.java, PhotoApiController.java
├── service/PhotoService.java            ← 인터페이스
├── service/impl/PhotoServiceImpl.java   ← 구현
└── dao/PhotoDAO.java, LinkDAO.java
```

기능(`photo`)으로 먼저 나누고, 그 안을 계층으로 나눕니다. 서비스는 **인터페이스와 구현**(`impl`)을 따로 둡니다.

## 2. 공통 패키지 cmmn

```text
com/fashion/cmmn/
├── controller/   로그인, 가입, 화면 공통 모델
├── dao/, service/
├── interceptor/  로그인 확인
├── listener/     정적 파일 버전
├── storage/      사진 저장
└── util/         Response, Constants, Validation, ...
```

여러 기능이 함께 쓰는 것은 `cmmn`(common)에 둡니다.

## 3. 빈 이름과 @Resource

```java
@Service("photoService")
public class PhotoServiceImpl implements PhotoService { ... }
```

```java
@Resource(name = "photoService")
private PhotoService photoService;
```

서비스 빈에 이름을 붙이고, 쓰는 쪽은 `@Resource(name = ...)`로 **이름을 지정해** 주입받습니다. 같은 인터페이스의 구현이 여럿이어도 어느 것을 쓰는지 코드에서 분명히 보입니다. DAO가 받는 `SqlSessionTemplate`도 이름으로 받습니다.

```java
@Resource(name = "sqlSession")
private SqlSessionTemplate sqlSession;
```

`sqlSession`은 `context-datasource.xml`에서 `id="sqlSession"`으로 만든 빈입니다.

## 4. DAO와 매퍼 namespace

`com/fashion/photo/dao/PhotoDAO.java`

```java
@Repository
public class PhotoDAO {

    // photo.xml의 namespace + "." — 뒤에 쿼리 id를 붙여 호출한다
    private static final String NS = "com.fashion.photo.dao.PhotoDAO.";

    @Resource(name = "sqlSession")
    private SqlSessionTemplate sqlSession;

    public List<Map<String, Object>> selectPhotoList(long ownerId, String slug) {
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("ownerId", ownerId);
        param.put("slug", slug);
        return sqlSession.selectList(NS + "selectPhotoList", param);
    }
}
```

`src/main/resources/sqlmap/mappers/fashion/photo/photo.xml`

```xml
<mapper namespace="com.fashion.photo.dao.PhotoDAO">
    <select id="selectPhotoList" parameterType="java.util.HashMap" resultType="java.util.HashMap">
```

| 규칙 | 예 |
|---|---|
| 매퍼 namespace = DAO 클래스 전체 이름 | `com.fashion.photo.dao.PhotoDAO` |
| SQL id = DAO 메서드 이름 | `selectPhotoList` |
| DAO는 `NS` 상수 + id로 호출 | `NS + "selectPhotoList"` |

DAO 메서드를 보면 매퍼의 어느 SQL인지 바로 찾을 수 있습니다. 파라미터와 결과는 `HashMap`으로 주고받습니다. 이 방식은 [DAO와 매퍼](/posts/fashion-archive-database-dao-mapper)에서 자세히 봅니다.

## 5. egovProps/globals.properties

```properties
Globals.Fashion.Postgre.Url=${FASHION_DB_URL:jdbc:postgresql://localhost:5432/fashion}
Globals.Fashion.Upload.Dir=${FASHION_UPLOAD_DIR:/data/fashion-uploads}
```

설정값은 `egovProps/globals.properties`에 `Globals.` 접두사로 둡니다. XML과 Java는 이 키로 값을 꺼냅니다.

```xml
<property name="url" value="${Globals.Fashion.Postgre.Url}" />
```

```java
@Value("${Globals.Fashion.Upload.Dir}")
private String uploadDir;
```

Fashion Archive는 값 자리에 다시 `${환경변수:기본값}`을 써서, 실제 값은 서버의 환경 변수에서 받습니다([로컬 실행](/posts/fashion-archive-intro-local-run)).

## 6. 공통 응답 Response와 결과 코드 Constants

```java
// Ajax 공통 응답 포맷. code: Constants.SUCCESS/FAIL 등, message: 사용자에게 보여줄 문구, data: 결과
public class Response {
    private final String code;
    private final String message;
    private final Object data;
    ...
}
```

```java
public final class Constants {
    public static final String SUCCESS = "00";
    public static final String LOGIN_FAIL = "01";
    public static final String FAIL = "99";

    public static final String SESSION_LOGIN_ID = "loginId";
    public static final String SESSION_USER_ID = "userId";
}
```

모든 API가 `{code, message, data}` 모양으로 답하고, 화면 JS는 `code`가 `"00"`인지로 성공을 판단합니다. 세션 키 이름도 상수로 모읍니다.

## 7. 설정 파일 위치

| 파일 | 위치 |
|---|---|
| 웹 앱 시작 설정 | `WEB-INF/web.xml` |
| Spring MVC 설정 | `WEB-INF/config/dispatcher-servlet.xml` |
| DB, MyBatis, 트랜잭션 설정 | `resources/spring/context-datasource.xml` |
| 매퍼 | `resources/sqlmap/mappers/{프로젝트}/{기능}/*.xml` |

## 정리

- 기능 패키지 안을 controller, service, service/impl, dao로 나누고, 공통은 `cmmn`에 둡니다.
- 서비스와 `SqlSessionTemplate`은 빈 이름을 붙여 `@Resource(name)`로 주입합니다.
- 매퍼 namespace는 DAO 클래스 이름, SQL id는 메서드 이름이고, DAO는 `NS` 상수로 호출합니다.
- 설정값은 `egovProps/globals.properties`의 `Globals.` 키로, 응답은 `Response{code, message, data}`로 통일합니다.
