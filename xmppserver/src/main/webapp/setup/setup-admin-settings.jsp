<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2016-2022 Ignite Realtime Foundation. All rights reserved.
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
<%--
--%>

<%@ page import="org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.auth.AuthFactory,
                 org.jivesoftware.openfire.ldap.LdapManager,
                 org.jivesoftware.openfire.user.User,
                 org.jivesoftware.openfire.user.UserManager,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="javax.servlet.http.HttpSession" %>
<%@ page import="java.util.*" %>
<%@ page import="org.jivesoftware.openfire.auth.UnauthorizedException" %>
<%@ page import="org.jivesoftware.openfire.XMPPServerInfo" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapGroupProvider" %>
<%@ page import="org.jivesoftware.openfire.admin.GroupBasedAdminProvider" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

<%
    // Redirect if we've already run setup:
    if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%! // Global vars, methods, etc
    void setSetupFinished(HttpSession session) {
        JiveGlobals.setXMLProperty("setup","true");
    }
%>

<%
    // Get parameters
    String username = ParamUtils.getParameter(request, "username");
    String password = ParamUtils.getParameter(request, "password");
    String email = ParamUtils.getParameter(request, "email");
    String newPassword = ParamUtils.getParameter(request, "newPassword");
    String newPasswordConfirm = ParamUtils.getParameter(request, "newPasswordConfirm");

    boolean doContinue = request.getParameter("continue") != null;
    boolean doSkip = request.getParameter("doSkip") != null;
    boolean doTest = request.getParameter("test") != null;

    boolean ldap = "true".equals(request.getParameter("ldap"));

    boolean addAdmin = request.getParameter("addAdministrator") != null;
    boolean deleteAdmins = request.getParameter("deleteAdmins") != null;
    boolean ldapFinished = request.getParameter("ldapFinished") != null;

    @SuppressWarnings("unchecked")
    Map<String,String> xmppSettings = (Map<String,String>)session.getAttribute("xmppSettings");
    String domain = xmppSettings.get(XMPPServerInfo.XMPP_DOMAIN.getKey());

    // Handle a skip request
    if (doSkip) {
        // assume the admin account is setup, so we're done:
        setSetupFinished(session);
        // redirect
        response.sendRedirect("setup-finished.jsp");
        return;
    }

    // Error checks
    Map<String, String> errors = new HashMap<>();
    if (doContinue) {
        if (password == null) {
            errors.put("password", "password");
        }
        try {
            AuthFactory.authenticate("admin", password);
        } catch (UnauthorizedException e) {
            errors.put("password", "password");
        } catch (Exception e) {
            e.printStackTrace();
            errors.put("general", "There was an unexpected error encountered when "
                + "setting the new admin information. Please check your error "
                + "logs and try to remedy the problem.");
        }
        if (email == null) {
            errors.put("email", "email");
        }
        if (newPassword == null) {
            errors.put("newPassword", "newPassword");
        }
        if (newPasswordConfirm == null) {
            errors.put("newPasswordConfirm", "newPasswordConfirm");
        }
        if (newPassword != null && newPasswordConfirm != null
                && !newPassword.equals(newPasswordConfirm)) {
            errors.put("match", "match");
        }
        // if no errors, continue:
        if (errors.size() == 0) {
            try {
                User adminUser = UserManager.getInstance().getUser("admin");
                adminUser.setPassword(newPassword);
                if (email != null) {
                    adminUser.setEmail(email);
                }
                Date now = new Date();
                adminUser.setCreationDate(now);
                adminUser.setModificationDate(now);

                // setup is finished, indicate so:
                setSetupFinished(session);
                // All good so redirect
                response.sendRedirect("setup-finished.jsp");
                return;
            }
            catch (Exception e) {
                //System.err.println("Could not find UserManager");
                e.printStackTrace();
                errors.put("general", "There was an unexpected error encountered when "
                        + "setting the new admin information. Please check your error "
                        + "logs and try to remedy the problem.");
            }
        }
    }

    if (ldapFinished) {
        setSetupFinished(session);
        // All good so redirect
        response.sendRedirect("setup-finished.jsp");
        return;
    }

    Cookie csrfCookie = CookieUtils.getCookie( request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (addAdmin || deleteAdmins)
    {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            errors.put( "csrf", "CSRF failure!");
            addAdmin = false;
            deleteAdmins = false;
        }
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (addAdmin && !doTest) {
        String admin = request.getParameter("administrator");
        boolean isGroup = ParamUtils.getBooleanParameter(request, "isGroup", false);
        if (admin != null) {
            admin = JID.escapeNode( admin );
            if (ldap) {
                // Try to verify that the username exists in LDAP
                Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
                Map<String, String> userSettings = (Map<String, String>) session.getAttribute("ldapUserSettings");
                Map<String, String> groupSettings = (Map<String, String>) session.getAttribute("ldapGroupSettings");

                if (settings != null) {
                    LdapManager manager = new LdapManager(settings);
                    if ( isGroup ) {
                        manager.setGroupNameField(groupSettings.get("ldap.groupNameField"));
                        manager.setGroupSearchFilter(groupSettings.get("ldap.groupSearchFilter"));
                        try {
                            manager.findGroupRDN(JID.unescapeNode(admin));

                            final Collection<JID> groupMembers = new LdapGroupProvider().getGroup(admin).getMembers();
                            if ( groupMembers.isEmpty() ) {
                                errors.put("group", "empty");
                            } else {

                                // Remove non-group (individual) admins
                                xmppSettings.remove("admin.authorizedJIDs");

                                // Set admin group provider.
                                xmppSettings.put("provider.admin.className", GroupBasedAdminProvider.class.getName());
                                xmppSettings.put("provider.group.groupBasedAdminProvider.groupName", admin);
                            }

                        } catch ( Exception e ) {
                            e.printStackTrace();
                            errors.put("administrator", "");
                        }
                    } else {
                        manager.setUsernameField(userSettings.get("ldap.usernameField"));
                        manager.setSearchFilter(userSettings.get("ldap.searchFilter"));
                        try {
                            manager.findUserRDN(JID.unescapeNode(admin));

                            // Add individual admin
                            String currentList = xmppSettings.get("admin.authorizedJIDs");
                            final List<String> users = new ArrayList<>(StringUtils.stringToCollection(currentList));
                            users.add(new JID(admin.toLowerCase(), domain, null).toBareJID());

                            String userList = StringUtils.collectionToString(users);
                            xmppSettings.put("admin.authorizedJIDs", userList);

                            // Remove admin group provider.
                            xmppSettings.remove( "provider.admin.className" );
                            xmppSettings.remove( "provider.group.groupBasedAdminProvider.groupName" );
                        } catch ( Exception e ) {
                            e.printStackTrace();
                            errors.put("administrator", "");
                        }
                    }
                }
            }
        } else {
            errors.put("administrator", "");
        }
    }

    if (deleteAdmins) {
        String[] params = request.getParameterValues("remove");
        String currentAdminList = xmppSettings.get("admin.authorizedJIDs");
        Collection<String> adminCollection = StringUtils.stringToCollection(currentAdminList);
        List<String> temporaryUserList = new ArrayList<>(adminCollection);
        final int no = params != null ? params.length : 0;
        for (int i = 0; i < no; i++) {
            temporaryUserList.remove( URLDecoder.decode( params[i] ));
        }

        String newUserList = StringUtils.collectionToString(temporaryUserList);
        if (temporaryUserList.size() == 0) {
            xmppSettings.put("admin.authorizedJIDs", "");
        } else {
            xmppSettings.put("admin.authorizedJIDs", newUserList);
        }
    }

    // This handles the case of reverting back to default settings from LDAP. Will
    // add admin to the authorizedJIDs list if the authorizedJIDs list contains
    // entries.
    if (!ldap && !doTest) {
        String currentAdminList = xmppSettings.get("admin.authorizedJIDs");
        List<String> adminCollection = new ArrayList<String>(StringUtils.stringToCollection(currentAdminList));
        if ((!adminCollection.isEmpty() && !adminCollection.contains("admin")) ||
                xmppSettings.get("admin.authorizedJIDs") != null) {
            adminCollection.add(new JID("admin", domain, null).toBareJID());
            xmppSettings.put("admin.authorizedJIDs",
                    StringUtils.collectionToString(adminCollection));
        }
    }

    // Save the updated settings
    session.setAttribute("xmppSettings", xmppSettings);

    pageContext.setAttribute( "ldap", ldap );
    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "username", username );
    pageContext.setAttribute( "password", password );
    pageContext.setAttribute( "newPassword", newPassword );
    pageContext.setAttribute( "newPasswordConfirm", newPasswordConfirm );
    pageContext.setAttribute( "doTest", doTest );

    // Populate authorized JIDs to be displayed on this page.
    final String groupName = xmppSettings.get("provider.group.groupBasedAdminProvider.groupName");
    if ( groupName != null ) {
        pageContext.setAttribute( "authorizedJIDs", new LdapGroupProvider().getGroup(groupName).getMembers() );
    } else {
        String currentList = xmppSettings.get("admin.authorizedJIDs");
        final List<String> users = new ArrayList<>(StringUtils.stringToCollection(currentList));
        pageContext.setAttribute( "authorizedJIDs", users.stream().map(JID::new).collect(Collectors.toSet()) );
    }
