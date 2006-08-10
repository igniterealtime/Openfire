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

	<h1>Profile Settings <span>- User Mapping</span></h1>

	<p>Configure user mapping and user profiles.</p>

	<!-- BEGIN jive-contentBox_stepbar -->
	<div id="jive-contentBox_stepbar">
		<span class="jive-stepbar_step">1. Connection Settings</span>
		<span class="jive-stepbar_step"><strong>2. User Mapping</strong></span>
		<span class="jive-stepbar_step"><em>3. Group Mapping</em></span>
	</div>
	<!-- END jive-contentBox-stepbar -->

	<!-- BEGIN jive-contentBox -->
	<div class="jive-contentBox jive-contentBox_for-stepbar">

	<h2>Step 2 of 3: <span>User Mapping</span></h2>
	<p>A sentance detailing the setup options below. Also, noting that the usermapping field is <strong>required</strong>. Lorem ipsum dolor siet amet. Also mention the help tooltip rollovers.</p>

	<form action="" method="get">
		<!-- BEGIN jive-contentBox_bluebox -->
		<div class="jive-contentBox_bluebox">

			<table border="0" cellpadding="0" cellspacing="2">
			<tr>
			<td colspan="2"><strong>User Mapping</strong></td>
			</tr>
			<tr>
			<td align="right">Username:</td>
			<td><input type="text" name="username" id="jiveLDAPusername" size="22" maxlength="30"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'The field name that the username lookups will be performed on. If this property is not set, the default value is <b>uid</b>. Active Directory users should try the default value <b>sAMAccountName</b>', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></a></span></td>
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
						<td align="right">Search Fields:</td>
						<td><input type="text" name="searchfield" value="Username/uid,Name/cname" id="jiveLDAPsearchfields" size="22" maxlength="50"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'The LDAP fields that will be used for user searches. If this property is not set, the username, name, and email fields will be searched. An example value for this field is &quot;Username/uid,Name/cname&quot;. That searches the uid and cname fields in the directory and labels them as &quot;Username&quot; and &quot;Name&quot; in the search UI. You can add as many fields as you\'d like using comma-delimited &quot;DisplayName/Field&quot; pairs. You should ensure that any fields used for searching are properly indexed so that searches return quickly.', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 10000);"></a></span></td>
						</tr>
						<tr>
						<td align="right">Search Filter:</td>
						<td><input type="text" name="searchfilter" value="uid={0}" id="jiveLDAPsearchfilter" size="22" maxlength="30"><span class="jive-setup-helpicon"><a href="" onmouseover="domTT_activate(this, event, 'content', 'An optional search filter to append to the default filter when loading users. The default search filter is created using the attribute specified by ldap.usernameField. For example, if the username field is &quot;uid&quot;, then the default search filter would be &quot;(uid={0})&quot; where {0} is dynamically replaced with the username being searched for. ', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 10000);"></a></span></td>
						</tr>
						</table>
					</div>
				</div>
			<!-- END jiveAdvancedPanelu (advanced user mapping settings) -->

		</div>
		<!-- END jive-contentBox_bluebox -->


		<script type="text/javascript" language="JavaScript">
			function jiveRowHighlight(theInput) {

				var e = $(jivevCardTable).getElementsByTagName('tr');
					for (var i = 0; i < e.length; i++) {
							e[i].style.backgroundColor = "#fff";
					}

				theInput.parentNode.parentNode.style.backgroundColor = "#eaeff4";
			}

		</script>

		<!-- BEGIN jive-contentBox_greybox -->
		<div class="jive-contentBox_greybox">
			<strong>User Profiles (vCard)</strong>
			<p>Lorem ipsum some sentance describing what all the fields below are, etc.</p>

			<!-- BEGIN vcard table -->
			<table border="0" cellpadding="0" cellspacing="1" class="jive-vcardTable" id="jivevCardTable">
				<thead>
				<tr>
					<th width="40%">Profile Field</th>
					<th width="60%">Value</th>
				</tr>
				</thead>
				<tbody>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						<strong>Name</strong>
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="name" value="{cn}" id="jiveLDAPname" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						<strong>Email</strong>
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="email" value="{mail}" id="jiveLDAPemail" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						&nbsp;
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						&nbsp;
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						<strong>Full Name</strong>
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="fullname" value="{firstName} {middleName} {lastName}" id="jiveLDAPfullname" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						<strong>Nickname</strong>
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="nickname" value="{nick}" id="jiveLDAPnickname" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						<strong>Birthday</strong>
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="dob" value="{dob}" id="jiveLDAPdob" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						<strong>Home</strong>
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						&nbsp;
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Street Address
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="homestreet" value="{homeAddress} {homeExtrAddress}" id="jiveLDAPhomestreet" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- City
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="homecity" value="{homeCity}" id="jiveLDAPhomecity" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- State/Province
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="homestate" value="{homeState}" id="jiveLDAPhomestate" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Postal Code
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="homezip" value="{homeZip}" id="jiveLDAPhomezip" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Country
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="homecountry" value="{homeCountry}" id="jiveLDAPhomecountry" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Phone Number
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="homephone" value="{homePhone}" id="jiveLDAPhomephone" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Mobile Number
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="homemobile" value="{homeMobile}" id="jiveLDAPhomemobile" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Fax
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="homefax" value="{homeFax}" id="jiveLDAPhomefax" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Pager
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="homepager" value="{homePager}" id="jiveLDAPhomePager" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						<strong>Business</strong>
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						&nbsp;
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Street Address
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="workstreet" value="317 SW Alder St, Ste 500" id="jiveLDAPworkstreet" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- City
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="workcity" value="Portland" id="jiveLDAPworkcity" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- State/Province
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="workstate" value="Oregon" id="jiveLDAPworkstate" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Postal Code
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="workzip" value="97204" id="jiveLDAPworkzip" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Country
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="workcountry" value="USA" id="jiveLDAPworkcountry" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Job Title
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="worktitle" value="{title}" id="jiveLDAPworktitle" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Department
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="workdept" value="{department}" id="jiveLDAPworkdept" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Phone Number
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="workphone" value="{workPhone}" id="jiveLDAPworkphone" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Mobile Number
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="workmobile" value="{workMobile}" id="jiveLDAPworkmobile" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Fax
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="workfax" value="{workFax}" id="jiveLDAPworkfax" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderBottom jive-vardBorderRight" nowrap>
						- Pager
					</td>
					<td class="jive-vcardTable-value jive-vardBorderBottom">
						<input type="text" name="workpager" value="{workPager}" id="jiveLDAPworkPager" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
				<tr>
					<td class="jive-vcardTable-label jive-vardBorderRight" nowrap>
						- Web page
					</td>
					<td class="jive-vcardTable-value">
						<input type="text" name="workurl" value="{webAddress}" id="jiveLDAPworkurl" size="22" maxlength="50" onFocus="jiveRowHighlight(this);">
					</td>
				</tr>
			</table>
			<!-- END vcard table -->

		</div>
		<!-- END jive-contentBox_greybox -->



		<!-- BEGIN jive-buttons -->
		<div class="jive-buttons">

			<!-- BEGIN left-aligned buttons -->
			<div align="left" style="float: left;">
				<input type="Submit" name="back" value="Back" id="jive-setup-back" border="0">
			</div>
			<!-- END left-aligned buttons -->

			<!-- BEGIN right-aligned buttons -->
			<div align="right">
				<a href="setup-ldap-user_test.jsp" class="lbOn" id="jive-setup-test2">
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
