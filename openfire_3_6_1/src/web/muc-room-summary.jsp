<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 java.util.*,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("muc-room-summary", 15));
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

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("muc-room-summary", range);
    }

    // Get the rooms in the server
    List<MUCRoom> rooms = mucService.getChatRooms();
    Collections.sort(rooms, new Comparator<MUCRoom>() {
        public int compare(MUCRoom room1, MUCRoom room2) {
            return room1.getName().toLowerCase().compareTo(room2.getName().toLowerCase());
        }
    });
    int roomsCount = rooms.size();

    // paginator vars
    int numPages = (int)Math.ceil((double)roomsCount/(double)range);
    int curPage = (start/range) + 1;
    int maxRoomIndex = (start+range <= roomsCount ? start+range : roomsCount);
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
<a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucService.getServiceName(), "UTF-8")%>"><%= mucService.getServiceDomain() %></a>
<fmt:message key="muc.room.summary.info2" />
</p>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="muc.room.summary.destroyed" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
<fmt:message key="muc.room.summary.total_room" />: <%= roomsCount %>,
<%  if (numPages > 1) { %>

    <fmt:message key="global.showing" /> <%= (start+1) %>-<%= (maxRoomIndex) %>,

<%  } %>
<fmt:message key="muc.room.summary.sorted_id" />

<% if (webManager.getMultiUserChatManager().getMultiUserChatServicesCount() > 1) { %>
-- <fmt:message key="muc.room.summary.service" />:
    <select name="mucname" onchange="location.href='muc-room-summary.jsp?mucname=' + this.options[this.selectedIndex].value;">
<% for (MultiUserChatService service : webManager.getMultiUserChatManager().getMultiUserChatServices()) {
    if (service.isHidden()) {
        // Private and hidden, skip it.
        continue;
    }
%>
    <option value="<%= service.getServiceName() %>"<%= mucService.getServiceName().equals(service.getServiceName()) ? " selected='selected'" : "" %>><%= service.getServiceDomain() %></option>
<% } %>
    </select>
<% } %>

</p>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />:
    [
    <%  for (int i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="muc-room-summary.jsp?mucname=<%= mucname == null ? "" : mucname %>&start=<%= (i*range) %>"
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
    Iterator<MUCRoom> roomsPage = rooms.subList(start, maxRoomIndex).iterator();
    if (!roomsPage.hasNext()) {
%>
    <tr>
        <td align="center" colspan="7">
            <fmt:message key="muc.room.summary.no_room_in_group" />
        </td>
    </tr>

<%
    }
    int i = start;
    while (roomsPage.hasNext()) {
        MUCRoom room = roomsPage.next();
        i++;
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
        <td width="1%">
            <%= i %>
        </td>
        <td width="45%" valign="middle">
            <% if (room.getName().equals(room.getNaturalLanguageName())) { %>
                 <a href="muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"title="<fmt:message key="global.click_edit" />">
	                 <%=  room.getName() %>
	             </a>
            <% }
               else { %>
	            <a href="muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"title="<fmt:message key="global.click_edit" />">
                <%= room.getNaturalLanguageName() %> (<%=  room.getName() %>)
	            </a>
            <% } %>
        </td>
        <td width="45%" valign="middle">
            <% if (!"".equals(room.getDescription())) { %>
                <%= room.getDescription() %>
            <% }
               else { %>
                &nbsp;
            <% } %>
        </td>
        <td width="1%" align="center">
                <% if (room.isPersistent()) { %>
                <img src="images/tape.gif" width="16" height="16" border="0" alt="<fmt:message key="muc.room.summary.alt_persistent" />">
                <% } else { %>
                <img src="images/blank.gif" width="16" height="16" border="0" alt="<fmt:message key="muc.room.summary.alt_temporary" />">
                <% } %>
        </td>
        <td width="1%" align="center">
            <nobr><%= room.getOccupantsCount() %> / <%= room.getMaxUsers() %></nobr>
        </td>
        <td width="1%" align="center">
            <a href="muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"
             title="<fmt:message key="global.click_edit" />"
             ><img src="images/edit-16x16.gif" width="17" height="17" border="0" alt=""></a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="muc-room-delete.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"
             title="<fmt:message key="global.click_delete" />"
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
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
    <fmt:message key="global.pages" />:
    [
    <%  for (i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="muc-room-summary.jsp?mucname=<%= mucname == null ? "" : mucname %>&start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

    </body>
</html>