%>
<html>
<head>
    <title><fmt:message key="setup.admin.settings.account" /></title>
    <meta name="currentStep" content="4"/>
</head>
<body>

    <h1>
    <fmt:message key="setup.admin.settings.account" />
    </h1>

    <c:choose>
        <c:when test="${not ldap}">
    <p>
    <fmt:message key="setup.admin.settings.info" />
    </p>

    <c:if test="${not empty errors}">
        <div class="error">
            <c:choose>
                <c:when test="${not empty errors['csrf']}">
                    <fmt:message key="global.csrf.failed" />
                </c:when>
                <c:when test="${not empty errors['general']}">
                    <c:out value="${errors['general']}"/>
                </c:when>
                <c:when test="${not empty errors['administrator']}">
                    <fmt:message key="setup.admin.settings.username-error" />
                </c:when>
                <c:otherwise>
                    <fmt:message key="setup.admin.settings.error" />
                </c:otherwise>
            </c:choose>
        </div>
    </c:if>

    <!-- BEGIN jive-contentBox -->
    <div class="jive-contentBox">


<script>
var clicked = false;
function checkClick() {
    if (!clicked) {
        clicked = true;
        return true;
    }
    return false;
}
</script>

<form action="setup-admin-settings.jsp" name="acctform" method="post" onsubmit="return checkClick();">
    <input type="hidden" name="csrf" value="${csrf}"/>

