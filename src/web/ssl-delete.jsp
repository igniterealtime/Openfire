<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>


<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.UserManager,
                 org.jivesoftware.messenger.net.SSLConfig"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String alias = request.getParameter("alias");
    String type = request.getParameter("type");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("ssl-settings.jsp");
        return;
    }

    // Handle a cert delete:
    if (delete) {
        if ("client".equals(type)){
            SSLConfig.getTrustStore().deleteEntry(alias);
        }
        else {
            SSLConfig.getKeyStore().deleteEntry(alias);
        }
        SSLConfig.saveStores();
        response.sendRedirect("ssl-settings.jsp");
        return;
    }
%>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
    String title = "Delete SSL Certificate";
    String[][] breadcrumbs = {
        { "Home", "main.jsp" },
        { "SSL Setings", "ssl-settings.jsp" },
        { title, "ssl-delete.jsp?alias=" + alias + "&type=" + type}
    };
%>
<%@ include file="title.jsp" %>

<br>

<p>
Are you sure you want to delete the certificate for the alias
<b><%= alias %></b>
from the system?
</p>

<form action="ssl-delete.jsp">
<input type="hidden" name="alias" value="<%= alias %>">
<input type="hidden" name="type" value="<%= type %>">
<input type="submit" name="delete" value="Delete Certificate">
<input type="submit" name="cancel" value="Cancel">
</form>

<%@ include file="footer.jsp" %>
