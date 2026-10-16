---
title: "기술 구성 한눈에 보기"
slug: "fashion-archive-intro-tech-stack"
category: "development"
publishedAt: "2026-10-08"
summary: "전자정부 표준프레임워크 4.1 실행환경 위에 Spring MVC 5.3, MyBatis, JSP와 Tiles, PostgreSQL로 만든 Fashion Archive의 기술 구성을 계층별로 정리합니다."
series: "fashion-archive"
chapter: "intro"
seriesOrder: 2
---
## 이 글에서 할 일

Fashion Archive는 **전자정부 표준프레임워크(eGovFrame) 4.1** 실행환경을 기반으로 만들었습니다. 이 글에서는 각 기술이 어느 계층에서 무슨 일을 하는지 지도를 그립니다.

## 계층별 지도

```text
[브라우저]  Tiles가 조립한 JSP 화면 + 화면별 JavaScript (fetch로 JSON 호출)
     │
     ▼
[Tomcat 9]  javax.servlet 기반 서블릿 컨테이너, 컨텍스트 경로 /p1
     │
     ▼
[Spring MVC 5.3]  DispatcherServlet → LoginInterceptor → Controller
     │                                   ├─ 화면 컨트롤러: 뷰 이름 → Tiles 레이아웃 + JSP
     │                                   └─ API 컨트롤러: Response → JSON
     ▼
[Service / DAO]  DAO가 SqlSessionTemplate으로 매퍼 SQL 실행
     │
     ▼
[MyBatis + commons-dbcp2]        [LocalStorage]  사진 파일 → 서버 디스크
     │                                  │
     ▼                                  ▼
[PostgreSQL 16]                  [nginx]  /uploads/ 로 사진을 직접 보냄
```

## 사용한 기술과 버전

`pom.xml` 기준입니다.

| 영역 | 기술 | 버전 | 역할 |
|---|---|---|---|
| 기반 | eGovFrame 실행환경 (`rte.ptl.mvc`, `rte.psl.dataaccess`) | 4.1.0 | Spring MVC와 MyBatis, 로그 라이브러리를 함께 가져오는 의존성 묶음 |
| 언어 | Java | 17 | `maven.compiler.release` 17 |
| 웹 프레임워크 | Spring Web MVC | 5.3.20 | eGovFrame 실행환경이 가져옴 |
| 서블릿 API | `javax.servlet-api` | 3.1.0 | Tomcat 9가 제공 (`provided`) |
| DB 접근 | MyBatis, mybatis-spring | 3.5.10, 2.0.7 | eGovFrame 실행환경이 가져옴 |
| 커넥션 풀 | commons-dbcp2 | 2.9.0 | DB 연결 재사용 |
| JDBC 드라이버 | PostgreSQL JDBC | 42.7.3 | |
| 화면 | JSP, JSTL, Tiles | JSTL 1.2, Tiles 3.0.5 | 공통 레이아웃 조립 |
| 화면 스크립트 | 순수 JavaScript | | `fetch`, `FormData` |
| JSON | Jackson | 2.14.2 | `@ResponseBody` 변환 |
| 비밀번호 | spring-security-core | 5.6.10 | BCrypt 해시**만** 사용 |
| 로그 | log4j2 | eGovFrame 실행환경이 가져옴 | 콘솔과 날짜별 파일 |
| 테스트 | JUnit Jupiter | 5.10.2 | 단위 테스트 |
| 실행 | Tomcat | 9 (Docker `tomcat:9-jdk17`) | war 실행 |
| DB | PostgreSQL | 16 | |
| 배포 | Docker Compose, nginx, GitHub Actions | | 서버 실행과 자동 배포 |

Spring과 MyBatis 버전을 `pom.xml`에 직접 적지 않은 것이 이 프로젝트의 특징입니다. eGovFrame 실행환경 모듈이 정해진 버전을 함께 가져옵니다. 어떤 모듈이 무엇을 가져오는지는 [eGovFrame Maven 의존성](/posts/fashion-archive-egovframe-maven)에서 공식 저장소의 POM으로 확인합니다.

## 눈여겨볼 선택

**`javax`와 Tomcat 9입니다.** eGovFrame 4.1의 Spring 5.3은 `javax.servlet`을 씁니다. 그래서 `jakarta.servlet`을 쓰는 Tomcat 10 이상이 아니라 Tomcat 9에서 실행합니다([Spring 5.3과 Tomcat 9](/posts/fashion-archive-egovframe-javax-tomcat9)).

**레이아웃은 Tiles입니다.** 화면 컨트롤러가 `/fashion/photo/list` 같은 뷰 이름을 돌려주면, Tiles가 공통 레이아웃에 본문 JSP를 끼워 완성합니다([Tiles 장](/posts/fashion-archive-tiles-tiles-setup)).

**사진은 서버 디스크에 저장합니다.** DB에는 사진의 공개 주소만 저장하고, 파일은 nginx가 직접 보냅니다([사진 업로드 장](/posts/fashion-archive-upload-multipart)).

## 정리

- eGovFrame 4.1 실행환경이 Spring MVC 5.3, MyBatis, log4j2를 함께 가져옵니다.
- `javax.servlet` 기반이라 Tomcat 9에서 실행합니다.
- 화면은 JSP와 Tiles, 데이터는 JSON API, 사진은 서버 디스크와 nginx가 맡습니다.
