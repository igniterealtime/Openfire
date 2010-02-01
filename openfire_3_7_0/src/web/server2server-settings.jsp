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

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%@ page import="org.jivesoftware.util.*,
                 java.util.Iterator,
                 org.jivesoftware.openfire.*,
                 java.util.*,
                 org.jivesoftware.openfire.server.RemoteServerManager,
                 org.jivesoftware.openfire.server.RemoteServerConfiguration"
    errorPage="error.jsp"
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    boolean update = request.getParameter("update") != null;
    boolean s2sEnabled = ParamUtils.getBooleanParameter(request,"s2sEnabled");
    int port = ParamUtils.getIntParameter(request,"port", 0);
    boolean closeEnabled = ParamUtils.getBooleanParameter(request,"closeEnabled");
    String idletime = ParamUtils.getParameter(request,"idletime");
    boolean closeSettings = request.getParameter("closeSettings") != null;
    boolean closeSettingsSuccess = request.getParameter("closeSettingsSuccess") != null;
    boolean permissionUpdate = request.getParameter("permissionUpdate") != null;
    String permissionFilter = ParamUtils.getParameter(request,"permissionFilter");
    String configToDelete = ParamUtils.getParameter(request,"deleteConf");
    boolean serverAllowed = request.getParameter("serverAllowed") != null;
    boolean serverBlocked = request.getParameter("serverBlocked") != null;
    String domain = ParamUtils.getParameter(request,"domain");
    String remotePort = ParamUtils.getParameter(request,"remotePort");
    boolean updateSucess = false;
    boolean allowSuccess = false;
    boolean blockSuccess = false;
    boolean deleteSuccess = false;

    // Get muc server
    SessionManager sessionManager = webManager.getSessionManager();
    ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();

    Map<String, String> errors = new HashMap<String, String>();
    if (update) {
        // Validate params
        if (s2sEnabled) {
            if (port <= 0) {
                errors.put("port","");
            }
        }
        // If no errors, continue:
        if (errors.isEmpty()) {
            if (!s2sEnabled) {
                connectionManager.enableServerListener(false);
                // Log the event
                webManager.logEvent("disabled s2s", null);
            }
            else {
                connectionManager.enableServerListener(true);
                connectionManager.setServerListenerPort(port);
                // Log the event
                webManager.logEvent("enabled s2s", "port = "+port);
            }
            updateSucess = true;
        }
    }

    // Handle an update of the kicking task settings
    if (closeSettings) {
       if (!closeEnabled) {
           // Disable kicking users by setting a value of -1
           sessionManager.setServerSessionIdleTime(-1);
           // Log the event
           webManager.logEvent("disabled s2s idle kick", null);
           response.sendRedirect("server2server-settings.jsp?closeSettingsSuccess=true");
           return;
       }
       // do validation
       if (idletime == null) {
           errors.put("idletime","idletime");
       }
       int idle = 0;
       // Try to obtain an int from the provided strings
       if (errors.size() == 0) {
           try {
               idle = Integer.parseInt(idletime) * 1000 * 60;
           }
           catch (NumberFormatException e) {
               errors.put("idletime","idletime");
           }
           if (idle < 0) {
               errors.put("idletime","idletime");
           }
       }

       if (errors.size() == 0) {
           sessionManager.setServerSessionIdleTime(idle);
           // Log the event
           webManager.logEvent("updated s2s idle kick timeout", "timeout = "+idle);
           response.sendRedirect("server2server-settings.jsp?closeSettingsSuccess=true");
           return;
       }
    }

    if (permissionUpdate) {
        RemoteServerManager.setPermissionPolicy(permissionFilter);
        // Log the event
        webManager.logEvent("updated s2s permission policy", "filter = "+permissionFilter);
        updateSucess = true;
    }

    if (configToDelete != null && configToDelete.trim().length() != 0) {
        RemoteServerManager.deleteConfiguration(configToDelete);
        // Log the event
        webManager.logEvent("deleted s2s configuration", "config to delete = "+configToDelete);
        deleteSuccess = true;
    }

    if (serverAllowed) {
        int intRemotePort = 0;
        // Validate params
        if (domain == null || domain.trim().length() == 0) {
            errors.put("domain","");
        }
        if (remotePort == null || remotePort.trim().length() == 0 ||  "0".equals(remotePort)) {
            errors.put("remotePort","");
        }
        else {
            try {
                intRemotePort = Integer.parseInt(remotePort);
            }
            catch (NumberFormatException e) {
                errors.put("remotePort","");
            }
        }
        // If no errors, continue:
        if (errors.isEmpty()) {
            RemoteServerConfiguration configuration = new RemoteServerConfiguration(domain);
            configuration.setRemotePort(intRemotePort);
            configuration.setPermission(RemoteServerConfiguration.Permission.allowed);
            RemoteServerManager.allowAccess(configuration);
            // Log the event
            webManager.logEvent("added s2s access for "+domain, "domain = "+domain+"\nport = "+intRemotePort);
            allowSuccess = true;
        }
    }

    if (serverBlocked) {
        // Validate params
        if (domain == null || domain.trim().length() == 0) {
            errors.put("domain","");
        }
        // If no errors, continue:
        if (errors.isEmpty()) {
            RemoteServerManager.blockAccess(domain);
            // Log the event
            webManager.logEvent("blocked s2s access for "+domain, "domain = "+domain);
            blockSuccess = true;
        }
    }

    // Set page vars
    if (errors.size() == 0) {
        s2sEnabled = connectionManager.isServerListenerEnabled();
        port = connectionManager.getServerListenerPort();
        permissionFilter = RemoteServerManager.getPermissionPolicy().toString();
        domain = "";
        remotePort = "0";
    }
    else {
        if (port == 0) {
            port = connectionManager.getServerListenerPort();
        }
        if (permissionFilter == null) {
            permissionFilter = RemoteServerManager.getPermissionPolicy().toString();
        }
        if (domain == null) {
            domain = "";
        }
        if (remotePort == null) {
            remotePort = "0";
        }
    }
