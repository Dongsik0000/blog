<%@ tag language="java" pageEncoding="UTF-8" body-content="scriptless"%>
<%@ attribute name="title" required="true"%>
<%@ attribute name="page" required="true"%>
<%@ attribute name="script" required="false" fragment="true"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<c:set var="uiPage" value="${page}" scope="request"/>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="theme-color" content="#eeeae4">
    <meta name="description" content="DevMonster가 기록하는 웹 개발, IT 뉴스, IT 장비 이야기. DevGear에서 개발자의 시선으로 기술을 읽어보세요.">
    <title><c:out value="${title}"/> · DevGear</title>
    <link rel="icon" href="<c:url value='/resources/images/devgear-mark.svg'/>" type="image/svg+xml">
    <link rel="stylesheet" crossorigin href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css">
    <link rel="stylesheet" href="<c:url value='/resources/css/reset.css'/>">
    <link rel="stylesheet" href="<c:url value='/resources/css/common.css'/>">
    <link rel="stylesheet" href="<c:url value='/resources/css/style.css'/>">
    <script>var contextPath = "${pageContext.request.contextPath}";</script>
    <jsp:invoke fragment="script"/>
</head>
<body>
<jsp:include page="/WEB-INF/layout/icons.jsp"/>
<a class="skip-link" href="#main-content">본문으로 바로가기</a>
<div class="app-shell">
    <jsp:include page="/WEB-INF/layout/siteHeader.jsp"/>
    <main class="app-main" id="main-content" tabindex="-1">
        <div class="page-content">
            <jsp:doBody/>
        </div>
    </main>
    <jsp:include page="/WEB-INF/layout/siteFooter.jsp"/>
</div>
</body>
</html>
