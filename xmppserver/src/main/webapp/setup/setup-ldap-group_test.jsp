<%--
  -
  - Copyright (C) 2006-2007 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.admin.LdapGroupTester" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.slf4j.LoggerFactory" %>

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
            LoggerFactory.getLogger("setup-ldap-group_test.jsp").error("Error occurred while trying to get a sample of group data from LDAP.", e);
        }
        if (groups.isEmpty()) {
            // Inform user that no users were found
            errorDetail = LocaleUtils.getLocalizedString("setup.ldap.group.test.group-not-found");
        }
    } else {
        // Information was not found in the HTTP Session. Internal error?
        errorDetail = LocaleUtils.getLocalizedString("setup.invalid_session");
    }

    pageContext.setAttribute( "errorDetail", errorDetail );
    pageContext.setAttribute( "groups", groups );
%>

<!-- BEGIN connection settings test panel -->
<div class="jive-testPanel">
    <div class="jive-testPanel-content">

        <div align="right" class="jive-testPanel-close">
            <form method="dialog">
                <button><fmt:message key="setup.ldap.server.test.close" /></button>
            </form>
        </div>


        <h2><fmt:message key="setup.ldap.server.test.title" />: <span><fmt:message key="setup.ldap.group_mapping" /></span></h2>

        <p><fmt:message key="setup.ldap.group.test.description" /></p>

        <c:choose>
            <c:when test="${empty errorDetail}">
                <table cellpadding="0" cellspacing="1" class="jive-testTable-vcard" style="margin-right: 5px;">
                    <tr>
                        <td width="19%" class="jive-testpanel-vcard-header"><fmt:message key="group.summary.page_name" /></td>
                        <td width="80%" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.group.test.label-description" /></td>
                        <td style="width: 1%"  class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.group.test.label-members" /></td>
                    </tr>
                    <c:forEach items="${groups}" var="group">
                        <tr>
                            <td style="vertical-align: top" class="jive-testpanel-vcard-value"><c:out value="${group.name}"/></td>
                            <td style="vertical-align: top" class="jive-testpanel-vcard-value"><c:out value="${group.description}"/></td>
                            <td style="vertical-align: top" class="jive-testpanel-vcard-value"><c:out value="${group.members}"/></td>
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
