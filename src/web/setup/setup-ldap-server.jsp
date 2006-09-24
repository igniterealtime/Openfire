<%@ page import="org.jivesoftware.wildfire.XMPPServer"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>

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
    // Get parameters
    String serverType = ParamUtils.getParameter(request, "serverType");
    String host = JiveGlobals.getXMLProperty("ldap.host");
    if (ParamUtils.getParameter(request, "host") != null) {
        host = ParamUtils.getParameter(request, "host");
    }


    boolean next = request.getParameter("continue") != null;
    if (next) {
        // Redirect
        response.sendRedirect("setup-admin-settings.jsp");
        return;
    }
%>
<html>
<head>
    <title><fmt:message key="setup.ldap.server.title" /></title>
    <meta name="currentStep" content="3"/>

</head>

<body>

	<h1><fmt:message key="setup.ldap.title" /> <span><fmt:message key="setup.ldap.server.settings" /></span></h1>

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

	<form action="" method="get">
		<!-- BEGIN jive-contentBox_bluebox -->
		<div class="jive-contentBox_bluebox">
			<table border="0" cellpadding="0" cellspacing="2">
			<tr>
			<td colspan="4"><strong><fmt:message key="setup.ldap.server.ldap_server" /></strong></td>
			</tr>
			<tr>
			<td align="right"><fmt:message key="setup.ldap.server.type" />:</td>
			<td colspan="3" nowrap><select name="servertype" size="1" id="jiveLDAPserverType">
				<option value="1" SELECTED><fmt:message key="setup.ldap.server.type_select" /></option>
				<option value="2">Active Directory</option>
				<option value="3">OpenLDAP</option>
                <option value="4"><fmt:message key="setup.ldap.server.type_other" /></option>
                </select><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.type_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			<tr>
			<td align="right"><fmt:message key="setup.ldap.server.host" />:</td>
			<td><input type="text" name="host" id="jiveLDAPphost" size="22" maxlength="50"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.host_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			<td align="right">&nbsp;<fmt:message key="setup.ldap.server.port" />:</td>
			<td><input type="text" name="port" id="jiveLDAPport" size="5" maxlength="5" value="389"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.port_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			<tr>
			<td align="right"><fmt:message key="setup.ldap.server.basedn" />:</td>
			<td colspan="3"><input type="text" name="basedn" id="jiveLDAPbasedn" size="40" maxlength="50"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.basedn_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 16000);"></a></span></td>
			</tr>
            <tr><td colspan="4">&nbsp;</td></tr>
            <tr>
			<td colspan="4"><strong><fmt:message key="setup.ldap.server.auth" />:</strong></td>
			</tr>
			<tr>
			<td align="right"><fmt:message key="setup.ldap.server.admindn" />:</td>
			<td colspan="3"><input type="text" name="admindn" id="jiveLDAPadmindn" size="40" maxlength="50"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.admindn_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', -1);"></a></span></td>
			</tr>
			<tr>
			<td align="right"><fmt:message key="setup.ldap.server.password" />:</td>
			<td colspan="3"><input type="text" name="adminpwd" id="jiveLDAPadminpwd" size="22" maxlength="30"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.ldap.server.password_help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			</table>
		</div>
		<!-- END jive-contentBox_bluebox -->


		<!-- BEGIN jiveAdvancedButton -->
		<div class="jiveAdvancedButton">
			<a href="#" onclick="togglePanel(jiveAdvanced); return false;" id="jiveAdvancedLink"><fmt:message key="setup.ldap.server.advanced" /></a>
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
						<input type="radio" name="connectionpool" value="yes" checked>
					</td>
					<td class="jive-advancedBorderBottom" align="center">
						<input type="radio" name="connectionpool" value="no">
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
						<input type="radio" name="ssl" value="yes">
					</td>
					<td class="jive-advancedBorderBottom" align="center">
						<input type="radio" name="ssl" value="no" checked>
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
						<input type="radio" name="debug" value="yes">
					</td>
					<td class="jive-advancedBorderBottom" align="center">
						<input type="radio" name="debug" value="no" checked>
					</td>
				</tr>
				<tr>
					<td class="jive-advancedLabel" nowrap>
						Follow Referrals:
					</td>
					<td class="jive-advancedDesc jive-advancedBorderBottom jive-advancedBorderRight">
						Automatically followed LDAP referrals
					</td>
					<td class="jive-advancedBorderBottom jive-advancedBorderRight" align="center">
						<input type="radio" name="referrals" value="yes" checked>
					</td>
					<td class="jive-advancedBorderBottom" align="center">
						<input type="radio" name="referrals" value="no">
					</td>
				</tr>
				<tr>
					<td class="jive-advancedLabel" nowrap>
						Enclose UserDN:
					</td>
					<td class="jive-advancedDesc jive-advancedBorderBottom jive-advancedBorderRight">
						&nbsp;
					</td>
					<td class="jive-advancedBorderBottom jive-advancedBorderRight" align="center">
						<input type="radio" name="userdn" value="yes" checked>
					</td>
					<td class="jive-advancedBorderBottom" align="center">
						<input type="radio" name="userdn" value="no">
					</td>
				</tr>
				<tr>
					<td class="jive-advancedLabel" nowrap>
						Search in Subtrees:
					</td>
					<td class="jive-advancedDesc jive-advancedBorderRight">
						&nbsp;
					</td>
					<td class="jive-advancedBorderRight" align="center">
						<input type="radio" name="subtrees" value="yes" checked>
					</td>
					<td class="" align="center">
						<input type="radio" name="subtrees" value="no">
					</td>
				</tr>
				</tbody>
				</table>
			</div>
		</div>
		<!-- END jiveAdvancedPanelcs (advanced connection settings) -->


		<!-- BEGIN jive-buttons -->
		<div class="jive-buttons">

			<!-- BEGIN left-aligned buttons -->
			<div align="left" style="float: left;">
				<!--<input type="Submit" name="back" value="Back" id="jive-setup-back" border="0">-->
			</div>
			<!-- END left-aligned buttons -->

			<!-- BEGIN right-aligned buttons -->
			<div align="right">
				<a href="setup-ldap-server_test.jsp" class="lbOn" id="jive-setup-test2">
				<img src="../images/setup_btn_gearplay.gif" alt="" width="14" height="14" border="0">
				Test Settings
				</a>

				<input type="Submit" name="save" value="Save & Continue" id="jive-setup-save" border="0">
			</div>
			<!-- END right-aligned buttons -->

		</div>
		<!-- END jive-buttons -->

	</form>

	</div>
	<!-- END jive-contentBox -->



</body>
</html>
