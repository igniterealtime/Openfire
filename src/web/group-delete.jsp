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
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.group.Group"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String groupName = ParamUtils.getParameter(request,"group");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("group-properties.jsp?group=" + groupName);
        return;
    }

    // Load the group object
    Group group = webManager.getGroupManager().getGroup(groupName);

    // Handle a group delete:
    if (delete) {
        // Delete the group
        webManager.getGroupManager().deleteGroup(group);
        // Done, so redirect
        response.sendRedirect("group-summary.jsp?deletesuccess=true");
        return;
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Delete Group";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "group-delete.jsp?group="+groupName));
    pageinfo.setSubPageID("group-delete");
    pageinfo.setExtraParams("group="+groupName);
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Are you sure you want to delete the group
<b><a href="group-properties.jsp?group=<%= group.getName() %>"><%= group.getName() %></a></b>
from the system?
</p>

<form action="group-delete.jsp">
<input type="hidden" name="group" value="<%= groupName %>">
<input type="submit" name="delete" value="Delete Group">
<input type="submit" name="cancel" value="Cancel">
</form>

<jsp:include page="bottom.jsp" flush="true" />
