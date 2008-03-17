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
                 java.text.DateFormat,
                 java.util.*,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 org.jivesoftware.openfire.forms.spi.*,
                 org.jivesoftware.openfire.forms.*,
                 org.dom4j.Element,
                 org.xmpp.packet.IQ,
                 org.xmpp.packet.Message,
                 org.xmpp.packet.JID,
                 org.jivesoftware.stringprep.Stringprep,
                 org.jivesoftware.stringprep.StringprepException,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.muc.NotAllowedException"%>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters
    boolean create = ParamUtils.getBooleanParameter(request,"create");
    boolean save = ParamUtils.getBooleanParameter(request,"save");
    boolean success = ParamUtils.getBooleanParameter(request,"success");
    boolean addsuccess = ParamUtils.getBooleanParameter(request,"addsuccess");
    JID roomJID = new JID(ParamUtils.getParameter(request,"roomJID"));
    String naturalName = ParamUtils.getParameter(request,"roomconfig_roomname");
    String description = ParamUtils.getParameter(request,"roomconfig_roomdesc");
    String maxUsers = ParamUtils.getParameter(request, "roomconfig_maxusers");
    String broadcastModerator = ParamUtils.getParameter(request, "roomconfig_presencebroadcast");
    String broadcastParticipant = ParamUtils.getParameter(request, "roomconfig_presencebroadcast2");
    String broadcastVisitor = ParamUtils.getParameter(request, "roomconfig_presencebroadcast3");
    String password = ParamUtils.getParameter(request, "roomconfig_roomsecret");
    String confirmPassword = ParamUtils.getParameter(request, "roomconfig_roomsecret2");
    String whois = ParamUtils.getParameter(request, "roomconfig_whois");
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
    String roomSubject = ParamUtils.getParameter(request, "room_topic");

    String roomName = roomJID.getNode();

    if (webManager.getMultiUserChatManager().getMultiUserChatServicesCount() < 1) {
        // No services exist, so redirect to where one can configure the services
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        response.sendRedirect("muc-room-summary.jsp");
        return;
    }

    // Load the room object
    MUCRoom room = null;
    if (!create) {
        room = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomName);

        if (room == null) {
            // The requested room name does not exist so return to the list of the existing rooms
            response.sendRedirect("muc-room-summary.jsp");
            return;
        }
    }

    // Handle an save
    Map<String, String> errors = new HashMap<String, String>();
    if (save) {
        // do validation

        if (naturalName == null) {
            errors.put("roomconfig_roomname","roomconfig_roomname");
        }
        if (description == null) {
            errors.put("roomconfig_roomdesc","roomconfig_roomdesc");
        }
        if (maxUsers == null) {
            errors.put("roomconfig_maxusers","roomconfig_maxusers");
        }
        if (password != null && !password.equals(confirmPassword)) {
            errors.put("roomconfig_roomsecret2","roomconfig_roomsecret2");
        }
        if (whois == null) {
            errors.put("roomconfig_whois","roomconfig_whois");
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
                        if (!room.getOwners().contains(address.toBareJID())) {
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
            FormField field;
            XDataFormImpl dataForm = new XDataFormImpl(DataForm.TYPE_SUBMIT);

            field = new XFormFieldImpl("FORM_TYPE");
            field.setType(FormField.TYPE_HIDDEN);
            field.addValue("http://jabber.org/protocol/muc#roomconfig");
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_roomname");
            field.addValue(naturalName);
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_roomdesc");
            field.addValue(description);
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_changesubject");
            field.addValue((changeSubject == null) ? "0": "1");
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_maxusers");
            field.addValue(maxUsers);
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_presencebroadcast");
            if (broadcastModerator != null) {
                field.addValue("moderator");
            }
            if (broadcastParticipant != null) {
                field.addValue("participant");
            }
            if (broadcastVisitor != null) {
                field.addValue("visitor");
            }
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_publicroom");
            field.addValue((publicRoom == null) ? "0": "1");
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_persistentroom");
            field.addValue((persistentRoom == null) ? "0": "1");
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_moderatedroom");
            field.addValue((moderatedRoom == null) ? "0": "1");
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_membersonly");
            field.addValue((membersOnly == null) ? "0": "1");
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_allowinvites");
            field.addValue((allowInvites == null) ? "0": "1");
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_passwordprotectedroom");
            field.addValue((password == null) ? "0": "1");
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_roomsecret");
            field.addValue(password);
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_whois");
            field.addValue(whois);
            dataForm.addField(field);

            field = new XFormFieldImpl("muc#roomconfig_enablelogging");
            field.addValue((enableLog == null) ? "0": "1");
            dataForm.addField(field);

            field = new XFormFieldImpl("x-muc#roomconfig_reservednick");
            field.addValue((reservedNick == null) ? "0": "1");
            dataForm.addField(field);

            field = new XFormFieldImpl("x-muc#roomconfig_canchangenick");
            field.addValue((canChangeNick == null) ? "0": "1");
            dataForm.addField(field);

            field = new XFormFieldImpl("x-muc#roomconfig_registration");
            field.addValue((registrationEnabled == null) ? "0": "1");
            dataForm.addField(field);

            // Keep the existing list of admins
            field = new XFormFieldImpl("muc#roomconfig_roomadmins");
            for (String jid : room.getAdmins()) {
                field.addValue(jid);
            }
            dataForm.addField(field);

            // Keep the existing list of owners
            field = new XFormFieldImpl("muc#roomconfig_roomowners");
            for (String jid : room.getOwners()) {
                field.addValue(jid);
            }
            dataForm.addField(field);

            // Create an IQ packet and set the dataform as the main fragment
            IQ iq = new IQ(IQ.Type.set);
            Element element = iq.setChildElement("query", "http://jabber.org/protocol/muc#owner");
            element.add(dataForm.asXMLElement());
            // Send the IQ packet that will modify the room's configuration
            room.getIQOwnerHandler().handleIQ(iq, room.getRole());

            if (roomSubject != null) {
                // Change the subject of the room by sending a new message
                Message message = new Message();
                message.setType(Message.Type.groupchat);
                message.setSubject(roomSubject);
                message.setFrom(room.getRole().getRoleAddress());
                message.setTo(room.getRole().getRoleAddress());
                room.changeSubject(message, room.getRole());
            }

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
            // TODO Make this default values configurable (see JM-79)
            maxUsers = "30";
            broadcastModerator = "true";
            broadcastParticipant = "true";
            broadcastVisitor = "true";
            whois = "moderator";
            publicRoom = "true";
            // Rooms created from the admin console are always persistent
            persistentRoom = "true";
            canChangeNick = "true";
            registrationEnabled = "true";
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
<title><fmt:message key="muc.room.edit.form.title"/></title>
<% if (create) { %>
<meta name="pageID" content="muc-room-create"/>
<% } else { %>
<meta name="subPageID" content="muc-room-edit-form"/>
<% } %>
<meta name="extraParams" content="<%= "roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8")+"&create="+create %>"/>
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

            <% if (errors.get("roomconfig_roomname") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint_name" />
            <% } else if (errors.get("roomconfig_roomdesc") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint_description" />
            <% } else if (errors.get("roomconfig_maxusers") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint_max_room" />
            <% } else if (errors.get("roomconfig_roomsecret2") != null) { %>
                <fmt:message key="muc.room.edit.form.new_password" />
            <% } else if (errors.get("roomconfig_whois") != null) { %>
                <fmt:message key="muc.room.edit.form.role" />
            <% } else if (errors.get("roomName") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint" />
            <% } else if (errors.get("room_already_exists") != null) { %>
                <fmt:message key="muc.room.edit.form.error_created_id" />
            <% } else if (errors.get("not_enough_permissions") != null) { %>
                <fmt:message key="muc.room.edit.form.error_created_privileges" />
            <% } else if (errors.get("room_topic") != null) { %>
                <fmt:message key="muc.room.edit.form.valid_hint_subject" />
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
            <td><%= room.getName() %></td>
            <% if (room.getOccupantsCount() == 0) { %>
            <td><%= room.getOccupantsCount() %> / <%= room.getMaxUsers() %></td>
            <% } else { %>
            <td><a href="muc-room-occupants.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8")%>"><%= room.getOccupantsCount() %> / <%= room.getMaxUsers() %></a></td>
            <% } %>
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
    <input type="hidden" name="roomJID" value="<%= roomJID.toBareJID() %>">
<% } %>
<input type="hidden" name="save" value="true">
<input type="hidden" name="create" value="<%= create %>">
<input type="hidden" name="roomconfig_persistentroom" value="<%= persistentRoom %>">

    <table width="100%" border="0"> <tr>
         <td width="70%">
            <table width="100%" border="0">
                <tbody>
                <% if (create) { %>
                <tr>
                    <td><fmt:message key="muc.room.edit.form.room_id" />:</td>
                    <td><input type="text" name="roomName" value="<%= roomName %>">
                        <% if (webManager.getMultiUserChatManager().getMultiUserChatServicesCount() > 1) { %>
                        @<select name="mucname">
                        <% for (MultiUserChatService service : webManager.getMultiUserChatManager().getMultiUserChatServices()) { %>
                        <option value="<%= service.getServiceName() %>"><%= service.getServiceDomain() %></option>
                        <% } %>
                        </select>
                        <% } else { %>
                        @<%
                            // We only have one service, none-the-less, we have to run through the list to get the first
                            for (MultiUserChatService service : webManager.getMultiUserChatManager().getMultiUserChatServices()) {
                                if (service.isServicePrivate()) {
                                    // Private and hidden, skip it.
                                    continue;
                                }
                                out.print(service.getServiceDomain());
                                break;
                            }
                        %>
                        <% } %>
                    </td>
                </tr>
                <% } %>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.room_name" />:</td>
                    <td><input type="text" name="roomconfig_roomname" value="<%= (naturalName == null ? "" : naturalName) %>">
                    </td>
                </tr>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.description" />:</td>
                    <td><input name="roomconfig_roomdesc" value="<%= (description == null ? "" : description) %>" type="text" size="40">
                    </td>
                </tr>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.topic" />:</td>
                    <td><input name="room_topic" value="<%= (roomSubject == null ? "" : roomSubject) %>" type="text" size="40">
                    </td>
                </tr>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.max_room" />:</td>
                    <td><select name="roomconfig_maxusers">
                            <option value="10" <% if ("10".equals(maxUsers)) out.write("selected");%>>10</option>
                            <option value="20" <% if ("20".equals(maxUsers)) out.write("selected");%>>20</option>
                            <option value="30" <% if ("30".equals(maxUsers)) out.write("selected");%>>30</option>
                            <option value="40" <% if ("40".equals(maxUsers)) out.write("selected");%>>40</option>
                            <option value="50" <% if ("50".equals(maxUsers)) out.write("selected");%>>50</option>
                            <option value="0" <% if ("0".equals(maxUsers)) out.write("selected");%>><fmt:message key="muc.room.edit.form.none" /></option>
                        </select>
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
                    <td><input type="password" name="roomconfig_roomsecret" <% if(password != null) { %> value="<%= password %>" <% } %>></td>
                </tr>
                 <tr>
                    <td><fmt:message key="muc.room.edit.form.confirm_password" />:</td>
                    <td><input type="password" name="roomconfig_roomsecret2" <% if(confirmPassword != null) { %> value="<%= confirmPassword %>" <% } %>>
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
</form>

    </body>
</html>