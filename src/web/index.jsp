<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.messenger.user.*,
                 java.util.*,
                 java.text.*,
                 org.jivesoftware.admin.AdminPageBean"

%>

<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>

<%-- Define page bean for header and sidebar --%>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%  // Simple logout code
    if ("true".equals(request.getParameter("logout"))) {
        session.removeAttribute("jive.admin.authToken");
        response.sendRedirect("index.jsp");
        return;
    }
%>

<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters //
    boolean serverOn = (webManager.getXMPPServer() != null);
%>

<%  // Title of this page and breadcrumbs
    String title = "Server Properties";
    pageinfo.setTitle(title);
    pageinfo.setPageID("server-settings");
%>

<%@ include file="top.jsp" %>

<jsp:include page="title.jsp" flush="true" />

<p>
Below are properties for this server. Click the "Edit Properties" button below to change
some of the server settings. Some settings can not be changed.
</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">Server Properties</th>
    </tr>
</thead>
<tbody>

    <%  if (serverOn) { %>

         <tr>
            <td class="c1">Server Uptime:</td>
            <td>
                <%  DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
                    long now = System.currentTimeMillis();
                    long lastStarted = webManager.getXMPPServer().getServerInfo().getLastStarted().getTime();
                    long uptime = (now - lastStarted) / 1000L;
                    String uptimeDisplay = null;
                    if (uptime < 60) {
                        uptimeDisplay = "Less than 1 minute";
                    }
                    else if (uptime < 60*60) {
                        long mins = uptime / (60);
                        uptimeDisplay = "Approx " + mins + ((mins==1) ? " minute" : " minutes");
                    }
                    else if (uptime < 60*60*24) {
                        long days = uptime / (60*60);
                        uptimeDisplay = "Approx " + days + ((days==1) ? " hour" : " hours");
                    }
                %>

                <%  if (uptimeDisplay != null) { %>

                    <%= uptimeDisplay %> --

                <%  } %>

                started <%= formatter.format(webManager.getXMPPServer().getServerInfo().getLastStarted()) %>
            </td>
        </tr>

    <%  } %>

    <tr>
        <td class="c1">Version:</td>
        <td class="c2">
            <fmt:message key="title" bundle="${lang}" />
            <%= webManager.getXMPPServer().getServerInfo().getVersion().getVersionString() %>
        </td>
    </tr>
    <tr>
        <td class="c1">Messenger Home:</td>
        <td class="c2">
            <%= JiveGlobals.getMessengerHome() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            Server Name:
        </td>
        <td class="c2">
            <c:out value="${webManager.serverInfo.name}" />
        </td>
    </tr>
    <c:if test="${!empty webManager.multiUserChatServer}">
        <tr>
            <td class="c1">
                Group Chat Service Name:
            </td>
            <td class="c2">
                <c:out value="${webManager.multiUserChatServer.serviceName}" />
            </td>
        </tr>
    </c:if>
</tbody>
<thead>
    <tr>
        <th colspan="2">Server Ports</th>
    </tr>
</thead>
<tbody>
    <%  int i=0; %>
    <c:forEach var="port" items="${webManager.serverInfo.serverPorts}">
        <%  i++; %>
        <tr>
            <td class="c1">
                <%= i %>: IP:Port, Security:
            </td>
            <td class="c2">
                <c:out value="${port.IPAddress}" />:<c:out value="${port.port}" />,
                <c:choose>
                    <c:when test="${empty port.securityType}">
                        NORMAL
                    </c:when>
                    <c:otherwise>
                        <c:choose>
                            <c:when test="${port.securityType == 'TLS'}">
                                TLS (SSL)
                            </c:when>
                            <c:otherwise>
                                <c:out value="${port.securityType}" />
                            </c:otherwise>
                        </c:choose>
                    </c:otherwise>
                </c:choose>
            </td>
        </tr>
        <tr valign="top">
            <td class="c1">
                <nobr>&nbsp;&nbsp;&nbsp; Domain Name(s):</nobr>
            </td>
            <td class="c2">
                <c:set var="sep" value="" />
                <c:forEach var="name" items="${port.domainNames}">
                    <c:out value="${sep}" /><c:out value="${name}" />
                    <c:set var="set" value=", " />
                </c:forEach>
            </td>
        </tr>
    </c:forEach>
</tbody>
<thead>
    <tr>
        <th colspan="2">Environment</th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">JVM Version and Vendor:</td>
        <td class="c2">
            <%= System.getProperty("java.version") %> <%= System.getProperty("java.vendor") %>
        </td>
    </tr>
    <tr>
        <td class="c1">Appserver:</td>
        <td class="c2">
            <%= application.getServerInfo() %>
        </td>
    </tr>
    <tr>
        <td class="c1">OS / Hardware:</td>
        <td class="c2">
            <%= System.getProperty("os.name") %> / <%= System.getProperty("os.arch") %>
        </td>
    </tr>
    <tr>
        <td class="c1">Locale / Timezone:</td>
        <td class="c2">
            <%= JiveGlobals.getLocale() %> / <%= JiveGlobals.getTimeZone().getDisplayName(JiveGlobals.getLocale()) %>
            (<%= (JiveGlobals.getTimeZone().getRawOffset()/1000/60/60) %> GMT)
        </td>
    </tr>
</tbody>
</table>
</div>
<br>

<form action="server-props.jsp">
<input type="submit" value="Edit Properties">
</form>

<jsp:include page="bottom.jsp" flush="true" />