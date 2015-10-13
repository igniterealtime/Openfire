<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
                 org.jivesoftware.openfire.ConnectionManager,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.server.ServerDialback,
                 org.jivesoftware.openfire.session.LocalClientSession,
                 org.jivesoftware.util.JiveGlobals"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.session.ConnectionSettings" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%  try { %>

<% // Get parameters:
    boolean update = request.getParameter("update") != null;
    boolean success = ParamUtils.getBooleanParameter(request, "success");
    // Client configuration parameters
    String clientSecurityRequired = ParamUtils.getParameter(request, "clientSecurityRequired");
    String ssl = ParamUtils.getParameter(request, "ssl");
    String tls = ParamUtils.getParameter(request, "tls");
	String clientMutualAuthenticationSocket = ParamUtils.getParameter(request, "clientMutualAuthenticationSocket");
	String clientMutualAuthenticationBOSH   = ParamUtils.getParameter(request, "clientMutualAuthenticationBOSH");
    // Server configuration parameters
    String serverSecurityRequired = ParamUtils.getParameter(request, "serverSecurityRequired");
    String dialback = ParamUtils.getParameter(request, "dialback");
    String server_tls = ParamUtils.getParameter(request, "server_tls");
    boolean selfSigned = ParamUtils.getBooleanParameter(request, "selfSigned");

    if (update) {
        if ("req".equals(clientSecurityRequired)) {
            // User selected that security is required

            // Enable 5222 port and make TLS required
            XMPPServer.getInstance().getConnectionManager().enableClientListener(true);
            LocalClientSession.setTLSPolicy(Connection.TLSPolicy.required);
            // Enable 5223 port (old SSL port)
            XMPPServer.getInstance().getConnectionManager().enableClientSSLListener(true);
        } else if ("notreq".equals(clientSecurityRequired)) {
            // User selected that security is NOT required

            // Enable 5222 port and make TLS optional
            XMPPServer.getInstance().getConnectionManager().enableClientListener(true);
            LocalClientSession.setTLSPolicy(Connection.TLSPolicy.optional);
            // Enable 5223 port (old SSL port)
            XMPPServer.getInstance().getConnectionManager().enableClientSSLListener(true);
        } else if ("custom".equals(clientSecurityRequired)) {
            // User selected custom client authentication

            // Enable or disable 5223 port (old SSL port)
            XMPPServer.getInstance().getConnectionManager().enableClientSSLListener("available".equals(ssl));

            // Enable port 5222 and configure TLS policy
            XMPPServer.getInstance().getConnectionManager().enableClientListener(true);
            if ("notavailable".equals(tls)) {
                LocalClientSession.setTLSPolicy(Connection.TLSPolicy.disabled);
            } else if ("optional".equals(tls)) {
                LocalClientSession.setTLSPolicy(Connection.TLSPolicy.optional);
            } else {
                LocalClientSession.setTLSPolicy(Connection.TLSPolicy.required);
            }
        }

        if ("req".equals(serverSecurityRequired)) {
            // User selected that security for s2s is required

            // Enable TLS and disable server dialback
            XMPPServer.getInstance().getConnectionManager().enableServerListener(true);
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ENABLED, "true");
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
        } else if ("notreq".equals(serverSecurityRequired)) {
            // User selected that security for s2s is NOT required

            // Enable TLS and enable server dialback
            XMPPServer.getInstance().getConnectionManager().enableServerListener(true);
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ENABLED, "true");
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
        } else if ("custom".equals(serverSecurityRequired)) {
            // User selected custom server authentication

            boolean dialbackEnabled = "available".equals(dialback);
            boolean tlsEnabled = "optional".equals(server_tls);

            if (dialbackEnabled || tlsEnabled) {
                XMPPServer.getInstance().getConnectionManager().enableServerListener(true);

                // Enable or disable server dialback
                JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, dialbackEnabled ? "true" : "false");

                // Enable or disable TLS for s2s connections
                JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ENABLED, tlsEnabled ? "true" : "false");
            } else {
                XMPPServer.getInstance().getConnectionManager().enableServerListener(false);
                // Disable server dialback
                JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");

                // Disable TLS for s2s connections
                JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ENABLED, "false");
            }
        }
        ServerDialback.setEnabledForSelfSigned(selfSigned);

		JiveGlobals.setProperty("xmpp.client.cert.policy", clientMutualAuthenticationSocket);
		JiveGlobals.setProperty("httpbind.client.cert.policy", clientMutualAuthenticationBOSH);

		success = true;
        // Log the event
        webManager.logEvent("updated SSL configuration",
                ConnectionSettings.Server.DIALBACK_ENABLED + " = " + JiveGlobals.getProperty(ConnectionSettings.Server.DIALBACK_ENABLED) + "\n" +
                ConnectionSettings.Server.TLS_ENABLED      + " = " + JiveGlobals.getProperty(ConnectionSettings.Server.TLS_ENABLED) + "\n" +
                "xmpp.client.cert.policy = "                       + JiveGlobals.getProperty("xmpp.client.cert.policy") + "\n" +
                "httpbind.client.cert.policy = "                   + JiveGlobals.getProperty("httpbind.client.cert.policy")
		);
    }

    // Set page vars
    ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
    if (connectionManager.isClientListenerEnabled() && connectionManager.isClientSSLListenerEnabled()) {
        if (Connection.TLSPolicy.required.equals(LocalClientSession.getTLSPolicy())) {
            clientSecurityRequired = "req";
            ssl = "available";
            tls = "required";
        } else if (Connection.TLSPolicy.optional.equals(LocalClientSession.getTLSPolicy())) {
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
        tls = Connection.TLSPolicy.disabled.equals(LocalClientSession.getTLSPolicy()) ? "notavailable" :
                LocalClientSession.getTLSPolicy().toString();
    }

    boolean tlsEnabled = JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ENABLED, true);
    boolean dialbackEnabled = JiveGlobals.getBooleanProperty(ConnectionSettings.Server.DIALBACK_ENABLED, true);
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
    selfSigned = ServerDialback.isEnabledForSelfSigned();

    clientMutualAuthenticationSocket = JiveGlobals.getProperty( "xmpp.client.cert.policy",     "disabled" );
    clientMutualAuthenticationBOSH   = JiveGlobals.getProperty( "httpbind.client.cert.policy", "disabled" );

    if ( !"disabled".equals( clientMutualAuthenticationSocket ) || !"disabled".equals( clientMutualAuthenticationBOSH ) ) {
        clientSecurityRequired = "custom";
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
    <admin:infobox type="success"><fmt:message key="ssl.settings.update" /></admin:infobox>
<%  } %>

