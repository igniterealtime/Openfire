<%--
  -	$RCSfile$
  -	$Revision: 3710 $
  -	$Date: 2006-04-05 11:53:01 -0700 (Wed, 05 Apr 2006) $
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.LocaleUtils,
                 org.jivesoftware.util.ParamUtils,
                 java.net.URLEncoder"
        %>
<%@ page import="org.jivesoftware.openfire.sip.sipaccount.SipAccountDAO" %>
<%@ page import="org.jivesoftware.openfire.sip.sipaccount.SipAccount" %>
<%@ page import="java.util.Collection" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%!
    final int DEFAULT_RANGE = 15;
    final int[] RANGE_PRESETS = {15, 25, 50, 75, 100};
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<% webManager.init(request, response, session, application, out); %>

<html>
<head>
    <title>
        <fmt:message key="sipark.user.title"/>
    </title>
    <meta name="pageID" content="sipark-user-summary"/>
</head>
<body>
<% // Get parameters
    int start = ParamUtils.getIntParameter(request, "start", 0);
    int range = ParamUtils.getIntParameter(request, "range", webManager.getRowsPerPage("user-summary", DEFAULT_RANGE));

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("user-summary", range);
    }

    // Get the user manager
    int userCount = SipAccountDAO.getUserCount();

    // paginator vars
    int numPages = (int) Math.ceil((double) userCount / (double) range);
    int curPage = (start / range) + 1;
%>

<style type="text/css">
    .jive-current {
        font-weight: bold;
        text-decoration: none;
    }
</style>

<% if (request.getParameter("deletesuccess") != null) { %>

<div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img src="/images/success-16x16.gif" width="16" height="16" border="0"></td>
                <td class="jive-icon-label">
                    <fmt:message key="user.summary.deleted"/>
                </td>
            </tr>
        </tbody>
    </table>
</div>
<br>

<% } %>

<p>
    <fmt:message key="sipark.user.description"/>
</p>

<p>
    <fmt:message key="user.summary.total_user"/>
    :
    <b><%= LocaleUtils.getLocalizedNumber(SipAccountDAO.getUserCount()) %>
    </b> --

    <% if (numPages > 1) { %>

    <fmt:message key="global.showing"/>
    <%= LocaleUtils.getLocalizedNumber(start + 1) %>-<%= LocaleUtils.getLocalizedNumber(start + range) %>,

    <% } %>
    <fmt:message key="user.summary.sorted"/>

    --
    <fmt:message key="user.summary.users_per_page"/>
    :
    <select size="1"
            onchange="location.href='sipark-user-summary.jsp?start=0&range=' + this.options[this.selectedIndex].value;">

        <% for (int i = 0; i < RANGE_PRESETS.length; i++) { %>

        <option value="<%= RANGE_PRESETS[i] %>"
                <%= (RANGE_PRESETS[i] == range ? "selected" : "") %>><%= RANGE_PRESETS[i] %>
        </option>

        <% } %>

    </select>
</p>

<% if (numPages > 1) { %>

<p>
    <fmt:message key="global.pages"/>
    :
    [
    <% int num = 15 + curPage;
        int s = curPage - 1;
        if (s > 5) {
            s -= 5;
        }
        if (s < 5) {
            s = 0;
        }
        if (s > 2) {
    %>
    <a href="sipark-user-summary.jsp?start=0&range=<%= range %>">1</a> ...

    <%
        }
        int i;
        for (i = s; i < numPages && i < num; i++) {
            String sep = ((i + 1) < numPages) ? " " : "";
            boolean isCurrent = (i + 1) == curPage;
    %>
    <a href="sipark-user-summary.jsp?start=<%= (i*range) %>&range=<%= range %>"
       class="<%= ((isCurrent) ? "jive-current" : "") %>"
            ><%= (i + 1) %>
    </a><%= sep %>

    <% } %>

    <% if (i < numPages) { %>

    ... <a href="sipark-user-summary.jsp?start=<%= ((numPages-1)*range) %>&range=<%= range %>"><%= numPages %>
</a>

    <% } %>

    ]

</p>

<% } %>

