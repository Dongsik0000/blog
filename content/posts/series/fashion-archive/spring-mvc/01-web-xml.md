---
title: "web.xml 읽기 (DispatcherServlet 하나, multipart, 리스너)"
slug: "fashion-archive-spring-mvc-web-xml"
category: "development"
publishedAt: "2026-10-08"
summary: "Fashion Archive의 web.xml을 위에서부터 읽으며 인코딩 필터, 정적 파일 버전을 만드는 리스너, 루트 컨텍스트 없이 설정 두 개를 함께 읽는 DispatcherServlet, Servlet 3.1 multipart 설정, 오류 화면과 세션 설정을 설명합니다."
series: "fashion-archive"
chapter: "spring-mvc"
seriesOrder: 1
---
## 이 글에서 할 일

`web.xml`은 Tomcat이 웹 앱을 시작할 때 가장 먼저 읽는 설정입니다. Fashion Archive의 `web.xml`은 짧지만, 업로드와 Spring 설정에서 중요한 결정이 들어 있습니다.

`src/main/webapp/WEB-INF/web.xml`

## 1. Servlet 3.1

```xml
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"
         version="3.1">
```

`javax` 쪽 Servlet 3.1 스키마입니다([javax와 Tomcat 9](/posts/fashion-archive-egovframe-javax-tomcat9)). 아래의 `<multipart-config>`는 Servlet 3.0부터 쓸 수 있는 표준 기능입니다.

## 2. 인코딩 필터

```xml
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

모든 요청과 응답의 글자 인코딩을 UTF-8로 맞춥니다. 사진 제목과 메모의 한글이 깨지지 않게 하는 설정입니다.

## 3. 리스너: 정적 파일 버전

```xml
<listener>
    <listener-class>com.fashion.cmmn.listener.AssetVersionListener</listener-class>
</listener>
```

**리스너**는 웹 앱이 시작하거나 끝날 때 실행되는 코드입니다. 이 리스너는 시작할 때 시각을 기록해 두고, JSP가 CSS와 JS 주소 뒤에 `?v=시각`을 붙이는 데 씁니다. 배포할 때마다 주소가 바뀌어 브라우저가 예전 파일을 쓰지 않습니다([정적 파일 버전](/posts/fashion-archive-view-asset-version)).

## 4. DispatcherServlet 하나

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
    <load-on-startup>1</load-on-startup>
    ...
</servlet>
<servlet-mapping>
    <servlet-name>dispatcher</servlet-name>
    <url-pattern>/</url-pattern>
</servlet-mapping>
```

`contextConfigLocation`에 설정 파일 **두 개**를 줄을 바꿔 적었습니다. DispatcherServlet이 두 파일을 함께 읽어 **Spring 컨텍스트 하나**를 만듭니다.

| 파일 | 담은 것 |
|---|---|
| `WEB-INF/config/dispatcher-servlet.xml` | 컴포넌트 스캔, 뷰 리졸버(Tiles, JSP), 인터셉터, multipartResolver |
| `classpath:spring/context-datasource.xml` | DB 연결 풀, MyBatis, 트랜잭션, BCrypt 빈 |

`ContextLoaderListener`로 루트 컨텍스트를 따로 만들지 않으므로, 모든 빈이 한 컨텍스트에 있습니다. 설정이 어느 컨텍스트에 적용되는지 따로 따질 필요가 없는 단순한 구성입니다.

| 그 밖의 줄 | 뜻 |
|---|---|
| `load-on-startup 1` | 첫 요청을 기다리지 않고 앱 시작과 함께 Spring을 준비 |
| `url-pattern /` | 모든 요청을 DispatcherServlet이 받음 |

## 5. multipart 설정

```xml
    <!-- Servlet 3.1 표준 multipart. 파일 10MB, 요청 전체 12MB -->
    <multipart-config>
        <max-file-size>10485760</max-file-size>
        <max-request-size>12582912</max-request-size>
    </multipart-config>
</servlet>
```

사진 업로드(`multipart/form-data`)를 **서블릿 컨테이너(Tomcat)가 직접** 해석하게 하는 설정입니다. DispatcherServlet의 `<servlet>` 안에 둡니다.

| 값 | 바이트 | 뜻 |
|---|---|---|
| `max-file-size` | 10,485,760 | 파일 하나 10MB |
| `max-request-size` | 12,582,912 | 요청 전체 12MB (파일 + 제목, 메모 등) |

nginx의 `client_max_body_size 12m`과 같은 크기입니다. Spring 쪽 연결은 [multipart 글](/posts/fashion-archive-upload-multipart)에서 봅니다.

## 6. 오류 화면

```xml
<error-page>
    <error-code>404</error-code>
    <location>/WEB-INF/jsp/error.jsp</location>
</error-page>
<error-page>
    <error-code>500</error-code>
    <location>/WEB-INF/jsp/error.jsp</location>
</error-page>
```

없는 주소(404)와 서버 오류(500)에 Tomcat 기본 화면 대신 앱의 오류 화면을 보여 줍니다.

## 7. 세션

```xml
<session-config>
    <session-timeout>60</session-timeout>
    <cookie-config>
        <http-only>true</http-only>
    </cookie-config>
    <tracking-mode>COOKIE</tracking-mode>
</session-config>
```

| 설정 | 뜻 |
|---|---|
| `session-timeout 60` | 60분 동안 요청이 없으면 로그인이 풀림 |
| `http-only` | JavaScript가 세션 쿠키를 읽지 못함 |
| `tracking-mode COOKIE` | 세션 ID를 쿠키로만 주고받음. 주소에 `;jsessionid=`가 붙지 않음 |

세션 쿠키의 `SameSite` 설정은 `META-INF/context.xml`에 있습니다([CSRF와 SameSite](/posts/fashion-archive-auth-csrf-url)).

## 정리

- 인코딩 필터로 한글을 지키고, 리스너가 정적 파일 버전을 만듭니다.
- DispatcherServlet 하나가 설정 파일 두 개를 함께 읽어 컨텍스트 하나를 만듭니다.
- 업로드는 Servlet 3.1 `<multipart-config>`로 파일 10MB, 요청 12MB까지 받습니다.
- 오류 화면과 세션(60분, HttpOnly, 쿠키 전용)을 설정합니다.
