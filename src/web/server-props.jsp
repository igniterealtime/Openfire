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
                 org.jivesoftware.messenger.XMPPServerInfo,
                 org.jivesoftware.messenger.ServerPort,
                 org.jivesoftware.admin.AdminPageBean,
                 java.util.*,
                 org.jivesoftware.messenger.XMPPServer"
%>

<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>
<c:set var="admin" value="${admin.manager}" />

<%
    // Get parameters
    String serverName = ParamUtils.getParameter(request,"serverName");
    String groupChatName = ParamUtils.getParameter(request,"groupChatName");
    boolean save = request.getParameter("save") != null;
    boolean cancel = request.getParameter("cancel") != null;

    if (cancel) {
        response.sendRedirect("index.jsp");
        return;
    }

    XMPPServer server = admin.getXMPPServer();
    Map errors = new HashMap();
    if (save) {
        if (serverName == null) {
            errors.put("serverName","");
        }
        if (groupChatName == null) {
            errors.put("groupChatName","");
        }
        if (errors.size() == 0) {
            server.getServerInfo().setName(serverName);
            admin.getMultiUserChatServer().setServiceName(groupChatName);
            response.sendRedirect("index.jsp?success=true");
            return;
        }
    }

    if (errors.size() == 0) {
        serverName = server.getServerInfo().getName();
        groupChatName = admin.getMultiUserChatServer().getServiceName();
    }
%>

<%  // Title of this page and breadcrumbs
    String title = "Edit Server Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Server Properties", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Edit", "server-props.jsp"));
    pageinfo.setPageID("server-props");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Use the form below to edit server properties.
</p>

<form action="server-props.jsp">

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">
            Server Properties
        </th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">
            Server Name:
        </td>
        <td class="c2">
            <input type="text" name="serverName" value="<%= (serverName != null) ? serverName : "" %>"
             size="30" maxlength="40">
        </td>
    </tr>
    <tr>
        <td class="c1">
             Group Chat Service Name:
        </td>
        <td class="c2">
            <input type="text" name="groupChatName" value="<%= (groupChatName != null) ? groupChatName : "" %>"
             size="30" maxlength="40">
        </td>
    </tr>
</tbody>
<tfoot>
    <tr>
        <td colspan="2">
            <input type="submit" name="save" value="Save">
            <input type="submit" name="cancel" value="Cancel">
        </td>
    </tr>
</tfoot>
</table>
</div>

</form>

<jsp:include page="bottom.jsp" flush="true" />
