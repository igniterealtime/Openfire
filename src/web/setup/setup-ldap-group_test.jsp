<%@ page import="org.jivesoftware.admin.LdapGroupTester" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    String errorDetail = null;
    Collection<LdapGroupTester.Group> groups = new ArrayList<LdapGroupTester.Group>();

    Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
    Map<String, String> userSettings = (Map<String, String>) session.getAttribute("ldapUserSettings");
    Map<String, String> groupSettings = (Map<String, String>) session.getAttribute("ldapGroupSettings");
    if (settings != null && userSettings != null && groupSettings != null) {
        LdapManager manager = new LdapManager(settings);
        manager.setUsernameField(userSettings.get("ldap.usernameField"));
        manager.setSearchFilter(userSettings.get("ldap.searchFilter"));
        manager.setGroupNameField(groupSettings.get("ldap.groupNameField"));
        manager.setGroupDescriptionField(groupSettings.get("ldap.groupDescriptionField"));
        manager.setGroupMemberField(groupSettings.get("ldap.groupMemberField"));
        manager.setGroupSearchFilter(groupSettings.get("ldap.groupSearchFilter"));

        // Build the tester with the recreated LdapManager and vcard mapping information
        LdapGroupTester tester = new LdapGroupTester(manager);
        try {
            groups = tester.getGroups(10);
        }
        catch (Exception e) {
            // Inform user that an error occurred while trying to get users data
            errorDetail = LocaleUtils.getLocalizedString("setup.ldap.test.error-loading-sample");
            Log.error("Error occurred while trying to get users data from LDAP", e);
        }
        if (groups.isEmpty()) {
            // Inform user that no users were found
            errorDetail = LocaleUtils.getLocalizedString("setup.ldap.group.test.group-not-found");
        }
    } else {
        // Information was not found in the HTTP Session. Internal error?
        errorDetail = LocaleUtils.getLocalizedString("setup.ldap.test.internal-server-error");
    }
%>

<html>
<head>
<meta name="decorator" content="none"/>
</head>
<body>


<!-- BEGIN connection settings test panel -->
<div class="jive-testPanel">
	<div class="jive-testPanel-content">

		<div align="right" class="jive-testPanel-close">
			<a href="#" class="lbAction" rel="deactivate">Close</a>
		</div>


		<h2><fmt:message key="setup.ldap.server.test.title" />: <span><fmt:message key="setup.ldap.group_mapping" /></span></h2>

        <p><fmt:message key="setup.ldap.group.test.description" /></p>

        <% if (errorDetail == null) { %>

        <table border="0" cellpadding="0" cellspacing="1" class="jive-testTable-vcard" style="margin-right: 5px;">
            <tr>
                <td width="19%" class="jive-testpanel-vcard-header"><fmt:message key="group.summary.page_name" /></td>
                <td width="80%" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.group.test.label-description" /></td>
                <td width="1%" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.group.test.label-members" /></td>
            </tr>
            <% for (LdapGroupTester.Group group : groups) { %>
            <tr>
                <td valign="top" class="jive-testpanel-vcard-value"><%= group.getName()%></td>
                <td valign="top" class="jive-testpanel-vcard-value"><%= group.getDescription()%></td>
                <td valign="top" class="jive-testpanel-vcard-value"><%= group.getMembers()%></td>
            </tr>
            <% } %>
        </table>
        <% } else { %>
        <h4 class="jive-testError"><fmt:message key="setup.ldap.server.test.status-error" /></h4>
        <p><%= errorDetail %></p>
        <% }%>
    </div>
</div>


</body>
</html>
