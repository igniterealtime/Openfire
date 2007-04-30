<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.openfire.Connection,
                 org.jivesoftware.openfire.ConnectionManager,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.session.ClientSession"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%  try { %>

<% // Get parameters:
    boolean update = request.getParameter("update") != null;
    boolean success = ParamUtils.getBooleanParameter(request, "success");
    // Client configuration parameters
    String clientSecurityRequired = ParamUtils.getParameter(request, "clientSecurityRequired");
    String ssl = ParamUtils.getParameter(request, "ssl");
    String tls = ParamUtils.getParameter(request, "tls");
    // Server configuration parameters
    String serverSecurityRequired = ParamUtils.getParameter(request, "serverSecurityRequired");
    String dialback = ParamUtils.getParameter(request, "dialback");
    String server_tls = ParamUtils.getParameter(request, "server_tls");

    if (update) {
        if ("req".equals(clientSecurityRequired)) {
            // User selected that security is required

            // Enable 5222 port and make TLS required
            XMPPServer.getInstance().getConnectionManager().enableClientListener(true);
            ClientSession.setTLSPolicy(Connection.TLSPolicy.required);
            // Enable 5223 port (old SSL port)
            XMPPServer.getInstance().getConnectionManager().enableClientSSLListener(true);
        } else if ("notreq".equals(clientSecurityRequired)) {
            // User selected that security is NOT required

            // Enable 5222 port and make TLS optional
            XMPPServer.getInstance().getConnectionManager().enableClientListener(true);
            ClientSession.setTLSPolicy(Connection.TLSPolicy.optional);
            // Enable 5223 port (old SSL port)
            XMPPServer.getInstance().getConnectionManager().enableClientSSLListener(true);
        } else if ("custom".equals(clientSecurityRequired)) {
            // User selected custom client authentication

            // Enable or disable 5223 port (old SSL port)
            XMPPServer.getInstance().getConnectionManager().enableClientSSLListener("available".equals(ssl));

            // Enable port 5222 and configure TLS policy
            XMPPServer.getInstance().getConnectionManager().enableClientListener(true);
            if ("notavailable".equals(tls)) {
                ClientSession.setTLSPolicy(Connection.TLSPolicy.disabled);
            } else if ("optional".equals(tls)) {
                ClientSession.setTLSPolicy(Connection.TLSPolicy.optional);
            } else {
                ClientSession.setTLSPolicy(Connection.TLSPolicy.required);
            }
        }

        if ("req".equals(serverSecurityRequired)) {
            // User selected that security for s2s is required

            // Enable TLS and disable server dialback
            XMPPServer.getInstance().getConnectionManager().enableServerListener(true);
            JiveGlobals.setProperty("xmpp.server.tls.enabled", "true");
            JiveGlobals.setProperty("xmpp.server.dialback.enabled", "false");
        } else if ("notreq".equals(serverSecurityRequired)) {
            // User selected that security for s2s is NOT required

            // Enable TLS and enable server dialback
            XMPPServer.getInstance().getConnectionManager().enableServerListener(true);
            JiveGlobals.setProperty("xmpp.server.tls.enabled", "true");
            JiveGlobals.setProperty("xmpp.server.dialback.enabled", "true");
        } else if ("custom".equals(serverSecurityRequired)) {
            // User selected custom server authentication

            boolean dialbackEnabled = "available".equals(dialback);
            boolean tlsEnabled = "optional".equals(server_tls);

            if (dialbackEnabled || tlsEnabled) {
                XMPPServer.getInstance().getConnectionManager().enableServerListener(true);

                // Enable or disable server dialback
                JiveGlobals.setProperty("xmpp.server.dialback.enabled", dialbackEnabled ? "true" : "false");

                // Enable or disable TLS for s2s connections
                JiveGlobals.setProperty("xmpp.server.tls.enabled", tlsEnabled ? "true" : "false");
            } else {
                XMPPServer.getInstance().getConnectionManager().enableServerListener(false);
                // Disable server dialback
                JiveGlobals.setProperty("xmpp.server.dialback.enabled", "false");

                // Disable TLS for s2s connections
                JiveGlobals.setProperty("xmpp.server.tls.enabled", "false");
            }
        }
        success = true;
    }

    // Set page vars
    ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
    if (connectionManager.isClientListenerEnabled() && connectionManager.isClientSSLListenerEnabled()) {
        if (Connection.TLSPolicy.required.equals(org.jivesoftware.openfire.session.ClientSession.getTLSPolicy())) {
            clientSecurityRequired = "req";
            ssl = "available";
            tls = "required";
        } else if (Connection.TLSPolicy.optional.equals(ClientSession.getTLSPolicy())) {
            clientSecurityRequired = "notreq";
            ssl = "available";
            tls = "optional";
        } else {
            clientSecurityRequired = "custom";
            ssl = "available";
            tls = "notavailable";
        }
    } else {
        clientSecurityRequired = "custom";
        ssl = connectionManager.isClientSSLListenerEnabled() ? "available" : "notavailable";
        tls = Connection.TLSPolicy.disabled.equals(ClientSession.getTLSPolicy()) ? "notavailable" :
                ClientSession.getTLSPolicy().toString();
    }

    boolean tlsEnabled = JiveGlobals.getBooleanProperty("xmpp.server.tls.enabled", true);
    boolean dialbackEnabled = JiveGlobals.getBooleanProperty("xmpp.server.dialback.enabled", true);
    if (tlsEnabled) {
        if (dialbackEnabled) {
            serverSecurityRequired = "notreq";
            dialback = "available";
            server_tls = "optional";
        } else {
            serverSecurityRequired = "req";
            dialback = "notavailable";
            server_tls = "optional";
        }
    } else {
        serverSecurityRequired = "custom";
        dialback = dialbackEnabled ? "available" : "notavailable";
        server_tls = "notavailable";
    }
