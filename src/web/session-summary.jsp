<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.Iterator,
                 org.jivesoftware.messenger.*,
                 java.util.Date,
                 org.jivesoftware.admin.*,
                 java.text.DateFormat" %>



<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Session Summary";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "session-summary.jsp"));
    pageinfo.setPageID("session-summary");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />



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
<%  if ("success".equals(request.getParameter("close"))) { %>

    <p class="jive-success-text">
    Session closed successfully.
    </p>

<%  } %>

<%
    String maxSession = "Unlimited";
    int maxSessions = -1;
    if(maxSessions != -1){
        maxSession = ""+maxSessions;
    }
%>
Active Sessions: <b><%= sessionCount %></b>,
Maximum Allowable Sessions: <%= maxSession %>

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
<table  cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeader"><td colspan="8" align="left">Current Sessions</td></tr>
<tr><td colspan="8" class="text">
Below is a list of sessions on this server.
</tr>



</table>
<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">

<tr class="tableHeaderBlue">
    <th>&nbsp;</th>
    <th>Name</th>
    <th>Resource</th>
    <th>Status</th>
    <th nowrap colspan="2">Presence (if authenticated)</th>
    <th nowrap>Client IP</th>
    <th nowrap>Close Connection</th>
</tr>

<%  // Get the iterator of sessions, print out session info if any exist.
    SessionResultFilter filter = new SessionResultFilter();
    filter.setStartIndex(start);
    filter.setNumResults(range);
    Iterator sessions = sessionManager.getSessions(filter);
    if (!sessions.hasNext()) {
%>
    <tr>
        <td colspan="6">

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

<p>
List last updated: <%= dateFormatter.format(new Date()) %>
</p>

<jsp:include page="bottom.jsp" flush="true" />
