<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>


<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.*"
%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<% admin.init(request, response, session, application, out ); %>


<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    long userID = ParamUtils.getLongParameter(request,"userID",-1L);

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-properties.jsp?userID=" + userID);
        return;
    }

    // Load the user object
    User user = admin.getUserManager().getUser(userID);

    // Handle a user delete:
    if (delete) {
        admin.getUserManager().deleteUser(user);
        // Deleted your own user account, force login
        if (userID == admin.getAuthToken().getUserID()){
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

<!-- Define BreadCrumbs -->
<c:set var="title" value="Delete User"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="User Summary" value="user-summary.jsp" />
<c:set target="${breadcrumbs}" property="User Properties" value="user-properties.jsp?userID=${param.userID}" />
<c:set target="${breadcrumbs}" property="${title}" value="user-delete.jsp?userID=${param.userID}" />
<c:set var="tab" value="delete" />
<%@ include file="top.jsp" %>



<%@ include file="user-tabs.jsp" %>

<p>
Are you sure you want to delete the user
<b><a href="user-properties.jsp?userID=<%= user.getID() %>"><%= user.getUsername() %></a></b>
from the system?
</p>

<c:if test="${admin.user.ID == param.userID}">
    <p class="jive-warning-text">
    Warning! You are about to delete your <b>own</b> user account. Are you sure you want to
    do this? Doing so will log you out of the system immediately.
    </p>
</c:if>

<form action="user-delete.jsp">
<input type="hidden" name="userID" value="<%= userID %>">
<input type="submit" name="delete" value="Delete User">
<input type="submit" name="cancel" value="Cancel">
</form>

<%@ include file="footer.jsp" %>
