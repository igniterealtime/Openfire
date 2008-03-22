<%--
  -	$RCSfile$
  -	$Revision: 9909 $
  -	$Date: 2008-02-13 22:32:17 -0500 (Wed, 13 Feb 2008) $
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.LocaleUtils,
                 org.jivesoftware.util.ParamUtils"
%><%@ page import="org.xmpp.packet.JID"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>
<%@ page import="java.util.List" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%!
    final int DEFAULT_RANGE = 15;
    final int[] RANGE_PRESETS = {15, 25, 50, 75, 100};
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="muc.service.summary.title"/></title>
        <meta name="pageID" content="muc-service-summary"/>
    </head>
    <body>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("muc-service-summary", DEFAULT_RANGE));

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("muc-service-summary", range);
    }

    // Get the number of registered services
    int servicesCount = webManager.getMultiUserChatManager().getServicesCount(false);

    // paginator vars
    int numPages = (int)Math.ceil((double)servicesCount/(double)range);
    int curPage = (start/range) + 1;
%>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="muc.service.summary.deleted" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>
<% if (webManager.getMultiUserChatManager().getMultiUserChatServicesCount() < 1) { %>

    <div class="jive-info">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/info-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="muc.service.summary.no_services_warning" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<% } %>

<p>
<fmt:message key="muc.service.summary.total_services" />:
<b><%= LocaleUtils.getLocalizedNumber(servicesCount) %></b> --

<%  if (numPages > 1) { %>

    <fmt:message key="global.showing" />
    <%= LocaleUtils.getLocalizedNumber(start+1) %>-<%= LocaleUtils.getLocalizedNumber(start+range > servicesCount ? servicesCount:start+range) %>,

<%  } %>
<fmt:message key="muc.service.summary.sorted" />

-- <fmt:message key="muc.service.summary.services_per_page" />:
<select size="1" onchange="location.href='muc-service-summary.jsp?start=0&range=' + this.options[this.selectedIndex].value;">

    <% for (int aRANGE_PRESETS : RANGE_PRESETS) { %>

    <option value="<%= aRANGE_PRESETS %>"
            <%= (aRANGE_PRESETS == range ? "selected" : "") %>><%= aRANGE_PRESETS %>
    </option>

    <% } %>

</select>
</p>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />:
    [
    <%  int num = 15 + curPage;
        int s = curPage-1;
        if (s > 5) {
            s -= 5;
        }
        if (s < 5) {
            s = 0;
        }
        if (s > 2) {
    %>
        <a href="muc-service-summary.jsp?start=0&range=<%= range %>">1</a> ...

    <%
        }
        int i;
        for (i=s; i<numPages && i<num; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="muc-service-summary.jsp?start=<%= (i*range) %>&range=<%= range %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>

    <%  if (i < numPages) { %>

        ... <a href="muc-service-summary.jsp?start=<%= ((numPages-1)*range) %>&range=<%= range %>"><%= numPages %></a>

    <%  } %>

    ]

    </p>

<%  } %>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="muc.service.summary.subdomain" /></th>
        <th nowrap><fmt:message key="muc.service.summary.description" /></th>
        <th nowrap><fmt:message key="muc.service.summary.numrooms" /></th>
        <th nowrap><fmt:message key="muc.service.summary.numusers" /></th>
        <th nowrap><fmt:message key="global.edit" /></th>
        <th nowrap><fmt:message key="global.delete" /></th>
    </tr>
</thead>
<tbody>

<%  // Print the list of users
    List<MultiUserChatService> services = webManager.getMultiUserChatManager().getMultiUserChatServices();
    if (services.isEmpty()) {
%>
    <tr>
        <td align="center" colspan="7">
            <fmt:message key="muc.service.summary.no_services" />
        </td>
    </tr>

<%
    }
    int i = start;
    for (MultiUserChatService service : services) {
        if (service.isHidden()) {
            // Private and hidden, skip it.
            continue;
        }
        i++;
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
        <td width="1%">
            <%= i %>
        </td>
        <td width="23%">
            <a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(service.getServiceName(), "UTF-8") %>"><%= JID.unescapeNode(service.getServiceName()) %></a>
        </td>
        <td width="33%">
            <%= service.getDescription() %> &nbsp;
        </td>
        <td width="5%">
            <a href="muc-room-summary.jsp?mucname==<%= URLEncoder.encode(service.getServiceName(), "UTF-8") %>"><%= service.getNumberChatRooms() %></a>
        </td>
        <td width="5%">
            <%= service.getNumberConnectedUsers(false) %>
        </td>
        <td width="1%" align="center">
            <a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(service.getServiceName(), "UTF-8") %>"
             title="<fmt:message key="global.click_edit" />"
             ><img src="images/edit-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.click_edit" />"></a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="muc-service-delete.jsp?mucname=<%= URLEncoder.encode(service.getServiceName(), "UTF-8") %>"
             title="<fmt:message key="global.click_delete" />"
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.click_delete" />"></a>
        </td>
    </tr>

<%
    }
%>
</tbody>
</table>
</div>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />:
    [
    <%  int num = 15 + curPage;
        int s = curPage-1;
        if (s > 5) {
            s -= 5;
        }
        if (s < 5) {
            s = 0;
        }
        if (s > 2) {
    %>
        <a href="muc-service-summary.jsp?start=0&range=<%= range %>">1</a> ...

    <%
        }
        for (i=s; i<numPages && i<num; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="muc-service-summary.jsp?start=<%= (i*range) %>&range=<%= range %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>

    <%  if (i < numPages) { %>

        ... <a href="muc-service-summary.jsp?start=<%= ((numPages-1)*range) %>&range=<%= range %>"><%= numPages %></a>

    <%  } %>

    ]

    </p>

<%  } %>

    </body>
</html>
