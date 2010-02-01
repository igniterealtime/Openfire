<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2010 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%@ page import="org.jivesoftware.util.JiveGlobals,
				 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.openfire.ConnectionManager,
                 java.util.Collection"
    errorPage="error.jsp"
%>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Map" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="client.connections.settings.title"/></title>
<meta name="pageID" content="client-connections-settings"/>
</head>
<body>

<%  // Get parameters
	int port = ParamUtils.getIntParameter(request, "port", -1);
	int sslPort = ParamUtils.getIntParameter(request, "sslPort", -1);
	int clientIdle = 1000* ParamUtils.getIntParameter(request, "clientIdle", -1);
    boolean idleDisco = ParamUtils.getBooleanParameter(request, "idleDisco");
    boolean pingIdleClients = ParamUtils.getBooleanParameter(request, "pingIdleClients");
    boolean sslEnabled = ParamUtils.getBooleanParameter(request, "sslEnabled");
    boolean save = request.getParameter("update") != null;
    boolean defaults = request.getParameter("defaults") != null;

    if (defaults) {
        port = ConnectionManager.DEFAULT_PORT;
        sslPort = ConnectionManager.DEFAULT_SSL_PORT;
        clientIdle = 6*60*1000;
        pingIdleClients = true;
        sslEnabled = true;
        save = true;
    }
    
    final ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
    final Map<String, String> errors = new HashMap<String, String>();
    if (save) {
        if (port < 1) {
            errors.put("port", "");
        }
        if (sslPort < 1 && sslEnabled) {
            errors.put("sslPort", "");
        }
        if (port > 0 && sslPort > 0) {
            if (port == sslPort) {
                errors.put("portsEqual", "");
            }
        }
        if (idleDisco && clientIdle <= 0) {
        	errors.put("clientIdle", "");
        }

        if (errors.size() == 0) {
			connectionManager.setClientListenerPort(port);
			connectionManager.enableClientSSLListener(sslEnabled);
			connectionManager.setClientSSLListenerPort(sslPort);
			// Log the event
			webManager.logEvent("edit client connections settings", "port = "+port+"\nsslPort = "+sslPort);
			response.sendRedirect("client-connections-settings.jsp?success=true");
			
			if (!idleDisco) {
            	JiveGlobals.setProperty("xmpp.client.idle", "-1");
			} else {
            	JiveGlobals.setProperty("xmpp.client.idle", String.valueOf(clientIdle));
			}
            JiveGlobals.setProperty("xmpp.client.idle.ping", String.valueOf(pingIdleClients));
            // Log the events
            webManager.logEvent("set server property xmpp.client.idle", "xmpp.client.idle = "+clientIdle);
            webManager.logEvent("set server property xmpp.client.idle.ping", "xmpp.client.idle.ping = "+pingIdleClients);

			return;
        }
    } else {
        sslEnabled = connectionManager.isClientSSLListenerEnabled();
        port = connectionManager.getClientListenerPort();
        sslPort = connectionManager.getClientSSLListenerPort();
        clientIdle = JiveGlobals.getIntProperty("xmpp.client.idle", 6*60*1000);
        pingIdleClients = JiveGlobals.getBooleanProperty("xmpp.client.idle.ping", true);
    }
%>

<p>
<fmt:message key="client.connections.settings.info">
    <fmt:param value="<%= "<a href='session-summary.jsp'>" %>" />
    <fmt:param value="<%= "</a>" %>" />
</fmt:message>
</p>

