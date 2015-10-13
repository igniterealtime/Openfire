<%--
  -	$Revision$
  -	$Date$
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

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%><%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.jivesoftware.openfire.roster.Roster" %>
<%@ page import="org.jivesoftware.openfire.roster.RosterItem" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.jivesoftware.openfire.group.Group" %>
<%@ page import="java.util.Collection" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<% // Get parameters
    boolean cancel = request.getParameter("cancel") != null;
    String username = ParamUtils.getParameter(request, "username");
    String jid = ParamUtils.getParameter(request, "jid");
    String nickname = ParamUtils.getParameter(request, "nickname");
    String groups = ParamUtils.getParameter(request, "groups");
    Integer sub = ParamUtils.getIntParameter(request, "sub", 0);
    boolean save = ParamUtils.getBooleanParameter(request, "save");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-roster.jsp?username=" + URLEncoder.encode(username, "UTF-8"));
        return;
    }

    // Load the user's roster object
    Roster roster = webManager.getRosterManager().getRoster(username);

    // Load the roster item from the user's roster.
    RosterItem item = roster.getRosterItem(new JID(jid));

    // Handle a roster item delete:
    if (save) {
        List<String> groupList = new ArrayList<String>();
        if (groups != null) {
            for (String group : groups.split(",")) {
                groupList.add(group.trim());
            }
        }
        item.setNickname(nickname);
        item.setGroups(groupList);
        item.setSubStatus(RosterItem.SubType.getTypeFromInt(sub));
        // Delete the roster item
        roster.updateRosterItem(item);
        // Log the event
        webManager.logEvent("deleted roster item from "+username, "roster item:\njid = "+jid);
        // Done, so redirect
        response.sendRedirect("user-roster.jsp?username=" + URLEncoder.encode(username, "UTF-8") + "&editsuccess=true");
        return;
    }
%>

<html>
    <head>
        <title><fmt:message key="user.roster.edit.title"/></title>
        <meta name="subPageID" content="user-roster"/>
        <meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, "UTF-8") %>"/>
    </head>
    <body>

<p>
<fmt:message key="user.roster.edit.info">
    <fmt:param value="<%= StringUtils.escapeForXML(username) %>"/>
</fmt:message>
</p>

<fieldset>
    <legend><fmt:message key="user.roster.item.settings" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td class="c1">
                <fmt:message key="user.roster.jid" />:
            </td>
            <td>
                <%= StringUtils.escapeHTMLTags(jid) %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="user.roster.nickname" />:
            </td>
            <td>
                <%= StringUtils.escapeHTMLTags(item.getNickname()) %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="user.roster.groups" />:
            </td>
            <td>
                <%
                    List<String> groupList = item.getGroups();
                    if (!groupList.isEmpty()) {
                        int count = 0;
                        for (String group : groupList) {
                            if (count != 0) {
                                out.print(",");
                            }
                            out.print(StringUtils.escapeForXML(group));
                            count++;
                        }
                    }
                    else {
                        out.print("<i>None</i>");
                    }
                %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <a href="group-summary.jsp"><fmt:message key="user.roster.shared_groups" /></a>:
            </td>
            <td>
                <%
                    Collection<Group> sharedGroups = item.getSharedGroups();
                    if (!sharedGroups.isEmpty()) {
                        int count = 0;
                        for (Group group : sharedGroups) {
                            if (count != 0) {
                                out.print(",");
                            }
                            out.print("<a href='group-edit.jsp?group="+URLEncoder.encode(group.getName(), "UTF-8")+"'>");
                            out.print(StringUtils.escapeForXML(group.getName()));
                            out.print("</a>");
                            count++;
                        }
                    }
                    else {
                        out.print("<i>None</i>");
                    }
                %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="user.roster.subscription" />:
            </td>
            <td>
                <%= StringUtils.escapeHTMLTags(item.getSubStatus().getName()) %>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<form style="display: inline" action="user-roster-edit.jsp">
<input type="hidden" name="jid" value="<%= StringUtils.escapeForXML(jid) %>">
<input type="hidden" name="username" value="<%= StringUtils.escapeForXML(username) %>">
<input type="submit" value="<fmt:message key="user.roster.edit" />">
</form>

<% if (sharedGroups.isEmpty()) { %>
<form style="display: inline" action="user-roster-delete.jsp">
<input type="hidden" name="jid" value="<%= StringUtils.escapeForXML(jid) %>">
<input type="hidden" name="username" value="<%= StringUtils.escapeForXML(username) %>">
<input type="submit" value="<fmt:message key="global.delete" />">
</form>
<% } %>

    </body>
</html>
