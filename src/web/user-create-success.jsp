<%@ taglib uri="core" prefix="c"%>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 org.jivesoftware.messenger.user.*,
                 org.jivesoftware.messenger.user.UserAlreadyExistsException"
%>
<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>


<%  // Get parameters
    String username = ParamUtils.getParameter(request,"username");

    // Load user object
    User user = admin.getUserManager().getUser(username);

    // Handle button clicks:
    if (request.getParameter("details") != null) {
        response.sendRedirect("user-properties.jsp?username=" + username);
        return;
    }

    if (request.getParameter("new") != null) {
        response.sendRedirect("user-create.jsp");
        return;
    }
%>


<!-- Define BreadCrumbs -->
<fmt:message key="title" bundle="${lang}" var="t" />
<c:set var="title" value="${t} Admin"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="user-create.jsp" />
<jsp:include page="top.jsp" flush="true" />



<p>
User created successfully!
</p>

<form action="user-create-success.jsp">
<input type="hidden" name="username" value="<%= username %>">

<center>
<input type="submit" name="details" value="Go to User Details">
<input type="submit" name="new" value="Create Another User">
</center>

</form>

<jsp:include page="bottom.jsp" flush="true" />
