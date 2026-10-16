---
title: "JSP와 JSTL"
slug: "ledger-view-jsp-jstl"
category: "development"
publishedAt: "2026-10-08"
summary: "서버에서 HTML을 만드는 JSP와 태그 라이브러리 JSTL의 기본, c:url로 컨텍스트 경로 붙이기, c:out으로 안전하게 출력하기, Ledger가 JSP에는 틀만 두고 데이터는 Ajax로 그리는 구조를 설명합니다."
series: "ledger"
chapter: "view"
seriesOrder: 1
---
## 이 글에서 할 일

**JSP**(JavaServer Pages)는 HTML 안에 Java 웹 기능을 섞어 쓰는 파일입니다. Tomcat이 JSP를 실행해서 최종 HTML을 만들고 브라우저에 보냅니다. Ledger의 화면 10개가 모두 JSP입니다.

## 맨 위의 지시어

`WEB-INF/jsp/ledger/settings/settingsMain.jsp`

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="/common/taglib.jsp"%>
```

| 줄 | 뜻 |
|---|---|
| `<%@ page ... %>` | 이 JSP의 설정. 응답은 UTF-8 HTML, 파일도 UTF-8로 읽음 |
| `<%@ include file="..." %>` | 다른 파일 내용을 이 자리에 그대로 붙임 |

`common/taglib.jsp`는 모든 화면이 쓰는 태그 라이브러리 선언을 모아 둔 파일입니다.

```jsp
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<%@ taglib prefix="fn" uri="jakarta.tags.functions"%>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>
```

`uri`가 `jakarta.tags.core`인 것은 JSTL 3.0(jakarta) 방식입니다. 예전 자료의 `http://java.sun.com/jsp/jstl/core`는 Tomcat 10.1에서 쓰지 않습니다. `t`는 직접 만든 태그 파일 폴더입니다([다음 글](/posts/ledger-view-layout-tag)).

## JSTL: 반복과 조건을 태그로

JSP 안에 `<% for(...) { %>` 같은 Java 코드를 쓸 수도 있지만 읽기 어렵습니다. **JSTL**은 그런 일을 태그로 합니다.

```jsp
<select id="payDay" name="payDay">
    <c:forEach var="d" begin="1" end="31"><option value="${d}">${d}일</option></c:forEach>
</select>
```

`c:forEach`로 1일부터 31일까지 `<option>`을 만듭니다. `${d}`는 **EL**(Expression Language)로, 변수 값을 꺼내 출력합니다.

| 태그 | 하는 일 |
|---|---|
| `c:forEach` | 반복 |
| `c:if`, `c:choose` | 조건 |
| `c:set` | 변수 정하기 |
| `c:url` | 컨텍스트 경로를 붙인 주소 |
| `c:out` | 글자를 HTML에 안전하게 출력 |

## c:url: 경로를 하드코딩하지 않기

```jsp
<link rel="stylesheet" href="<c:url value='/resources/css/style.css'/>">
<a href="<c:url value='/ledger/dashboard'/>">대시보드</a>
```

`c:url`은 앞에 컨텍스트 경로를 붙여 `/p3/resources/css/style.css`를 만듭니다. `/p3`를 직접 쓰지 않으므로, 다른 경로로 배포해도 고칠 것이 없습니다. JS에는 레이아웃이 `contextPath` 변수로 같은 값을 넘깁니다.

```jsp
<script>var contextPath = "${pageContext.request.contextPath}";</script>
```

## c:out: XSS 막기

```jsp
<title><c:if test="${not empty title}"><c:out value="${title}"/> · </c:if>나의 가계부</title>
```

`c:out`은 `<`, `>`, `&`, 따옴표를 `&lt;` 같은 글자로 바꿔 출력합니다. 값에 `<script>`가 들어 있어도 실행되지 않고 글자로 보입니다. 사용자가 넣은 값을 `${값}`으로 바로 출력하면 그 값이 HTML로 해석되어 **XSS**(교차 사이트 스크립팅)가 될 수 있습니다.

## Ledger의 구조: JSP는 틀, 데이터는 Ajax

Ledger의 JSP에는 사용자 데이터가 거의 없습니다. 설정 화면 주석이 그 구조를 설명합니다.

```jsp
<%-- 설정: 목록·값은 settings.js 가 /ledger/settings/load 로 채운다. 공휴일·내보내기 동작은 holiday.js·settings.js --%>
```

```text
1. GET /ledger/settings     → JSP: 빈 목록과 입력 칸이 있는 HTML 틀
2. 화면이 뜨면 settings.js   → POST /ledger/settings/load
3. 받은 JSON으로              → 목록과 값을 채움
```

| | JSP가 데이터까지 그리기 | 틀 + Ajax (Ledger) |
|---|---|---|
| 저장 후 화면 갱신 | 페이지 전체 새로고침 | 바뀐 부분만 다시 그림 |
| 사용자 값 출력 위치 | JSP (`c:out`) | JS (`textContent`) |
| Controller | 화면마다 모델 준비 | 화면 메서드는 JSP 이름만 반환 |

그래서 Ledger의 XSS 방어는 주로 **JS가 화면을 그릴 때** 이루어집니다. JS가 사용자 값을 넣을 때는 `textContent`를 쓰고, HTML 문자열이 꼭 필요하면 `App.escape`를 거칩니다([화면별 JS 구조](/posts/ledger-view-page-js), [CSRF와 XSS](/posts/ledger-auth-csrf-xss)).

## 정리

- JSP는 Tomcat이 실행해 HTML을 만드는 파일이고, JSTL은 반복과 조건을 태그로 씁니다.
- `c:url`로 컨텍스트 경로를 붙이고, `c:out`으로 값을 안전하게 출력합니다.
- Tomcat 10.1의 JSTL 3.0은 `jakarta.tags.*` URI를 씁니다.
- Ledger의 JSP는 화면 틀만 그리고, 데이터는 화면별 JS가 Ajax로 받아 채웁니다.
