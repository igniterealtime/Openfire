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
                 org.jivesoftware.admin.*,
                 java.util.*,
                 org.jivesoftware.messenger.muc.MUCRoom,
                 org.jivesoftware.messenger.forms.spi.*,
                 org.jivesoftware.messenger.forms.*,
                 org.dom4j.Element,
                 org.dom4j.DocumentHelper,
                 org.dom4j.QName,
                 org.xmpp.packet.IQ,
                 org.xmpp.packet.Message,
                 org.xmpp.packet.JID,
                 org.jivesoftware.messenger.auth.UnauthorizedException"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters
    boolean create = ParamUtils.getBooleanParameter(request,"create");
    boolean save = ParamUtils.getBooleanParameter(request,"save");
    boolean success = ParamUtils.getBooleanParameter(request,"success");
    boolean addsuccess = ParamUtils.getBooleanParameter(request,"addsuccess");
    String roomName = ParamUtils.getParameter(request,"roomName");
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
    String roomSubject = ParamUtils.getParameter(request, "room_topic");

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        response.sendRedirect("muc-room-summary.jsp");
        return;
    }

    // Load the room object
    MUCRoom room = null;
    if (!create) {
        room = webManager.getMultiUserChatServer().getChatRoom(roomName);

        if (room == null) {
            // The requested room name does not exist so return to the list of the existing rooms
            response.sendRedirect("muc-room-summary.jsp");
            return;
        }
    }

    // Handle an save
    Map errors = new HashMap();
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
            String params = "";
            if (create) {
                params = "addsuccess=true&roomName=" + roomName;
            }
            else {
                params = "success=true&roomName=" + roomName;
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
        }
    }
    // Formatter for dates
    DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Room Administration";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    if (create) {
        pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "muc-room-edit-form.jsp?create=true"));
        pageinfo.setPageID("muc-room-create");
    }
    else {
        pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "muc-room-edit-form.jsp?roomName="+roomName));
        pageinfo.setSubPageID("muc-room-edit-form");
    }
    pageinfo.setExtraParams("roomName="+roomName+"&create="+create);
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  if (success || addsuccess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <%  if (success) { %>

        Room settings edited successfully.

        <%  } else if (addsuccess) { %>

        Room creation was successfully.

        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br

<%  } %>

<%  if (!create) { %>
    <p>
    Use the form below to edit the room settings.
    </p>
    <div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th scope="col">Room ID</th>
            <th scope="col">Users</th>
            <th scope="col">Created On</th>
            <th scope="col">Last Modified</th>
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
    <p>Change the room settings of this room using the form below</p>
<%  } else { %>
    <p>Use the form below to create a new persistent room. The new room will be immediately available.</p>

    <% if (errors.containsKey("room_already_exists") || errors.containsKey("not_enough_permissions")) { %>
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

<%  } %>
<form action="muc-room-edit-form.jsp">
<% if (!create) { %>
    <input type="hidden" name="roomName" value="<%= roomName %>">
<% } %>
<input type="hidden" name="save" value="true">
<input type="hidden" name="create" value="<%= create %>">
<input type="hidden" name="roomconfig_persistentroom" value="<%= persistentRoom %>">

    <table width="100%" border="0"> <tr>
         <td width="70%"><table width="100%"  border="0">
                <tbody>
                <% if (create) { %>
                <tr>
                    <td>Room ID:</td>
                    <td><input type="text" name="roomName" value="<%= roomName != null ? roomName : ""%>">
                    <%  if (errors.get("roomName") != null) { %>

                        <span class="jive-error-text">
                        Please enter a valid ID. Do not include the service name in the ID.
                        </span>

                    <%  } %>
                    </td>
                </tr>
                <% } %>
                 <tr>
                    <td>Room Name:</td>
                    <td><input type="text" name="roomconfig_roomname" value="<%= (naturalName == null ? "" : naturalName) %>">
                    <%  if (errors.get("roomconfig_roomname") != null) { %>

                        <span class="jive-error-text">
                        Please enter a valid name.
                        </span>

                    <%  } %>
                    </td>
                </tr>
                 <tr>
                    <td>Description:</td>
                    <td><input name="roomconfig_roomdesc" value="<%= (description == null ? "" : description) %>" type="text" size="40">
                    <%  if (errors.get("roomconfig_roomdesc") != null) { %>

                        <span class="jive-error-text">
                        Please enter a valid description.
                        </span>

                    <%  } %>
                    </td>
                </tr>
                 <tr>
                    <td>Topic:</td>
                    <td><input name="room_topic" value="<%= (roomSubject == null ? "" : roomSubject) %>" type="text" size="40">
                    <%  if (errors.get("room_topic") != null) { %>

                        <span class="jive-error-text">
                        Please enter a valid subject.
                        </span>

                    <%  } %>
                    </td>
                </tr>
                 <tr>
                    <td>Maximun Room Occupants:</td>
                    <td><select name="roomconfig_maxusers">
                            <option value="10" <% if ("10".equals(maxUsers)) out.write("selected");%>>10</option>
                            <option value="20" <% if ("20".equals(maxUsers)) out.write("selected");%>>20</option>
                            <option value="30" <% if ("30".equals(maxUsers)) out.write("selected");%>>30</option>
                            <option value="40" <% if ("40".equals(maxUsers)) out.write("selected");%>>40</option>
                            <option value="50" <% if ("50".equals(maxUsers)) out.write("selected");%>>50</option>
                            <option value="0" <% if ("0".equals(maxUsers)) out.write("selected");%>>None</option>
                        </select>
                        <%  if (errors.get("roomconfig_maxusers") != null) { %>

                            <span class="jive-error-text">
                            Please select the maximun room occupants.
                            </span>

                        <%  } %>
                    </td>
                </tr>
                 <tr>
                    <td valign="top">Roles for Which Presence is Broadcast:</td>
                    <td><fieldset>
                        <input name="roomconfig_presencebroadcast" type="checkbox" value="true" id="moderator" <% if ("true".equals(broadcastModerator)) out.write("checked");%>>
                        <LABEL FOR="moderator">Moderator</LABEL>
                        <input name="roomconfig_presencebroadcast2" type="checkbox" value="true" id="participant" <% if ("true".equals(broadcastParticipant)) out.write("checked");%>>
                        <LABEL FOR="participant">Participant</LABEL>
                        <input name="roomconfig_presencebroadcast3" type="checkbox" value="true" id="visitor" <% if ("true".equals(broadcastVisitor)) out.write("checked");%>>
                        <LABEL FOR="visitor">Visitor</LABEL>
                        </fieldset></td>
                </tr>
                 <tr>
                    <td>Password required to enter:</td>
                    <td><input type="password" name="roomconfig_roomsecret" <% if(password != null) { %> value="<%= password %>" <% } %>></td>
                </tr>
                 <tr>
                    <td>Confirm password:</td>
                    <td><input type="password" name="roomconfig_roomsecret2" <% if(confirmPassword != null) { %> value="<%= confirmPassword %>" <% } %>>
                        <%  if (errors.get("roomconfig_roomsecret2") != null) { %>

                            <span class="jive-error-text">
                            Please make sure to enter the same new password.
                            </span>

                        <%  } %>
                    </td>
                </tr>
                 <tr>
                    <td>Role that May Discover Real JIDs of Occupants:</td>
                    <td><select name="roomconfig_whois">
                            <option value="moderator" <% if ("moderator".equals(whois)) out.write("selected");%>>Moderator</option>
                            <option value="anyone" <% if ("anyone".equals(whois)) out.write("selected");%>>Anyone</option>
                        </select>
                        <%  if (errors.get("roomconfig_whois") != null) { %>

                            <span class="jive-error-text">
                            Please select a role.
                            </span>

                        <%  } %>
                    </td>
                 </tr>
         </tbody>
         </table></td>
        <td width="30%" valign="top" >
        <fieldset>
        <legend>Room Options</legend>
        <table width="100%"  border="0">
        <tbody>
            <tr>
                <td><input type="checkbox" name="roomconfig_publicroom" value="true" id="public" <% if ("true".equals(publicRoom)) out.write("checked");%>>
                    <LABEL FOR="public">List Room in Directory</LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_moderatedroom" value="true" id="moderated" <% if ("true".equals(moderatedRoom)) out.write("checked");%>>
                    <LABEL FOR="moderated">Make Room Moderated</LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_membersonly" value="true" id="membersOnly" <% if ("true".equals(membersOnly)) out.write("checked");%>>
                    <LABEL FOR="membersOnly">Make Room Members-only</LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_allowinvites" value="true" id="allowinvites" <% if ("true".equals(allowInvites)) out.write("checked");%>>
                    <LABEL FOR="allowinvites">Allow Occupants to invite Others</LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_changesubject" value="true" id="changesubject" <% if ("true".equals(changeSubject)) out.write("checked");%>>
                    <LABEL FOR="changesubject">Allow Occupants to change Subject</LABEL></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="roomconfig_enablelogging" value="true" id="enablelogging" <% if ("true".equals(enableLog)) out.write("checked");%>>
                    <LABEL FOR="enablelogging">Log Room Conversations</td>
            </tr>
        </table>
        </fieldset>
        </tr>
         <tr align="center">
            <td colspan="2"><input type="submit" name="Submit" value="Save changes">
            <input type="submit" name="cancel" value="Cancel"></td>
        </tr>
    </tbody>
    </table>
</form>

<jsp:include page="bottom.jsp" flush="true" />