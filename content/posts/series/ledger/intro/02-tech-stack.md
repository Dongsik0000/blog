---
title: "기술 구성 한눈에 보기"
slug: "ledger-intro-tech-stack"
category: "development"
publishedAt: "2026-10-08"
summary: "Java 17, Spring Framework 6.2, MyBatis, JSP, PostgreSQL로 이루어진 Ledger의 기술 구성을 계층별로 정리하고, 각 기술이 어떤 역할을 맡는지 설명합니다."
series: "ledger"
chapter: "intro"
seriesOrder: 2
---
## 이 글에서 할 일

Ledger는 공공기관과 SI 프로젝트에서 많이 쓰는 **Spring MVC + MyBatis + JSP** 구성으로 만들었습니다. 이 글에서는 각 기술이 어느 계층에서 무슨 일을 하는지 지도를 그립니다. 하나하나의 자세한 설명은 뒤의 장에서 다룹니다.

## 계층별 지도

```text
[브라우저]  JSP가 만든 HTML + 화면별 JavaScript (fetch로 Ajax 호출) + Chart.js
     │  JSON
     ▼
[Tomcat 10.1]  war 파일을 실행하는 서블릿 컨테이너
     │
     ▼
[Spring MVC 6.2]  DispatcherServlet → 인터셉터 → Controller → (JSP 화면 | JSON 응답)
     │
     ▼
[Service / DAO]  Spring 빈. DAO가 MyBatis로 SQL 실행
     │
     ▼
[MyBatis 3.5 + HikariCP]  매퍼 XML의 SQL을 실행하고 결과를 Map으로
     │
     ▼
[PostgreSQL 16]
```

## 사용한 기술과 버전

`pom.xml` 기준입니다.

| 영역 | 기술 | 버전 | 역할 |
|---|---|---|---|
| 언어 | Java | 17 | 서버 코드 |
| 빌드 | Maven | 3.9 | 의존성 관리, war 빌드 |
| 웹 프레임워크 | Spring Web MVC | 6.2.7 | 요청을 컨트롤러로 연결, 화면과 JSON 응답 |
| DB 접근 | MyBatis, mybatis-spring | 3.5.19, 3.0.4 | SQL을 XML에 두고 실행 |
| 커넥션 풀 | HikariCP | 6.3.0 | DB 연결을 미리 만들어 재사용 |
| JDBC 드라이버 | PostgreSQL JDBC | 42.7.5 | Java와 PostgreSQL 연결 |
| JSON | Jackson | 2.18.3 | Java 객체와 JSON 변환 |
| 비밀번호 | spring-security-crypto | 6.5.0 | BCrypt 해시 (Spring Security 전체가 아니라 암호화 모듈만) |
| 화면 | JSP, JSTL | 3.0 | 서버에서 HTML 만들기 |
| 화면 스크립트 | 순수 JavaScript, Chart.js | Chart.js 4.4 | Ajax 호출, 차트 |
| 로그 | SLF4J, Logback | 2.0.17, 1.5.18 | 로그 출력 |
| 테스트 | JUnit Jupiter | 5.11.4 | 단위 테스트 |
| 실행 | Tomcat | 10.1 | war 실행 |
| DB | PostgreSQL | 16 | 데이터 저장 |
| 배포 | Docker Compose, nginx, GitHub Actions | | 서버 실행과 자동 배포 |

### 눈여겨볼 선택

**Spring Boot가 아니라 Spring Framework + XML 설정입니다.** Boot는 설정을 자동으로 해 주지만, 무엇이 어떻게 연결되는지 감춰져 있습니다. Ledger는 `web.xml`, `dispatcher-servlet.xml`, `context-datasource.xml`에 모든 연결을 직접 적었습니다. 실무의 전자정부 프레임워크 프로젝트와 같은 방식이라, 설정 파일만 읽으면 앱이 어떻게 조립되는지 보입니다. [Spring MVC 장](/posts/ledger-spring-servlet-war)에서 한 줄씩 읽습니다.

**JPA가 아니라 MyBatis입니다.** SQL을 직접 쓰고, 그 SQL을 XML 파일에 모아 둡니다. 주기별 집계처럼 SQL로 표현하기 좋은 조회가 많아서 SQL을 그대로 보는 편이 이해하기 쉽습니다.

**Tomcat 10.1과 `jakarta.*` 패키지입니다.** Spring 6과 Tomcat 10부터 서블릿 API의 패키지 이름이 `javax.servlet`에서 `jakarta.servlet`으로 바뀌었습니다. 인터넷의 예전 예제를 따라 할 때 `javax`로 import하면 컴파일되지 않습니다.

**jQuery를 쓰지 않습니다.** 브라우저 기본 기능인 `fetch`로 Ajax를 만들었습니다. [화면 장](/posts/ledger-view-ajax)에서 다룹니다.

## 정리

- 화면은 JSP와 순수 JavaScript, 서버는 Spring MVC, DB 접근은 MyBatis, 저장은 PostgreSQL입니다.
- Spring Boot 없이 XML 설정으로 모든 연결을 직접 적었습니다.
- 서블릿 API는 `jakarta.*` 패키지를 씁니다.
