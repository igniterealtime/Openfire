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
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.messenger.user.*,
                 java.util.*,
                 java.text.*,
                 org.jivesoftware.admin.AdminPageBean"
%>

<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>

<%@ include file="global.jsp" %>

<%-- Define page bean for header and sidebar --%>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out); %>

<%  // Title of this page and breadcrumbs
    String title = "Jive Messenger Admin";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.setPageID("server-status");
%>

<jsp:include page="top.jsp" flush="true" />

<p>Welcome to the Jive Messenger Admin tool.</p>

<table class="box" cellpadding="3" cellspacing="1" border="0" width="100%">
<tr class="tableHeaderBlue"><td colspan="2" align="center"><fmt:message key="title" bundle="${lang}" /> Information</td></tr>
<tr><td colspan="2">
<tr>  
    <td class="jive-label">   
        Version:
    </td>
    <td>
        <fmt:message key="title" bundle="${lang}" /> <%= admin.getXMPPServer().getServerInfo().getVersion().getVersionString() %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        JVM Version and Vendor:
    </td>
    <td>
        <%= System.getProperty("java.version") %> <%= System.getProperty("java.vendor") %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        Appserver:
    </td>
    <td>
        <%= application.getServerInfo() %>
    </td>
</tr>
</table>
</div>

<jsp:include page="top.jsp" flush="true" />