<%--
  -
  - Copyright (C) 2006-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>
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
    <fmt:param value="<a href=\"${redirectPage}\">"/>
    <fmt:param value="</a>"/>
</fmt:message>
</body>
</html>
