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

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.CookieUtils,
                 java.util.*,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 org.xmpp.forms.*,
                 org.dom4j.Element,
                 org.xmpp.packet.IQ,
                 org.xmpp.packet.Message,
                 org.xmpp.packet.JID,
                 gnu.inet.encoding.Stringprep,
                 gnu.inet.encoding.StringprepException,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.muc.NotAllowedException"%>
<%@ page import="org.jivesoftware.openfire.muc.spi.MUCPersistenceManager" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="admin" prefix="admin" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters
    Map<String, String> errors = new HashMap<>();

    boolean create = ParamUtils.getBooleanParameter(request,"create");
    boolean save = ParamUtils.getBooleanParameter(request,"save");
    boolean success = ParamUtils.getBooleanParameter(request,"success");
    boolean addsuccess = ParamUtils.getBooleanParameter(request,"addsuccess");
    String roomName = ParamUtils.getParameter(request,"roomName");
    String mucName = ParamUtils.getParameter(request,"mucName");
    String roomJIDStr = ParamUtils.getParameter(request,"roomJID");
    JID roomJID = null;
    if (roomName != null && mucName != null) {
        try {
            JID.nodeprep( roomName );
        } catch ( IllegalArgumentException e ) {
            errors.put("roomName","roomName");
        }
        try {
            JID.domainprep( mucName );
        } catch ( IllegalArgumentException e ) {
            errors.put("mucName","mucName");
        }

        if ( errors.isEmpty() )
        {
            roomJID = new JID( roomName, mucName, null );
        }
    }
    else if (roomJIDStr != null) {
        try {
            roomJID = new JID( roomJIDStr );
        } catch ( IllegalArgumentException e ) {
            errors.put( "roomJID", "roomJID" );
        }
        if ( roomJID != null )
        {
            roomName = roomJID.getNode();
            mucName = roomJID.getDomain();
        }
    }
    String naturalName = ParamUtils.getParameter(request,"roomconfig_roomname");
    String description = ParamUtils.getParameter(request,"roomconfig_roomdesc");
    String maxUsers = ParamUtils.getParameter(request, "roomconfig_maxusers");
    boolean broadcastModerator = ParamUtils.getBooleanParameter(request, "roomconfig_presencebroadcast");
    boolean broadcastParticipant = ParamUtils.getBooleanParameter(request, "roomconfig_presencebroadcast2");
    boolean broadcastVisitor = ParamUtils.getBooleanParameter(request, "roomconfig_presencebroadcast3");
    String password = ParamUtils.getParameter(request, "roomconfig_roomsecret");
    String confirmPassword = ParamUtils.getParameter(request, "roomconfig_roomsecret2");
    String whois = ParamUtils.getParameter(request, "roomconfig_whois");
    String allowpm = ParamUtils.getParameter(request, "roomconfig_allowpm");
    boolean publicRoom = ParamUtils.getBooleanParameter(request, "roomconfig_publicroom");
    boolean persistentRoom = ParamUtils.getBooleanParameter(request, "roomconfig_persistentroom");
    boolean moderatedRoom = ParamUtils.getBooleanParameter(request, "roomconfig_moderatedroom");
    boolean membersOnly = ParamUtils.getBooleanParameter(request, "roomconfig_membersonly");
    boolean allowInvites = ParamUtils.getBooleanParameter(request, "roomconfig_allowinvites");
    boolean changeSubject = ParamUtils.getBooleanParameter(request, "roomconfig_changesubject");
    boolean enableLog = ParamUtils.getBooleanParameter(request, "roomconfig_enablelogging");
    boolean reservedNick = ParamUtils.getBooleanParameter(request, "roomconfig_reservednick");
    boolean canchangenick = ParamUtils.getBooleanParameter(request, "roomconfig_canchangenick");
    boolean registration = ParamUtils.getBooleanParameter(request, "roomconfig_registration");
    String roomSubject = ParamUtils.getParameter(request, "room_topic", true);

    if (webManager.getMultiUserChatManager().getMultiUserChatServicesCount() < 1) {
        // No services exist, so redirect to where one can configure the services
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        if (roomJID == null) {
            // case when canceling creating a new room
            response.sendRedirect("muc-room-summary.jsp");
        } else {
            // case when canceling a room edit, used on summary to set service
            response.sendRedirect("muc-room-summary.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
        }
        return;
    }

    // Load the room object
    MUCRoom room = null;
    if (!create) {
        room = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomName);

        if (room == null) {
            // The requested room name does not exist so return to the list of the existing rooms
            response.sendRedirect("muc-room-summary.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
            return;
        }
    }

    // Handle an save
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    if (save) {
        // do validation

        if (naturalName == null) {
            errors.put("roomconfig_roomname","roomconfig_roomname");
        }
        if (description == null) {
            errors.put("roomconfig_roomdesc","roomconfig_roomdesc");
        }
        if (maxUsers == null || maxUsers.isEmpty()) {
            maxUsers = "0"; // 0 indicates no limit.
        }
        try {
            Integer.parseInt(maxUsers);
        } catch ( NumberFormatException e ) {
            errors.put("roomconfig_maxusers", "roomconfig_maxusers");
        }
        if (password != null && !password.equals(confirmPassword)) {
            errors.put("roomconfig_roomsecret2","roomconfig_roomsecret2");
        }
        if (whois == null) {
            errors.put("roomconfig_whois","roomconfig_whois");
        }
        if ( allowpm == null || !( allowpm.equals( "anyone" ) || allowpm.equals( "moderators" ) || allowpm.equals( "participants" ) || allowpm.equals( "none" )) ) {
            errors.put("roomconfig_allowpm","romconfig_allowpm");
        }
        if (roomSubject != null && roomSubject.length() > 100) {
            errors.put("room_topic_longer","room_topic_longer");
        }

        if (create && errors.size() == 0) {
            if (roomName == null || roomName.contains("@")) {
                errors.put("roomName","roomName");
            }
            else {
                // Check that the room name is a valid node
                try {
                    roomName = Stringprep.nodeprep(roomName);
                }
                catch (StringprepException e) {
                    errors.put("roomName","roomName");
                }
            }

            if (errors.size() == 0) {
                // Check that the requested room ID is available
                room = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomName);
                if (room != null) {
                    errors.put("room_already_exists", "room_already_exists");
                }
                else {
                    // Try to create a new room
                    JID address = new JID(webManager.getUser().getUsername(), webManager.getServerInfo().getXMPPDomain(), null);
                    try {
                        room = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomName, address);
                        // Check if the room was created concurrently by another user
                        if (!room.getOwners().contains(address.asBareJID())) {
                            errors.put("room_already_exists", "room_already_exists");
                        }
                    }
                    catch (NotAllowedException e) {
                        // This user is not allowed to create rooms
                        errors.put("not_enough_permissions", "not_enough_permissions");
                    }
                }
            }
        }

        if (errors.size() == 0) {
            // Set the new configuration sending an IQ packet with an dataform
            final DataForm dataForm = new DataForm(DataForm.Type.submit);
            dataForm.addField("FORM_TYPE", null, FormField.Type.hidden).addValue("http://jabber.org/protocol/muc#roomconfig");
            dataForm.addField("muc#roomconfig_roomname", null, null).addValue(naturalName);
            dataForm.addField("muc#roomconfig_roomdesc", null, null).addValue(description);
            dataForm.addField("muc#roomconfig_changesubject", null, null).addValue(changeSubject ? "1": "0");
            dataForm.addField("muc#roomconfig_maxusers", null, null).addValue(maxUsers);

            final FormField broadcastField = dataForm.addField("muc#roomconfig_presencebroadcast", null, null);
            if (broadcastModerator) {
                broadcastField.addValue("moderator");
            }
            if (broadcastParticipant) {
                broadcastField.addValue("participant");
            }
            if (broadcastVisitor) {
                broadcastField.addValue("visitor");
            }

            dataForm.addField("muc#roomconfig_publicroom", null, null).addValue(publicRoom ? "1": "0");
            dataForm.addField("muc#roomconfig_persistentroom", null, null).addValue(persistentRoom ? "1": "0");
            dataForm.addField("muc#roomconfig_moderatedroom", null, null).addValue(moderatedRoom ? "1": "0");
            dataForm.addField("muc#roomconfig_membersonly", null, null).addValue(membersOnly ? "1": "0");
            dataForm.addField("muc#roomconfig_allowinvites", null, null).addValue(allowInvites ? "1": "0");
            dataForm.addField("muc#roomconfig_passwordprotectedroom", null, null).addValue((password == null) ? "0": "1");
            dataForm.addField("muc#roomconfig_roomsecret", null, null).addValue(password);
            dataForm.addField("muc#roomconfig_whois", null, null).addValue(whois);
            dataForm.addField("muc#roomconfig_allowpm", null, null).addValue(allowpm);
            dataForm.addField("muc#roomconfig_enablelogging", null, null).addValue(enableLog ? "1": "0");
            dataForm.addField("x-muc#roomconfig_reservednick", null, null).addValue(reservedNick ? "1": "0");
            dataForm.addField("x-muc#roomconfig_canchangenick", null, null).addValue(canchangenick ? "1": "0");
            dataForm.addField("x-muc#roomconfig_registration", null, null).addValue(registration ? "1": "0");

            final FormField roomAdminsField = dataForm.addField("muc#roomconfig_roomadmins", null, null);
            room.getAdmins().forEach( admin -> roomAdminsField.addValue( admin.toString() ));

            // Keep the existing list of owners
            final FormField roomOwnersField = dataForm.addField("muc#roomconfig_roomowners", null, null);
            room.getOwners().forEach( owner -> roomOwnersField.addValue( owner.toString() ));

            // update subject before sending IQ (to include subject with cluster update)
            if (roomSubject != null) {
                // Change the subject of the room by sending a new message
                Message message = new Message();
                message.setType(Message.Type.groupchat);
                message.setSubject(roomSubject);
                message.setFrom(room.getRole().getRoleAddress());
                message.setTo(room.getRole().getRoleAddress());
                room.changeSubject(message, room.getRole());
            }

            // Create an IQ packet and set the dataform as the main fragment
            IQ iq = new IQ(IQ.Type.set);
            Element element = iq.setChildElement("query", "http://jabber.org/protocol/muc#owner");
            element.add(dataForm.getElement());
            // Send the IQ packet that will modify the room's configuration
            room.getIQOwnerHandler().handleIQ(iq, room.getRole());

            // Changes good, so redirect
            String params;
            if (create) {
                params = "addsuccess=true&roomJID=" + URLEncoder.encode(roomJID.toBareJID(), "UTF-8");
                // Log the event
                webManager.logEvent("created new MUC room "+roomName, "subject = "+roomSubject+"\nroomdesc = "+description+"\nroomname = "+naturalName+"\nmaxusers = "+maxUsers);
            }
            else {
                params = "success=true&roomJID=" + URLEncoder.encode(roomJID.toBareJID(), "UTF-8");
                // Log the event
                webManager.logEvent("updated MUC room "+roomName, "subject = "+roomSubject+"\nroomdesc = "+description+"\nroomname = "+naturalName+"\nmaxusers = "+maxUsers);
            }
            response.sendRedirect("muc-room-edit-form.jsp?" + params);
            return;
        }
    }
    else {
        if (create) {
            // Before a selection for a service has been made (which is part of the room creation process in cases where
            // more than one service exists) it's impossible to predict what service-specific configuration to use. To prevent
            // having the user to go through a second step, we'll use the first available service. Given that having more than one
            // service is a very uncommon scenario, this is an acceptable shortcut.
            final String serviceName = webManager.getMultiUserChatManager().getMultiUserChatServices().iterator().next().getServiceName();
            maxUsers = MUCPersistenceManager.getProperty(serviceName, "room.maxUsers", "30");
            broadcastModerator = MUCPersistenceManager.getBooleanProperty(serviceName, "room.broadcastModerator", true);
            broadcastParticipant = MUCPersistenceManager.getBooleanProperty(serviceName, "room.broadcastParticipant", true);
            broadcastVisitor = MUCPersistenceManager.getBooleanProperty(serviceName, "room.broadcastVisitor", true);
            whois = MUCPersistenceManager.getBooleanProperty(serviceName, "room.canAnyoneDiscoverJID", true) ? "anyone" : "moderator";
            allowpm = MUCPersistenceManager.getProperty(serviceName, "room.allowpm", "anyone");
            publicRoom = MUCPersistenceManager.getBooleanProperty(serviceName, "room.publicRoom", true);
            persistentRoom = true; // Rooms created from the admin console are always persistent
            moderatedRoom = MUCPersistenceManager.getBooleanProperty(serviceName, "room.moderated", false);
            membersOnly = MUCPersistenceManager.getBooleanProperty(serviceName, "room.membersOnly", false);
            allowInvites = MUCPersistenceManager.getBooleanProperty(serviceName, "room.canOccupantsInvite", false);
            changeSubject = MUCPersistenceManager.getBooleanProperty(serviceName, "room.canOccupantsChangeSubject", false);
            enableLog = MUCPersistenceManager.getBooleanProperty(serviceName, "room.logEnabled", true);
            reservedNick = MUCPersistenceManager.getBooleanProperty(serviceName, "room.loginRestrictedToNickname", false);
            canchangenick = MUCPersistenceManager.getBooleanProperty(serviceName, "room.canChangeNickname", true);
            registration = MUCPersistenceManager.getBooleanProperty(serviceName, "room.registrationEnabled", true);
        }
        else {
            naturalName = room.getNaturalLanguageName();
            description = room.getDescription();
            roomSubject = room.getSubject();
            maxUsers = Integer.toString(room.getMaxUsers());
            broadcastModerator = room.canBroadcastPresence("moderator");
            broadcastParticipant = room.canBroadcastPresence("participant");
            broadcastVisitor = room.canBroadcastPresence("visitor");
            password = room.getPassword();
            confirmPassword = room.getPassword();
            whois = (room.canAnyoneDiscoverJID() ? "anyone" : "moderator");
            allowpm = room.canSendPrivateMessage();
            publicRoom = room.isPublicRoom();
            persistentRoom = room.isPersistent();
            moderatedRoom = room.isModerated();
            membersOnly = room.isMembersOnly();
            allowInvites = room.canOccupantsInvite();
            changeSubject = room.canOccupantsChangeSubject();
            enableLog = room.isLogEnabled();
            reservedNick = room.isLoginRestrictedToNickname();
            canchangenick = room.canChangeNickname();
            registration = room.isRegistrationEnabled();
        }
    }
    roomName = roomName == null ? "" : roomName;

    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("create", create);
    pageContext.setAttribute("success", success);
    pageContext.setAttribute("addsuccess", addsuccess);
    pageContext.setAttribute("room", room);
    pageContext.setAttribute("roomJID", roomJID);
    pageContext.setAttribute("roomJIDBare", roomJID != null ? roomJID.toBareJID() : null);
    pageContext.setAttribute("persistentRoom", persistentRoom);
    pageContext.setAttribute("roomName", roomName);
    pageContext.setAttribute("mucName", mucName);
    pageContext.setAttribute("naturalName", naturalName);
    pageContext.setAttribute("description", description);
    pageContext.setAttribute("roomSubject", roomSubject);
    pageContext.setAttribute("maxUser", maxUsers);
    pageContext.setAttribute("broadcastModerator", broadcastModerator);
    pageContext.setAttribute("broadcastParticipant", broadcastParticipant);
    pageContext.setAttribute("broadcastVisitor", broadcastVisitor);
    pageContext.setAttribute("password", password);
    pageContext.setAttribute("confirmPassword", confirmPassword);
    pageContext.setAttribute("whois", whois);
    pageContext.setAttribute("allowpm", allowpm);
    pageContext.setAttribute("publicRoom", publicRoom);
    pageContext.setAttribute("moderatedRoom", moderatedRoom);
    pageContext.setAttribute("membersonly", membersOnly);
    pageContext.setAttribute("allowInvites", allowInvites);
    pageContext.setAttribute("changeSubject", changeSubject);
    pageContext.setAttribute("reservedNick", reservedNick);
    pageContext.setAttribute("canchangenick", canchangenick);
    pageContext.setAttribute("registration", registration);
    pageContext.setAttribute("enableLog", enableLog);

