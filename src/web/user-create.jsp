<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 org.jivesoftware.messenger.user.UserManager,
                 org.jivesoftware.messenger.user.*,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*,
                 java.io.StringWriter,
                 java.io.StringWriter,
                 java.io.IOException,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 java.io.PrintStream,
                 org.dom4j.xpath.DefaultXPath,
                 org.dom4j.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<jsp:useBean id="errors" class="java.util.HashMap" />
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
        if (password == null) {
            errors.put("password","");
        }
        if (passwordConfirm == null) {
            errors.put("passwordConfirm","");
        }
        if (password != null && passwordConfirm != null && !password.equals(passwordConfirm)) {
            errors.put("passwordMatch","");
        }

        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                User newUser = webManager.getUserManager().createUser(username, password, name, email);

                // Successful, so redirect
                if (another) {
                    response.sendRedirect("user-create.jsp?success=true");
                }
                else {
                    response.sendRedirect("user-properties.jsp?success=true&username=" + newUser.getUsername());
                }
                return;
            }
            catch (UserAlreadyExistsException e) {
                errors.put("usernameAlreadyExists","");
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("general","");
                Log.error(e);
            }
        }
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean"/>
<%   // Title of this page and breadcrumbs
    String title = "Create User";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-create.jsp"));
    pageinfo.setPageID("user-create");
%>
<jsp:include page="top.jsp" flush="true"/>
<jsp:include page="title.jsp" flush="true"/>

<p>Use the form below to create a new user.</p>

<c:set var="submit" value="${param.create}"/>
<c:set var="errors" value="${errors}"/>

<%  if (!errors.isEmpty()) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"/></td>
            <td class="jive-icon-label">

            <% if (errors.get("general") != null) { %>
                Error creating the user account. Please check your error logs.
            <% } else if (errors.get("username") != null) { %>
                Invalid username.
            <% } else if (errors.get("usernameAlreadyExists") != null) { %>
                Username already exists - please choose a different one.
            <% } else if (errors.get("name") != null) { %>
                Invalid name.
            <% } else if (errors.get("email") != null) { %>
                Invalid email.
            <% } else if (errors.get("password") != null) { %>
                Invalid password.
            <% } else if (errors.get("passwordMatch") != null) { %>
                Passwords don't match.
            <% } else if (errors.get("passwordConfirm") != null) { %>
                Invalid password confirmation.
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
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        New user created successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form name="f" action="user-create.jsp" method="post">

<fieldset>
    <legend>Create New User</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
    <tr>
        <td width="1%" nowrap><label for="usernametf">Username:</label> *</td>
        <td width="99%">
            <input type="text" name="username" size="30" maxlength="75" value="<%= ((username!=null) ? username : "") %>"
             id="usernametf" autocomplete="off">
        </td>
    </tr>
    <tr>
        <td width="1%" nowrap>
            <label for="nametf">Name:</label>
        </td>
        <td width="99%">
            <input type="text" name="name" size="30" maxlength="75" value="<%= ((name!=null) ? name : "") %>"
             id="nametf">
        </td>
    </tr>
    <tr>
        <td width="1%" nowrap>
            <label for="emailtf">Email:</label></td>
        <td width="99%">
            <input type="text" name="email" size="30" maxlength="75" value="<%= ((email!=null) ? email : "") %>"
             id="emailtf">
        </td>
    </tr>
    <tr>
        <td nowrap>
            <label for="passtf">Password:</label> *
        </td>
        <td width="99%">
            <input type="password" name="password" value="" size="20" maxlength="75"
             id="passtf">
        </td>
    </tr>
    <tr>
        <td width="1%" nowrap>
            <label for="confpasstf">Confirm Password:</label> *
        </td>
        <td width="99%">
            <input type="password" name="passwordConfirm" value="" size="20" maxlength="75"
             id="confpasstf">
        </td>
    </tr>
    </tbody>
    </table>
    <br>
    <span class="jive-description">
    * Required fields
    </span>
    </div>
</fieldset>

<br><br>

<input type="submit" name="create" value="Create User">
<input type="submit" name="another" value="Create &amp; Create Another">
<input type="submit" name="cancel" value="Cancel">

</form>

<script language="JavaScript" type="text/javascript">
document.f.username.focus();
</script>

<jsp:include page="bottom.jsp" flush="true"/>