<%--
  -	$RCSfile$
  -	$Revision: $
  -	$Date: $
  -
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.wildfire.ConnectionManager,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.wildfire.multiplex.ConnectionMultiplexerManager"
    errorPage="error.jsp"
%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Map"%>

<html>
    <head>
        <title><fmt:message key="connection-manager.settings.title"/></title>
        <meta name="pageID" content="connection-managers-settings"/>
    </head>
    <body>

<%  // Get parameters
    boolean update = request.getParameter("update") != null;
    boolean managerEnabled = ParamUtils.getBooleanParameter(request,"managerEnabled");
    int port = ParamUtils.getIntParameter(request,"port", 0);
    String defaultSecret = ParamUtils.getParameter(request,"defaultSecret");
    String secret = ParamUtils.getParameter(request,"secret");
    boolean updateSucess = false;

    String serverName = XMPPServer.getInstance().getServerInfo().getName();
    ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();


    // Update the session kick policy if requested
    Map<String, String> errors = new HashMap<String, String>();
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
                connectionManager.enableConnectionManagerListener(false);
            }
            else {
                connectionManager.enableConnectionManagerListener(true);
                connectionManager.setConnectionManagerListenerPort(port);
                ConnectionMultiplexerManager.setDefaultSecret(defaultSecret);
            }
            updateSucess = true;
        }
    }

    // Set page vars
    if (errors.size() == 0) {
        managerEnabled = connectionManager.isConnectionManagerListenerEnabled();
        port = connectionManager.getConnectionManagerListenerPort();
        defaultSecret = ConnectionMultiplexerManager.getDefaultSecret();
        secret = "";
    }
    else {
        if (port == 0) {
            port = connectionManager.getConnectionManagerListenerPort();
        }
        if (defaultSecret == null) {
            defaultSecret = ConnectionMultiplexerManager.getDefaultSecret();
        }
        if (secret == null) {
            secret = "";
        }
    }
%>

<p>
<fmt:message key="connection-manager.settings.info">
    <fmt:param value="<%= "<a href='connection-manager-session-summary.jsp'>" %>" />
    <fmt:param value="<%= "</a>" %>" />
</fmt:message>
</p>

<%  if (!errors.isEmpty()) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"/></td>
            <td class="jive-icon-label">

            <% if (errors.get("port") != null) { %>
                <fmt:message key="connection-manager.settings.valid.port" />
            <% } else if (errors.get("defaultSecret") != null) { %>
                <fmt:message key="connection-manager.settings.valid.defaultSecret" />
            <% } %>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<%  } else if (updateSucess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="connection-manager.settings.confirm.updated" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="connection-managers-settings.jsp" method="post">

<fieldset>
    <legend><fmt:message key="connection-manager.settings.enabled.legend" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="middle">
            <td width="1%" nowrap>
                <input type="radio" name="managerEnabled" value="false" id="rb01"
                 <%= (!managerEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01">
                <b><fmt:message key="connection-manager.settings.label_disable" /></b> - <fmt:message key="connection-manager.settings.label_disable_info" />
                </label>
            </td>
        </tr>
        <tr valign="middle">
            <td width="1%" nowrap>
                <input type="radio" name="managerEnabled" value="true" id="rb02"
                 <%= (managerEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02">
                <b><fmt:message key="connection-manager.settings.label_enable" /></b> - <fmt:message key="connection-manager.settings.label_enable_info" />
                </label>
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                &nbsp;
            </td>
            <td width="99%">
                <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tr valign="top">
                    <td width="1%" nowrap class="c1">
                        <fmt:message key="connection-manager.settings.port" />
                    </td>
                    <td width="99%">
                        <input type="text" size="10" maxlength="50" name="port"
                         value="<%= port %>">
                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap class="c1">
                        <fmt:message key="connection-manager.settings.defaultSecret" />
                    </td>
                    <td width="99%">
                        <input type="text" size="15" maxlength="70" name="defaultSecret"
                         value="<%= ((defaultSecret != null) ? defaultSecret : "") %>">
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

<br>

<table>
    <tr>
        <td><%= serverName %></td>
        <td>
            <table>
                <%
                    ConnectionMultiplexerManager multiplexerManager = ConnectionMultiplexerManager.getInstance();
                    for (String managerName : multiplexerManager.getMultiplexers()) {
                %>
                <tr>
                    <td><%= managerName%>, <%= multiplexerManager.getNumConnectedClients(managerName)%> <fmt:message key="connection-manager.sessions" />, XXX <fmt:message key="connection-manager.packetsRate" /></td>
                </tr>
                <%
                    }
                %>
            </table>
        </td>
    </tr>
</table>

    </body>
</html>
