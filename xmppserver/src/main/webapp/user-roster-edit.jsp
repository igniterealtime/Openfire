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

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.CookieUtils,
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
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters
    boolean cancel = request.getParameter("cancel") != null;
    String username = ParamUtils.getParameter(request, "username");
    String jid = ParamUtils.getParameter(request, "jid");
    String nickname = ParamUtils.getParameter(request, "nickname");
    String groups = ParamUtils.getParameter(request, "groups");
    Integer sub = ParamUtils.getIntParameter(request, "sub", 0);
    Integer ask = ParamUtils.getIntParameter(request, "ask", 0);
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
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Handle a roster item update:
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
        item.setAskStatus(RosterItem.AskType.getTypeFromInt(ask));
        // update the roster item
        roster.updateRosterItem(item);
        // Log the event
        webManager.logEvent("updated roster item for "+username, "roster item:\njid = "+jid+"\nnickname = "+nickname+"\ngroupList = "+groupList);
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

<form action="user-roster-edit.jsp">
        <input type="hidden" name="csrf" value="${csrf}">

<input type="hidden" name="username" value="<%= StringUtils.escapeForXML(username) %>">
<input type="hidden" name="jid" value="<%= StringUtils.escapeForXML(jid) %>">
<input type="hidden" name="save" value="true">

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
                <label for="nickname"><fmt:message key="user.roster.nickname" />:</label>
            </td>
            <td>
                <input type="text" size="30" maxlength="150" name="nickname" id="nickname"
                 value="<%= item.getNickname() == null || item.getNickname().isEmpty() ? "" : StringUtils.escapeForXML(item.getNickname()) %>">
            </td>
        </tr>
        <tr>
            <td class="c1">
                <label for="groups"><fmt:message key="user.roster.groups" />:</label>
            </td>
            <td>
                <input type="text" size="30" maxlength="255" name="groups" id="groups"
                 value="<%
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
                  %>">
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
                <label for="sub"><fmt:message key="user.roster.subscription" />:</label>
            </td>
            <td>
                <select name="sub" id="sub">
                    <option value="<%= RosterItem.SUB_REMOVE.getValue() %>"<%= item.getSubStatus() == RosterItem.SUB_REMOVE ? " SELECTED" : "" %>>Remove</option>
                    <option value="<%= RosterItem.SUB_NONE.getValue() %>"<%= item.getSubStatus() == RosterItem.SUB_NONE  ? " SELECTED" : "" %>>None</option>
                    <option value="<%= RosterItem.SUB_TO.getValue() %>"<%= item.getSubStatus() == RosterItem.SUB_TO  ? " SELECTED" : "" %>>To</option>
                    <option value="<%= RosterItem.SUB_FROM.getValue() %>"<%= item.getSubStatus() == RosterItem.SUB_FROM  ? " SELECTED" : "" %>>From</option>
                    <option value="<%= RosterItem.SUB_BOTH.getValue() %>"<%= item.getSubStatus() == RosterItem.SUB_BOTH  ? " SELECTED" : "" %>>Both</option>
                </select>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <label for="ask"><fmt:message key="user.roster.ask" />:</label>
            </td>
            <td>
                <select name="ask" id="ask">
                    <option value="<%= RosterItem.ASK_NONE.getValue() %>"<%= item.getAskStatus() == RosterItem.ASK_NONE ? " SELECTED" : "" %>>None</option>
                    <option value="<%= RosterItem.ASK_SUBSCRIBE.getValue() %>"<%= item.getAskStatus() == RosterItem.ASK_SUBSCRIBE  ? " SELECTED" : "" %>>Subscribe</option>
                    <option value="<%= RosterItem.ASK_UNSUBSCRIBE.getValue() %>"<%= item.getAskStatus() == RosterItem.ASK_UNSUBSCRIBE  ? " SELECTED" : "" %>>Unsubscribe</option>
                </select>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="<fmt:message key="global.save" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">

</form>

    </body>
</html>
