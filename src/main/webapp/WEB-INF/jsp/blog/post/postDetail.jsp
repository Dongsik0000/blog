<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@ include file="/common/taglib.jsp"%>
<t:layout title="${post.title}" page="posts">
    <jsp:attribute name="script">
        <script defer src="<c:url value='/resources/js/common/common.js'/>"></script>
        <script defer src="<c:url value='/resources/js/app/comment/comment.js'/>"></script>
    </jsp:attribute>
    <jsp:body>
    <div class="article-back"><a href="<c:url value='/posts'/>">← 모든 글로 돌아가기</a></div>
    <div class="blog-layout reading-layout">
        <article class="article-page">
            <header class="article-header">
                <c:url var="categoryUrl" value="/posts"><c:param name="category" value="${post.category}"/></c:url>
                <a class="article-category" href="<c:out value='${categoryUrl}'/>"><c:out value="${post.categoryLabel}"/></a>
                <c:if test="${post.status eq 'DRAFT'}"><p class="draft-notice">임시저장 글입니다. 관리자에게만 보입니다.</p></c:if>
                <h1><c:out value="${post.title}"/></h1>
                <c:if test="${not empty post.summary}"><p class="article-summary"><c:out value="${post.summary}"/></p></c:if>
                <div class="article-meta">
                    <img src="<c:url value='/resources/images/devmonster-avatar.png'/>" width="36" height="36" alt="" decoding="async">
                    <a href="<c:url value='/about'/>" translate="no">DevMonster</a>
                    <time datetime="<c:out value='${post.publishedAt}'/>"><c:out value="${post.publishedAt}"/></time>
                    <c:if test="${requestScope.isAdmin}"><a class="article-edit" href="<c:url value='/admin/posts/${post.id}'/>">수정</a></c:if>
                </div>
            </header>
            <t:articleToc blocks="${post.blocks}"/>
            <c:if test="${not empty post.coverImage and (fn:startsWith(post.coverImage, '/resources/images/') or fn:startsWith(post.coverImage, '/uploads/')) and not fn:contains(post.coverImage, '..')}">
                <c:url var="coverUrl" value="${post.coverImage}"/>
                <figure class="article-cover">
                    <img src="<c:out value='${coverUrl}'/>" alt="<c:out value='${post.coverAlt}'/>" width="1200" height="675" fetchpriority="high">
                    <c:if test="${not empty post.coverCaption}"><figcaption><c:out value="${post.coverCaption}"/></figcaption></c:if>
                </figure>
            </c:if>
            <div class="article-content">
                <c:forEach var="block" items="${post.blocks}" varStatus="position">
                    <c:choose>
                        <c:when test="${block.type eq 'heading2'}"><h2 id="section-${position.index}"><c:out value="${block.text}"/></h2></c:when>
                        <c:when test="${block.type eq 'heading3'}"><h3><c:out value="${block.text}"/></h3></c:when>
                        <c:when test="${block.type eq 'paragraph'}"><p><t:inlineText segments="${block.segments}" text="${block.text}"/></p></c:when>
                        <c:when test="${block.type eq 'quote'}"><blockquote><t:inlineText segments="${block.segments}" text="${block.text}"/></blockquote></c:when>
                        <c:when test="${block.type eq 'code'}"><pre tabindex="0" aria-label="코드 예제"><code><c:out value="${block.text}"/></code></pre></c:when>
                        <c:when test="${block.type eq 'list'}"><ul><c:forEach var="item" items="${block.items}"><li><t:inlineText segments="${item.segments}" text="${item.text}"/></li></c:forEach></ul></c:when>
                    </c:choose>
                </c:forEach>
                <c:if test="${not empty post.sources}">
                    <section class="article-sources" aria-labelledby="sources-title">
                        <h2 id="sources-title">참고한 기사와 자료</h2>
                        <ol><c:forEach var="source" items="${post.sources}"><li>
                            <c:choose>
                                <c:when test="${fn:startsWith(source.url, 'https://')}"><a href="<c:out value='${source.url}'/>" rel="noopener noreferrer" target="_blank"><c:out value="${source.publisher}"/>, <cite><c:out value="${source.title}"/></cite> <span class="sr-only">(새 창)</span></a></c:when>
                                <c:otherwise><c:out value="${source.publisher}"/>, <cite><c:out value="${source.title}"/></cite></c:otherwise>
                            </c:choose>
                            <span><c:out value="${source.publishedAt}"/></span>
                        </li></c:forEach></ol>
                    </section>
                </c:if>
                <c:if test="${not empty post.hashtags}"><div class="article-tags" aria-label="해시태그"><c:forEach var="tag" items="${post.hashtags}"><span>#<c:out value="${tag}"/></span></c:forEach></div></c:if>
            </div>
            <nav class="article-end" aria-label="글 탐색"><span>읽어주셔서 감사합니다.</span><a class="text-link" href="<c:url value='/posts'/>">모든 글 보기 <span aria-hidden="true">→</span></a></nav>
            <t:commentSection slug="${post.slug}"/>
        </article>
        <t:blogSidebar blocks="${post.blocks}"/>
    </div>
    </jsp:body>
</t:layout>
