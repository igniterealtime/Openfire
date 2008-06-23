<%--
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.openfire.clearspace.ClearspaceManager"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.StringUtils" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%

    ClearspaceManager manager = ClearspaceManager.getInstance();

    // Checks if CS and OF are currently connected
    boolean connectedOF = manager.isOpenfireConnected();

    // Get the information to build the redirect request
    String username = webManager.getUser().getUsername();
    String secret = manager.getSharedSecret();
    String uri = manager.getConnectionURI();
    String nonce = manager.getNonce();

    // If all the information is OK
    if (connectedOF && username != null && secret != null && uri != null && nonce != null) {
        // Build de URL and send the redirect to the admin console of Clearspace.
        response.sendRedirect(uri + "admin/login.jsp?login=true&username=" + username + "&secret=" +
                StringUtils.hash(username + ":" + secret + ":" + nonce) + "&nonce=" + nonce);
        return;
    }

%>

<html>
<head>
<title><fmt:message key="clearspace.admin.title"/></title>
<meta name="pageID" content="clearspace-admin"/>
</head>

<body>

<div class="error">
    <fmt:message key="clearspace.admin.error.disconnected"/>
</div>

<fmt:message key="clearspace.admin.disconnected.description">
    <fmt:param value="<%= "<a href='clearspace-status.jsp'>" %>" />
    <fmt:param value="<%= "</a>" %>" />
</fmt:message>

</body>
</html>