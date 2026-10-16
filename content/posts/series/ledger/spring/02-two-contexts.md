---
title: "루트 컨텍스트와 서블릿 컨텍스트"
slug: "ledger-spring-two-contexts"
category: "development"
publishedAt: "2026-10-08"
summary: "Spring 설정이 context-datasource.xml과 dispatcher-servlet.xml 두 파일로 나뉜 이유, 두 컨텍스트의 부모와 자식 관계, 같은 설정을 양쪽에 두 번 적어야 하는 경우를 설명합니다."
series: "ledger"
chapter: "spring"
seriesOrder: 2
---
## 이 글에서 할 일

Ledger의 Spring 설정은 두 파일로 나뉩니다. 처음에는 "왜 하나로 안 하지?" 싶지만, 이 구조를 모르면 실제로 설정이 동작하지 않는 문제를 겪습니다. Ledger도 개발 중에 그런 문제를 겪었습니다.

## 컨텍스트란

**컨텍스트**(ApplicationContext)는 Spring이 만든 객체(빈)들을 담아 두는 **상자**입니다. 설정 파일 하나를 읽으면 상자 하나가 만들어집니다.

| 컨텍스트 | 만드는 곳 | 설정 파일 | 담는 것 |
|---|---|---|---|
| 루트 컨텍스트 | `ContextLoaderListener` | `classpath:spring/context-*.xml` | 데이터소스, MyBatis, 트랜잭션 매니저, 비밀번호 해시 |
| 서블릿 컨텍스트 | `DispatcherServlet` | `/WEB-INF/config/dispatcher-servlet.xml` | Controller, Service, DAO(컴포넌트 스캔), 뷰, 인터셉터, 스케줄러 |

## 부모와 자식

두 상자는 **부모(루트)와 자식(서블릿)** 관계입니다.

```text
┌─────────────── 루트 컨텍스트 (부모) ───────────────┐
│ dataSource-postgresql, sqlSession-ledger-postgre,   │
│ transactionManager, passwordEncoder                 │
└──────────────────────▲──────────────────────────────┘
                       │ 자식은 부모의 빈을 볼 수 있음
┌──────────── 서블릿 컨텍스트 (자식) ────────────────┐
│ XxxApiController, XxxServiceImpl, XxxDAO,           │
│ viewResolver, jsonView, 인터셉터, scheduler          │
└─────────────────────────────────────────────────────┘
```

- **자식은 부모의 빈을 쓸 수 있습니다.** 그래서 서블릿 컨텍스트의 `SettingsDAO`가 루트의 `sqlSession-ledger-postgre`를 주입받습니다.
- **부모는 자식의 빈을 모릅니다.**
- **설정(후처리기)은 자기 상자에만 적용됩니다.** 이것이 아래 두 사례의 원인입니다.

## 사례 1. property-placeholder를 두 번 적는 이유

`${...}` 자리에 `globals.properties`의 값을 넣어 주는 설정이 양쪽 파일에 모두 있습니다.

`context-datasource.xml`

```xml
<context:property-placeholder location="classpath:/egovProps/globals.properties"/>
```

`dispatcher-servlet.xml`

```xml
<!-- 플레이스홀더 설정은 컨텍스트마다 따로 필요하다. 루트(context-datasource.xml)의 것은 이 컨텍스트의 @Value 에 적용되지 않는다 -->
<context:property-placeholder location="classpath:/egovProps/globals.properties"/>
```

루트의 설정은 루트 상자 안의 `${Globals.Ledger.Postgre.Url}`만 채웁니다. Controller의 `@Value("${Globals.Ledger.SignupCode}")`는 서블릿 상자에 있으므로, 서블릿 컨텍스트에도 같은 설정이 있어야 값이 들어갑니다.

## 사례 2. @Transactional이 동작하지 않던 문제

처음에는 트랜잭션 설정(`tx:annotation-driven`)이 루트 컨텍스트에만 있었습니다. 그런데 `@Transactional`을 붙인 클래스는 컴포넌트 스캔으로 **서블릿 컨텍스트**에 만들어집니다. 루트의 트랜잭션 설정은 서블릿 상자의 빈을 보지 못하므로, **`@Transactional`을 붙여도 아무 일도 일어나지 않았습니다.** 오류도 나지 않아 알아채기 어려운 종류의 문제입니다.

해결은 서블릿 컨텍스트에도 같은 설정을 넣는 것이었습니다.

`dispatcher-servlet.xml`

```xml
<!-- @Transactional 활성화. tx:annotation-driven 은 자기 컨텍스트의 빈에만 적용되는데 컴포넌트 스캔은 이 컨텍스트에 있다.
     로직을 가진 ApiController 메서드에 @Transactional 을 붙인다. transactionManager 는 루트 컨텍스트(context-datasource.xml) 빈 -->
<tx:annotation-driven transaction-manager="transactionManager"/>
```

`transactionManager` 빈 자체는 루트에 있지만, 자식이 부모의 빈을 쓸 수 있으므로 이름으로 가리키면 됩니다. 이 수정은 커밋 "공통: 서블릿 컨텍스트 트랜잭션"에 기록되어 있습니다. 트랜잭션 자체는 [트랜잭션 글](/posts/ledger-spring-transaction)에서 다룹니다.

스케줄러(`task:annotation-driven`)도 같은 이유로 서블릿 컨텍스트에 있습니다. `@Scheduled` 메서드를 가진 빈이 그 컨텍스트에서 스캔되기 때문입니다.

## 왜 둘로 나누나

Spring MVC의 전통적인 구조입니다. 웹과 상관없는 것(DB, 트랜잭션 매니저)은 루트에, 웹에 관련된 것(Controller, 뷰, 인터셉터)은 서블릿에 둡니다. DispatcherServlet을 여러 개 두더라도 루트의 DB 설정을 함께 쓸 수 있게 하려는 구조입니다.

Ledger처럼 컴포넌트 스캔을 서블릿 컨텍스트에 한 번만 두면, Controller와 Service와 DAO가 모두 같은 상자에 있어 단순합니다. 대신 위의 두 사례처럼 **스캔된 빈에 적용할 설정은 서블릿 컨텍스트에 있어야** 한다는 점만 기억하면 됩니다.

## 정리

- 루트 컨텍스트는 DB와 공용 빈, 서블릿 컨텍스트는 웹 관련 빈과 컴포넌트 스캔 결과를 담습니다.
- 자식(서블릿)은 부모(루트)의 빈을 쓸 수 있지만, 설정은 자기 컨텍스트의 빈에만 적용됩니다.
- 그래서 `property-placeholder`와 `tx:annotation-driven`을 서블릿 컨텍스트에도 적었습니다.
