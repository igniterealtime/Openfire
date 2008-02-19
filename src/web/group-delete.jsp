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
                 org.jivesoftware.openfire.group.Group,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String groupName = ParamUtils.getParameter(request,"group");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("group-edit.jsp?group=" + URLEncoder.encode(groupName, "UTF-8"));
        return;
    }

    // Load the group object
    Group group = webManager.getGroupManager().getGroup(groupName);

    // Handle a group delete:
    if (delete) {
        // Delete the group
        webManager.getGroupManager().deleteGroup(group);
        // Log the event
        webManager.logEvent("deleted group "+group, null);
        // Done, so redirect
        response.sendRedirect("group-summary.jsp?deletesuccess=true");
        return;
    }
%>

<html>
    <head>
        <title><fmt:message key="group.delete.title"/></title>
        <meta name="subPageID" content="group-delete"/>
        <meta name="extraParams" content="<%= "group="+URLEncoder.encode(groupName, "UTF-8") %>"/>
        <meta name="helpPage" content="delete_a_group.html"/>
    </head>
    <body>

<% if (webManager.getGroupManager().isReadOnly()) { %>
<div class="error">
    <fmt:message key="group.read_only"/>
</div>
<% } %>

<p>
<fmt:message key="group.delete.hint_info" />
<b><a href="group-edit.jsp?group=<%= URLEncoder.encode(group.getName(), "UTF-8")%>"><%= group.getName() %></a></b>
<fmt:message key="group.delete.hint_info1" />
</p>

<form action="group-delete.jsp">
<input type="hidden" name="group" value="<%= groupName %>">
<input type="submit" name="delete" value="<fmt:message key="group.delete.delete" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

    <%  // Disable the form if a read-only user provider.
    if (webManager.getGroupManager().isReadOnly()) { %>

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