<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@ include file="/common/taglib.jsp"%>
<%-- 시리즈: 장별 목차. series 는 PostController 가 넘긴다 (PostService.selectSeries) --%>
<t:layout title="${series.title}" page="posts">
    <div class="blog-layout">
        <section class="feed series-page" aria-labelledby="series-title">
            <header class="feed-heading">
                <p class="post-category">개발 기록 시리즈</p>
                <h1 id="series-title"><c:out value="${series.title}"/><span>.</span></h1>
                <c:if test="${not empty series.summary}"><p><c:out value="${series.summary}"/></p></c:if>
                <c:if test="${not empty series.stack}"><ul class="series-stack" aria-label="사용 기술"><c:forEach var="tech" items="${series.stack}"><li><c:out value="${tech}"/></li></c:forEach></ul></c:if>
                <p class="series-meta">글 <c:out value="${series.postCount}"/>개 · <c:out value="${fn:length(series.chapters)}"/>개 장<c:if test="${not empty series.repo}"> · <a href="<c:out value='${series.repo}'/>" target="_blank" rel="noopener noreferrer">GitHub 저장소</a></c:if></p>
                <c:if test="${not empty series.firstPost}"><c:url var="firstUrl" value="/posts/${series.firstPost.slug}"/><a class="button primary" href="<c:out value='${firstUrl}'/>">처음부터 읽기 <span aria-hidden="true">→</span></a></c:if>
            </header>
            <c:if test="${empty series.chapters}"><p class="feed-empty">아직 발행된 글이 없습니다.</p></c:if>
            <c:forEach var="chapter" items="${series.chapters}" varStatus="chapterStatus">
                <section class="series-chapter" aria-labelledby="chapter-${chapter.id}">
                    <h2 id="chapter-${chapter.id}"><span class="series-chapter-no"><c:out value="${chapterStatus.count}"/>장</span> <c:out value="${chapter.title}"/></h2>
                    <ol>
                        <c:forEach var="p" items="${chapter.posts}"><c:url var="postUrl" value="/posts/${p.slug}"/>
                        <li><a href="<c:out value='${postUrl}'/>"><strong><c:out value="${p.title}"/><c:if test="${p.status eq 'DRAFT'}"> <span class="status-badge">임시저장</span></c:if></strong><span><c:out value="${p.summary}"/></span></a></li>
                        </c:forEach>
                    </ol>
                </section>
            </c:forEach>
        </section>
        <t:blogSidebar categories="false"/>
    </div>
</t:layout>
