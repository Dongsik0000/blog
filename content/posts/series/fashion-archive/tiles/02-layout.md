---
title: "레이아웃 JSP와 레이아웃 없이 그리는 화면"
slug: "fashion-archive-tiles-layout"
category: "development"
publishedAt: "2026-10-08"
summary: "공통 레이아웃 fashionLayout.jsp가 맡는 일(공통 CSS와 JS, 화면 JS용 전역 값, 카테고리 메뉴, 계절 색), 본문 JSP가 자기 스크립트를 싣는 방법, 로그인과 오류 화면이 레이아웃 없이 그려지는 구조를 설명합니다."
series: "fashion-archive"
chapter: "tiles"
seriesOrder: 2
---
## 이 글에서 할 일

[Tiles 설정](/posts/fashion-archive-tiles-tiles-setup)에서 본 레이아웃 JSP의 안을 봅니다. 그리고 레이아웃을 쓰지 않는 화면(로그인, 가입, 오류)이 어떻게 그려지는지 정리합니다.

## fashionLayout.jsp가 맡는 일

`WEB-INF/layout/fashionLayout.jsp` (구조)

```jsp
<%@ taglib uri="http://tiles.apache.org/tags-tiles" prefix="tiles"%>
<%@ include file="/common/taglib.jsp"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <title>...</title>
    <link rel="stylesheet" href="<c:url value='/resources/css/reset.css'/>?v=${applicationScope.assetVersion}">
    <link rel="stylesheet" href="<c:url value='/resources/css/fashion.css'/>?v=${applicationScope.assetVersion}">
    <link rel="manifest" href="<c:url value='/resources/manifest.json'/>">
    <script>
        var contextPath = '${pageContext.request.contextPath}';
        var categories = [ ... ];
        var isAdmin = ${not empty sessionScope.loginId};
    </script>
    <script src="<c:url value='/resources/js/common/common.js'/>?v=${applicationScope.assetVersion}"></script>
</head>
<body data-season="<c:out value='${currentSlug}'/>">
<header class="site-header">
    ... 사이트 제목, 카테고리 메뉴, 사진 올리기 / 로그아웃 또는 로그인 ...
</header>
<main class="site-main">
    <tiles:insertAttribute name="body" />
</main>
</body>
</html>
```

| 부분 | 하는 일 |
|---|---|
| `<tiles:insertAttribute name="body" />` | 본문 JSP가 들어가는 자리 |
| CSS, `common.js` | 모든 화면이 쓰는 파일. `?v=` 버전은 [정적 파일 버전](/posts/fashion-archive-view-asset-version) |
| `contextPath` | 화면 JS가 API 주소 앞에 붙일 `/p1` |
| `categories` | 카테고리 목록을 JS 배열로 ([ControllerAdvice](/posts/fashion-archive-spring-mvc-controller-advice)) |
| `isAdmin` | 로그인 여부. 화면 JS가 편집 버튼을 보일지 정함 |
| 헤더 | 카테고리 메뉴와 로그인 상태별 링크 |

### 로그인 상태별 헤더

```jsp
<div class="site-account">
    <c:choose>
        <c:when test="${not empty sessionScope.loginId}">
            <a href="<c:url value='/admin/photos/new'/>">사진 올리기</a>
            <a href="<c:url value='/logout'/>">로그아웃</a>
        </c:when>
        <c:otherwise>
            <a href="<c:url value='/login'/>">로그인</a>
        </c:otherwise>
    </c:choose>
</div>
```

`sessionScope.loginId`는 로그인할 때 세션에 넣은 값입니다. JSP는 EL로 세션 값을 바로 읽을 수 있습니다.

### 계절 색: data-season

```jsp
<%-- data-season: 현재 카테고리 slug. CSS의 --season 색이 이 값으로 정해진다 --%>
<body data-season="<c:out value='${currentSlug}'/>">
```

카테고리 페이지(`/c/summer`)에서는 `<body data-season="summer">`가 됩니다. CSS가 이 속성으로 강조 색(`--season`)을 바꿔, 계절마다 화면 색이 달라집니다. 데이터 값을 HTML 속성으로 두고 CSS가 읽는 방식이라 JS 없이 색이 바뀝니다.

