<%@ page contentType="text/html; charset=UTF-8" %>
<%--
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

<%@ page errorPage="error.jsp" %>
<%@ page import="org.jivesoftware.openfire.Connection" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionManagerImpl" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="compression.settings.title"/></title>
<meta name="pageID" content="server-compression"/>
</head>
<body>

<% // Get parameters:
    boolean update = request.getParameter("update") != null;
    boolean clientEnabled = ParamUtils.getBooleanParameter(request, "clientEnabled");
    boolean serverEnabled = ParamUtils.getBooleanParameter(request, "serverEnabled");

    final ConnectionManagerImpl connectionManager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (update) {
        // Update c2s compression policy
        final Connection.CompressionPolicy newClientPolicy = clientEnabled ? Connection.CompressionPolicy.optional : Connection.CompressionPolicy.disabled;
        connectionManager.getListener( ConnectionType.SOCKET_C2S, false ).setCompressionPolicy( newClientPolicy );
        connectionManager.getListener( ConnectionType.SOCKET_C2S, true  ).setCompressionPolicy( newClientPolicy );
        connectionManager.getListener( ConnectionType.BOSH_C2S,   false ).setCompressionPolicy( newClientPolicy );
        connectionManager.getListener( ConnectionType.BOSH_C2S,   true  ).setCompressionPolicy( newClientPolicy );

        // Update s2s compression policy
        final Connection.CompressionPolicy newServerPolicy = serverEnabled ? Connection.CompressionPolicy.optional : Connection.CompressionPolicy.disabled;
        connectionManager.getListener( ConnectionType.SOCKET_S2S, false ).setCompressionPolicy( newServerPolicy );

        // TODO Add components, connection managers
        // Log the event
        webManager.logEvent("set compression policy", "c2s compression = "+clientEnabled+"\ns2s compression = "+serverEnabled);
%>
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="compression.settings.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
    <%

    }

    // Set page vars
    clientEnabled = Connection.CompressionPolicy.optional.equals( connectionManager.getListener( ConnectionType.SOCKET_C2S, false ).getCompressionPolicy() );
        serverEnabled = Connection.CompressionPolicy.optional.equals( connectionManager.getListener( ConnectionType.SOCKET_S2S, false ).getCompressionPolicy() );
%>

<p>
<fmt:message key="compression.settings.info" />
</p>


<!-- BEGIN compression settings -->
<form action="compression-settings.jsp">
    <input type="hidden" name="csrf" value="${csrf}">

    <div class="jive-contentBox" style="-moz-border-radius: 3px;">

    <h4><fmt:message key="compression.settings.client.policy" /></h4>
    <table cellpadding="3" cellspacing="0" border="0">
    <tbody>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="clientEnabled" value="true" id="rb01"
                 <%= (clientEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01">
                <b><fmt:message key="compression.settings.client.enable" /></b> -
                <fmt:message key="compression.settings.client.enable_info" />
                </label>
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="clientEnabled" value="false" id="rb02"
                 <%= (!clientEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02">
                <b><fmt:message key="compression.settings.client.disable" /></b> -
                <fmt:message key="compression.settings.client.disable_info" />
                </label>
            </td>
        </tr>
    </tbody>
    </table>

    <br>
    <br>

    <h4><fmt:message key="compression.settings.server.policy" /></h4>
    <table cellpadding="3" cellspacing="0" border="0">
    <tbody>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="serverEnabled" value="true" id="rb03"
             <%= (serverEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb03">
                <b><fmt:message key="compression.settings.server.enable" /></b> -
                <fmt:message key="compression.settings.server.enable_info" />
                </label>
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="serverEnabled" value="false" id="rb04"
             <%= (!serverEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb04">
                <b><fmt:message key="compression.settings.server.disable" /></b> -
                <fmt:message key="compression.settings.server.disable_info" />
                </label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END compression settings -->


</body>
</html>
