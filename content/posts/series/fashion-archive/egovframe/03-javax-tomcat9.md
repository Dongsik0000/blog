---
title: "Spring 5.3, javax, Tomcat 9 조합"
slug: "fashion-archive-egovframe-javax-tomcat9"
category: "development"
publishedAt: "2026-10-08"
summary: "eGovFrame 4.1의 Spring 5.3.20이 javax.servlet을 쓰는 이유, Java 17로 컴파일하고 Tomcat 9에서 실행하는 구성, web.xml과 JSTL에서 javax 시대의 이름을 쓰는 곳을 정리합니다."
series: "fashion-archive"
chapter: "egovframe"
seriesOrder: 3
---
## 이 글에서 할 일

Fashion Archive는 Tomcat 9에서 실행되고, import 문은 `javax.servlet`으로 시작합니다. 같은 서버의 Ledger는 Tomcat 10.1과 `jakarta.servlet`입니다. 이 글에서는 왜 그런지, 어느 파일에서 그 차이가 드러나는지 정리합니다.

## javax에서 jakarta로

Java 웹 표준(서블릿, JSP 등)은 원래 `javax.*` 패키지 이름을 썼습니다. 표준의 관리 주체가 Eclipse 재단으로 옮겨지면서 Jakarta EE 9부터 패키지 이름이 `jakarta.*`로 바뀌었습니다. 이름만 바뀌었지만 Java에서는 패키지가 다르면 다른 클래스라, 두 쪽은 서로 섞어 쓸 수 없습니다.

| | javax 쪽 | jakarta 쪽 |
|---|---|---|
| 서블릿 API | `javax.servlet.*` | `jakarta.servlet.*` |
| Tomcat | 9 이하 | 10 이상 |
| Spring Framework | 5.x | 6.x |
| 이 서버의 앱 | **Fashion Archive** | Ledger |

eGovFrame 4.1의 실행환경은 Spring 5.3.20을 쓰므로([Maven 의존성](/posts/fashion-archive-egovframe-maven)) `javax` 쪽입니다. 그래서 실행 환경도 Tomcat 9입니다.

## 파일에서 보이는 javax

### Java 코드

`com/fashion/cmmn/interceptor/LoginInterceptor.java`

```java
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
```

DAO의 `@Resource`도 `javax.annotation.Resource`입니다.

### pom.xml

```xml
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>javax.servlet-api</artifactId>
    <version>3.1.0</version>
    <scope>provided</scope>
</dependency>
```

### web.xml

```xml
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"
         version="3.1">
```

Servlet 3.1 스키마입니다. 이 버전부터 쓸 수 있는 `<multipart-config>`로 파일 업로드를 설정합니다([multipart](/posts/fashion-archive-upload-multipart)).

### JSTL

```xml
<dependency>
    <groupId>javax.servlet.jsp.jstl</groupId>
    <artifactId>jstl-api</artifactId>
    <version>1.2</version>
</dependency>
```

JSP의 `<c:forEach>`, `<c:out>`은 JSTL 1.2입니다. Ledger(JSTL 3.0)의 `uri="jakarta.tags.core"`와 달리 javax 시대의 URI를 씁니다.

## Java 17로 컴파일

```xml
<maven.compiler.release>17</maven.compiler.release>
```

```dockerfile
FROM tomcat:9-jdk17
```

eGovFrame 4.1 실행환경 모듈은 Java 8을 대상으로 컴파일되어 있지만(공식 POM의 컴파일 설정), Java 8용으로 만든 라이브러리는 더 높은 버전의 JVM에서 실행됩니다. Fashion Archive는 자기 코드를 Java 17로 컴파일하고, JDK 17이 들어 있는 Tomcat 9 이미지에서 실행합니다. 그래서 Java 8에 없던 표준 라이브러리 기능도 씁니다.

```java
// LoginInterceptor: Charset을 받는 URLEncoder.encode는 Java 10부터
URLEncoder.encode(next, StandardCharsets.UTF_8)
```

```java
// ValidationTest: String.repeat는 Java 11부터
Validation.requireText("a".repeat(101), "제목", 100)
```

## Docker 이미지

```dockerfile
FROM tomcat:9-jdk17
```

공식 Tomcat 이미지의 태그는 "Tomcat 버전-JDK 버전" 형식입니다. Tomcat 9와 JDK 17을 함께 담은 이미지를 고릅니다. 이미지 구성은 [Dockerfile 글](/posts/fashion-archive-deploy-dockerfile)에서 다룹니다.

## 정리

- 서블릿 표준의 패키지 이름이 `javax`에서 `jakarta`로 바뀌었고, 두 쪽은 섞어 쓸 수 없습니다.
- eGovFrame 4.1은 Spring 5.3이라 `javax` 쪽이고, 그래서 Tomcat 9에서 실행합니다.
- Java 코드의 import, `servlet-api`, `web.xml` 스키마, JSTL 1.2에서 `javax` 쪽임이 드러납니다.
- 코드는 Java 17로 컴파일하고 JDK 17이 든 Tomcat 9 이미지에서 실행합니다.
