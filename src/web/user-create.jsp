<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
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
                 org.jivesoftware.messenger.user.spi.*,
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
    boolean create = request.getParameter("create") != null;
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
                User newUser = webManager.getUserManager().createUser(username, password, email);
                if (name != null) {
                    newUser.getInfo().setName(name);
                }
                newUser.saveInfo();
                
                // Successful, so redirect
                response.sendRedirect("user-properties.jsp?success=true&username=" + newUser.getUsername());
                return;
            }
            catch (UserAlreadyExistsException e) {
                e.printStackTrace();
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
<c:set var="submit" value="${param.create}"/>
<c:set var="errors" value="${errors}"/>
<%   if (errors.get("general") != null) { %>
<div class="jive-success">
  <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
      <tr>
        <td class="jive-icon">
          <img src="images/success-16x16.gif" width="16" height="16" border="0"/>
        </td>
        <td class="jive-icon-label">Error creating the user account. Please check your error logs.</td>
      </tr>
    </tbody>
  </table>
</div>
<br/>
<%   } %>
<form name="f" action="user-create.jsp" method="post">
<fieldset><legend>Create New User</legend>
<table  cellpadding="3" cellspacing="1" border="0">

   <tr><td class="c1">* Required fields</td></tr>
    <tr>
      <td width="1%" class="c1">Username: *</td>
      <td class="c2">
        <input type="text" name="username" size="30" maxlength="75" value="<%= ((username!=null) ? username : "") %>"/>
        <%   if (errors.get("username") != null) { %>
        <span class="jive-error-text">Invalid username. </span>
        <%   } else if (errors.get("usernameAlreadyExists") != null) { %>
        <span class="jive-error-text">Username already exists - please choose a different one. </span>
        <%   } %>
      </td>
    </tr>
    <tr>
      <td width="1%" class="c1">Name:</td>
      <td class="c2">
        <input type="text" name="name" size="30" maxlength="75" value="<%= ((name!=null) ? name : "") %>"/>
        <%   if (errors.get("name") != null) { %>
        <span class="jive-error-text">Invalid name. </span>
        <%   } %>
      </td>
    </tr>
    <tr>
      <td width="1%" class="c1">Email:</td>
      <td class="c2">
        <input type="text" name="email" size="30" maxlength="75" value="<%= ((email!=null) ? email : "") %>"/>
        <%   if (errors.get("email") != null) { %>
        <span class="jive-error-text">Invalid email. </span>
        <%   } %>
      </td>
    </tr>
    <tr>
      <td class="c1">Password: *</td>
      <td class="c2">
        <input type="password" name="password" value="" size="20" maxlength="75"/>
        <%   if (errors.get("password") != null) { %>
        <span class="jive-error-text">Invalid password. </span>
        <%   } else if (errors.get("passwordMatch") != null) { %>
        <span class="jive-error-text">Passwords don't match. </span>
        <%   } %>
      </td>
    </tr>
    <tr>
      <td width="1%" class="c1">Confirm Password: *</td>
      <td class="c2">
        <input type="password" name="passwordConfirm" value="" size="20" maxlength="75"/>
        <%   if (errors.get("passwordConfirm") != null) { %>
        <span class="jive-error-text">Invalid password confirmation. </span>
        <%   } %>
      </td>
    </tr>
    <tr>
      <td colspan="2" nowrap><input type="submit" name="create" value="Create User"/><input type="submit" name="cancel" value="Cancel"/>
      </tr></td></fieldset>
  </form>
  <script language="JavaScript" type="text/javascript">
     document.f.username.focus();
     function checkFields() {
     }
</script>

  <jsp:include page="bottom.jsp" flush="true"/>
</table>


