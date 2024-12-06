<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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
    boolean clear = request.getParameter("clear") != null;
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (clear) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            clear = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    JID roomJID = new JID(ParamUtils.getParameter(request,"roomJID"));
    String roomName = roomJID.getNode();

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("muc-room-edit-form.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
        return;
    }

    // Load the room object
    MUCRoom room = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomName);

    // Handle a room clear:
    if (clear) {
        // Clear the room
        if (room != null) {
            webManager.logEvent("Making a request to clear chat history of MUC room ", roomJID.toString());
            // finalWebManager is a final variable that references webManager
            // allowing webManager to be used inside the lambda expression without causing a compilation error
            final WebManager finalWebManager = webManager;
            room.clearChatHistory().thenRun(() -> {
                finalWebManager.logEvent("Cleared the chat history of MUC room ", roomJID.toString());
            });
        }
        // Done, so redirect to the room edit form
        response.sendRedirect("muc-room-edit-form.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8")+"&clearchatsuccess=true");
        return;
    }
%>

<html>
    <head>
        <title><fmt:message key="muc.room.clear_chat.title"/></title>
        <meta name="subPageID" content="muc-room-clear-chat"/>
        <meta name="extraParams" content="<%= "roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>"/>
    </head>
    <body>
        <p>
        <fmt:message key="muc.room.clear_chat.info" />
        <b><a href="muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"><%= StringUtils.escapeHTMLTags(room.getJID().toBareJID()) %></a></b>
        <fmt:message key="muc.room.clear_chat.detail" />
        </p>

        <form action="muc-room-clear-chat.jsp">
            <input type="hidden" name="csrf" value="${csrf}">
            <input type="hidden" name="roomJID" value="<%= StringUtils.escapeForXML(roomJID.toBareJID()) %>">

            <br>

            <input type="submit" name="clear" value="<fmt:message key="muc.room.clear_chat.clear_command" />">
            <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
        </form>
    </body>
</html>
