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
                 org.jivesoftware.admin.AdminPageBean,
                 org.jivesoftware.admin.AdminConsole"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

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
    boolean stop = request.getParameter("stop") != null;
    boolean restart = request.getParameter("restart") != null;

    // Handle stops & restarts
    if (stop) {
      response.sendRedirect("server-stopped.jsp");
      return;
    }
    else if (restart) {
      response.sendRedirect("server-stopped.jsp?restart=Restart");
      return;
    }
%>

<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("index.title");
    pageinfo.setTitle(title);
    pageinfo.setPageID("server-settings");
%>

<%@ include file="top.jsp" %>

<jsp:include page="title.jsp" flush="true" />

<p>
<fmt:message key="index.title.info" />
</p>

<script lang="JavaScript" type="text/javascript">
    var checked = false;
    function checkClick() {
        if (checked) { return false; }
        else { checked = true; return true; }
    }
</script>

<form action="index.jsp" onsubmit="return checkClick();">
<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2"><fmt:message key="index.properties" /></th>
    </tr>
</thead>
<tbody>

    <%  if (serverOn) { %>

         <tr>
            <td class="c1"><fmt:message key="index.uptime" /></td>
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

                <% if (webManager.getXMPPServer().isStandAlone()){ %>
                        &nbsp;&nbsp;<input type="submit" value="<fmt:message key="global.stop" />" name="stop" <%= ((serverOn) ? "" : "disabled") %>>
                    <% if (webManager.getXMPPServer().isRestartable()){ %>
                        &nbsp;&nbsp;<input type="submit" value="<fmt:message key="global.restart" />" name="restart" <%= ((serverOn) ? "" : "disabled") %>>
                    <% } %>
                <% } %>

            </td>
        </tr>

    <%  } %>

    <tr>
        <td class="c1"><fmt:message key="index.version" /></td>
        <td class="c2">
            <%= AdminConsole.getAppName() %>
            <%= AdminConsole.getVersionString() %>
        </td>
    </tr>
    <tr>
        <td class="c1"><fmt:message key="index.home" /></td>
        <td class="c2">
            <%= JiveGlobals.getMessengerHome() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="index.server_name" />
        </td>
        <td class="c2">
            ${webManager.serverInfo.name}
        </td>
    </tr>
</tbody>
<thead>
    <tr>
        <th colspan="2"><fmt:message key="index.server_port" /></th>
    </tr>
</thead>
<tbody>
    <%  int i=0; %>
    <c:forEach var="port" items="${webManager.serverInfo.serverPorts}">
        <%  i++; %>
        <tr>
            <td class="c1">
                <%= i %>: <fmt:message key="index.server_ip" />
            </td>
            <td class="c2">
                ${port.IPAddress}:${port.port},
                <c:choose>
                    <c:when test="${empty port.securityType}">
                        <fmt:message key="index.port_type" />
                    </c:when>
                    <c:otherwise>
                        <c:choose>
                            <c:when test="${port.securityType == 'TLS'}">
                                <fmt:message key="index.port_type1" />
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
                <nobr>&nbsp;&nbsp;&nbsp; <fmt:message key="index.domain_name" /></nobr>
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
        <th colspan="2"><fmt:message key="index.environment" /></th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1"><fmt:message key="index.jvm" /></td>
        <td class="c2">
            <%= System.getProperty("java.version") %> <%= System.getProperty("java.vendor") %>
        </td>
    </tr>
    <tr>
        <td class="c1"><fmt:message key="index.app" /></td>
        <td class="c2">
            <%= application.getServerInfo() %>
        </td>
    </tr>
    <tr>
        <td class="c1"><fmt:message key="index.os" /></td>
        <td class="c2">
            <%= System.getProperty("os.name") %> / <%= System.getProperty("os.arch") %>
        </td>
    </tr>
    <tr>
        <td class="c1"><fmt:message key="index.local" /></td>
        <td class="c2">
            <%= JiveGlobals.getLocale() %> / <%= JiveGlobals.getTimeZone().getDisplayName(JiveGlobals.getLocale()) %>
            (<%= (JiveGlobals.getTimeZone().getRawOffset()/1000/60/60) %> GMT)
        </td>
    </tr>
</tbody>
</table>
</div>
</form>
<br>

<form action="server-props.jsp">
<input type="submit" value="<fmt:message key="global.edit_properties" />">
</form>

<jsp:include page="bottom.jsp" flush="true" />