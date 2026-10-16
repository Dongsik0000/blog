---
title: "서블릿, Tomcat, web.xml"
slug: "ledger-spring-servlet-war"
category: "development"
publishedAt: "2026-10-08"
summary: "Spring MVC 아래에 깔린 서블릿과 Tomcat의 역할을 이해하고, 웹 앱의 시작 설정인 web.xml을 한 줄씩 읽습니다."
series: "ledger"
chapter: "spring"
seriesOrder: 1
---
## 이 글에서 할 일

Spring MVC는 혼자 동작하지 않습니다. **서블릿**이라는 Java 웹 표준 위에서, **Tomcat**이라는 서블릿 컨테이너가 실행해 줍니다. 이 글에서는 그 바닥부터 이해하고, Tomcat이 웹 앱을 시작할 때 가장 먼저 읽는 `web.xml`을 봅니다.

## 서블릿과 Tomcat

**서블릿**(Servlet)은 "HTTP 요청을 받아 응답을 만드는 Java 클래스"의 표준 규격입니다. **Tomcat**은 서블릿을 실행하는 프로그램(서블릿 컨테이너)입니다.

```text
브라우저 ──HTTP──▶ Tomcat ──▶ 서블릿 (Java 코드) ──▶ 응답
```

Tomcat이 하는 일은 다음과 같습니다.

- 포트(8081)에서 HTTP 요청을 받아 `HttpServletRequest` 객체로 만들어 줍니다.
- 주소를 보고 어느 웹 앱(`/p3`), 어느 서블릿이 처리할지 정합니다.
- 서블릿이 `HttpServletResponse`에 쓴 내용을 HTTP 응답으로 보냅니다.
- 세션, JSP 실행, 스레드 관리도 맡습니다.

Spring MVC를 쓰면 서블릿을 직접 만들지 않습니다. Spring이 제공하는 **DispatcherServlet** 하나가 모든 요청을 받고, 우리가 만든 Controller로 나눠 줍니다.

## 컨텍스트 경로 `/p3`

Tomcat 하나에는 웹 앱 여러 개를 올릴 수 있고, 주소의 첫 부분으로 구분합니다. 이것을 **컨텍스트 경로**라고 합니다.

```text
http://localhost:8081/p3/ledger/dashboard
                     └┬┘└──────┬───────┘
              컨텍스트 경로   앱 안의 경로 (Controller의 @RequestMapping과 비교)
```

Tomcat은 `webapps/p3.war`라는 파일 이름을 보고 컨텍스트 경로를 `/p3`로 정합니다. Controller에는 `/p3`를 빼고 `/ledger/dashboard`만 적습니다. JSP와 JS에서 주소를 만들 때는 `contextPath`(= `/p3`)를 앞에 붙입니다. 그래서 경로를 바꿔 배포해도 코드는 고칠 필요가 없습니다.

## web.xml: 웹 앱의 시작 설정

`src/main/webapp/WEB-INF/web.xml`은 Tomcat이 웹 앱을 시작할 때 읽는 설정 파일입니다. 위에서부터 읽습니다.

### 1. 인코딩 필터

