---
title: "전자정부 프레임워크 방식을 따른 부분"
slug: "ledger-spring-egov-style"
category: "development"
publishedAt: "2026-10-08"
summary: "Ledger는 전자정부 표준프레임워크 라이브러리를 쓰지 않지만 패키지 이름, 설정 파일 위치, Service와 DAO 구조 같은 관례를 따랐습니다. 실제로 eGovFrame을 쓰는 fashion-archive와 비교해 무엇이 같고 다른지 정리합니다."
series: "ledger"
chapter: "spring"
seriesOrder: 7
---
## 이 글에서 할 일

**전자정부 표준프레임워크**(eGovFrame)는 공공기관 시스템을 만들 때 쓰는 Spring 기반 개발 환경입니다. 공공 SI 프로젝트에서 많이 쓰여, 그 코드 모양에 익숙한 개발자가 많습니다.

Ledger는 eGovFrame **라이브러리를 쓰지 않습니다.** `pom.xml`에 `org.egovframe` 의존성이 없고, 순수 Spring Framework 6.2와 MyBatis만 씁니다. 대신 **코드 구조와 이름은 eGovFrame 프로젝트의 관례**를 따랐습니다. 이 글에서는 eGovFrame 4.1 라이브러리를 실제로 쓰는 다른 프로젝트 **fashion-archive**와 나란히 놓고 비교합니다. eGovFrame 자체는 fashion-archive 시리즈에서 자세히 다룹니다.

## 같은 부분: 따른 관례

| 관례 | Ledger | fashion-archive (eGovFrame 4.1) |
|---|---|---|
| 기능 패키지 안의 계층 | `controller`, `service`, `service/impl`, `dao` | 같음 |
| 공통 패키지 | `ledger.cmmn` (`util`, `controller`) | `com.fashion.cmmn` (`util`, `dao`, `interceptor` 등) |
| 서비스 인터페이스와 구현 분리 | `XxxService` + `XxxServiceImpl` | 같음 |
| DAO | `SqlSessionTemplate`을 `@Resource(name=...)`로 주입받아 `selectOne`, `selectList` 호출 | 같음 |
| SQL 이름 | DAO 클래스 전체 이름을 매퍼 namespace로 (`ledger.settings.dao.SettingsDAO`) | 같음 (`com.fashion.cmmn.dao.CmmnDAO`) |
| 설정값 파일 | `egovProps/globals.properties` | 같음 |
| Spring 설정 위치 | `spring/context-*.xml`, `WEB-INF/config/dispatcher-servlet.xml` | 같음 |
| 공통 응답과 결과 코드 | `cmmn/util/Response`, `Constants` | 같은 이름의 클래스 |

같은 사람이 같은 관례로 만들었기 때문에, 한 프로젝트를 읽을 수 있으면 다른 프로젝트도 같은 위치에서 같은 것을 찾을 수 있습니다.

## 다른 부분

| 항목 | Ledger | fashion-archive |
|---|---|---|
| 프레임워크 | 순수 Spring 6.2 | eGovFrame 4.1 (`org.egovframe.rte.ptl.mvc`, `org.egovframe.rte.psl.dataaccess`) |
| 서블릿 API 패키지 | `jakarta.*` (Tomcat 10.1) | `javax.*` (Tomcat 9) |
| 화면 레이아웃 | JSP 태그 파일 (`layout.tag`) | Tiles 3 (`tiles-layout.xml`) |

fashion-archive가 쓰는 eGovFrame 4.1은 Spring 5.3 기반이라(`pom.xml` 주석: spring-webmvc 5.3.20) `javax.*`를 씁니다. Ledger는 라이브러리 없이 관례만 따랐기 때문에 Spring 6과 Tomcat 10.1, `jakarta.*`를 쓸 수 있었습니다.

레이아웃에 대해서는 설정 파일 주석에 선택이 남아 있습니다.

```xml
<!-- 레이아웃(메뉴+본문)은 Tiles 대신 /WEB-INF/tags/layout.tag 로 각 JSP가 자기 본문을 감싼다. -->
```

태그 파일은 JSP 표준 기능이라 별도 라이브러리가 필요 없습니다. [태그 파일 레이아웃](/posts/ledger-view-layout-tag)에서 다룹니다.

## 관례를 따르면 좋은 점

- eGovFrame 프로젝트를 해 본 사람은 처음 봐도 어디에 무엇이 있는지 압니다.
- 반대로 eGovFrame 프로젝트에 투입되었을 때 같은 구조를 바로 읽을 수 있습니다.
- 라이브러리 없이 관례만 따르면 최신 Spring을 쓸 수 있습니다.

## 정리

- Ledger는 eGovFrame 라이브러리 없이 순수 Spring 6.2와 MyBatis로 만들었습니다.
- 계층 패키지, `cmmn`, `globals.properties`, DAO의 `SqlSessionTemplate` 사용 같은 관례는 eGovFrame 프로젝트인 fashion-archive와 같습니다.
- 프레임워크 버전(Spring 6과 `jakarta.*`)과 레이아웃 방식(태그 파일)이 다릅니다.
