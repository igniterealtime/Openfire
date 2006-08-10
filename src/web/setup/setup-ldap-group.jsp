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

	<h1>Profile Settings <span>- Group Mapping</span></h1>

	<p>Configure group mapping and finish the directory server profile integration setup.</p>

	<!-- BEGIN jive-contentBox_stepbar -->
	<div id="jive-contentBox_stepbar">
		<span class="jive-stepbar_step">1. Connection Settings</span>
		<span class="jive-stepbar_step">2. User Mapping</span>
		<span class="jive-stepbar_step"><strong>3. Group Mapping</strong></span>
	</div>
	<!-- END jive-contentBox-stepbar -->

	<!-- BEGIN jive-contentBox -->
	<div class="jive-contentBox jive-contentBox_for-stepbar">

	<h2>Step 3 of 3: <span>Group Mapping</span></h2>
	<p>A sentance detailing the setup options below. Also, noting that all fields are <strong>optional</strong>. Lorem ipsum dolor siet amet. Also mention the help tooltip rollovers.</p>

	<form action="" method="get">
		<!-- BEGIN jive-contentBox_bluebox -->
		<div class="jive-contentBox_bluebox">

			<table border="0" cellpadding="0" cellspacing="2">
			<tr>
			<td colspan="2"><strong>Group Mapping</strong></td>
			</tr>
			<tr>
			<td align="right">Name:</td>
			<td><input type="text" name="groupname" value="cn" id="jiveLDAPgroupname" size="22" maxlength="30"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'The field name that the groupname lookups will be performed on. If this property is not set, the default value is <b>cn</b>.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			<tr>
			<td align="right">Member:</td>
			<td><input type="text" name="groupmember" value="member" id="jiveLDAPgroupmember" size="22" maxlength="30"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'The field name that holds the members in a group. If this property is not set, the default value is <b>member</b>.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			<tr>
			<td align="right">Description:</td>
			<td><input type="text" name="groupdesc" value="description" id="jiveLDAPgroupdesc" size="22" maxlength="30"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'The field name that holds the description a group. If this property is not set, the default value is <b>description</b>.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
			</tr>
			</table>

			<!-- BEGIN jiveAdvancedButton -->
			<div class="jiveAdvancedButton jiveAdvancedButtonTopPad">
				<a href="#" onclick="togglePanel(jiveAdvanced); return false;" id="jiveAdvancedLink">Advanced Settings</a>
			</div>
			<!-- END jiveAdvancedButton -->

			<!-- BEGIN jiveAdvancedPanelu (advanced user mapping settings) -->
				<div class="jiveadvancedPanelu" id="jiveAdvanced" style="display: none;">
					<div>
						<table border="0" cellpadding="0" cellspacing="2">
						<tr>
						<td align="right">Posix Mode:</td>
						<td><span style="float: left;">
							<label for="posix1"><input type="radio" name="posix" value="yes" style="float: none;" id="posix1"> Yes  </label>
							<label for="posix2"><input type="radio" name="posix" value="no" style="float: none;" id="posix2" checked> No </label>
							</span>
							<span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'A value of &quot;true&quot; means that users are stored within the group by their user name alone. A value of &quot;false&quot; means that users are stored by their entire DN within the group. If this property is not set, the default value is <b>false</b>. The posix mode must be set correctly for your server in order for group integration to work.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 10000);"></a></span></td>
						</tr>
						<tr>
						<td align="right">Search Filter:</td>
						<td><input type="text" name="gropusearchfilter" value="ldap.groupNameField={0}" id="jiveLDAPgroupsearchfilter" size="22" maxlength="30"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'An optional search filter to append to the default filter when loading groups. The default group search filter is created using the attribute specified by ldap.groupNameField. For example, if the group name field is &quot;cn&quot;, then the default group search filter would be &quot;(cn={0})&quot; where {0} is dynamically replaced with the group name being searched for.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 10000);"></a></span></td>
						</tr>
						</table>
					</div>
				</div>
			<!-- END jiveAdvancedPanelu (advanced user mapping settings) -->

		</div>
		<!-- END jive-contentBox_bluebox -->



		<!-- BEGIN jive-buttons -->
		<div class="jive-buttons">

			<!-- BEGIN left-aligned buttons -->
			<div align="left" style="float: left;">
				<input type="Submit" name="back" value="Back" id="jive-setup-back" border="0">
			</div>
			<!-- END left-aligned buttons -->

			<!-- BEGIN right-aligned buttons -->
			<div align="right">
				<a href="setup-ldap-group_test.jsp" class="lbOn" id="jive-setup-test2">
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
