<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2005-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="admin" uri="admin" %>

<%@ page import="org.jivesoftware.openfire.ConnectionManager,
                 org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.multiplex.ConnectionMultiplexerManager,
                 org.jivesoftware.openfire.session.ConnectionMultiplexerSession,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.util.StringUtils"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionConfiguration" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionListener" %>
<%@ page import="java.util.*" %>
<%@ page import="java.util.concurrent.ConcurrentHashMap" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%  webManager.init(request, response, session, application, out); %>

<html>
<head>
    <title>
        <fmt:message key="connection-manager.settings.title"/></title>
        <meta name="pageID" content="connection-managers-settings"/>
    </head>
    <body>

<%
    final ConnectionType connectionType = ConnectionType.CONNECTION_MANAGER;
    final ConnectionManager manager = XMPPServer.getInstance().getConnectionManager();

    final ConnectionConfiguration plaintextConfiguration = manager.getListener( connectionType, false ).generateConnectionConfiguration();
    final ConnectionConfiguration directtlsConfiguration = manager.getListener( connectionType, true  ).generateConnectionConfiguration();

    // Get parameters
    boolean update = request.getParameter("update") != null;
    String defaultSecret = ParamUtils.getParameter(request,"defaultSecret");

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

    if (update) {
        // Validate params
        // plaintext
        final boolean plaintextEnabled = ParamUtils.getBooleanParameter( request, "plaintext-enabled" );
        final int plaintextTcpPort = ParamUtils.getIntParameter( request, "plaintext-tcpPort", plaintextConfiguration.getPort() );

        // Direct TLS
        final boolean directtlsEnabled      = ParamUtils.getBooleanParameter( request, "directtls-enabled" );
        final int directtlsTcpPort          = ParamUtils.getIntParameter( request, "directtls-tcpPort", directtlsConfiguration.getPort() );

        if (plaintextEnabled) {
            if (defaultSecret == null || defaultSecret.trim().isEmpty()) {
                errors.put("defaultSecret","");
            }
            if (plaintextTcpPort <= 0) {
                errors.put("port","");
            }
        }

        if (directtlsEnabled) {
            if (defaultSecret == null || defaultSecret.trim().isEmpty()) {
                errors.put("defaultSecret","");
            }
            if (directtlsTcpPort <= 0) {
                errors.put("port","");
            }
        }

        // If no errors, continue:
        if (errors.isEmpty()) {

            // Apply
            final ConnectionListener plaintextListener = manager.getListener( connectionType, false );
            final ConnectionListener directtlsListener = manager.getListener( connectionType, true  );

            plaintextListener.enable( plaintextEnabled );
            plaintextListener.setPort( plaintextTcpPort );

            directtlsListener.enable( directtlsEnabled );
            directtlsListener.setPort( directtlsTcpPort );

            // Get hash value of existing default secret
            String existingHashDefaultSecret = "";
            if (ConnectionMultiplexerManager.getDefaultSecret() != null) {
                existingHashDefaultSecret = StringUtils.hash(ConnectionMultiplexerManager.getDefaultSecret());
            }

            // Check if the new default secret was changed. If it wasn't changed, then it is the original hashed
            // default secret
            // NOTE: if the new PLAIN default secret equals the previous HASHED default secret this fails,
            // but is unlikely.
            if (!existingHashDefaultSecret.equals(defaultSecret)) {
                // Hash the new default secret since it was changed
                String newHashDefaultSecret = "";
                if (defaultSecret != null) {
                    newHashDefaultSecret = StringUtils.hash(defaultSecret);
                }
                // Change default secret if hash values are different
                if (!existingHashDefaultSecret.equals(newHashDefaultSecret)) {
                    ConnectionMultiplexerManager.setDefaultSecret(defaultSecret);
                }
            }

            webManager.logEvent( "Updated connection settings for " + connectionType, "plain: enabled=" + plaintextEnabled + ", port=" + plaintextTcpPort + "\nDirect TLS: enabled=" + directtlsEnabled+ ", port=" + directtlsTcpPort+ "\n" );
            response.sendRedirect( "connection-managers-settings.jsp?success=true" );
        }
    }

    final Collection<String> connectionManagers = ConnectionMultiplexerManager.getInstance().getMultiplexers();
    final Map<String, List<ConnectionMultiplexerSession>> connectionManagerSessions = new ConcurrentHashMap<>();
    final Map<String, Integer> connectionManagerUserCounts = new ConcurrentHashMap<>();
    for (final String managerName : connectionManagers) {
        connectionManagerSessions.put(managerName, SessionManager.getInstance().getConnectionMultiplexerSessions(managerName));
        connectionManagerUserCounts.put(managerName, ConnectionMultiplexerManager.getInstance().getNumConnectedClients(managerName));
    }

    // Set page vars
    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("plaintextConfiguration", plaintextConfiguration);
    pageContext.setAttribute("directtlsConfiguration", directtlsConfiguration);
    pageContext.setAttribute("defaultSecretHash", ConnectionMultiplexerManager.getDefaultSecret() != null && !ConnectionMultiplexerManager.getDefaultSecret().isBlank() ? StringUtils.hash(ConnectionMultiplexerManager.getDefaultSecret()) : "");
    pageContext.setAttribute("connectionManagerSessions", connectionManagerSessions);
    pageContext.setAttribute("connectionManagerUserCounts", connectionManagerUserCounts);
