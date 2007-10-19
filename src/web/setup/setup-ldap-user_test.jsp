<%@ page import="org.jivesoftware.admin.LdapUserProfile" %>
<%@ page import="org.jivesoftware.admin.LdapUserTester" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    String errorDetail = null;
    Map<String, String> attributes = null;

    Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
    Map<String, String> userSettings = (Map<String, String>) session.getAttribute("ldapUserSettings");
    LdapUserProfile vCardSettings = (LdapUserProfile) session.getAttribute("ldapVCardBean");
    int userIndex = ParamUtils.getIntParameter(request, "userIndex", -1);
    if (settings != null && userSettings != null && vCardSettings != null) {
        LdapManager manager = new LdapManager(settings);
        manager.setUsernameField(userSettings.get("ldap.usernameField"));
        manager.setSearchFilter(userSettings.get("ldap.searchFilter"));

        // Build the tester with the recreated LdapManager and vcard mapping information
        LdapUserTester tester = new LdapUserTester(manager, vCardSettings);
        List<String> usernames = new ArrayList<String>();
        try {
            usernames = tester.getSample(40);
        }
        catch (Exception e) {
            // Inform user that an error occurred while trying to get users data
            errorDetail = LocaleUtils.getLocalizedString("setup.ldap.test.error-loading-sample");
            Log.error("Error occurred while trying to get users data from LDAP", e);
        }
        if (usernames.isEmpty()) {
            // Inform user that no users were found
            errorDetail = LocaleUtils.getLocalizedString("setup.ldap.user.test.users-not-found");
        } else {
            // Pick a user from the sample list of users
            userIndex = userIndex + 1;
            if (usernames.size() <= userIndex) {
                userIndex = 0;
            }
            // Get attributes for selected user
            attributes = tester.getAttributes(usernames.get(userIndex));
        }
    }
    else {
        // Information was not found in the HTTP Session. Internal error?
        errorDetail = LocaleUtils.getLocalizedString("setup.ldap.user.test.internal-server-error");
    }
%>
<html>
<head>
<meta name="decorator" content="none"/>
</head>
<body>
<script type="text/javascript" language="javascript" src="../js/tooltips/domTT.js"></script>
<script type="text/javascript" language="javascript" src="../js/tooltips/domLib.js"></script>
<style type="text/css">
#lightbox{
	top: 20%;
	margin-top: -20px;
	}

.jive-testPanel {
	margin-top: -100px;
	}
html>body .jive-testPanel {
	margin-top: 0px;
	}
</style>

<!-- BEGIN connection settings test panel -->
<div class="jive-testPanel">
	<div class="jive-testPanel-content">

		<div align="right" class="jive-testPanel-close">
			<a href="#" class="lbAction" rel="deactivate"><fmt:message key="setup.ldap.server.test.close" /></a>
		</div>


		<h2><fmt:message key="setup.ldap.server.test.title" />: <span><fmt:message key="setup.ldap.user_mapping" /></span></h2>

		<!--<h4 class="jive-testSuccess">Success!</h4>-->
		<!-- <h4 class="jive-testError">Error</h4> -->

		<p><fmt:message key="setup.ldap.user.vcard.test.description" /></p>

		<div class="jive-testpanel-vcard">
            <% if (attributes != null) { %>
            <table width="331" border="0" cellpadding="0" cellspacing="1" class="jive-testTable-vcard" style="margin-right: 5px;">
				<tr>
					<td colspan="2" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.user.vcard.personal" /></td>
				</tr>
				<tr>
                    <% String value = attributes.get(LdapUserTester.NAME);
                       boolean failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label" width="20%"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.name" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.EMAIL);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.email" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.NICKNAME);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.nickname" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BIRTHDAY);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.birthday" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.PHOTO);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.photo" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : StringUtils.chopAtWord(value , 17) + "..."%></td>
				</tr>
				<tr>
					<td colspan="2"></td>
				</tr>
				<tr>
					<td colspan="2" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.user.vcard.home" /></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.HOME_STREET);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.street" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.HOME_CITY);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.city" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.HOME_STATE);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.state" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.HOME_ZIP);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.pcode" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.HOME_COUNTRY);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.country" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.HOME_PHONE);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.phone" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.HOME_MOBILE);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.mobile" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.HOME_FAX);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.fax" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>

				<tr>
                    <% value = attributes.get(LdapUserTester.HOME_PAGER);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.pager" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
			</table>

			<table width="331" border="0" cellpadding="0" cellspacing="1" class="jive-testTable-vcard">
				<tr>
					<td colspan="2" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.user.vcard.business" /></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_STREET);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label" width="20%"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.street" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_CITY);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.city" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_STATE);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.state" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_ZIP);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.pcode" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_COUNTRY);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.country" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_JOB_TITLE);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.title" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_DEPARTMENT);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.department" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_PHONE);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.phone" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_MOBILE);
                        failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.mobile" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_FAX);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.fax" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
                    <% value = attributes.get(LdapUserTester.BUSINESS_PAGER);
                       failed = value != null && value.contains("{");
                    %>
					<td class="jive-testpanel-vcard-label"><%= value != null ? "<strong>" : ""%><fmt:message key="setup.ldap.user.vcard.pager" />:<%= value != null ? "</strong>" : ""%></td>
                    <td class="jive-testpanel-vcard-value"><%= failed || value == null? "" : value%></td>
				</tr>
				<tr>
					<td colspan="2" class="jive-testpanel-vcard-next">
						<a href="<%= ParamUtils.getParameter(request, "currentPage")%>?test=true&serverType=<%= ParamUtils.getParameter(request, "serverType")%>&userIndex=<%=userIndex%>"><fmt:message key="setup.ldap.user.vcard.test.random" /></a>
					</td>
				</tr>
			</table>
            <% } else { %>
            <h4 class="jive-testError"><fmt:message key="setup.ldap.server.test.status-error" /></h4>
            <p><%= errorDetail %></p>
            <% } %>
		</div>

	</div>
</div>


</body>
</html>
