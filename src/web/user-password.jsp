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
                 org.jivesoftware.messenger.user.UserManager,
                 java.text.DateFormat,
                 org.jivesoftware.messenger.auth.AuthFactory,
                 org.jivesoftware.messenger.user.*,
                 org.jivesoftware.admin.AdminPageBean"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean update = request.getParameter("update") != null;
    String username = ParamUtils.getParameter(request,"username");
    String password = ParamUtils.getParameter(request,"password");
    String passwordConfirm = ParamUtils.getParameter(request,"passwordConfirm");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-properties.jsp?username=" + username);
        return;
    }

    // Load the user object
    User user = admin.getUserManager().getUser(username);

    // Handle a password update:
    boolean errors = false;
    if (update) {
        // Validate the passwords:
        if (password != null && passwordConfirm != null && password.equals(passwordConfirm)) {
            user.setPassword(password);
            // Done, so redirect
            response.sendRedirect("user-password.jsp?success=true&username=" + username);
            return;
        }
        else {
            errors = true;
        }
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Change Password";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-password.jsp?username="+username));
    pageinfo.setSubPageID("user-password");
    pageinfo.setExtraParams("username="+username);
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  if (errors) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Error setting the password. Please make sure the password you enter is valid and
        matches the confirmation password.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (request.getParameter("success") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Password updated successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
Use the form below to change the user's password.
</p>

<form action="user-password.jsp" name="passform" method="post">
<input type="hidden" name="username" value="<%= username %>">

<fieldset>
    <legend>Change Password</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td class="c1">
                User ID:
            </td>
            <td class="c2">
                <%= user.getUsername() %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                Username:
            </td>
            <td class="c2">
                <%= user.getUsername() %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                New Password:
            </td>
            <td clas="c2">
                <input type="password" name="password" value="" size="20" maxlength="50">
            </td>
        </tr>
        <tr>
            <td class="c1">
                Confirm New Password:
            </td>
            <td class="c2">
                <input type="password" name="passwordConfirm" value="" size="20" maxlength="50">
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Update Password" name="update">
<input type="submit" value="Cancel" name="cancel">
</form>

<script lang="JavaScript" type="text/javascript">
document.passform.password.focus();
</script>

<jsp:include page="bottom.jsp" flush="true" />
