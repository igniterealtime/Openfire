<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.UserManager,
                 java.text.DateFormat,
                 org.jivesoftware.messenger.auth.AuthFactory,
                 org.jivesoftware.messenger.user.*,
                 org.jivesoftware.admin.AdminPageBean"
%>
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
            response.sendRedirect("user-password-success.jsp?username=" + username);
            return;
        }
        else {
            errors = true;
        }
    }
%>

<c:set var="sbar" value="users" scope="page" />
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Change Password";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-password.jsp?username="+username));
    pageinfo.setSubPageID("user-edit-password");
    pageinfo.setExtraParams("username="+username);
%>
<c:set var="tab" value="pass" />
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%@ include file="user-tabs.jsp" %>
<br>

<%  if (errors) { %>

    <p class="jive-error-text">
    Error setting the password. Please make sure the password you enter is valid and
    matches the confirmation password.
    </p>

<%  } %>
<table cellpadding="3" cellspacing="1" border="0" width="600">
<form action="user-password.jsp" name="passform">
<tr><td colspan="2" class="text">
Use the form below to change the user's password
</td></tr>
</table>
<table cellpadding="3" cellspacing="1" border="0">

<input type="hidden" name="username" value="<%= username %>">


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
</table>


<br>

<input type="submit" value="Update Password" name="update">
<input type="submit" value="Cancel" name="cancel">
</form>

<script lang="JavaScript" type="text/javascript">
document.passform.password.focus();
</script>

<%@ include file="footer.jsp" %>
