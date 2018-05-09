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

<%@ page import="org.jivesoftware.openfire.PresenceManager,
                 org.jivesoftware.openfire.admin.AdminManager,
                 org.jivesoftware.openfire.user.User,
                 org.jivesoftware.openfire.user.UserManager,
                 org.jivesoftware.openfire.group.Group,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.LocaleUtils"
%><%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="org.xmpp.packet.Presence" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.jivesoftware.util.ListPager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.function.Predicate" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.group.GroupManager" %>
<%@ page import="java.util.function.Function" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="user.summary.title"/></title>
        <meta name="pageID" content="user-summary"/>
        <meta name="helpPage" content="about_users_and_groups.html"/>
    </head>
    <body>

<%
    // Get the presence manager
    PresenceManager presenceManager = webManager.getPresenceManager();

    // By default, display all users
    Predicate<User> filter = new Predicate<User>() {
        @Override
        public boolean test(final User user) {
            return true;
        }
    };
    final String searchUsername = ParamUtils.getStringParameter(request, "searchUsername", "");
    if (!searchUsername.trim().isEmpty()) {
        final String searchCriteria = searchUsername.trim().toLowerCase();
        filter = filter.and(new Predicate<User>() {
            @Override
            public boolean test(final User user) {
                return user.getUsername().toLowerCase().contains(searchCriteria);
            }
        });
    }
    final String searchName = ParamUtils.getStringParameter(request, "searchName", "");
    if (!searchName.trim().isEmpty()) {
        final String searchCriteria = searchName.trim().toLowerCase();
        filter = filter.and(new Predicate<User>() {
            @Override
            public boolean test(final User user) {
                return user.getName().toLowerCase().contains(searchCriteria);
            }
        });
    }
    final String searchGroup = ParamUtils.getStringParameter(request, "searchGroup", "");
    final GroupManager groupManager = webManager.getGroupManager();
    if (!searchGroup.isEmpty()) {
        final Predicate<String> searchPredicate = new Predicate<String>() {
            @Override
            public boolean test(final String groupName) {
                return searchGroup.equals(groupName);
            }
        };
        filter = filter.and(new Predicate<User>() {
            @Override
            public boolean test(final User user) {
                return groupManager.getGroups(user).stream()
                    .map(new Function<Group, String>() {
                        @Override
                        public String apply(final Group group) {
                            return group.getName();
                        }
                    })
                    .anyMatch(searchPredicate);
            }
        });
    }

    final ListPager<User> listPager = new ListPager<>(request, response, new ArrayList<>(webManager.getUserManager().getUsers()), filter,
        "searchUsername", "searchName", "searchGroup");
    pageContext.setAttribute("listPager", listPager);
    pageContext.setAttribute("searchUsername", searchUsername);
    pageContext.setAttribute("searchName", searchName);
    pageContext.setAttribute("searchGroup", searchGroup);

    pageContext.setAttribute("groups", groupManager.getGroups());
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
<fmt:message key="user.summary.total_user" />: <b><%= LocaleUtils.getLocalizedNumber(listPager.getTotalItemCount()) %></b>
<c:if test="${listPager.filtered}">
    <fmt:message key="user.summary.filtered_user_count" />: <c:out value="${listPager.filteredItemCount}"/>
</c:if>

<c:if test="${listPager.totalPages > 1}">
    <fmt:message key="global.showing" />
    <%= LocaleUtils.getLocalizedNumber(listPager.getFirstItemNumberOnPage()) %>-<%= LocaleUtils.getLocalizedNumber(listPager.getLastItemNumberOnPage()) %>,
</c:if>
<fmt:message key="user.summary.sorted" />

-- <fmt:message key="user.summary.users_per_page" />: ${listPager.pageSizeSelection}

</p>

<c:if test="${listPager.totalPages > 1}">
    <p><fmt:message key="global.pages" />: ${listPager.pageLinks}</p>
