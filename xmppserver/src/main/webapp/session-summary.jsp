<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
                 org.jivesoftware.openfire.SessionResultFilter,
                 org.jivesoftware.openfire.session.ClientSession,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.util.StringUtils"
    errorPage="error.jsp"
%>
<%@ page import="java.util.Date" %>
<%@ page import="org.jivesoftware.util.ListPager" %>
<%@ page import="java.util.function.Predicate" %>
<%@ page import="java.net.UnknownHostException" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%!
    private static final String NONE = LocaleUtils.getLocalizedString("global.none");

    private static final int[] REFRESHES = {0, 10, 30, 60, 90};
    private static final String[] REFRESHES_LABELS = {NONE,"10","30","60","90"};
%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    int refresh = ParamUtils.getIntParameter(request,"refresh",webManager.getRefreshValue("session-summary", 0));
    pageContext.setAttribute("refresh", refresh);
    boolean close = ParamUtils.getBooleanParameter(request,"close");
    int order = ParamUtils.getIntParameter(request, "order",
            webManager.getPageProperty("session-summary", "console.order", SessionResultFilter.ASCENDING));
    String jid = ParamUtils.getParameter(request,"jid");

    if (request.getParameter("refresh") != null) {
        webManager.setRefreshValue("session-summary", refresh);
    }

    if (request.getParameter("order") != null) {
        webManager.setPageProperty("session-summary", "console.order", order);
    }

    // Get the user manager
    SessionManager sessionManager = webManager.getSessionManager();

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (close) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            close = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    // Close a connection if requested
    if (close) {
        JID address = new JID(jid);
        try {
            Session sess = sessionManager.getSession(address);
            sess.close();
            // Log the event
            webManager.logEvent("closed session for address "+address, null);
            // wait one second
            Thread.sleep(1000L);
        }
        catch (Exception ignored) {
            // Session might have disappeared on its own
        }
        // Redirect back to this page
        response.sendRedirect("session-summary.jsp?close=success");
        return;
    }

    SessionResultFilter sessionResultFilter = SessionResultFilter.createDefaultSessionFilter();
    sessionResultFilter.setSortOrder(order);
    // Filter out the dodgy looking sessions
    List<ClientSession> sessions = new ArrayList<>(sessionManager.getSessions( sessionResultFilter));

    // By default, display all nodes
    Predicate<ClientSession> filter = clientSession -> true;
    final String searchName = ParamUtils.getStringParameter(request, "searchName", "");
    if(!searchName.trim().isEmpty()) {
        final String searchCriteria = searchName.trim();
        filter = filter.and(clientSession -> StringUtils.containsIgnoringCase(clientSession.getAddress().getNode(), searchCriteria));
    }
    final String searchResource = ParamUtils.getStringParameter(request, "searchResource", "");
    if(!searchResource.trim().isEmpty()) {
        final String searchCriteria = searchResource.trim();
        filter = filter.and(clientSession -> StringUtils.containsIgnoringCase(clientSession.getAddress().getResource(), searchCriteria));
    }
    final String searchNode = ParamUtils.getStringParameter(request, "searchNode", "");
    if(searchNode.equals("local")) {
        filter = filter.and(LocalClientSession.class::isInstance);
    } else if (searchNode.equals("remote")) {
        filter = filter.and(clientSession -> !LocalClientSession.class.isInstance(clientSession));
    }
    final String searchStatus = ParamUtils.getStringParameter(request, "searchStatus", "");
    if(!searchStatus.isEmpty()) {
        filter = filter.and(clientSession -> {
            if (searchStatus.equals("detached")) {
                return clientSession instanceof LocalSession && ((LocalSession) clientSession).isDetached();
            }
            switch (clientSession.getStatus()) {
                case Session.STATUS_CLOSED:
                    return "closed".equals(searchStatus);
                case Session.STATUS_CONNECTED:
                    return "connected".equals(searchStatus);
                case Session.STATUS_AUTHENTICATED:
                    return "authenticated".equals(searchStatus);
                default:
                    return "unknown".equals(searchStatus);
            }
        });
    }
    final String searchPresence = ParamUtils.getStringParameter(request, "searchPresence", "");
    if(!searchPresence.isEmpty()) {
        filter = filter.and(clientSession -> {
            final Presence presence = clientSession.getPresence();
            if (!presence.isAvailable()) {
                return "offline".equals(searchPresence);
            }
            final Presence.Show show = presence.getShow();
            if (show == null) {
                return "online".equals(searchPresence);
            }
            switch (show) {
                case away:
                    return "away".equals(searchPresence);
                case chat:
                    return "chat".equals(searchPresence);
                case dnd:
                    return "dnd".equals(searchPresence);
                case xa:
                    return "xa".equals(searchPresence);
                default:
                    return "unknown".equals(searchPresence);
            }
        });
    }
    final String searchPriority = ParamUtils.getStringParameter(request, "searchPriority", "");
    if (!searchPriority.trim().isEmpty()) {
        int intValue;
        try {
            intValue = Integer.parseInt(searchPriority.trim());
        } catch (final NumberFormatException e) {
            // If we can't parse it, use a default which no session can actually have
            intValue = Integer.MIN_VALUE;
        }
        final int searchCriteria = intValue;
        filter = filter.and(clientSession -> clientSession.getPresence().getPriority() == searchCriteria);
    }
    final String searchHostAddress = ParamUtils.getStringParameter(request, "searchHostAddress", "");
    if(!searchHostAddress.trim().isEmpty()) {
        final String searchCriteria = searchHostAddress.trim();
        filter = filter.and(clientSession -> {
            try {
                return StringUtils.containsIgnoringCase(clientSession.getHostAddress(), searchCriteria);
            } catch (final UnknownHostException e) {
                return false;
            }
        });
    }

    final ListPager<ClientSession> listPager = new ListPager<>(request, response, sessions, filter,
        "refresh", "searchName", "searchResource", "searchNode", "searchStatus", "searchPresence", "searchPriority", "searchHostAddress");
    pageContext.setAttribute("listPager", listPager);
    pageContext.setAttribute("searchName", searchName);
    pageContext.setAttribute("searchResource", searchResource);
    pageContext.setAttribute("searchNode", searchNode);
    pageContext.setAttribute("searchStatus", searchStatus);
    pageContext.setAttribute("searchPresence", searchPresence);
    pageContext.setAttribute("searchPriority", searchPriority);
    pageContext.setAttribute("searchHostAddress", searchHostAddress);
    pageContext.setAttribute("clusteringEnabled", ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting() );