%>

<html>
<head>
<title><fmt:message key="server2server.settings.title"/></title>
<meta name="pageID" content="server2server-settings"/>
</head>
<body>

<p>
<fmt:message key="server2server.settings.info">
<fmt:param value="<a href='server-session-summary.jsp'>" />
<fmt:param value="</a>" />
</fmt:message>
</p>

<%  if (!errors.isEmpty()) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""/></td>
            <td class="jive-icon-label">

            <% if (errors.get("idletime") != null) { %>
                <fmt:message key="server2server.settings.valid.idle_minutes" />
            <% } else if (errors.get("domain") != null) { %>
                <fmt:message key="server2server.settings.valid.domain" />
            <% } else if (errors.get("remotePort") != null) { %>
                <fmt:message key="server2server.settings.valid.remotePort" />
            <% } %>
            </td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (closeSettingsSuccess || updateSucess || allowSuccess || blockSuccess || deleteSuccess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <% if (updateSucess) { %>
            <fmt:message key="server2server.settings.confirm.updated" />
        <% } else if (allowSuccess) { %>
            <fmt:message key="server2server.settings.confirm.allowed" />
        <% } else if (blockSuccess) { %>
            <fmt:message key="server2server.settings.confirm.blocked" />
        <% } else if (deleteSuccess) { %>
            <fmt:message key="server2server.settings.confirm.deleted" />
        <% } else if (closeSettingsSuccess) { %>
            <fmt:message key="server2server.settings.update" />
        <%
            }
        %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<!-- BEGIN 'Service Enabled' -->
<form action="server2server-settings.jsp" method="post">
	<div class="jive-contentBoxHeader">
		<fmt:message key="server2server.settings.enabled.legend" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
			<tr valign="middle">
				<td width="1%" nowrap>
					<input type="radio" name="s2sEnabled" value="true" id="rb02"
					 <%= (s2sEnabled ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb02">
					<b><fmt:message key="server2server.settings.label_enable" /></b> - <fmt:message key="server2server.settings.label_enable_info" />
					</label>  <input type="text" size="5" maxlength="10" name="port" value="<%= port %>">
				</td>
			</tr>
            <tr valign="middle">
				<td width="1%" nowrap>
					<input type="radio" name="s2sEnabled" value="false" id="rb01"
					 <%= (!s2sEnabled ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb01">
					<b><fmt:message key="server2server.settings.label_disable" /></b> - <fmt:message key="server2server.settings.label_disable_info" />
					</label>
				</td>
			</tr>
		</tbody>
		</table>
        <br/>
        <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
	</div>
</form>
<!-- END 'Service Enabled' -->


<!-- BEGIN 'Idle Connection Settings' -->
<form action="server2server-settings.jsp?closeSettings" method="post">
	<div class="jive-contentBoxHeader">
		<fmt:message key="server2server.settings.close_settings" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
			<tr valign="middle">
				<td width="1%" nowrap>
					<input type="radio" name="closeEnabled" value="true" id="rb04"
					 <%= ((webManager.getSessionManager().getServerSessionIdleTime() > -1) ? "checked" : "") %>>
				</td>
				<td width="99%">
						<label for="rb04"><fmt:message key="server2server.settings.close_session" /></label>
						 <input type="text" name="idletime" size="5" maxlength="5"
							 onclick="this.form.closeEnabled[1].checked=true;"
							 value="<%= webManager.getSessionManager().getServerSessionIdleTime() == -1 ? 30 : webManager.getSessionManager().getServerSessionIdleTime() / 1000 / 60 %>">
						 <fmt:message key="global.minutes" />.
				</td>
			</tr>
            <tr valign="middle">
				<td width="1%" nowrap>
					<input type="radio" name="closeEnabled" value="false" id="rb03"
					 <%= ((webManager.getSessionManager().getServerSessionIdleTime() < 0) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb03"><fmt:message key="server2server.settings.never_close" /></label>
				</td>
			</tr>
		</tbody>
		</table>
        <br/>
        <input type="submit" value="<fmt:message key="global.save_settings" />">
	</div>
</form>
<!-- END 'Idle Connection Settings' -->

<!-- BEGIN 'Allowed to Connect' -->
	<div class="jive-contentBoxHeader">
		<fmt:message key="server2server.settings.allowed" />
	</div>
	<div class="jive-contentBox">
		<form action="server2server-settings.jsp" method="post">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>

			<tr valign="top">
				<td width="1%" nowrap>
					<input type="radio" name="permissionFilter" value="<%= RemoteServerManager.PermissionPolicy.blacklist %>" id="rb05"
					 <%= (RemoteServerManager.PermissionPolicy.blacklist.toString().equals(permissionFilter) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb05">
					<b><fmt:message key="server2server.settings.anyone" /></b> - <fmt:message key="server2server.settings.anyone_info" />
					</label>
				</td>
			</tr>
			<tr valign="top">
				<td width="1%" nowrap>
					<input type="radio" name="permissionFilter" value="<%= RemoteServerManager.PermissionPolicy.whitelist %>" id="rb06"
					 <%= (RemoteServerManager.PermissionPolicy.whitelist.toString().equals(permissionFilter) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb06">
					<b><fmt:message key="server2server.settings.whitelist" /></b> - <fmt:message key="server2server.settings.whitelist_info" />
					</label>
				</td>
			</tr>
		</tbody>
		</table>
		<br/>
		<input type="submit" name="permissionUpdate" value="<fmt:message key="global.save_settings" />">
		<br><br>
		</form>

		<table class="jive-table" cellpadding="0" cellspacing="0" border="0" >
		<thead>
			<tr>
				<th width="1%">&nbsp;</th>
				<th width="50%" nowrap><fmt:message key="server2server.settings.domain" /></th>
				<th width="49%" nowrap><fmt:message key="server2server.settings.remotePort" /></th>
				<th width="10%" nowrap><fmt:message key="global.delete" /></th>
			</tr>
		</thead>
		<tbody>
		<% Collection<RemoteServerConfiguration> configs = RemoteServerManager.getAllowedServers();
		   if (configs.isEmpty()) { %>
			<tr>
				<td align="center" colspan="7"><fmt:message key="server2server.settings.empty_list" /></td>
			</tr>
		   <% }
			else {
			int count = 1;
			for (Iterator<RemoteServerConfiguration> it=configs.iterator(); it.hasNext(); count++) {
				RemoteServerConfiguration configuration = it.next();
		   %>
			<tr class="jive-<%= (((count%2)==0) ? "even" : "odd") %>">
				<td>
					<%= count %>
				</td>
				<td>
					<%= configuration.getDomain() %>
				</td>
				<td>
					<%= configuration.getRemotePort() %>
				</td>
				<td align="center" style="border-right:1px #ccc solid;">
					<a href="#" onclick="if (confirm('<fmt:message key="server2server.settings.confirm_delete" />')) { location.replace('server2server-settings.jsp?deleteConf=<%= configuration.getDomain() %>'); } "
					 title="<fmt:message key="global.click_delete" />"
					 ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
				</td>
			</tr>
		   <% }
		   }
		%>
		</tbody>
		</table>
		<br>
		<table cellpadding="3" cellspacing="1" border="0" >
		<form action="server2server-settings.jsp" method="post">
		<tr>
			<td nowrap>
				<fmt:message key="server2server.settings.domain" />
				<input type="text" size="40" name="domain" value="<%= serverAllowed ?  domain : "" %>"/>
				&nbsp;
				<fmt:message key="server2server.settings.remotePort" />
				<input type="text" size="5" name="remotePort"value="<%= serverAllowed ?  remotePort : "5269" %>"/>
				<input type="submit" name="serverAllowed" value="<fmt:message key="server2server.settings.allow" />">
			</td>
		</tr>
		</form>
		</table>
	</div>
<!-- END 'Allowed to Connect' -->

<!-- BEGIN 'Not Allowed to Connect' -->
	<div class="jive-contentBoxHeader">
		<fmt:message key="server2server.settings.disallowed" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="1" border="0" width="100%"><tr><td>
		<fmt:message key="server2server.settings.disallowed.info" />
		</td></tr></table>
		<p>
		<table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="100%">
		<thead>
			<tr>
				<th width="1%">&nbsp;</th>
				<th width="89%" nowrap><fmt:message key="server2server.settings.domain" /></th>
				<th width="10%" nowrap><fmt:message key="global.delete" /></th>
			</tr>
		</thead>
		<tbody>
		<% Collection<RemoteServerConfiguration> blockedComponents = RemoteServerManager.getBlockedServers();
		   if (blockedComponents.isEmpty()) { %>
			<tr>
				<td align="center" colspan="7"><fmt:message key="server2server.settings.empty_list" /></td>
			</tr>
		   <% }
			else {
			int count = 1;
			for (Iterator<RemoteServerConfiguration> it=blockedComponents.iterator(); it.hasNext(); count++) {
				RemoteServerConfiguration configuration = it.next();
		   %>
			<tr class="jive-<%= (((count%2)==0) ? "even" : "odd") %>">
				<td>
					<%= count %>
				</td>
				<td>
					<%= configuration.getDomain() %>
				</td>
				<td align="center" style="border-right:1px #ccc solid;">
					<a href="#" onclick="if (confirm('<fmt:message key="server2server.settings.confirm_delete" />')) { location.replace('server2server-settings.jsp?deleteConf=<%= configuration.getDomain() %>'); } "
					 title="<fmt:message key="global.click_delete" />"
					 ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
				</td>
			</tr>
		   <% }
		   }
		%>
		</tbody>
		</table>
		<br>
		<table cellpadding="3" cellspacing="1" border="0" width="100%">
		<form action="server2server-settings.jsp" method="post">
		<tr>
			<td nowrap width="1%">
				<fmt:message key="server2server.settings.domain" />
			</td>
			<td>
				<input type="text" size="40" name="domain" value="<%= serverBlocked ?  domain : "" %>"/>&nbsp;
				<input type="submit" name="serverBlocked" value="<fmt:message key="server2server.settings.block" />">
			</td>
		</tr>
		</form>
		</table>
	</div>
<!-- END 'Not Allowed to Connect' -->


</body>
</html>
