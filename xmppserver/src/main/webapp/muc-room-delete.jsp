<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            delete = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    JID roomJID = new JID(ParamUtils.getParameter(request,"roomJID"));
    String alternateJIDString = ParamUtils.getParameter(request,"alternateJID");
    JID alternateJID = null;
    if (alternateJIDString != null && alternateJIDString.trim().length() > 0 ) {
        // OF-526: Ignore invalid alternative JIDs.
        try {
            alternateJID = new JID(alternateJIDString.trim());
            if (alternateJID.getNode() == null) {
                alternateJID = null;
            }
        } catch (IllegalArgumentException ex) {
            alternateJID = null;
        }
    } else {
        alternateJID = null;
    }
    String reason = ParamUtils.getParameter(request,"reason");
    String roomName = roomJID.getNode();

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("muc-room-summary.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
        return;
    }

    // Load the room object
    MUCRoom room = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomName);

    // Handle a room delete:
    if (delete) {
        // Delete the room
        if (room != null) {
            // If the room still exists then destroy it
            room.destroyRoom(alternateJID, reason);
            // Log the event
            webManager.logEvent("destroyed MUC room "+roomName, "reason = "+reason+"\nalt jid = "+alternateJID);
        }
        // Done, so redirect
        response.sendRedirect("muc-room-summary.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8")+"&deletesuccess=true");
        return;
    }
%>

<html>
    <head>
        <title><fmt:message key="muc.room.delete.title"/></title>
        <meta name="subPageID" content="muc-room-delete"/>
        <meta name="extraParams" content="<%= "roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>"/>
        <meta name="helpPage" content="delete_a_group_chat_room.html"/>
    </head>
    <body>

<p>
<fmt:message key="muc.room.delete.info" />
<b><a href="muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"><%= StringUtils.escapeHTMLTags(room.getJID().toBareJID()) %></a></b>
<fmt:message key="muc.room.delete.detail" />
</p>

<form action="muc-room-delete.jsp">
    <input type="hidden" name="csrf" value="${csrf}">
<input type="hidden" name="roomJID" value="<%= StringUtils.escapeForXML(roomJID.toBareJID()) %>">

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
                <%= StringUtils.escapeHTMLTags(room.getJID().toBareJID()) %>
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

    </body>
</html>
