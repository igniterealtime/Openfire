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

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.admin.*,
                 java.util.*,
                 org.jivesoftware.messenger.muc.MUCRoom,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 org.xmpp.packet.JID"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters
    boolean save = ParamUtils.getBooleanParameter(request,"save");
    String roomName = ParamUtils.getParameter(request,"roomName");

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        response.sendRedirect("muc-room-summary.jsp");
        return;
    }

    // Handle a save
    Map errors = new HashMap();
    if (save) {
        // do validation
        if (roomName == null || roomName.contains("@")) {
            errors.put("roomName","roomName");
        }

        MUCRoom room = null;
        // If everything is ok so far then try to create a new room (with default configuration)
        if (errors.size() == 0) {
            // Check that the requested room ID is available
            room = webManager.getMultiUserChatServer().getChatRoom(roomName);
            if (room != null) {
                errors.put("room_already_exists", "room_already_exists");
            }
            else {
                // Try to create a new room
                JID address = new JID(webManager.getUser().getUsername(), webManager.getServerInfo().getName(), null);
                try {
                    room = webManager.getMultiUserChatServer().getChatRoom(roomName, address);
                    // Check if the room was created concurrently by another user
                    if (!room.getOwners().contains(address.toBareJID())) {
                        errors.put("room_already_exists", "room_already_exists");
                    }
                }
                catch (UnauthorizedException e) {
                    // This user is not allowed to create rooms
                    errors.put("not_enough_permissions", "not_enough_permissions");
                }
            }
        }

        if (errors.size() == 0) {
            // Creation good, so redirect
            response.sendRedirect("muc-room-edit-form.jsp?addsuccess=true&roomconfig_persistentroom=true&roomName=" + roomName);
            return;
        }
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Room Creation";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "muc-room-create.jsp"));
    pageinfo.setPageID("muc-room-create");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>Use the form below to create a new room. After the room has been created you will need to set the
room configuration to unlock the room.</p>

<%  if (errors.containsKey("room_already_exists") || errors.containsKey("not_enough_permissions")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <%  if (errors.containsKey("room_already_exists")) { %>

        Error creating the room. A room with the request ID already exists.

        <%  } else if (errors.containsKey("not_enough_permissions")) { %>

        Error creating the room. You do not have enough privileges to create rooms.

        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="muc-room-create.jsp">

<input type="hidden" name="save" value="true">

<table cellpadding="3" cellspacing="1" border="0" width="350">
<tr class="jive-even">
    <td width="70">
        Room ID:
    </td>
    <td><input type="text" name="roomName" value="<%= roomName != null ? roomName : ""%>">
    <%  if (errors.get("roomName") != null) { %>

        <span class="jive-error-text">
        Please enter a valid ID. Do not include the service name in the ID.
        </span>

    <%  } %>
    </td>
</tr>
</table>

<br>

<input type="submit" name="Submit" value="Create Room">
<input type="submit" name="cancel" value="Cancel">

</form>

<jsp:include page="bottom.jsp" flush="true" />