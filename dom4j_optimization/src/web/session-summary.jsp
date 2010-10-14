<%--
  -	$Revision$
  -	$Date$
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
                 java.util.Collection"
    errorPage="error.jsp"
%>
<%@ page import="java.util.Date" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%!
    static final String NONE = LocaleUtils.getLocalizedString("global.none");

    final int DEFAULT_RANGE = 15;
    final int[] RANGE_PRESETS = {15, 25, 50, 75, 100};

    static final int[] REFRESHES = {0, 10, 30, 60, 90};
    static final String[] REFRESHES_LABELS = {NONE,"10","30","60","90"};
%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("session-summary", DEFAULT_RANGE));
    int refresh = ParamUtils.getIntParameter(request,"refresh",webManager.getRefreshValue("session-summary", 0));
    boolean close = ParamUtils.getBooleanParameter(request,"close");
    int order = ParamUtils.getIntParameter(request, "order",
            webManager.getPageProperty("session-summary", "console.order", SessionResultFilter.ASCENDING));
    String jid = ParamUtils.getParameter(request,"jid");

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("session-summary", range);
    }

    if (request.getParameter("refresh") != null) {
        webManager.setRefreshValue("session-summary", refresh);
    }

    if (request.getParameter("order") != null) {
        webManager.setPageProperty("session-summary", "console.order", order);
    }

    // Get the user manager
    SessionManager sessionManager = webManager.getSessionManager();

    // Get the session count
    int sessionCount = sessionManager.getUserSessionsCount(false);

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

    // paginator vars
    int numPages = (int)Math.ceil((double)sessionCount/(double)range);
    int curPage = (start/range) + 1;
    // Check that we are not out of bounds
    if (curPage > numPages && numPages > 0){
      curPage = numPages;
      start = (numPages-1)*range;
    }
%>

<html>
    <head>
        <title><fmt:message key="session.summary.title"/></title>
        <meta name="pageID" content="session-summary"/>
        <meta name="helpPage" content="view_active_client_sessions.html"/>
    </head>
    <body>

<%  if ("success".equals(request.getParameter("close"))) { %>

    <p class="jive-success-text">
    <fmt:message key="session.summary.close" />
    </p>

<%  } %>

<%  if (refresh > 0) { %>
    <meta http-equiv="refresh" content="<%= refresh %>">
<%  } %>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tbody>
<form action="session-summary.jsp" method="get">
    <tr valign="top">
        <td width="99%">
            <fmt:message key="session.summary.active" />: <b><%= sessionCount %></b>

            <%  if (numPages > 1) { %>

                -- <fmt:message key="global.showing" /> <%= (start+1) %>-<%= (start+range) %>

            <%  } %>

            <%  if (numPages > 1) { %>

                <p>
                <fmt:message key="global.pages" />:
                [
                <%  for (int i=0; i<numPages; i++) {
                        String sep = ((i+1)<numPages) ? " " : "";
                        boolean isCurrent = (i+1) == curPage;
                %>
                    <a href="session-summary.jsp?start=<%= (i*range) %>"
                     class="<%= ((isCurrent) ? "jive-current" : "") %>"
                     ><%= (i+1) %></a><%= sep %>

                <%  } %>
                ]

            <%  } %>
            -- <fmt:message key="session.summary.sessions_per_page" />:
            <select size="1" name="range" onchange="this.form.submit();">

                <% for (int aRANGE_PRESETS : RANGE_PRESETS) { %>

                <option value="<%= aRANGE_PRESETS %>"<%= (aRANGE_PRESETS == range ? "selected" : "") %>><%= aRANGE_PRESETS %>
                </option>

                <% } %>

            </select>
        </td>
        <td width="1%" nowrap>
            <fmt:message key="global.refresh" />:
            <select size="1" name="refresh" onchange="this.form.submit();">
            <%  for (int j=0; j<REFRESHES.length; j++) {
                    String selected = REFRESHES[j] == refresh ? " selected" : "";
            %>
                <option value="<%= REFRESHES[j] %>"<%= selected %>><%= REFRESHES_LABELS[j] %>

            <%  } %>
            </select>
            (<fmt:message key="global.seconds" />)

        </td>
    </tr>
</form>
</tbody>
</table>
<br>

 <% // Get the iterator of sessions, print out session info if any exist.
     SessionResultFilter filter = SessionResultFilter.createDefaultSessionFilter();
     filter.setSortOrder(order);
     filter.setStartIndex(start);
     filter.setNumResults(range);
     Collection<ClientSession> sessions = sessionManager.getSessions(filter);
 %>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap>
        <%
            if (filter.getSortField() == SessionResultFilter.SORT_USER) {
                if (filter.getSortOrder() == SessionResultFilter.DESCENDING) {
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
        <th nowrap colspan="2"><fmt:message key="session.details.status" /></th>
        <th nowrap colspan="2"><fmt:message key="session.details.presence" /></th>
        <th nowrap><fmt:message key="session.details.priority" /></th>
        <th nowrap><fmt:message key="session.details.clientip" /></th>
        <th nowrap><fmt:message key="session.details.close_connect" /></th>
    </tr>
</thead>
<tbody>
    <%
        if (sessions.isEmpty()) {
    %>
        <tr>
            <td colspan="10">

                <fmt:message key="session.summary.not_session" />

            </td>
        </tr>

    <%  } %>

    <%  int count = start;
        boolean current = false; // needed in session-row.jspf
        String linkURL = "session-details.jsp";
        for (ClientSession sess : sessions) {
            count++;
    %>
        <%@ include file="session-row.jspf" %>
    <%  } %>

</tbody>
</table>
</div>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />:
    [
    <%  for (int i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="session-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<br>
<p>
<fmt:message key="session.summary.last_update" />: <%= JiveGlobals.formatDateTime(new Date()) %>
</p>

    </body>
</html>