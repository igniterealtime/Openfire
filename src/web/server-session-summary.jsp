<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.session.OutgoingServerSession,
                 org.jivesoftware.openfire.session.Session,
                 org.jivesoftware.util.ParamUtils,
                 java.util.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%!
    final int DEFAULT_RANGE = 15;
    final int[] RANGE_PRESETS = {15, 25, 50, 75, 100};
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("server-session-summary", DEFAULT_RANGE));
    boolean close = ParamUtils.getBooleanParameter(request,"close");
    String hostname = ParamUtils.getParameter(request,"hostname");

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("server-session-summary", range);
    }

    // Get the user manager
    SessionManager sessionManager = webManager.getSessionManager();

    Collection<String> hostnames = new TreeSet<String>();
    // Get the incoming session hostnames
    Collection<String> inHostnames = sessionManager.getIncomingServers();
    hostnames.addAll(inHostnames);
    // Get the outgoing session hostnames
    Collection<String> outHostnames = sessionManager.getOutgoingServers();
    hostnames.addAll(outHostnames);

    // Get the session count
    int sessionCount = hostnames.size();

    // Close all connections related to the specified host
    if (close) {
        try {
            for (Session sess : sessionManager.getIncomingServerSessions(hostname)) {
                sess.close();
            }

            Session sess = sessionManager.getOutgoingServerSession(hostname);
            if (sess != null) {
                sess.close();
            }
            // wait one second
            Thread.sleep(1000L);
        }
        catch (Exception ignored) {
            // Session might have disappeared on its own
        }
        // redirect back to this page
        response.sendRedirect("server-session-summary.jsp?close=success");
        return;
    }
    // paginator vars
    int numPages = (int)Math.ceil((double)sessionCount/(double)range);
    int curPage = (start/range) + 1;
    int maxIndex = (start+range <= sessionCount ? start+range : sessionCount);
%>

<html>
    <head>
        <title><fmt:message key="server.session.summary.title"/></title>
        <meta name="pageID" content="server-session-summary"/>
        <meta name="helpPage" content="view_active_server_sessions.html"/>
    </head>
    <body>

<%  if ("success".equals(request.getParameter("close"))) { %>

    <p class="jive-success-text">
    <fmt:message key="server.session.summary.close" />
    </p>

<%  } %>

<p>
<fmt:message key="server.session.summary.active" />: <b><%= hostnames.size() %></b>

<%  if (numPages > 1) { %>

    - <fmt:message key="global.showing" /> <%= (start+1) %>-<%= (start+range) %>

<%  } %>
 - <fmt:message key="server.session.summary.sessions_per_page" />:
<select size="1" onchange="location.href='server-session-summary.jsp?start=0&range=' + this.options[this.selectedIndex].value;">

    <%  for (int i=0; i<RANGE_PRESETS.length; i++) { %>

        <option value="<%= RANGE_PRESETS[i] %>"
         <%= (RANGE_PRESETS[i] == range ? "selected" : "") %>><%= RANGE_PRESETS[i] %></option>

    <%  } %>

</select>
</p>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />:
    [
    <%  for (int i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="server-session-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<p>
<fmt:message key="server.session.summary.info">
    <fmt:param value="<%= "<a href='server2server-settings.jsp'>" %>" />
    <fmt:param value="<%= "</a>" %>" />
</fmt:message>
</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="server.session.label.host" /></th>
        <th nowrap colspan="3"><fmt:message key="server.session.label.connection" /></th>
        <th nowrap><fmt:message key="server.session.label.creation" /></th>
        <th nowrap><fmt:message key="server.session.label.last_active" /></th>
        <th nowrap><fmt:message key="server.session.label.close_connect" /></th>
    </tr>
</thead>
<tbody>
    <%  // Check if no out/in connection to/from a remote server exists
        if (hostnames.isEmpty()) {
    %>
        <tr>
            <td colspan="9">

                <fmt:message key="server.session.summary.not_session" />

            </td>
        </tr>

    <%  } %>

    <% int count = 0;
        hostnames = new ArrayList<String>(hostnames).subList(start, maxIndex);
        for (String host : hostnames) {
            count++;
            List<IncomingServerSession> inSessions = sessionManager.getIncomingServerSessions(host);
            OutgoingServerSession outSession = sessionManager.getOutgoingServerSession(host);
            if (inSessions.isEmpty() && outSession == null) {
                // If the connections were just closed then skip this host
                continue;
            }
    %>
        <%@ include file="server-session-row.jspf" %>
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
        <a href="server-session-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<br>
<p>
<fmt:message key="server.session.summary.last_update" />: <%= JiveGlobals.formatDateTime(new Date()) %>
</p>

    </body>
</html>