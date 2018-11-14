<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="error.jsp" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"/>
<% admin.init( request, response, session, application, out ); %>

<%
    // Log the event
    admin.logEvent( "restarted the http server", null );
    XMPPServer.getInstance().restartHTTPServer();
%>
<c:set var="redirectPage" value="login.jsp?url=${fn:escapeXml(page)}&username=${admin.authToken.username}"/>
<html>
<head>
    <title><fmt:message key="server-restart.title"/></title>
    <meta name="pageID" content="server-settings"/>
    <meta http-equiv="refresh" content="5; URL=${redirectPage}">
</head>
<body><fmt:message key="server-restart.info">
    <fmt:param value="<a href=\"$redirectPage}\">"/>
    <fmt:param value="</a>"/>
</fmt:message>
</body>
</html>