%>

<html>
<head>
<title><fmt:message key="ssl.settings.title"/></title>
<meta name="pageID" content="server-ssl"/>
<meta name="helpPage" content="manage_security_certificates.html"/>
<script language="JavaScript" type="text/javascript">
	<!-- // code for window popups
	function showOrHide(whichLayer, mode)
	{

		if (mode == "show") {
			mode = "";
		}
		else {
			mode = "none";
		}

		if (document.getElementById)
		{
			// this is the way the standards work
			var style2 = document.getElementById(whichLayer).style;
			style2.display = mode;
		}
		else if (document.all)
		{
			// this is the way old msie versions work
			var style2 = document.all[whichLayer].style;
			style2.display = mode;
		}
		else if (document.layers)
		{
			// this is the way nn4 works
			var style2 = document.layers[whichLayer].style;
			style2.display = mode;
		}
	}
	//-->
</script>
</head>
<body>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="ssl.settings.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (ParamUtils.getBooleanParameter(request,"deletesuccess")) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="ssl.settings.uninstalled" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
<fmt:message key="ssl.settings.client.info" />
</p>


<!-- BEGIN 'Client Connection Security' -->
<form action="ssl-settings.jsp" method="post">
	<div class="jive-contentBox" style="-moz-border-radius: 3px;">
	<h4><fmt:message key="ssl.settings.client.legend" /></h4>
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
			<tr valign="middle">
				<tr valign="middle">
					<td width="1%" nowrap>
						<input type="radio" name="clientSecurityRequired" value="notreq" id="rb02" onclick="showOrHide('custom', 'hide')"
						 <%= ("notreq".equals(clientSecurityRequired) ? "checked" : "") %>>
					</td>
					<td width="99%">
						<label for="rb02">
						<b><fmt:message key="ssl.settings.client.label_notrequired" /></b> - <fmt:message key="ssl.settings.client.label_notrequired_info" />
						</label>
					</td>
				</tr>
				<tr valign="middle">
					<td width="1%" nowrap>
						<input type="radio" name="clientSecurityRequired" value="req" id="rb01" onclick="showOrHide('custom', 'hide')"
					 <%= ("req".equals(clientSecurityRequired) ? "checked" : "") %>>
					</td>
					<td width="99%">
						<label for="rb01">
						<b><fmt:message key="ssl.settings.client.label_required" /></b> - <fmt:message key="ssl.settings.client.label_required_info" />
						</label>
					</td>
				</tr>
				<tr valign="middle">
					<td width="1%" nowrap>
						<input type="radio" name="clientSecurityRequired" value="custom" id="rb03" onclick="showOrHide('custom', 'show')"
						 <%= ("custom".equals(clientSecurityRequired) ? "checked" : "") %>>
					</td>
					<td width="99%">
						<label for="rb03">
						<b><fmt:message key="ssl.settings.client.label_custom" /></b> - <fmt:message key="ssl.settings.client.label_custom_info" />
						</label>
					</td>
				</tr>
				<tr valign="top" id="custom" <% if (!"custom".equals(clientSecurityRequired)) out.write("style=\"display:none\""); %>>
					<td width="1%" nowrap>
						&nbsp;
					</td>
					<td width="99%">
						<table cellpadding="3" cellspacing="0" border="0">
						<tr valign="top">
							<td width="1%" nowrap>
								<fmt:message key="ssl.settings.client.customSSL" />
							</td>
							<td width="99%">
								<input type="radio" name="ssl" value="notavailable" id="rb04" <%= ("notavailable".equals(ssl) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb04"><fmt:message key="ssl.settings.notavailable" /></label>&nbsp;&nbsp;
								<input type="radio" name="ssl" value="available" id="rb05" <%= ("available".equals(ssl) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb05"><fmt:message key="ssl.settings.available" /></label>
							</td>
						</tr>
						<tr valign="top">
							<td width="1%" nowrap>
								<fmt:message key="ssl.settings.client.customTLS" />
							</td>
							<td width="99%">
								<input type="radio" name="tls" value="notavailable" id="rb06" <%= ("notavailable".equals(tls) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb06"><fmt:message key="ssl.settings.notavailable" /></label>&nbsp;&nbsp;
								<input type="radio" name="tls" value="optional" id="rb07" <%= ("optional".equals(tls) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb07"><fmt:message key="ssl.settings.optional" /></label>&nbsp;&nbsp;
								<input type="radio" name="tls" value="required" id="rb08" <%= ("required".equals(tls) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb08"><fmt:message key="ssl.settings.required" /></label>
							</td>
						</tr>
						</table>
					</td>
				</tr>
			</tr>
		</tbody>
		</table>


<!-- END 'Client Connection Security' -->

    <br/>
    <br/>

<!-- BEGIN 'Server Connection Security' -->

    <h4><fmt:message key="ssl.settings.server.legend" /></h4>
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
			<tr valign="middle">
				<tr valign="middle">
					<td width="1%" nowrap>
						<input type="radio" name="serverSecurityRequired" value="notreq" id="rb09" onclick="showOrHide('server_custom', 'hide')"
						 <%= ("notreq".equals(serverSecurityRequired) ? "checked" : "") %>>
					</td>
					<td width="99%">
						<label for="rb09">
						<b><fmt:message key="ssl.settings.server.label_notrequired" /></b> - <fmt:message key="ssl.settings.server.label_notrequired_info" />
						</label>
					</td>
				</tr>
				<tr valign="middle">
					<td width="1%" nowrap>
						<input type="radio" name="serverSecurityRequired" value="req" id="rb10" onclick="showOrHide('server_custom', 'hide')"
					 <%= ("req".equals(serverSecurityRequired) ? "checked" : "") %>>
					</td>
					<td width="99%">
						<label for="rb10">
						<b><fmt:message key="ssl.settings.server.label_required" /></b> - <fmt:message key="ssl.settings.server.label_required_info" />
						</label>
					</td>
				</tr>
				<tr valign="middle">
					<td width="1%" nowrap>
						<input type="radio" name="serverSecurityRequired" value="custom" id="rb11" onclick="showOrHide('server_custom', 'show')"
						 <%= ("custom".equals(serverSecurityRequired) ? "checked" : "") %>>
					</td>
					<td width="99%">
						<label for="rb11">
						<b><fmt:message key="ssl.settings.server.label_custom" /></b> - <fmt:message key="ssl.settings.server.label_custom_info" />
						</label>
					</td>
				</tr>
				<tr valign="top" id="server_custom" <% if (!"custom".equals(serverSecurityRequired)) out.write("style=\"display:none\""); %>>
					<td width="1%" nowrap>
						&nbsp;
					</td>
					<td width="99%">
						<table cellpadding="3" cellspacing="0" border="0" width="100%">
						<tr valign="top">
							<td width="1%" nowrap>
								<fmt:message key="ssl.settings.server.dialback" />
							</td>
							<td width="99%">
								<input type="radio" name="dialback" value="notavailable" id="rb12" <%= ("notavailable".equals(dialback) ? "checked" : "") %>
									   onclick="this.form.serverSecurityRequired[2].checked=true;">&nbsp;<label for="rb12"><fmt:message key="ssl.settings.notavailable" /></label>&nbsp;&nbsp;
								<input type="radio" name="dialback" value="available" id="rb13" <%= ("available".equals(dialback) ? "checked" : "") %>
									   onclick="this.form.serverSecurityRequired[2].checked=true;">&nbsp;<label for="rb13"><fmt:message key="ssl.settings.available" /></label>
							</td>
						</tr>
						<tr valign="top">
							<td width="1%" nowrap>
								<fmt:message key="ssl.settings.server.customTLS" />
							</td>
							<td width="99%">
								<input type="radio" name="server_tls" value="notavailable" id="rb14" <%= ("notavailable".equals(server_tls) ? "checked" : "") %>
									   onclick="this.form.serverSecurityRequired[2].checked=true;">&nbsp;<label for="rb14"><fmt:message key="ssl.settings.notavailable" /></label>&nbsp;&nbsp;
								<input type="radio" name="server_tls" value="optional" id="rb15" <%= ("optional".equals(server_tls) ? "checked" : "") %>
									   onclick="this.form.serverSecurityRequired[2].checked=true;">&nbsp;<label for="rb15"><fmt:message key="ssl.settings.optional" /></label>&nbsp;&nbsp;
							</td>
						</tr>
						</table>
					</td>
				</tr>
			</tr>
		</tbody>
		</table>
	</div>

    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
<!-- BEGIN 'Server Connection Security' -->

</body>
</html>

<%  } catch (Throwable t) { t.printStackTrace(); } %>