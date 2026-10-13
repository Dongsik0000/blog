<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@ include file="/common/taglib.jsp"%>
<t:layout title="페이지 안내" page="error">
    <section class="error-page"><p class="eyebrow">DEVGEAR / PAGE NOTICE</p><span aria-hidden="true"><c:out value="${requestScope['jakarta.servlet.error.status_code']}" default="!"/><span>.</span></span><h1><c:choose><c:when test="${requestScope['jakarta.servlet.error.status_code'] eq 404}">이 페이지에는 아직 이야기가 없습니다.</c:when><c:otherwise>페이지를 불러오지 못했습니다.</c:otherwise></c:choose></h1><p>주소를 다시 확인하거나 DevGear의 다른 글을 살펴보세요.</p><a class="button primary" href="<c:url value='/'/>">홈으로 돌아가기 <svg class="icon" aria-hidden="true"><use href="#i-arrow"/></svg></a></section>
</t:layout>
