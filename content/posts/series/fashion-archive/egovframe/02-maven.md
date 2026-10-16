---
title: "eGovFrame Maven 저장소와 실행환경 모듈이 가져오는 것"
slug: "fashion-archive-egovframe-maven"
category: "development"
publishedAt: "2026-10-08"
summary: "pom.xml에 eGovFrame Maven 저장소를 등록하는 이유와, ptl.mvc와 psl.dataaccess 4.1.0 모듈이 Spring 5.3.20, MyBatis 3.5.10, log4j2를 어떻게 함께 가져오는지 공식 저장소의 POM 파일로 확인합니다."
series: "fashion-archive"
chapter: "egovframe"
seriesOrder: 2
---
## 이 글에서 할 일

Fashion Archive의 `pom.xml`에는 `spring-webmvc`도 `mybatis`도 직접 적혀 있지 않습니다. 그런데 코드는 둘 다 씁니다. 이 글에서는 eGovFrame 실행환경 모듈이 무엇을 가져오는지 **공식 Maven 저장소의 POM 파일**을 열어 확인합니다.

## 1. eGovFrame Maven 저장소 등록

`pom.xml`

```xml
<repositories>
    <repository>
        <id>egovframe</id>
        <url>https://maven.egovframe.go.kr/maven/</url>
        <releases><enabled>true</enabled></releases>
        <snapshots><enabled>false</enabled></snapshots>
    </repository>
</repositories>
```

Maven은 기본으로 **Maven 중앙 저장소**에서 라이브러리를 받습니다. eGovFrame 실행환경 모듈은 표준프레임워크의 자체 저장소에서 배포되므로, 그 주소를 `repositories`에 추가합니다.

| 설정 | 뜻 |
|---|---|
| `releases enabled` | 정식 버전을 받음 |
| `snapshots disabled` | 개발 중인 스냅샷 버전은 받지 않음. 빌드할 때마다 결과가 달라지는 것을 막음 |

## 2. 버전은 속성으로

```xml
<properties>
    <maven.compiler.release>17</maven.compiler.release>
    <spring.maven.artifact.version>5.3.20</spring.maven.artifact.version>
    <org.egovframe.rte.version>4.1.0</org.egovframe.rte.version>
</properties>
```

실행환경 모듈 두 개가 같은 `${org.egovframe.rte.version}`을 씁니다. 버전을 올릴 때 한 곳만 바꿉니다.

## 3. 모듈이 가져오는 것

Maven은 의존성의 의존성까지 따라가며 받습니다(**전이 의존성**). 공식 저장소의 POM 파일에 적힌 compile 범위 의존성을 정리하면 다음과 같습니다.

```text
org.egovframe.rte.ptl.mvc 4.1.0            (화면처리)
├── org.egovframe.rte.fdl.cmmn 4.1.0        (공통기반)
│   ├── org.egovframe.rte.fdl.logging 4.1.0
│   │   ├── log4j-core 2.17.2
│   │   ├── log4j-slf4j-impl 2.17.2
│   │   └── jcl-over-slf4j, log4j-over-slf4j 1.7.36
│   ├── spring-context-support, spring-tx 5.3.20
│   └── aspectjweaver, aspectjrt 1.9.9.1
├── spring-webmvc 5.3.20
├── spring-modules-validation 0.9, commons-validator 1.7
└── javax.annotation-api 1.3.2

org.egovframe.rte.psl.dataaccess 4.1.0     (데이터처리)
├── org.egovframe.rte.fdl.logging 4.1.0
├── spring-context, spring-orm 5.3.20
├── mybatis 3.5.10
├── mybatis-spring 2.0.7
├── ibatis-sqlmap 2.3.4.726
└── commons-collections 3.2.2
```

| 프로젝트가 쓰는 것 | 어디서 오나 |
|---|---|
| Spring MVC (`@Controller`, DispatcherServlet) | `ptl.mvc` → `spring-webmvc` |
| 트랜잭션 (`TransactionTemplate`, `@Transactional`) | `fdl.cmmn` → `spring-tx` |
| MyBatis, `SqlSessionTemplate` | `psl.dataaccess` → `mybatis`, `mybatis-spring` |
| log4j2 (`log4j2.xml`) | `fdl.logging` → `log4j-core`, `log4j-slf4j-impl` |
| 로그 API (`LoggerFactory.getLogger`) | SLF4J. `log4j-slf4j-impl`이 log4j2로 연결 |

