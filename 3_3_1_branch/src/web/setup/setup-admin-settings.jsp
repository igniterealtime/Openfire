<%--
  -	$RCSfile$
  -	$Revision: 1410 $
  -	$Date: 2005-05-26 23:00:40 -0700 (Thu, 26 May 2005) $
--%>

<%@ page import="org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.auth.AuthFactory,
                 org.jivesoftware.openfire.ldap.LdapManager,
                 org.jivesoftware.openfire.user.User" %>
<%@ page import="org.jivesoftware.openfire.user.UserManager"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="javax.servlet.http.HttpSession" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.*" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

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
                System.err.println("Could not find UserManager");
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

    if (addAdmin) {
        final String admin = request.getParameter("administrator");
        if (admin != null) {
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
                String currentList = JiveGlobals.getXMLProperty("admin.authorizedUsernames");
                final List users = new ArrayList(StringUtils.stringToCollection(currentList));
                users.add(admin);

                String userList = StringUtils.collectionToString(users);
                JiveGlobals.setXMLProperty("admin.authorizedUsernames", userList);
            }
        } else {
            errors.put("administrator", "");
        }
    }

    if (deleteAdmins) {
        String[] params = request.getParameterValues("remove");
        String currentAdminList = JiveGlobals.getXMLProperty("admin.authorizedUsernames");
        Collection<String> adminCollection = StringUtils.stringToCollection(currentAdminList);
        List temporaryUserList = new ArrayList<String>(adminCollection);
        final int no = params != null ? params.length : 0;
        for (int i = 0; i < no; i++) {
            temporaryUserList.remove(params[i]);
        }

        String newUserList = StringUtils.collectionToString(temporaryUserList);
        if (temporaryUserList.size() == 0) {
            JiveGlobals.setXMLProperty("admin.authorizedUsernames", "");
        } else {
            JiveGlobals.setXMLProperty("admin.authorizedUsernames", newUserList);
        }
    }

    // This handles the case of reverting back to default settings from LDAP. Will
    // add admin to the authorizedUsername list if the authorizedUsername list contains
    // entries.
    if (!ldap && !doTest) {
        String currentAdminList = JiveGlobals.getXMLProperty("admin.authorizedUsernames");
        List<String> adminCollection = new ArrayList<String>(StringUtils.stringToCollection(currentAdminList));
        if ((!adminCollection.isEmpty() && !adminCollection.contains("admin")) ||
                JiveGlobals.getXMLProperty("admin.authorizedJIDs") != null) {
            adminCollection.add("admin");
            JiveGlobals.setXMLProperty("admin.authorizedUsernames",
                    StringUtils.collectionToString(adminCollection));
        }
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

<% if(!ldap){ %>
    <p>
	<fmt:message key="setup.admin.settings.info" />
	</p>

<%  if (errors.size() > 0) { %>

    <div class="error">
    <%  if (errors.get("general") != null) { %>

        <%= errors.get("general") %>

    <%  } else if (errors.get("administrator") != null) { %>

        <fmt:message key="setup.admin.settings.username-error" />

    <%  } else { %>

        <fmt:message key="setup.admin.settings.error" />

    <%  } %>
    </div>

<%  } %>


	<!-- BEGIN jive-contentBox -->
	<div class="jive-contentBox">


<script language="JavaScript" type="text/javascript">
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

<table cellpadding="3" cellspacing="2" border="0">

<%
    // If the current password is "admin", don't show the text box for them to type
    // the current password. This makes setup simpler for first-time users.
    String currentPass = null;
    try {
        currentPass = AuthFactory.getPassword("admin");
    }
    catch (Exception e) {
        // Ignore.
    }
    if ("admin".equals(currentPass)) {
%>
<input type="hidden" name="password" value="admin">
<%
    }
    else {
%>

<tr valign="top">
    <td class="jive-label">
        <fmt:message key="setup.admin.settings.current_password" />
    </td>
    <td>
        <input type="password" name="password" size="20" maxlength="50"
         value="<%= ((password!=null) ? password : "") %>"><br>

        <%  if (errors.get("password") != null) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.admin.settings.current_password_error" />
            </span>
        <%  } else { %>
            <span class="jive-description">
            <fmt:message key="setup.admin.settings.current_password_description" />
            </span>
        <% } %>
    </td>
</tr>

<%  } %>

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
%>

<tr valign="top">
    <td class="jive-label" align="right">
        <fmt:message key="setup.admin.settings.email" />
    </td>
    <td>
        <input type="text" name="email" size="40" maxlength="150"
         value="<%= ((email!=null) ? email : currentEmail) %>"><br>

        <%  if (errors.get("email") != null) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.admin.settings.email_error" />
            </span>
        <%  } else { %>
            <span class="jive-description">
            <fmt:message key="setup.admin.settings.email_description" />
            </span>
        <% } %>
    </td>