<%  if ("true".equals(request.getParameter("success"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
            <fmt:message key="client.connections.settings.confirm.updated" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
<%  } %>

<form action="client-connections-settings.jsp" method="post" name="editform">

<!-- BEGIN 'Client ports' -->
	<div class="jive-contentBoxHeader">
		<fmt:message key="client.connections.settings.ports.title" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0" width="100%">
		<tbody>
		<tr valign="top">
			<td width="1%" nowrap class="c1">
				<fmt:message key="server.props.port" />
			</td>
			<td width="99%">
	            <input type="text" name="port" value="<%= (port > 0 ? String.valueOf(port) : "") %>"
	             size="5" maxlength="5">
            <%  if (errors.containsKey("port")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.valid_port" />
                <a href="#" onclick="document.editform.port.value='<%=ConnectionManager.DEFAULT_PORT%>';"
                 ><fmt:message key="server.props.valid_port1" /></a>.
                </span>
            <%  } else if (errors.containsKey("portsEqual")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.error_port" />
                </span>
            <%  } %>
			</td>
		</tr>
		<tr valign="top">
			<td width="1%" nowrap class="c1">
				<fmt:message key="server.props.ssl" />
			</td>
			<td width="99%">
	            <table cellpadding="0" cellspacing="0" border="0">
	            <tbody>
	                <tr>
	                    <td>
	                        <input type="radio" name="sslEnabled" value="true" <%= (sslEnabled ? "checked" : "") %>
	                         id="SSL01">
	                    </td>
	                    <td><label for="SSL01"><fmt:message key="server.props.enable" /></label></td>
	                </tr>
	                <tr>
	                    <td>
	                        <input type="radio" name="sslEnabled" value="false" <%= (!sslEnabled ? "checked" : "") %>
	                         id="SSL02">
	                    </td>
	                    <td><label for="SSL02"><fmt:message key="server.props.disable" /></label></td>
	                </tr>
	            </tbody>
	            </table>
			</td>
		</tr>		
		<tr valign="top">
			<td width="1%" nowrap class="c1">
	             <fmt:message key="server.props.ssl_port" />
    	    </td>
			<td width="99%">
	            <input type="text" name="sslPort" value="<%= (sslPort > 0 ? String.valueOf(sslPort) : "") %>"
	             size="5" maxlength="5">
	            <%  if (errors.containsKey("sslPort")) { %>
	                <br>
	                <span class="jive-error-text">
	                <fmt:message key="server.props.ssl_valid" />
	                <a href="#" onclick="document.editform.sslPort.value='<%=ConnectionManager.DEFAULT_SSL_PORT%>';"
	                 ><fmt:message key="server.props.ssl_valid1" /></a>.
	                </span>
	            <%  } %>
	        </td>
    	</tr>
		</tbody>
		</table>
		
	</div>
<!-- END 'Client Ports' -->

<br />

<!-- BEGIN 'Idle Connection Policy' -->
	<div class="jive-contentBoxHeader">
		<fmt:message key="client.connections.settings.idle.title" />
	</div>

	<div class="jive-contentBox">
		<p><fmt:message key="client.connections.settings.idle.info" /></p>
		<table cellpadding="3" cellspacing="0" border="0" width="100%">
		<tbody>
		<tr valign="top">
			<td width="1%" nowrap class="c1">
				<input type="radio" name="idleDisco" value="false" <%= (clientIdle <= 0 ? "checked" : "") %>
	                         id="IDL01">
	        </td>
	        <td width="99%"><label for="IDL01"><fmt:message key="client.connections.settings.idle.disable" /></label></td>
	    </tr>
		<tr valign="top">
			<td width="1%" nowrap class="c1">
				<input type="radio" name="idleDisco" value="true" <%= (clientIdle > 0 ? "checked" : "") %>
	                         id="IDL02">
	        </td>
	        <td width="99%">
	            <label for="IDL02"><fmt:message key="client.connections.settings.idle.enable" /></label>
	        	<br />
	            <input type="text" name="clientIdle" value="<%= (clientIdle > 0 ? String.valueOf((clientIdle/1000)) : "") %>"
	             size="5" maxlength="5">&nbsp;<fmt:message key="global.seconds" />.
            <%  if (errors.containsKey("clientIdle")) { %>
                <br>
                <span class="jive-error-text">
                	<fmt:message key="client.connections.settings.idle.valid_timeout" />.
                </span>
            <%  } %>
	        </td>
	    </tr>
	    <tr><td colspan="2">&nbsp;</td></tr>
	    <tr>
	    	<td>&nbsp;</td>
	    	<td>
				<p><fmt:message key="client.connections.settings.ping.info" />
				<fmt:message key="client.connections.settings.ping.footnote" /></p>
				<table cellpadding="3" cellspacing="0" border="0" width="100%">
				<tbody>
					<tr valign="top">
						<td width="1%" nowrap class="c1">
							<input type="radio" name="pingIdleClients" value="true" <%= (pingIdleClients ? "checked" : "") %>
				                         id="PNG01">
				        </td>
				        <td width="99%"><label for="PNG01"><fmt:message key="client.connections.settings.ping.enable" /></label></td>
				    </tr>
					<tr valign="top">
						<td width="1%" nowrap class="c1">
							<input type="radio" name="pingIdleClients" value="false" <%= (!pingIdleClients ? "checked" : "") %>
				                         id="PNG02">
				        </td>
				        <td width="99%"><label for="PNG02"><fmt:message key="client.connections.settings.ping.disable" /></label></td>
				    </tr>
		    	</tbody>
		    	</table>
		    </td>
	    </tr>
		</tbody>
		</table>		
	</div>
<!-- END 'Idle Connection Policy' -->
	
	<input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
	<input type="submit" name="defaults" value="<fmt:message key="global.restore_defaults" />">
</form>

</body>
</html>
