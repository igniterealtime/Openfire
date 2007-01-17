<%--
  -	$Revision: $
  -	$Date: $
  -
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.wildfire.XMPPServer" %>
<%@ page import="org.jivesoftware.wildfire.mediaproxy.MediaProxyService" %>
<%@ page import="org.jivesoftware.wildfire.mediaproxy.MediaProxySession" %>
<%@ page import="org.jivesoftware.wildfire.mediaproxy.RelaySession" %>
<%@ page import="java.util.Collection" %>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%

    MediaProxyService mediaProxyService = XMPPServer.getInstance().getMediaProxyService();

    boolean stop = request.getParameter("stop") != null;
    if (stop) {
        mediaProxyService.stopAgents();
    }

    boolean save = request.getParameter("update") != null;
    boolean success = false;

    long keepAliveDelay = 3600;
    long lifetime = 0;
    int minPort = 10000;
    int maxPort = 20000;
    boolean enabled = true;

    if (save) {
        keepAliveDelay = ParamUtils.getLongParameter(request, "idleTimeout", keepAliveDelay);
        if (keepAliveDelay > 50) {
            mediaProxyService.setKeepAliveDelay(keepAliveDelay * 1000);
            JiveGlobals
                    .setProperty("mediaproxy.idleTimeout", String.valueOf(keepAliveDelay));
        }

        lifetime = ParamUtils.getLongParameter(request, "lifetime", lifetime);
        if (lifetime > 0) {
            mediaProxyService.setLifetime(lifetime);
            JiveGlobals
                    .setProperty("mediaproxy.lifetime", String.valueOf(lifetime));
        }

        minPort = ParamUtils.getIntParameter(request, "minport", minPort);
        maxPort = ParamUtils.getIntParameter(request, "maxport", maxPort);
        enabled = ParamUtils.getBooleanParameter(request, "enabled", enabled);

        JiveGlobals.setProperty("mediaproxy.enabled", String.valueOf(enabled));

        if (minPort > 0 && maxPort > 0) {
            if (maxPort - minPort > 1000) {
                mediaProxyService.setMinPort(minPort);
                mediaProxyService.setMaxPort(maxPort);
                JiveGlobals.setProperty("mediaproxy.portMin", String.valueOf(minPort));
                JiveGlobals.setProperty("mediaproxy.portMax", String.valueOf(maxPort));
            }
        }

        mediaProxyService.setEnabled(enabled);

        success = true;
    }

%>
<html>
<head>
    <title>Media Proxy</title>
    <meta name="pageID" content="media-proxy-service"/>
</head>
<body>

<p>
    The media proxy enables clients to make rich media (including VoIP) connections to one another
    when peer to peer connections fail, such as when one or both clients are behind a
    strict firewall.<br>
</p>

<% if (success) { %>

<div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" alt="Success"
                                           border="0"></td>
                <td class="jive-icon-label">Settings updated successfully.</td>
            </tr>
        </tbody>
    </table>
</div>
<br>

<% } %>

<form action="media-proxy.jsp" method="post">
    <div class="jive-contentBoxHeader">
        Media Proxy Settings
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
                <tr valign="middle">
                    <td width="1%" nowrap valign="top">
                        <input type="radio" name="enabled" value="true" id="rb02"
                        <%= (enabled ? "checked" : "") %> >
                    </td>
                    <td width="99%">
                        <label for="rb02">
                            <b>Enabled</b>
                            - This server will act as a media proxy.
                        </label>
                        <br>
                        <table>
                            <tr>
                                <td>Session Idle Timeout (in seconds):&nbsp;
                                </td>
                                <td>
                                    <input type="text" size="5" maxlength="8"
                                           name="idleTimeout"
                                           value="<%=mediaProxyService.getIdleTime()/1000%>"
                                           align="left">
                                </td>
                            </tr>
                            <tr>
                                <td>Session Life Time (in seconds):&nbsp;
                                </td>
                                <td>
                                    <input type="text" size="5" maxlength="8"
                                           name="lifetime"
                                           value="<%=mediaProxyService.getLifetime()%>"
                                           align="left"> &nbsp;<i>Life Time is the maximum time that a Session can
                                    lives. After this time it is destroyed, even if it stills active.</i>
                                </td>
                            </tr>
                            <tr>
                                <td>Port Range Min:&nbsp;
                                </td>
                                <td>
                                    <input type="text" size="7" maxlength="20" name="minport"
                                           value="<%=mediaProxyService.getMinPort()%>">
                                </td>
                            </tr>
                            <tr>
                                <td> Port Range Max:&nbsp;
                                </td>
                                <td>
                                    <input type="text" size="7" maxlength="20" name="maxport"
                                           value="<%=mediaProxyService.getMaxPort()%>">

                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td>&nbsp;</td>
                </tr>
                <tr valign="middle">
                    <td width="1%" nowrap>
                        <input type="radio" name="enabled" value="false" id="rb01"
                        <%= (!enabled ? "checked" : "") %> >
                    </td>
                    <td width="99%">
                        <label for="rb01">
                            <b>Disabled</b>
                            - This server will not act as a media proxy.
                        </label>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>

<% if (mediaProxyService.isEnabled()) { %>

<p>
    <b>Active Sessions Summary</b><br>
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
                Collection<MediaProxySession> sessions = mediaProxyService.getAgents();
                if (sessions.isEmpty()) {
            %>
            <tr>
                <td align="center" colspan="7">
                    No active Agents
                </td>
            </tr>

            <%
                }
                int i = 0;
                for (MediaProxySession proxySession : sessions) {
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
                    <%=(System.currentTimeMillis() - proxySession.getTimestamp()) / 1000%>
                </td>
                <td width="10%">
                    <% if (proxySession instanceof RelaySession) { %>
                    Smart Session
                    <% } else { %>
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

<% } // end enabled check %>

</body>
</html>