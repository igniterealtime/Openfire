<%--
  -	$RCSfile$
  -	$Revision: 3195 $
  -	$Date: $
  -
  - Copyright (C) 2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.openfire.Connection,
                 org.jivesoftware.openfire.PrivateStorage"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.session.ClientSession" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="compression.settings.title"/></title>
<meta name="pageID" content="server-compression"/>
</head>
<body>

<%  // Get parameters:
    boolean update = request.getParameter("update") != null;
    boolean clientEnabled = ParamUtils.getBooleanParameter(request,"clientEnabled");
    boolean serverEnabled = ParamUtils.getBooleanParameter(request,"serverEnabled");

    // Get an audit manager:
    PrivateStorage privateStorage = webManager.getPrivateStore();

    if (update) {
        // Update c2s compression policy
        ClientSession.setCompressionPolicy(clientEnabled ? Connection.CompressionPolicy.optional : Connection.CompressionPolicy.disabled);
        // Update s2s compression policy
        JiveGlobals.setProperty("xmpp.server.compression.policy", serverEnabled ? Connection.CompressionPolicy.optional.toString() : Connection.CompressionPolicy.disabled.toString());
    %>
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="compression.settings.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
    <%

    }

    // Set page vars
    clientEnabled = Connection.CompressionPolicy.optional == ClientSession.getCompressionPolicy();
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