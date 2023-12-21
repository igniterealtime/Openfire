<%--
  -
  - Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
    final ConnectionType connectionType = ConnectionType.SOCKET_C2S;
    final ConnectionManager manager = XMPPServer.getInstance().getConnectionManager();

    final ConnectionConfiguration plaintextConfiguration = manager.getListener( connectionType, false ).generateConnectionConfiguration();
    final ConnectionConfiguration directtlsConfiguration = manager.getListener( connectionType, true  ).generateConnectionConfiguration();

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
        // plaintext
        final boolean plaintextEnabled      = ParamUtils.getBooleanParameter( request, "plaintext-enabled" );
        final int plaintextTcpPort          = ParamUtils.getIntParameter( request, "plaintext-tcpPort", plaintextConfiguration.getPort() );

        // Direct TLS
        final boolean directtlsEnabled      = ParamUtils.getBooleanParameter( request, "directtls-enabled" );
        final int directtlsTcpPort          = ParamUtils.getIntParameter( request, "directtls-tcpPort", directtlsConfiguration.getPort() );

        // Apply
        final ConnectionListener plaintextListener = manager.getListener( connectionType, false );
        final ConnectionListener directtlsListener = manager.getListener( connectionType, true  );

        plaintextListener.enable( plaintextEnabled );
        plaintextListener.setPort( plaintextTcpPort );

        directtlsListener.enable( directtlsEnabled );
        directtlsListener.setPort( directtlsTcpPort );

        // Log the event
        webManager.logEvent( "Updated connection settings for " + connectionType, "plain: enabled=" + plaintextEnabled + ", port=" + plaintextTcpPort + "\nDirect TLS: enabled=" + directtlsEnabled+ ", port=" + directtlsTcpPort+ "\n" );
        response.sendRedirect( "connection-settings-socket-c2s.jsp?success=true" );


        // TODO below is the 'idle connection' handing. This should go into the connection configuration, like all other configuration.
        final int clientIdle = 1000* ParamUtils.getIntParameter(request, "clientIdle", -1);
        final boolean idleDisco = ParamUtils.getBooleanParameter(request, "idleDisco");
        final boolean pingIdleClients = ParamUtils.getBooleanParameter(request, "pingIdleClients");

        if (!idleDisco) {
            ConnectionSettings.Client.IDLE_TIMEOUT_PROPERTY.setValue(Duration.ofMillis(-1));
        } else {
            ConnectionSettings.Client.IDLE_TIMEOUT_PROPERTY.setValue(Duration.ofMillis(clientIdle));
        }
        ConnectionSettings.Client.KEEP_ALIVE_PING_PROPERTY.setValue(pingIdleClients);

        webManager.logEvent("set server property " + ConnectionSettings.Client.IDLE_TIMEOUT_PROPERTY.getKey(), ConnectionSettings.Client.IDLE_TIMEOUT_PROPERTY.getKey() + " = " + ConnectionSettings.Client.IDLE_TIMEOUT_PROPERTY.getDisplayValue());
        webManager.logEvent("set server property " + ConnectionSettings.Client.KEEP_ALIVE_PING_PROPERTY.getKey(), ConnectionSettings.Client.KEEP_ALIVE_PING_PROPERTY.getKey() + " = " + ConnectionSettings.Client.KEEP_ALIVE_PING_PROPERTY.getDisplayValue());

        return;
    }

    pageContext.setAttribute( "errors",                 errors );
    pageContext.setAttribute( "plaintextConfiguration", plaintextConfiguration );
    pageContext.setAttribute( "directtlsConfiguration", directtlsConfiguration );
    pageContext.setAttribute( "clientIdle",             ConnectionSettings.Client.IDLE_TIMEOUT_PROPERTY.getValue().toMillis());
    pageContext.setAttribute( "pingIdleClients",        ConnectionSettings.Client.KEEP_ALIVE_PING_PROPERTY.getValue());


%>
<html>
<head>
    <title><fmt:message key="client.connections.settings.title"/></title>
    <meta name="pageID" content="client-connections-settings"/>
    <script>
        // Displays or hides the configuration block for a particular connection type, based on the status of the
        // 'enable' checkbox for that connection type.
        function applyDisplayable( connectionType )
        {
            let configBlock, enabled;

            // Select the right configuration block and enable or disable it as defined by the the corresponding checkbox.
            configBlock = document.getElementById( connectionType + "-config" );
            enabled     = document.getElementById( connectionType + "-enabled" ).checked;

            if ( ( configBlock != null ) && ( enabled != null ) )
            {
                if ( enabled )
                {
                    configBlock.style.display = "block";
                }
                else
                {
                    configBlock.style.display = "none";
                }
            }
        }

        // Ensure that the various elements are set properly when the page is loaded.
        window.onload = function()
        {
            applyDisplayable( "plaintext" );
            applyDisplayable( "directtls" );
        };
    </script>
</head>
<body>

<c:if test="${param.success and empty errors}">
    <admin:infoBox type="success"><fmt:message key="client.connections.settings.confirm.updated" /></admin:infoBox>
</c:if>

