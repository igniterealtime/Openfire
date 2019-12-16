<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="admin" uri="admin" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<jsp:useBean scope="request" id="securityAuditProvider" type="org.jivesoftware.openfire.security.SecurityAuditProvider"/>
<jsp:useBean scope="request" id="search" type="org.jivesoftware.admin.servlet.SecurityAuditViewerServlet.Search"/>
<jsp:useBean scope="request" id="listPager" type="org.jivesoftware.util.ListPager"/>

<html>
<head>
    <title><fmt:message key="security.audit.viewer.title"/></title>
    <meta name="pageID" content="security-audit-viewer"/>
</head>
<body>
<c:choose>
<c:when test="${securityAuditProvider.writeOnly}">
    <div class="warning">
    <fmt:message key="security.audit.viewer.write_only"/>
    <c:if test="${not empty securityAuditProvider.auditURL}">
        <br />
        <br />
        <strong><fmt:message key="security.audit.viewer.view_url" /></strong>:
        <a target="_new" href="${securityAuditProvider.auditURL}">${securityAuditProvider.auditURL}></a>
    </c:if>
    </div>
</c:when>
<c:otherwise>
<p>
    <fmt:message key="security.audit.viewer.total"/>: <c:out value="${listPager.totalItemCount}"/>
    <c:if test="${listPager.filtered}">
        <fmt:message key="security.audit.viewer.filtered"/>: <c:out value="${listPager.filteredItemCount}"/>
    </c:if>

    <c:if test="${listPager.totalPages > 1}">
        <fmt:message key="global.showing"/> <c:out value="${listPager.firstItemNumberOnPage}"/>-<c:out
        value="${listPager.lastItemNumberOnPage}"/>
    </c:if>
    -- <fmt:message key="security.audit.viewer.per-page"/>: ${listPager.pageSizeSelection}

</p>

<p><fmt:message key="global.pages"/>: [ ${listPager.pageLinks} ]</p>
<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
        <tr>
            <th nowrap><fmt:message key="security.audit.viewer.id" /></th>
            <th nowrap><fmt:message key="security.audit.viewer.username" /></th>
            <th nowrap><fmt:message key="security.audit.viewer.node" /></th>
            <th nowrap><fmt:message key="security.audit.viewer.event" /></th>
            <th nowrap><fmt:message key="security.audit.viewer.timestamp" /></th>
        </tr>
        <tr>
            <td></td>
            <td>
                <input type="search"
                       id="searchUsername"
                       size="20"
                       value="<c:out value="${search.username}"/>"/>
                <img src="images/search-16x16.png"
                     width="16" height="16"
                     class="clickable"
                     alt="Search" title="Search"
                     style="vertical-align: middle; cursor: pointer;"
                     onclick="submitForm();"
                >
            </td>
            <td>
                <input type="search"
                       id="searchNode"
                       size="20"
                       value="<c:out value="${search.node}"/>"/>
                <img src="images/search-16x16.png"
                     width="16" height="16"
                     class="clickable"
                     alt="Search" title="Search"
                     style="vertical-align: middle; cursor: pointer;"
                     onclick="submitForm();"
                >
            </td>
            <td>
                <input type="search"
                       id="searchSummary"
                       size="20"
                       value="<c:out value="${search.summary}"/>"/>
                <img src="images/search-16x16.png"
                     width="16" height="16"
                     class="clickable"
                     alt="Search" title="Search"
                     style="vertical-align: middle; cursor: pointer;"
                     onclick="submitForm();"
                >
            </td>
            <td>
                <table>
                    <tr>
                        <td nowrap>
                            <fmt:message key="security.audit.viewer.timestamp-from"/>:
                        </td>
                        <td nowrap>
                            <input type="search"
                                   id="searchFrom"
                                   placeholder="YYYY/MM/DD"
                                   size="10"
                                   value="<c:out value="${search.from}"/>"/>
                            <img src="images/search-16x16.png"
                                 width="16" height="16"
                                 class="clickable"
                                 alt="Search" title="Search"
                                 style="vertical-align: middle; cursor: pointer;"
                                 onclick="submitForm();"
                            >
                        </td>
                    </tr>
                    <tr>
                        <td nowrap>
                            <fmt:message key="security.audit.viewer.timestamp-to"/>:
                        </td>
                        <td nowrap>
                            <input type="search"
                                   id="searchTo"
                                   placeholder="YYYY/MM/DD"
                                   size="10"
                                   value="<c:out value="${search.to}"/>"/>
                            <img src="images/search-16x16.png"
                                 width="16" height="16"
                                 class="clickable"
                                 alt="Search" title="Search"
                                 style="vertical-align: middle; cursor: pointer;"
                                 onclick="submitForm();"
                            >
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        </thead>
        <tbody>
        <c:set var="rowClass" value="jive-even"/>
        <c:forEach var="event" items="${listPager.itemsOnCurrentPage}">
            <%--@elvariable id="event" type="org.jivesoftware.openfire.security.SecurityAuditEvent"--%>
            <c:choose>
                <c:when test="${rowClass == 'jive-even'}"><c:set var="rowClass" value="jive-odd"/></c:when>
                <c:otherwise><c:set var="rowClass" value="jive-even"/></c:otherwise>
            </c:choose>
            <tr class="${rowClass}">
                <td><c:out value="${event.msgID}"/></td>
                <td><c:out value="${event.username}"/></td>
                <td><c:out value="${event.node}"/></td>
                <td>
                    <c:out value="${event.summary}"/>
                    <c:if test="${not empty event.details}">
                        <span id='showDetails<c:out value="${event.msgID}"/>'>
                            <a href="" onclick='document.getElementById("showDetails<c:out value='${event.msgID}'/>").style.display="none"; document.getElementById("hideDetails<c:out value='${event.msgID}'/>").style.display=""; document.getElementById("detailsRow<c:out value='${event.msgID}'/>").style.display=""; return false;'><fmt:message key="security.audit.viewer.show_details"/></a>
                        </span>
                        <span id='hideDetails<c:out value="${event.msgID}"/>' style='display:none'>
                            <a href="" onclick='document.getElementById("showDetails<c:out value='${event.msgID}'/>").style.display=""; document.getElementById("hideDetails<c:out value='${event.msgID}'/>").style.display="none"; document.getElementById("detailsRow<c:out value='${event.msgID}'/>").style.display="none"; return false;'><fmt:message key="security.audit.viewer.hide_details"/></a>
                        </span>
                    </c:if>
                </td>
                <td><c:out value="${admin:formatDateTime(event.eventStamp)}"/></td>
            </tr>
            <tr class="${rowClass}" id='detailsRow<c:out value="${event.msgID}"/>' style="display:none">
                <td colspan="5"><c:out value="${event.details}"/></td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>
<p><fmt:message key="global.pages"/>: [ ${listPager.pageLinks} ]</p>
${listPager.jumpToPageForm}
<script type="text/javascript">
    ${listPager.pageFunctions}
</script>
</c:otherwise>
</c:choose>
</body>
