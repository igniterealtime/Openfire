<%--
  -	$RCSfile$
  -	$Revision: 3710 $
  -	$Date: 2006-04-05 11:53:01 -0700 (Wed, 05 Apr 2006) $
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%--

This page summarize every active Session in the RTP Media Proxy

--%>

<%@ page import="java.util.GregorianCalendar,
                 java.util.List"
        %>
<%@ page import="org.jivesoftware.wildfire.mediaproxy.MediaProxyService" %>
<%@ page import="org.jivesoftware.wildfire.XMPPServer" %>
<%@ page import="org.jivesoftware.wildfire.mediaproxy.Session" %>
<%@ page import="org.jivesoftware.wildfire.mediaproxy.SmartSession" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%

    MediaProxyService mediaProxyService = XMPPServer.getInstance().getMediaProxyService();

    boolean stop = request.getParameter("stop") != null;

    if (stop) {
        mediaProxyService.stopAgents();
    }

%>

<html>
<head>
    <title>Active Sessions Summary</title>
    <meta name="pageID" content="media-proxy-session-summary"/>
</head>
<body>

<style type="text/css">
    .jive-current {
        font-weight: bold;
        text-decoration: none;
    }
</style>

<p>
    Sessions are Media Proxy Channels that controls packet relaying.
    The list below shows current sessions running and which user created the channel.
</p>

<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
            <tr>
                <th>&nbsp;</th>
                <th nowrap align="left" valign="middle">Creator</th>
                <th nowrap align="left" valign="middle">Port A</th>
                <th nowrap align="left" valign="middle">Port B</th>
                <th nowrap align="left" valign="middle">Server</th>
                <th nowrap align="left" valign="middle">Inactivity(secs)</th>
                <th nowrap align="left" valign="middle">Type</th>
            </tr>
        </thead>
        <tbody>

            <% // Print the list of agents
                List<Session> agents = mediaProxyService.getAgents();
                if (agents.isEmpty()) {
            %>
            <tr>
                <td align="center" colspan="7">
                    No active Agents
                </td>
            </tr>

            <%
                }
                int i = 0;
                for (Session proxySession : agents) {
                    i++;
            %>
            <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
                <td width="1%">
                    <%= i %>
                </td>
                <td width="20%" align="left" valign="middle">
                    <%=proxySession.getCreator()%>
                </td>
                <td width="10%" align="left" valign="middle">
                    <%=proxySession.getLocalPortA()%>
                </td>
                <td width="10%" align="left" valign="middle">
                    <%=proxySession.getLocalPortB()%>
                </td>
                <td width="10%" align="left" valign="middle">
                    <%=proxySession.getLocalhost()%>
                </td>
                <td width="20%" align="left" valign="middle">
                    <%=(new GregorianCalendar().getTimeInMillis() - proxySession.getTimestamp()) / 1000%>
                </td>
                <td width="10%">
                    <% if (proxySession instanceof SmartSession) { %>
                    Smart Session
                    <% }
                    else { %>
                    Fixed Session
                    <% } %>
                </td>
            </tr>

            <%
                }
            %>
        </tbody>
    </table>
    <form action="">
        <input type="submit" name="stop" value="Stop Active Sessions"/>
    </form>
</div>

</body>
</html>
