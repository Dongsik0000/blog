<%@ tag language="java" pageEncoding="UTF-8" body-content="empty"%>
<%@ attribute name="blocks" required="false" type="java.lang.Object"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<c:set var="hasHeadings" value="false"/>
<c:forEach var="block" items="${blocks}"><c:if test="${block.type eq 'heading2'}"><c:set var="hasHeadings" value="true"/></c:if></c:forEach>
<c:if test="${hasHeadings}">
    <nav class="sidebar-section article-toc" aria-label="이 글의 목차">
        <h2>이 글의 목차</h2>
        <ol><c:forEach var="block" items="${blocks}" varStatus="position"><c:if test="${block.type eq 'heading2'}"><li><a href="#section-${position.index}"><c:out value="${block.text}"/></a></li></c:if></c:forEach></ol>
    </nav>
</c:if>
