<%--
  - Copyright (C) 2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.openfire.user.*,
                 org.jivesoftware.openfire.plugin.RegistrationPlugin,
                 org.jivesoftware.util.*,
                 org.jivesoftware.stringprep.Stringprep,
                 org.jivesoftware.stringprep.StringprepException,
                 org.xmpp.packet.JID"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<html>
<head>
    <title><fmt:message key="registration.sign.up.title" /></title>
    <link rel="stylesheet" type="text/css" href="/style/global.css">
    <style type="text/css">
        .drop-shadow {
             font-weight: bold;
             font-size: 14pt;
             color: white;
             text-shadow: black 0.1em 0.1em 0.2em;
             padding-top: 21px;}
    </style>
    <meta name="decorator" content="none"/>
</head>
    
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<jsp:useBean id="errors" class="java.util.HashMap" />
<%  webManager.init(request, response, session, application, out);
 
    boolean create = request.getParameter("create") != null;
    String username = ParamUtils.getParameter(request,"username");
    String name = ParamUtils.getParameter(request,"name");
    String email = ParamUtils.getParameter(request,"email");
    String password = ParamUtils.getParameter(request,"password");
    String passwordConfirm = ParamUtils.getParameter(request,"passwordConfirm");

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
                webManager.getUserManager().createUser(username, password, name, email);
                
                response.sendRedirect("sign-up.jsp?success=true");
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
    
    RegistrationPlugin plugin = (RegistrationPlugin) webManager.getXMPPServer().getPluginManager().getPlugin("registration");
%>

<body>

<div id="jive-header">
<table cellpadding="0" cellspacing="0" width="100%" border="0">
    <tbody>
        <tr><td class="drop-shadow">&nbsp;<%=plugin.getHeader() %></td></tr>    
    </tbody>
</table>
</div>

<div id="jive-content">

<% if (!plugin.webEnabled()) { %>

<fmt:message key="registration.sign.up.unavailable" />

<% } else { %>

<p><fmt:message key="registration.sign.up.instructions" /></p>

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
                <fmt:message key="registration.sign.up.error_creating_account" />
            <% } else if (errors.get("username") != null) { %>
                <fmt:message key="registration.sign.up.invalid_username" />
            <% } else if (errors.get("usernameAlreadyExists") != null) { %>
                <fmt:message key="registration.sign.up.user_exist" />
            <% } else if (errors.get("name") != null) { %>
                <fmt:message key="registration.sign.up.invalid_name" />
            <% } else if (errors.get("email") != null) { %>
                <fmt:message key="registration.sign.up.invalid_email" />
            <% } else if (errors.get("password") != null) { %>
                <fmt:message key="registration.sign.up.invalid_password" />
            <% } else if (errors.get("passwordMatch") != null) { %>
                <fmt:message key="registration.sign.up.invalid_match_password" />
            <% } else if (errors.get("passwordConfirm") != null) { %>
                <fmt:message key="registration.sign.up.invalid_password_confirm" />
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
        <tr>
            <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label"><fmt:message key="registration.sign.up.success" /></td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form name="f" action="sign-up.jsp" method="get">

<div class="jive-contentBoxHeader"><fmt:message key="registration.sign.up.create_account" /></div>
<div class="jive-contentBox">
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
    <tr>
        <td width="1%" nowrap><label for="usernametf"><fmt:message key="registration.sign.up.username" />:</label> *</td>
        <td width="99%">
            <input type="text" name="username" size="30" maxlength="75" value="<%= ((username!=null) ? username : "") %>"
             id="usernametf" autocomplete="off">
        </td>
    </tr>
    <tr>
        <td width="1%" nowrap>
            <label for="nametf"><fmt:message key="registration.sign.up.name" />:</label>
        </td>
        <td width="99%">
            <input type="text" name="name" size="30" maxlength="75" value="<%= ((name!=null) ? name : "") %>"
             id="nametf">
        </td>
    </tr>
    <tr>
        <td width="1%" nowrap>
            <label for="emailtf"><fmt:message key="registration.sign.up.email" />:</label></td>
        <td width="99%">
            <input type="text" name="email" size="30" maxlength="75" value="<%= ((email!=null) ? email : "") %>"
             id="emailtf">
        </td>
    </tr>
    <tr>
        <td nowrap>
            <label for="passtf"><fmt:message key="registration.sign.up.password" />:</label> *
        </td>
        <td width="99%">
            <input type="password" name="password" value="" size="20" maxlength="75"
             id="passtf">
        </td>
    </tr>
    <tr>
        <td width="1%" nowrap>
            <label for="confpasstf"><fmt:message key="registration.sign.up.confirm_password" />:</label> *
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
    * <fmt:message key="registration.sign.up.required_fields" />
    </span>
    </div>
</div>

<input type="submit" name="create" value="<fmt:message key="registration.sign.up.create_account" />">
</form>

<script language="JavaScript" type="text/javascript">
document.f.username.focus();
</script>

<% } %>

</body>
</html>