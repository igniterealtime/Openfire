<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="org.jivesoftware.util.ParamUtils, org.jivesoftware.wildfire.XMPPServer, java.util.HashMap, java.util.Map"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
	// Redirect if we've already run setup:
	if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    String serverType = null;
    String host;
    int port = 389;
    String baseDN;
    String adminDN;
    String adminPassword;
    boolean connectionPoolEnabled = true;
    boolean sslEnabled = false;
    boolean debugEnabled = false;
    boolean referralsEnabled = false;

    // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean test = request.getParameter("test") != null;

    Map<String, String> errors = new HashMap<String, String>();

    if (save || test) {
        int serverTypeInt = ParamUtils.getIntParameter(request, "servertype", 1);
        switch (serverTypeInt) {
            case 1:
                serverType = "other";
                break;
            case 2:
                serverType = "activedirectory";
                break;
            case 3:
                serverType = "openldap";
                break;
            default:
                serverType = "other";
        }

        host = ParamUtils.getParameter(request, "host");
        if (host == null) {
            errors.put("host", LocaleUtils.getLocalizedString("setup.ldap.server.host_error"));
        }
        port = ParamUtils.getIntParameter(request, "port", port);
        if (port <= 0) {
            errors.put("port", LocaleUtils.getLocalizedString("setup.ldap.server.port_error"));
        }
        baseDN = ParamUtils.getParameter(request, "basedn");
        if (baseDN == null) {
            errors.put("baseDN", LocaleUtils.getLocalizedString("setup.ldap.server.basedn_error"));
        }
        adminDN = ParamUtils.getParameter(request, "admindn");
        adminPassword = ParamUtils.getParameter(request, "adminpwd");
        connectionPoolEnabled =
                ParamUtils.getBooleanParameter(request, "connectionpool", connectionPoolEnabled);
        sslEnabled = ParamUtils.getBooleanParameter(request, "ssl", sslEnabled);
        debugEnabled = ParamUtils.getBooleanParameter(request, "debug", debugEnabled);
        referralsEnabled = ParamUtils.getBooleanParameter(request, "referrals", referralsEnabled);

        if (errors.isEmpty()) {
            // Store settings in a map and keep it in the session
            Map<String, String> settings = new HashMap<String, String>();
            settings.put("ldap.serverType", serverType);
            settings.put("ldap.host", host);
            settings.put("ldap.port", Integer.toString(port));
            settings.put("ldap.baseDN", baseDN);
            settings.put("ldap.adminDN", adminDN);
            settings.put("ldap.adminPassword", adminPassword);
            settings.put("ldap.connectionPoolEnabled",
                    Boolean.toString(connectionPoolEnabled));
            settings.put("ldap.sslEnabled", Boolean.toString(sslEnabled));
            settings.put("ldap.debugEnabled", Boolean.toString(debugEnabled));
            settings.put("ldap.autoFollowReferrals", Boolean.toString(referralsEnabled));
            // Always disable connection pooling so that connections aren't left hanging open.
            settings.put("ldap.connectionPoolEnabled", "false");
            session.setAttribute("ldapSettings", settings);

            if (save) {
                // Save settings and redirect
                JiveGlobals.setXMLProperty("ldap.host", host);
                JiveGlobals.setXMLProperty("ldap.port", Integer.toString(port));
                JiveGlobals.setXMLProperty("ldap.baseDN", baseDN);
                JiveGlobals.setXMLProperty("ldap.adminDN", adminDN);
                JiveGlobals.setXMLProperty("ldap.adminPassword", adminPassword);
                JiveGlobals.setXMLProperty("ldap.connectionPoolEnabled",
                        Boolean.toString(connectionPoolEnabled));
                JiveGlobals.setXMLProperty("ldap.sslEnabled", Boolean.toString(sslEnabled));
                JiveGlobals.setXMLProperty("ldap.debugEnabled", Boolean.toString(debugEnabled));
                JiveGlobals.setXMLProperty("ldap.autoFollowReferrals",
                        Boolean.toString(referralsEnabled));

                // Redirect to next step.
                response.sendRedirect("setup-ldap-user.jsp?serverType=" + serverType);
                return;
            }
        }
    }
    else {
        // See if there are already values for the variables defined.
        host = JiveGlobals.getXMLProperty("ldap.host");
        port = JiveGlobals.getXMLProperty("ldap.port", port);
        baseDN = JiveGlobals.getXMLProperty("ldap.baseDN");
        adminDN = JiveGlobals.getXMLProperty("ldap.adminDN");
        adminPassword = JiveGlobals.getXMLProperty("ldap.adminPassword");
        connectionPoolEnabled =
                JiveGlobals.getXMLProperty("ldap.connectionPoolEnabled", connectionPoolEnabled);
        sslEnabled = JiveGlobals.getXMLProperty("ldap.sslEnabled", sslEnabled);
        debugEnabled = JiveGlobals.getXMLProperty("ldap.debugEnabled", debugEnabled);
        referralsEnabled = JiveGlobals.getXMLProperty("ldap.autoFollowReferrals", referralsEnabled);
    }