```xml
<!-- 한글 깨짐 방지 -->
<filter>
    <filter-name>encodingFilter</filter-name>
    <filter-class>org.springframework.web.filter.CharacterEncodingFilter</filter-class>
    <init-param>
        <param-name>encoding</param-name>
        <param-value>UTF-8</param-value>
    </init-param>
    <init-param>
        <param-name>forceEncoding</param-name>
        <param-value>true</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>encodingFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

**필터**는 서블릿보다 먼저 모든 요청을 거치는 관문입니다. 이 필터는 요청과 응답의 글자 인코딩을 UTF-8로 맞춰 한글이 깨지지 않게 합니다. `/*`는 모든 주소에 적용한다는 뜻입니다.

### 2. 루트 컨텍스트

```xml
<!-- 루트 컨텍스트: 데이터소스, MyBatis, 트랜잭션, 스케줄러 -->
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>classpath:spring/context-*.xml</param-value>
</context-param>
<listener>
    <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
```

`ContextLoaderListener`는 웹 앱이 시작될 때 Spring 설정 파일(`spring/context-*.xml`)을 읽어 **루트 컨텍스트**를 만듭니다. DB 연결처럼 앱 전체가 함께 쓰는 것이 여기 들어갑니다. `context-*.xml`이라서 나중에 `context-xxx.xml`을 추가해도 자동으로 읽힙니다.

### 3. DispatcherServlet

```xml
<!-- 서블릿 컨텍스트: MVC, 뷰, 인터셉터, 컴포넌트 스캔 -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
    <init-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>/WEB-INF/config/dispatcher-servlet.xml</param-value>
    </init-param>
    <load-on-startup>1</load-on-startup>
</servlet>
<servlet-mapping>
    <servlet-name>dispatcher</servlet-name>
    <url-pattern>/</url-pattern>
</servlet-mapping>
```

| 부분 | 뜻 |
|---|---|
| `DispatcherServlet` | 모든 요청을 받아 Controller로 나눠 주는 Spring의 서블릿 |
| `contextConfigLocation` | 이 서블릿이 읽을 Spring 설정 (**서블릿 컨텍스트**) |
| `load-on-startup 1` | 첫 요청을 기다리지 않고 앱 시작과 함께 준비. 첫 사용자가 느려지지 않음 |
| `url-pattern /` | 다른 서블릿에 맞지 않는 모든 요청을 이 서블릿이 받음 |

루트 컨텍스트와 서블릿 컨텍스트가 왜 둘인지는 [다음 글](/posts/ledger-spring-two-contexts)에서 다룹니다.

### 4. 첫 화면

```xml
<welcome-file-list>
    <welcome-file>index.jsp</welcome-file>
</welcome-file-list>
```

`/p3/`처럼 폴더 주소로 들어오면 `index.jsp`를 보여 줍니다. `index.jsp`는 한 줄로 대시보드로 넘깁니다.

```jsp
<jsp:forward page="/ledger/dashboard"/>
```

### 5. 오류 화면

```xml
<error-page>
    <error-code>404</error-code>
    <location>/WEB-INF/jsp/ledger/error.jsp</location>
</error-page>
<error-page>
    <error-code>500</error-code>
    <location>/WEB-INF/jsp/ledger/error.jsp</location>
</error-page>
<!-- 그 밖의 오류(400, 403, 405 …)도 Tomcat 기본 화면(서버 버전 노출) 대신 같은 화면 -->
<error-page>
    <location>/WEB-INF/jsp/ledger/error.jsp</location>
</error-page>
```

Tomcat의 기본 오류 화면에는 Tomcat 버전이 찍혀 공격자에게 정보를 줍니다. 모든 오류를 앱의 화면으로 바꿉니다. 오류 처리 전체 흐름은 [공통 예외 처리](/posts/ledger-spring-exception-handling)에서 다룹니다.

### 6. 세션 설정

```xml
<session-config>
    <session-timeout>1440</session-timeout>
    <!-- Session ID URL 노출 차단 -->
    <tracking-mode>COOKIE</tracking-mode>
    <cookie-config>
        <http-only>true</http-only>
    </cookie-config>
</session-config>
```

| 설정 | 뜻 |
|---|---|
| `session-timeout 1440` | 1440분(24시간) 동안 요청이 없으면 세션 만료 |
| `tracking-mode COOKIE` | 세션 ID를 쿠키로만 주고받음. 주소에 `;jsessionid=...`가 붙어 노출되는 것을 막음 |
| `http-only` | JavaScript가 세션 쿠키를 읽지 못하게 함. XSS가 있어도 세션 탈취를 어렵게 함 |

세션 보안은 [세션 관리](/posts/ledger-auth-session)에서 더 다룹니다.

## 정리

- 서블릿은 Java 웹 표준, Tomcat은 서블릿을 실행하는 컨테이너입니다.
- Spring MVC는 `DispatcherServlet` 하나로 모든 요청을 받아 Controller로 나눠 줍니다.
- `web.xml`은 인코딩 필터, 루트 컨텍스트, DispatcherServlet, 오류 화면, 세션을 설정합니다.
- 컨텍스트 경로(`/p3`)는 war 파일 이름으로 정해지고, 코드에는 `contextPath`로 붙입니다.
