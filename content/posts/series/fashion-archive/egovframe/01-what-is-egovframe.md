---
title: "전자정부 표준프레임워크와 실행환경"
slug: "fashion-archive-egovframe-what-is-egovframe"
category: "development"
publishedAt: "2026-10-08"
summary: "전자정부 표준프레임워크가 무엇이고 왜 만들어졌는지, 실행, 개발, 운영, 관리 4개 환경과 실행환경의 계층을 행정안전부 소개 자료 기준으로 정리하고, Fashion Archive가 그중 무엇을 쓰는지 짚습니다."
series: "fashion-archive"
chapter: "egovframe"
seriesOrder: 1
---
## 이 글에서 할 일

공공기관 웹 시스템을 만드는 프로젝트에서는 **전자정부 표준프레임워크**(줄여서 eGovFrame, 표준프레임워크)라는 이름을 자주 듣습니다. 이 글에서는 표준프레임워크가 무엇인지 정리하고, Fashion Archive가 그중 어떤 부분을 쓰는지 연결합니다.

## 무엇을 위해 만들었나

행정안전부의 소개 자료는 목적을 이렇게 설명합니다.

> 공공 정보시스템이 특정 사업자 및 기술에 종속되는 현상을 탈피하고, 소프트웨어(SW) 재사용성을 높여 효율적인 정보화를 추진

공공 시스템마다 업체가 서로 다른 독자 프레임워크로 만들면, 유지보수를 그 업체에 계속 맡길 수밖에 없고 다른 시스템과 코드를 나누기도 어렵습니다. 표준프레임워크는 **오픈소스(Spring, MyBatis 등) 위에 공통 구조와 기능을 표준으로 정해** 누가 만들어도 비슷한 구조가 되게 합니다.

## 4개의 환경

| 환경 | 하는 일 |
|---|---|
| **실행환경** | 앱이 실행될 때 쓰는 기반 기능. 화면 처리, 업무 처리, 데이터 처리 등 |
| 개발환경 | 구현, 배포, 테스트 도구 |
| 운영환경 | 모니터링과 운영 관리 도구 |
| 관리환경 | 서비스 요청과 변경 관리 |

개발자가 코드에서 직접 만나는 것은 **실행환경**입니다. 실행환경은 Maven 의존성(라이브러리)으로 프로젝트에 들어옵니다.

## 실행환경의 계층

행정안전부 자료는 실행환경을 **화면처리, 업무처리, 데이터처리, 연계통합, 공통기반, 배치처리** 계층과 모바일 지원으로 설명합니다. 계층마다 Maven 모듈이 있고, 모듈 이름에 계층이 드러납니다.

```text
org.egovframe.rte.ptl.mvc         ← ptl: 화면처리(Presentation Layer), Spring MVC 기반
org.egovframe.rte.psl.dataaccess  ← psl: 데이터처리(Persistence Layer), MyBatis 기반
org.egovframe.rte.fdl.cmmn        ← fdl: 공통기반(Foundation Layer)
org.egovframe.rte.fdl.logging     ← fdl: 로그
```

| 이름 조각 | 뜻 |
|---|---|
| `rte` | Runtime Environment (실행환경) |
| `ptl` | 화면처리 계층 |
| `psl` | 데이터처리 계층 |
| `fdl` | 공통기반 계층 |

## Fashion Archive가 쓰는 것

`pom.xml`

```xml
<!-- 표준프레임워크 실행환경: spring-webmvc 5.3.20, mybatis 3.5.10, mybatis-spring 2.0.7, log4j2 포함 -->
<dependency>
    <groupId>org.egovframe.rte</groupId>
    <artifactId>org.egovframe.rte.ptl.mvc</artifactId>
    <version>${org.egovframe.rte.version}</version>
    ...
</dependency>
<dependency>
    <groupId>org.egovframe.rte</groupId>
    <artifactId>org.egovframe.rte.psl.dataaccess</artifactId>
    <version>${org.egovframe.rte.version}</version>
</dependency>
```

화면처리(`ptl.mvc`)와 데이터처리(`psl.dataaccess`) 두 모듈을 4.1.0 버전으로 씁니다. 이 두 모듈이 Spring MVC, MyBatis, 로그 라이브러리를 정해진 버전으로 함께 가져옵니다. 무엇이 어떤 버전으로 들어오는지는 [다음 글](/posts/fashion-archive-egovframe-maven)에서 공식 저장소의 POM으로 확인합니다.

Fashion Archive의 Java 코드는 eGovFrame이 제공하는 클래스를 상속하거나 호출하지 않습니다. Controller, Service, DAO는 Spring과 MyBatis의 클래스(`@Controller`, `SqlSessionTemplate` 등)를 직접 씁니다. 표준프레임워크에서 받는 것은 **검증된 버전 조합의 실행환경 라이브러리**와 **패키지와 설정 파일의 관례**입니다([따른 관례](/posts/fashion-archive-egovframe-conventions)).

## 정리

- 표준프레임워크는 공공 시스템이 특정 업체 기술에 묶이지 않도록 오픈소스 위에 공통 구조를 정한 것입니다.
- 실행, 개발, 운영, 관리 4개 환경 중 코드에서 만나는 것은 실행환경입니다.
- 실행환경 모듈 이름의 `ptl`, `psl`, `fdl`은 화면처리, 데이터처리, 공통기반 계층입니다.
- Fashion Archive는 `ptl.mvc`와 `psl.dataaccess` 4.1.0을 쓰고, 코드는 Spring과 MyBatis를 직접 씁니다.

## 출처

- 행정안전부, [전자정부 표준프레임워크 소개](https://mois.go.kr/frt/sub/a06/b04/egovInfra/screen.do) (2026-10-08 확인)