%>

<p>
<fmt:message key="connection-manager.settings.info">
    <fmt:param value="<a href='connection-manager-session-summary.jsp'>"/>
    <fmt:param value="</a>"/>
</fmt:message>
</p>

<c:choose>
    <c:when test="${not empty errors}">
        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                    <c:when test="${err.key eq 'port'}"><fmt:message key="connection-manager.settings.valid.port" /></c:when>
                    <c:when test="${err.key eq 'defaultSecret'}"><fmt:message key="connection-manager.settings.valid.defaultSecret" /></c:when>
                    <c:otherwise>
                        <c:if test="${not empty err.value}">
                            <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                        </c:if>
                        (<c:out value="${err.key}"/>)
                    </c:otherwise>
                </c:choose>
            </admin:infobox>
        </c:forEach>
    </c:when>
    <c:when test="${not empty param.success and empty errors}">
        <admin:infoBox type="success">
            <fmt:message key="connection-manager.settings.confirm.updated" />
        </admin:infoBox>
    </c:when>
</c:choose>

<form action="connection-managers-settings.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <fmt:message key="connection-manager.settings.plaintext.boxtitle" var="plaintextboxtitle"/>
    <admin:contentBox title="${plaintextboxtitle}">

        <p><fmt:message key="connection-manager.settings.plaintext.info"/></p>

        <table>
            <tr>
                <td colspan="2"><input type="checkbox" name="plaintext-enabled" id="plaintext-enabled" ${plaintextConfiguration.enabled ? 'checked' : ''}/><label for="plaintext-enabled"><fmt:message key="connection-manager.settings.plaintext.label_enable"/></label></td>
            </tr>
            <tr id="plaintext-config">
                <td style="width: 1%; white-space: nowrap"><label for="plaintext-tcpPort"><fmt:message key="ports.port"/></label></td>
                <td><input type="text" name="plaintext-tcpPort" id="plaintext-tcpPort" value="${plaintextConfiguration.port}"/></td>
            </tr>
            <tr>
                <td colspan="2"><a href="./connection-settings-advanced.jsp?connectionType=CONNECTION_MANAGER&connectionMode=plain"><fmt:message key="connection-manager.settings.label_custom_info"/>...</a></td>
            </tr>
        </table>

    </admin:contentBox>

    <fmt:message key="connection-manager.settings.directtls.boxtitle" var="directtlsboxtitle"/>
    <admin:contentBox title="${directtlsboxtitle}">

        <p><fmt:message key="ssl.settings.client.directtls.info"/></p>

        <table>
            <tr>
                <td colspan="2"><input type="checkbox" name="directtls-enabled" id="directtls-enabled" ${directtlsConfiguration.enabled ? 'checked' : ''}/><label for="directtls-enabled"><fmt:message key="connection-manager.settings.directtls.label_enable"/></label></td>
            </tr>
            <tr id="directtls-config">
                <td style="width: 1%; white-space: nowrap"><label for="directtls-tcpPort"><fmt:message key="ports.port"/></label></td>
                <td><input type="text" name="directtls-tcpPort" id="directtls-tcpPort" value="${directtlsConfiguration.port}"></td>
            </tr>
            <tr>
                <td colspan="2"><a href="./connection-settings-advanced.jsp?connectionType=CONNECTION_MANAGER&connectionMode=directtls"><fmt:message key="connection-manager.settings.label_custom_info"/>...</a></td>
            </tr>
        </table>

    </admin:contentBox>

    <fmt:message key="connection-manager.settings.authentication" var="idleTitle"/>
    <admin:contentBox title="${idleTitle}">
        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap"><label for="defaultSecret"><fmt:message key="connection-manager.settings.defaultSecret" /></label></td>
                <td><input type="password" size="30" maxlength="150" id="defaultSecret" name="defaultSecret" value="${admin:escapeHTMLTags(defaultSecretHash != null ? defaultSecretHash : '')}"></td>
            </tr>
        </table>
    </admin:contentBox>

    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">