<!-- 📷 스크린샷: 여름과 겨울 카테고리 화면의 색 차이 -->

## 본문 JSP

`WEB-INF/jsp/fashion/photo/list.jsp`

```jsp
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="/common/taglib.jsp"%>
<%-- 모델: currentSlug(카테고리 페이지일 때). 사진 데이터는 list.js가 POST /api/photos/list 로 가져와 그린다 --%>
<c:set var="pageTitle" value="최근 사진"/>
<c:forEach var="cat" items="${categories}"><c:if test="${cat.slug eq currentSlug}"><c:set var="pageTitle" value="${cat.name}"/></c:if></c:forEach>

<header class="list-head">
    <h1 class="list-title"><c:out value="${pageTitle}"/></h1>
    <p class="list-count" id="listCount"></p>
</header>

<ul class="photo-grid" id="photoGrid" data-slug="<c:out value='${currentSlug}'/>"></ul>
...
<script defer src="<c:url value='/resources/js/app/photo/list.js'/>?v=${applicationScope.assetVersion}"></script>
```

- 본문 JSP에는 `<html>`, `<head>`가 없습니다. 레이아웃이 감쌉니다.
- 맨 위 주석에 **이 JSP가 받는 모델 값**과 **데이터를 어디서 가져오는지** 적었습니다. 화면과 JS, API의 연결을 JSP 하나만 열어도 알 수 있습니다.
- 화면별 JS는 본문 끝에 `defer`로 싣습니다. `defer` 스크립트는 HTML을 다 읽은 뒤 실행되므로, 레이아웃 `<head>`의 `common.js`(`App.ajax` 등)가 먼저 준비됩니다.
- 목록 칸은 비어 있고, `list.js`가 API로 받은 사진으로 채웁니다.

## 레이아웃 없이 그리는 화면

| 화면 | 뷰 이름 | 그리는 방법 |
|---|---|---|
| 로그인 | `login/loginMain` | Tiles 정의에 없음 → JSP 리졸버 → 단독 JSP |
| 가입 | `login/signupMain` | 같음 |
| 오류 | (web.xml `error-page`) | `/WEB-INF/jsp/error.jsp` 직접 |

설계 문서(`docs/superpowers/specs/2026-09-16-fashion-archive-design.md`)의 화면 표는 로그인 화면을 "레이아웃 없음"으로 정해 두었습니다. 그래서 이 JSP들은 `<html>`부터 직접 갖춘 완전한 페이지입니다.

`WEB-INF/jsp/login/loginMain.jsp` (앞부분)

```jsp
<!DOCTYPE html>
<html lang="ko">
<head>
    <title>로그인 - Fashion Archive</title>
    <link rel="stylesheet" href="<c:url value='/resources/css/reset.css'/>?v=${applicationScope.assetVersion}">
    <link rel="stylesheet" href="<c:url value='/resources/css/fashion.css'/>?v=${applicationScope.assetVersion}">
    <script>var contextPath = '${pageContext.request.contextPath}';</script>
    <script src="<c:url value='/resources/js/common/common.js'/>?v=${applicationScope.assetVersion}"></script>
    <script defer src="<c:url value='/resources/js/app/login/login.js'/>?v=${applicationScope.assetVersion}"></script>
</head>
<body class="login-page">
```

레이아웃이 하던 일 중 필요한 것(CSS, `contextPath`, `common.js`)만 직접 싣습니다.

## 정리

- 레이아웃 JSP가 공통 CSS와 JS, 화면 JS용 전역 값, 헤더 메뉴, 계절 색을 맡고, `body` 자리에 본문이 들어갑니다.
- 본문 JSP는 맨 위 주석에 받는 모델과 데이터 출처를 적고, 화면 JS를 `defer`로 싣습니다.
- 로그인, 가입, 오류 화면은 Tiles 정의 밖의 이름으로 레이아웃 없이 그립니다.
