---
title: "Tiles 설정과 와일드카드 정의"
slug: "fashion-archive-tiles-tiles-setup"
category: "development"
publishedAt: "2026-10-08"
summary: "Apache Tiles가 공통 레이아웃에 본문 JSP를 끼워 화면을 조립하는 원리, TilesConfigurer와 TilesView 설정, /fashion/*/* 와일드카드 정의 하나로 모든 화면을 처리하는 방법을 설명합니다."
series: "fashion-archive"
chapter: "tiles"
seriesOrder: 1
---
## 이 글에서 할 일

사진 목록, 상세, 등록 화면은 모두 같은 헤더(사이트 제목, 카테고리 메뉴, 로그인 링크)를 가집니다. 화면마다 헤더를 복사하면 메뉴 하나를 고칠 때 모든 JSP를 고쳐야 합니다. **Apache Tiles**는 공통 틀(레이아웃)과 바뀌는 부분(본문)을 나눠 두고, 요청할 때 둘을 조립합니다.

## Tiles의 원리

```text
레이아웃 (fashionLayout.jsp)
┌──────────────────────────────┐
│ <head> CSS, 공통 JS           │
│ 헤더: 제목, 카테고리, 로그인   │
├──────────────────────────────┤
│  <tiles:insertAttribute       │ ← 이 자리에
│       name="body" />          │    본문 JSP가 들어감
└──────────────────────────────┘
          +
본문 (/WEB-INF/jsp/fashion/photo/list.jsp)
          =
완성된 화면
```

레이아웃 JSP는 "본문이 들어갈 자리"를 `body`라는 **속성(attribute)** 이름으로 표시합니다. **정의**(definition)는 어떤 레이아웃에 어떤 본문을 넣을지 적은 설정입니다.

## 1. 의존성

`pom.xml`

```xml
<dependency>
    <groupId>org.apache.tiles</groupId>
    <artifactId>tiles-core</artifactId>
    <version>3.0.5</version>
</dependency>
<dependency>
    <groupId>org.apache.tiles</groupId>
    <artifactId>tiles-servlet</artifactId>
    <version>3.0.5</version>
</dependency>
<dependency>
    <groupId>org.apache.tiles</groupId>
    <artifactId>tiles-jsp</artifactId>
    <version>3.0.5</version>
</dependency>
```

| 모듈 | 역할 |
|---|---|
| `tiles-core` | 정의를 읽고 조립 |
| `tiles-servlet` | 서블릿 환경에서 실행 |
| `tiles-jsp` | JSP 태그 (`<tiles:insertAttribute>`) |

Spring 5.3의 `spring-webmvc`에는 Tiles 3를 연결하는 클래스(`org.springframework.web.servlet.view.tiles3`)가 들어 있습니다.

## 2. Spring 연결

`WEB-INF/config/dispatcher-servlet.xml`

```xml
<!-- 1순위: Tiles. 정의에 없는 뷰 이름은 2순위 JSP 리졸버로 넘어간다 -->
<bean id="tilesViewResolver" class="org.springframework.web.servlet.view.UrlBasedViewResolver">
    <property name="viewClass" value="org.springframework.web.servlet.view.tiles3.TilesView" />
    <property name="order" value="1" />
</bean>
<bean id="tilesConfigurer" class="org.springframework.web.servlet.view.tiles3.TilesConfigurer">
    <property name="definitions">
        <list>
            <value>/WEB-INF/tiles/tiles-layout.xml</value>
        </list>
    </property>
</bean>
```

| 빈 | 역할 |
|---|---|
| `TilesConfigurer` | 시작할 때 정의 파일(`tiles-layout.xml`)을 읽어 Tiles를 준비 |
| `UrlBasedViewResolver` + `TilesView` | 컨트롤러가 돌려준 뷰 이름을 **Tiles 정의 이름**으로 보고 화면을 조립 |
| `order 1` | JSP 리졸버(2순위)보다 먼저 시도 |

## 3. 정의 파일

`WEB-INF/tiles/tiles-layout.xml`

```xml
<tiles-definitions>
    <definition name="fashionLayout" template="/WEB-INF/layout/fashionLayout.jsp">
        <put-attribute name="body" value="" />
    </definition>
    <definition name="/fashion/*/*" extends="fashionLayout">
        <put-attribute name="body" value="/WEB-INF/jsp/fashion/{1}/{2}.jsp" />
    </definition>
</tiles-definitions>
```

### 기본 정의

```xml
<definition name="fashionLayout" template="/WEB-INF/layout/fashionLayout.jsp">
    <put-attribute name="body" value="" />
</definition>
```

레이아웃 JSP(`template`)와, 비어 있는 `body` 자리를 정합니다.

### 와일드카드 정의

```xml
<definition name="/fashion/*/*" extends="fashionLayout">
    <put-attribute name="body" value="/WEB-INF/jsp/fashion/{1}/{2}.jsp" />
</definition>
```

| 부분 | 뜻 |
|---|---|
| `name="/fashion/*/*"` | `/fashion/무엇/무엇` 모양의 뷰 이름에 모두 맞음 |
| `extends="fashionLayout"` | 기본 정의(레이아웃)를 물려받음 |
| `{1}`, `{2}` | 이름의 첫째, 둘째 `*` 자리에 들어온 글자 |

컨트롤러가 돌려주는 뷰 이름과 실제 본문 JSP가 이렇게 연결됩니다.

| 뷰 이름 | `{1}` | `{2}` | 본문 JSP |
|---|---|---|---|
| `/fashion/photo/list` | photo | list | `/WEB-INF/jsp/fashion/photo/list.jsp` |
| `/fashion/photo/detail` | photo | detail | `/WEB-INF/jsp/fashion/photo/detail.jsp` |
| `/fashion/admin/photoForm` | admin | photoForm | `/WEB-INF/jsp/fashion/admin/photoForm.jsp` |

**정의 하나로 모든 화면을 처리합니다.** 새 화면은 `WEB-INF/jsp/fashion/폴더/이름.jsp`를 만들고 컨트롤러에서 `/fashion/폴더/이름`을 돌려주기만 하면 됩니다. 정의 파일은 고칠 필요가 없습니다.

## 뷰 이름이 정의에 맞지 않으면

`login/loginMain`은 `/fashion/*/*`에 맞지 않습니다. Tiles는 이 이름을 처리하지 않고, 2순위 JSP 리졸버가 `/WEB-INF/jsp/login/loginMain.jsp`를 레이아웃 없이 그립니다([레이아웃 JSP](/posts/fashion-archive-tiles-layout)).

## 정리

- Tiles는 레이아웃 JSP의 `body` 자리에 본문 JSP를 끼워 화면을 조립합니다.
- Spring에는 `TilesConfigurer`(정의 읽기)와 `TilesView`를 쓰는 뷰 리졸버(1순위)로 연결합니다.
- `/fashion/*/*` 와일드카드 정의 하나가 `{1}/{2}` 자리로 본문 JSP를 찾아, 모든 화면을 같은 레이아웃으로 그립니다.