`pom.xml` 주석("spring-webmvc 5.3.20, mybatis 3.5.10, mybatis-spring 2.0.7, log4j2 포함")이 이 내용과 맞습니다. 직접 확인하려면 다음 명령으로 실제 받은 의존성 나무를 봅니다.

```bash
mvn dependency:tree
```

### 직접 적은 의존성

실행환경이 가져오지 않거나 다른 범위로 가져오는 것은 `pom.xml`에 직접 적었습니다.

| 의존성 | 이유 |
|---|---|
| `javax.servlet-api` 3.1.0 (`provided`) | `ptl.mvc`의 POM에서도 `provided`라 전이되지 않음. 실행은 Tomcat이 제공 |
| `jstl-api` 1.2, `taglibs standard` 1.1.2 | `pom.xml` 주석: "eGov 모듈에는 test 스코프로만 있어 직접 선언한다" |
| `commons-dbcp2` 2.9.0 | `psl.dataaccess` POM에서는 test 범위. 실행에 쓰려면 직접 선언 |
| Tiles 3.0.5 | 레이아웃 |
| `postgresql` 42.7.3 | JDBC 드라이버 |
| `spring-security-core` 5.6.10 | BCrypt (`pom.xml` 주석: "BCrypt만 사용") |
| `jackson-databind` 2.14.2 | JSON 변환 |

**test 범위 의존성은 전이되지 않습니다.** 모듈의 POM에 이름이 있어도 범위가 `test`이면 그 모듈을 쓰는 프로젝트에는 들어오지 않습니다. JSTL과 commons-dbcp2를 직접 적은 이유입니다.

### commons-logging 제외

```xml
<artifactId>org.egovframe.rte.ptl.mvc</artifactId>
<exclusions>
    <exclusion>
        <groupId>commons-logging</groupId>
        <artifactId>commons-logging</artifactId>
    </exclusion>
</exclusions>
```

Spring 5.3은 자체 로그 연결 모듈(`spring-jcl`)을 쓰고, 실행환경은 `jcl-over-slf4j`로 commons-logging API를 SLF4J로 넘깁니다. 같은 이름의 클래스를 가진 원래 `commons-logging` jar가 함께 들어오면 어느 쪽이 쓰일지 헷갈리므로 빼 둡니다.

## 정리

- eGovFrame 실행환경 모듈은 표준프레임워크 자체 Maven 저장소에서 받으므로 `repositories`에 등록합니다.
- `ptl.mvc`는 Spring MVC와 공통기반, 로그를, `psl.dataaccess`는 MyBatis와 Spring ORM을 함께 가져옵니다.
- 실행에 필요하지만 실행환경 모듈이 compile 범위로 가져오지 않는 JSTL, commons-dbcp2 등은 직접 선언합니다.

## 출처

공식 eGovFrame Maven 저장소의 POM 파일 (2026-10-08 확인)

- [org.egovframe.rte.ptl.mvc 4.1.0](https://maven.egovframe.go.kr/maven/org/egovframe/rte/org.egovframe.rte.ptl.mvc/4.1.0/org.egovframe.rte.ptl.mvc-4.1.0.pom)
- [org.egovframe.rte.psl.dataaccess 4.1.0](https://maven.egovframe.go.kr/maven/org/egovframe/rte/org.egovframe.rte.psl.dataaccess/4.1.0/org.egovframe.rte.psl.dataaccess-4.1.0.pom)
- [org.egovframe.rte.fdl.cmmn 4.1.0](https://maven.egovframe.go.kr/maven/org/egovframe/rte/org.egovframe.rte.fdl.cmmn/4.1.0/org.egovframe.rte.fdl.cmmn-4.1.0.pom)
- [org.egovframe.rte.fdl.logging 4.1.0](https://maven.egovframe.go.kr/maven/org/egovframe/rte/org.egovframe.rte.fdl.logging/4.1.0/org.egovframe.rte.fdl.logging-4.1.0.pom)
