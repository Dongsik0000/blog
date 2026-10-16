---
title: "같은 관례로 만든 Ledger와 비교"
slug: "fashion-archive-egovframe-vs-ledger"
category: "development"
publishedAt: "2026-10-08"
summary: "같은 사람이 같은 관례로 만든 두 앱, eGovFrame 4.1 기반의 Fashion Archive와 순수 Spring 6.2 기반의 Ledger를 프레임워크, 설정 방식, 레이아웃, 연결 풀, 로그, 예외 처리, 배포 빌드에서 나란히 비교합니다."
series: "fashion-archive"
chapter: "egovframe"
seriesOrder: 5
---
## 이 글에서 할 일

Fashion Archive와 [Ledger](/series/ledger)는 같은 서버에서 돌아가는 Java 웹 앱이고, 패키지와 파일 이름의 관례가 같습니다. 하지만 기반 기술과 몇 가지 설계가 다릅니다. 두 프로젝트를 나란히 놓으면 각 선택이 코드의 어디에 드러나는지 잘 보입니다.

먼저 만든 쪽은 Fashion Archive(2026-09-16 시작)이고, Ledger(2026-09-22 시작)가 뒤에 만들어졌습니다. 로그인 시도 제한(`LoginAttemptLimiter`)과 클라이언트 IP 판별(`RequestUtil`)은 Ledger에서 만든 것을 Fashion Archive로 옮겨 왔습니다(Fashion Archive 커밋 `38f6f95`).

## 같은 것

| 항목 | 두 프로젝트 공통 |
|---|---|
| 패키지 | 기능 → controller, service, service/impl, dao. 공통은 `cmmn` |
| DAO | `SqlSessionTemplate` + 매퍼 namespace를 DAO 클래스 이름으로 |
| 설정값 | `egovProps/globals.properties`의 `Globals.` 키, 값은 `${환경변수:기본값}` |
| 응답 | `Response{code, message, data}`, 성공 `"00"`, 실패 `"99"` |
| 로그인 | 세션 + `HandlerInterceptor`, BCrypt (Spring Security 전체가 아니라 암호화 기능만) |
| 화면 | JSP + 화면별 JS 모듈(`App.xxx`), `fetch`로 만든 `App.ajax` |
| 보안 | Ajax 헤더 검사로 CSRF 방어, `SameSite=Lax`, 가입 코드, 시도 제한 |
| 실행 | Docker의 Tomcat, 호스트 네트워크, `127.0.0.1`에만 열기, nginx 하위 경로 |
| 배포 | GitHub Actions → SSH → `deploy/deploy.sh` (같은 `flock` 잠금 파일) |

## 다른 것

| 항목 | Fashion Archive | Ledger |
|---|---|---|
| 기반 | eGovFrame 4.1 실행환경 | 순수 Spring Framework |
| Spring | 5.3.20 | 6.2.7 |
| 서블릿 | `javax` / Tomcat 9 | `jakarta` / Tomcat 10.1 |
| Spring 컨텍스트 | DispatcherServlet 하나에 모든 설정 | 루트 + 서블릿 두 개 |
| 레이아웃 | Tiles 3 | JSP 태그 파일 |
| 연결 풀 | commons-dbcp2 | HikariCP |
| 로그 | log4j2 (콘솔 + 날짜별 파일) | Logback (콘솔) |
| 트랜잭션 | `TransactionTemplate` 빈을 API 컨트롤러에서 사용 | `@Transactional` 중심, 반복 처리는 `TransactionTemplate` |
| 예외 처리 | API 메서드마다 try/catch로 `"99"` 응답 | `@ControllerAdvice` 한 곳에서 처리 |
| 화면과 API | 화면 컨트롤러와 API 컨트롤러를 다른 클래스로 | 한 컨트롤러에 화면 GET과 Ajax POST |
| 스키마 | `schema.sql` 한 파일 | 번호 붙인 SQL 파일 000~009 |
| 배포 빌드 | 서버에서 `mvn -q package`(테스트 포함) 후 war 복사 | Docker 멀티 스테이지 빌드 |

## 차이가 코드에 드러나는 곳

### Spring 컨텍스트: 하나 vs 둘

Fashion Archive `web.xml`

```xml
<!-- 루트 컨텍스트 없이 DispatcherServlet 하나에 모든 설정을 싣는다 -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
    <init-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>
            /WEB-INF/config/dispatcher-servlet.xml
            classpath:spring/context-datasource.xml
        </param-value>
    </init-param>
```

설정 파일 두 개를 DispatcherServlet 하나가 함께 읽어 **컨텍스트가 하나**입니다. 모든 빈이 한 상자에 있으므로 `property-placeholder`나 `tx:annotation-driven`을 어느 컨텍스트에 둘지 고민할 필요가 없습니다. Ledger는 루트와 서블릿 컨텍스트로 나누었고, 그래서 설정을 양쪽에 둔 곳이 있습니다([Ledger의 두 컨텍스트](/posts/ledger-spring-two-contexts)).

### 예외 처리: 메서드마다 vs 한 곳

Fashion Archive `PhotoApiController`

```java
try {
    ...
    return Response.of(Constants.SUCCESS, data);
} catch (IllegalArgumentException e) {
    return Response.of(Constants.FAIL, e.getMessage(), null);
} catch (Exception e) {
    logger.error("사진 상세 조회 중 오류", e);
    return Response.of(Constants.FAIL, "사진을 불러오지 못했습니다.", null);
}
```

각 API가 자기 상황에 맞는 실패 문장("사진을 불러오지 못했습니다")을 직접 정합니다. 입력 검증은 `Validation`이 사용자용 문장을 담은 `IllegalArgumentException`을 던지고, API가 그 문장을 그대로 응답합니다([입력 검증과 오류 응답](/posts/fashion-archive-spring-mvc-validation)). 여러 SQL을 묶어야 하는 곳은 `TransactionTemplate`으로 범위를 정하므로, try/catch 안에서도 트랜잭션은 그 범위에서 롤백됩니다.

### 배포 빌드: 서버의 Maven vs 컨테이너 안의 Maven

Fashion Archive `deploy/deploy.sh`

```bash
# Dockerfile 이 target/fashion-archive.war 를 복사하므로 이미지 빌드 전에 war 를 만든다
mvn -q package
docker compose up -d --build
```

서버에서 `mvn -q package`로 war를 만들고, Dockerfile은 그 war를 복사합니다. `package` 단계는 `test` 단계를 포함하므로 **배포할 때 테스트가 실행되고, 실패하면 `set -e`로 배포가 멈춥니다.** Ledger는 Docker 멀티 스테이지 빌드로 컨테이너 안에서 Maven을 실행합니다.

## 정리

- 두 프로젝트는 패키지, DAO, 설정값, 응답 형식, 로그인, 배포 방식의 관례가 같습니다.
- Fashion Archive는 eGovFrame 4.1(Spring 5.3, `javax`, Tomcat 9), Ledger는 Spring 6.2(`jakarta`, Tomcat 10.1)입니다.
- 컨텍스트 구성, 레이아웃, 연결 풀, 로그, 예외 처리, 배포 빌드가 다르고, 각각 코드의 정해진 곳에 드러납니다.