<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
            <tr>
                <th>&nbsp;</th>
                <th nowrap>
                    <fmt:message key="user.summary.enabled"/>
                </th>
                <th nowrap>
                    <fmt:message key="user.summary.status"/>
                </th>
                <th nowrap>
                    <fmt:message key="user.create.username"/>
                </th>
                <th nowrap>SIP
                    <fmt:message key="user.create.username"/>
                </th>
                <th nowrap>
                    <fmt:message key="user.create.server"/>
                </th>
                <th nowrap>
                    <fmt:message key="user.summary.edit"/>
                </th>
                <th nowrap>
                    <fmt:message key="global.delete"/>
                </th>
            </tr>
        </thead>
        <tbody>

            <% // Print the list of users
                Collection<SipAccount> users = SipAccountDAO.getUsers(start, range);

                if (users.isEmpty()) {
            %>
            <tr>
                <td align="center" colspan="8">
                    <fmt:message key="user.summary.not_user"/>
                </td>
            </tr>
            <%
                }
                int i = start;
                for (SipAccount user : users) {
                    i++;
            %>
            <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
                <td width="1%">
                    <%= i %>
                </td>
                <td width="1%" align="center" valign="middle">
                    <% if (user.isEnabled()) { %>
                    <img src="/images/check.gif" width="17" height="17" border="0">
                    <% } else { %>
                    <img src="/images/x.gif" width="17" height="17" border="0">
                    <% } %>
                </td>
                <td width="10%" align="center" valign="middle">
                    <%=user.getStatus().name()%>
                </td>
                <td width="20%">
                    <a href="./../../user-properties.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"><%= user.getUsername() %>
                    </a>
                </td>
                <td width="20%">
                    <%= user.getSipUsername() %>
                </td>
                <td width="20%">
                    <%= user.getServer() %>
                </td>
                <td width="1%" align="center">
                    <a href="create-sipark-mapping.jsp?node=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"
                       title="<fmt:message key="global.click_edit" />"
                            ><img src="/images/edit-16x16.gif" width="17" height="17" border="0"></a>
                </td>
                <td width="1%" align="center" style="border-right:1px #ccc solid;">
                    <a href="sipark-user-delete.jsp?username=<%= URLEncoder.encode(user.getUsername(), "UTF-8") %>"
                       title="<fmt:message key="global.click_delete" />"
                            ><img src="/images/delete-16x16.gif" width="16" height="16" border="0"></a>
                </td>
            </tr>

            <%
                }
            %>
            <tr>
                <tr>
                    <td colspan="8">
                        <a href="create-sipark-mapping.jsp"><img src="/images/add-16x16.gif" border="0" align="texttop"
                                                                 style="margin-right: 3px;"/>
                            <fmt:message key="sipark.user.mapping.add"/>
                        </a>
                    </td>
                </tr>
            </tr>

        </tbody>
    </table>
</div>

<% if (numPages > 1) { %>

<p>
    <fmt:message key="global.pages"/>
    :
    [
    <% int num = 15 + curPage;
        int s = curPage - 1;
        if (s > 5) {
            s -= 5;
        }
        if (s < 5) {
            s = 0;
        }
        if (s > 2) {
    %>
    <a href="sipark-user-summary.jsp?start=0&range=<%= range %>">1</a> ...

    <%
        }
        i = 0;
        for (i = s; i < numPages && i < num; i++) {
            String sep = ((i + 1) < numPages) ? " " : "";
            boolean isCurrent = (i + 1) == curPage;
    %>
    <a href="user-summary.jsp?start=<%= (i*range) %>&range=<%= range %>"
       class="<%= ((isCurrent) ? "jive-current" : "") %>"
            ><%= (i + 1) %>
    </a><%= sep %>

    <% } %>

    <% if (i < numPages) { %>

    ... <a href="sipark-user-summary.jsp?start=<%= ((numPages-1)*range) %>&range=<%= range %>"><%= numPages %>
</a>

    <% } %>

    ]

</p>

<% } %>

</body>
</html>
