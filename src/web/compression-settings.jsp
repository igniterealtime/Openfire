<%--
  -	$RCSfile$
  -	$Revision: 3195 $
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

<%@ page import="org.jivesoftware.openfire.Connection,
                 org.jivesoftware.openfire.session.LocalClientSession,
                 org.jivesoftware.util.JiveGlobals"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.ParamUtils" %>

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

    if (update) {
        // Update c2s compression policy
        LocalClientSession.setCompressionPolicy(
                clientEnabled ? Connection.CompressionPolicy.optional : Connection.CompressionPolicy.disabled);
        // Update s2s compression policy
        JiveGlobals.setProperty("xmpp.server.compression.policy", serverEnabled ?
                Connection.CompressionPolicy.optional.toString() : Connection.CompressionPolicy.disabled.toString());
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
    clientEnabled = Connection.CompressionPolicy.optional == LocalClientSession.getCompressionPolicy();
    serverEnabled = Connection.CompressionPolicy.optional.toString().equals(JiveGlobals.getProperty("xmpp.server.compression.policy", Connection.CompressionPolicy.disabled.toString()));
%>

<p>
<fmt:message key="compression.settings.info" />
</p>


<!-- BEGIN compression settings -->
<form action="compression-settings.jsp">

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