</form>

<c:if test="${plaintextConfiguration.enabled or directtlsConfiguration.enabled or not empty connectionManagerSessions}">

<br>

<style>
.connectionManagers {
    margin-top: 8px;
    border: 1px solid #DCDCDC;
    border-bottom: none;
    }
.connectionManagers tr.head {
    background-color: #F3F7FA;
    border-bottom: 1px solid red;
    }
.connectionManagers tr.head td {
    padding: 3px 6px 3px 6px;
    border-bottom: 1px solid #DCDCDC;
    }
.connectionManagers tr td {
    padding: 3px;
    border-bottom: 1px solid #DCDCDC;
    }
.connectionManagers tr td img {
    margin: 3px;
    }
</style>
<b><fmt:message key="connection-manager.details.title" >
        <fmt:param value="<%= XMPPServer.getInstance().getServerInfo().getXMPPDomain() %>" />
    </fmt:message>
</b>
<br>
<table class="connectionManagers">
    <tr class="head">
        <td><strong><fmt:message key="connection-manager.details.name" /></strong></td>
        <td><strong><fmt:message key="connection-manager.details.address" /></strong></td>
        <td style="text-align: center; width: 15%"><strong><fmt:message key="connection-manager.details.sessions" /></strong></td>
    </tr>
    <tbody>
        <c:choose>
            <c:when test="${empty connectionManagerSessions}">
                <tr>
                    <td colspan="3" style="text-align: center; white-space: nowrap"><fmt:message key="connection-manager.details.no-managers-connected" /></td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach var="muxManager" items="${connectionManagerSessions}">
                    <c:forEach var="muxSession" items="${muxManager.value}" varStatus="status">
                        <tr>
                            <td><img src="images/connection-manager_16x16.gif" alt="Connection Manager"><c:out value="${muxManager.key}"/></td>
                            <td><c:out value="${muxSession.hostAddress}"/> / <c:out value="${muxSession.hostName}"/></td>
                            <c:if test="${status.first}">
                                <td style="text-align: center" rowspan="${fn:length(muxManager.value)}">
                                    <fmt:formatNumber value="${connectionManagerUserCounts[muxManager.key]}"/>
                                </td>
                            </c:if>
                        </tr>
                    </c:forEach>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </tbody>
</table>
</c:if>

</body>
</html>