<table cellpadding="3" cellspacing="2">

<%
    // If the current password is "admin", don't show the text box for them to type
    // the current password. This makes setup simpler for first-time users.
    boolean defaultPassword = false;
    try {
        AuthFactory.authenticate("admin", "admin");
        defaultPassword = true;
    }
    catch (UnauthorizedException e) {
        // Ignore.
    }
    catch (Exception e) {
        e.printStackTrace();
        errors.put("general", "There was an unexpected error encountered when "
            + "setting the new admin information. Please check your error "
            + "logs and try to remedy the problem.");
    }

    pageContext.setAttribute( "defaultPassword", defaultPassword );
%>
    <c:choose>
        <c:when test="${defaultPassword}">
            <input type="hidden" name="password" value="admin">
        </c:when>
        <c:otherwise>
            <tr>
                <td class="jive-label">
                    <label for="password"><fmt:message key="setup.admin.settings.current_password" /></label>
                </td>
                <td>
                    <input type="password" name="password" id="password" size="20" maxlength="50"
                     value="${not empty password ? fn:escapeXml(password) : ''}"><br>

                    <c:choose>
                        <c:when test="${not empty errors['password']}">
                            <span class="jive-error-text">
                                <fmt:message key="setup.admin.settings.current_password_error" />
                            </span>
                        </c:when>
                        <c:otherwise>
                            <span class="jive-description">
                                <fmt:message key="setup.admin.settings.current_password_description" />
                            </span>
                        </c:otherwise>
                    </c:choose>
                </td>
            </tr>
        </c:otherwise>
    </c:choose>

