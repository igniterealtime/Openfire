<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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

<%@ page import="org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.server.OutgoingSessionPromise,
                 org.jivesoftware.openfire.server.FailedOutgoingServerSessionAttempt,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.CookieUtils,
                 java.net.URLEncoder,
                 java.nio.charset.StandardCharsets,
                 java.util.*"
    errorPage="error.jsp"
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="admin" prefix="admin" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%
    final String domainName = ParamUtils.getParameter(request, "hostname");
    final SessionManager sessionManager = webManager.getSessionManager();
    final Set<String> failedServers = new HashSet<>(sessionManager.getFailedServers());

    final Map<String, String> errors = new HashMap<>();
    if (domainName == null || domainName.trim().isEmpty()) {
        response.sendRedirect("server-session-summary.jsp");
        return;
    }

    if (!failedServers.contains(domainName)) {
        response.sendRedirect("server-session-details.jsp?hostname=" + java.net.URLEncoder.encode(domainName, java.nio.charset.StandardCharsets.UTF_8));
        return;
    }

    final Optional<FailedOutgoingServerSessionAttempt> failedAttempt = OutgoingSessionPromise.getInstance().getFailedServerAttempt(domainName);

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");
    String action = ParamUtils.getParameter(request, "action");
    boolean showSearchResults = false;
    final Set<String> matchingLocalUsers = new TreeSet<>();

    if (action != null) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            errors.put("csrf", "CSRF Failure!");
        }

        if (errors.isEmpty()) {
            switch (action) {
                case "retry":
                    response.sendRedirect("server-connectiontest.jsp?server2server-testing-domain=" + URLEncoder.encode(domainName, StandardCharsets.UTF_8));
                    return;
                case "block":
                    response.sendRedirect("connection-settings-socket-s2s.jsp?serverBlocked=true&domain=" + URLEncoder.encode(domainName, StandardCharsets.UTF_8));
                    return;
                case "search":
                    showSearchResults = true;
                    break;
                default:
                    errors.put("Unknown action", "unknown action");
                    break;
            }
        }
    }

    if (showSearchResults) {
        matchingLocalUsers.addAll(webManager.getRosterManager().getUsernamesWithRosterItemsOnDomain(domainName));
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);

    pageContext.setAttribute("errors", errors);
%>

<html>
<head>
    <title><fmt:message key="server.session.failure.title"/></title>
    <meta name="pageID" content="server-session-summary"/>
</head>
<body>

<p>
    <fmt:message key="server.session.failure.info">
        <fmt:param value="<b><%= StringUtils.escapeForXML(domainName) %></b>" />
    </fmt:message>
</p>

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

<div class="jive-table">
    <table>
        <tr>
            <th colspan="2"><fmt:message key="server.session.failure.section.overview"/></th>
        </tr>
        <tr>
            <td class="c1"><fmt:message key="server.session.details.domainname"/></td>
            <td><%= StringUtils.escapeHTMLTags(domainName) %></td>
        </tr>
        <tr>
            <td class="c1"><fmt:message key="server.session.label.connection"/></td>
            <td>
                <img src="images/warning-16x16.gif" width="16" height="16" alt="<fmt:message key='server.session.connection.failed' />">
                <fmt:message key="server.session.connection.failed"/>
            </td>
        </tr>
        <tr>
            <td class="c1"><fmt:message key="server.session.failure.last_attempt"/></td>
            <td>
                <% if (failedAttempt.isPresent() && failedAttempt.get().getWhen() != null) { %>
                    <%= JiveGlobals.formatDateTime(Date.from(failedAttempt.get().getWhen())) %>
                <% } else { %>
                    <fmt:message key="server.session.summary.not_available"/>
                <% } %>
            </td>
        </tr>
        <tr>
            <td class="c1"><fmt:message key="server.session.failure.reason"/></td>
            <td>
                <% if (failedAttempt.isPresent() && failedAttempt.get().getErrorMessage() != null && !failedAttempt.get().getErrorMessage().trim().isEmpty()) { %>
                    <%= StringUtils.escapeHTMLTags(failedAttempt.get().getErrorMessage()) %>
                <% } else if (failedAttempt.isPresent() && failedAttempt.get().getErrorType() != null) { %>
                    <%= StringUtils.escapeHTMLTags(failedAttempt.get().getErrorType()) %>
                <% } else { %>
                    <fmt:message key="server.session.summary.not_available"/>
                <% } %>
            </td>
        </tr>
    </table>
</div>

<br/>

<div class="jive-table">
    <table>
        <tr>
            <th><fmt:message key="server.session.failure.section.initiator"/></th>
        </tr>
        <tr>
            <td><fmt:message key="server.session.failure.stub.initiator"/></td>
        </tr>
    </table>
</div>

<br/>

<div style="text-align: center;">
    <form action="server-session-failure-details.jsp" style="display: inline;">
        <input type="hidden" name="hostname" value="<%= StringUtils.escapeForXML(domainName) %>">
        <input type="hidden" name="csrf" value="<%= csrfParam %>"/>
        <input type="hidden" name="action" id="failure-action" value=""/>
        <input type="submit" value="<fmt:message key="server.session.failure.action.retry"/>" onclick="document.getElementById('failure-action').value='retry';"/>
        <input type="submit" value="<fmt:message key="server.session.failure.action.block"/>" onclick="document.getElementById('failure-action').value='block';"/>
        <input type="submit" value="<fmt:message key="server.session.failure.action.search"/>" onclick="document.getElementById('failure-action').value='search';"/>
        <input type="submit" value="<fmt:message key="session.details.back_button"/>" formaction="server-session-summary.jsp" onclick="document.getElementById('failure-action').value='';"/>
    </form>
</div>

<% if (showSearchResults) { %>
<br/>
<div class="jive-table">
    <table>
        <tr>
            <th><fmt:message key="server.session.failure.search.results.title"/></th>
        </tr>
        <% if (matchingLocalUsers.isEmpty()) { %>
        <tr>
            <td><fmt:message key="server.session.failure.search.results.none"/></td>
        </tr>
        <% } else { %>
            <% for (final String username : matchingLocalUsers) { %>
            <tr>
                <td><a href="user-roster.jsp?username=<%= URLEncoder.encode(username, StandardCharsets.UTF_8) %>"><%= StringUtils.escapeHTMLTags(username) %></a></td>
            </tr>
            <% } %>
        <% } %>
    </table>
</div>
<% } %>

</body>
</html>



