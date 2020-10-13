<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean scope="request" id="errorMessage" class="java.lang.String"/>
<jsp:useBean scope="request" id="warningMessage" class="java.lang.String"/>
<jsp:useBean scope="request" id="successMessage" class="java.lang.String"/>
<jsp:useBean scope="request" id="csrf" type="java.lang.String"/>
<jsp:useBean scope="request" id="cacheName" type="java.lang.String"/>
<jsp:useBean scope="request" id="listPager" type="org.jivesoftware.util.ListPager"/>
<jsp:useBean scope="request" id="search" type="org.jivesoftware.admin.servlet.SystemCacheDetailsServlet.Search"/>
<%@ page contentType="text/html; charset=UTF-8" %>
<head>
    <fmt:message key="system.cache-details.title" var="title"><fmt:param value="${cacheName}"/></fmt:message>
    <title><c:out value="${title}"/></title>
    <meta name="pageID" content="system-cache"/>
    <style type="text/css">
        img.clickable {
            cursor: pointer;
        }
    </style>
</head>
<body>
<c:if test="${not empty errorMessage}">
    <div class="error">${errorMessage}</div>
</c:if>

<c:if test="${not empty warningMessage}">
    <div class="warning">${warningMessage}</div>
</c:if>

<c:if test="${not empty successMessage}">
    <div class="success">${successMessage}</div>
</c:if>

<p>
    <fmt:message key="system.cache-details.total"/>: <c:out value="${listPager.totalItemCount}"/>
    <c:if test="${listPager.filtered}">
        <fmt:message key="system.cache-details.filtered"/>: <c:out value="${listPager.filteredItemCount}"/>
    </c:if>

    <c:if test="${listPager.totalPages > 1}">
        <fmt:message key="global.showing"/> <c:out value="${listPager.firstItemNumberOnPage}"/>-<c:out
        value="${listPager.lastItemNumberOnPage}"/>
    </c:if>
    -- <fmt:message key="system.cache-details.per-page"/>: ${listPager.pageSizeSelection}

</p>

<p><fmt:message key="global.pages"/>: [ ${listPager.pageLinks} ]</p>
<div class="jive-table">
    <table style="padding:0; border-spacing:0; width: 100%">
        <thead>
        <tr>
            <th nowrap><label for="searchKey"><fmt:message key="system.cache-details.key"/></label></th>
            <th nowrap><label for="searchValue"><fmt:message key="system.cache-details.value"/></label></th>
            <th style="text-align:center;"><fmt:message key="global.delete"/></th>
        </tr>
        <tr>
            <td nowrap>
                <input type="search"
                       id="searchKey"
                       size="40"
                       value="<c:out value="${search.key}"/>"/>
                <img src="images/search-16x16.png"
                     width="16" height="16"
                     class="clickable"
                     alt="Search" title="Search"
                     style="vertical-align: middle;"
                     onclick="submitForm();"
                >
            </td>
            <td nowrap>
                <input type="search"
                       id="searchValue"
                       size="40"
                       value="<c:out value="${search.value}"/>"/>
                <img src="images/search-16x16.png"
                     width="16" height="16"
                     class="clickable"
                     alt="Search" title="Search"
                     style="vertical-align: middle;"
                     onclick="submitForm();"
                >
            </td>
            <td nowrap>
            </td>
        </tr>
        </thead>
        <tbody>
        <c:set var="rowClass" value="jive-even"/>
        <c:forEach var="entry" items="${listPager.itemsOnCurrentPage}">
            <%--@elvariable id="entry" type="java.util.Map.Entry"--%>
            <c:choose>
                <c:when test="${rowClass == 'jive-even'}"><c:set var="rowClass" value="jive-odd"/></c:when>
                <c:otherwise><c:set var="rowClass" value="jive-even"/></c:otherwise>
            </c:choose>
            <tr class="${rowClass}">
                <td>
                    <%--
                    Note; wrap the property key (and value) in a span so it's easy to extract it in JavaScript
                    without any extra whitespace
                    --%>
                    <span><c:out value="${entry.key}"/></span>
                </td>
                <td>
                    <c:out value="${entry.value}"/>
                </td>
                <td style="text-align:center">
                    <img class="clickable"
                         src="images/delete-16x16.gif"
                         width="16" height="16"
                         onclick="doDelete(this);"
                         alt="<fmt:message key="system.cache-details.alt_delete"/>"
                         title="<fmt:message key="system.cache-details.alt_delete"/>"
                        >
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>
<p><fmt:message key="global.pages"/>: [ ${listPager.pageLinks} ]</p>
${listPager.jumpToPageForm}


<form method="post" id="actionForm">
    <%=listPager.getHiddenFields()%>
    <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
    <input type="hidden" name="action">
    <input type="hidden" name="key">
</form>

<script type="text/javascript">
    ${listPager.pageFunctions}

    function doDelete(imgObject) {
        var key = imgObject.parentNode.parentNode.childNodes[1].childNodes[1].textContent;
        var action;
        if (confirm('<fmt:message key="system.cache-details.delete_confirm"/>'.replace('{0}', key))) {
            action = 'delete'
        } else {
            action = 'cancel'
        }
        var form = document.getElementById("actionForm");
        form["action"].value = action;
        form["key"].value = key;
        form.submit();
    }
</script>
</body>