<c:if test="${param.deletesuccess}">
    <admin:infobox type="success"><fmt:message key="ssl.settings.uninstalled" /></admin:infobox>
</c:if>

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
						<tr valign="top">
							<td width="1%" nowrap>
								<fmt:message key="ssl.settings.client.custom.mutualauth.socket" />
							</td>
							<td width="99%">
								<input type="radio" name="clientMutualAuthenticationSocket" value="disabled" id="rb16" <%= ("disabled".equals(clientMutualAuthenticationSocket) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb16"><fmt:message key="ssl.settings.notavailable" /></label>&nbsp;&nbsp;
								<input type="radio" name="clientMutualAuthenticationSocket" value="wanted" id="rb17" <%= ("wanted".equals(clientMutualAuthenticationSocket) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb17"><fmt:message key="ssl.settings.optional" /></label>&nbsp;&nbsp;
								<input type="radio" name="clientMutualAuthenticationSocket" value="needed" id="rb18" <%= ("needed".equals(clientMutualAuthenticationSocket) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb18"><fmt:message key="ssl.settings.required" /></label>
							</td>
						</tr>
						<tr valign="top">
							<td width="1%" nowrap>
								<fmt:message key="ssl.settings.client.custom.mutualauth.bosh" />
							</td>
							<td width="99%">
								<input type="radio" name="clientMutualAuthenticationBOSH" value="disabled" id="rb19" <%= ("disabled".equals(clientMutualAuthenticationBOSH) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb19"><fmt:message key="ssl.settings.notavailable" /></label>&nbsp;&nbsp;
								<input type="radio" name="clientMutualAuthenticationBOSH" value="wanted" id="rb20" <%= ("wanted".equals(clientMutualAuthenticationBOSH) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb20"><fmt:message key="ssl.settings.optional" /></label>&nbsp;&nbsp;
								<input type="radio" name="clientMutualAuthenticationBOSH" value="needed" id="rb21" <%= ("needed".equals(clientMutualAuthenticationBOSH) ? "checked" : "") %>
									   onclick="this.form.clientSecurityRequired[2].checked=true;">&nbsp;<label for="rb21"><fmt:message key="ssl.settings.required" /></label>
							</td>
						</tr>
						</table>
					</td>
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
                <tr valign="middle">
                    <td width="1%" nowrap>
                        <input type="checkbox" name="selfSigned" id="cb02" <%= (selfSigned ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <label for="rb02">
                        <fmt:message key="ssl.settings.client.label_self-signed" />
                        </label>
                    </td>
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