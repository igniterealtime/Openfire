<%@ taglib uri="core" prefix="c"%>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>


<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.*,
                 org.jivesoftware.admin.*"
%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>


<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String username = ParamUtils.getParameter(request,"username");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-properties.jsp?username=" + username);
        return;
    }

    // Load the user object
    User user = webManager.getUserManager().getUser(username);

    // Handle a user delete:
    if (delete) {
        webManager.getUserManager().deleteUser(user);
        // Deleted your own user account, force login
        if (username.equals(webManager.getAuthToken().getUsername())){
            session.removeAttribute("jive.admin.authToken");
            response.sendRedirect("login.jsp");
        }
        else {
            // Done, so redirect
            response.sendRedirect("user-delete-success.jsp");
        }
        return;
    }
%>




<c:set var="sbar" value="users" scope="page" />

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Change Password";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-password.jsp?username="+username));
    pageinfo.setSubPageID("user-delete");
    pageinfo.setExtraParams("username="+username);
%>
<c:set var="tab" value="delete" />
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />



<%@ include file="user-tabs.jsp" %>

<p>
Are you sure you want to delete the user
<b><a href="user-properties.jsp?username=<%= user.getUsername() %>"><%= user.getUsername() %></a></b>
from the system?
</p>

<c:if test="${admin.user.ID == param.userID}">
    <p class="jive-warning-text">
    Warning! You are about to delete your <b>own</b> user account. Are you sure you want to
    do this? Doing so will log you out of the system immediately.
    </p>
</c:if>

<form action="user-delete.jsp">
<input type="hidden" name="username" value="<%= username %>">
<input type="submit" name="delete" value="Delete User">
<input type="submit" name="cancel" value="Cancel">
</form>

<%@ include file="footer.jsp" %>
