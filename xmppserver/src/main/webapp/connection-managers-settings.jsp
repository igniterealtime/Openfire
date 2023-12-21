<%@ page contentType="text/html; charset=UTF-8" %>
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

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
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
<%@ page import="java.util.Collection"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.List"%>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%  webManager.init(request, response, session, application, out); %>

<html>
<head>
    <title>
        <fmt:message key="connection-manager.settings.title"/></title>
        <meta name="pageID" content="connection-managers-settings"/>
    </head>
    <body>

<%  // Get parameters
    boolean update = request.getParameter("update") != null;
    boolean managerEnabled = ParamUtils.getBooleanParameter(request,"managerEnabled");
    int port = ParamUtils.getIntParameter(request,"port", 0);
    String defaultSecret = ParamUtils.getParameter(request,"defaultSecret");
    boolean updateSuccess = false;

    ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();

    // Update the session kick policy if requested
    Map<String, String> errors = new HashMap<>();
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
        if (managerEnabled) {
            if (defaultSecret == null || defaultSecret.trim().length() == 0) {
                errors.put("defaultSecret","");
            }
            if (port <= 0) {
                errors.put("port","");
            }
        }
        // If no errors, continue:
        if (errors.isEmpty()) {
            if (!managerEnabled) {
                connectionManager.enable(ConnectionType.CONNECTION_MANAGER, false, false);
                // Log the event
                webManager.logEvent("disabled connection manager settings", null);
            }
            else {
                connectionManager.enable(ConnectionType.CONNECTION_MANAGER, false, true);
                connectionManager.setPort(ConnectionType.CONNECTION_MANAGER, false, port);

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
                // Log the event
                webManager.logEvent("enabled connection manager settings", "port = "+port);
            }
            updateSuccess = true;
        }
    }

    // Set page vars
    if (errors.size() == 0) {
        managerEnabled = connectionManager.isEnabled(ConnectionType.CONNECTION_MANAGER, false);
        port = connectionManager.getPort(ConnectionType.CONNECTION_MANAGER, false);
        defaultSecret = ConnectionMultiplexerManager.getDefaultSecret();
    }
    else {
        if (port == 0) {
            port = connectionManager.getPort(ConnectionType.CONNECTION_MANAGER, false);
        }
        if (defaultSecret == null) {
            defaultSecret = ConnectionMultiplexerManager.getDefaultSecret();
        }
    }
    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("updateSuccess", updateSuccess);
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
    <c:when test="${updateSuccess}">
        <admin:infoBox type="success">
            <fmt:message key="connection-manager.settings.confirm.updated" />
        </admin:infoBox>
    </c:when>
</c:choose>

<form action="connection-managers-settings.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

<fieldset>
    <div>
    <table>
    <tbody>
        <tr>
            <td style="width: 1%; white-space: nowrap">
                <input type="radio" name="managerEnabled" value="false" id="rb01" <%= (!managerEnabled ? "checked" : "") %>>
            </td>
            <td>
                <label for="rb01">
                <b><fmt:message key="connection-manager.settings.label_disable" /></b> - <fmt:message key="connection-manager.settings.label_disable_info" />
                </label>
            </td>
        </tr>
        <tr>
            <td style="width: 1%; white-space: nowrap">
                <input type="radio" name="managerEnabled" value="true" id="rb02" <%= (managerEnabled ? "checked" : "") %>>
            </td>
            <td>
                <label for="rb02">
                <b><fmt:message key="connection-manager.settings.label_enable" /></b> - <fmt:message key="connection-manager.settings.label_enable_info" />
                </label>
            </td>
        </tr>
        <tr>
            <td style="width: 1%; white-space: nowrap">
                &nbsp;
            </td>
            <td>
                <table>
                <tr>
                    <td style="width: 1%; white-space: nowrap; text-align: right;" class="c1">
                        <label for="port"><fmt:message key="connection-manager.settings.port" /></label>
                    </td>
                    <td>
                        <input type="text" size="10" maxlength="50" id="port" name="port" value="<%= port %>">
                    </td>
                </tr>
                <tr>
                    <td style="width: 1%; white-space: nowrap; text-align: right;" class="c1">
                        <label for="defaultSecret"><fmt:message key="connection-manager.settings.defaultSecret" /></label>
                    </td>
                    <td>
                        <input type="password" size="30" maxlength="150" id="defaultSecret" name="defaultSecret" value="<%= ((defaultSecret != null) ? StringUtils.hash(defaultSecret) : "") %>">
                    </td>
                </tr>
                </table>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>
<br>

<input type="submit" name="update" value="<fmt:message key="global.save_settings" />">

</form>

<% if (managerEnabled) { %>

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
<%
    ConnectionMultiplexerManager multiplexerManager = ConnectionMultiplexerManager.getInstance();
    SessionManager sessionManager = SessionManager.getInstance();
    Collection<String> connectionManagers = multiplexerManager.getMultiplexers();
    if (connectionManagers.isEmpty()) {
%>
    <tr>
        <td colspan="3" style="text-align: center; white-space: nowrap"><fmt:message key="connection-manager.details.no-managers-connected" /></td>
    </tr>
<% } else {
    for (String managerName : connectionManagers) {
        List<ConnectionMultiplexerSession> sessions = sessionManager.getConnectionMultiplexerSessions(managerName);
        if (sessions.isEmpty()) {
            continue;
        }
        String hostAddress = sessions.get(0).getHostAddress();
        String hostName = sessions.get(0).getHostName();
%>
<tr>
    <td><img src="images/connection-manager_16x16.gif" alt="Connection Manager"><%= managerName%></td>
    <td><%= hostAddress %> / <%= hostName %></td>
    <td style="text-align: center"><%= multiplexerManager.getNumConnectedClients(managerName)%></td>
</tr>
<%
        }
    }
%>
</tbody>
</table>


<% } %>

</body>
</html>
