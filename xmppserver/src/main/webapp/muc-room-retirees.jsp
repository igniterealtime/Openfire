<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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

<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService"
%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.jivesoftware.util.*" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="admin" uri="admin" %>

<%!
    final int DEFAULT_RANGE = 100;
    final int[] RANGE_PRESETS = {25, 50, 75, 100, 500, 1000, -1};
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="muc.room.retirees.title"/></title>
        <meta name="pageID" content="muc-room-retirees"/>
    </head>
    <body>

<%  // Get parameters
    String mucname = ParamUtils.getParameter(request,"mucname");

    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("retirees-summary", DEFAULT_RANGE));
    boolean delete = ParamUtils.getBooleanParameter(request,"delete");
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            delete = false;
        }
    }

    // Delete retiree if requested
    if (delete) {
        String service = ParamUtils.getParameter(request,"service");
        String name = ParamUtils.getParameter(request,"name");

        if (service != null && name != null) {
            webManager.getMultiUserChatManager().deleteRetiree(service, name);
            response.sendRedirect("muc-room-retirees.jsp?mucname=" + URLEncoder.encode(service, "UTF-8") + "&deletesuccess=true");
            return;
        }
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("retirees-summary", range);
    }

    MultiUserChatService mucService = null;
    if (mucname != null && webManager.getMultiUserChatManager().isServiceRegistered(mucname)) {
        mucService = webManager.getMultiUserChatManager().getMultiUserChatService(mucname);
    }
    else {
        for (MultiUserChatService muc : webManager.getMultiUserChatManager().getMultiUserChatServices()) {
            if (muc.isHidden()) {
                // Private and hidden, skip it.
                continue;
            }
            mucService = muc;
            break;
        }
    }

    if (mucService == null) {
        // No services exist, so redirect to where one can configure the services
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Get the retirees manager
    int retireeCount = webManager.getMultiUserChatManager().getRetireeCount(mucService.getServiceName());

    // paginator vars
    int numPages = (int)Math.ceil((double) retireeCount /(double)range);
    int curPage = (start/range) + 1;

%>

<p>
    <fmt:message key="muc.room.retirees.info" />
    <a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucService.getServiceName(), "UTF-8")%>"><%= StringUtils.escapeHTMLTags(mucService.getServiceDomain()) %></a>
    <fmt:message key="muc.room.retirees.info2" />
</p>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <admin:infoBox type="success">
        <fmt:message key="muc.room.retirees.deleted" />
    </admin:infoBox>

<%  } %>

<p>
<fmt:message key="muc.room.retirees.total" />:
<b><%= LocaleUtils.getLocalizedNumber(retireeCount) %></b>

<%  if (numPages > 1) { %>
    --
    <fmt:message key="global.showing" />
    <%= LocaleUtils.getLocalizedNumber(start+1) %>-<%= LocaleUtils.getLocalizedNumber(Math.min(start + range, retireeCount)) %>,

<%  } %>

<% if (retireeCount > 0) { %>
<fmt:message key="muc.room.retirees.sorted" />
<% } %>


<% if (webManager.getMultiUserChatManager().getMultiUserChatServicesCount() > 1) { %>
-- <fmt:message key="muc.room.summary.service" />:
<select name="mucname" id="mucname" onchange="location.href='muc-room-retirees.jsp?mucname=' + this.options[this.selectedIndex].value;">
    <% for (MultiUserChatService service : webManager.getMultiUserChatManager().getMultiUserChatServices()) {
        if (service.isHidden()) {
            // Private and hidden, skip it.
            continue;
        }
    %>
    <option value="<%= StringUtils.escapeForXML(service.getServiceName()) %>"<%= mucService.getServiceName().equals(service.getServiceName()) ? " selected='selected'" : "" %>><%= StringUtils.escapeHTMLTags(service.getServiceDomain()) %></option>
    <% } %>
</select>
<% } %>

<% if (retireeCount > 0) { %>
-- <fmt:message key="muc.room.retirees.per_page" />:
<select size="1" onchange="location.href='muc-room-retirees.jsp?start=0&range=' + this.options[this.selectedIndex].value;">

    <% for (int aRANGE_PRESETS : RANGE_PRESETS) { %>

    <option value="<%  if (aRANGE_PRESETS > 0) { %><%= aRANGE_PRESETS %><%  }else{ %><%= retireeCount %><%}%>"
            <%= (aRANGE_PRESETS == range ? "selected" : "") %>><%  if (aRANGE_PRESETS > 0) { %><%= aRANGE_PRESETS %><%  }else{ %><%= retireeCount %><%}%>
    </option>

    <% } %>

</select>
<% } %>

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
        <a href="muc-room-retirees.jsp?start=0&range=<%= range %>">1</a> ...

    <%
        }
        int i;
        for (i=s; i<numPages && i<num; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="muc-room-retirees.jsp?start=<%= (i*range) %>&range=<%= range %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>

    <%  if (i < numPages) { %>

        ... <a href="muc-room-retirees.jsp?start=<%= ((numPages-1)*range) %>&range=<%= range %>"><%= numPages %></a>

    <%  } %>

    ]

    </p>

<%  } %>

<div class="jive-table">
<table>
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="user.create.name" /></th>
        <th nowrap><fmt:message key="global.delete" /></th>
    </tr>
</thead>
<tbody>

<%  // Print the list of retirees
    Collection<String> retirees = webManager.getMultiUserChatManager().getRetirees(mucService.getServiceName(), start, range);
    if (retirees.isEmpty()) {
%>
    <tr>
        <td style="text-align: center" colspan="7">
            <fmt:message key="muc.room.retirees.no_retirees" />
        </td>
    </tr>

<%
    }

    int i = start;
    for (String retiree : retirees) {
        i++;
%>
    <tr>
        <td style="width: 1%">
            <%= i %>
        </td>
        <td style="width: 20%">
            <%= StringUtils.escapeHTMLTags(retiree) %> &nbsp;
        </td>
        <td style="width: 1%; text-align: center; border-right:1px #ccc solid;">
            <a href="muc-room-retirees.jsp?delete=true&service=<%= URLEncoder.encode(mucService.getServiceName(), "UTF-8") %>&name=<%= URLEncoder.encode(retiree, "UTF-8") %>&csrf=<%= csrfParam %>"
             title="<fmt:message key="global.click_delete" />"
             ><img src="images/delete-16x16.gif" alt="<fmt:message key="global.click_delete" />"></a>
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
        <a href="muc-room-retirees.jsp?start=0&range=<%= range %>">1</a> ...

    <%
        }
        for (i=s; i<numPages && i<num; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="muc-room-retirees.jsp?start=<%= (i*range) %>&range=<%= range %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>

    <%  if (i < numPages) { %>

        ... <a href="muc-room-retirees.jsp?start=<%= ((numPages-1)*range) %>&range=<%= range %>"><%= numPages %></a>

    <%  } %>

    ]

    </p>

<%  } %>

    </body>
</html>
