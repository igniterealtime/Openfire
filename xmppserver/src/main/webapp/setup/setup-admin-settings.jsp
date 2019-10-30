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
    Map<String, String> errors = new HashMap<String, String>();
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
        if (admin != null) {
            admin = JID.escapeNode( admin );
            if (ldap) {
                // Try to verify that the username exists in LDAP
                Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
                Map<String, String> userSettings = (Map<String, String>) session.getAttribute("ldapUserSettings");
                if (settings != null) {
                    LdapManager manager = new LdapManager(settings);
                    manager.setUsernameField(userSettings.get("ldap.usernameField"));
                    manager.setSearchFilter(userSettings.get("ldap.searchFilter"));
                    try {
                        manager.findUserDN(JID.unescapeNode(admin));
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        errors.put("administrator", "");
                    }
                }
            }
            if (errors.isEmpty()) {
                String currentList = xmppSettings.get("admin.authorizedJIDs");
                final List<String> users = new ArrayList<>(StringUtils.stringToCollection(currentList));
                users.add(new JID(admin.toLowerCase(), domain, null).toBareJID());

                String userList = StringUtils.collectionToString(users);
                xmppSettings.put("admin.authorizedJIDs", userList);
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


<script type="text/javascript">
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

<table cellpadding="3" cellspacing="2" border="0">

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
            <tr valign="top">
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

<tr valign="top">
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
<tr valign="top">
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
<tr valign="top">
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
            <input type="submit" name="continue" value="<fmt:message key="global.continue" />" id="jive-setup-save" border="0">
            <input type="submit" name="doSkip" value="<fmt:message key="setup.admin.settings.skip_this_step" />" id="jive-setup-skip" border="0">
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

    <a href="${testLink}" id="lbmessage" title="<fmt:message key="global.test" />" style="display:none;"></a>
    <script type="text/javascript">
        function loadMsg() {
            var lb = new lightbox(document.getElementById('lbmessage'));
            lb.activate();
        }
        setTimeout('loadMsg()', 250);
    </script>

        </c:if>
<p>
 <fmt:message key="setup.admin.settings.ldap.info" />
  </p>
<div class="jive-contentBox">

<form action="setup-admin-settings.jsp" name="acctform" method="post">
    <input type="hidden" name="csrf" value="${csrf}"/>

    <!-- Admin Table -->

<table cellpadding="3" cellspacing="2" border="0">
    <tr valign="top">
        <td class="jive-label">
            <fmt:message key="setup.admin.settings.add.administrator" />:
        </td>
         <td>
        <input type="text" name="administrator" size="20" maxlength="50"/>
        </td>
        <td>
            <input type="submit" name="addAdministrator" value="<fmt:message key="global.add" />"/>
        </td>
    </tr>
</table>
<%
    Collection<JID> authorizedJIDs = StringUtils.stringToCollection( xmppSettings.get("admin.authorizedJIDs") ).stream().map( JID::new ).collect( Collectors.toSet() );
    pageContext.setAttribute( "authorizedJIDs", authorizedJIDs );
%>
        <c:if test="${not empty authorizedJIDs}">
            <!-- List of admins -->
            <table class="jive-vcardTable" cellpadding="3" cellspacing="0" border="0">
            <tr>
                <th nowrap><fmt:message key="setup.admin.settings.administrator" /></th>
                <th width="1%" nowrap><fmt:message key="global.test" /></th>
                <th width="1%" nowrap><fmt:message key="setup.admin.settings.remove" /></th>
            </tr>

            <c:forEach var="authJID" items="${authorizedJIDs}">
                <tr valign="top">
                    <td>
                        <c:out value="${authJID.node}"/>
                    </td>
                    <td width="1%" align="center">
                        <a href="setup-admin-settings.jsp?ldap=true&test=true&username=${admin:urlEncode(authJID.node)}&csrf=${csrf}"
                           title="<fmt:message key="global.click_test" />"
                        ><img src="../images/setup_btn_gearplay.gif" width="14" height="14" border="0" alt="<fmt:message key="global.click_test" />"></a>
                    </td>
                    <td>
                        <input type="checkbox" name="remove" value="${admin:urlEncode(authJID.toBareJID())}"/>
                    </td>
                </tr>
            </c:forEach>

         <tr valign="top">
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
            </table>

        </c:if>

<input type="hidden" name="ldap" value="true"/>

     <div align="right">
    <br/>
  <input type="submit" name="ldapFinished" value="<fmt:message key="global.continue" />"  id="jive-setup-save" border="0" style="display:none;">
          </div>
 </form>

</div>

<c:if test="${not empty authorizedJIDs}">
    <script type="text/javascript">
    document.getElementById("jive-setup-save").style.display = "";
    </script>
</c:if>

    </c:otherwise>
</c:choose>

</body>
</html>