</tr>
<tr valign="top">
    <td class="jive-label" align="right">
        <fmt:message key="setup.admin.settings.new_password" />
    </td>
    <td>
        <input type="password" name="newPassword" size="20" maxlength="50"
         value="<%= ((newPassword!=null) ? newPassword : "") %>"><br>

        <%  if (errors.get("newPassword") != null) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.admin.settings.valid_new_password" />
            </span>
        <%  } else if (errors.get("match") != null) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.admin.settings.not_new_password" />
            </span>
        <%  } %>
    </td>
</tr>
<tr valign="top">
    <td class="jive-label" align="right">
        <fmt:message key="setup.admin.settings.confirm_password" />
    </td>
    <td>
        <input type="password" name="newPasswordConfirm" size="20" maxlength="50"
         value="<%= ((newPasswordConfirm!=null) ? newPasswordConfirm : "") %>"><br>
        <%  if (errors.get("newPasswordConfirm") != null) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.admin.settings.valid_confirm" />
            </span>
        <%  } %>
    </td>
</tr>
</table>

<br>
		<div align="right">
			<input type="submit" name="doSkip" value="<fmt:message key="setup.admin.settings.skip_this_step" />" id="jive-setup-skip" border="0">
			<input type="Submit" name="continue" value="<fmt:message key="global.continue" />" id="jive-setup-save" border="0">
		</div>

	</form>
	</div>
	<!-- END jive-contentBox -->


<script language="JavaScript" type="text/javascript">
<!--
document.acctform.newPassword.focus();
//-->
</script>



<% } else {
    if (errors.size() > 0) { %>

        <div class="error">
        <%  if (errors.get("general") != null) { %>

            <%= errors.get("general") %>

        <%  } else if (errors.get("administrator") != null) { %>

            <fmt:message key="setup.admin.settings.username-error" />

        <%  } else { %>

            <fmt:message key="setup.admin.settings.error" />

        <%  } %>
        </div>

    <%  }
        if (doTest) {
            StringBuffer testLink = new StringBuffer();
            testLink.append("setup-admin-settings_test.jsp?username=");
            testLink.append(URLEncoder.encode(username, "UTF-8"));
            if (password != null) {
                testLink.append("&password=").append(URLEncoder.encode(password, "UTF-8"));
            }
    %>

        <a href="<%= testLink %>" id="lbmessage" title="<fmt:message key="global.test" />" style="display:none;"></a>
        <script type="text/javascript">
            function loadMsg() {
                var lb = new lightbox(document.getElementById('lbmessage'));
                lb.activate();
            }
            setTimeout('loadMsg()', 250);
        </script>

    <% } %>
    <p>
     <fmt:message key="setup.admin.settings.ldap.info" />
      </p>
    <div class="jive-contentBox">

    <form action="setup-admin-settings.jsp" name="acctform" method="post">

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
        String authorizedUsernames = JiveGlobals.getXMLProperty("admin.authorizedUsernames");
        boolean hasAuthorizedName = authorizedUsernames != null && authorizedUsernames.length() > 0;
%>
        <% if(hasAuthorizedName) { %>
        <!-- List of admins -->
        <table class="jive-vcardTable" cellpadding="3" cellspacing="0" border="0">
            <tr>
                <th nowrap><fmt:message key="setup.admin.settings.administrator" /></th>
                <th width="1%" nowrap><fmt:message key="global.test" /></th>
                <th width="1%" nowrap><fmt:message key="setup.admin.settings.remove" /></th>
            </tr>
    <%
        for (String authUsername : StringUtils.stringToCollection(authorizedUsernames)) {
    %>
        <tr valign="top">
            <td>
                <%= authUsername%>
            </td>
            <td width="1%" align="center">
                <a href="setup-admin-settings.jsp?ldap=true&test=true&username=<%= URLEncoder.encode(authUsername, "UTF-8") %>"
                 title="<fmt:message key="global.click_test" />"
                 ><img src="../images/setup_btn_gearplay.gif" width="14" height="14" border="0" alt="<fmt:message key="global.click_test" />"></a>
            </td>
            <td>
                <input type="checkbox" name="remove" value="<%=authUsername%>"/>
            </td>
        </tr>

        <%
            }
            if (authorizedUsernames != null) {
        %>
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

            <%
                }

            %>
    </table>
        <% } %>


    <input type="hidden" name="ldap" value="true"/>

         <div align="right">
        <br/>
      <input type="submit" name="ldapFinished" value="<fmt:message key="global.continue" />"  id="jive-setup-save" border="0" style="display:none;">
              </div>
     </form>

    </div>

    <%
        if(hasAuthorizedName) {%>
            <script type="text/javascript">
                document.getElementById("jive-setup-save").style.display = "";
            </script>
    <% } %>

<% } %>
</body>
</html>
