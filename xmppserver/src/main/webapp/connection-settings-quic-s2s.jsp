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
  -
  - QUIC server-to-server (S2S / federation) connection settings:
  -   - Inbound QUIC S2S listener (UDP port, idle timeout, ALPN)
  -   - Outbound QUIC S2S via _xmpp-server._quic SRV discovery
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="io.netty.handler.codec.quic.Quic" %>
<%@ page import="org.jivesoftware.openfire.ConnectionManager" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.session.ConnectionSettings" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionConfiguration" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionListener" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.time.Duration" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page errorPage="error.jsp" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out); %>
<%
    final ConnectionManager manager = XMPPServer.getInstance().getConnectionManager();
    final ConnectionListener quicS2SListener = manager.getListener(ConnectionType.QUIC_S2S, false);
    final ConnectionConfiguration quicS2SConfiguration = quicS2SListener.generateConnectionConfiguration();

    final Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    boolean update = request.getParameter("update") != null;

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (update && errors.isEmpty()) {
        // Inbound listener settings
        final boolean quicEnabled = ParamUtils.getBooleanParameter(request, "quic-enabled");
        final int quicPort = ParamUtils.getIntParameter(request, "quic-port", quicS2SConfiguration.getPort());
        final int quicIdleSeconds = ParamUtils.getIntParameter(request, "quic-idleSeconds",
            (int) ConnectionSettings.Server.QUIC_IDLE_TIMEOUT_PROPERTY.getValue().toSeconds());
        final String quicAlpn = ParamUtils.getParameter(request, "quic-alpn");

        // Outbound settings
        final boolean quicOutboundEnabled = ParamUtils.getBooleanParameter(request, "quic-outbound-enabled");

        if (quicPort <= 0 || quicPort > 65535) {
            errors.put("quic-port", "invalid");
        }
        if (quicIdleSeconds < -1) {
            errors.put("quic-idleSeconds", "invalid");
        }
        if (quicAlpn == null || quicAlpn.trim().isEmpty()) {
            errors.put("quic-alpn", "invalid");
        }

        if (errors.isEmpty()) {
            quicS2SListener.enable(quicEnabled);
            quicS2SListener.setPort(quicPort);
            ConnectionSettings.Server.QUIC_IDLE_TIMEOUT_PROPERTY.setValue(Duration.ofSeconds(quicIdleSeconds));

            final java.util.List<String> quicAlpnList = new ArrayList<>();
            for (final String token : quicAlpn.split("[,\\s]+")) {
                if (!token.trim().isEmpty()) {
                    quicAlpnList.add(token.trim());
                }
            }
            ConnectionSettings.Server.QUIC_ALPN.setValue(quicAlpnList);
            ConnectionSettings.Server.QUIC_OUTBOUND_ENABLED.setValue(quicOutboundEnabled);

            webManager.logEvent("Updated QUIC S2S connection settings",
                "inbound: enabled=" + quicEnabled + ", port=" + quicPort
                    + ", idleSeconds=" + quicIdleSeconds + ", alpn=" + quicAlpnList
                    + "\noutbound: enabled=" + quicOutboundEnabled);

            response.sendRedirect("connection-settings-quic-s2s.jsp?success=true");
            return;
        }
    }

    pageContext.setAttribute("quicS2SConfiguration",   quicS2SConfiguration);
    pageContext.setAttribute("quicIdleSeconds",         ConnectionSettings.Server.QUIC_IDLE_TIMEOUT_PROPERTY.getValue().toSeconds());
    pageContext.setAttribute("quicAlpn",                String.join(", ", ConnectionSettings.Server.QUIC_ALPN.getValue()));
    pageContext.setAttribute("quicOutboundEnabled",     ConnectionSettings.Server.QUIC_OUTBOUND_ENABLED.getValue());
    pageContext.setAttribute("quicNativeAvailable",     Quic.isAvailable());
    pageContext.setAttribute("errors",                  errors);
%>

<html>
<head>
    <title><fmt:message key="quic.server.connections.settings.title"/></title>
    <meta name="pageID" content="connection-settings-quic-s2s"/>
</head>
<body>

