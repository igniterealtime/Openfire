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
                 org.dom4j.*,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
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

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean"/>
<%   // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("user.create.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-create.jsp"));
    pageinfo.setPageID("user-create");
%>
<jsp:include page="top.jsp" flush="true"/>
<jsp:include page="title.jsp" flush="true"/>

<p><fmt:message key="user.create.info" /></p>

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
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="user.create.created_success" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form name="f" action="user-create.jsp" method="get">

<fieldset>
    <legend><fmt:message key="user.create.new_user" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
    <tr>
        <td width="1%" nowrap><label for="usernametf"><fmt:message key="user.create.username" />:</label> *</td>
        <td width="99%">
            <input type="text" name="username" size="30" maxlength="75" value="<%= ((username!=null) ? username : "") %>"
             id="usernametf" autocomplete="off">
        </td>
    </tr>
    <tr>
        <td width="1%" nowrap>
            <label for="nametf"><fmt:message key="user.create.name" />:</label>
        </td>
        <td width="99%">
            <input type="text" name="name" size="30" maxlength="75" value="<%= ((name!=null) ? name : "") %>"
             id="nametf">
        </td>
    </tr>
    <tr>
        <td width="1%" nowrap>
            <label for="emailtf"><fmt:message key="user.create.email" />:</label></td>
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
    </tbody>
    </table>
    <br>
    <span class="jive-description">
    * <fmt:message key="user.create.requied" />
    </span>
    </div>
</fieldset>

<br><br>

<input type="submit" name="create" value="<fmt:message key="user.create.create" />">
<input type="submit" name="another" value="<fmt:message key="user.create.create_another" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">

</form>

<script language="JavaScript" type="text/javascript">
document.f.username.focus();
</script>

<jsp:include page="bottom.jsp" flush="true"/>