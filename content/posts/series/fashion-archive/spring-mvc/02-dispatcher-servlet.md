---
title: "dispatcher-servlet.xml과 context-datasource.xml 읽기"
slug: "fashion-archive-spring-mvc-dispatcher-servlet"
category: "development"
publishedAt: "2026-10-08"
summary: "정적 자원, 컴포넌트 스캔, Tiles와 JSP 뷰 리졸버의 순서, multipartResolver 빈 이름, 로그인 인터셉터 매핑, DB 연결 풀과 TransactionTemplate 빈까지 Spring 설정 두 파일을 한 줄씩 읽습니다."
series: "fashion-archive"
chapter: "spring-mvc"
seriesOrder: 2
---
## 이 글에서 할 일

[web.xml](/posts/fashion-archive-spring-mvc-web-xml)의 DispatcherServlet이 함께 읽는 설정 파일 두 개를 봅니다.

## dispatcher-servlet.xml

`src/main/webapp/WEB-INF/config/dispatcher-servlet.xml`

### 기본 설정

```xml
<mvc:resources mapping="/resources/**" location="/resources/" />
<mvc:annotation-driven />
<context:component-scan base-package="com.fashion" />
<tx:annotation-driven />
```

| 줄 | 뜻 |
|---|---|
| `mvc:resources` | `/resources/**` 요청은 컨트롤러를 찾지 않고 `webapp/resources/`의 파일을 그대로 보냄 (CSS, JS, 폰트, 아이콘) |
| `mvc:annotation-driven` | `@GetMapping`, `@PostMapping`, `@RequestBody`, `@ResponseBody`(Jackson JSON 변환)를 켬 |
| `context:component-scan` | `com.fashion` 아래의 `@Controller`, `@Service`, `@Repository`, `@Component`를 빈으로 만듦 |
| `tx:annotation-driven` | `@Transactional`을 읽게 함. 이 프로젝트의 여러 SQL 묶음은 아래의 `TransactionTemplate`으로 처리 |

### 뷰 리졸버: Tiles 먼저, JSP 다음

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
<bean id="viewResolver" class="org.springframework.web.servlet.view.InternalResourceViewResolver">
    <property name="prefix" value="/WEB-INF/jsp/" />
    <property name="suffix" value=".jsp" />
    <property name="order" value="2" />
