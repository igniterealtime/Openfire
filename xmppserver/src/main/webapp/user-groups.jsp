<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

<%@ page
    import="org.jivesoftware.openfire.PresenceManager,
                 org.jivesoftware.openfire.group.Group,
                 org.jivesoftware.openfire.group.GroupManager,
                 org.jivesoftware.openfire.group.GroupNotFoundException,
                 org.jivesoftware.openfire.security.SecurityAuditManager,
                 org.jivesoftware.openfire.user.User,
                 org.jivesoftware.openfire.user.UserManager,
                 org.jivesoftware.openfire.user.UserNotFoundException"%>
<%@ page import="gnu.inet.encoding.Stringprep"%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="org.jivesoftware.util.Log"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="org.jivesoftware.util.CookieUtils"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.xmpp.packet.Presence"%>
<%@ page import="java.io.UnsupportedEncodingException"%>
<%@ page import="java.net.URLDecoder"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.util.*"%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<!-- Define Administration Bean -->
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%
    webManager.init(pageContext);
%>

<%
    // Get parameters
    String add = StringUtils.escapeHTMLTags(ParamUtils.getParameter(request, "add"));
    String delete = StringUtils.escapeHTMLTags(ParamUtils.getParameter(request, "delete"));
    boolean success = ParamUtils.getBooleanParameter(request,"updatesuccess");
    String username = StringUtils.escapeHTMLTags(ParamUtils.getParameter(request, "username"));
    JID jid = webManager.getXMPPServer().createJID(username, null);

        Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
        String csrfParam = ParamUtils.getParameter(request, "csrf");

        if (add != null || delete != null) {
            if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
                add = null;
                delete = null;
            }
        }
        csrfParam = StringUtils.randomString(15);
        CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
        pageContext.setAttribute("csrf", csrfParam);


    if(add != null) {
        try {
            Group group = webManager.getGroupManager().getGroup(add);
            group.getMembers().add(jid);
            response.sendRedirect("user-groups.jsp?username=" + URLEncoder.encode(username, "UTF-8") + "&updatesuccess=true");
        } catch (GroupNotFoundException exp) {
            return;
        }
    }

    if(delete != null) {
        try {
            Group group = webManager.getGroupManager().getGroup(delete);
            group.getMembers().remove(jid);
            group.getAdmins().remove(jid);
            response.sendRedirect("user-groups.jsp?username=" + URLEncoder.encode(username, "UTF-8") + "&updatesuccess=true");
        } catch (GroupNotFoundException exp) {
            return;
        }
    }

    // Load the user object
    User user = null;
    try {
        user = webManager.getUserManager().getUser(username);
    }
    catch (UserNotFoundException unfe) {
    }
    
    Collection<Group> userGroups = webManager.getGroupManager().getGroups(user);
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",15);
    
    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("group-summary", range);
    }

    ArrayList<Group> groups = new ArrayList<Group>(webManager.getGroupManager().getGroups());
    // Remove already joined groups 
    groups.removeAll(userGroups);
    
    int groupCount = groups.size();
    int groupIndex = start + range;

    String search = null;
    if (webManager.getGroupManager().isSearchSupported() && request.getParameter("search") != null
            && !request.getParameter("search").trim().equals("")) {
        search = request.getParameter("search");
        search = StringUtils.escapeHTMLTags(search);
        // Use the search terms to get the list of groups.
        groups = new ArrayList<Group>(webManager.getGroupManager().search(search));
        // Count already joined groups in the search result 
        int userGroupCount = 0;
        for(Group group : groups) {
    if(userGroups.contains(group)) {
        userGroupCount++;
    }
        }
        groups.removeAll(userGroups);
        groupCount = groups.size() - userGroupCount;
    }
    
    if(groupIndex >= groupCount) {
        groupIndex = groupCount;
    }

    // paginator vars
    int numPages = (int)Math.ceil((double)groupCount/(double)range);
    int curPage = (start/range) + 1;
    
    if(success) {
%>
<div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img src="images/success-16x16.gif"
                    width="16" height="16" border="0" alt=""></td>
                <td class="jive-icon-label"><fmt:message
                        key="user.groups.form.update" /></td>
            </tr>
        </tbody>
    </table>
</div>
<br>
<%
    }
%>

<html>
<head>
<title><fmt:message key="user.groups.title" /></title>
<meta name="subPageID" content="user-groups" />
<meta name="extraParams"
    content="<%="username="+URLEncoder.encode(username, "UTF-8")%>" />
