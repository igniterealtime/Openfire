<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2007 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.openfire.user.UserNotFoundException"
    errorPage="error.jsp"
%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="org.jivesoftware.openfire.roster.Roster" %>
<%@ page import="org.jivesoftware.openfire.roster.RosterItem" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="java.util.*" %>
<%@ page import="org.jivesoftware.openfire.group.Group" %>

<%!
    final int DEFAULT_RANGE = 15;
    final int[] RANGE_PRESETS = {15, 25, 50, 75, 100};
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<%
    class RosterItemComparator implements Comparator<RosterItem> {
        public int compare(RosterItem itemA, RosterItem itemB) {
            return itemA.getJid().toBareJID().compareTo(itemB.getJid().toBareJID());
        }
    }
%>
<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("user-roster", DEFAULT_RANGE));
    int filter = ParamUtils.getIntParameter(request,"filter",webManager.getPageProperty("user-roster", "filter", 0));

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("user-roster", range);
    }

    if (request.getParameter("filter") != null) {
        webManager.setPageProperty("user-roster", "filter", filter);
    }

    // Get parameters //
    String username = ParamUtils.getParameter(request, "username");

    // Load the roster object
    Roster roster = null;
    int rosterCount = 0;
    try {
        roster = webManager.getRosterManager().getRoster(username);
        if (filter == 2) {
            for (RosterItem item : roster.getRosterItems()) {
                if (item.isOnlyShared()) {
                    rosterCount++;
                }
            }
        }
        else if (filter == 1) {
            for (RosterItem item : roster.getRosterItems()) {
                if (!item.isOnlyShared()) {
                    rosterCount++; 
                }
            }
        }
        else {
            rosterCount = roster.getRosterItems().size();
        }
    }
    catch (UserNotFoundException unfe) {
        // ignore
    }

    // paginator vars
    int numPages = (int)Math.ceil((double)rosterCount/(double)range);
    int curPage = (start/range) + 1;
%>

<html>
    <head>
        <title><fmt:message key="user.roster.title"/></title>
        <meta name="subPageID" content="user-roster"/>
        <meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, "UTF-8") %>"/>
        <style type="text/css">
        .jive-current {
            font-weight: bold;
            text-decoration: none;
        }
        </style>
    </head>
    <body>

    <%  if (request.getParameter("addsuccess") != null) { %>

        <div class="jive-success">
        <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr><td class="jive-icon"><img src="images/success-16x16.gif" alt="" width="16" height="16" border="0"></td>
            <td class="jive-icon-label">
            <fmt:message key="user.roster.added" />
            </td></tr>
        </tbody>
        </table>
        </div><br>

    <%  } %>

    <%  if (request.getParameter("editsuccess") != null) { %>

        <div class="jive-success">
        <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr><td class="jive-icon"><img src="images/success-16x16.gif" alt="" width="16" height="16" border="0"></td>
            <td class="jive-icon-label">
            <fmt:message key="user.roster.edited" />
            </td></tr>
        </tbody>
        </table>
        </div><br>

    <%  } %>

    <%  if (request.getParameter("deletesuccess") != null) { %>

        <div class="jive-success">
        <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr><td class="jive-icon"><img src="images/success-16x16.gif" alt="" width="16" height="16" border="0"></td>
            <td class="jive-icon-label">
            <fmt:message key="user.roster.deleted" />
            </td></tr>
        </tbody>
        </table>
        </div><br>

    <%  } %>

<p>
<fmt:message key="user.roster.info">
    <fmt:param value="<%= "<b>"+username+"</b>" %>" />
</fmt:message>
</p>

<p style="margin-bottom: 2px">
<fmt:message key="user.roster.total_items" />:
<b><%= LocaleUtils.getLocalizedNumber(rosterCount) %></b> --

<%  if (numPages > 1) { %>

    <fmt:message key="global.showing" />
    <%= LocaleUtils.getLocalizedNumber(start+1) %>-<%= LocaleUtils.getLocalizedNumber(start+range > rosterCount ? rosterCount:start+range) %>,

<%  } %>
<fmt:message key="user.roster.sorted" />

<%  if (numPages > 1) { %>

    --

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
        <a href="user-roster.jsp?username=<%= URLEncoder.encode(username, "UTF-8") %>&start=0&range=<%= range %>&filter=<%= filter %>">1</a> ...

    <%
        }
        int i;
        for (i = s; i < numPages && i < num; i++) {
            String sep = ((i + 1) < numPages) ? " " : "";
            boolean isCurrent = (i + 1) == curPage;
    %>
        <a href="user-roster.jsp?username=<%= URLEncoder.encode(username, "UTF-8") %>&start=<%= (i*range) %>&range=<%= range %>&filter=<%= filter %>"
        class="<%= ((isCurrent) ? "jive-current" : "") %>"
        ><%= (i+1) %></a><%= sep %>

    <%  } %>

    <%  if (i < numPages) { %>

        ... <a href="user-roster.jsp?username=<%= URLEncoder.encode(username, "UTF-8") %>&start=<%= ((numPages-1)*range) %>&range=<%= range %>&filter=<%= filter %>"><%= numPages %></a>

    <%  } %>

    ]

<%  } %>
</p>

<div style="float:right; vertical-align: bottom; padding: 0; margin-bottom: 0; background-color: #ffffff; border: 0.0px solid #005500; vertical-align: middle">
    <a style="color: #007700; font-weight: bold; vertical-align: middle; text-decoration: none" href="user-roster-add.jsp?username=<%= URLEncoder.encode(username, "UTF-8") %>"><fmt:message key="user.roster.add"/><img style="position: relative; left: 3px; top: 3px" src="images/add-16x16.gif" alt="" width="16" height="16" border="0"></a>
