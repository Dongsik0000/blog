<%@ tag language="java" pageEncoding="UTF-8" body-content="empty" trimDirectiveWhitespaces="true"%>
<%@ attribute name="segments" required="false" type="java.lang.Object"%>
<%@ attribute name="text" required="false"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<c:choose><c:when test="${empty segments}"><c:out value="${text}"/></c:when><c:otherwise><c:forEach var="segment" items="${segments}"><c:choose>
    <c:when test="${segment.bold and segment.italic}"><strong><em><c:out value="${segment.text}"/></em></strong></c:when>
    <c:when test="${segment.bold}"><strong><c:out value="${segment.text}"/></strong></c:when>
    <c:when test="${segment.italic}"><em><c:out value="${segment.text}"/></em></c:when>
    <c:otherwise><c:out value="${segment.text}"/></c:otherwise>
</c:choose></c:forEach></c:otherwise></c:choose>
