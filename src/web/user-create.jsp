<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.user.*,
                 java.net.URLEncoder,
                 org.jivesoftware.stringprep.Stringprep,
                 org.jivesoftware.stringprep.StringprepException"
    errorPage="error.jsp"
%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.HashMap"%><%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.jivesoftware.openfire.security.SecurityAuditManager" %>
<%@ page import="org.jivesoftware.openfire.admin.AdminManager" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean another = request.getParameter("another") != null;
    boolean create = another || request.getParameter("create") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String username = ParamUtils.getParameter(request,"username");
    String name = ParamUtils.getParameter(request,"name");
    String email = ParamUtils.getParameter(request,"email");
    String password = ParamUtils.getParameter(request,"password");
    String passwordConfirm = ParamUtils.getParameter(request,"passwordConfirm");
    boolean isAdmin = ParamUtils.getBooleanParameter(request,"isadmin");

    Map<String, String> errors = new HashMap<String, String>();
    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-summary.jsp");
        return;
    }

    // Handle a request to create a user:
    if (create) {
        // Validate
        if (username == null) {
            errors.put("username","");
        }
        else {
            try {
                username = username.trim().toLowerCase();
                username = JID.escapeNode(username);
                username = Stringprep.nodeprep(username);
            }
            catch (StringprepException se) {
                errors.put("username", "");
            }
        }
        // Trim the password. This means we don't accept spaces as passwords. We don't
        // trim the passwordConfirm as well since not trimming will ensure the user doesn't
        // think space is an ok password character.
        password = password.trim();
        if (password == null || password.equals("")) {
            errors.put("password","");
        }
        if (passwordConfirm == null) {
            errors.put("passwordConfirm","");
        }
        if (password != null && passwordConfirm != null && !password.equals(passwordConfirm)) {
            errors.put("passwordMatch","");
        }
        // If provider requires email, validate
        if (UserManager.getUserProvider().isEmailRequired()) {
            if (StringUtils.isValidEmailAddress(email)) {
                errors.put("email","");
            }
        }
        // If provider requires name, validate
        if (UserManager.getUserProvider().isNameRequired()) {
            if (name == null || name.equals("")) {
                errors.put("name","");
            }
        }

        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                User newUser = webManager.getUserManager().createUser(username, password, name, email);

                if (!AdminManager.getAdminProvider().isReadOnly()) {
                    boolean isCurrentAdmin = AdminManager.getInstance().isUserAdmin(newUser.getUsername(), false);
                    if (isCurrentAdmin && !isAdmin) {
                        AdminManager.getInstance().removeAdminAccount(newUser.getUsername());
                    }
                    else if (!isCurrentAdmin && isAdmin) {
                        AdminManager.getInstance().addAdminAccount(newUser.getUsername());
                    }
                }

                if (!SecurityAuditManager.getSecurityAuditProvider().blockUserEvents()) {
                    // Log the event
                    webManager.logEvent("created new user "+username, "name = "+name+", email = "+email+", admin = "+isAdmin);
                }

                // Successful, so redirect
                if (another) {
                    response.sendRedirect("user-create.jsp?success=true");
                }
                else {
                    response.sendRedirect("user-properties.jsp?success=true&username=" +
                            URLEncoder.encode(newUser.getUsername(), "UTF-8"));
                }
                return;
            }
            catch (UserAlreadyExistsException e) {
                errors.put("usernameAlreadyExists","");
            }
            catch (Exception e) {
                errors.put("general","");
                Log.error(e);
            }
        }
    }
%>

<html>
    <head>
        <title><fmt:message key="user.create.title"/></title>
        <meta name="pageID" content="user-create"/>
        <meta name="helpPage" content="add_users_to_the_system.html"/>
    </head>
    <body>

<% if (UserManager.getUserProvider().isReadOnly()) { %>
<div class="error">
    <fmt:message key="user.read_only"/>
</div>
<% } %>

<p><fmt:message key="user.create.info" /></p>

<%--<c:set var="submit" value="${param.create}"/>--%>
<%--<c:set var="errors" value="${errors}"/>--%>

