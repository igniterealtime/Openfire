<%@ taglib uri="core" prefix="c"%>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.UserManager,
                 org.jivesoftware.messenger.user.*,
                 java.text.DateFormat"
%>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<!-- Define BreadCrumbs -->
<fmt:message key="title" bundle="${lang}" var="t" />
<c:set var="title"value="${t} Admin"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="User Summary" value="user-summary.jsp" />
<c:set target="${breadcrumbs}" property="User Properties" value="user-edit-form.jsp?userID=${param.userID}" />
<c:set target="${breadcrumbs}" property="${title}" value="user-password?userID=${param.userID}" />
<jsp:include page="top.jsp" flush="true" />
<c:set var="tab" value="pass" />


<%  // Get parameters //
    String username = ParamUtils.getParameter(request,"username");

    // Load the user object
    User user = admin.getUserManager().getUser(username);
%>



<br>

<%@ include file="user-tabs.jsp" %>

<p>
User password updated successfully.
</p>

<form action="user-properties.jsp">
<input type="hidden" name="username" value="<%= user.getUsername() %>">
<input type="submit" value="Back to user properties">
</form>

<%@ include file="footer.jsp" %>
