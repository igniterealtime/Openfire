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
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.group.Group,
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
        // Done, so redirect
        response.sendRedirect("group-summary.jsp?deletesuccess=true");
        return;
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("group.delete.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "group-delete.jsp?group="+URLEncoder.encode(groupName, "UTF-8")));
    pageinfo.setSubPageID("group-delete");
    pageinfo.setExtraParams("group="+URLEncoder.encode(groupName, "UTF-8"));
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

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

<jsp:include page="bottom.jsp" flush="true" />