%>
<html>
    <head>
        <title><fmt:message key="session.summary.title"/></title>
        <meta name="pageID" content="session-summary"/>
        <meta name="helpPage" content="view_active_client_sessions.html"/>
        <c:if test="${refresh > 0}">
            <meta http-equiv="refresh" content="${refresh}">
        </c:if>
    </head>
    <body>

<%  if ("success".equals(request.getParameter("close"))) { %>

    <p class="jive-success-text">
    <fmt:message key="session.summary.close" />
    </p>

<%  } %>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tbody>
    <tr valign="top">
        <td width="99%">
            <fmt:message key="session.summary.active" />: <b>${listPager.totalItemCount}</b>
            <c:if test="${listPager.filtered}">
                <fmt:message key="session.summary.filtered_session_count" />: <c:out value="${listPager.filteredItemCount}"/>
            </c:if>
        <c:if test="${listPager.totalPages > 1}">
                -- <fmt:message key="global.showing" /> <c:out value="${listPager.firstItemNumberOnPage}"/>-<c:out value="${listPager.lastItemNumberOnPage}"/>
                <p><fmt:message key="global.pages" />: [ ${listPager.pageLinks} ]
            </c:if>
            -- <fmt:message key="session.summary.sessions_per_page" />:
            ${listPager.pageSizeSelection}
        </td>
        <td width="1%" nowrap>
            <label for="refresh"><fmt:message key="global.refresh" />:</label>
            <select size="1" id="refresh" name="refresh" onchange="submitForm();">
            <%  for (int j=0; j<REFRESHES.length; j++) {
                    String selected = REFRESHES[j] == refresh ? " selected" : "";
            %>
                <option value="<%= REFRESHES[j] %>"<%= selected %>><%= REFRESHES_LABELS[j] %>

            <%  } %>
            </select>
            (<fmt:message key="global.seconds" />)

        </td>
    </tr>
