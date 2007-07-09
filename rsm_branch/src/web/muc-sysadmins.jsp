<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.openfire.muc.MultiUserChatServer"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt" prefix="fmt"%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    String userJID = ParamUtils.getParameter(request,"userJID");
    boolean add = request.getParameter("add") != null;
    boolean delete = ParamUtils.getBooleanParameter(request,"delete");

	// Get muc server
    MultiUserChatServer mucServer = webManager.getMultiUserChatServer();

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

<html>
<head>
<title><fmt:message key="groupchat.admins.title"/></title>
<meta name="pageID" content="muc-sysadmin"/>
<meta name="helpPage" content="edit_group_chat_service_administrators.html"/>
</head>
<body>

<p>
<fmt:message key="groupchat.admins.introduction" />
</p>

<%  if ("true".equals(request.getParameter("deletesuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="groupchat.admins.user_removed" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if ("true".equals(request.getParameter("addsuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="groupchat.admins.user_added" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="groupchat.admins.error_adding" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>


<!-- BEGIN 'Administrators' -->
<form action="muc-sysadmins.jsp?add" method="post">
	<div class="jive-contentBoxHeader">
		<fmt:message key="groupchat.admins.legend" />
	</div>
	<div class="jive-contentBox">
		<label for="userJIDtf"><fmt:message key="groupchat.admins.label_add_admin" /></label>
		<input type="text" name="userJID" size="30" maxlength="100" value="<%= (userJID != null ? userJID : "") %>"
		 id="userJIDtf">
		<input type="submit"s value="<fmt:message key="groupchat.admins.add" />">
		<br><br>

		<div class="jive-table" style="width:400px;">
			<table cellpadding="0" cellspacing="0" border="0" width="100%">
			<thead>
				<tr>
					<th width="99%"><fmt:message key="groupchat.admins.column_user" /></th>
					<th width="1%" nowrap><fmt:message key="groupchat.admins.column_remove" /></th>
				</tr>
			</thead>
			<tbody>
				<%  if (mucServer.getSysadmins().size() == 0) { %>

					<tr>
						<td colspan="2">
							<fmt:message key="groupchat.admins.no_admins" />
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
							 title="<fmt:message key="groupchat.admins.dialog.title" />"
							 onclick="return confirm('<fmt:message key="groupchat.admins.dialog.text" />');"
							 ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
						</td>
					</tr>

				<%  } %>
			</tbody>
			</table>
		</div>
	</div>
</form>
<!-- END 'Administrators' -->


</body>
</html>