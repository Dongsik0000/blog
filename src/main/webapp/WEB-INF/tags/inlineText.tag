<%@ tag language="java" pageEncoding="UTF-8" body-content="empty" trimDirectiveWhitespaces="true"%>
<%@ attribute name="segments" required="false" type="java.lang.Object"%>
<%@ attribute name="text" required="false"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<%@ taglib prefix="fn" uri="jakarta.tags.functions"%>
<%-- 문장 구간 출력: 굵게·기울임·코드 글자·링크. 글자는 c:out 으로만 넣는다.
     href 는 서버(MarkdownBlocks.safeHref)가 허용한 주소만 온다: 블로그 안(/posts·/series)은 c:url, 밖은 새 창 --%>
<c:choose><c:when test="${empty segments}"><c:out value="${text}"/></c:when><c:otherwise><c:forEach var="s" items="${segments}">
<c:if test="${not empty s.href}"><c:choose><c:when test="${fn:startsWith(s.href, '/')}"><a href="<c:url value='${s.href}'/>"></c:when><c:otherwise><a href="<c:out value='${s.href}'/>" target="_blank" rel="noopener noreferrer"></c:otherwise></c:choose></c:if>
<c:if test="${s.bold}"><strong></c:if><c:if test="${s.italic}"><em></c:if><c:if test="${s.code}"><code></c:if>
<c:out value="${s.text}"/>
<c:if test="${s.code}"></code></c:if><c:if test="${s.italic}"></em></c:if><c:if test="${s.bold}"></strong></c:if>
<c:if test="${not empty s.href}"></a></c:if>
</c:forEach></c:otherwise></c:choose>
