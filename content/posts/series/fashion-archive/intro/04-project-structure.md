---
title: "폴더와 패키지 구조"
slug: "fashion-archive-intro-project-structure"
category: "development"
publishedAt: "2026-10-08"
summary: "cmmn 공통 패키지와 photo 기능 패키지, 설정과 매퍼, Tiles 레이아웃과 JSP, 배포 파일이 어디에 있는지 정리하고 기능 하나를 고칠 때 열어야 할 파일을 짚습니다."
series: "fashion-archive"
chapter: "intro"
seriesOrder: 4
---
## 이 글에서 할 일

Fashion Archive의 Java 코드는 패키지 두 개(`cmmn`, `photo`)뿐입니다. 이 글에서는 폴더 구조를 보고, 기능 하나를 고칠 때 어떤 파일을 열어야 하는지 정리합니다.

## 저장소 맨 위

```text
fashion-archive/
├── pom.xml                       ← eGovFrame 저장소와 의존성
├── src/
├── Dockerfile                    ← Tomcat 9 이미지에 war 복사
├── docker-compose.yml            ← 서버 실행 설정
├── deploy/
│   ├── deploy.sh                 ← GitHub Actions가 서버에서 실행하는 배포 스크립트
│   ├── nginx-fashion-archive.conf← /p1/, /uploads/ nginx 설정 원본
│   └── OPERATIONS.md             ← 운영 가이드
├── docs/                         ← 설계 문서와 구현 계획
└── .github/workflows/deploy.yml  ← push하면 배포
```

## Java 패키지

```text
src/main/java/com/fashion/
├── cmmn/                              공통
│   ├── controller/CmmnController       /login, /signup, /logout 화면
│   ├── controller/CmmnApiController    /api/login, /api/signup
│   ├── controller/GlobalModelAdvice    모든 화면에 카테고리 목록 넣기
│   ├── dao/CmmnDAO                     카테고리, 사용자 SQL
│   ├── service/CmmnService (+impl)
│   ├── interceptor/LoginInterceptor    로그인 확인, /api 요청 검사
│   ├── listener/AssetVersionListener   정적 파일 버전
│   ├── storage/LocalStorage            사진 파일 저장과 삭제
│   └── util/                           Response, Constants, Validation, LoginAttemptLimiter, RequestUtil
└── photo/                             사진과 제품 링크
    ├── controller/PhotoController      화면 (뷰 이름만 반환)
    ├── controller/PhotoApiController   JSON API (조회, 등록, 수정, 삭제, 링크)
    ├── dao/PhotoDAO, LinkDAO
    └── service/PhotoService (+impl)
```

`cmmn`, `service/impl`, `dao` 같은 이름은 전자정부 표준프레임워크 프로젝트의 관례입니다([따른 관례](/posts/fashion-archive-egovframe-conventions)).

화면 컨트롤러와 API 컨트롤러를 **클래스로 나눈 것**이 특징입니다. `PhotoController`는 주소를 받아 뷰 이름만 돌려주고, 데이터는 화면이 뜬 뒤 `PhotoApiController`의 JSON API로 받습니다([컨트롤러 나누기](/posts/fashion-archive-spring-mvc-controllers)).

## resources

```text
src/main/resources/
├── spring/context-datasource.xml          DB 연결 풀, MyBatis, 트랜잭션, BCrypt 빈
├── egovProps/globals.properties           설정값 (환경 변수 참조)
├── sqlmap/mybatis-config.xml
├── sqlmap/mappers/fashion/cmmn/cmmn.xml   카테고리, 사용자
├── sqlmap/mappers/fashion/photo/photo.xml 사진, 사진-카테고리
├── sqlmap/mappers/fashion/photo/link.xml  제품 링크
├── schema.sql                             테이블 정의
└── log4j2.xml                             로그
```

## webapp

```text
src/main/webapp/
├── WEB-INF/
│   ├── web.xml
│   ├── config/dispatcher-servlet.xml      Spring MVC, 뷰 리졸버, 인터셉터
│   ├── tiles/tiles-layout.xml             Tiles 정의
│   ├── layout/fashionLayout.jsp           공통 레이아웃 (헤더, 카테고리 메뉴)
│   └── jsp/
│       ├── fashion/photo/list.jsp, detail.jsp
│       ├── fashion/admin/photoForm.jsp    등록과 수정 공용
│       ├── login/loginMain.jsp, signupMain.jsp   (레이아웃 없음)
│       └── error.jsp
├── META-INF/context.xml                   SameSite 쿠키 설정
├── common/taglib.jsp
└── resources/
    ├── css/, fonts/ (Pretendard), images/ (아이콘)
    ├── js/common/common.js                App.ajax, App.upload, App.toast
    ├── js/app/photo/list.js, detail.js, photoForm.js
    ├── js/app/login/login.js, signup.js
    └── manifest.json
```

JSP 폴더 이름(`fashion/photo/list`)이 Tiles 정의의 와일드카드와 맞물려 있습니다. 뷰 이름 `/fashion/photo/list`가 `/WEB-INF/jsp/fashion/photo/list.jsp`를 본문으로 씁니다([Tiles 설정](/posts/fashion-archive-tiles-tiles-setup)).

## 기능 하나에 열어야 할 파일

예를 들어 "제품 링크에 칸을 하나 추가"하려면 다음 순서로 엽니다.

| 순서 | 파일 | 할 일 |
|---|---|---|
| 1 | `schema.sql` | `product_link`에 컬럼 추가 |
| 2 | `sqlmap/mappers/fashion/photo/link.xml` | SELECT, INSERT, UPDATE에 컬럼 추가 |
| 3 | `PhotoApiController.linkParam` | 입력 검증 추가 |
| 4 | `WEB-INF/jsp/fashion/photo/detail.jsp` | 화면 요소 |
| 5 | `resources/js/app/photo/detail.js` | 읽기 모드와 편집 모드에 표시 |

DAO와 Service는 Map을 그대로 넘기므로, 컬럼만 추가할 때는 고칠 필요가 없습니다.

## 정리

- Java 코드는 공통(`cmmn`)과 사진(`photo`) 두 패키지이고, 각각 controller, service, impl, dao로 나뉩니다.
- 화면 컨트롤러와 JSON API 컨트롤러를 다른 클래스로 둡니다.
- 설정은 `resources/spring`, `WEB-INF/config`, `WEB-INF/tiles`에, 매퍼는 기능 이름 폴더에 있습니다.
- JSP 폴더 구조가 Tiles 정의와 맞물려 본문 JSP를 찾습니다.
