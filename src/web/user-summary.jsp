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
                 org.jivesoftware.messenger.user.*,
                 java.util.Iterator,
                 org.jivesoftware.messenger.user.UserManager,
                 java.text.DateFormat,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.PresenceManager,
                 org.xmpp.packet.Presence"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "User Summary";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-summary.jsp"));
    pageinfo.setPageID("user-summary");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",15);

    // Get the user manager
    int userCount = webManager.getUserManager().getUserCount();

    // Get the presence manager
    PresenceManager presenceManager = webManager.getPresenceManager();

    // paginator vars
    int numPages = (int)Math.ceil((double)userCount/(double)range);
    int curPage = (start/range) + 1;

    // Formatter for dates
    DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM);
%>

<p>
Below is a list of users in the system.
</p>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        User deleted successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
Total Users: <%= webManager.getUserManager().getUserCount() %>,
<%  if (numPages > 1) { %>

    Showing <%= (start+1) %>-<%= (start+range) %>,

<%  } %>
Sorted by Username
</p>

<%  if (numPages > 1) { %>

    <p>
    Pages:
    [
    <%  for (int i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="user-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th>Online</th>
        <th>Username</th>
        <th>Name</th>
        <th>Created</th>
        <th>Edit</th>
        <th>Delete</th>
    </tr>
</thead>
<tbody>

<%  // Print the list of users
    Iterator users = webManager.getUserManager().users(start, range);
    if (!users.hasNext()) {
%>
    <tr>
        <td align="center" colspan="7">
            No users in the system.
        </td>
    </tr>

<%
    }
    int i = start;
    while (users.hasNext()) {
        User user = (User)users.next();
        i++;
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
        <td width="1%">
            <%= i %>
        </td>
        <td width="1%" align="center" valign="middle">
            <%  if (presenceManager.isAvailable(user)) {
                    Presence presence = presenceManager.getPresence(user);
            %>
                <% if (presence.getShow() == null) { %>
                <img src="images/user-green-16x16.gif" width="16" height="16" border="0" alt="Available">
                <% } %>
                <% if (presence.getShow() == Presence.Show.chat) { %>
                <img src="images/user-green-16x16.gif" width="16" height="16" border="0" alt="Available to Chat">
                <% } %>
                <% if (presence.getShow() == Presence.Show.away) { %>
                <img src="images/user-yellow-16x16.gif" width="16" height="16" border="0" alt="Away">
                <% } %>
                <% if (presence.getShow() == Presence.Show.xa) { %>
                <img src="images/user-yellow-16x16.gif" width="16" height="16" border="0" alt="Extended Away">
                <% } %>
                <% if (presence.getShow() == Presence.Show.dnd) { %>
                <img src="images/user-red-16x16.gif" width="16" height="16" border="0" alt="Do not Disturb">
                <% } %>

            <%  } else { %>

                <img src="images/user-clear-16x16.gif" width="16" height="16" border="0" alt="Offline">

            <%  } %>
        </td>
        <td width="30%">
            <a href="user-properties.jsp?username=<%= user.getUsername() %>"><%= user.getUsername() %></a>
        </td>
        <td width="40%">
            <%= user.getInfo().getName() %> &nbsp;
        </td>
        <td width="26%">
            <%= dateFormatter.format(user.getInfo().getCreationDate()) %>
        </td>
        <td width="1%" align="center">
            <a href="user-edit-form.jsp?username=<%= user.getUsername() %>"
             title="Click to edit..."
             ><img src="images/edit-16x16.gif" width="17" height="17" border="0"></a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="user-delete.jsp?username=<%= user.getUsername() %>"
             title="Click to delete..."
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
        </td>
    </tr>

<%
    }
%>
</tbody>
</table>
</div>

<%  if (numPages > 1) { %>

    <p>
    Pages:
    [
    <%  for (i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="user-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<jsp:include page="bottom.jsp" flush="true" />
