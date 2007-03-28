<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software.
  - Use is subject to license terms.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 java.util.*,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="muc.room.summary.title"/></title>
        <meta name="pageID" content="muc-room-summary"/>
        <meta name="helpPage" content="edit_group_chat_room_settings.html"/>
    </head>
    <body>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("muc-room-summary", 15));

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("muc-room-summary", range);
    }

    // Get the rooms in the server
    List<MUCRoom> rooms = webManager.getMultiUserChatServer().getChatRooms();
    Collections.sort(rooms, new Comparator() {
        public int compare(Object o1, Object o2) {
            MUCRoom room1 = (MUCRoom)o1;
            MUCRoom room2 = (MUCRoom)o2;
            return room1.getName().toLowerCase().compareTo(room2.getName().toLowerCase());
        }
    });
    int roomsCount = rooms.size();

    // paginator vars
    int numPages = (int)Math.ceil((double)roomsCount/(double)range);
    int curPage = (start/range) + 1;
    int maxRoomIndex = (start+range <= roomsCount ? start+range : roomsCount);
%>

<p>
<fmt:message key="muc.room.summary.info" />
</p>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
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
</p>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />:
    [
    <%  for (int i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="muc-room-summary.jsp?start=<%= (i*range) %>"
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
                 <a href="muc-room-edit-form.jsp?roomName=<%= URLEncoder.encode(room.getName(), "UTF-8") %>"title="<fmt:message key="global.click_edit" />">
	                 <%=  room.getName() %>
	             </a>
            <% }
               else { %>
	            <a href="muc-room-edit-form.jsp?roomName=<%= URLEncoder.encode(room.getName(), "UTF-8") %>"title="<fmt:message key="global.click_edit" />">
                <%= room.getNaturalLanguageName() %> (<%=  room.getName() %>)
	            </a>
            <% } %>
        </td>
        <td width="45%" valign="middle">
            <%=  room.getDescription() %>
        </td>
        <td width="1%" align="center">
                <% if (room.isPersistent()) { %>
                <img src="images/tape.gif" width="16" height="16" border="0" alt="<fmt:message key="muc.room.summary.alt_persistent" />">
                <% } else { %>
                <img src="images/blank.gif" width="16" height="16" border="0" alt="<fmt:message key="muc.room.summary.alt_temporary" />">
                <% } %>
        </td>
        <td width="1%" align="center">
            <%= room.getOccupantsCount() %> / <%= room.getMaxUsers() %>
        </td>
        <td width="1%" align="center">
            <a href="muc-room-edit-form.jsp?roomName=<%= URLEncoder.encode(room.getName(), "UTF-8") %>"
             title="<fmt:message key="global.click_edit" />"
             ><img src="images/edit-16x16.gif" width="17" height="17" border="0"></a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="muc-room-delete.jsp?roomName=<%= URLEncoder.encode(room.getName(), "UTF-8") %>"
             title="<fmt:message key="global.click_delete" />"
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
    <fmt:message key="global.pages" />:
    [
    <%  for (i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="muc-room-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

    </body>
</html>