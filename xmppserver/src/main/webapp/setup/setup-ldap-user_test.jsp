<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.admin.LdapUserProfile" %>
<%@ page import="org.jivesoftware.admin.LdapUserTester" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

<%
    String errorDetail = null;
    Map<String, String> attributes = null;

    Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
    Map<String, String> userSettings = (Map<String, String>) session.getAttribute("ldapUserSettings");
    LdapUserProfile vCardSettings = (LdapUserProfile) session.getAttribute("ldapVCardBean");
    int userIndex = ParamUtils.getIntParameter(request, "userIndex", -1);
    if (settings != null && userSettings != null && vCardSettings != null) {
        settings.computeIfAbsent( "ldap.adminPassword", (key) -> LdapManager.getInstance().getAdminPassword() );
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
            final String username = usernames.get( userIndex );
            attributes = tester.getAttributes( username );

            if ( attributes == null || attributes.isEmpty() ) {
                errorDetail = "Unable to get attributes for: " + username;
            } else {
                // Postprocessing - remove all values that include the '{' character.
                for ( final Map.Entry<String, String> entry : attributes.entrySet() ) {
                    if ( entry.getValue().contains( "{" ) ){
                        entry.setValue( null );
                    }
                }
            }
        }
    }
    else {
        // Information was not found in the HTTP Session. Internal error?
        errorDetail = LocaleUtils.getLocalizedString("setup.ldap.user.test.internal-server-error");
    }

    pageContext.setAttribute( "attributes", attributes );
    pageContext.setAttribute( "errorDetail", errorDetail );
    pageContext.setAttribute( "currentPage", ParamUtils.getParameter(request, "currentPage") );
    pageContext.setAttribute( "serverType", ParamUtils.getParameter(request, "serverType") );
    pageContext.setAttribute( "userIndex", userIndex );
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
            <c:choose>
                <c:when test="${not empty attributes}">

            <table width="331" border="0" cellpadding="0" cellspacing="1" class="jive-testTable-vcard" style="margin-right: 5px;">
                <tr>
                    <td colspan="2" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.user.vcard.personal" /></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" width="20%" style="${not empty attributes['Name'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.name" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['Name'] ? attributes['Name'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['Email'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.email" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['Email'] ? attributes['Email'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['Nickname'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.nickname" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['Nickname'] ? attributes['Nickname'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['Birthday'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.birthday" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['Birthday'] ? attributes['Birthday'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['Photo'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.photo" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['Photo'] ? admin:chopAtWord(attributes['Photo'], 17) : ''}"/></td>
                </tr>
                <tr>
                    <td colspan="2"></td>
                </tr>
                <tr>
                    <td colspan="2" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.user.vcard.home" /></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['HomeStreet'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.street" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['HomeStreet'] ? attributes['HomeStreet'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['HomeCity'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.city" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['HomeCity'] ? attributes['HomeCity'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['HomeState'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.state" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['HomeState'] ? attributes['HomeState'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['HomeZip'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.pcode" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['HomeZip'] ? attributes['HomeZip'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['HomeCountry'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.country" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['HomeCountry'] ? attributes['HomeCountry'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['HomePhone'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.phone" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['HomePhone'] ? attributes['HomePhone'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['HomeMobile'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.mobile" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['HomeMobile'] ? attributes['HomeMobile'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['HomeFax'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.fax" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['HomeFax'] ? attributes['HomeFax'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['HomePager'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.pager" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['HomePager'] ? attributes['HomePager'] : ''}"/></td>
                </tr>
            </table>

            <table width="331" border="0" cellpadding="0" cellspacing="1" class="jive-testTable-vcard">
                <tr>
                    <td colspan="2" class="jive-testpanel-vcard-header"><fmt:message key="setup.ldap.user.vcard.business" /></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessStreet'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.street" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessStreet'] ? attributes['BusinessStreet'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessCity'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.city" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessCity'] ? attributes['BusinessCity'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessState'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.state" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessState'] ? attributes['BusinessState'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessZip'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.pcode" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessZip'] ? attributes['BusinessZip'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessCountry'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.country" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessCountry'] ? attributes['BusinessCountry'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessJobTitle'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.title" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessJobTitle'] ? attributes['BusinessJobTitle'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessDepartment'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.department" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessDepartment'] ? attributes['BusinessDepartment'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessPhone'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.phone" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessPhone'] ? attributes['BusinessPhone'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessMobile'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.mobile" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessMobile'] ? attributes['BusinessMobile'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessFax'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.fax" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessFax'] ? attributes['BusinessFax'] : ''}"/></td>
                </tr>
                <tr>
                    <td class="jive-testpanel-vcard-label" style="${not empty attributes['BusinessPager'] ? 'font-weight: bold' : ''}"><fmt:message key="setup.ldap.user.vcard.pager" />:</td>
                    <td class="jive-testpanel-vcard-value"><c:out value="${not empty attributes['BusinessPager'] ? attributes['BusinessPager'] : ''}"/></td>
                </tr>
                <tr>
                    <td colspan="2" class="jive-testpanel-vcard-next">
                        <c:url var="nextPage" value="${currentPage}">
                            <c:param name="test" value="true"/>
                            <c:param name="serverType" value="${serverType}"/>
                            <c:param name="userIndex" value="${userIndex}"/>
                        </c:url>
                        <a href="${nextPage}"><fmt:message key="setup.ldap.user.vcard.test.random" /></a>
                    </td>
                </tr>
            </table>
                </c:when>
                <c:otherwise>
                    <h4 class="jive-testError"><fmt:message key="setup.ldap.server.test.status-error" /></h4>
                    <p><c:out value="${errorDetail}"/></p>
                    <p>
                        <c:url var="nextPage" value="${currentPage}">
                            <c:param name="test" value="true"/>
                            <c:param name="serverType" value="${serverType}"/>
                            <c:param name="userIndex" value="${userIndex}"/>
                        </c:url>
                        <a href="${nextPage}"><fmt:message key="setup.ldap.user.vcard.test.random" /></a>
                    </p>
                </c:otherwise>
            </c:choose>
        </div>

    </div>
</div>


</body>
</html>
