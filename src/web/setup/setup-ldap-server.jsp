<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 java.util.Date,
                 org.jivesoftware.wildfire.user.User,
                 org.jivesoftware.wildfire.user.UserManager,
                 org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.wildfire.XMPPServer"%>
<%@ page import="org.jivesoftware.wildfire.auth.AuthFactory"%>

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

    boolean next = request.getParameter("continue") != null;
    if (next) {
        // Redirect
        response.sendRedirect("setup-admin-settings.jsp");
        return;
    }
%>
<html>
<head>
    <title>Profile Settings - Directory Server</title>
    <meta name="currentStep" content="3"/>

</head>

<body>

	<h1>Profile Settings <span>- LDAP Connection Settings</span></h1>

	<p>Configure the directory server connection settings here.</p>

	<!-- BEGIN jive-contentBox_stepbar -->
	<div id="jive-contentBox_stepbar">
		<span class="jive-stepbar_step"><strong>1. Connection Settings</strong></span>
		<span class="jive-stepbar_step"><em>2. User Mapping</em></span>
		<span class="jive-stepbar_step"><em>3. Group Mapping</em></span>
	</div>
	<!-- END jive-contentBox-stepbar -->

	<!-- BEGIN jive-contentBox -->
	<div class="jive-contentBox jive-contentBox_for-stepbar">

	<h2>Step 1 of 3: <span>Connection Settings</span></h2>
	<p>A sentance detailing the setup options below. Also, noting that all fields are required. Lorem ipsum dolor siet amet. Also mention the help tooltip rollovers.</p>

	<form action="" method="get">
		<!-- BEGIN jive-contentBox_bluebox -->
		<div class="jive-contentBox_bluebox">
			<table border="0" cellpadding="0" cellspacing="2">
			<tr>
			<td colspan="4"><strong>LDAP Server</strong></td>
			</tr>
			<tr>
			<td align="right">Server Type:</td>
			<td colspan="3" nowrap><select name="servertype" size="1" id="jiveLDAPserverType">
				<option value="1" SELECTED>--Select directory server type--</option>
				<option value="2">Active Directory</option>
				<option value="3">OpenLDAP</option>
                <option value="4">Other or Unknown</option>
                </select><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'Lorem ipsum dolor something about this form option blah blah', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			<tr>
			<td align="right">Host:</td>
			<td><input type="text" name="host" id="jiveLDAPphost" size="22" maxlength="50"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'LDAP server host name; e.g. localhost or ldap.example.com, etc.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			<td align="right">&nbsp;Port:</td>
			<td><input type="text" name="port" id="jiveLDAPport" size="5" maxlength="5" value="389"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'LDAP server port number. The default value is 389.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			<tr>
			<td align="right">Base DN:</td>
			<td colspan="3"><input type="text" name="basedn" id="jiveLDAPbasedn" size="40" maxlength="50"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'This is the starting DN that searches for users will performed with. The entire subtree under the base DN will be searched for user accounts.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			<tr>
			<td colspan="4"><strong>Authentication:</strong></td>
			</tr>
			<tr>
			<td align="right">Administrator DN:</td>
			<td colspan="3"><input type="text" name="admindn" id="jiveLDAPadmindn" size="40" maxlength="50"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'A directory administrator\'s DN. All directory operations will be performed with this account. The admin must be able to perform searches and load user records. The user does not need to be able to make changes to the directory, as Wildfire treats the directory as read-only. If this property is not set, an anonymous login to the server will be attempted.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			<tr>
			<td align="right">Password:</td>
			<td colspan="3"><input type="text" name="adminpwd" id="jiveLDAPadminpwd" size="22" maxlength="30"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'The password for the directory administrator.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			</table>
		</div>
		<!-- END jive-contentBox_bluebox -->


		<!-- BEGIN jiveAdvancedButton -->
		<div class="jiveAdvancedButton">
			<a href="#" onclick="togglePanel(jiveAdvanced); return false;" id="jiveAdvancedLink">Advanced Settings</a>
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
					<th width="50">Yes</th>
					<th width="50">No</th>
				</tr>
				</thead>
				<tbody>
				<tr>
					<td class="jive-advancedLabel" nowrap>
						Use Connection Pool:
					</td>
					<td class="jive-advancedDesc jive-advancedBorderBottom jive-advancedBorderRight">
						Connection Pooling. Default is 'Yes'
					</td>
					<td class="jive-advancedBorderBottom jive-advancedBorderRight" align="center">
						<input type="radio" name="connectionpool" value="yes">
					</td>
					<td class="jive-advancedBorderBottom" align="center">
						<input type="radio" name="connectionpool" value="no" checked>
					</td>
				</tr>
				<tr>
					<td class="jive-advancedLabel" nowrap>
						Use SSL:
					</td>
					<td class="jive-advancedDesc jive-advancedBorderBottom jive-advancedBorderRight">
						Enable SSL connections to your LDAP server, default port is usually 636
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
						Enable Debug:
					</td>
					<td class="jive-advancedDesc jive-advancedBorderBottom jive-advancedBorderRight">
						Trace information about buffers written to System.out
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
						Allow Referrals:
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
