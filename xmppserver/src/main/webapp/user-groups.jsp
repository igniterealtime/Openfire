<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2005-2008 Jive Software, 2017-2026 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>

<%@ page
    import="     org.jivesoftware.openfire.group.Group,
                 org.jivesoftware.openfire.group.GroupNotFoundException,
                 org.jivesoftware.openfire.user.User,
                 org.jivesoftware.openfire.user.UserNotFoundException"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="org.jivesoftware.util.CookieUtils"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.util.*"%>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib prefix="admin" uri="admin" %>
<!-- Define Administration Bean -->
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%
    webManager.init(pageContext);
%>

<%
    // Get parameters
    String add = ParamUtils.getParameter(request, "add");
    String delete = ParamUtils.getParameter(request, "delete");
    String username = ParamUtils.getParameter(request, "username");
    JID jid = webManager.getXMPPServer().createJID(username, null);

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    Map<String,String> errors = new HashMap<>();
    pageContext.setAttribute("errors", errors);
    if (add != null || delete != null) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            add = null;
            delete = null;
            errors.put("csrf", "CSRF security check failed! Please reload page and try again.");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);


    if(add != null) {
        try {
            Group group = webManager.getGroupManager().getGroup(add);
            group.getMembers().add(jid);
            response.sendRedirect("user-groups.jsp?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&updatesuccess=true");
        } catch (GroupNotFoundException exp) {
            return;
        }
    }

    if(delete != null) {
        try {
            Group group = webManager.getGroupManager().getGroup(delete);
            group.getMembers().remove(jid);
            group.getAdmins().remove(jid);
            response.sendRedirect("user-groups.jsp?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&updatesuccess=true");
        } catch (GroupNotFoundException exp) {
            return;
        }
    }

    // Load the user object
    User user = null;
    try {
        user = webManager.getUserManager().getUser(username);
    }
    catch (UserNotFoundException unfe) {
    }
    
    Collection<Group> userGroups = webManager.getGroupManager().getGroups(user);
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",15);
    if (range <= 0) {
        range = 15;
    }

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("group-summary", range);
    }

    ArrayList<Group> groups = new ArrayList<>(webManager.getGroupManager().getGroups());
    // Remove already joined groups 
    groups.removeAll(userGroups);
    

    String search = null;
    if (webManager.getGroupManager().isSearchSupported() && request.getParameter("search") != null
            && !request.getParameter("search").trim().isEmpty()) {
        search = request.getParameter("search");
        // Use the search terms to get the list of groups.
        groups = new ArrayList<>(webManager.getGroupManager().search(search));
        groups.removeAll(userGroups);
    }

    int groupCount = groups.size();

    if (start < 0) {
        start = 0;
    }
    if (start >= groupCount) {
        start = Math.max(0, ((groupCount - 1) / range) * range);
    }
    int end = Math.min(start + range, groupCount);
    List<Group> pagedGroups = groups.subList(start, end);

    // paginator vars
    int numPages = groupCount == 0 ? 1 : (int)Math.ceil((double)groupCount / range);
    int curPage = groupCount == 0 ? 1 : (start / range) + 1;

    pageContext.setAttribute("username", username);
    pageContext.setAttribute("userGroups", userGroups);
    pageContext.setAttribute("groupCount", groupCount);
    pageContext.setAttribute("pagedGroups", pagedGroups);
    pageContext.setAttribute("numPages", numPages);
    pageContext.setAttribute("start", start);
    pageContext.setAttribute("curPage", curPage);
    pageContext.setAttribute("range", range);
    pageContext.setAttribute("search", search);
%>
<html>
<head>
    <title><fmt:message key="user.groups.title" /></title>
    <meta name="subPageID" content="user-groups" />
    <meta name="extraParams" content="username=${admin:escapeHTMLTags(username)}" />