</tbody>
</table>
<br>


<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap>
        <%
            if (sessionResultFilter.getSortField() == SessionResultFilter.SORT_USER) {
                if (sessionResultFilter.getSortOrder() == SessionResultFilter.DESCENDING) {
        %>
        <table border="0"><tr valign="middle"><th>
        <a href="session-summary.jsp?order=<%=SessionResultFilter.ASCENDING %>">
        <fmt:message key="session.details.name" /></a>
        </th><th>
        <a href="session-summary.jsp?order=<%=SessionResultFilter.ASCENDING %>">
        <img src="images/sort_descending.gif" border="0" width="16" height="16" alt=""></a>
        </th></tr></table>
        <%
                }
                else {
        %>
        <table border="0"><tr valign="middle"><th>
        <a href="session-summary.jsp?order=<%=SessionResultFilter.DESCENDING %>">
        <fmt:message key="session.details.name" /></a>
        </th><th>
        <a href="session-summary.jsp?order=<%=SessionResultFilter.DESCENDING %>">
        <img src="images/sort_ascending.gif" width="16" height="16" border="0" alt=""></a>
        </th></tr></table>
        <%
                }
            }
            else {
        %>
            <fmt:message key="session.details.name" />
        <%
            }
        %>
        </th>
        <th nowrap><fmt:message key="session.details.resource" /></th>
        <c:if test="${clusteringEnabled}">
        <th nowrap><fmt:message key="session.details.node" /></th>
        </c:if>
        <th nowrap colspan="2"><fmt:message key="session.details.status" /></th>
        <th nowrap colspan="2"><fmt:message key="session.details.presence" /></th>
        <th nowrap><fmt:message key="session.details.priority" /></th>
        <th nowrap><fmt:message key="session.details.clientip" /></th>
        <th nowrap><fmt:message key="session.details.close_connect" /></th>
    </tr>
    <tr>
        <td nowrap></td>
        <td nowrap>
            <input type="search"
                   id="searchName"
                   size="20"
                   value="<c:out value="${searchName}"/>"/>
            <img src="images/search-16x16.png"
                 width="16" height="16"
                 alt="search" title="search"
                 style="vertical-align: middle;"
                 onclick="submitForm();"
            >
        </td>
        <td nowrap>
            <input type="search"
                   id="searchResource"
                   size="20"
                   value="<c:out value="${searchResource}"/>"/>
            <img src="images/search-16x16.png"
                 width="16" height="16"
                 alt="search" title="search"
                 style="vertical-align: middle;"
                 onclick="submitForm();"
            >
        </td>
        <c:if test="${clusteringEnabled}">
        <td nowrap>
            <select id="searchNode" onchange="submitForm();">
                <option <c:if test='${searchNode eq ""}'>selected</c:if> value=""></option>
                <option <c:if test='${searchNode eq "local"}'>selected </c:if>value="local"><fmt:message key="session.details.local"/></option>
                <option <c:if test='${searchNode eq "remote"}'>selected </c:if>value="remote"><fmt:message key="session.details.remote"/></option>
            </select>
        </td>
        </c:if>
        <td nowrap colspan="2">
            <select id="searchStatus" onchange="submitForm();">
                <option <c:if test='${searchStatus eq ""}'>selected</c:if> value=""></option>
                <option <c:if test='${searchStatus eq "closed"}'>selected</c:if> value="closed"><fmt:message key="session.details.close"/></option>
                <option <c:if test='${searchStatus eq "connected"}'>selected</c:if> value="connected"><fmt:message key="session.details.connect"/></option>
                <option <c:if test='${searchStatus eq "authenticated"}'>selected</c:if> value="authenticated"><fmt:message key="session.details.authenticated"/></option>
                <option <c:if test='${searchStatus eq "detached"}'>selected</c:if> value="detached"><fmt:message key="session.details.local"/> & <fmt:message key="session.details.sm-detached"/></option>
                <option <c:if test='${searchStatus eq "unknown"}'>selected</c:if> value="unknown"><fmt:message key="session.details.unknown"/></option>
            </select>
        </td>
        <td nowrap colspan="2">
            <select id="searchPresence" onchange="submitForm();">
                <option <c:if test='${searchPresence eq ""}'>selected</c:if> value=""></option>
                <option <c:if test='${searchPresence eq "online"}'>selected</c:if> value="online"><fmt:message key="session.details.online"/></option>
                <option <c:if test='${searchPresence eq "away"}'>selected</c:if> value="away"><fmt:message key="session.details.away"/></option>
                <option <c:if test='${searchPresence eq "xa"}'>selected</c:if> value="xa"><fmt:message key="session.details.extended"/></option>
                <option <c:if test='${searchPresence eq "offline"}'>selected</c:if> value="offline"><fmt:message key="user.properties.offline"/></option>
                <option <c:if test='${searchPresence eq "chat"}'>selected</c:if> value="chat"><fmt:message key="session.details.chat_available"/></option>
                <option <c:if test='${searchPresence eq "dnd"}'>selected</c:if> value="dnd"><fmt:message key="session.details.not_disturb"/></option>
                <option <c:if test='${searchPresence eq "unknown"}'>selected</c:if> value="unknown"><fmt:message key="session.details.unknown"/></option>
            </select>
        </td>
        <td nowrap>
            <input type="search"
                   id="searchPriority"
                   size="5"
                   min="-128"
                   max="127"
                   value="<c:out value="${searchPriority}"/>"/>
            <img src="images/search-16x16.png"
                 width="16" height="16"
                 alt="search" title="search"
                 style="vertical-align: middle;"
                 onclick="submitForm();"
            >
        </td>
        <td nowrap>
            <input type="search"
                   id="searchHostAddress"
                   size="20"
                   value="<c:out value="${searchHostAddress}"/>"/>
            <img src="images/search-16x16.png"
                 width="16" height="16"
                 alt="search" title="search"
                 style="vertical-align: middle;"
                 onclick="submitForm();"
            >
        </td>
        <td nowrap></td>
    </tr>
</thead>
<tbody>
    <%
        if (sessions.isEmpty()) {
    %>
        <tr>
            <td colspan="11">

                <fmt:message key="session.summary.not_session" />

            </td>
        </tr>

    <%  } %>

    <%
        // needed in session-row.jspf
        int count = listPager.getFirstItemNumberOnPage();
        final boolean current = false;
        final String linkURL = "session-details.jsp";
        for (final ClientSession sess : listPager.getItemsOnCurrentPage()) {
    %>
        <%@ include file="session-row.jspf" %>
    <%  count++;
        } %>

</tbody>
</table>
</div>

<c:if test="${listPager.totalPages > 1}">
<p><fmt:message key="global.pages" />: [ ${listPager.pageLinks} ]</p>
</c:if>

<br>
<p>
<fmt:message key="session.summary.last_update" />: <%= JiveGlobals.formatDateTime(new Date()) %>
</p>

${listPager.jumpToPageForm}

<script type="text/javascript">
    ${listPager.pageFunctions}
</script>
    </body>
</html>
