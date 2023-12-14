<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
                 org.jivesoftware.openfire.muc.MUCRoom,
                 java.util.*,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.util.stream.Collectors" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="admin" uri="admin" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    String mucname = ParamUtils.getParameter(request,"mucname");
    String roomJIDStr = ParamUtils.getParameter(request,"roomJID");
    JID roomJID = null;
    if (roomJIDStr != null) roomJID = new JID(roomJIDStr);

    MultiUserChatService mucService = null;
    if (roomJID != null) {
        mucService = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID);
    }
    else if (mucname != null && webManager.getMultiUserChatManager().isServiceRegistered(mucname)) {
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

    // Get the rooms in the server
    final List<String> names = mucService.getAllRoomNames().stream()
        .sorted(Comparator.comparing(String::toLowerCase))
        .collect(Collectors.toList());

    // paginator vars
    final ListPager<String> listPager = new ListPager<>(request, response, names, "mucname");
%>
<html>
    <head>
        <title><fmt:message key="muc.room.summary.title"/></title>
        <meta name="pageID" content="muc-room-summary"/>
        <meta name="helpPage" content="edit_group_chat_room_settings.html"/>
    </head>
    <body>

<p>
<fmt:message key="muc.room.summary.info" />
<a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucService.getServiceName(), "UTF-8")%>"><%= StringUtils.escapeHTMLTags(mucService.getServiceDomain()) %></a>
<fmt:message key="muc.room.summary.info2" />
</p>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <admin:infoBox type="success">
        <fmt:message key="muc.room.summary.destroyed" />
    </admin:infoBox>

<%  } %>

<p>
<fmt:message key="muc.room.summary.total_room" />: <%= listPager.getTotalItemCount() %>,
<%  if (listPager.getTotalPages() > 1) { %>

    <fmt:message key="global.showing" /> <%= listPager.getFirstItemNumberOnPage() %>-<%= listPager.getLastItemNumberOnPage() %>,

<%  } %>
<fmt:message key="muc.room.summary.sorted_id" />

<% if (webManager.getMultiUserChatManager().getMultiUserChatServicesCount() > 1) { %>
-- <fmt:message key="muc.room.summary.service" />:
    <select name="mucname" id="mucname" onchange="location.href='muc-room-summary.jsp?mucname=' + this.options[this.selectedIndex].value;">
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

</p>

<%  if (listPager.getTotalPages() > 1) { %>

    <p><fmt:message key="global.pages" />: [ <%=listPager.getPageLinks() %> ]</p>

<%  } %>

<div class="jive-table">
<table>
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="muc.room.summary.room" /></th>
        <th nowrap><fmt:message key="muc.room.summary.description" /></th>
        <th nowrap><fmt:message key="muc.room.summary.persistent" /></th>
        <th nowrap><fmt:message key="muc.room.summary.users" /></th>
        <th nowrap><fmt:message key="muc.room.summary.edit" /></th>
        <th nowrap><fmt:message key="muc.room.summary.destroy" /></th>
    </tr>
</thead>
<tbody>

<%  // Print the list of rooms
    if (listPager.getTotalItemCount() < 1) {
%>
    <tr>
        <td style="text-align: center" colspan="7">
            <fmt:message key="muc.room.summary.no_room_in_group" />
        </td>
    </tr>

<%
    }
    final List<String> itemsOnCurrentPage = listPager.getItemsOnCurrentPage();
    int i = listPager.getFirstItemNumberOnPage();
    for(String name : itemsOnCurrentPage) {
        MUCRoom room = mucService.getChatRoom(name); // This will load the room on-demand if it's not yet in memory.
        i++;
%>
    <tr>
        <td style="width: 1%">
            <%= (i-1) %>
        </td>
        <td style="width: 45%; vertical-align: middle">
            <% if (room.getName().equals(room.getNaturalLanguageName())) { %>
                 <a href="muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>" title="<fmt:message key="global.click_edit" />">
                     <%=  StringUtils.escapeHTMLTags(room.getName()) %>
                 </a>
            <% }
               else { %>
                <a href="muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"title="<fmt:message key="global.click_edit" />">
                <%= StringUtils.escapeHTMLTags(room.getNaturalLanguageName()) %> (<%=  StringUtils.escapeHTMLTags(room.getName()) %>)
                </a>
            <% } %>
        </td>
        <td style="width: 45%; vertical-align: middle">
            <% if (!"".equals(room.getDescription())) { %>
                <%= StringUtils.escapeHTMLTags(room.getDescription()) %>
            <% }
               else { %>
                &nbsp;
            <% } %>
        </td>
        <td style="width: 1%; text-align: center">
                <% if (room.isPersistent()) { %>
                <img src="images/tape.gif" alt="<fmt:message key="muc.room.summary.alt_persistent" />">
                <% } else { %>
                <img src="images/blank.gif" alt="<fmt:message key="muc.room.summary.alt_temporary" />">
                <% } %>
        </td>
        <td style="width: 1%; text-align: center">
            <% if (room.isLocked()) {%>
            <img src="images/lock.gif" alt="<fmt:message key="muc.room.summary.alt_locked" />">
            <% } else { %>
                <nobr><%= room.getOccupantsCount() %>
                <% if (room.getMaxUsers() > 0 ) { %>
                    / <%= room.getMaxUsers() %>
                <% } %></nobr>
            <% } %>
        </td>
        <td style="width: 1%; text-align: center">
            <a href="muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"
             title="<fmt:message key="global.click_edit" />"
             ><img src="images/edit-16x16.gif" alt="Edit"></a>
        </td>
        <td style="width: 1%; text-align: center; border-right:1px #ccc solid;">
            <a href="muc-room-delete.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"
             title="<fmt:message key="global.click_delete" />"
             ><img src="images/delete-16x16.gif" alt="Delete"></a>
        </td>
    </tr>

<%
    }
%>
</tbody>
</table>
</div>

<%  if (listPager.getTotalPages() > 1) { %>
        <p><fmt:message key="global.pages" />: [ <%=listPager.getPageLinks() %> ]</p>
<%  } %>

<%=listPager.getJumpToPageForm()%>

<script>
    <%=listPager.getPageFunctions()%>
</script>
    </body>
</html>
