<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.UserManager,
                 java.text.DateFormat,
                 org.jivesoftware.messenger.auth.AuthFactory,
                 org.jivesoftware.messenger.user.*"
%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />


<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean update = request.getParameter("update") != null;
    long userID = ParamUtils.getLongParameter(request,"userID",-1L);
    String password = ParamUtils.getParameter(request,"password");
    String passwordConfirm = ParamUtils.getParameter(request,"passwordConfirm");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-properties.jsp?userID=" + userID);
        return;
    }

    // Load the user object
    User user = admin.getUserManager().getUser(userID);

    // Handle a password update:
    boolean errors = false;
    if (update) {
        // Validate the passwords:
        if (password != null && passwordConfirm != null && password.equals(passwordConfirm)) {
            user.setPassword(password);
            // Done, so redirect
            response.sendRedirect("user-password-success.jsp?userID=" + userID);
            return;
        }
        else {
            errors = true;
        }
    }
%>

<c:set var="sbar" value="users" scope="page" />




<!-- Define BreadCrumbs -->
<c:set var="title" value="User Password"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="User Summary" value="user-summary.jsp" />
<c:set target="${breadcrumbs}" property="User Properties" value="user-properties.jsp?userID=${param.userID}" />
<c:set target="${breadcrumbs}" property="${title}" value="user-password.jsp?userID=${param.userID}" />
<c:set var="tab" value="pass" />
<jsp:include page="top.jsp" flush="true" />


<%@ include file="user-tabs.jsp" %>
<br>

<%  if (errors) { %>

    <p class="jive-error-text">
    Error setting the password. Please make sure the password you enter is valid and
    matches the confirmation password.
    </p>

<%  } %>
<form action="user-password.jsp" name="passform">
<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeaderBlue"><td colspan="2" align="center">Change Password For <%= user.getUsername() %></td></tr>
<tr><td colspan="2" class="text">
Use the form below to change the user's password
</td></tr>


<input type="hidden" name="userID" value="<%= userID %>">


<tr class="jive-even">
    <td class="jive-label">
        User ID:
    </td>
    <td>
        <%= user.getID() %>
    </td>
</tr>
<tr class="jive-odd">
    <td>
        Username:
    </td>
    <td>
        <%= user.getUsername() %>
    </td>
</tr>
<tr class="jive-even">
    <td class="jive-label">
        New Password:
    </td>
    <td>
        <input type="password" name="password" value="" size="20" maxlength="50">
    </td>
</tr>
<tr class="jive-odd">
    <td>
        Confirm New Password:
    </td>
    <td>
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