</bean>
```

컨트롤러가 돌려준 **뷰 이름**을 실제 화면으로 바꾸는 리졸버가 둘이고, `order` 순서로 시도합니다.

| 뷰 이름 | 처리 |
|---|---|
| `/fashion/photo/list` | 1순위 Tiles 정의 `/fashion/*/*`에 맞음 → 레이아웃 + 본문 JSP |
| `login/loginMain` | Tiles 정의에 없음 → 2순위 JSP 리졸버 → `/WEB-INF/jsp/login/loginMain.jsp` 단독 |

로그인 화면은 메뉴가 있는 공통 레이아웃 없이 그려야 하므로, Tiles에 맞지 않는 이름을 써서 JSP 리졸버로 넘깁니다. Tiles 정의는 [Tiles 장](/posts/fashion-archive-tiles-tiles-setup)에서 봅니다.

### multipartResolver

```xml
<!-- web.xml의 multipart-config를 사용. 빈 이름은 반드시 multipartResolver -->
<bean id="multipartResolver" class="org.springframework.web.multipart.support.StandardServletMultipartResolver" />
```

DispatcherServlet은 `multipartResolver`라는 **이름**의 빈을 찾아 파일 업로드 요청을 처리합니다. 이름이 다르면 찾지 못해 `MultipartFile` 파라미터를 받을 수 없습니다. `StandardServletMultipartResolver`는 [web.xml의 multipart 설정](/posts/fashion-archive-spring-mvc-web-xml)대로 Tomcat이 해석한 결과를 씁니다.

### 인터셉터

```xml
<mvc:interceptors>
    <!-- 로그인·가입 화면과 정적 파일을 뺀 모든 요청은 로그인이 필요하다 -->
    <mvc:interceptor>
        <mvc:mapping path="/**" />
        <mvc:exclude-mapping path="/login" />
        <mvc:exclude-mapping path="/logout" />
        <mvc:exclude-mapping path="/signup" />
        <mvc:exclude-mapping path="/api/login" />
        <mvc:exclude-mapping path="/api/signup" />
        <mvc:exclude-mapping path="/resources/**" />
        <bean class="com.fashion.cmmn.interceptor.LoginInterceptor" />
    </mvc:interceptor>
</mvc:interceptors>
```

**전체(`/**`)에 걸고 필요한 것만 뺍니다.** 새 화면이나 API를 추가하면 자동으로 로그인 검사를 받습니다. 빼는 것은 로그인 전에 열려야 하는 화면과 API, 정적 파일입니다. 인터셉터 코드는 [인터셉터 글](/posts/fashion-archive-spring-mvc-interceptor)에서 봅니다.

## context-datasource.xml

`src/main/resources/spring/context-datasource.xml`

### 설정값

```xml
<!-- globals.properties 값 안의 ${FASHION_*}는 환경변수/시스템 프로퍼티로 다시 치환된다 -->
<context:property-placeholder location="classpath:/egovProps/globals.properties" />
```

컨텍스트가 하나라서 이 한 줄이 XML의 `${...}`와 Java의 `@Value`에 모두 적용됩니다.

### DB 연결 풀

```xml
<bean id="dataSource" class="org.apache.commons.dbcp2.BasicDataSource" destroy-method="close">
    <property name="driverClassName" value="org.postgresql.Driver" />
    <property name="url" value="${Globals.Fashion.Postgre.Url}" />
    <property name="username" value="${Globals.Fashion.Postgre.UserName}" />
    <property name="password" value="${Globals.Fashion.Postgre.Password}" />
    <property name="maxTotal" value="5" />
</bean>
```

commons-dbcp2의 `BasicDataSource`로 연결을 최대 5개까지 열어 재사용합니다. MyBatis 연결은 [DB 장](/posts/fashion-archive-database-datasource)에서 봅니다.

### 트랜잭션

```xml
<bean id="transactionManager" class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
    <property name="dataSource" ref="dataSource" />
</bean>
<!-- SQL이 2개 이상인 작업(사진 등록/수정)을 API 컨트롤러에서 명시적으로 묶는다 -->
<bean id="transactionTemplate" class="org.springframework.transaction.support.TransactionTemplate">
    <property name="transactionManager" ref="transactionManager" />
</bean>
```

`TransactionTemplate`을 빈으로 만들어 두고, 사진 등록과 수정처럼 SQL이 둘 이상인 작업을 API 컨트롤러에서 코드로 묶습니다.

```java
id = transactionTemplate.execute(status -> {
    photoService.insertPhoto(photo);
    long newId = ((Number) photo.get("id")).longValue();
    photoService.insertPhotoCategories(newId, ids);
    return newId;
});
```

람다 안의 두 INSERT 중 하나라도 실패하면 둘 다 취소됩니다. 묶는 범위가 코드에 그대로 보입니다([파일과 DB 맞추기](/posts/fashion-archive-upload-consistency)).

### BCrypt

```xml
<bean id="passwordEncoder" class="org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder" />
```

비밀번호 해시 빈입니다([BCrypt](/posts/fashion-archive-auth-bcrypt)).

## 정리

- 뷰 리졸버는 Tiles(1순위) → JSP(2순위) 순서이고, 로그인 화면은 Tiles에 맞지 않는 이름으로 레이아웃 없이 그립니다.
- 업로드는 `multipartResolver`라는 이름의 `StandardServletMultipartResolver` 빈이 맡습니다.
- 로그인 인터셉터는 전체에 걸고 로그인 전 화면, API, 정적 파일만 뺍니다.
- DB는 commons-dbcp2(최대 5개), SQL 묶음은 `TransactionTemplate` 빈으로 처리합니다.
