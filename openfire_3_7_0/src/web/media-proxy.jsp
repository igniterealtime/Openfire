<%--
  -	$Revision: $
  -	$Date: $
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.mediaproxy.MediaProxyService" %>
<%@ page import="org.jivesoftware.openfire.mediaproxy.MediaProxySession" %>
<%@ page import="org.jivesoftware.openfire.mediaproxy.RelaySession" %>
<%@ page import="java.util.Collection" %>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%

    MediaProxyService mediaProxyService = XMPPServer.getInstance().getMediaProxyService();

    boolean stop = request.getParameter("stop") != null;
    if (stop) {
        mediaProxyService.stopAgents();
    }

    boolean save = request.getParameter("update") != null;
    boolean success = false;

    long keepAliveDelay = mediaProxyService.getIdleTime();
    long lifetime = mediaProxyService.getLifetime();
    int minPort = mediaProxyService.getMinPort();
    int maxPort = mediaProxyService.getMaxPort();
    int echoPort = mediaProxyService.getEchoPort();
    boolean enabled = mediaProxyService.isEnabled();

    if (save) {
        keepAliveDelay = ParamUtils.getLongParameter(request, "idleTimeout", keepAliveDelay);
        if (keepAliveDelay > 1) {
            mediaProxyService.setKeepAliveDelay(keepAliveDelay * 1000);
            JiveGlobals
                    .setProperty("mediaproxy.idleTimeout", String.valueOf(keepAliveDelay * 1000));
        }

        lifetime = ParamUtils.getLongParameter(request, "lifetime", lifetime);
        if (lifetime > 0) {
            mediaProxyService.setLifetime(lifetime);
            JiveGlobals
                    .setProperty("mediaproxy.lifetime", String.valueOf(lifetime));
        }

        minPort = ParamUtils.getIntParameter(request, "minport", minPort);
        maxPort = ParamUtils.getIntParameter(request, "maxport", maxPort);
        echoPort = ParamUtils.getIntParameter(request, "echoport", echoPort);
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

        if (echoPort > 0){
            mediaProxyService.setEchoPort(echoPort);
            JiveGlobals.setProperty("mediaproxy.echoPort", String.valueOf(echoPort));
        }

        mediaProxyService.setEnabled(enabled);

        // Log the event
        webManager.logEvent("edited media proxy settings", "minport = "+minPort+"\nmaxport = "+maxPort+"\nechoport = "+echoPort+"\nenabled = "+enabled+"\nlifetime = "+lifetime+"\nkeepalivedelay = "+keepAliveDelay);

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
    <fmt:message key="mediaproxy.desc"/>
    <br>
</p>

<% if (success) { %>

<div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" alt="Success"
                                           border="0"></td>
                <td class="jive-icon-label">
                    <fmt:message key="mediaproxy.settings.success"/>
                </td>
            </tr>
        </tbody>
    </table>
</div>
<br>

<% } %>

<form action="media-proxy.jsp" method="post">
    <div class="jive-contentBoxHeader">
        <fmt:message key="mediaproxy.form.label"/>
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
                            <b>
                                <fmt:message key="mediaproxy.form.enabled"/>
                            </b>
                            -
                            <fmt:message key="mediaproxy.form.enabled.desc"/>
                        </label>
                        <br>
                        <table>
                            <tr>
                                <td>
                                    <fmt:message key="mediaproxy.form.idletimeout"/>
                                    :&nbsp;
                                </td>
                                <td>
                                    <input type="text" size="5" maxlength="8"
                                           name="idleTimeout"
                                           value="<%=mediaProxyService.getIdleTime()/1000%>"
                                           align="left"> &nbsp;<i>
                                    <fmt:message key="mediaproxy.form.idletimeout.tip"/>
                                </i>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <fmt:message key="mediaproxy.form.lifetime"/>
                                    :&nbsp;
                                </td>
                                <td>
                                    <input type="text" size="5" maxlength="8"
                                           name="lifetime"
                                           value="<%=mediaProxyService.getLifetime()%>"
                                           align="left"> &nbsp;<i>
                                    <fmt:message key="mediaproxy.form.lifetime.tip"/>
                                </i>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <fmt:message key="mediaproxy.form.minport"/>
                                    :&nbsp;
                                </td>
                                <td>
                                    <input type="text" size="7" maxlength="20" name="minport"
                                           value="<%=mediaProxyService.getMinPort()%>">
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <fmt:message key="mediaproxy.form.maxport"/>
                                    :&nbsp;
                                </td>
                                <td>
                                    <input type="text" size="7" maxlength="20" name="maxport"
                                           value="<%=mediaProxyService.getMaxPort()%>">
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <fmt:message key="mediaproxy.form.echoport"/>
                                    :&nbsp;
                                </td>
                                <td>
                                    <input type="text" size="7" maxlength="20" name="echoport"
                                           value="<%=mediaProxyService.getEchoPort()%>">
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
                            <b>
                                <fmt:message key="mediaproxy.form.disabled"/>
                            </b>
                            -
                            <fmt:message key="mediaproxy.form.disabled.desc"/>
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
    <b>
        <fmt:message key="mediaproxy.summary.label"/>
    </b><br>
    <fmt:message key="mediaproxy.summary.desc"/>
</p>

<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
            <tr>
                <th>&nbsp;</th>
                <th nowrap align="left" valign="middle">
                    <fmt:message key="mediaproxy.summary.session.creator"/>
                </th>
                <th nowrap align="left" valign="middle">
                    <fmt:message key="mediaproxy.summary.session.port"/>
                    A
                </th>
                <th nowrap align="left" valign="middle">
                    <fmt:message key="mediaproxy.summary.session.port"/>
                    B
                </th>
                <th nowrap align="left" valign="middle">
                    <fmt:message key="mediaproxy.summary.session.server"/>
                </th>
                <th nowrap align="left" valign="middle">
                    <fmt:message key="mediaproxy.summary.session.inactivity"/>
                </th>
                <th nowrap align="left" valign="middle">
                    <fmt:message key="mediaproxy.summary.session.type"/>
                </th>
            </tr>
        </thead>
        <tbody>

            <% // Print the list of agents
                Collection<MediaProxySession> sessions = mediaProxyService.getAgents();
                if (sessions.isEmpty()) {
            %>
            <tr>
                <td align="center" colspan="7">
                    <fmt:message key="mediaproxy.summary.session.noactive"/>
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
                <td width="10%" align="left" valign="middle">
                    <%=proxySession.getCreator()%>
                </td>
                <td width="15%" align="left" valign="middle">
                    <%=proxySession.getHostA()%>:<%=proxySession.getLocalPortA()%>
                </td>
                <td width="15%" align="left" valign="middle">
                    <%=proxySession.getHostB()%>:<%=proxySession.getLocalPortB()%>
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
        <input type="submit" name="stop" value="<fmt:message key="mediaproxy.summary.stopbutton" />"/>
    </form>
</div>

<% } // end enabled check %>

</body>
</html>