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
                 java.util.*,
                 java.text.DateFormat,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.group.*,
                 java.net.URLDecoder,
                 java.net.URLEncoder"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Group Summary";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "group-summary.jsp"));
    pageinfo.setPageID("group-summary");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",15);

    // Get the user manager
    int groupCount = webManager.getGroupManager().getGroupCount();

    // paginator vars
    int numPages = (int)Math.ceil((double)groupCount/(double)range);
    int curPage = (start/range) + 1;
%>             

<p>
Below is a list of the groups in the system.
</p>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Group deleted successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
Total Groups: <%= webManager.getGroupManager().getGroupCount() %>,
<%  if (numPages > 1) { %>

    Showing <%= (start+1) %>-<%= (start+range) %>,

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
        <a href="group-summary.jsp?start=<%= (i*range) %>"
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
        <th>Name</th>
        <th>Members</th>
        <th>Admins</th>
        <th>Edit</th>
        <th>Delete</th>
    </tr>
</thead>
<tbody>

<%  // Print the list of groups
    Collection<Group> groups = webManager.getGroupManager().getGroups(start, range);
    if (groups.isEmpty()) {
%>
    <tr>
        <td align="center" colspan="6">
            No groups in the system.
        </td>
    </tr>

<%
    }
    int i = start;
    for (Group group : groups) {
        String groupName = URLEncoder.encode(group.getName(), "UTF-8");
        i++;
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
        <td width="1%" valign="top">
            <%= i %>
        </td>
        <td width="60%">
            <a href="group-edit.jsp?group=<%= groupName %>"><%= group.getName() %></a>
            <% if (group.getDescription() != null) { %>
            <br>
                <span class="jive-description">
                <%= group.getDescription() %>
                </span>
             <% } %>
        </td>
        <td width="10%" align="center">
            <%= group.getMembers().size() %>
        </td>
        <td width="10%" align="center">
            <%= group.getAdmins().size() %>
        </td>
        <td width="1%" align="center">
            <a href="group-edit.jsp?group=<%= groupName %>"
             title="Click to edit..."
             ><img src="images/edit-16x16.gif" width="17" height="17" border="0"></a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="group-delete.jsp?group=<%= groupName %>"
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
        <a href="group-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<jsp:include page="bottom.jsp" flush="true" />
