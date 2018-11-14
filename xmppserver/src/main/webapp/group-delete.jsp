<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.group.Group,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.security.SecurityAuditManager" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String groupName = ParamUtils.getParameter(request,"group");
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            delete = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

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
        if (!SecurityAuditManager.getSecurityAuditProvider().blockGroupEvents()) {
            // Log the event
            webManager.logEvent("deleted group "+group, null);
        }
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
    <input type="hidden" name="csrf" value="${csrf}">
<input type="hidden" name="group" value="<%= StringUtils.escapeForXML(groupName) %>">
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
