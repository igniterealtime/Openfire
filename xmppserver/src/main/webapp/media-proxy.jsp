<%--
  -
  - Copyright (C) 2005-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.mediaproxy.MediaProxyService" %>
<%@ page import="org.jivesoftware.openfire.mediaproxy.MediaProxySession" %>
<%@ page import="org.jivesoftware.openfire.mediaproxy.RelaySession" %>
<%@ page import="java.util.Collection" %>

<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="admin" uri="admin" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%

    MediaProxyService mediaProxyService = XMPPServer.getInstance().getMediaProxyService();

    boolean stop = request.getParameter("stop") != null;
    boolean save = request.getParameter("update") != null;
    boolean success = false;

    long keepAliveDelay = mediaProxyService.getIdleTime();
    long lifetime = mediaProxyService.getLifetime();
    int minPort = mediaProxyService.getMinPort();
    int maxPort = mediaProxyService.getMaxPort();
    int echoPort = mediaProxyService.getEchoPort();
    boolean enabled = mediaProxyService.isEnabled();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save || stop) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
            stop = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (stop) {
        mediaProxyService.stopAgents();
    }

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

    <admin:infoBox type="success">
        <fmt:message key="mediaproxy.settings.success"/>
    </admin:infoBox>

<% } %>

<form action="media-proxy.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <div class="jive-contentBoxHeader">
        <fmt:message key="mediaproxy.form.label"/>
    </div>
    <div class="jive-contentBox">
        <table>
            <tbody>
                <tr>
                    <td style="width: 1%; white-space: nowrap; vertical-align: top">
                        <input type="radio" name="enabled" value="true" id="rb02" <%= (enabled ? "checked" : "") %> >
                    </td>
                    <td>
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
                                    <label for="idleTimeout"><fmt:message key="mediaproxy.form.idletimeout"/>:</label>
                                </td>
                                <td>
                                    <input type="text" size="5" maxlength="8" id="idleTimeout" name="idleTimeout"
                                           value="<%=mediaProxyService.getIdleTime()/1000%>">&nbsp;
                                    <i><fmt:message key="mediaproxy.form.idletimeout.tip"/></i>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <label for="lifetime"><fmt:message key="mediaproxy.form.lifetime"/>:</label>
                                </td>
                                <td>
                                    <input type="text" size="5" maxlength="8" id="lifetime" name="lifetime"
                                           value="<%=mediaProxyService.getLifetime()%>">&nbsp;
                                    <i><fmt:message key="mediaproxy.form.lifetime.tip"/></i>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <label for="minport"><fmt:message key="mediaproxy.form.minport"/>:</label>
                                </td>
                                <td>
                                    <input type="text" size="7" maxlength="20" id="minport" name="minport"
                                           value="<%=mediaProxyService.getMinPort()%>">
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <label for="maxport"><fmt:message key="mediaproxy.form.maxport"/>:</label>
                                </td>
                                <td>
                                    <input type="text" size="7" maxlength="20" id="maxport" name="maxport"
                                           value="<%=mediaProxyService.getMaxPort()%>">
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <label for="echoport"><fmt:message key="mediaproxy.form.echoport"/>:</label>
                                </td>
                                <td>
                                    <input type="text" size="7" maxlength="20" id="echoport" name="echoport"
                                           value="<%=mediaProxyService.getEchoPort()%>">
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td>&nbsp;</td>
                </tr>
                <tr>
                    <td style="width: 1%; white-space: nowrap">
                        <input type="radio" name="enabled" value="false" id="rb01"
                        <%= (!enabled ? "checked" : "") %> >
                    </td>
                    <td>
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
    <table>
        <thead>
            <tr>
                <th>&nbsp;</th>
                <th nowrap>
                    <fmt:message key="mediaproxy.summary.session.creator"/>
                </th>
                <th nowrap>
                    <fmt:message key="mediaproxy.summary.session.port"/>
                    A
                </th>
                <th nowrap>
                    <fmt:message key="mediaproxy.summary.session.port"/>
                    B
                </th>
                <th nowrap>
                    <fmt:message key="mediaproxy.summary.session.server"/>
                </th>
                <th nowrap>
                    <fmt:message key="mediaproxy.summary.session.inactivity"/>
                </th>
                <th nowrap>
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
                <td style="text-align: center" colspan="7">
                    <fmt:message key="mediaproxy.summary.session.noactive"/>
                </td>
            </tr>

            <%
                }
                int i = 0;
                for (MediaProxySession proxySession : sessions) {
                    i++;
            %>
            <tr>
                <td style="width: 1%">
                    <%= i %>
                </td>
                <td style="width: 10%">
                    <%= StringUtils.escapeHTMLTags(proxySession.getCreator())%>
                </td>
                <td style="width: 15%">
                    <%=proxySession.getHostA()%>:<%=proxySession.getLocalPortA()%>
                </td>
                <td style="width: 15%">
                    <%=proxySession.getHostB()%>:<%=proxySession.getLocalPortB()%>
                </td>
                <td style="width: 10%">
                    <%=proxySession.getLocalhost()%>
                </td>
                <td style="width: 20%">
                    <%=(System.currentTimeMillis() - proxySession.getTimestamp()) / 1000%>
                </td>
                <td style="width: 10%">
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
        <input type="hidden" name="csrf" value="${csrf}">
        <input type="submit" name="stop" value="<fmt:message key="mediaproxy.summary.stopbutton" />"/>
    </form>
</div>

<% } // end enabled check %>

</body>
</html>