</c:if>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="session.details.online" /></th>
        <th nowrap><fmt:message key="user.create.username" /></th>
        <th nowrap><fmt:message key="user.create.name" /></th>
        <th nowrap><fmt:message key="user.roster.groups" /></th>
        <th nowrap><fmt:message key="user.summary.created" /></th>
        <th nowrap><fmt:message key="user.summary.last-logout" /></th>
         <%  // Don't allow editing or deleting if users are read-only.
            if (!UserManager.getUserProvider().isReadOnly()) { %>
        <th nowrap><fmt:message key="user.summary.edit" /></th>
        <th nowrap><fmt:message key="global.delete" /></th>
        <% } %>
    </tr>
    <tr>
        <td></td>
        <td></td>
        <td nowrap>
            <input type="search"
                   id="searchUsername"
                   size="20"
                   value="<c:out value="${searchUsername}"/>"/>
            <img src="images/search-16x16.png"
                 width="16" height="16"
                 alt="search" title="search"
                 style="vertical-align: middle;"
                 onclick="submitForm();"
            >
        </td>
        <td nowrap>
            <input type="search"
                   id="searchName"
                   size="20"
                   value="<c:out value="${searchName}"/>"/>
            <img src="images/search-16x16.png"
                 width="16" height="16"
                 alt="search" title="search"
                 style="vertical-align: middle;"
                 onclick="submitForm();"
            >
        </td>
        <td nowrap>
            <select id="searchGroup" onchange="submitForm();">
                <option <c:if test='${searchGroup eq ""}'>selected</c:if> value=""></option>
                <%--@elvariable id="group" type="org.jivesoftware.openfire.group.Group"--%>
                <c:forEach var="group" items="${groups}">
                    <option <c:if test='${searchGroup eq group.name}'>selected</c:if> value="<c:out value="${group.name}"/>"><c:out value="${group.name}"/></option>
                </c:forEach>
            </select>
        </td>
        <td></td>
        <td></td>
        <c:if test="${!UserManager.userProvider.readOnly}">
            <td></td>
            <td></td>
        </c:if>
    </tr>
</thead>
<tbody>

<c:if test="${listPager.totalItemCount == 0}">
    <tr>
        <td align="center" colspan="7">
            <fmt:message key="user.summary.not_user" />
        </td>
    </tr>
</c:if>

<%
    int i = listPager.getFirstItemNumberOnPage();
    for (User user : listPager.getItemsOnCurrentPage()) {
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
            <a href="user-properties.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"<%= lockedOut ? " style='text-decoration: line-through underline;'" : "" %>><%= StringUtils.escapeHTMLTags(JID.unescapeNode(user.getUsername())) %></a>
            <% if (isAdmin) { %><img src="images/star-16x16.gif" height="16" width="16" align="top" alt="<fmt:message key='user.properties.isadmin'/>" title="<fmt:message key='user.properties.isadmin'/>"/><% } %>
            <% if (lockedOut) { %><img src="images/forbidden-16x16.gif" height="16" width="16" align="top" alt="<fmt:message key='user.properties.locked'/>" title="<fmt:message key='user.properties.locked'/>"/><% } %>
            <% if (pendingLockOut) { %><img src="images/warning-16x16.gif" height="16" width="16" align="top" alt="<fmt:message key='user.properties.locked_set'/>" title="<fmt:message key='user.properties.locked_set'/>"/><% } %>
        </td>
        <td width="23%">
            <%= StringUtils.escapeHTMLTags(user.getName()) %> &nbsp;
        </td>
        <td width="15%">
            <%
                Collection<Group> groups = groupManager.getGroups(user);
                if (groups.isEmpty()) {
            %>
                <i>None</i>
            <%
                }
                else {
                    int count = 0;
                    for (Group group : groups) {
                        if (count != 0) {
                            out.print(", ");
                        }
                        %><a href="group-edit.jsp?group=<%= URLEncoder.encode(group.getName(), "UTF-8") %>"><%= StringUtils.escapeHTMLTags(JID.unescapeNode(group.getName())) %></a><%
                        count++;
                    }
                }
            %>
        </td>
        <td width="12%">
            <%= user.getCreationDate() != null ? JiveGlobals.formatDate(user.getCreationDate()) : "&nbsp;" %>
        </td>
        <td width="23%">
            <% if (presenceManager.isAvailable(user)) { %>
            <fmt:message key="session.details.online" />
            <% } else {
                 long logoutTime = presenceManager.getLastActivity(user);
                 if (logoutTime > -1) {
                    out.println(StringUtils.getElapsedTime(logoutTime));
                 } else { %>
            <fmt:message key="user.properties.never_logged_in" />
            <%   }
               }%>
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
        i++;
    }
%>
</tbody>
</table>
</div>

<c:if test="${listPager.totalPages > 1}">
    <p><fmt:message key="global.pages" />: ${listPager.pageLinks}</p>
</c:if>

${listPager.jumpToPageForm}

<script type="text/javascript">
    ${listPager.pageFunctions}
</script>
    </body>
</html>