%>

<html>
    <head>
        <c:choose>
            <c:when test="${create}">
                <title><fmt:message key="muc.room.edit.form.create.title"/></title>
                <meta name="pageID" content="muc-room-create"/>
            </c:when>
            <c:otherwise>
                <title><fmt:message key="muc.room.edit.form.edit.title"/></title>
                <meta name="subPageID" content="muc-room-edit-form"/>
            </c:otherwise>
        </c:choose>

        <meta name="extraParams" content="<%= "roomJID="+(roomJID != null ? URLEncoder.encode(roomJID.toBareJID(), "UTF-8") : "")+"&create="+create %>"/>
        <meta name="helpPage" content="view_group_chat_room_summary.html"/>
    </head>
    <body>

    <c:choose>
        <c:when test="${not empty errors}">
            <c:forEach var="err" items="${errors}">
                <admin:infobox type="error">
                    <c:choose>
                        <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                        <c:when test="${err.key eq 'roomconfig_roomname'}"><fmt:message key="muc.room.edit.form.valid_hint_name" /></c:when>
                        <c:when test="${err.key eq 'roomconfig_roomdesc'}"><fmt:message key="muc.room.edit.form.valid_hint_description" /></c:when>
                        <c:when test="${err.key eq 'roomconfig_maxusers'}"><fmt:message key="muc.room.edit.form.valid_hint_max_room" /></c:when>
                        <c:when test="${err.key eq 'roomconfig_roomsecret2'}"><fmt:message key="muc.room.edit.form.new_password" /></c:when>
                        <c:when test="${err.key eq 'roomconfig_whois'}"><fmt:message key="muc.room.edit.form.role" /></c:when>
                        <c:when test="${err.key eq 'roomconfig_allowpm'}"><fmt:message key="muc.room.edit.form.role" /></c:when>
                        <c:when test="${err.key eq 'roomName'}"><fmt:message key="muc.room.edit.form.valid_hint" /></c:when>
                        <c:when test="${err.key eq 'room_already_exists'}"><fmt:message key="muc.room.edit.form.error_created_id" /></c:when>
                        <c:when test="${err.key eq 'not_enough_permissions'}"><fmt:message key="muc.room.edit.form.error_created_privileges" /></c:when>
                        <c:when test="${err.key eq 'room_topic'}"><fmt:message key="muc.room.edit.form.valid_hint_subject" /></c:when>
                        <c:when test="${err.key eq 'room_topic_longer'}"><fmt:message key="muc.room.edit.form.valid_hint_subject_too_long" /></c:when>
                        <c:otherwise>
                            <c:if test="${not empty err.value}">
                                <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                            </c:if>
                            (<c:out value="${err.key}"/>)
                        </c:otherwise>
                    </c:choose>
                </admin:infobox>
            </c:forEach>
        </c:when>
        <c:when test="${success}">
            <admin:infobox type="success">
                <fmt:message key="muc.room.edit.form.edited" />
            </admin:infobox>
        </c:when>
        <c:when test="${addsuccess}">
            <admin:infobox type="success">
                <fmt:message key="muc.room.edit.form.created" />
            </admin:infobox>
        </c:when>
    </c:choose>

    <c:choose>
        <c:when test="${not create}">
            <p>
                <fmt:message key="muc.room.edit.form.info" />
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
                        <td>
                            <c:out value="${room.name}"/>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${room.occupantsCount eq 0}">
                                    <c:out value="${room.occupantsCount}"/>
                                    <c:if test="${room.maxUsers gt 0}">
                                        / <c:out value="${room.maxUsers}"/>
                                    </c:if>
                                </c:when>
                                <c:otherwise>
                                    <c:url var="mucroomoccupantslink" value="muc-room-occupants.jsp">
                                        <c:param name="roomJID" value="${roomJIDBare}"/>
                                    </c:url>
                                    <a href="${mucroomoccupantslink}">
                                        <c:out value="${room.occupantsCount}"/>
                                        <c:if test="${room.maxUsers gt 0}">
                                            / <c:out value="${room.maxUsers}"/>
                                        </c:if>
                                    </a>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <fmt:formatDate value="${room.creationDate}" dateStyle="medium" timeStyle="short"/>
                        </td>
                        <td>
                            <fmt:formatDate value="${room.modificationDate}" dateStyle="medium" timeStyle="short"/>
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>
            <br>
            <p><fmt:message key="muc.room.edit.form.change_room" /></p>
        </c:when>
        <c:otherwise>
            <p><fmt:message key="muc.room.edit.form.persistent_room" /></p>
        </c:otherwise>
    </c:choose>

    <form action="muc-room-edit-form.jsp">
        <c:if test="${not create}">
            <input type="hidden" name="roomJID" value="${fn:escapeXml(roomJIDBare)}">
        </c:if>
        <input type="hidden" name="csrf" value="${csrf}">
        <input type="hidden" name="save" value="true">
        <input type="hidden" name="create" value="${create}">
        <input type="hidden" name="roomconfig_persistentroom" value="${persistentRoom}">

    <table width="100%" border="0">
        <tr>
            <td width="70%">
                <table width="100%" border="0">
                <tbody>
                    <tr>
                        <c:choose>
                            <c:when test="${create}">
                                <td><label for="roomName"><fmt:message key="muc.room.edit.form.room_id" /></label>: *</td>
                                <td><input type="text" name="roomName" id="roomName" value="${fn:escapeXml(roomName)}">
                                    <c:choose>
                                        <c:when test="${webManager.multiUserChatManager.multiUserChatServicesCount gt 1}">
                                            @<select name="mucName">
                                            <c:forEach var="service" items="${webManager.multiUserChatManager.multiUserChatServices}">
                                                <c:if test="${not service.hidden}">
                                                    <option value="${fn:escapeXml(service.serviceDomain)}" ${service.serviceDomain eq mucName ? "selected='selected'" : ""}>
                                                        <c:out value="${service.serviceDomain}"/>
                                                    </option>
                                                </c:if>
                                            </c:forEach>
                                            </select>
                                        </c:when>
                                        <c:otherwise>
                                            <c:forEach var="service" items="${webManager.multiUserChatManager.multiUserChatServices}">
                                                <c:if test="${not service.hidden}">
                                                    <input type="hidden" name="mucName" value="${fn:escapeXml(service.serviceDomain)}"/>
                                                    @<c:out value="${service.serviceDomain}"/>
                                                </c:if>
                                            </c:forEach>
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                            </c:when>
                            <c:otherwise>
                                <td><fmt:message key="muc.room.edit.form.service" />:</td>
                                <td><c:out value="${roomJID.domain}"/></td>
                            </c:otherwise>
                        </c:choose>
                    </tr>
                    <tr>
                        <td><label for="roomconfig_roomname"><fmt:message key="muc.room.edit.form.room_name" /></label>: *</td>
                        <td><input type="text" name="roomconfig_roomname" id="roomconfig_roomname" value="${empty naturalName ? "" : fn:escapeXml(naturalName)}"></td>
                    </tr>
                    <tr>
                        <td><label for="roomconfig_roomdesc"><fmt:message key="muc.room.edit.form.description" /></label>:  *</td>
                        <td><input name="roomconfig_roomdesc" id="roomconfig_roomdesc" value="${empty description ? "" : fn:escapeXml(description)}" type="text" size="40"></td>
                    </tr>
                    <tr>
                        <td><label for="room_topic"><fmt:message key="muc.room.edit.form.topic" /></label>:</td>
                        <td><input name="room_topic" id="room_topic" value="${empty roomSubject ? "" : fn:escapeXml(roomSubject)}" type="text" size="40"></td>
                    </tr>
                    <tr>
                        <td><label for="roomconfig_maxusers"><fmt:message key="muc.room.edit.form.max_room" /></label>:</td>
                        <td><input type="number" name="roomconfig_maxusers" id="roomconfig_maxusers" min="1" value="${empty maxUser or maxUser eq '0' ? "" : fn:escapeXml(maxUser)}" size="5">
                            <fmt:message key="muc.room.edit.form.empty_nolimit" />
                        </td>
                    </tr>
                    <tr>
                        <td valign="top"><fmt:message key="muc.room.edit.form.broadcast" />:</td>
                        <td>
                            <fieldset>
                                <input name="roomconfig_presencebroadcast" type="checkbox" value="true" id="moderator" ${broadcastModerator ? 'checked' : ''}>
                                <label for="moderator"><fmt:message key="muc.room.edit.form.moderator" /></label>
                                <input name="roomconfig_presencebroadcast2" type="checkbox" value="true" id="participant" ${broadcastParticipant ? 'checked' : ''}>
                                <label for="participant"><fmt:message key="muc.room.edit.form.participant" /></label>
                                <input name="roomconfig_presencebroadcast3" type="checkbox" value="true" id="visitor" ${broadcastVisitor ? 'checked' : ''}>
                                <label for="visitor"><fmt:message key="muc.room.edit.form.visitor" /></label>
                            </fieldset>
                        </td>
                    </tr>
                    <tr>
                        <td><label for="roomconfig_roomsecret"><fmt:message key="muc.room.edit.form.required_password" /></label>:</td>
                        <td><input type="password" name="roomconfig_roomsecret" id="roomconfig_roomsecret" <c:if test="${not empty password}">value="${fn:escapeXml(password)}"</c:if>></td>
                    </tr>
                    <tr>
                        <td><label for="roomconfig_roomsecret2"><fmt:message key="muc.room.edit.form.confirm_password" /></label>:</td>
                        <td><input type="password" name="roomconfig_roomsecret2" id="roomconfig_roomsecret2" <c:if test="${not empty confirmPassword}">value="${fn:escapeXml(confirmPassword)}"</c:if>></td>
                    </tr>
                    <tr>
                        <td><label for="roomconfig_whois"><fmt:message key="muc.room.edit.form.discover_jid" /></label>:</td>
                        <td>
                            <select name="roomconfig_whois" id="roomconfig_whois">
                                <option value="moderator" ${whois eq 'moderator' ? 'selected' : ''}><fmt:message key="muc.room.edit.form.moderator" /></option>
                                <option value="anyone" ${whois eq 'anyone' ? 'selected' : ''}><fmt:message key="muc.room.edit.form.anyone" /></option>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td><label for="roomconfig_allowpm"><fmt:message key="muc.room.edit.form.allowpm" /></label>:</td>
                        <td>
                            <select name="roomconfig_allowpm" id="roomconfig_allowpm">
                                <option value="none" ${allowpm eq 'none' ? 'selected' : ''}><fmt:message key="muc.form.conf.none" /></option>
                                <option value="moderators" ${allowpm eq 'moderators' ? 'selected' : ''}><fmt:message key="muc.room.edit.form.moderator" /></option>
                                <option value="participants" ${allowpm eq 'participants' ? 'selected' : ''}><fmt:message key="muc.room.edit.form.participant" /></option>
                                <option value="anyone" ${allowpm eq 'anyone' ? 'selected' : ''}><fmt:message key="muc.room.edit.form.anyone" /></option>
                            </select>
                        </td>
                    </tr>
                </tbody>
                </table>
            </td>
            <td width="30%" valign="top">
                <fieldset>
                    <legend><fmt:message key="muc.room.edit.form.room_options" /></legend>
                    <table width="100%"  border="0">
                    <tbody>
                        <tr>
                            <td><input type="checkbox" name="roomconfig_publicroom" value="true" id="public" ${publicRoom ? 'checked' : ''}>
                                <label for="public"><fmt:message key="muc.room.edit.form.list_room" /></label></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="roomconfig_moderatedroom" value="true" id="moderated" ${moderatedRoom ? 'checked' : ''}>
                                <label for="moderated"><fmt:message key="muc.room.edit.form.room_moderated" /></label></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="roomconfig_membersonly" value="true" id="membersonly" ${membersonly ? 'checked' : ''}>
                                <label for="membersonly"><fmt:message key="muc.room.edit.form.moderated_member_only" /></label></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="roomconfig_allowinvites" value="true" id="allowinvites" ${allowInvites ? 'checked' : ''}>
                                <label for="allowinvites"><fmt:message key="muc.room.edit.form.invite_other" /></label></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="roomconfig_changesubject" value="true" id="changesubject" ${changeSubject ? 'checked' : ''}>
                                <label for="changesubject"><fmt:message key="muc.room.edit.form.change_subject" /></label></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="roomconfig_reservednick" value="true" id="reservednick"${reservedNick ? 'checked' : ''}>
                                <label for="reservednick"><fmt:message key="muc.room.edit.form.reservednick" /></label></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="roomconfig_canchangenick" value="true" id="canchangenick" ${canchangenick ? 'checked' : ''}>
                                <label for="canchangenick"><fmt:message key="muc.room.edit.form.canchangenick" /></label></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="roomconfig_registration" value="true" id="registration" ${registration ? 'checked' : ''}>
                                <label for="registration"><fmt:message key="muc.room.edit.form.registration" /></label></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="roomconfig_enablelogging" value="true" id="enablelogging" ${enableLog ? 'checked' : ''}>
                                <label for="enablelogging"><fmt:message key="muc.room.edit.form.log" /></label></td>
                        </tr>
                    </tbody>
                    </table>
                </fieldset>
            </td>
        </tr>
        <tr align="center">
            <td colspan="2"><input type="submit" name="Submit" value="<fmt:message key="global.save_changes" />">
            <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />"></td>
        </tr>
    </table>
    <span class="jive-description">* <fmt:message key="muc.room.edit.form.required_field" /> </span>
</form>

    </body>
</html>