</head>
<body>
    <p>
        <fmt:message key="user.groups.member.info" />
        <b><%=username%>.</b>
    </p>
    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
                <tr>
                    <th>&nbsp;</th>
                    <th><fmt:message key="user.groups.name" /></th>
                    <th><fmt:message key="global.delete" /></th>
                </tr>
            </thead>
            <tbody>
                <%
                    // Print the list of groups
                                                            if (userGroups.isEmpty()) {
                %>
                <tr>
                    <td align="center" colspan="6"><fmt:message
                            key="group.summary.no_groups" /></td>
                </tr>

                <%
                    }
                                                            int x = 0;
                                                            for (Group group : userGroups) {
                                                                String groupName = URLEncoder.encode(group.getName(), "UTF-8");
                                                                x++;
                %>
                <tr class="jive-<%=(((x % 2) == 0) ? "even" : "odd")%>">
                    <td width="1%" valign="top"><%=x%></td>
                    <td><a href="group-edit.jsp?group=<%=groupName%>"><%=StringUtils.escapeHTMLTags(group.getName())%></a>
                        <%
                            if (group.getDescription() != null) {
                        %> <br> <span class="jive-description"> <%=StringUtils.escapeHTMLTags(group.getDescription())%>
                    </span> <%
    }
 %></td>

                    <td width="5%"><a
                        href="user-groups.jsp?username=<%=URLEncoder.encode(user.getUsername(), "UTF-8")%>&delete=<%=groupName%>&csrf=${csrf}"
                        title="<fmt:message key="global.click_delete" />"><img
                            src="images/delete-16x16.gif" width="16" height="16" border="0"
                            alt="<fmt:message key="global.click_delete" />"></a></td>
                </tr>
                <%
                    }
                %>
            </tbody>
        </table>
    </div>
    <br />

    <p>
        <fmt:message key="user.groups.info" />
        <b><%=username%>.</b>
    </p>
    <%
        if (webManager.getGroupManager().isSearchSupported()) {
    %>

    <form action="user-groups.jsp" method="get" name="searchForm">
        <table border="0" width="100%" cellpadding="0" cellspacing="0">
            <tr>
                <td valign="bottom"><fmt:message
                        key="group.summary.total_group" /> <b><%=groupCount%></b></td>
                <td align="right" valign="bottom"><fmt:message
                        key="group.summary.search" />: <input type="text" size="30"
                    maxlength="150" name="search"
                    value="<c:out value='${param.search}'/>"></td>
            </tr>
        </table>
        <input type="hidden" name="username"
            value="<%=StringUtils.escapeForXML(user.getUsername())%>">
    </form>

    <script language="JavaScript" type="text/javascript">
        document.searchForm.search.focus();
    </script>

    <%
        }
        // Otherwise, searching is not supported.
        else {
    %>
    <p>
        <fmt:message key="group.summary.total_group" />
        <b><%=groupCount%></b>
        <%
            if (numPages > 1) {
        %>

        ,
        <fmt:message key="global.showing" />
        <%=(start + 1)%>-<%=(start + range)%>

        <%
            }
        %>
    </p>
    <%
        }
    %>

    <%
        if (numPages > 1) {
    %>

    <p>
        <fmt:message key="global.pages" />
        [
        <%
            for (int i = 0; i < numPages; i++) {
                    String sep = ((i + 1) < numPages) ? " " : "";
                    boolean isCurrent = (i + 1) == curPage;
        %>
        <a
            href="user-groups.jsp?username=<%=StringUtils.escapeForXML(user.getUsername())%>&start=<%=(i * range)%><%=search != null ? "&search=" + URLEncoder.encode(search, "UTF-8") : ""%>"
            class="<%=((isCurrent) ? "jive-current" : "")%>"><%=(i + 1)%></a><%=sep%>

        <%
            }
        %>
        ]
    </p>

    <%
        }
    %>

    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
                <tr>
                    <th>&nbsp;</th>
                    <th nowrap><fmt:message key="user.groups.name" /></th>
                    <th nowrap><fmt:message key="global.add" /></th>
                </tr>
            </thead>
            <tbody>

                <%
                    // Print the list of groups
                    if (groups.isEmpty()) {
                %>
                <tr>
                    <td align="center" colspan="6"><fmt:message
                            key="group.summary.no_groups" /></td>
                </tr>

                <%
                    }
                    int i = 0;
                    for (Group group : groups.subList(start, groupIndex)) {
                        String groupName = URLEncoder.encode(group.getName(), "UTF-8");
                        i++;
                %>
                <tr class="jive-<%=(((i % 2) == 0) ? "even" : "odd")%>">
                    <td width="1%" valign="top"><%=i%></td>
                    <td><a href="group-edit.jsp?group=<%=groupName%>"><%=StringUtils.escapeHTMLTags(group.getName())%></a>
                        <%
                            if (group.getDescription() != null) {
                        %> <br> <span class="jive-description"> <%=StringUtils.escapeHTMLTags(group.getDescription())%>
                    </span> <%
    }
 %></td>

                    <td width="5%"><a
                        href="user-groups.jsp?username=<%=URLEncoder.encode(user.getUsername(), "UTF-8")%>&add=<%=groupName%>&csrf=${csrf}"
                        title="<fmt:message key="global.click_add" />"> <img
                            src="images/add-16x16.gif" width="16" height="16" border="0"
                            alt="<fmt:message key="global.click_add" />"></a></td>
                </tr>
                <%
                    }
                %>
            </tbody>
        </table>
    </div>

    <%
        if (numPages > 1) {
    %>
    <br>
    <p>
        <fmt:message key="global.pages" />
        [
        <%
            for (i = 0; i < numPages; i++) {
                    String sep = ((i + 1) < numPages) ? " " : "";
                    boolean isCurrent = (i + 1) == curPage;
        %>
        <a
            href="user-groups.jsp?username=<%=StringUtils.escapeForXML(user.getUsername())%>&start=<%=(i * range)%><%=search != null ? "&search=" + URLEncoder.encode(search, "UTF-8") : ""%>"
            class="<%=((isCurrent) ? "jive-current" : "")%>"><%=(i + 1)%></a><%=sep%>

        <%
            }
        %>
        ]
    </p>

    <%
        }
    %>

</body>
</html>
