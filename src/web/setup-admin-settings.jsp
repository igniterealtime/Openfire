<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 java.util.Date,
                 org.jivesoftware.messenger.user.User,
                 org.jivesoftware.messenger.user.UserManager,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 org.jivesoftware.messenger.auth.AuthFactory,
                 org.jivesoftware.messenger.auth.AuthToken,
                 org.jivesoftware.messenger.JiveGlobals,
                 org.jivesoftware.messenger.auth.DefaultAuthProvider,
                 org.jivesoftware.messenger.spi.BasicServer" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%! // Global vars, methods, etc
    void setSetupFinished(HttpSession session) {
        JiveGlobals.setXMLProperty("setup","true");
        // update the sidebar status
        session.setAttribute("jive.setup.sidebar.4","done");
        // Indicate a server is required:
        session.setAttribute("jive.setup.requireRestart","true");
    }
%>

<%@ include file="setup-global.jspf" %>
<jsp:useBean id="adminManager" class="org.jivesoftware.util.WebManager" />

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
    Map errors = new HashMap();
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

                // TODO: Check for Plugin

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

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
Administrator Account
</p>

<p>
Enter settings for the system administrator account below. It is important choose a password for the
account that cannot be easily guessed -- for example, at least six characters long and containing a
mix of letters and numbers. You can skip this step if you have already setup your admin
account (not for first time users).
</p>

<%  if (errors.size() > 0) { %>

    <span class="jive-error-text">
    <%  if (errors.get("general") != null) { %>

        <%= errors.get("general") %>

    <%  } else { %>

        There were errors when updating the admin account. Please see below.

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
<tr valign="top">
    <td class="jive-label">
        Current Password:
    </td>
    <td>
        <input type="password" name="password" size="20" maxlength="50"
         value="<%= ((password!=null) ? password : "") %>">
        <span class="jive-description">
        <br>
        If this is a new <fmt:message key="short.title" /> installation, the current password will be <b>admin</b>.
        </span>
        <%  if (errors.get("password") != null) { %>
            <span class="jive-error-text">
            <br>Please enter the correct current password.
            </span>
        <%  } %>
    </td>
</tr>
<tr valign="top">
    <td class="jive-label">
        Admin Email Address:
    </td>
    <td>
        <input type="text" name="email" size="40" maxlength="150"
         value="<%= ((email!=null) ? email : "") %>">
        <span class="jive-description">
        <br>
        A valid email address for the admin account.
        </span>
        <%  if (errors.get("email") != null) { %>
            <span class="jive-error-text">
            <br>Please enter a valid email address.
            </span>
        <%  } %>
    </td>
</tr>
<tr valign="top">
    <td class="jive-label">
        New Password:
    </td>
    <td>
        <input type="password" name="newPassword" size="20" maxlength="50"
         value="<%= ((newPassword!=null) ? newPassword : "") %>">
        <span class="jive-description">

        </span>
        <%  if (errors.get("newPassword") != null) { %>
            <span class="jive-error-text">
            <br>Please enter a valid new password.
            </span>
        <%  } else if (errors.get("match") != null) { %>
            <span class="jive-error-text">
            <br>The new passwords do not match.
            </span>
        <%  } %>
    </td>
</tr>
<tr valign="top">
    <td class="jive-label">
        Confirm Password:
    </td>
    <td>
        <input type="password" name="newPasswordConfirm" size="20" maxlength="50"
         value="<%= ((newPasswordConfirm!=null) ? newPasswordConfirm : "") %>">
        <span class="jive-description">

        </span>
        <%  if (errors.get("newPasswordConfirm") != null) { %>
            <span class="jive-error-text">
            <br>Please enter a valid new confirmation password.
            </span>
        <%  } %>
    </td>
</tr>
</table>

<br><br>

<hr size="0">

<div align="right">
    <input type="submit" name="continue" value=" Continue ">
    <input type="submit" name="doSkip" value="Skip This Step">
</div>

</form>

<script language="JavaScript" type="text/javascript">
<!--
document.acctform.password.focus();
//-->
</script>

<%@ include file="setup-footer.jsp" %>