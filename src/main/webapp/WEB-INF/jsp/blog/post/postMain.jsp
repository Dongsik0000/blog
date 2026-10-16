<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@ include file="/common/taglib.jsp"%>
<%-- posts·currentPage·totalPages·selectedCategory 는 PostController 가 넘긴다 --%>
<c:choose>
    <c:when test="${selectedCategory eq 'development'}"><c:set var="categoryTitle" value="개발 기록"/><c:set var="categoryDescription" value="만들고 고치고, 다시 이해한 웹 개발 이야기."/></c:when>
    <c:when test="${selectedCategory eq 'news'}"><c:set var="categoryTitle" value="IT 뉴스"/><c:set var="categoryDescription" value="오늘의 기술 소식에 개발자의 생각을 더합니다."/></c:when>
    <c:when test="${selectedCategory eq 'gear'}"><c:set var="categoryTitle" value="IT 장비"/><c:set var="categoryDescription" value="책상 위에서 일상까지, 개발자가 관심을 두는 도구."/></c:when>
    <c:otherwise><c:set var="categoryTitle" value="모든 글"/><c:set var="categoryDescription" value="개발과 기술에 관해 남긴 이야기를 한곳에 모았습니다."/></c:otherwise>
</c:choose>
<t:layout title="${categoryTitle}" page="posts">
    <div class="blog-layout">
        <section class="feed" aria-labelledby="archive-title">
            <header class="feed-heading"><h1 id="archive-title"><c:out value="${categoryTitle}"/><span>.</span></h1><p><c:out value="${categoryDescription}"/></p></header>
            <t:categoryNav selected="${selectedCategory}"/>
            <c:if test="${not empty seriesCards}">
                <section class="series-cards" aria-labelledby="series-cards-title">
                    <h2 id="series-cards-title">시리즈로 읽기</h2>
                    <ul>
                        <c:forEach var="s" items="${seriesCards}"><c:url var="cardUrl" value="/series/${s.slug}"/>
                        <li><a href="<c:out value='${cardUrl}'/>"><strong><c:out value="${s.title}"/></strong><span><c:out value="${s.summary}"/></span><small>글 <c:out value="${s.postCount}"/>개 · <c:out value="${fn:length(s.chapters)}"/>개 장</small></a></li>
                        </c:forEach>
                    </ul>
                </section>
            </c:if>
            <div class="feed-caption"><span>글 모음</span><span>최신 글부터</span></div>
            <t:postList items="${posts}" category="${selectedCategory}"/>
            <c:if test="${totalPages > 1}">
                <nav class="pagination" aria-label="글 목록 페이지">
                    <c:choose>
                        <c:when test="${currentPage > 1}">
                            <c:url var="prevUrl" value="/posts"><c:if test="${not empty selectedCategory}"><c:param name="category" value="${selectedCategory}"/></c:if><c:param name="page" value="${currentPage - 1}"/></c:url>
                            <a class="pagination-link" href="<c:out value='${prevUrl}'/>" rel="prev"><span aria-hidden="true">←</span> 최신 글</a>
                        </c:when>
                        <c:otherwise><span></span></c:otherwise>
                    </c:choose>
                    <span class="pagination-count"><c:out value="${currentPage}"/> / <c:out value="${totalPages}"/></span>
                    <c:choose>
                        <c:when test="${currentPage < totalPages}">
                            <c:url var="nextUrl" value="/posts"><c:if test="${not empty selectedCategory}"><c:param name="category" value="${selectedCategory}"/></c:if><c:param name="page" value="${currentPage + 1}"/></c:url>
                            <a class="pagination-link" href="<c:out value='${nextUrl}'/>" rel="next">지난 글 <span aria-hidden="true">→</span></a>
                        </c:when>
                        <c:otherwise><span></span></c:otherwise>
                    </c:choose>
                </nav>
            </c:if>
        </section>
        <t:blogSidebar categories="false"/>
    </div>
</t:layout>
