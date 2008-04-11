<%--
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.openfire.clearspace.ClearspaceManager,
                 org.jivesoftware.util.StringUtils"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%

    boolean connected = ClearspaceManager.getInstance().testConnection();

    String username = webManager.getUser().getUsername();
    String secret = ClearspaceManager.getInstance().getSharedSecret();
    String uri = ClearspaceManager.getInstance().getConnectionURI();
    String nonce = ClearspaceManager.getInstance().getNonce();

    if (connected && username != null && secret != null && uri != null) {
        // Redirect to the admin console of Clearspace.
        response.sendRedirect(uri + "admin/login.jsp?login=true&username=" + username + "&secret=" +
                StringUtils.hash(username + ":" + secret + ":" + nonce) + "&nonce=" + nonce);
        return;
    }

%>

<html>
<head>
<title><fmt:message key="clearspace.admin.title"/></title>
<meta name="pageID" content="clearspace-admin"/>

<script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
</head>

<style type="text/css">

.light-gray-border {
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 1px 1px 1px;
    padding: 5px;
	-moz-border-radius: 3px;
}

.table-header {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 0 1px 0;
    padding: 5px;
}

.table-header-align-right {
    text-align: right;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 0 1px 0;
    padding: 5px;
}

.row-header {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 1px 1px 0;
    padding: 5px;
}

.table-header-left {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 0 1px 1px;
    padding: 5px;

}

.table-header-right {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 1px 1px 0;
    padding: 5px;
}

.line-bottom-border {
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 9pt;
    border-color: #e3e3e3;
    border-style: solid;
    border-width: 0 0 1px 0;
    padding: 5px;
}
</style>
<body>

    <h3><fmt:message key="clearspace.admin.notconnected.title"/></h3>
    <p><fmt:message key="clearspace.admin.notconnected.description"/></p>

</body>
</html>