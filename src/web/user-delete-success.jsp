<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>


<%@ page import="org.jivesoftware.util.*,
                     org.jivesoftware.messenger.user.UserManager"
%>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>



<!-- Define BreadCrumbs -->
<c:set var="title" value="User Delete Successfully"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="user-delete-success.jsp" />
<jsp:include page="top.jsp" flush="true" />

<p>
The user has been deleted successfully.
</p>

<form action="user-summary.jsp">
<input type="submit" name="" value="Go To User Summary">
</form>

<jsp:include page="bottom.jsp" flush="true" />
