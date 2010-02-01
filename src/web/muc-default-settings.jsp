<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2010 Jive Software. All rights reserved.
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
                 org.jivesoftware.openfire.muc.spi.MUCPersistenceManager"
    errorPage="error.jsp"
%>
<%@ page import="java.net.URLEncoder" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    String mucname = ParamUtils.getParameter(request,"mucname");

    String publicRoom = ParamUtils.getParameter(request, "roomconfig_publicroom");
    String persistentRoom = ParamUtils.getParameter(request, "roomconfig_persistentroom");
    String moderatedRoom = ParamUtils.getParameter(request, "roomconfig_moderatedroom");
    String membersOnly = ParamUtils.getParameter(request, "roomconfig_membersonly");
    String nonanonymous = ParamUtils.getParameter(request, "roomconfig_nonanonymous");
    String allowInvites = ParamUtils.getParameter(request, "roomconfig_allowinvites");
    String changeSubject = ParamUtils.getParameter(request, "roomconfig_changesubject");
    String reservedNick = ParamUtils.getParameter(request, "roomconfig_reservednick");
    String canChangeNick = ParamUtils.getParameter(request, "roomconfig_canchangenick");
    String registrationEnabled = ParamUtils.getParameter(request, "roomconfig_registration");
    String enableLog = ParamUtils.getParameter(request, "roomconfig_enablelogging");
    String maxUsers = ParamUtils.getParameter(request, "roomconfig_maxusers");

    if (!webManager.getMultiUserChatManager().isServiceRegistered(mucname)) {
        // The requested service name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Handle a save
    Map<String,String> errors = new HashMap<String,String>();
    if (save) {
        try {
            int max = Integer.parseInt(maxUsers);
            MUCPersistenceManager.setProperty(mucname, "room.maxUsers", maxUsers);
        }
        catch (Exception e) {
            errors.put("max_users", "max_users");
        }
        if (errors.size() == 0) {
            if (publicRoom != null && publicRoom.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.publicRoom", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.publicRoom", "false");
            }
            if (persistentRoom != null && persistentRoom.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.persistent", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.persistent", "false");
            }
            if (moderatedRoom != null && moderatedRoom.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.moderated", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.moderated", "false");
            }
            if (membersOnly != null && membersOnly.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.membersOnly", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.membersOnly", "false");
            }
            if (nonanonymous != null && nonanonymous.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.canAnyoneDiscoverJID", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.canAnyoneDiscoverJID", "false");
            }
            if (allowInvites != null && allowInvites.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.canOccupantsInvite", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.canOccupantsInvite", "false");
            }
            if (changeSubject != null && changeSubject.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.canOccupantsChangeSubject", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.canOccupantsChangeSubject", "false");
            }
            if (reservedNick != null && reservedNick.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.loginRestrictedToNickname", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.loginRestrictedToNickname", "false");
            }
            if (canChangeNick != null && canChangeNick.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.canChangeNickname", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.canChangeNickname", "false");
            }
            if (registrationEnabled != null && registrationEnabled.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.registrationEnabled", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.registrationEnabled", "false");
            }
            if (enableLog != null && enableLog.trim().length() > 0) {
                MUCPersistenceManager.setProperty(mucname, "room.logEnabled", "true");
            }
            else {
                MUCPersistenceManager.setProperty(mucname, "room.logEnabled", "false");
            }
        }

        response.sendRedirect("muc-default-settings.jsp?success=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
        return;
    }
%>

<html>
<head>
<title><fmt:message key="muc.default.settings.title"/></title>
<meta name="subPageID" content="muc-defaultsettings"/>
<meta name="extraParams" content="<%= "mucname="+URLEncoder.encode(mucname, "UTF-8") %>"/>
<meta name="helpPage" content="set_group_chat_room_creation_permissions.html"/>
</head>
<body>

<p>
<fmt:message key="muc.default.settings.info" />
<fmt:message key="groupchat.service.settings_affect" /> <b><a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucname, "UTF-8") %>"><%= mucname %></a></b>
</p>

<%  if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="muc.default.settings.error" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
            <fmt:message key="muc.default.settings.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<!-- BEGIN 'Default Room Settings' -->
<form action="muc-default-settings.jsp?save" method="post">
    <input type="hidden" name="mucname" value="<%= mucname %>" />
    <div class="jive-contentBoxHeader">
        <fmt:message key="muc.default.settings.title" />
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td width="1%">
                    <input name="roomconfig_publicroom" value="true" id="publicRoom" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.publicRoom", true)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="publicRoom"><fmt:message key="muc.default.settings.public_room" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input name="roomconfig_persistentroom" value="true" id="persistentRoom" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.persistent", false)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="persistentRoom"><fmt:message key="muc.default.settings.persistent_room" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input name="roomconfig_moderatedroom" value="true" id="moderated" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.moderated", false)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="moderated"><fmt:message key="muc.default.settings.moderated" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input name="roomconfig_membersonly" value="true" id="membersOnly" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.membersOnly", false)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="membersOnly"><fmt:message key="muc.default.settings.members_only" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input name="roomconfig_nonanonymous" value="true" id="nonanonymous" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.canAnyoneDiscoverJID", true)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="nonanonymous"><fmt:message key="muc.default.settings.can_anyone_discover_jid" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input name="roomconfig_allowinvites" value="true" id="allowInvites" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.canOccupantsInvite", false)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="allowInvites"><fmt:message key="muc.default.settings.allow_invites" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input name="roomconfig_changesubject" value="true" id="changeSubject" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.canOccupantsChangeSubject", false)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="changeSubject"><fmt:message key="muc.default.settings.change_subject" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input name="roomconfig_reservednick" value="true" id="reservedNick" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.loginRestrictedToNickname", false)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="reservedNick"><fmt:message key="muc.default.settings.reserved_nick" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input name="roomconfig_canchangenick" value="true" id="canChangeNick" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.canChangeNickname", true)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="canChangeNick"><fmt:message key="muc.default.settings.can_change_nick" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input name="roomconfig_registration" value="true" id="registration" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.registrationEnabled", true)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="registration"><fmt:message key="muc.default.settings.registration" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input name="roomconfig_enablelogging" value="true" id="enableLogging" type="checkbox"
                    <%= ((MUCPersistenceManager.getBooleanProperty(mucname, "room.logEnabled", true)) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="enableLogging"><fmt:message key="muc.default.settings.enable_logging" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    &nbsp;
                </td>
                <td width="99%">
                    <label for="roomconfig_maxusers"><fmt:message key="muc.default.settings.max_users" />:</label>
                    &nbsp;
                    <select name="roomconfig_maxusers">
                        <% for(int i=10; i<=50; i=i+10) { %>
                            <option value="<%= i %>"
                            <%= ((MUCPersistenceManager.getIntProperty(mucname, "room.maxUsers", 30)) == i ? "selected=\"selected\"" : "") %>
                            ><%= i %></option>
                        <% } %>
                        <option value="<%= 0 %>"
                        <%= ((MUCPersistenceManager.getIntProperty(mucname, "room.maxUsers", 30)) == 0 ? "selected=\"selected\"" : "") %>
                        ><fmt:message key="global.unlimited" /></option>
                    </select>
                </td>
            </tr>
        </tbody>
        </table>
    </div>
    <input type="submit" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END 'Default Room Settings' -->

</body>
</html>
