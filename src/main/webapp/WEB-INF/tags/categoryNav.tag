<%@ tag language="java" pageEncoding="UTF-8" body-content="empty"%>
<%@ attribute name="selected" required="false"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<nav class="category-nav" aria-label="글 카테고리">
    <c:url var="allUrl" value="/posts"/>
    <a href="${allUrl}" class="${empty selected ? 'is-active' : ''}" aria-current="${empty selected and requestScope.uiPage eq 'posts' ? 'page' : 'false'}">전체</a>
    <c:forTokens var="category" items="development,news,gear" delims=",">
        <c:url var="categoryUrl" value="/posts"><c:param name="category" value="${category}"/></c:url>
        <a href="<c:out value='${categoryUrl}'/>" class="${selected eq category ? 'is-active' : ''}" aria-current="${selected eq category ? 'page' : 'false'}"><c:choose><c:when test="${category eq 'development'}">개발 기록</c:when><c:when test="${category eq 'news'}">IT 뉴스</c:when><c:otherwise>IT 장비</c:otherwise></c:choose></a>
    </c:forTokens>
</nav>
