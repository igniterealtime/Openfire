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
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.muc.MultiUserChatServer,
                 java.util.Iterator"
%>

<%@ taglib uri="core" prefix="c"%>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<% admin.init(request, response, session, application, out ); %>


<%  // Get parameters
    String userJID = ParamUtils.getParameter(request,"userJID");
    boolean add = request.getParameter("add") != null;
    boolean delete = ParamUtils.getBooleanParameter(request,"delete");

	// Get muc server
    MultiUserChatServer mucServer = (MultiUserChatServer)admin.getServiceLookup().lookup(MultiUserChatServer.class);

    // Handle a save
    Map errors = new HashMap();
    if (add) {
        // do validation
        if (userJID == null || userJID.indexOf('@') == -1) {
            errors.put("userJID","userJID");
        }
        if (errors.size() == 0) {
            mucServer.addSysadmin(userJID);
            response.sendRedirect("muc-sysadmins.jsp?addsuccess=true");
            return;
        }
    }

    if (delete) {
        // Remove the user from the list of system administrators
        mucServer.removeSysadmin(userJID);
        // done, return
        response.sendRedirect("muc-sysadmins.jsp?deletesuccess=true");
        return;
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Group Chat Administrators";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Group Chat Admin", "muc-sysadmins.jsp"));
    pageinfo.setPageID("muc-sysadmin");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Below is the list of system administrators of the group chat service. System administrators can
enter any groupchat room and their permissions are the same as the room owner.
</p>

<%  if ("true".equals(request.getParameter("deletesuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        User removed from the list successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<%  if ("true".equals(request.getParameter("addsuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        User added to the list successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="muc-sysadmins.jsp" method="post">

<fieldset>
    <legend>Add Admin User</legend>
    <div>
    User (enter JID):
    <input type="text" name="userJID" size="30" maxlength="100" value="<%= (userJID != null ? userJID : "") %>">
    <input type="submit" name="add" value="Add">
    </div>
</fieldset>

</form>

<br>

<fieldset>
    <legend>Admin Users</legend>
    <div>
    <div class="jive-table" style="width:400px;">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th width="99%">User</th>
            <th width="1%">Remove</th>
        </tr>
    </thead>
    <tbody>
        <%  if (mucServer.getSysadmins().size() == 0) { %>

            <tr>
                <td colspan="2">
                    No admins specified, use the form above to add one.
                </td>
            </tr>

        <%  } %>

        <%  for (String user : mucServer.getSysadmins()) { %>

            <tr>
                <td width="99%">
                    <%= user %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-sysadmins.jsp?userJID=<%= user %>&delete=true"
                     title="Click to delete..."
                     onclick="return confirm('Are you sure you want to remove this user from the list?');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
                </td>
            </tr>

        <%  } %>
    </tbody>
    </table>
    </div>
    </div>
</fieldset>

<jsp:include page="bottom.jsp" flush="true" />