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
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.muc.MUCRoom"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String roomName = ParamUtils.getParameter(request,"roomName");
    String alternateJID = ParamUtils.getParameter(request,"alternateJID");
    String reason = ParamUtils.getParameter(request,"reason");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("muc-room-summary.jsp");
        return;
    }

    // Load the room object
    MUCRoom room = webManager.getMultiUserChatServer().getChatRoom(roomName);

    // Handle a room delete:
    if (delete) {
        // Delete the room
        if (room !=  null) {
            // If the room still exists then destroy it
            room.destroyRoom(alternateJID, reason);
        }
        // Done, so redirect
        response.sendRedirect("muc-room-summary.jsp?deletesuccess=true");
        return;
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Destroy Room";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "muc-room-delete.jsp?roomName="+roomName));
    pageinfo.setSubPageID("muc-room-delete");
    pageinfo.setExtraParams("roomName="+roomName);
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Are you sure you want to destroy the room
<b><a href="muc-room-edit-form.jsp?roomName=<%= room.getName() %>"><%= room.getName() %></a></b>
from the system? You may specify a reason for the room destruction and an alternative room
address that will replace this room. This information will be sent to room occupants.
</p>

<form action="muc-room-delete.jsp">
<input type="hidden" name="roomName" value="<%= roomName %>">

<fieldset>
    <legend>Destruction Details</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td class="c1">
                Room ID:
            </td>
            <td>
                <%= room.getName() %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                Reason:
            </td>
            <td>
                <input type="text" size="50" maxlength="150" name="reason">
            </td>
        </tr>
        <tr>
            <td class="c1">
                Alternate Room Address:
            </td>
            <td>
                <input type="text" size="30" maxlength="150" name="alternateJID">
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="delete" value="Destroy Room">
<input type="submit" name="cancel" value="Cancel">
</form>

<jsp:include page="bottom.jsp" flush="true" />
