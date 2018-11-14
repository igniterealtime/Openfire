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
                 org.jivesoftware.openfire.group.Group,
                 org.jivesoftware.openfire.group.GroupJID,
                 java.util.*,
                 org.xmpp.packet.*,
                 org.jivesoftware.openfire.muc.MultiUserChatService"
    errorPage="error.jsp"
%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.net.URLDecoder" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    String userJID = ParamUtils.getParameter(request,"userJID");
    String[] groupNames = ParamUtils.getParameters(request, "groupNames");
    boolean allowAllRegisteredUsers =  ParamUtils.getBooleanParameter(request,"allowAllRegisteredUsers");
    boolean add = request.getParameter("add") != null;
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    boolean addsuccess = request.getParameter("addsuccess") != null;
    boolean deletesuccess = request.getParameter("deletesuccess") != null;
    boolean delete = ParamUtils.getBooleanParameter(request,"delete");
    boolean openPerms = ParamUtils.getBooleanParameter(request,"openPerms");
    String mucname = ParamUtils.getParameter(request,"mucname");

    if (!webManager.getMultiUserChatManager().isServiceRegistered(mucname)) {
        // The requested service name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save || add || delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
            add = false;
            delete = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    // Get muc server
    MultiUserChatService mucService = webManager.getMultiUserChatManager().getMultiUserChatService(mucname);

    // Handle a save
    Map<String,String> errors = new HashMap<String,String>();
    if (save) {
        if (openPerms) {
            // Remove all users who have the ability to create rooms
            List<JID> removeables = new ArrayList<JID>();
            for (JID user : mucService.getUsersAllowedToCreate()) {
                removeables.add(user);
            }
            for (JID user : removeables) {
                mucService.removeUserAllowedToCreate(user);
            }
            mucService.setRoomCreationRestricted(false);
            // Log the event
            webManager.logEvent("set MUC room creation to restricted for service "+mucname, null);
            response.sendRedirect("muc-create-permission.jsp?success=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
            return;
        }
        else {
            mucService.setRoomCreationRestricted(true);
            // Log the event
            webManager.logEvent("set MUC room creation to not restricted for service "+mucname, null);
            response.sendRedirect("muc-create-permission.jsp?success=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
            return;
        }
    }

    List<JID> allowedJIDs = new ArrayList<JID>();
    try {
        if (userJID != null && userJID.trim().length() > 0) {
            String allowedJID;
            // do validation; could be a group jid
            if (userJID.indexOf('@') == -1) {
                String username = JID.escapeNode(userJID);
                String domain = webManager.getXMPPServer().getServerInfo().getXMPPDomain();
                allowedJID = username + '@' + domain;
            }
            else {
                String username = JID.escapeNode(userJID.substring(0, userJID.indexOf('@')));
                String rest = userJID.substring(userJID.indexOf('@'), userJID.length());
                allowedJID = username + rest.trim();
            }
            allowedJIDs.add(GroupJID.fromString(allowedJID.trim()).asBareJID());
        }
        if (groupNames != null) {
            // create a group JID for each group
            for (String groupName : groupNames) {
                GroupJID groupJID = new GroupJID(URLDecoder.decode(groupName, "UTF-8"));
                allowedJIDs.add(groupJID);
            }
        }
    } catch (java.lang.IllegalArgumentException ex) {
        errors.put("userJID","userJID");
    }

    if (errors.size() == 0) {
        // Handle an add
        if (add) {
            mucService.addUsersAllowedToCreate(allowedJIDs);
            mucService.setAllRegisteredUsersAllowedToCreate(allowAllRegisteredUsers);
            // Log the event
            webManager.logEvent("updated MUC room creation permissions for service "+mucname, null);
            response.sendRedirect("muc-create-permission.jsp?addsuccess=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
            return;
        }

        // Handle delete
        if (delete) {
            // Remove the user from the allowed list
            mucService.removeUserAllowedToCreate(GroupJID.fromString(userJID));
            // Log the event
            webManager.logEvent("removed MUC room creation permission from "+userJID+" for service "+mucname, null);
            // done, return
            response.sendRedirect("muc-create-permission.jsp?deletesuccess=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
            return;
        }
    }
%>

<html>
<head>
<title><fmt:message key="muc.create.permission.title"/></title>
<meta name="subPageID" content="muc-perms"/>
<meta name="extraParams" content="<%= "mucname="+URLEncoder.encode(mucname, "UTF-8") %>"/>
<meta name="helpPage" content="set_group_chat_room_creation_permissions.html"/>
</head>
<body>

<p>
<fmt:message key="muc.create.permission.info" />
<fmt:message key="groupchat.service.settings_affect" /> <b><a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucname, "UTF-8") %>"><%= StringUtils.escapeHTMLTags(mucname) %></a></b>
</p>

<%  if (errors.size() > 0) { 
        if (delete) {
            userJID = null; // mask group jid on error
        }
%>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="muc.create.permission.error" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (success || addsuccess || deletesuccess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <%  if (success) { %>

            <fmt:message key="muc.create.permission.update" />

        <%  } else if (addsuccess) { %>

            <fmt:message key="muc.create.permission.add_user" />

        <%  } else if (deletesuccess) { %>

            <fmt:message key="muc.create.permission.user_removed" />

        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<!-- BEGIN 'Permission Policy' -->
<form action="muc-create-permission.jsp?save" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="mucname" value="<%= StringUtils.escapeForXML(mucname) %>" />
    <div class="jive-contentBoxHeader">
        <fmt:message key="muc.create.permission.policy" />
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td width="1%">
                    <input type="radio" name="openPerms" value="true" id="rb01"
                     <%= ((!mucService.isRoomCreationRestricted()) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb01"><fmt:message key="muc.create.permission.anyone_created" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input type="radio" name="openPerms" value="false" id="rb02"
                     onfocus="this.form.userJID.focus();"
                     <%= ((mucService.isRoomCreationRestricted()) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb02"><fmt:message key="muc.create.permission.specific_created" /></label>
                </td>
            </tr>
        </tbody>
        </table>
    </div>
    <input type="submit" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END 'Permission Policy' -->

<br>


<%  if (mucService.isRoomCreationRestricted()) { %>
<!-- BEGIN 'Allowed Users' -->
<form action="muc-create-permission.jsp?add" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="mucname" value="<%= StringUtils.escapeForXML(mucname) %>" />
    <div class="jive-contentBoxHeader">
        <fmt:message key="muc.create.permission.allowed_users" />
    </div>
    <div class="jive-contentBox">
        <p>
            <input type="checkbox" id="allowAllRegisteredUsers" name="allowAllRegisteredUsers" <%=mucService.isAllRegisteredUsersAllowedToCreate()?"checked":""%> onChange="this.form.submit()">
            <label for="allowAllRegisteredUsers"><fmt:message key="muc.create.permission.allow_registered" /></label>
        </p>
        <p>
        <label for="groupJIDs"><fmt:message key="muc.create.permission.add_group" /></label><br/>
        <select name="groupNames" size="6" multiple style="width:400px;font-family:verdana,arial,helvetica,sans-serif;font-size:8pt;" 
         onclick="this.form.openPerms[1].checked=true;" id="groupJIDs">
        <%  for (Group g : webManager.getGroupManager().getGroups()) {	%>
            <option value="<%= URLEncoder.encode(g.getName(), "UTF-8") %>"
             <%= (StringUtils.contains(groupNames, g.getName()) ? "selected" : "") %>
             ><%= StringUtils.escapeHTMLTags(g.getName()) %></option>
        <%  } %>
        </select>
        </p>
        <p>
        <label for="userJIDtf"><fmt:message key="muc.create.permission.add_jid" /></label>
        <input type="text" name="userJID" size="30" maxlength="100" value="<%= (userJID != null ? userJID : "") %>"
         onclick="this.form.openPerms[1].checked=true;" id="userJIDtf">
        <input type="submit" value="Add">
        </p>

        <div class="jive-table" style="width:400px;">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
                <tr>
                    <th width="99%">User/Group</th>
                    <th width="1%">Remove</th>
                </tr>
            </thead>
            <tbody>
                <%  if (mucService.getUsersAllowedToCreate().size() == 0) { %>

                    <tr>
                        <td colspan="2">
                            <fmt:message key="muc.create.permission.no_allowed_users" />
                        </td>
                    </tr>

                <%  } %>

                <%  for (JID jid : mucService.getUsersAllowedToCreate()) {
                        boolean isGroup = GroupJID.isGroup(jid);
                        String jidDisplay = isGroup ? ((GroupJID)jid).getGroupName() : jid.toString();
                %>
                    <tr>
                        <td width="99%">
                          <% if (isGroup) { %>
                            <img src="images/group.gif" width="16" height="16" align="top" title="<fmt:message key="muc.create.permission.group" />" alt="<fmt:message key="muc.create.permission.group" />"/>
                          <% } else { %>
                            <img src="images/user.gif" width="16" height="16" align="top" title="<fmt:message key="muc.create.permission.user" />" alt="<fmt:message key="muc.create.permission.user" />"/>
                          <% } %>
                          <a href="<%= isGroup ? "group-edit.jsp?group=" + URLEncoder.encode(jidDisplay) : "user-properties.jsp?username=" + URLEncoder.encode(jid.getNode()) %>">
                          <%= jidDisplay %></a>
                        </td>
                        <td width="1%" align="center">
                            <a href="muc-create-permission.jsp?userJID=<%= jid.toString() %>&delete=true&csrf=${csrf}&mucname=<%= URLEncoder.encode(mucname, "UTF-8") %>"
                             title="<fmt:message key="muc.create.permission.click_title" />"
                             onclick="return confirm('<fmt:message key="muc.create.permission.confirm_remove" />');"
                             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                        </td>
                    </tr>

                <%  } %>
            </tbody>
            </table>
        </div>
    </div>
</form>
<!-- END 'Allowed Users' -->

<%  } %>


</body>
</html>

