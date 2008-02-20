<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils, org.jivesoftware.openfire.ldap.LdapManager, org.jivesoftware.openfire.user.UserNotFoundException, org.xmpp.packet.JID" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.clearspace.ClearspaceManager" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    String username = ParamUtils.getParameter(request, "username");
    String password = ParamUtils.getParameter(request, "password");
    boolean ldap = "true".equals(request.getParameter("ldap"));
    boolean clearspace = "true".equals(request.getParameter("clearspace"));

    if (ldap) {
        boolean success = false;
        String errorDetail = "";
        Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
        Map<String, String> userSettings =
                (Map<String, String>) session.getAttribute("ldapUserSettings");
        // Run the test if password was provided and we have the ldap information
        if (settings != null && password != null) {
            LdapManager manager = new LdapManager(settings);
            manager.setUsernameField(userSettings.get("ldap.usernameField"));
            manager.setSearchFilter(userSettings.get("ldap.searchFilter"));
            try {
                String userDN = manager.findUserDN(JID.unescapeNode(username));
                // See if the user authenticates.
                if (manager.checkAuthentication(userDN, password)) {
                    // User was able to authenticate with provided password
                    success = true;
                }
                else {
                    errorDetail = LocaleUtils.getLocalizedString("setup.admin.settings.test.error-password");
                }
            }
            catch (UserNotFoundException e) {
                errorDetail = LocaleUtils.getLocalizedString("setup.admin.settings.test.error-user");
            }
            catch (Exception e) {
                errorDetail = e.getMessage();
                e.printStackTrace();
            }
        }
%>
    <!-- BEGIN connection settings test panel -->
	<div class="jive-testPanel">
		<div class="jive-testPanel-content">

			<div align="right" class="jive-testPanel-close">
				<a href="#" class="lbAction" rel="deactivate"><fmt:message key="setup.ldap.server.test.close" /></a>
			</div>

            <h2><fmt:message key="global.test" />: <span><fmt:message key="setup.admin.settings.test.title-desc" /></span></h2>
            <% if (password != null) { %>
                <% if (success) { %>
                <h4 class="jive-testSuccess"><fmt:message key="setup.admin.settings.test.status-success" /></h4>

		    	<p><fmt:message key="setup.admin.settings.test.status-success.detail" /></p>
                <% } else { %>
                <h4 class="jive-testError"><fmt:message key="setup.admin.settings.test.status-error" /></h4>
                <p><%= errorDetail %></p>
                <% }
                }
                if (!success) {
             %>
            <form action="setup-admin-settings.jsp" name="testform" method="post">
                <input type="hidden" name="ldap" value="true">
                <input type="hidden" name="test" value="true">
                <input type="hidden" name="username" value="<%= URLEncoder.encode(username, "UTF-8")%>">
                <table cellpadding="3" cellspacing="2" border="0">
                    <tr valign="top">
                        <td class="jive-label">
                            <fmt:message key="setup.admin.settings.administrator" />:
                        </td>
                         <td>
                        <%= JID.unescapeNode(username) %>
                        </td>
                        <td>
                            &nbsp;
                        </td>
                    </tr>
                    <tr valign="top">
                        <td class="jive-label">
                            <fmt:message key="setup.ldap.server.password" />:
                        </td>
                         <td>
                        <input type="password" name="password" size="20" maxlength="50"/>
                        </td>
                        <td>
                            <input type="submit" name="addAdministrator" value="<fmt:message key="global.test" />"/>
                        </td>
                    </tr>
                </table>
            </form>
            <% } %>
        </div>
	</div>
	<!-- END connection settings test panel -->
<%  } else if (clearspace) {
    boolean success = false;
    String errorDetail = "";
    Map<String, String> settings = (Map<String, String>) session.getAttribute("clearspaceSettings");
    // Run the test if password was provided and we have the clearspace information
    if (settings != null && password != null) {
        ClearspaceManager manager = new ClearspaceManager(settings);
        try {
            // See if the user authenticates.
            if (manager.checkAuthentication(username, password)) {
                // User was able to authenticate with provided password
                success = true;
            }
            else {
                errorDetail = LocaleUtils.getLocalizedString("setup.admin.settings.test.error-password");
            }
        }
//        catch (UserNotFoundException e) {
//            errorDetail = LocaleUtils.getLocalizedString("setup.admin.settings.test.error-user");
//        }
        catch (Exception e) {
            errorDetail = e.getMessage();
            e.printStackTrace();
        }
    }
%>
<!-- BEGIN connection settings test panel -->
<div class="jive-testPanel">
    <div class="jive-testPanel-content">

        <div align="right" class="jive-testPanel-close">
            <a href="#" class="lbAction" rel="deactivate"><fmt:message key="setup.clearspace.service.test.close" /></a>
        </div>

        <h2><fmt:message key="global.test" />: <span><fmt:message key="setup.admin.settings.test.title-desc" /></span></h2>
        <% if (password != null) { %>
            <% if (success) { %>
            <h4 class="jive-testSuccess"><fmt:message key="setup.admin.settings.test.status-success" /></h4>

            <p><fmt:message key="setup.admin.settings.test.status-success.detail" /></p>
            <% } else { %>
            <h4 class="jive-testError"><fmt:message key="setup.admin.settings.test.status-error" /></h4>
            <p><%= errorDetail %></p>
            <% }
            }
            if (!success) {
         %>
        <form action="setup-admin-settings.jsp" name="testform" method="post">
            <input type="hidden" name="clearspace" value="true">
            <input type="hidden" name="test" value="true">
            <input type="hidden" name="username" value="<%= URLEncoder.encode(username, "UTF-8")%>">
            <table cellpadding="3" cellspacing="2" border="0">
                <tr valign="top">
                    <td class="jive-label">
                        <fmt:message key="setup.admin.settings.administrator" />:
                    </td>
                     <td>
                    <%= JID.unescapeNode(username) %>
                    </td>
                    <td>
                        &nbsp;
                    </td>
                </tr>
                <tr valign="top">
                    <td class="jive-label">
                        <fmt:message key="setup.clearspace.service.password" />:
                    </td>
                     <td>
                    <input type="password" name="password" size="20" maxlength="50"/>
                    </td>
                    <td>
                        <input type="submit" name="addAdministrator" value="<fmt:message key="global.test" />"/>
                    </td>
                </tr>
            </table>
        </form>
        <% } %>
    </div>
</div>
<!-- END connection settings test panel -->
<%  } %>