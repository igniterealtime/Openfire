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
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
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
    String title = LocaleUtils.getLocalizedString("muc.room.delete.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "muc-room-delete.jsp?roomName="+roomName));
    pageinfo.setSubPageID("muc-room-delete");
    pageinfo.setExtraParams("roomName="+roomName);
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
<fmt:message key="muc.room.delete.info" />
<b><a href="muc-room-edit-form.jsp?roomName=<%= room.getName() %>"><%= room.getName() %></a></b>
<fmt:message key="muc.room.delete.detail" />
</p>

<form action="muc-room-delete.jsp">
<input type="hidden" name="roomName" value="<%= roomName %>">

<fieldset>
    <legend><fmt:message key="muc.room.delete.destructon_title" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td class="c1">
                <fmt:message key="muc.room.delete.room_id" />
            </td>
            <td>
                <%= room.getName() %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="muc.room.delete.reason" />
            </td>
            <td>
                <input type="text" size="50" maxlength="150" name="reason">
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="muc.room.delete.alternate_address" />
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

<input type="submit" name="delete" value="<fmt:message key="muc.room.delete.destroy_room" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

<jsp:include page="bottom.jsp" flush="true" />
