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
                 java.text.DateFormat,
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
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>
<%@ page import="org.jivesoftware.openfire.muc.spi.MUCPersistenceManager" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
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
    String broadcastModerator = ParamUtils.getParameter(request, "roomconfig_presencebroadcast");
    String broadcastParticipant = ParamUtils.getParameter(request, "roomconfig_presencebroadcast2");
    String broadcastVisitor = ParamUtils.getParameter(request, "roomconfig_presencebroadcast3");
    String password = ParamUtils.getParameter(request, "roomconfig_roomsecret");
    String confirmPassword = ParamUtils.getParameter(request, "roomconfig_roomsecret2");
    String whois = ParamUtils.getParameter(request, "roomconfig_whois");
    String allowpm = ParamUtils.getParameter(request, "roomconfig_allowpm");
    String publicRoom = ParamUtils.getParameter(request, "roomconfig_publicroom");
    String persistentRoom = ParamUtils.getParameter(request, "roomconfig_persistentroom");
    String moderatedRoom = ParamUtils.getParameter(request, "roomconfig_moderatedroom");
    String membersOnly = ParamUtils.getParameter(request, "roomconfig_membersonly");
    String allowInvites = ParamUtils.getParameter(request, "roomconfig_allowinvites");
    String changeSubject = ParamUtils.getParameter(request, "roomconfig_changesubject");
    String enableLog = ParamUtils.getParameter(request, "roomconfig_enablelogging");
    String reservedNick = ParamUtils.getParameter(request, "roomconfig_reservednick");
    String canChangeNick = ParamUtils.getParameter(request, "roomconfig_canchangenick");
    String registrationEnabled = ParamUtils.getParameter(request, "roomconfig_registration");
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
            dataForm.addField(null, null, FormField.Type.hidden).addValue("http://jabber.org/protocol/muc#roomconfig");
            dataForm.addField("muc#roomconfig_roomname", null, null).addValue(naturalName);
            dataForm.addField("muc#roomconfig_roomdesc", null, null).addValue(description);
            dataForm.addField("muc#roomconfig_changesubject", null, null).addValue((changeSubject == null) ? "0": "1");
            dataForm.addField("muc#roomconfig_maxusers", null, null).addValue(maxUsers);

            final FormField broadcastField = dataForm.addField("muc#roomconfig_presencebroadcast", null, null);
            if (broadcastModerator != null) {
                broadcastField.addValue("moderator");
            }
            if (broadcastParticipant != null) {
                broadcastField.addValue("participant");
            }
            if (broadcastVisitor != null) {
                broadcastField.addValue("visitor");
            }

            dataForm.addField("muc#roomconfig_publicroom", null, null).addValue((publicRoom == null) ? "0": "1");
            dataForm.addField("muc#roomconfig_persistentroom", null, null).addValue((persistentRoom == null) ? "0": "1");
            dataForm.addField("muc#roomconfig_moderatedroom", null, null).addValue((moderatedRoom == null) ? "0": "1");
            dataForm.addField("muc#roomconfig_membersonly", null, null).addValue((membersOnly == null) ? "0": "1");
            dataForm.addField("muc#roomconfig_allowinvites", null, null).addValue((allowInvites == null) ? "0": "1");
            dataForm.addField("muc#roomconfig_passwordprotectedroom", null, null).addValue((password == null) ? "0": "1");
            dataForm.addField("muc#roomconfig_roomsecret", null, null).addValue(password);
            dataForm.addField("muc#roomconfig_whois", null, null).addValue(whois);
            dataForm.addField("muc#roomconfig_allowpm", null, null).addValue(allowpm);
            dataForm.addField("muc#roomconfig_enablelogging", null, null).addValue((enableLog == null) ? "0": "1");
            dataForm.addField("x-muc#roomconfig_reservednick", null, null).addValue((reservedNick == null) ? "0": "1");
            dataForm.addField("x-muc#roomconfig_canchangenick", null, null).addValue((canChangeNick == null) ? "0": "1");
            dataForm.addField("x-muc#roomconfig_registration", null, null).addValue((registrationEnabled == null) ? "0": "1");

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
            broadcastModerator = MUCPersistenceManager.getProperty(serviceName, "room.broadcastModerator", "true");
            broadcastParticipant = MUCPersistenceManager.getProperty(serviceName, "room.broadcastParticipant", "true");
            broadcastVisitor = MUCPersistenceManager.getProperty(serviceName, "room.broadcastVisitor", "true");
            whois = MUCPersistenceManager.getBooleanProperty(serviceName, "room.canAnyoneDiscoverJID", true) ? "anyone" : "moderator";
            allowpm = MUCPersistenceManager.getProperty(serviceName, "room.allowpm", "anyone");
            publicRoom = MUCPersistenceManager.getProperty(serviceName, "room.publicRoom", "true");
            persistentRoom = "true"; // Rooms created from the admin console are always persistent
            moderatedRoom = MUCPersistenceManager.getProperty(serviceName, "room.moderated", "false");
            membersOnly = MUCPersistenceManager.getProperty(serviceName, "room.membersOnly", "false");
            allowInvites = MUCPersistenceManager.getProperty(serviceName, "room.canOccupantsInvite", "false");
            changeSubject = MUCPersistenceManager.getProperty(serviceName, "room.canOccupantsChangeSubject", "false");
            enableLog = MUCPersistenceManager.getProperty(serviceName, "room.logEnabled", "true");
            reservedNick = MUCPersistenceManager.getProperty(serviceName, "room.loginRestrictedToNickname", "false");
            canChangeNick = MUCPersistenceManager.getProperty(serviceName, "room.canChangeNickname", "true");
            registrationEnabled = MUCPersistenceManager.getProperty(serviceName, "room.registrationEnabled", "true");
        }
        else {
            naturalName = room.getNaturalLanguageName();
            description = room.getDescription();
            roomSubject = room.getSubject();
            maxUsers = Integer.toString(room.getMaxUsers());
            broadcastModerator = Boolean.toString(room.canBroadcastPresence("moderator"));
            broadcastParticipant = Boolean.toString(room.canBroadcastPresence("participant"));
            broadcastVisitor = Boolean.toString(room.canBroadcastPresence("visitor"));
            password = room.getPassword();
            confirmPassword = room.getPassword();
            whois = (room.canAnyoneDiscoverJID() ? "anyone" : "moderator");
            allowpm = room.canSendPrivateMessage();
            publicRoom = Boolean.toString(room.isPublicRoom());
            persistentRoom = Boolean.toString(room.isPersistent());
            moderatedRoom = Boolean.toString(room.isModerated());
            membersOnly = Boolean.toString(room.isMembersOnly());
            allowInvites = Boolean.toString(room.canOccupantsInvite());
            changeSubject = Boolean.toString(room.canOccupantsChangeSubject());
            enableLog = Boolean.toString(room.isLogEnabled());
            reservedNick = Boolean.toString(room.isLoginRestrictedToNickname());
            canChangeNick = Boolean.toString(room.canChangeNickname());
            registrationEnabled = Boolean.toString(room.isRegistrationEnabled());
        }
    }
    // Formatter for dates
    DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    roomName = roomName == null ? "" : roomName;