<c:if test="${param.success eq 'true'}">
    <admin:infobox type="success"><fmt:message key="quic.server.connections.settings.saved"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['csrf']}">
    <admin:infobox type="error"><fmt:message key="global.csrf.failed"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['quic-port']}">
    <admin:infobox type="error"><fmt:message key="quic.server.connections.settings.valid.port"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['quic-idleSeconds']}">
    <admin:infobox type="error"><fmt:message key="quic.server.connections.settings.valid.idle"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['quic-alpn']}">
    <admin:infobox type="error"><fmt:message key="quic.server.connections.settings.valid.alpn"/></admin:infobox>
</c:if>

<p><fmt:message key="quic.server.connections.settings.info"/></p>

<form action="connection-settings-quic-s2s.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="update" value="true">

    <%-- ═══════════════════════════════════════════════════════════════
         Native library availability banner
    ═══════════════════════════════════════════════════════════════ --%>
    <c:choose>
        <c:when test="${quicNativeAvailable}">
            <admin:infobox type="info"><fmt:message key="quic.server.connections.settings.native.available"/></admin:infobox>
        </c:when>
        <c:otherwise>
            <admin:infobox type="warning"><fmt:message key="quic.server.connections.settings.native.unavailable"/></admin:infobox>
        </c:otherwise>
    </c:choose>

    <%-- ═══════════════════════════════════════════════════════════════
         Inbound QUIC S2S listener
    ═══════════════════════════════════════════════════════════════ --%>
    <fmt:message key="quic.server.connections.settings.inbound.boxtitle" var="inboundboxtitle"/>
    <admin:contentBox title="${inboundboxtitle}">
        <p><fmt:message key="quic.server.connections.settings.inbound.info"/></p>
        <table>
            <tr>
                <td colspan="2">
                    <input type="checkbox" name="quic-enabled" id="quic-enabled"
                           ${quicS2SConfiguration.enabled ? 'checked' : ''}/>
                    <label for="quic-enabled"><fmt:message key="quic.server.connections.settings.label_enable"/></label>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <label for="quic-port"><fmt:message key="ports.port"/></label>
                </td>
                <td>
                    <input type="number" name="quic-port" id="quic-port"
                           value="${quicS2SConfiguration.port}" min="1" max="65535" step="1">
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <label for="quic-idleSeconds"><fmt:message key="quic.server.connections.settings.label_idle"/></label>
                </td>
                <td>
                    <input type="number" name="quic-idleSeconds" id="quic-idleSeconds"
                           value="${quicIdleSeconds}" min="-1" step="1">
                    <fmt:message key="global.seconds"/>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <label for="quic-alpn"><fmt:message key="quic.server.connections.settings.label_alpn"/></label>
                </td>
                <td>
                    <input type="text" name="quic-alpn" id="quic-alpn"
                           value="<c:out value='${quicAlpn}'/>" size="40">
                </td>
            </tr>
            <tr>
                <td colspan="2">
                    <a href="./connection-settings-advanced.jsp?connectionType=QUIC_S2S&amp;connectionMode=directtls">
                        <fmt:message key="ssl.settings.client.label_custom_info"/>...
                    </a>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <%-- ═══════════════════════════════════════════════════════════════
         Outbound QUIC S2S
    ═══════════════════════════════════════════════════════════════ --%>
    <fmt:message key="quic.server.connections.settings.outbound.boxtitle" var="outboundboxtitle"/>
    <admin:contentBox title="${outboundboxtitle}">
        <p><fmt:message key="quic.server.connections.settings.outbound.info"/></p>
        <table>
            <tr>
                <td colspan="2">
                    <input type="checkbox" name="quic-outbound-enabled" id="quic-outbound-enabled"
                           ${quicOutboundEnabled ? 'checked' : ''}/>
                    <label for="quic-outbound-enabled">
                        <fmt:message key="quic.server.connections.settings.outbound.label_enable"/>
                    </label>
                </td>
            </tr>
        </table>
        <p><fmt:message key="quic.server.connections.settings.outbound.srv_note"/></p>
    </admin:contentBox>

    <input type="submit" value="<fmt:message key='global.save_settings'/>">
</form>

</body>
</html>