<p>
    <fmt:message key="client.connections.settings.info">
        <fmt:param value="<a href=\"session-summary.jsp\">" />
        <fmt:param value="</a>" />
    </fmt:message>
</p>

<form action="connection-settings-socket-c2s.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <fmt:message key="ssl.settings.client.plaintext.boxtitle" var="plaintextboxtitle"/>
    <admin:contentBox title="${plaintextboxtitle}">

        <p><fmt:message key="ssl.settings.client.plaintext.info"/></p>

        <table>
            <tr>
                <td colspan="2"><input type="checkbox" name="plaintext-enabled" id="plaintext-enabled" onclick="applyDisplayable('plaintext')" ${plaintextConfiguration.enabled ? 'checked' : ''}/><label for="plaintext-enabled"><fmt:message key="ssl.settings.client.plaintext.label_enable"/></label></td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><label for="plaintext-tcpPort"><fmt:message key="ports.port"/></label></td>
                <td><input type="text" name="plaintext-tcpPort" id="plaintext-tcpPort" value="${plaintextConfiguration.port}"/></td>
            </tr>
            <tr>
                <td colspan="2"><a href="./connection-settings-advanced.jsp?connectionType=SOCKET_C2S&connectionMode=plain"><fmt:message key="ssl.settings.client.label_custom_info"/>...</a></td>
            </tr>
        </table>

    </admin:contentBox>

    <fmt:message key="ssl.settings.client.directtls.boxtitle" var="directtlsboxtitle"/>
    <admin:contentBox title="${directtlsboxtitle}">

        <p><fmt:message key="ssl.settings.client.directtls.info"/></p>

        <table>
            <tr>
                <td colspan="2"><input type="checkbox" name="directtls-enabled" id="directtls-enabled" onclick="applyDisplayable('directtls')" ${directtlsConfiguration.enabled ? 'checked' : ''}/><label for="directtls-enabled"><fmt:message key="ssl.settings.client.directtls.label_enable"/></label></td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><label for="directtls-tcpPort"><fmt:message key="ports.port"/></label></td>
                <td><input type="text" name="directtls-tcpPort" id="directtls-tcpPort" value="${directtlsConfiguration.port}"></td>
            </tr>
            <tr>
                <td colspan="2"><a href="./connection-settings-advanced.jsp?connectionType=SOCKET_C2S&connectionMode=directtls"><fmt:message key="ssl.settings.client.label_custom_info"/>...</a></td>
            </tr>
        </table>

    </admin:contentBox>

    <!-- BEGIN 'Idle Connection Policy' -->
    <fmt:message key="client.connections.settings.idle.title" var="idleTitle" />
    <admin:contentBox title="${idleTitle}">
        <p><fmt:message key="client.connections.settings.idle.info" /></p>
        <table>
            <tbody>
            <tr>
                <td style="width: 1%; white-space: nowrap" class="c1">
                    <input type="radio" name="idleDisco" value="false" ${clientIdle le 0 ? 'checked' : ''} id="IDL01">
                </td>
                <td><label for="IDL01"><fmt:message key="client.connections.settings.idle.disable" /></label></td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap; vertical-align: top" class="c1">
                    <input type="radio" name="idleDisco" value="true" ${clientIdle gt 0 ? 'checked' : ''} id="IDL02">
                </td>
                <td>
                    <label for="IDL02"><fmt:message key="client.connections.settings.idle.enable" /></label>
                    <br />
                    <c:if test="${clientIdle gt 0}">
                        <fmt:parseNumber integerOnly="true" var="seconds">${clientIdle div 1000}</fmt:parseNumber>
                    </c:if>
                    <input type="text" name="clientIdle" value="${clientIdle gt 0 ? seconds : ''}" size="5" maxlength="5">&nbsp;<fmt:message key="global.seconds" />
                    <c:if test="${not empty errors['clientIdle']}">
                        <br/>
                        <span class="jive-error-text">
                            <fmt:message key="client.connections.settings.idle.valid_timeout" />.
                        </span>
                    </c:if>
                </td>
            </tr>
            <tr><td colspan="2">&nbsp;</td></tr>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <p><fmt:message key="client.connections.settings.ping.info" />
                        <fmt:message key="client.connections.settings.ping.footnote" /></p>
                    <table>
                        <tbody>
                        <tr>
                            <td style="width: 1%; white-space: nowrap" class="c1">
                                <input type="radio" name="pingIdleClients" value="true" ${pingIdleClients ? 'checked' : ''} id="PNG01">
                            </td>
                            <td><label for="PNG01"><fmt:message key="client.connections.settings.ping.enable" /></label></td>
                        </tr>
                        <tr>
                            <td style="width: 1%; white-space: nowrap" class="c1">
                                <input type="radio" name="pingIdleClients" value="false" ${pingIdleClients ? '' : 'checked'} id="PNG02">
                            </td>
                            <td><label for="PNG02"><fmt:message key="client.connections.settings.ping.disable" /></label></td>
                        </tr>
                        </tbody>
                    </table>
                </td>
            </tr>
            </tbody>
        </table>
    </admin:contentBox>

    <!-- END 'Idle Connection Policy' -->

    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
</body>
</html>
