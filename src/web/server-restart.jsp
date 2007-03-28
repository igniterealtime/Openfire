<%@ page import="org.jivesoftware.util.ParamUtils"
         errorPage="error.jsp"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%--
  Created by IntelliJ IDEA.
  User: gato
  Date: Nov 13, 2006
  Time: 2:01:11 PM
  To change this template use File | Settings | File Templates.
--%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%
    // Get redirection page (after login)
    String redirectPage = ParamUtils.getParameter(request, "page");
    // Build redirection URL
    StringBuilder url = new StringBuilder();
    url.append("login.jsp?url=").append(redirectPage);
    url.append("&username=").append(admin.getAuthToken().getUsername());

    XMPPServer.getInstance().restartHTTPServer();
%>

<html>
<head>
    <title><fmt:message key="server-restart.title"/></title>
    <meta name="pageID" content="server-settings"/>
    <meta http-equiv="refresh" content="5; URL=<%=url.toString()%>">
</head>
  <body><fmt:message key="server-restart.info">
            <fmt:param value="<%= "<a href='" + url.toString() + "'>" %>" />
            <fmt:param value="<%= "</a>" %>" />
        </fmt:message>
  </body>
</html>