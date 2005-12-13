<%--
  -	$RCSfile$
  -	$Revision: 1410 $
  -	$Date: 2005-05-26 23:00:40 -0700 (Thu, 26 May 2005) $
--%>

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

<%! // Global vars, methods, etc
    void setSetupFinished(HttpSession session) {
        JiveGlobals.setXMLProperty("setup","true");
        // update the sidebar status
        session.setAttribute("jive.setup.sidebar.4","done");
    }
%>

<%  // Get parameters
    String password = ParamUtils.getParameter(request,"password");
    String email = ParamUtils.getParameter(request,"email");
    String newPassword = ParamUtils.getParameter(request,"newPassword");
    String newPasswordConfirm = ParamUtils.getParameter(request,"newPasswordConfirm");

    boolean doContinue = request.getParameter("continue") != null;
    boolean doSkip = request.getParameter("doSkip") != null;

    // Handle a skip request
    if (doSkip) {
        // assume the admin account is setup, so we're done:
        setSetupFinished(session);
        // redirect
        response.sendRedirect("setup-finished.jsp");
        return;
    }

    // Error checks
    Map<String,String> errors = new HashMap<String,String>();
    if (doContinue) {
        if (password == null) {
            errors.put("password","password");
        }
        if (email == null) {
            errors.put("email","email");
        }
        if (newPassword == null) {
            errors.put("newPassword","newPassword");
        }
        if (newPasswordConfirm == null) {
            errors.put("newPasswordConfirm","newPasswordConfirm");
        }
        if (newPassword != null && newPasswordConfirm != null
                && !newPassword.equals(newPasswordConfirm))
        {
            errors.put("match","match");
        }
        // if no errors, continue:
        if (errors.size() == 0) {
            try {
                // Get the service
                UserManager userManager = UserManager.getInstance();

                User adminUser = userManager.getUser("admin");

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
                errors.put("general","There was an unexpected error encountered when "
                        + "setting the new admin information. Please check your error "
                        + "logs and try to remedy the problem.");
            }
        }
    }
%>
<html>
    <head>
        <title><fmt:message key="setup.admin.settings.account" /></title>
    </head>
<body>

<p class="jive-setup-page-header">
<fmt:message key="setup.admin.settings.account" />
</p>

<p>
<fmt:message key="setup.admin.settings.info" />
</p>

<%  if (errors.size() > 0) { %>

    <span class="jive-error-text">
    <%  if (errors.get("general") != null) { %>

        <%= errors.get("general") %>

    <%  } else { %>

        <fmt:message key="setup.admin.settings.error" />

    <%  } %>
    </span>

<%  } %>

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

<tr valign="top">
    <td class="jive-label">
        <fmt:message key="setup.admin.settings.email" />
    </td>
    <td>
        <input type="text" name="email" size="40" maxlength="150"
         value="<%= ((email!=null) ? email : "") %>"><br>

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
    <td class="jive-label">
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
    <td class="jive-label">
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

<br><br>

<hr size="0">

<div align="right">
    <input type="submit" name="continue" value=" <fmt:message key="global.continue" /> ">
    <input type="submit" name="doSkip" value="<fmt:message key="setup.admin.settings.skip_this_step" />">
</div>

</form>

<script language="JavaScript" type="text/javascript">
<!--
document.acctform.password.focus();
//-->
</script>

</body>
</html>
