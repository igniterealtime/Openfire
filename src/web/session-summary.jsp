<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.Iterator,
                 org.jivesoftware.messenger.*,
                 java.util.Date,
                 org.jivesoftware.admin.*,
                 java.text.DateFormat"
    errorPage="error.jsp"
%>

<%@ taglib uri="core" prefix="c"%>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",15);
    boolean close = ParamUtils.getBooleanParameter(request,"close");
    String jid = ParamUtils.getParameter(request,"jid");

    // Get the user manager
    SessionManager sessionManager = admin.getSessionManager();

    // Get the session count
    int sessionCount = sessionManager.getSessionCount();

    // Close a connection if requested
    if (close) {
        XMPPAddress address = XMPPAddress.parseJID(jid);
        try {
            Session sess = sessionManager.getSession(address);
            sess.getConnection().close();
            // wait one second
            Thread.sleep(1000L);
        }
        catch (Exception ignored) {
            // Session might have disappeared on its own
        }
        // redirect back to this page
        response.sendRedirect("session-summary.jsp?close=success");
        return;
    }

    // paginator vars
    int numPages = (int)Math.ceil((double)sessionCount/(double)range);
    int curPage = (start/range) + 1;

    // Date dateFormatter for all dates on this page:
    DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT);
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Session Summary";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "session-summary.jsp"));
    pageinfo.setPageID("session-summary");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  if ("success".equals(request.getParameter("close"))) { %>

    <p class="jive-success-text">
    Session closed successfully.
    </p>

<%  } %>

Active Sessions: <b><%= sessionCount %></b>

<%  if (numPages > 1) { %>

    - Showing <%= (start+1) %>-<%= (start+range) %>

<%  } %>
</p>

<%  if (numPages > 1) { %>

    <p>
    Pages:
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

<p>
Below is a list of sessions on this server.
</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th>Name</th>
        <th>Resource</th>
        <th>Status</th>
        <th nowrap colspan="2">Presence</th>
        <th nowrap>Client IP</th>
        <th nowrap>Close Connection</th>
    </tr>
</thead>
<tbody>
    <%  // Get the iterator of sessions, print out session info if any exist.
        SessionResultFilter filter = new SessionResultFilter();
        filter.setStartIndex(start);
        filter.setNumResults(range);
        Iterator sessions = sessionManager.getSessions(filter);
        if (!sessions.hasNext()) {
    %>
        <tr>
            <td colspan="8">

                No Sessions

            </td>
        </tr>

    <%  } %>

    <%  int count = start;
        boolean current = false; // needed in session-row.jspf
        String linkURL = "session-details.jsp";
        while (sessions.hasNext()) {
            Session sess = (Session)sessions.next();
            count++;
    %>
        <%@ include file="session-row.jspf" %>

    <%  } %>

</tbody>
</table>
</div>

<%  if (numPages > 1) { %>

    <p>
    Pages:
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
List last updated: <%= dateFormatter.format(new Date()) %>
</p>

<jsp:include page="bottom.jsp" flush="true" />
