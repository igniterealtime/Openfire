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
                 java.text.DateFormat,
                 java.util.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.messenger.group.Group,
                 org.jivesoftware.messenger.group.GroupNotFoundException"
    errorPage="error.jsp"
%>

<%@ taglib uri="core" prefix="c"%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String groupName = ParamUtils.getParameter(request,"group");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("group-summary.jsp");
        return;
    }

    // Handle a delete
    if (delete) {
        response.sendRedirect("group-delete.jsp?group=" + groupName);
        return;
    }

    // Load the group object
    Group group = null;
    try {
        group = webManager.getGroupManager().getGroup(groupName);
    }
    catch (GroupNotFoundException gnfe) {
        group = webManager.getGroupManager().getGroup(groupName);
    }

    PresenceManager presenceManager = webManager.getPresenceManager();

    // Date formatter for dates
    DateFormat formatter = DateFormat.getDateInstance(DateFormat.MEDIUM);
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Group Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "group-properties.jsp?group="+groupName));
    pageinfo.setSubPageID("group-properties");
    pageinfo.setExtraParams("group="+groupName);
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Below is a summary of the group. To edit properties, click the "Edit" button below.
</p>

<%  if (request.getParameter("success") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        New group created successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (request.getParameter("editsuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Group updated successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">
            Group Properties
        </th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">
            Name:
        </td>
        <td>
            <%= group.getName() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            Description:
        </td>
        <td>
            <%  if (group.getDescription() == null) { %>

                &nbsp;

            <%  } else { %>

                <%= group.getDescription() %>

            <%  } %>
        </td>
    </tr>
</tbody>
</table>
</div>

<br><br>

<form action="group-edit-form.jsp">
<input type="hidden" name="group" value="<%= group.getName() %>">
<input type="submit" value="Edit Properties">
</form>

<jsp:include page="bottom.jsp" flush="true" />