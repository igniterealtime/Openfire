<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
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

<%@ page import="org.jivesoftware.openfire.PresenceManager,
                 org.jivesoftware.openfire.admin.AdminManager,
                 org.jivesoftware.openfire.user.User,
                 org.jivesoftware.openfire.user.UserManager,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.LocaleUtils,
                 org.jivesoftware.util.ParamUtils"
%><%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="org.xmpp.packet.Presence" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.Collection" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%!
    final int DEFAULT_RANGE = 100;
    final int[] RANGE_PRESETS = {25, 50, 75, 100, 500, 1000, -1};
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="user.summary.title"/></title>
        <meta name="pageID" content="user-summary"/>
        <meta name="helpPage" content="about_users_and_groups.html"/>
    </head>
    <body>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("user-summary", DEFAULT_RANGE));

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("user-summary", range);
    }

    // Get the user manager
    int userCount = webManager.getUserManager().getUserCount();

    // Get the presence manager
    PresenceManager presenceManager = webManager.getPresenceManager();

    // paginator vars
    int numPages = (int)Math.ceil((double)userCount/(double)range);
    int curPage = (start/range) + 1;
%>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="user.summary.deleted" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
<fmt:message key="user.summary.total_user" />:
<b><%= LocaleUtils.getLocalizedNumber(userCount) %></b> --

<%  if (numPages > 1) { %>

    <fmt:message key="global.showing" />
    <%= LocaleUtils.getLocalizedNumber(start+1) %>-<%= LocaleUtils.getLocalizedNumber(start+range > userCount ? userCount:start+range) %>,

<%  } %>
<fmt:message key="user.summary.sorted" />

-- <fmt:message key="user.summary.users_per_page" />:
<select size="1" onchange="location.href='user-summary.jsp?start=0&range=' + this.options[this.selectedIndex].value;">

    <% for (int aRANGE_PRESETS : RANGE_PRESETS) { %>

    <option value="<%= aRANGE_PRESETS %>"
            <%= (aRANGE_PRESETS == range ? "selected" : "") %>><%  if (aRANGE_PRESETS > 0) { %><%= aRANGE_PRESETS %><%  }else{ %><%= userCount %><%}%>
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
        <a href="user-summary.jsp?start=0&range=<%= range %>">1</a> ...

    <%
        }
        int i;
        for (i=s; i<numPages && i<num; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="user-summary.jsp?start=<%= (i*range) %>&range=<%= range %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>

    <%  if (i < numPages) { %>

        ... <a href="user-summary.jsp?start=<%= ((numPages-1)*range) %>&range=<%= range %>"><%= numPages %></a>

    <%  } %>

    ]

    </p>

<%  } %>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="session.details.online" /></th>
        <th nowrap><fmt:message key="user.create.username" /></th>
        <th nowrap><fmt:message key="user.create.name" /></th>
        <th nowrap><fmt:message key="user.summary.created" /></th>
        <th nowrap><fmt:message key="user.summary.last-logout" /></th>
         <%  // Don't allow editing or deleting if users are read-only.
            if (!UserManager.getUserProvider().isReadOnly()) { %>
        <th nowrap><fmt:message key="user.summary.edit" /></th>
        <th nowrap><fmt:message key="global.delete" /></th>
        <% } %>
    </tr>
</thead>
<tbody>

<%  // Print the list of users
    Collection<User> users = webManager.getUserManager().getUsers(start, range);
    if (users.isEmpty()) {
%>
    <tr>
        <td align="center" colspan="7">
            <fmt:message key="user.summary.not_user" />
        </td>
    </tr>

<%
    }
    int i = start;
    for (User user : users) {
        i++;
        Boolean lockedOut = false;
        Boolean pendingLockOut = false;
        if (webManager.getLockOutManager().getDisabledStatus(user.getUsername()) != null) {
            // User is locked out. Check if its locked out now!
            if (webManager.getLockOutManager().isAccountDisabled(user.getUsername())) {
                lockedOut = true;
            }
            else {
                pendingLockOut = true;
            }
        }
        Boolean isAdmin = AdminManager.getInstance().isUserAdmin(user.getUsername(), false);
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
        <td width="1%">
            <%= i %>
        </td>
        <td width="1%" align="center" valign="middle">
            <%  if (presenceManager.isAvailable(user)) {
                    Presence presence = presenceManager.getPresence(user);
            %>
                <% if (presence.getShow() == null) { %>
                <img src="images/user-green-16x16.gif" width="16" height="16" border="0" title="<fmt:message key="user.properties.available" />" alt="<fmt:message key="user.properties.available" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.chat) { %>
                <img src="images/user-green-16x16.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.chat_available" />" alt="<fmt:message key="session.details.chat_available" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.away) { %>
                <img src="images/user-yellow-16x16.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.away" />" alt="<fmt:message key="session.details.away" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.xa) { %>
                <img src="images/user-yellow-16x16.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.extended" />" alt="<fmt:message key="session.details.extended" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.dnd) { %>
                <img src="images/user-red-16x16.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.not_disturb" />" alt="<fmt:message key="session.details.not_disturb" />">
                <% } %>

            <%  } else { %>

                <img src="images/user-clear-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="user.properties.offline" />">

            <%  } %>
        </td>
        <td width="23%">
            <a href="user-properties.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"<%= lockedOut ? " style='text-decoration: line-through underline;'" : "" %>><%= JID.unescapeNode(user.getUsername()) %></a>
            <% if (isAdmin) { %><img src="/images/star-16x16.gif" height="16" width="16" align="top" alt="<fmt:message key='user.properties.isadmin'/>" title="<fmt:message key='user.properties.isadmin'/>"/><% } %>
            <% if (lockedOut) { %><img src="/images/forbidden-16x16.gif" height="16" width="16" align="top" alt="<fmt:message key='user.properties.locked'/>" title="<fmt:message key='user.properties.locked'/>"/><% } %>
            <% if (pendingLockOut) { %><img src="/images/warning-16x16.gif" height="16" width="16" align="top" alt="<fmt:message key='user.properties.locked_set'/>" title="<fmt:message key='user.properties.locked_set'/>"/><% } %>
        </td>
        <td width="33%">
            <%= user.getName() %> &nbsp;
        </td>
        <td width="15%">
            <%= JiveGlobals.formatDate(user.getCreationDate()) %>
        </td>
        <td width="25%">
            <% long logoutTime = presenceManager.getLastActivity(user);
                if (logoutTime > -1) {
                    out.println(StringUtils.getElapsedTime(logoutTime));
                }
                else {
                    out.println("&nbsp;");
                } %>
        </td>
         <%  // Don't allow editing or deleting if users are read-only.
            if (!UserManager.getUserProvider().isReadOnly()) { %>
        <td width="1%" align="center">
            <a href="user-edit-form.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"
             title="<fmt:message key="global.click_edit" />"
             ><img src="images/edit-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.click_edit" />"></a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="user-delete.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"
             title="<fmt:message key="global.click_delete" />"
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.click_delete" />"></a>
        </td>
        <% } %>
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
        <a href="user-summary.jsp?start=0&range=<%= range %>">1</a> ...

    <%
        }
        for (i=s; i<numPages && i<num; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="user-summary.jsp?start=<%= (i*range) %>&range=<%= range %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>

    <%  if (i < numPages) { %>

        ... <a href="user-summary.jsp?start=<%= ((numPages-1)*range) %>&range=<%= range %>"><%= numPages %></a>

    <%  } %>

    ]

    </p>

<%  } %>

    </body>
</html>
