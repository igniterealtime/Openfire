<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software.
  - Use is subject to license terms.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.muc.spi.*,
                 java.text.DateFormat,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.muc.MUCRoom,
                 java.util.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Group Chat Rooms";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "muc-room-summary.jsp"));
    pageinfo.setPageID("muc-room-summary");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",15);

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
Below is an overview of the Group Chat Rooms in the system. From here you can view the rooms, edit their
properties, and create new rooms.
</p>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Room destroyed successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
Total Rooms: <%= roomsCount %>,
<%  if (numPages > 1) { %>

    Showing <%= (start+1) %>-<%= (maxRoomIndex) %>,

<%  } %>
Sorted by Room ID
</p>

<%  if (numPages > 1) { %>

    <p>
    Pages:
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
        <th>Room</th>
        <th>Description</th>
        <th>Persistent</th>
        <th>Users</th>
        <th>Edit</th>
        <th>Destroy</th>
    </tr>
</thead>
<tbody>

<%  // Print the list of rooms
    Iterator<MUCRoom> roomsPage = rooms.subList(start, maxRoomIndex).iterator();
    if (!roomsPage.hasNext()) {
%>
    <tr>
        <td align="center" colspan="7">
            No rooms in the Group Chat service.
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
                <%=  room.getName() %>
            <% }
               else { %>
                <%= room.getNaturalLanguageName() %> (<%=  room.getName() %>)
            <% } %>
        </td>
        <td width="45%" valign="middle">
            <%=  room.getDescription() %>
        </td>
        <td width="1%" align="center">
                <% if (room.isPersistent()) { %>
                <img src="images/tape.gif" width="16" height="16" border="0" alt="Room is persistent">
                <% } else { %>
                <img src="images/blank.gif" width="16" height="16" border="0" alt="Room is temporary">
                <% } %>
        </td>
        <td width="1%" align="center">
            <%= room.getOccupantsCount() %> / <%= room.getMaxUsers() %>
        </td>
        <td width="1%" align="center">
            <a href="muc-room-edit-form.jsp?roomName=<%= room.getName() %>"
             title="Click to edit..."
             ><img src="images/edit-16x16.gif" width="17" height="17" border="0"></a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="muc-room-delete.jsp?roomName=<%= room.getName() %>"
             title="Click to delete..."
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
    Pages:
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

<jsp:include page="bottom.jsp" flush="true" />
