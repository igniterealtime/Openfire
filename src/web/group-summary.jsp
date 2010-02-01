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

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.openfire.group.*,
                 java.net.URLEncoder"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="group.summary.title"/></title>
        <meta name="pageID" content="group-summary"/>
        <meta name="helpPage" content="about_users_and_groups.html"/>
    </head>
    <body>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("group-summary", 15));

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("group-summary", range);
    }

    int groupCount = webManager.getGroupManager().getGroupCount();
    Collection<Group> groups = webManager.getGroupManager().getGroups(start, range);

    String search = null;
    if (webManager.getGroupManager().isSearchSupported() && request.getParameter("search") != null
            && !request.getParameter("search").trim().equals(""))
    {
        search = request.getParameter("search");
        // Santize variables to prevent vulnerabilities
        search = StringUtils.escapeHTMLTags(search);
        // Use the search terms to get the list of groups and group count.
        groups = webManager.getGroupManager().search(search, start, range);
        // Get the count as a search for *all* groups. That will let us do pagination even
        // though it's a bummer to execute the search twice.
        groupCount = webManager.getGroupManager().search(search).size();
    }

    // paginator vars
    int numPages = (int)Math.ceil((double)groupCount/(double)range);
    int curPage = (start/range) + 1;
%>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="group.summary.delete_group" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<% if (webManager.getGroupManager().isSearchSupported()) { %>

<form action="group-summary.jsp" method="get" name="searchForm">
<table border="0" width="100%" cellpadding="0" cellspacing="0">
    <tr>
        <td valign="bottom">
<fmt:message key="group.summary.total_group" /> <b><%= groupCount %></b>
<%  if (numPages > 1) { %>

    , <fmt:message key="global.showing" /> <%= LocaleUtils.getLocalizedNumber(start+1) %>-<%= LocaleUtils.getLocalizedNumber(start+range > groupCount ? groupCount:start+range) %>

<%  } %>
        </td>
        <td align="right" valign="bottom">
   <fmt:message key="group.summary.search" />: <input type="text" size="30" maxlength="150" name="search" value="<%= ((search!=null) ? search : "") %>">
        </td>
    </tr>
</table>
</form>

<script language="JavaScript" type="text/javascript">
document.searchForm.search.focus();
</script>

<% }
   // Otherwise, searching is not supported.
   else {
%>
    <p>
    <fmt:message key="group.summary.total_group" /> <b><%= groupCount %></b>
    <%  if (numPages > 1) { %>

        , <fmt:message key="global.showing" /> <%= (start+1) %>-<%= (start+range) %>

    <%  } %>
    </p>
<% } %>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />
    [
    <%  for (int i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="group-summary.jsp?start=<%= (i*range) %><%= search!=null? "&search=" + URLEncoder.encode(search, "UTF-8") : ""%>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="group.summary.page_name" /></th>
        <th nowrap><fmt:message key="group.summary.page_member" /></th>
        <th nowrap><fmt:message key="group.summary.page_admin" /></th>
        <%  // Only show edit and delete options if the groups aren't read-only.
            if (!webManager.getGroupManager().isReadOnly()) { %>
        <th nowrap><fmt:message key="group.summary.page_edit" /></th>
        <th nowrap><fmt:message key="global.delete" /></th>
        <% } %>
    </tr>
</thead>
<tbody>

<%  // Print the list of groups
    if (groups.isEmpty()) {
%>
    <tr>
        <td align="center" colspan="6">
            <fmt:message key="group.summary.no_groups" />
        </td>
    </tr>

<%
    }
    int i = start;
    for (Group group : groups) {
        String groupName = URLEncoder.encode(group.getName(), "UTF-8");
        i++;
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
        <td width="1%" valign="top">
            <%= i %>
        </td>
        <td width="60%">
            <a href="group-edit.jsp?group=<%= groupName %>"><%= group.getName() %></a>
            <% if (group.getDescription() != null) { %>
            <br>
                <span class="jive-description">
                <%= group.getDescription() %>
                </span>
             <% } %>
        </td>
        <td width="10%" align="center">
            <%= group.getMembers().size() %>
        </td>
        <td width="10%" align="center">
            <%= group.getAdmins().size() %>
        </td>
        <%  // Only show edit and delete options if the groups aren't read-only.
            if (!webManager.getGroupManager().isReadOnly()) { %>
        <td width="1%" align="center">
            <a href="group-edit.jsp?group=<%= groupName %>"
             title=<fmt:message key="global.click_edit" />
            ><img src="images/edit-16x16.gif" width="16" height="16" border="0" alt=""></a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="group-delete.jsp?group=<%= groupName %>"
             title=<fmt:message key="global.click_delete" />
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
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
    <br>
    <p>
    <fmt:message key="global.pages" />
    [
    <%  for (i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="group-summary.jsp?start=<%= (i*range) %><%= search!=null? "&search=" + URLEncoder.encode(search, "UTF-8") : ""%>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

    </body>
</html>