<%  if (!errors.isEmpty()) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""/></td>
            <td class="jive-icon-label">

            <% if (errors.get("general") != null) { %>
                <fmt:message key="user.create.error_creating_account" />
            <% } else if (errors.get("username") != null) { %>
                <fmt:message key="user.create.invalid_username" />
            <% } else if (errors.get("usernameAlreadyExists") != null) { %>
                <fmt:message key="user.create.user_exist" />
            <% } else if (errors.get("name") != null) { %>
                <fmt:message key="user.create.invalid_name" />
            <% } else if (errors.get("email") != null) { %>
                <fmt:message key="user.create.invalid_email" />
            <% } else if (errors.get("password") != null) { %>
                <fmt:message key="user.create.invalid_password" />
            <% } else if (errors.get("passwordMatch") != null) { %>
                <fmt:message key="user.create.invalid_match_password" />
            <% } else if (errors.get("passwordConfirm") != null) { %>
                <fmt:message key="user.create.invalid_password_confirm" />
            <% } %>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<%  } else if (request.getParameter("success") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="user.create.created_success" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form name="f" action="user-create.jsp" method="get">

	<div class="jive-contentBoxHeader">
		<fmt:message key="user.create.new_user" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
		<tr>
			<td width="1%" nowrap><label for="usernametf"><fmt:message key="user.create.username" />:</label> *</td>
			<td width="99%">
				<input type="text" name="username" size="30" maxlength="75" value="<%= ((username!=null) ? username : "") %>"
				 id="usernametf" autocomplete="off">
			</td>
		</tr>
		<tr>
			<td width="1%" nowrap><label for="nametf"><fmt:message key="user.create.name" />:</label> <%= UserManager.getUserProvider().isNameRequired() ? "*" : "" %></td>
			<td width="99%">
				<input type="text" name="name" size="30" maxlength="75" value="<%= ((name!=null) ? name : "") %>"
				 id="nametf">
			</td>
		</tr>
		<tr>
			<td width="1%" nowrap>
				<label for="emailtf"><fmt:message key="user.create.email" />:</label> <%= UserManager.getUserProvider().isEmailRequired() ? "*" : "" %></td>
			<td width="99%">
				<input type="text" name="email" size="30" maxlength="75" value="<%= ((email!=null) ? email : "") %>"
				 id="emailtf">
			</td>
		</tr>
		<tr>
			<td nowrap>
				<label for="passtf"><fmt:message key="user.create.pwd" />:</label> *
			</td>
			<td width="99%">
				<input type="password" name="password" value="" size="20" maxlength="75"
				 id="passtf">
			</td>
		</tr>
		<tr>
			<td width="1%" nowrap>
				<label for="confpasstf"><fmt:message key="user.create.confirm_pwd" />:</label> *
			</td>
			<td width="99%">
				<input type="password" name="passwordConfirm" value="" size="20" maxlength="75"
				 id="confpasstf">
			</td>
		</tr>
        <% if (!AdminManager.getAdminProvider().isReadOnly()) { %>
        <tr>
            <td class="c1">
                <fmt:message key="user.create.isadmin" />
            </td>
            <td>
                <input type="checkbox" name="isadmin">
                (<fmt:message key="user.create.admin_info"/>)
            </td>
        </tr>
        <% } %>
        <tr>

			<td colspan="2" style="padding-top: 10px;">
				<input type="submit" name="create" value="<fmt:message key="user.create.create" />">
				<input type="submit" name="another" value="<fmt:message key="user.create.create_another" />">
				<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />"></td>
		</tr>
		</tbody>
		</table>

	</div>

	<span class="jive-description">
    * <fmt:message key="user.create.requied" />
    </span>


</form>

<script language="JavaScript" type="text/javascript">
document.f.username.focus();
</script>

<%  // Disable the form if a read-only user provider.
if (UserManager.getUserProvider().isReadOnly()) { %>

<script language="Javascript" type="text/javascript">
  function disable() {
    var limit = document.forms[0].elements.length;
    for (i=0;i<limit;i++) {
      document.forms[0].elements[i].disabled = true;
    }
  }
  disable();
</script>
    <% } %>

    </body>
</html>