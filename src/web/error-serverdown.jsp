<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
    String title = "Jive Messenger Server Down";
    String[][] breadcrumbs = {
        { "Home", "index.jsp" },
        { title, "error-serverdown.jsp" }
    };
%>

<p>
Jive Messenger is currently down. Please see the
<a href="server-status.jsp">server status</a> page for more details.
</p>

<%@ include file="footer.jsp" %>