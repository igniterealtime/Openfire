<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.admin.LdapGroupTester" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    String errorDetail = null;
    Collection<LdapGroupTester.Group> groups = new ArrayList<>();

    Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
    Map<String, String> userSettings = (Map<String, String>) session.getAttribute("ldapUserSettings");
    Map<String, String> groupSettings = (Map<String, String>) session.getAttribute("ldapGroupSettings");
    if (settings != null && userSettings != null && groupSettings != null) {
        settings.computeIfAbsent( "ldap.adminPassword", (key) -> LdapManager.getInstance().getAdminPassword() );
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

    pageContext.setAttribute( "errorDetail", errorDetail );
    pageContext.setAttribute( "groups", groups );
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

        <c:choose>
            <c:when test="${empty errorDetail}">
                <table border="0" cellpadding="0" cellspacing="1" class="jive-testTable-vcard" style="margin-right: 5px;">
                    <tr>
                        <td width="19%" class="jive-testpanel-vcard-header"><fmt:message key="group.summary.page_name" /></td>
                        <td width="80%" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.group.test.label-description" /></td>
                        <td width="1%" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.group.test.label-members" /></td>
                    </tr>
                    <c:forEach items="${groups}" var="group">
                        <tr>
                            <td valign="top" class="jive-testpanel-vcard-value"><c:out value="${group.name}"/></td>
                            <td valign="top" class="jive-testpanel-vcard-value"><c:out value="${group.description}"/></td>
                            <td valign="top" class="jive-testpanel-vcard-value"><c:out value="${group.members}"/></td>
                        </tr>
                    </c:forEach>
                </table>
            </c:when>
            <c:otherwise>
                <h4 class="jive-testError"><fmt:message key="setup.ldap.server.test.status-error" /></h4>
                <p><c:out value="${errorDetail}"/></p>
            </c:otherwise>
        </c:choose>
    </div>
</div>


</body>
</html>
