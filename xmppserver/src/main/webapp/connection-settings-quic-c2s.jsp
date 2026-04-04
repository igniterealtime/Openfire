<%--
  -
  - Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="java.time.Duration" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.session.ConnectionSettings" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionConfiguration" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionListener" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.openfire.ConnectionManager" %>
<%@ page errorPage="error.jsp" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%
    final ConnectionType connectionType = ConnectionType.QUIC_C2S;
    final ConnectionManager manager = XMPPServer.getInstance().getConnectionManager();

    final ConnectionConfiguration configuration = manager.getListener( connectionType, true ).generateConnectionConfiguration();

    boolean update = request.getParameter( "update" ) != null;
    final Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if ( update && errors.isEmpty() )
    {
        final boolean enabled = ParamUtils.getBooleanParameter(request, "enabled");
        final int tcpPort = ParamUtils.getIntParameter(request, "tcpPort", configuration.getPort());
        final int idleSeconds = ParamUtils.getIntParameter(request, "idleSeconds", (int) ConnectionSettings.Client.QUIC_IDLE_TIMEOUT_PROPERTY.getValue().toSeconds());
        final int maxStreams = ParamUtils.getIntParameter(request, "maxStreams", ConnectionSettings.Client.QUIC_MAX_STREAMS.getValue());

        if (tcpPort <= 0 || tcpPort > 65535) {
            errors.put("tcpPort", "invalid");
        }
        if (idleSeconds < -1) {
            errors.put("idleSeconds", "invalid");
        }
        if (maxStreams < 1) {
            errors.put("maxStreams", "invalid");
        }

        if (errors.isEmpty()) {
            final ConnectionListener listener = manager.getListener(connectionType, true);
            listener.enable(enabled);
            listener.setPort(tcpPort);

            ConnectionSettings.Client.QUIC_IDLE_TIMEOUT_PROPERTY.setValue(Duration.ofSeconds(idleSeconds));
            ConnectionSettings.Client.QUIC_MAX_STREAMS.setValue(maxStreams);

            webManager.logEvent(
                "Updated connection settings for " + connectionType,
                "enabled=" + enabled + ", port=" + tcpPort + ", idleSeconds=" + idleSeconds + ", maxStreams=" + maxStreams
            );
            response.sendRedirect("connection-settings-quic-c2s.jsp?success=true");
            return;
        }
    }

    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("configuration", configuration);
    pageContext.setAttribute("idleSeconds", ConnectionSettings.Client.QUIC_IDLE_TIMEOUT_PROPERTY.getValue().toSeconds());
    pageContext.setAttribute("maxStreams", ConnectionSettings.Client.QUIC_MAX_STREAMS.getValue());
%>
<html>
<head>
    <title><fmt:message key="quic.client.connections.settings.title"/></title>
    <meta name="pageID" content="quic-client-connections-settings"/>
</head>
<body>

<c:if test="${param.success and empty errors}">
    <admin:infoBox type="success"><fmt:message key="quic.client.connections.settings.confirm.updated" /></admin:infoBox>
</c:if>

<c:if test="${not empty errors['csrf']}">
    <admin:infobox type="error"><fmt:message key="admin.error"/>: <c:out value="${errors['csrf']}"/></admin:infobox>
</c:if>

<c:if test="${not empty errors['tcpPort']}">
    <admin:infobox type="error"><fmt:message key="quic.client.connections.settings.valid.port"/></admin:infobox>
</c:if>

<c:if test="${not empty errors['idleSeconds']}">
    <admin:infobox type="error"><fmt:message key="quic.client.connections.settings.valid.idle"/></admin:infobox>
</c:if>

<c:if test="${not empty errors['maxStreams']}">
    <admin:infobox type="error"><fmt:message key="quic.client.connections.settings.valid.maxstreams"/></admin:infobox>
</c:if>

<p><fmt:message key="quic.client.connections.settings.info"/></p>

<form action="connection-settings-quic-c2s.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <fmt:message key="quic.client.connections.settings.boxtitle" var="boxtitle"/>
    <admin:contentBox title="${boxtitle}">
        <table>
            <tr>
                <td colspan="2"><input type="checkbox" name="enabled" id="enabled" ${configuration.enabled ? 'checked' : ''}/><label for="enabled"><fmt:message key="quic.client.connections.settings.label_enable"/></label></td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><label for="tcpPort"><fmt:message key="ports.port"/></label></td>
                <td><input type="number" name="tcpPort" id="tcpPort" value="${configuration.port}" min="1" max="65535" step="1"></td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><label for="idleSeconds"><fmt:message key="quic.client.connections.settings.label_idle"/></label></td>
                <td><input type="number" name="idleSeconds" id="idleSeconds" value="${idleSeconds}" min="-1" step="1"> <fmt:message key="global.seconds"/></td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><label for="maxStreams"><fmt:message key="quic.client.connections.settings.label_maxstreams"/></label></td>
                <td><input type="number" name="maxStreams" id="maxStreams" value="${maxStreams}" min="1" step="1"></td>
            </tr>
            <tr>
                <td colspan="2"><a href="./connection-settings-advanced.jsp?connectionType=QUIC_C2S&connectionMode=directtls"><fmt:message key="ssl.settings.client.label_custom_info"/>...</a></td>
            </tr>
        </table>
    </admin:contentBox>

    <input type="submit" name="update" value="<fmt:message key='global.save_settings'/>">
</form>

</body>
</html>
