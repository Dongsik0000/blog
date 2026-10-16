<%@ tag language="java" pageEncoding="UTF-8" body-content="empty"%>
<%@ attribute name="items" required="false" type="java.lang.Object"%>
<%@ attribute name="category" required="false"%>
<%@ attribute name="featured" required="false" type="java.lang.Boolean"%>
<%@ attribute name="welcome" required="false" type="java.lang.Boolean"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<%@ taglib prefix="fn" uri="jakarta.tags.functions"%>
<c:set var="visibleCount" value="0"/>
<div class="post-list">
    <c:forEach var="entry" items="${items}">
        <c:if test="${empty category or entry.category eq category}">
            <c:set var="visibleCount" value="${visibleCount + 1}"/>
            <c:url var="entryUrl" value="/posts/${entry.slug}"/>
            <article class="post-item${featured and visibleCount eq 1 ? ' post-featured' : ''}">
                <a class="post-link" href="<c:out value='${entryUrl}'/>">
                    <div class="post-copy">
                        <span class="post-category"><c:out value="${entry.categoryLabel}"/><c:if test="${not empty entry.seriesTitle}"> · <c:out value="${entry.seriesTitle}"/></c:if></span>
                        <h2><c:out value="${entry.title}"/></h2>
                        <p class="post-summary"><c:out value="${entry.summary}"/></p>
                        <div class="post-meta"><span translate="no">DevMonster</span><span aria-hidden="true">·</span><time datetime="<c:out value='${entry.publishedAt}'/>"><c:out value="${entry.publishedAt}"/></time></div>
                    </div>
                    <c:if test="${not empty entry.coverImage and (fn:startsWith(entry.coverImage, '/resources/images/') or fn:startsWith(entry.coverImage, '/uploads/')) and not fn:contains(entry.coverImage, '..')}">
                        <c:url var="imageUrl" value="${entry.coverImage}"/>
                        <div class="post-thumbnail"><img src="<c:out value='${imageUrl}'/>" alt="" width="400" height="300" loading="${visibleCount eq 1 ? 'eager' : 'lazy'}" decoding="async"></div>
                    </c:if>
                </a>
            </article>
        </c:if>
    </c:forEach>
</div>
<c:if test="${visibleCount eq 0}">
    <c:choose>
    <c:when test="${welcome}">
        <article class="welcome-story">
            <span class="post-category">블로그 소개</span>
            <h2>만들다 막히고,<br>읽다가 궁금해진 것들.</h2>
            <p>안녕하세요. 신입 웹개발자 DevMonster입니다.<br>개발하면서 겪은 일, 관심 있게 읽은 기술 소식,<br>작업에 쓰는 도구 이야기를 이곳에 남깁니다.</p>
            <a class="text-link" href="<c:url value='/about'/>">DevGear 소개 <span aria-hidden="true">→</span></a>
        </article>
        <p class="publication-note">첫 게시물을 준비하고 있습니다. 발행된 글은 이곳에서 볼 수 있습니다.</p>
    </c:when>
    <c:otherwise>
    <div class="feed-empty">
        <h2><c:choose><c:when test="${empty category}">아직 발행된 글이 없습니다.</c:when><c:otherwise>이 주제의 첫 글을 준비하고 있습니다.</c:otherwise></c:choose></h2>
        <p>직접 만든 것, 새롭게 알게 된 것,<br>곁에 두고 쓰는 것들을 이곳에 기록할 예정입니다.</p>
        <c:choose><c:when test="${not empty category}"><a class="text-link" href="<c:url value='/posts'/>">모든 글 보기 <span aria-hidden="true">→</span></a></c:when><c:otherwise><a class="text-link" href="<c:url value='/about'/>">DevGear 소개 <span aria-hidden="true">→</span></a></c:otherwise></c:choose>
    </div>
    </c:otherwise>
    </c:choose>
</c:if>