<%
    // Get the current email address, if there is one.
    String currentEmail = "";
    try {
        User adminUser = UserManager.getInstance().getUser("admin");
        if (adminUser.getEmail() != null) {
            currentEmail = adminUser.getEmail();
        }
    }
    catch (Exception e) {
        // Ignore.
    }

    pageContext.setAttribute( "email", email );
    pageContext.setAttribute( "currentEmail", currentEmail );
%>

<tr>
    <td class="jive-label" align="right">
        <label for="email"><fmt:message key="setup.admin.settings.email" /></label>
    </td>
    <td>
        <input type="text" name="email" size="40" maxlength="150" id="email"
         value="${not empty email ? fn:escapeXml(email) : fn:escapeXml(currentEmail)}"><br>

        <c:choose>
            <c:when test="${not empty errors['email']}">
                <span class="jive-error-text">
                    <fmt:message key="setup.admin.settings.email_error" />
                </span>
            </c:when>
            <c:otherwise>
                <span class="jive-description">
                    <fmt:message key="setup.admin.settings.email_description" />
                </span>
            </c:otherwise>
        </c:choose>
    </td>
</tr>
<tr>
    <td class="jive-label" align="right">
        <label for="newPassword"><fmt:message key="setup.admin.settings.new_password" /></label>
    </td>
    <td>
        <input type="password" name="newPassword" id="newPassword" size="20" maxlength="50"
         value="${not empty newPassword ? fn:escapeXml(newPassword) : ''}"><br>

        <c:choose>
            <c:when test="${not empty errors['newPassword']}">
                <span class="jive-error-text">
                    <fmt:message key="setup.admin.settings.valid_new_password" />
                </span>
            </c:when>
            <c:when test="${not empty errors['match']}">
                <span class="jive-error-text">
                    <fmt:message key="setup.admin.settings.not_new_password" />
                </span>
            </c:when>
        </c:choose>
    </td>
</tr>
<tr>
    <td class="jive-label" align="right">
        <label for="newPasswordConfirm"><fmt:message key="setup.admin.settings.confirm_password" /></label>
    </td>
    <td>
        <input type="password" name="newPasswordConfirm" id="newPasswordConfirm" size="20" maxlength="50"
         value="${not empty newPasswordConfirm ? fn:escapeXml(newPasswordConfirm) : ''}"><br>
        <c:if test="${not empty errors['newPasswordConfirm']}">
            <span class="jive-error-text">
                <fmt:message key="setup.admin.settings.valid_confirm" />
            </span>
        </c:if>
    </td>
</tr>
</table>

<br>
        <div align="right">
            <input type="submit" name="continue" value="<fmt:message key="global.continue" />" id="jive-setup-save">
            <input type="submit" name="doSkip" value="<fmt:message key="setup.admin.settings.skip_this_step" />" id="jive-setup-skip">
        </div>

    </form>
    </div>
    <!-- END jive-contentBox -->


<script>
<!--
document.acctform.newPassword.focus();
//-->
</script>


    </c:when>
    <c:otherwise>

        <c:if test="${not empty errors}">
            <div class="error">
                <c:choose>
                    <c:when test="${not empty errors['csrf']}">
                        <fmt:message key="global.csrf.failed" />
                    </c:when>
                    <c:when test="${not empty errors['general']}">
                        <c:out value="${errors['general']}"/>
                    </c:when>
                    <c:when test="${not empty errors['administrator']}">
                        <fmt:message key="setup.admin.settings.username-error" />
                    </c:when>
                    <c:otherwise>
                        <fmt:message key="setup.admin.settings.error" />
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <c:if test="${doTest}">
            <c:url var="testLink" value="setup-admin-settings_test.jsp">
                <c:param name="username" value="${username}"/>
                <c:if test="${not empty password}">
                    <c:param name="password" value="${password}"/>
                </c:if>
                <c:param name="ldap" value="true"/>
                <c:param name="csrf" value="${csrf}"/>
            </c:url>

            <dialog open>
                <c:import url="${testLink}"/>
            </dialog>

        </c:if>
    <p>
        <fmt:message key="setup.admin.settings.ldap.info"/>
        <fmt:message key="setup.admin.settings.ldap.info.group"/>
    </p>
