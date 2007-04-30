<%--
  -	$RCSfile$
  -	$Revision: 5300 $
  -	$Date: 2006-09-08 16:16:21 -0700 (Fri, 08 Sep 2006) $
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software.
  - Use is subject to license terms.
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.openfire.muc.MUCRole,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 java.net.URLEncoder,
                 java.text.DateFormat"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters
    String roomName = ParamUtils.getParameter(request,"roomName");

    // Load the room object
    MUCRoom room = webManager.getMultiUserChatServer().getChatRoom(roomName);
    if (room == null) {
        // The requested room name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-room-summary.jsp");
        return;
    }

    // Formatter for dates
    DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
%>

<html>
<head>
<title><fmt:message key="muc.room.occupants.title"/></title>
<meta name="subPageID" content="muc-room-edit-form"/>
<meta name="extraParams" content="<%= "roomName="+URLEncoder.encode(roomName, "UTF-8")+"&create=false" %>"/>
</head>
<body>

    <p>
    <fmt:message key="muc.room.occupants.info" />
    </p>
    <div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th scope="col"><fmt:message key="muc.room.edit.form.room_id" /></th>
            <th scope="col"><fmt:message key="muc.room.edit.form.users" /></th>
            <th scope="col"><fmt:message key="muc.room.edit.form.on" /></th>
            <th scope="col"><fmt:message key="muc.room.edit.form.modified" /></th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td><%= room.getName() %></td>
            <td><%= room.getOccupantsCount() %> / <%= room.getMaxUsers() %></td>
            <td><%= dateFormatter.format(room.getCreationDate()) %></td>
            <td><%= dateFormatter.format(room.getModificationDate()) %></td>
        </tr>
    </tbody>
    </table>
    </div>

    <br>
    <p>
        <fmt:message key="muc.room.occupants.detail.info" />
    </p>

    <div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th scope="col"><fmt:message key="muc.room.occupants.user" /></th>
            <th scope="col"><fmt:message key="muc.room.occupants.nickname" /></th>
            <th scope="col"><fmt:message key="muc.room.occupants.role" /></th>
            <th scope="col"><fmt:message key="muc.room.occupants.affiliation" /></th>
        </tr>
    </thead>
    <tbody>
        <% for (MUCRole role : room.getOccupants()) { %>
        <tr>
            <td><%= role.getChatUser().getAddress() %></td>
            <td><%= role.getNickname() %></td>
            <td><%= role.getRole() %></td>
            <td><%= role.getAffiliation() %></td>
        </tr>
        <% } %>
    </tbody>
    </table>
    </div>

    </body>
</html>