<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.UserManager,
                 java.text.DateFormat"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    long userID = ParamUtils.getLongParameter(request,"userID",-1L);

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-edit-form.jsp?userID=" + userID);
        return;
    }

    // Load the user object
    User user = userManager.getUser(userID);
%>

<jsp:include page="header.jsp" flush="true" />

<%  // Title of this page and breadcrumbs
    String title = "Email User";
    String[][] breadcrumbs = {
        { "Home", "main.jsp" },
        { "User Summary", "user-summary.jsp" },
        { "Edit User", "user-edit-form.jsp?userID="+userID },
        { title, "user-email.jsp?userID="+userID }
    };
%>
<jsp:include page="title.jsp" flush="true" />

<p>
Use the form below to send an email to the user.
</p>

<form action="user-email.jsp" name="emailform">
<input type="hidden" name="userID" value="<%= userID %>">

<div class="jive-table">
<table cellpadding="3" cellspacing="1" border="0" width="100%">
<tr>
    <th colspan="2">
        Email Message
    </th>
</tr>
<tr>
    <td class="jive-label">
        From:
    </td>
    <td>
        <%= pageUser.getUsername() %> &lt;<%= pageUser.getInfo().getEmail() %>&gt;
        (<a href="user-edit-form.jsp?userID=<%= user.getID() %>">Edit User</a>)
    </td>
</tr>
<tr>
    <td class="jive-label">
        TO:
    </td>
    <td>
        <input type="text" name="to" value="<%= user.getUsername() %> &lt;<%= user.getInfo().getEmail() %>&gt;"
         size="45" maxlength="150">
    </td>
</tr>
<tr>
    <td class="jive-label">
        CC:
    </td>
    <td>
        <input type="text" name="cc" value="" size="45" maxlength="150">
    </td>
</tr>
<tr>
    <td class="jive-label">
        Subject:
    </td>
    <td>
        <input type="text" name="subject" value="" size="55" maxlength="150" style="width:100%">
    </td>
</tr>
<tr>
    <td class="jive-label" valign="top">
        Message:
    </td>
    <td>
        <textarea name="body" cols="50" rows="7" wrap="virtual" style="width:100%"></textarea>
    </td>
</tr>
</table>
</div>

<br>

<input type="submit" value="Send!" name="send" onclick="alert('Not implemented yet');return false;">
<input type="submit" value="Cancel" name="cancel">
</form>

<script lang="JavaScript" type="text/javascript">
document.emailform.subject.focus();
</script>

<jsp:include page="footer.jsp" flush="true" />