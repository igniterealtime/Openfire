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
                 org.jivesoftware.messenger.user.*,
                 java.util.*,
                 org.jivesoftware.messenger.user.UserManager,
                 java.text.DateFormat,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.PresenceManager,
                 org.xmpp.packet.Presence,
                 java.net.URLEncoder,
                 org.jivesoftware.messenger.JiveGlobals"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%!
    final int DEFAULT_RANGE = 15;
    final int[] RANGE_PRESETS = {15, 25, 50, 75, 100};
    final String USER_RANGE_PROP = "admin.userlist.range";
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("user.summary.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-summary.jsp"));
    pageinfo.setPageID("user-summary");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",DEFAULT_RANGE);
    boolean setRange = request.getParameter("range") != null;

    if (setRange) {
        User user = webManager.getUser();
        if (user != null) {
            if (range == DEFAULT_RANGE) {
                user.deleteProperty(USER_RANGE_PROP);
            }
            else {
                user.setProperty(USER_RANGE_PROP, String.valueOf(range));
            }
        }
    }

    // Adjust the range based on the user's settings
    if (webManager.getUser() != null) {
        User user = webManager.getUser();
        try {
            range = Integer.parseInt(user.getProperty(USER_RANGE_PROP));
        }
        catch (Exception e) {
            user.deleteProperty(USER_RANGE_PROP);
        }
    }

    // Get the user manager
    int userCount = webManager.getUserManager().getUserCount();

    // Get the presence manager
    PresenceManager presenceManager = webManager.getPresenceManager();

    // paginator vars
    int numPages = (int)Math.ceil((double)userCount/(double)range);
    int curPage = (start/range) + 1;

    // Formatter for dates
    DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM);
%>

<p>
<fmt:message key="user.summary.info" />
</p>

<style type="text/css">
.jive-current {
    font-weight : bold;
    text-decoration : none;
}
</style>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="user.summary.deleted" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
<fmt:message key="user.summary.total_user" />:
<%= LocaleUtils.getLocalizedNumber(webManager.getUserManager().getUserCount()) %> --

<%  if (numPages > 1) { %>

    <fmt:message key="user.summary.showing" />
    <%= LocaleUtils.getLocalizedNumber(start+1) %>-<%= LocaleUtils.getLocalizedNumber(start+range) %>,

<%  } %>
<fmt:message key="user.summary.sorted" />

- <fmt:message key="user.summary.users_per_page" />:
<select size="1" onchange="location.href='user-summary.jsp?start=0&range=' + this.options[this.selectedIndex].value;">

    <%  for (int i=0; i<RANGE_PRESETS.length; i++) { %>

        <option value="<%= RANGE_PRESETS[i] %>"
         <%= (RANGE_PRESETS[i] == range ? "selected" : "") %>><%= RANGE_PRESETS[i] %></option>

    <%  } %>

</select>
</p>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="user.summary.pages" />:
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
        int i = 0;
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
        <th nowrap><fmt:message key="user.summary.edit" /></th>
        <th nowrap><fmt:message key="user.summary.delete" /></th>
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
                <img src="images/user-green-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="user.properties.available" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.chat) { %>
                <img src="images/user-green-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="session.details.chat_available" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.away) { %>
                <img src="images/user-yellow-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="session.details.away" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.xa) { %>
                <img src="images/user-yellow-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="session.details.extended" />">
                <% } %>
                <% if (presence.getShow() == Presence.Show.dnd) { %>
                <img src="images/user-red-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="session.details.not_disturb" />">
                <% } %>

            <%  } else { %>

                <img src="images/user-clear-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="user.properties.offline" />">

            <%  } %>
        </td>
        <td width="30%">
            <a href="user-properties.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"><%= user.getUsername() %></a>
        </td>
        <td width="40%">
            <%= user.getName() %> &nbsp;
        </td>
        <td width="26%">
            <%= dateFormatter.format(user.getCreationDate()) %>
        </td>
        <td width="1%" align="center">
            <a href="user-edit-form.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"
             title="<fmt:message key="user.summary.click_edit" />"
             ><img src="images/edit-16x16.gif" width="17" height="17" border="0"></a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="user-delete.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"
             title="<fmt:message key="user.summary.click_delete" />"
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
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
    <fmt:message key="user.summary.pages" />:
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
        i = 0;
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

<jsp:include page="bottom.jsp" flush="true" />