%>
<html>
<head>
    <title><fmt:message key="setup.ldap.title" /></title>
    <meta name="currentStep" content="3"/>
</head>

<body>

    <% if (test && errors.isEmpty()) { %>

        <a href="setup-ldap-server_test.jsp?serverType=<%= serverType%>" id="lbmessage" title="Test" style="display:none;"></a>
        <script type="text/javascript">
            function loadMsg() {
                var lb = new lightbox(document.getElementById('lbmessage'));
                lb.activate();
            }
            setTimeout('loadMsg()', 250);
        </script>

    <% } %>

    <h1><fmt:message key="setup.ldap.profile" />: <span><fmt:message key="setup.ldap.connection_settings" /></span></h1>

	<!-- BEGIN jive-contentBox_stepbar -->
	<div id="jive-contentBox_stepbar">
		<span class="jive-stepbar_step"><strong>1. <fmt:message key="setup.ldap.connection_settings" /></strong></span>
		<span class="jive-stepbar_step"><em>2. <fmt:message key="setup.ldap.user_mapping" /></em></span>
		<span class="jive-stepbar_step"><em>3. <fmt:message key="setup.ldap.group_mapping" /></em></span>
	</div>
	<!-- END jive-contentBox-stepbar -->

    <!-- BEGIN jive-contentBox -->
    <div class="jive-contentBox jive-contentBox_for-stepbar">

	<h2><fmt:message key="setup.ldap.step_one" />: <span><fmt:message key="setup.ldap.connection_settings" /></span></h2>
	<p><fmt:message key="setup.ldap.server.description" /></p>

    <%  if (errors.size() > 0) { %>

    <div class="error">
        <% for (String error:errors.values()) { %>
            <%= error%><br/>  
        <% } %>
    </div>

    <%  } %>

    <form action="setup-ldap-server.jsp" method="post">
		<!-- BEGIN jive-contentBox_bluebox -->
		<div class="jive-contentBox_bluebox">
			<table border="0" cellpadding="0" cellspacing="2">
			<tr>
			    <td colspan="4"><strong><fmt:message key="setup.ldap.server.ldap_server" /></strong></td>
			</tr>
			<tr>
                <td align="right" width="1%" nowrap="nowrap"><fmt:message key="setup.ldap.server.type" />:</td>
                <td colspan="3" nowrap>
                    <select name="servertype" size="1" id="jiveLDAPserverType" style="width:90%;">
                        <option value="1" <%= serverType == null ? "selected" : "" %>><fmt:message key="setup.ldap.server.type_select" /></option>
                        <option value="2" <%= "activedirectory".equals(serverType) ? "selected" : "" %>>Active Directory</option>
                        <option value="3" <%= "openldap".equals(serverType) ? "selected" : "" %>>OpenLDAP</option>
                        <option value="4" <%= "other".equals(serverType) ? "selected" : "" %>><fmt:message key="setup.ldap.server.type_other" /></option>
                    </select><span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.type_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span>
                </td>
			</tr>
			<tr>
			    <td align="right" width="1%" nowrap="nowrap"><fmt:message key="setup.ldap.server.host" />:</td>
                <td width="1%">
                    <table cellpadding="0" cellspacing="0" border="0" width="100%">
                    <tr>
                        <td width="1%" nowrap="nowrap">
                            <input type="text" name="host" id="jiveLDAPphost" size="22" maxlength="50" value="<%= host!=null?host:"" %>">    
                        </td>
                        <td width="99%">
                            <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.host_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span>
                        </td>
                    </tr>
                    </table>
                </td>
                <td align="right" width="1%" nowrap="nowrap">&nbsp;&nbsp; <fmt:message key="setup.ldap.server.port" />:</td>
                <td><input type="text" name="port" id="jiveLDAPport" size="5" maxlength="5" value="<%= port %>"><span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.port_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span></td>
			</tr>
			<tr>
                <td align="right"><fmt:message key="setup.ldap.server.basedn" />:</td>
                <td colspan="3">
                    <input type="text" name="basedn" id="jiveLDAPbasedn" size="40" maxlength="150" value="<%= baseDN!=null?baseDN:""%>" style="width:90%;">
                    <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.basedn_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 16000);"></span>
                </td>
			</tr>
            <tr><td colspan="4">&nbsp;</td></tr>
            <tr>
			    <td colspan="4"><strong><fmt:message key="setup.ldap.server.auth" />:</strong></td>
			</tr>
			<tr>
                <td align="right" width="1%" nowrap="nowrap"><fmt:message key="setup.ldap.server.admindn" />:</td>
                <td colspan="3">
                    <input type="text" name="admindn" id="jiveLDAPadmindn" size="40" maxlength="150" value="<%= adminDN!=null?adminDN:""%>" style="width:90%;">
                    <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.admindn_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', -1);"></span>
                </td>
			</tr>
			<tr>
                <td align="right" width="1%" nowrap="nowrap"><fmt:message key="setup.ldap.server.password" />:</td>
                <td colspan="3"><input type="password" name="adminpwd" id="jiveLDAPadminpwd" size="22" maxlength="30" value="<%= adminPassword!=null?adminPassword:""%>"> <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.password_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span></td>
			</tr>
			</table>
		</div>
		<!-- END jive-contentBox_bluebox -->


		<!-- BEGIN jiveAdvancedButton -->
		<div class="jiveAdvancedButton">
			<a href="#" onclick="togglePanel(jiveAdvanced); return false;" id="jiveAdvancedLink"><fmt:message key="setup.ldap.advanced" /></a>
		</div>
		<!-- END jiveAdvancedButton -->

		<!-- BEGIN jiveAdvancedPanelcs (advanced connection settings) -->
		<div class="jiveadvancedPanelcs" id="jiveAdvanced" style="display: none;">
			<div>
				<table border="0" cellpadding="0" cellspacing="1">
				<thead>
				<tr>
					<th width="10%"></th>
					<th></th>
					<th width="50"><fmt:message key="global.yes" /></th>
					<th width="50"><fmt:message key="global.no" /></th>
				</tr>
				</thead>
				<tbody>
				<tr>
					<td class="jive-advancedLabel" nowrap>
						<fmt:message key="setup.ldap.server.connection_pool" />:
					</td>
					<td class="jive-advancedDesc jive-advancedBorderBottom jive-advancedBorderRight">
					    <fmt:message key="setup.ldap.server.connection_pool_help" />
					</td>
					<td class="jive-advancedBorderBottom jive-advancedBorderRight" align="center">
						<input type="radio" name="connectionpool" value="yes" <% if (connectionPoolEnabled) { %>checked <% } %>>
					</td>
					<td class="jive-advancedBorderBottom" align="center">
						<input type="radio" name="connectionpool" value="no" <% if (!connectionPoolEnabled) { %>checked <% } %>>
					</td>
				</tr>
				<tr>
					<td class="jive-advancedLabel" nowrap>
						<fmt:message key="setup.ldap.server.ssl" />:
					</td>
					<td class="jive-advancedDesc jive-advancedBorderBottom jive-advancedBorderRight">
						<fmt:message key="setup.ldap.server.ssl_help" />
					</td>
					<td class="jive-advancedBorderBottom jive-advancedBorderRight" align="center">
						<input type="radio" name="ssl" value="yes" <% if (sslEnabled) { %>checked <% } %>>
					</td>
					<td class="jive-advancedBorderBottom" align="center">
						<input type="radio" name="ssl" value="no" <% if (!sslEnabled) { %>checked <% } %>>
					</td>
				</tr>
				<tr>
					<td class="jive-advancedLabel" nowrap>
						<fmt:message key="setup.ldap.server.debug" />:
					</td>
					<td class="jive-advancedDesc jive-advancedBorderBottom jive-advancedBorderRight">
						<fmt:message key="setup.ldap.server.debug_help" />
					</td>
					<td class="jive-advancedBorderBottom jive-advancedBorderRight" align="center">
						<input type="radio" name="debug" value="yes" <% if (debugEnabled) { %>checked <% } %>>
					</td>
					<td class="jive-advancedBorderBottom" align="center">
						<input type="radio" name="debug" value="no" <% if (!debugEnabled) { %>checked <% } %>>
					</td>
				</tr>
				<tr>
					<td class="jive-advancedLabel" nowrap>
						<fmt:message key="setup.ldap.server.referral" />:
					</td>
					<td class="jive-advancedDesc jive-advancedBorderBottom jive-advancedBorderRight">
						<fmt:message key="setup.ldap.server.referral_help" />
					</td>
					<td class="jive-advancedBorderBottom jive-advancedBorderRight" align="center">
						<input type="radio" name="referrals" value="yes" <% if (referralsEnabled) { %>checked <% } %>>
					</td>
					<td class="jive-advancedBorderBottom" align="center">
						<input type="radio" name="referrals" value="no" <% if (!referralsEnabled) { %>checked <% } %>>
					</td>
				</tr>
				</tbody>
				</table>
			</div>
		</div>
		<!-- END jiveAdvancedPanelcs (advanced connection settings) -->


		<!-- BEGIN jive-buttons -->
		<div class="jive-buttons">

			<!-- BEGIN right-aligned buttons -->
			<div align="right">
				
                <input type="Submit" name="test" value="<fmt:message key="setup.ldap.test" />" id="jive-setup-test" border="0">

                <input type="Submit" name="save" value="<fmt:message key="setup.ldap.continue" />" id="jive-setup-save" border="0">
			</div>
			<!-- END right-aligned buttons -->

		</div>
		<!-- END jive-buttons -->

	</form>

	</div>
	<!-- END jive-contentBox -->



</body>
</html>