<div class="jive-contentBox">

<form action="setup-admin-settings.jsp" name="acctform" method="post">
    <input type="hidden" name="csrf" value="${csrf}"/>

    <!-- Admin Table -->

<table cellpadding="3" cellspacing="2" style="margin-bottom: 1em;">
    <tr>
        <td class="jive-label">
            <label for="administrator"><fmt:message key="setup.admin.settings.add.administrator" />:</label>
        </td>
        <td>
            <input type="text" name="administrator" id="administrator" size="20" maxlength="50" value="${not empty xmppSettings['provider.group.groupBasedAdminProvider.groupName'] ? fn:escapeXml(xmppSettings['provider.group.groupBasedAdminProvider.groupName']) : ''}"/>
        </td>
    </tr>
    <tr>
        <td class="jive-label" colspan="2">
            <input type="radio" name="isGroup" value="false" id="isUser" ${not empty xmppSettings['provider.group.groupBasedAdminProvider.groupName'] ? '' : 'checked'}> <label for="isUser"><fmt:message key="setup.admin.settings.is-user" /></label>
        </td>
    </tr>
    <tr>
        <td class="jive-label" colspan="2">
            <input type="radio" name="isGroup" value="true" id="isGroup" ${not empty xmppSettings['provider.group.groupBasedAdminProvider.groupName'] ? 'checked' : ''}> <label for="isGroup"><fmt:message key="setup.admin.settings.is-group" /></label>
        </td>
    </tr>
    <tr>
        <td class="jive-label" colspan="2">
            <input type="submit" name="addAdministrator" value="<fmt:message key="global.add" />"/>
        </td>
    </tr>
</table>
        <c:if test="${not empty authorizedJIDs}">
            <!-- List of admins -->
            <div class="jive-table">
            <table>
            <tr>
                <th style="width: 1%; white-space: nowrap">&nbsp;</th>
                <th nowrap><fmt:message key="setup.admin.settings.administrator" /></th>
                <th style="width: 1%; white-space: nowrap"><fmt:message key="global.test" /></th>
                <c:if test="${not empty xmppSettings['admin.authorizedJIDs']}">
                    <th style="width: 1%; white-space: nowrap"><fmt:message key="setup.admin.settings.remove" /></th>
                </c:if>
            </tr>

            <c:forEach var="authJID" items="${authorizedJIDs}">
                <tr>
                    <td>&nbsp;</td>
                    <td>
                        <c:out value="${authJID.node}"/>
                    </td>
                    <td style="width: 1%; text-align: center">
                        <a href="setup-admin-settings.jsp?ldap=true&test=true&username=${admin:urlEncode(authJID.node)}&csrf=${csrf}"
                           title="<fmt:message key="global.click_test" />"
                        ><img src="../images/setup_btn_gearplay.gif" width="14" height="14" alt="<fmt:message key="global.click_test" />"></a>
                    </td>
                    <c:if test="${not empty xmppSettings['admin.authorizedJIDs']}">
                        <td>
                            <input type="checkbox" name="remove" value="${admin:urlEncode(authJID.toBareJID())}"/>
                        </td>
                    </c:if>
                </tr>
            </c:forEach>

            <c:if test="${not empty xmppSettings['admin.authorizedJIDs']}">
                <tr>
                    <td>
                       &nbsp;
                    </td>
                    <td>
                       &nbsp;
                    </td>
                    <td>
                        <input type="submit" name="deleteAdmins" value="Remove"/>
                    </td>
                </tr>
            </c:if>
            </table>
            </div>
        </c:if>

<input type="hidden" name="ldap" value="true"/>

     <div align="right">
    <br/>
  <input type="submit" name="ldapFinished" value="<fmt:message key="global.continue" />"  id="jive-setup-save" style="display:none;">
          </div>
 </form>

</div>

<c:if test="${not empty authorizedJIDs}">
    <script>
    document.getElementById("jive-setup-save").style.display = "";
    </script>
</c:if>

    </c:otherwise>
</c:choose>

</body>
</html>