</head>
<body>

    <c:choose>
        <c:when test="${not empty errors}">
            <c:forEach var="err" items="${errors}">
                <admin:infobox type="error">
                    <c:choose>
                        <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                        <c:otherwise>
                            <c:if test="${not empty err.value}">
                                <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                            </c:if>
                            (<c:out value="${err.key}"/>)
                        </c:otherwise>
                    </c:choose>
                </admin:infobox>
            </c:forEach>
        </c:when>
        <c:when test="${param.success}">
            <admin:infobox type="success">
                <fmt:message key="user.groups.form.update" />
            </admin:infobox>
        </c:when>
    </c:choose>

    <p>
        <fmt:message key="user.groups.member.info" />
        <b><c:out value="${username}"/>.</b>
    </p>
    <div class="jive-table">
        <table>
            <thead>
                <tr>
                    <th>&nbsp;</th>
                    <th><fmt:message key="user.groups.name" /></th>
                    <th><fmt:message key="global.delete" /></th>
                </tr>
            </thead>
            <tbody>
                <c:choose>
                    <c:when test="${empty userGroups}">
                        <tr>
                            <td style="text-align: center" colspan="6"><fmt:message key="group.summary.no_groups" /></td>
                        </tr>
                    </c:when>
                    <c:otherwise>
                        <c:forEach var="group" items="${userGroups}" varStatus="status">
                            <tr>
                                <td style="width: 1%; vertical-align: top"><c:out value="${status.count}"/></td>
                                <td><a href="group-edit.jsp?group=${admin:urlEncode(group.name)}"><c:out value="${group.name}"/></a>
                                    <c:if test="${not empty group.description}">
                                        <br> <span class="jive-description"> <c:out value="${group.description}"/></span>
                                    </c:if>
                                </td>
                                <td style="width: 5%"><a
                                    href="user-groups.jsp?username=${admin:urlEncode(username)}&delete=${admin:urlEncode(group.name)}&csrf=${admin:urlEncode(csrf)}"
                                    title="<fmt:message key="global.click_delete" />"><img
                                    src="images/delete-16x16.gif"
                                    alt="<fmt:message key="global.click_delete" />"></a></td>
                            </tr>
                        </c:forEach>
                    </c:otherwise>
                </c:choose>
            </tbody>
        </table>
    </div>
    <br />

    <p>
        <fmt:message key="user.groups.info" />
        <b><c:out value="${username}"/>.</b>
    </p>
    <c:choose>
        <c:when test="${webManager.groupManager.searchSupported}">
            <form action="user-groups.jsp" method="get" name="searchForm">
                <table style="width: 100%">
                    <tr>
                        <td style="vertical-align: bottom"><fmt:message key="group.summary.total_group" /> <b><fmt:formatNumber value="${groupCount}"/></b></td>
                        <td style="text-align: right; vertical-align: bottom"><label for="search"><fmt:message key="group.summary.search" />:</label> <input type="text" size="30" maxlength="150" id="search" name="search" value="<c:out value='${param.search}'/>"></td>
                    </tr>
                </table>
                <input type="hidden" name="username" value="${admin:escapeHTMLTags(username)}"/>
                <input type="hidden" name="csrf" value="${admin:escapeHTMLTags(csrf)}"/>
            </form>

            <script>
                document.searchForm.search.focus();
            </script>
        </c:when>
        <c:otherwise>
            <p>
                <fmt:message key="group.summary.total_group" />
                <b><fmt:formatNumber value="${groupCount}"/></b>
                <c:if test="${numPages gt 1}">
                    ,
                    <fmt:message key="global.showing" />
                    <fmt:formatNumber value="${start + 1}"/>-<fmt:formatNumber value="${start + pagedGroups.size()}"/>
                </c:if>
            </p>
        </c:otherwise>
    </c:choose>

    <c:if test="${numPages gt 1}">
        <p>
            <fmt:message key="global.pages" />
            [
            <c:forEach var="i" begin="0" end="${numPages - 1}">
                <c:set var="sep" value="${i + 1 lt numPages ? ' ' : ''}"/>
                <c:set var="current" value="${i+1 eq curPage}"/>
                <a href="user-groups.jsp?username=${admin:urlEncode(username)}&start=${admin:urlEncode(i*range)}&range=${admin:urlEncode(range)}&search=${admin:urlEncode(empty search ? '' : search)}" class="${current ? 'jive-current' : ''}"><c:out value="${i + 1}"/></a><c:out value="${sep}"/>
            </c:forEach>
            ]
        </p>
    </c:if>

    <div class="jive-table">
        <table>
            <thead>
                <tr>
                    <th>&nbsp;</th>
                    <th nowrap><fmt:message key="user.groups.name" /></th>
                    <th nowrap><fmt:message key="global.add" /></th>
                </tr>
            </thead>
            <tbody>
                <c:choose>
                    <c:when test="${empty pagedGroups}">
                        <tr>
                            <td style="text-align: center" colspan="6"><fmt:message key="group.summary.no_groups" /></td>
                        </tr>
                    </c:when>
                    <c:otherwise>
                        <c:forEach var="group" items="${pagedGroups}" varStatus="status">
                            <tr>
                                <td style="width: 1%; vertical-align: top"><fmt:formatNumber value="${status.count + start}"/></td>
                                <td><a href="group-edit.jsp?group=${admin:urlEncode(group.name)}"><c:out value="${group.name}"/></a>
                                    <c:if test="${not empty group.description}">
                                        <br> <span class="jive-description"> <c:out value="${group.description}"/> </span>
                                    </c:if>
                                </td>
                                <td style="width: 5%"><a
                                    href="user-groups.jsp?username=${admin:urlEncode(username)}&add=${admin:urlEncode(group.name)}&csrf=${admin:urlEncode(csrf)}"
                                    title="<fmt:message key="global.click_add" />"> <img
                                    src="images/add-16x16.gif"
                                    alt="<fmt:message key="global.click_add" />"></a></td>
                            </tr>
                        </c:forEach>
                    </c:otherwise>
                </c:choose>
            </tbody>
        </table>
    </div>

    <c:if test="${numPages gt 1}">
        <br>
        <p>
            <fmt:message key="global.pages" />
            [
            <c:forEach var="i" begin="0" end="${numPages - 1}">
                <c:set var="sep" value="${i + 1 lt numPages ? ' ' : ''}"/>
                <c:set var="current" value="${i+1 eq curPage}"/>
                <a href="user-groups.jsp?username=${admin:urlEncode(username)}&start=${admin:urlEncode(i*range)}&range=${admin:urlEncode(range)}&search=${admin:urlEncode(empty search ? '' : search)}" class="${current ? 'jive-current' : ''}"><c:out value="${i + 1}"/></a><c:out value="${sep}"/>
            </c:forEach>
            ]
        </p>
    </c:if>

</body>
</html>
