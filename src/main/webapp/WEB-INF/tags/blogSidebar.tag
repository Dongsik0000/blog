<%@ tag language="java" pageEncoding="UTF-8" body-content="empty"%>
<%@ attribute name="blocks" required="false" type="java.lang.Object"%>
<%@ attribute name="categories" required="false" type="java.lang.Boolean"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>
<aside class="blog-sidebar" aria-label="작성자와 글 탐색">
    <section class="author-profile" aria-labelledby="profile-name">
        <img class="author-avatar" src="<c:url value='/resources/images/devmonster-avatar.png'/>" width="88" height="88" alt="" decoding="async">
        <span class="profile-role">만나서 반갑습니다</span>
        <h2 id="profile-name" translate="no">DevMonster</h2>
        <p>코드를 쓰고, 기술을 읽고,<br>좋은 도구를 찾아봅니다.<br>신입 웹개발자의 배움을 기록합니다.</p>
        <a class="profile-link" href="<c:url value='/about'/>">DevGear 소개 <span aria-hidden="true">→</span></a>
    </section>
    <t:articleToc blocks="${blocks}"/>
    <c:if test="${empty categories or categories}">
    <nav class="sidebar-section sidebar-categories" aria-label="주제별 글 모음">
        <h2>카테고리</h2>
        <c:url var="developmentUrl" value="/posts"><c:param name="category" value="development"/></c:url>
        <c:url var="newsUrl" value="/posts"><c:param name="category" value="news"/></c:url>
        <c:url var="gearUrl" value="/posts"><c:param name="category" value="gear"/></c:url>
        <a href="${developmentUrl}"><span>개발 기록<small>직접 만들며 배운 것들</small></span><span aria-hidden="true">→</span></a>
        <a href="${newsUrl}"><span>IT 뉴스<small>개발자의 시선으로 읽는 소식</small></span><span aria-hidden="true">→</span></a>
        <a href="${gearUrl}"><span>IT 장비<small>일상과 작업을 채우는 도구</small></span><span aria-hidden="true">→</span></a>
    </nav>
    </c:if>
</aside>
