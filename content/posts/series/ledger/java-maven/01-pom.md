---
title: "pom.xml 읽기"
slug: "ledger-java-maven-pom"
category: "development"
publishedAt: "2026-10-08"
summary: "Maven의 설정 파일 pom.xml을 위에서부터 읽으며 좌표, war 패키징, 버전 속성, 의존성과 scope, 플러그인이 무엇을 뜻하는지 Ledger의 실제 설정으로 설명합니다."
series: "ledger"
chapter: "java-maven"
seriesOrder: 1
---
## 이 글에서 할 일

**Maven**은 Java 프로젝트의 빌드 도구입니다. 필요한 라이브러리를 인터넷에서 받아 오고(의존성 관리), 소스를 컴파일하고, 테스트를 돌리고, 실행 파일(war)로 묶어 줍니다. 이 모든 설정이 `pom.xml` 하나에 있습니다. Ledger의 `pom.xml`을 위에서부터 읽습니다.

## 1. 프로젝트 좌표

```xml
<groupId>ledger</groupId>
<artifactId>ledger</artifactId>
<version>1.0.0</version>
<packaging>war</packaging>
```

| 항목 | 뜻 |
|---|---|
| `groupId` | 만든 조직이나 프로젝트 묶음 이름. 보통 도메인을 거꾸로 씁니다(`kr.example`) |
| `artifactId` | 이 프로젝트의 이름 |
| `version` | 버전 |
| `packaging` | 빌드 결과물의 형태. `war`는 Tomcat 같은 서블릿 컨테이너에 올리는 웹 앱 묶음입니다 |

`jar`는 일반 Java 프로그램이나 라이브러리, `war`(Web ARchive)는 `WEB-INF/`, JSP, CSS, JS까지 웹 앱 전체를 담은 압축 파일입니다. Spring Boot는 보통 Tomcat을 품은 `jar`로 만들지만, Ledger는 Tomcat을 따로 두고 `war`를 올리는 전통적인 방식입니다.

## 2. 속성 (properties)

```xml
<properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring.version>6.2.7</spring.version>
    <spring.security.version>6.5.0</spring.security.version>
</properties>
```

- `maven.compiler.release`: Java 17 문법으로 컴파일하고 Java 17에서 실행되는 결과를 만듭니다.
- `sourceEncoding`: 소스 파일을 UTF-8로 읽습니다. 빠뜨리면 운영체제에 따라 한글 주석이나 문자열이 깨질 수 있습니다.
- `spring.version`: 직접 만든 변수입니다. 아래에서 `${spring.version}`으로 씁니다. Spring 모듈 세 개의 버전을 한 곳에서 바꿀 수 있습니다.

## 3. 의존성 (dependencies)

의존성은 "이 프로젝트가 쓰는 남의 라이브러리"입니다. Maven 중앙 저장소에서 자동으로 받아 옵니다.

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webmvc</artifactId>
    <version>${spring.version}</version>
</dependency>
```

Ledger의 의존성을 역할별로 묶으면 다음과 같습니다.

| 묶음 | artifactId | 역할 |
|---|---|---|
| Spring | `spring-webmvc`, `spring-jdbc`, `spring-context-support` | 웹 MVC, DB 트랜잭션, 스케줄러 등 부가 기능 |
| 보안 | `spring-security-crypto` | BCrypt 비밀번호 해시**만** |
| DB | `mybatis`, `mybatis-spring`, `HikariCP`, `postgresql` | SQL 실행, Spring 연결, 커넥션 풀, JDBC 드라이버 |
| JSON | `jackson-databind`, `jackson-datatype-jsr310` | JSON 변환, `LocalDate` 같은 날짜 타입 지원 |
| 웹 표준 | `jakarta.servlet-api`, `jakarta.annotation-api`, JSTL | 서블릿, `@Resource`, JSP 태그 |
| 로그 | `slf4j-api`, `logback-classic` | 로그 |
| 테스트 | `junit-jupiter` | JUnit 5 |

### 전이 의존성과 직접 고정

`spring-webmvc` 하나만 적어도 `spring-core`, `spring-beans` 등 Spring이 필요로 하는 라이브러리가 함께 따라옵니다. 이것을 **전이 의존성**이라고 합니다. 편리하지만, 버전이 의도와 다르게 섞일 수 있습니다. Ledger의 주석이 실제 사례입니다.

```xml
<!-- 로깅. slf4j-api 는 전이 의존성으로 1.7 이 섞여 들어오므로 2.x 로 직접 고정 (logback 1.5 는 SLF4J 2 필요) -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.17</version>
</dependency>
```

다른 라이브러리가 끌고 온 옛 버전(1.7) 대신 원하는 버전(2.0)을 직접 적으면 Maven은 직접 적은 쪽을 씁니다. 어떤 버전이 어디서 들어왔는지는 다음 명령으로 봅니다.

```bash
mvn dependency:tree
```

### scope: 언제 필요한가

```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
    <scope>provided</scope>
</dependency>
```

| scope | 컴파일 | 테스트 | war에 포함 | 예 |
|---|---|---|---|---|
| (기본) compile | O | O | O | Spring, MyBatis |
| `provided` | O | O | **X** | 서블릿 API. 실행할 때는 **Tomcat이 제공**합니다 |
| `test` | X | O | X | JUnit |

서블릿 API를 `provided`로 두지 않고 war에 넣으면, Tomcat이 가진 것과 war 안의 것이 충돌할 수 있습니다. "컴파일할 때는 필요하지만 실행 환경이 이미 갖고 있는 것"은 `provided`입니다.

## 4. 빌드 설정 (build)

```xml
<build>
    <finalName>ledger</finalName>
    <plugins>
        <plugin><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version></plugin>
        <plugin><artifactId>maven-war-plugin</artifactId><version>3.4.0</version></plugin>
        <plugin><artifactId>maven-surefire-plugin</artifactId><version>3.5.2</version></plugin>
    </plugins>
</build>
```

| 줄 | 뜻 |
|---|---|
| `finalName` | 결과 파일 이름. `target/ledger.war`가 됩니다 (없으면 `ledger-1.0.0.war`) |
| `maven-compiler-plugin` | Java 컴파일 |
| `maven-war-plugin` | war로 묶기 |
| `maven-surefire-plugin` | 테스트 실행. JUnit 5를 쓰려면 최신 버전이 필요합니다 |

플러그인 버전을 적어 두지 않으면 Maven 버전에 따라 기본 플러그인 버전이 달라질 수 있습니다. 버전을 고정하면 누가 빌드해도 같은 결과가 나옵니다.

## 정리

- `pom.xml`은 프로젝트 좌표, 의존성, 빌드 방법을 담습니다.
- `packaging`이 `war`라서 Tomcat에 올리는 웹 앱으로 빌드됩니다.
- 버전은 `properties`로 한 곳에서 관리하고, 전이 의존성 충돌은 직접 버전을 적어 해결합니다.
- 서블릿 API처럼 Tomcat이 제공하는 것은 `provided`, 테스트 도구는 `test` scope입니다.