%>

<html>
<head>
<% if (create) { %>
<title><fmt:message key="muc.room.edit.form.create.title"/></title>
<meta name="pageID" content="muc-room-create"/>
<% } else { %>
<title><fmt:message key="muc.room.edit.form.edit.title"/></title>
<meta name="subPageID" content="muc-room-edit-form"/>
<% } %>
<meta name="extraParams" content="<%= "roomJID="+(roomJID != null ? URLEncoder.encode(roomJID.toBareJID(), "UTF-8") : "")+"&create="+create %>"/>
<meta name="helpPage" content="view_group_chat_room_summary.html"/>
</head>
<body>

<%  if (!errors.isEmpty()) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""/></td>
            <td class="jive-icon-label">

            <% if (errors.get("csrf") != null) { %>
                <fmt:message key="global.csrf.failed" />
            <% } if (errors.get("roomconfig_roomname") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint_name" />
            <% } if (errors.get("roomconfig_roomdesc") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint_description" />
            <% } if (errors.get("roomconfig_maxusers") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint_max_room" />
            <% } if (errors.get("roomconfig_roomsecret2") != null) { %>
                <fmt:message key="muc.room.edit.form.new_password" />
            <% } if (errors.get("roomconfig_whois") != null) { %>
                <fmt:message key="muc.room.edit.form.role" />
            <% } if (errors.get("roomconfig_allowpm") != null) { %>
                <fmt:message key="muc.room.edit.form.role" />
            <% } if (errors.get("roomName") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint" />
            <% } if (errors.get("room_already_exists") != null) { %>
                <fmt:message key="muc.room.edit.form.error_created_id" />
            <% } if (errors.get("not_enough_permissions") != null) { %>
                <fmt:message key="muc.room.edit.form.error_created_privileges" />
            <% } if (errors.get("room_topic") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint_subject" />
            <% } if (errors.get("room_topic_longer") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint_subject_too_long" />
            <% } %>
            </td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (success || addsuccess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <%  if (success) { %>

        <fmt:message key="muc.room.edit.form.edited" />

        <%  } else if (addsuccess) { %>

        <fmt:message key="muc.room.edit.form.created" />

        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<%  if (!create) { %>
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
            <td><%= StringUtils.escapeHTMLTags(room.getName()) %></td>
            <td><% if (room.getOccupantsCount() == 0) { %>
                    <%= room.getOccupantsCount() %>
                    <% if (room.getMaxUsers() > 0 ) { %>
                        / <%= room.getMaxUsers() %>
                    <% } %>
                <% } else { %>
                    <a href="muc-room-occupants.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8")%>"><%= room.getOccupantsCount() %>
                    <% if (room.getMaxUsers() > 0 ) { %>
                        / <%= room.getMaxUsers() %>
                    <% } %>
                    </a>
                <% } %>
            </td>
            <td><%= dateFormatter.format(room.getCreationDate()) %></td>
            <td><%= dateFormatter.format(room.getModificationDate()) %></td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>
    <p><fmt:message key="muc.room.edit.form.change_room" /></p>
<%  } else { %>
    <p><fmt:message key="muc.room.edit.form.persistent_room" /></p>
<%  } %>
<form action="muc-room-edit-form.jsp">
<% if (!create) { %>
    <input type="hidden" name="roomJID" value="<%= StringUtils.escapeForXML(roomJID.toBareJID()) %>">
<% } %>
    <input type="hidden" name="csrf" value="${csrf}">
<input type="hidden" name="save" value="true">
<input type="hidden" name="create" value="<%= create %>">
<input type="hidden" name="roomconfig_persistentroom" value="<%= persistentRoom %>">

    <table width="100%" border="0"> <tr>
         <td width="70%">
            <table width="100%" border="0">
                <tbody>
                <% if (create) { %>
                <tr>
                    <td><fmt:message key="muc.room.edit.form.room_id" />: *</td>
                    <td><input type="text" name="roomName" value="<%= StringUtils.escapeForXML(roomName) %>">
                        <% if (webManager.getMultiUserChatManager().getMultiUserChatServicesCount() > 1) { %>
                        @<select name="mucName">
                        <% for (MultiUserChatService service : webManager.getMultiUserChatManager().getMultiUserChatServices()) { %>
                        <%      if (service.isHidden()) continue; %>
                        <option value="<%= StringUtils.escapeForXML(service.getServiceDomain()) %>"<%= service.getServiceDomain().equals(mucName) ? " selected='selected'" : "" %>><%= StringUtils.escapeHTMLTags(service.getServiceDomain()) %></option>
                        <% } %>
                        </select>
                        <% } else { %>
                        @<%
                            // We only have one service, none-the-less, we have to run through the list to get the first
                            for (MultiUserChatService service : webManager.getMultiUserChatManager().getMultiUserChatServices()) {
                                if (service.isHidden()) {
                                    // Private and hidden, skip it.
                                    continue;
                                }
                                out.print("<input type='hidden' name='mucName' value='"+StringUtils.escapeForXML(service.getServiceDomain())+"'/>"+StringUtils.escapeHTMLTags(service.getServiceDomain()));
                                break;
                            }
                        %>
                        <% } %>
                    </td>
                </tr>
                <% } else { %>
                <tr>
                   <td><fmt:message key="muc.room.edit.form.service" />:</td>
                   <td><%= StringUtils.escapeHTMLTags(roomJID.getDomain()) %></td>
               </tr>
                <% } %>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.room_name" />: *</td>
                    <td><input type="text" name="roomconfig_roomname" value="<%= (naturalName == null ? "" : StringUtils.escapeForXML(naturalName)) %>">
                    </td>
                </tr>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.description" />:  *</td>
                    <td><input name="roomconfig_roomdesc" value="<%= (description == null ? "" : StringUtils.escapeForXML(description)) %>" type="text" size="40">
                    </td>
                </tr>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.topic" />:</td>
                    <td><input name="room_topic" value="<%= (roomSubject == null ? "" : StringUtils.escapeForXML(roomSubject)) %>" type="text" size="40">
                    </td>
                </tr>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.max_room" />:</td>
                    <td><input type="number" name="roomconfig_maxusers" min="1" value="<%= maxUsers == null || maxUsers.equals("0") ? "" : StringUtils.escapeForXML(maxUsers)%>" size="5">
                        <fmt:message key="muc.room.edit.form.empty_nolimit" />
                    </td>
                </tr>
                 <tr>
                    <td valign="top"><fmt:message key="muc.room.edit.form.broadcast" />:</td>
                    <td><fieldset>
                        <input name="roomconfig_presencebroadcast" type="checkbox" value="true" id="moderator" <% if ("true".equals(broadcastModerator)) out.write("checked");%>>
                        <LABEL FOR="moderator"><fmt:message key="muc.room.edit.form.moderator" /></LABEL>
                        <input name="roomconfig_presencebroadcast2" type="checkbox" value="true" id="participant" <% if ("true".equals(broadcastParticipant)) out.write("checked");%>>
                        <LABEL FOR="participant"><fmt:message key="muc.room.edit.form.participant" /></LABEL>
                        <input name="roomconfig_presencebroadcast3" type="checkbox" value="true" id="visitor" <% if ("true".equals(broadcastVisitor)) out.write("checked");%>>
                        <LABEL FOR="visitor"><fmt:message key="muc.room.edit.form.visitor" /></LABEL>
                        </fieldset></td>
                </tr>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.required_password" />:</td>
                    <td><input type="password" name="roomconfig_roomsecret" <% if(password != null) { %> value="<%= (password == null ? "" : StringUtils.escapeForXML(password)) %>" <% } %>></td>
                </tr>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.confirm_password" />:</td>
                    <td><input type="password" name="roomconfig_roomsecret2" <% if(confirmPassword != null) { %> value="<%= (confirmPassword == null ? "" : StringUtils.escapeForXML(confirmPassword)) %>" <% } %>>
                    </td>
                </tr>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.discover_jid" />:</td>
                    <td><select name="roomconfig_whois">
                            <option value="moderator" <% if ("moderator".equals(whois)) out.write("selected");%>><fmt:message key="muc.room.edit.form.moderator" /></option>
                            <option value="anyone" <% if ("anyone".equals(whois)) out.write("selected");%>><fmt:message key="muc.room.edit.form.anyone" /></option>
                        </select>
                    </td>
                 </tr>
                <tr>
                    <td><fmt:message key="muc.room.edit.form.allowpm" />:</td>
                    <td><select name="roomconfig_allowpm">
                        <option value="none" <% if ("none".equals( allowpm )) out.write("selected");%>><fmt:message key="muc.form.conf.none" /></option>
                        <option value="moderators" <% if ("moderators".equals( allowpm )) out.write("selected");%>><fmt:message key="muc.room.edit.form.moderator" /></option>
                        <option value="participants" <% if ("participants".equals( allowpm )) out.write("selected");%>><fmt:message key="muc.room.edit.form.participant" /></option>
                        <option value="anyone" <% if ("anyone".equals( allowpm )) out.write("selected");%>><fmt:message key="muc.room.edit.form.anyone" /></option>
                    </select>
                    </td>
                </tr>
         </tbody>
         </table>

         </td>
        <td width="30%" valign="top" >
        <fieldset>
        <legend><fmt:message key="muc.room.edit.form.room_options" /></legend>
        <table width="100%"  border="0">
        <tbody>
            <tr>
                <td><input type="checkbox" name="roomconfig_publicroom" value="true" id="public" <% if ("true".equals(publicRoom)) out.write("checked");%>>
                    <LABEL FOR="public"><fmt:message key="muc.room.edit.form.list_room" /></LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_moderatedroom" value="true" id="moderated" <% if ("true".equals(moderatedRoom)) out.write("checked");%>>
                    <LABEL FOR="moderated"><fmt:message key="muc.room.edit.form.room_moderated" /></LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_membersonly" value="true" id="membersOnly" <% if ("true".equals(membersOnly)) out.write("checked");%>>
                    <LABEL FOR="membersOnly"><fmt:message key="muc.room.edit.form.moderated_member_only" /></LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_allowinvites" value="true" id="allowinvites" <% if ("true".equals(allowInvites)) out.write("checked");%>>
                    <LABEL FOR="allowinvites"><fmt:message key="muc.room.edit.form.invite_other" /></LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_changesubject" value="true" id="changesubject" <% if ("true".equals(changeSubject)) out.write("checked");%>>
                    <LABEL FOR="changesubject"><fmt:message key="muc.room.edit.form.change_subject" /></LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_reservednick" value="true" id="reservednick" <% if ("true".equals(reservedNick)) out.write("checked");%>>
                    <LABEL FOR="reservednick"><fmt:message key="muc.room.edit.form.reservednick" /></LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_canchangenick" value="true" id="canchangenick" <% if ("true".equals(canChangeNick)) out.write("checked");%>>
                    <LABEL FOR="canchangenick"><fmt:message key="muc.room.edit.form.canchangenick" /></LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_registration" value="true" id="registration" <% if ("true".equals(registrationEnabled)) out.write("checked");%>>
                    <LABEL FOR="registration"><fmt:message key="muc.room.edit.form.registration" /></LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_enablelogging" value="true" id="enablelogging" <% if ("true".equals(enableLog)) out.write("checked");%>>
                    <LABEL FOR="enablelogging"><fmt:message key="muc.room.edit.form.log" /></LABEL></td>
            </tr>
        </tbody>
        </table>
        </fieldset>
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
