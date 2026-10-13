<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<header class="site-header">
    <div class="header-inner">
        <a class="brand" href="<c:url value='/'/>" aria-label="DevGear 홈" translate="no"><img src="<c:url value='/resources/images/devgear-mark.svg'/>" width="38" height="38" alt=""><span class="brand-wordmark">Dev<span>Gear</span></span></a>
        <span class="brand-description">DevMonster의<br>개발과 기술 기록</span>
        <nav class="site-nav" aria-label="주 메뉴">
            <a class="nav-link${requestScope.uiPage eq 'home' ? ' is-active' : ''}" aria-current="${requestScope.uiPage eq 'home' ? 'page' : 'false'}" href="<c:url value='/'/>">홈</a>
            <a class="nav-link${requestScope.uiPage eq 'posts' ? ' is-active' : ''}" aria-current="${requestScope.uiPage eq 'posts' ? 'page' : 'false'}" href="<c:url value='/posts'/>">모든 글</a>
            <a class="nav-link${requestScope.uiPage eq 'about' ? ' is-active' : ''}" aria-current="${requestScope.uiPage eq 'about' ? 'page' : 'false'}" href="<c:url value='/about'/>">DevGear 소개</a>
            <c:if test="${requestScope.isAdmin}"><a class="nav-link${requestScope.uiPage eq 'admin' ? ' is-active' : ''}" aria-current="${requestScope.uiPage eq 'admin' ? 'page' : 'false'}" href="<c:url value='/admin/posts'/>">관리</a></c:if>
        </nav>
    </div>
</header>
