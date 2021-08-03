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
                 java.util.*,
                 org.jivesoftware.openfire.muc.MultiUserChatService"
    errorPage="error.jsp"
%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.time.Duration" %>
<%@ page import="org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="org.jivesoftware.openfire.muc.MUCUser" %>
<%@ page import="org.jivesoftware.util.cache.Cache" %>
<%@ page import="org.jivesoftware.openfire.muc.MUCRoom" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="org.jivesoftware.openfire.muc.MUCRole" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    String mucName = ParamUtils.getParameter(request,"mucname");

    if (!webManager.getMultiUserChatManager().isServiceRegistered(mucName)) {
        // The requested service name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Get muc server
    MultiUserChatService mucServiceIfc = webManager.getMultiUserChatManager().getMultiUserChatService(mucName);
    if (mucServiceIfc == null || !(mucServiceIfc instanceof MultiUserChatServiceImpl)) {
        // The requested service is not of an inspectable type, so return to the list of the existing rooms
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }
    MultiUserChatServiceImpl mucService = MultiUserChatServiceImpl.class.cast(mucServiceIfc);
    final Cache<String, MUCRoom> roomsClustered = mucService.getLocalMUCRoomManager().getROOM_CACHE();
    final Map<String, MUCRoom> roomsLocal = mucService.getLocalMUCRoomManager().getLocalRooms();
    final Map<String, MUCRoom> allRooms = new HashMap<>();
    allRooms.putAll(roomsClustered);
    allRooms.putAll(roomsLocal);
    final List<String> allRoomNames = new ArrayList<>(allRooms.keySet());
    Collections.sort(allRoomNames);

    final Set<String> roomsInBothCaches = roomsClustered.keySet().stream().filter(jid -> roomsLocal.containsKey(jid)).collect(Collectors.toSet());
    final Set<String> roomsOnlyInClusteredCache = roomsClustered.keySet().stream().filter(jid -> !roomsLocal.containsKey(jid)).collect(Collectors.toSet());
    final Set<String> roomsOnlyInLocalCache = roomsLocal.keySet().stream().filter(jid -> !roomsClustered.containsKey(jid)).collect(Collectors.toSet());

    pageContext.setAttribute("mucName", mucName);
    pageContext.setAttribute("mucService", mucService);
    pageContext.setAttribute("roomsClustered", roomsClustered);
    pageContext.setAttribute("roomsLocal", roomsLocal);
    pageContext.setAttribute("roomsInBothCaches", roomsInBothCaches);
    pageContext.setAttribute("roomsOnlyInClusteredCache", roomsOnlyInClusteredCache);
    pageContext.setAttribute("roomsOnlyInLocalCache", roomsOnlyInLocalCache);
%>

<html>
<head>
<title><fmt:message key="muc.room_cache.title"/></title>
<meta name="subPageID" content="muc-room-cache"/>
<meta name="extraParams" content="<%= "mucName=" + URLEncoder.encode(mucName, "UTF-8") %>"/>
<meta name="helpPage" content="edit_idle_user_settings.html"/>
</head>
<body>

<p>
<fmt:message key="muc.room_cache.info" />
</p>

<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
        <tr>
            <th nowrap><fmt:message key="muc.room_cache.room_name" /></th>
            <th nowrap></th>
            <th nowrap><fmt:message key="muc.room_cache.local_cache" /></th>
            <th nowrap><fmt:message key="muc.room_cache.shared_cache" /></th>
            <th nowrap><fmt:message key="muc.room_cache.room_details" /></th>
        </tr>
        </thead>
        <tbody>

        <%
            if (allRooms.isEmpty()) {
        %>
        <tr>
            <td align="center" colspan="7">
                <fmt:message key="muc.service.summary.no_services" />
            </td>
        </tr>

        <%
            }
            int i = 0;
            for (String roomName : allRoomNames) {
                MUCRoom localRoom = roomsLocal.get(roomName);
                MUCRoom clusteredRoom = roomsClustered.get(roomName);
                i++;
        %>
        <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
            <td width="10%">
                <%= roomName %>
            </td>
            <td width="15%" align="right">
                <fmt:message key="muc.room_cache.description" /><br/>
                <fmt:message key="muc.room_cache.topic" /><br/>
                <fmt:message key="muc.room_cache.occupants" /><br/>
                <fmt:message key="muc.room_cache.moderators" /><br/>
                <fmt:message key="muc.room_cache.owners" /><br/>
                <fmt:message key="muc.room_cache.admins" /><br/>
                <fmt:message key="muc.room_cache.members" /><br/>
            </td>
            <td width="25%">
                <%
                    if (localRoom != null) {
                        Set<String> occupants = localRoom.getOccupants().stream().map(role -> role.getNickname() + ":" + role.getUserAddress().toFullJID()).collect(Collectors.toSet());
                        String occupantsDump = String.join("\n", occupants);
                        Set<String> moderators = localRoom.getModerators().stream().map(role -> role.getNickname() + ":" + role.getUserAddress().toFullJID()).collect(Collectors.toSet());
                        String moderatorsDump = String.join("\n", moderators);
                        Set<String> owners = localRoom.getOwners().stream().map(JID::toBareJID).collect(Collectors.toSet());
                        String ownersDump = String.join("\n", owners);
                        Set<String> admins = localRoom.getAdmins().stream().map(JID::toBareJID).collect(Collectors.toSet());
                        String adminsDump = String.join("\n", admins);
                        Set<String> members = localRoom.getMembers().stream().map(JID::toBareJID).collect(Collectors.toSet());
                        String membersDump = String.join("\n", members);
                %>
                    <%= localRoom.getDescription() %><br/>
                    <%= localRoom.getSubject() %><br/>
                    <span title="<%=occupantsDump%>"><%= occupants.size() %></span><br/>
                    <span title="<%=moderatorsDump%>"><%= moderators.size() %></span><br/>
                    <span title="<%=ownersDump%>"><%= owners.size() %></span><br/>
                    <span title="<%=adminsDump%>"><%= admins.size() %></span><br/>
                    <span title="<%=membersDump%>"><%= members.size() %></span><br/>
                <%
                    }
                %>
            </td>
            <td width="25%" class="jive-icon">
                <%
                    if (clusteredRoom != null) {
                        Set<String> occupants = clusteredRoom.getOccupants().stream().map(role -> role.getNickname() + ":" + role.getUserAddress().toFullJID()).collect(Collectors.toSet());
                        String occupantsDump = String.join("\n", occupants);
                        Set<String> moderators = clusteredRoom.getModerators().stream().map(role -> role.getNickname() + ":" + role.getUserAddress().toFullJID()).collect(Collectors.toSet());
                        String moderatorsDump = String.join("\n", moderators);
                        Set<String> owners = clusteredRoom.getOwners().stream().map(JID::toBareJID).collect(Collectors.toSet());
                        String ownersDump = String.join("\n", owners);
                        Set<String> admins = clusteredRoom.getAdmins().stream().map(JID::toBareJID).collect(Collectors.toSet());
                        String adminsDump = String.join("\n", admins);
                        Set<String> members = clusteredRoom.getMembers().stream().map(JID::toBareJID).collect(Collectors.toSet());
                        String membersDump = String.join("\n", members);
                %>
                    <%= clusteredRoom.getDescription() %><br/>
                    <%= clusteredRoom.getSubject() %><br/>
                    <span title="<%=occupantsDump%>"><%= occupants.size() %></span><br/>
                    <span title="<%=moderatorsDump%>"><%= moderators.size() %></span><br/>
                    <span title="<%=ownersDump%>"><%= owners.size() %></span><br/>
                    <span title="<%=adminsDump%>"><%= admins.size() %></span><br/>
                    <span title="<%=membersDump%>"><%= members.size() %></span><br/>
                <%
                    }
                %>
            </td>
            <td width="25%">
            </td>
        </tr>
        <%
            }
        %>
        </tbody>
    </table>
</div>

<br>
</body>
</html>