</div>

<p style="margin-bottom: 2px">
    <fmt:message key="user.roster.items_per_page" />:
    <select size="1" onchange="location.href='user-roster.jsp?username=<%= URLEncoder.encode(username, "UTF-8") %>&start=0&range=' + this.options[this.selectedIndex].value;">

        <% for (int aRANGE_PRESETS : RANGE_PRESETS) { %>

        <option value="<%= aRANGE_PRESETS %>"
                <%= (aRANGE_PRESETS == range ? "selected" : "") %>><%= aRANGE_PRESETS %>
        </option>

        <% } %>

    </select>

    --

    <fmt:message key="user.roster.filter" />:
<select size="1" onchange="location.href='user-roster.jsp?username=<%= URLEncoder.encode(username, "UTF-8") %>&start=0&range=<%= range %>&filter=' + this.options[this.selectedIndex].value;">

    <option value="0"<%= filter == 0 ? " SELECTED" : "" %>><fmt:message key="user.roster.filter.all" /></option>
    <option value="1"<%= filter == 1 ? " SELECTED" : "" %>><fmt:message key="user.roster.filter.noshared" /></option>
    <option value="2"<%= filter == 2 ? " SELECTED" : "" %>><fmt:message key="user.roster.filter.onlyshared" /></option>

</select>
</p>

<div class="jive-table" style="clear: both">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="user.roster.jid" /></th>
        <th nowrap><fmt:message key="user.roster.nickname" /></th>
        <th nowrap><fmt:message key="user.roster.groups" /></th>
        <th nowrap><fmt:message key="user.roster.subscription" /></th>
        <th nowrap><fmt:message key="user.roster.edit" /></th>
        <th nowrap><fmt:message key="global.delete" /></th>
    </tr>
</thead>
<tbody>
    <%
        if (roster == null) {
    %>
    <tr>
        <td colspan="7" align="center">
            <fmt:message key="error.requested_user_not_found" />
        </td>
    </tr>
    <%
        } else if (rosterCount < 1) {
    %>
    <tr>
        <td colspan="7" align="center">
            <i><fmt:message key="user.roster.none_found" /></i>
        </td>
    </tr>
    <%
        } else {
            List<RosterItem> rosterItems = new ArrayList<RosterItem>(roster.getRosterItems());
            Collections.sort(rosterItems, new RosterItemComparator());
            int i = 0;
            for (RosterItem rosterItem : rosterItems) {
                if (filter == 2 && !rosterItem.isOnlyShared()) {
                    continue;
                }
                if (filter == 1 && rosterItem.isOnlyShared()) {
                    continue;
                }
                i++;
                if (i < start) {
                    continue;
                }
                if (i > start+range) {
                    break;
                }
    %>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">

        <td width="1%">
            <%= i %>
        </td>
        <td>
            <a href="user-roster-view.jsp?username=<%= URLEncoder.encode(username, "UTF-8") %>&jid=<%= URLEncoder.encode(rosterItem.getJid().toString(), "UTF-8") %>"
             title="<fmt:message key="user.roster.click_view" />"
             ><%= rosterItem.getJid() %></a>
        </td>
        <td>
            <%= (rosterItem.getNickname() != null ? rosterItem.getNickname() : "<i>None</i>") %>
        </td>
        <td>
            <%
                List<String> groups = rosterItem.getGroups();
                int count = 0;
                if (!groups.isEmpty()) {
                    for (String group : groups) {
                        if (count != 0) {
                            out.print(", ");
                        }
                        out.print(group);
                        count++;
                    }
                }
                Collection<Group> sharedGroups = rosterItem.getSharedGroups();
                if (filter != 1) {
                    if (!sharedGroups.isEmpty()) {
                        for (Group group : sharedGroups) {
                            if (count != 0) {
                                out.print(", ");
                            }
                            out.print("<a style='text-decoration: underline' href='group-edit.jsp?group="+URLEncoder.encode(group.getName(), "UTF-8")+"'>");
                            out.print(group.getName());
                            out.print("</a>");
                            count++;
                        }
                    }
                }
                if (count == 0) {
            %>
                <i>None</i>
            <%
                }
            %>
        </td>
        <td>
            <%= rosterItem.getSubStatus().getName() %>
        </td>
        <td width="1%" align="center">
            <a href="user-roster-edit.jsp?username=<%= URLEncoder.encode(username, "UTF-8") %>&jid=<%= URLEncoder.encode(rosterItem.getJid().toString(), "UTF-8") %>"
             title="<fmt:message key="global.click_edit" />"
             ><img src="images/edit-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.click_edit" />"></a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <% if (sharedGroups.isEmpty()) { %>
            <a href="user-roster-delete.jsp?username=<%= URLEncoder.encode(username, "UTF-8") %>&jid=<%= URLEncoder.encode(rosterItem.getJid().toString(), "UTF-8") %>"
             title="<fmt:message key="global.click_delete" />"
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.click_delete" />"></a>
            <% } else { %>
             <img onclick='alert("<fmt:message key="user.roster.cant_delete" />")' src="images/lock.gif" width="16" height="16" border="0" alt="">
            <% } %>
        </td>
    </tr>
    <%
            }
        }
    %>
</tbody>
</table>
</div>

<br><br>
    
</body>
</html>