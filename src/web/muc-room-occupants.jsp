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

<%@ page import="org.jivesoftware.openfire.muc.MUCRole,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 org.jivesoftware.util.ParamUtils,
                 java.net.URLEncoder,
                 java.text.DateFormat"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.muc.NotAllowedException" %>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters
    JID roomJID = new JID(ParamUtils.getParameter(request,"roomJID"));
    String nickName = ParamUtils.getParameter(request,"nickName");
    String kick = ParamUtils.getParameter(request,"kick");
    String roomName = roomJID.getNode();

    // Load the room object
    MUCRoom room = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomName);
    if (room == null) {
        // The requested room name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-room-summary.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
        return;
    }

    // Kick nick specified
    if (kick != null) {
        MUCRole role = room.getOccupant(nickName);
        if (role != null) {
            try {
                room.kickOccupant(role.getUserAddress(), XMPPServer.getInstance().createJID(webManager.getUser().getUsername(), null), "");
                // Log the event
                webManager.logEvent("kicked MUC occupant "+nickName+" from "+roomName, null);
                // Done, so redirect
                response.sendRedirect("muc-room-occupants.jsp?roomJID="+URLEncoder.encode(room.getJID().toBareJID(), "UTF-8")+"&nickName="+URLEncoder.encode(role.getNickname(), "UTF-8")+"&deletesuccess=true");
                return;
            }
            catch (NotAllowedException e) {
                // Done, so redirect
                response.sendRedirect("muc-room-occupants.jsp?roomJID="+URLEncoder.encode(room.getJID().toBareJID(), "UTF-8")+"&nickName="+URLEncoder.encode(role.getNickname(), "UTF-8")+"&deletefailed=true");
                return;
            }
        }
    }

    // Formatter for dates
    DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
%>

<html>
<head>
<title><fmt:message key="muc.room.occupants.title"/></title>
<meta name="subPageID" content="muc-room-edit-form"/>
<meta name="extraParams" content="<%= "roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8")+"&create=false" %>"/>
</head>
<body>

    <p>
    <fmt:message key="muc.room.occupants.info" />
    </p>

    <%  if (request.getParameter("deletesuccess") != null) { %>

        <div class="jive-success">
        <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
            <fmt:message key="muc.room.occupants.kicked">
                <fmt:param value="<%= nickName %>"/>
            </fmt:message>
            </td></tr>
        </tbody>
        </table>
        </div><br>

    <%  } %>

    <%  if (request.getParameter("deletefailed") != null) { %>

        <div class="jive-error">
        <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
            <fmt:message key="muc.room.occupants.kickfailed">
                <fmt:param value="<%= nickName %>"/>
            </fmt:message>
            </td></tr>
        </tbody>
        </table>
        </div><br>

    <%  } %>

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
            <th scope="col"><fmt:message key="muc.room.occupants.kick" /></th>
        </tr>
    </thead>
    <tbody>
        <% for (MUCRole role : room.getOccupants()) { %>
        <tr>
            <td><%= role.getUserAddress() %></td>
            <td><%= role.getNickname() %></td>
            <td><%= role.getRole() %></td>
            <td><%= role.getAffiliation() %></td>
            <td><a href="muc-room-occupants.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>&nickName=<%= URLEncoder.encode(role.getNickname(), "UTF-8") %>&kick=1" title="<fmt:message key="muc.room.occupants.kick"/>"><img src="images/delete-16x16.gif" alt="<fmt:message key="muc.room.occupants.kick"/>" border="0" width="16" height="16"/></a></td>
        </tr>
        <% } %>
    </tbody>
    </table>
    </div>

    </body>
</html>