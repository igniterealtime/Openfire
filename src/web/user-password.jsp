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
                 java.net.URLEncoder"
    errorPage="error.jsp"
%><%@ page import="org.xmpp.packet.JID"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<% admin.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean update = request.getParameter("update") != null;
    String username = ParamUtils.getParameter(request,"username");
    String password = ParamUtils.getParameter(request,"password");
    String passwordConfirm = ParamUtils.getParameter(request,"passwordConfirm");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-properties.jsp?username=" + URLEncoder.encode(username, "UTF-8"));
        return;
    }

    // Load the user object
    User user = admin.getUserManager().getUser(username);

    // Handle a password update:
    boolean errors = false;
    if (update) {
        // Validate the passwords:
        if (password != null && passwordConfirm != null && password.equals(passwordConfirm)) {
            user.setPassword(password);
            // Log the event
            admin.logEvent("set password for user "+username, null);
            // Done, so redirect
            response.sendRedirect("user-password.jsp?success=true&username=" + URLEncoder.encode(username, "UTF-8"));
            return;
        }
        else {
            errors = true;
        }
    }
%>

<html>
    <head>
        <title><fmt:message key="user.password.title"/></title>
        <meta name="subPageID" content="user-password"/>
        <meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, "UTF-8") %>"/>
        <meta name="helpPage" content="change_a_user_password.html"/>
    </head>
    <body>

<% if (UserManager.getUserProvider().isReadOnly()) { %>
<div class="error">
    <fmt:message key="user.read_only"/>
</div>
<% } %>

<%  if (errors) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="user.password.error_set_pwd" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (request.getParameter("success") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="user.password.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
<fmt:message key="user.password.info" />
</p>

<form action="user-password.jsp" name="passform" method="post">
<input type="hidden" name="username" value="<%= username %>">

<fieldset>
    <legend><fmt:message key="user.password.change" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td class="c1">
                <fmt:message key="user.create.username" />:
            </td>
            <td class="c2">
                <%= JID.unescapeNode(user.getUsername()) %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="user.password.new_pwd" />:
            </td>
            <td class="c2">
                <input type="password" name="password" value="" size="20" maxlength="50">
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="user.password.confirm_new_pwd" />:
            </td>
            <td class="c2">
                <input type="password" name="passwordConfirm" value="" size="20" maxlength="50">
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="<fmt:message key="user.password.update_pwd" />" name="update">
<input type="submit" value="<fmt:message key="global.cancel" />" name="cancel">
</form>

<script language="JavaScript" type="text/javascript">
document.passform.password.focus();
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
