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
                 org.xmpp.packet.JID,
                 java.net.URLEncoder,
                 org.jivesoftware.openfire.group.GroupManager"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String username = ParamUtils.getParameter(request,"username");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-properties.jsp?username=" + URLEncoder.encode(username, "UTF-8"));
        return;
    }

    // Load the user object
    User user = webManager.getUserManager().getUser(username);

    // Handle a user delete:
    if (delete) {
        // Delete the user
        webManager.getUserManager().deleteUser(user);
        // Delete the user's roster
        JID userAddress = new JID(username, webManager.getServerInfo().getName(), null);
        // Delete the roster of the user
        webManager.getRosterManager().deleteRoster(userAddress);
        // Delete the user from all the Groups
        GroupManager.getInstance().deleteUser(user);
        // Deleted your own user account, force login
        if (username.equals(webManager.getAuthToken().getUsername())){
            session.removeAttribute("jive.admin.authToken");
            response.sendRedirect("login.jsp");
        }
        else {
            // Done, so redirect
            response.sendRedirect("user-summary.jsp?deletesuccess=true");
        }
        return;
    }
%>

<html>
    <head>
        <title><fmt:message key="user.delete.title"/></title>
        <meta name="subPageID" content="user-delete"/>
        <meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, "UTF-8") %>"/>
        <meta name="helpPage" content="remove_a_user_from_the_system.html"/>
    </head>
    <body>

<% if (UserManager.getUserProvider().isReadOnly()) { %>
<div class="error">
    <fmt:message key="user.read_only"/>
</div>
<% } %>

<p>
<fmt:message key="user.delete.info" />
<b><a href="user-properties.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"><%= JID.unescapeNode(user.getUsername()) %></a></b>
<fmt:message key="user.delete.info1" />
</p>

<c:if test="${admin.user.username == param.username}">
    <p class="jive-warning-text">
    <fmt:message key="user.delete.warning" /> <b><fmt:message key="user.delete.warning2" /></b> <fmt:message key="user.delete.warning3" />
    </p>
</c:if>

<form action="user-delete.jsp">
<input type="hidden" name="username" value="<%= username %>">
<input type="submit" name="delete" value="<fmt:message key="user.delete.delete" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

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
