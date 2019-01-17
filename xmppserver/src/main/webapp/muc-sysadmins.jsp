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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    String userJID = ParamUtils.getParameter(request,"userJID");
    String[] groupNames = ParamUtils.getParameters(request, "groupNames");
    boolean add = request.getParameter("add") != null;
    boolean passwordPolicy = request.getParameter("passwordPolicy") != null;
    boolean delete = ParamUtils.getBooleanParameter(request,"delete");
    boolean requirePassword = ParamUtils.getBooleanParameter(request,"requirePassword");
    String mucname = ParamUtils.getParameter(request,"mucname");

    if (!webManager.getMultiUserChatManager().isServiceRegistered(mucname)) {
        // The requested service name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Get muc server
    MultiUserChatService mucService = webManager.getMultiUserChatManager().getMultiUserChatService(mucname);

    // Handle a save
    Map<String,String> errors = new HashMap<String,String>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (add || delete || passwordPolicy) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            add = false;
            delete = false;
            passwordPolicy = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
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
        if (add) {
            mucService.addSysadmins(allowedJIDs);
            // Log the event
            webManager.logEvent("added muc sysadmin permissions for service "+mucname, null);
            response.sendRedirect("muc-sysadmins.jsp?addsuccess=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
            return;
        }

        if (delete) {
            // Remove the user from the list of system administrators
            mucService.removeSysadmin(GroupJID.fromString(userJID));
            // Log the event
            webManager.logEvent("removed muc sysadmin "+userJID+" for service "+mucname, null);
            // done, return
            response.sendRedirect("muc-sysadmins.jsp?deletesuccess=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
            return;
        }

        if (passwordPolicy) {
            mucService.setPasswordRequiredForSysadminsToJoinRoom(requirePassword);
            // Log the event
            webManager.logEvent("muc sysadmins for service "+mucname + "now " + (requirePassword ? "cannot" : "can") + " join a password-protected room, without supplying the password.", null);
            // done, return
            response.sendRedirect("muc-sysadmins.jsp?success=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
            return;
        }
    }
%>

<html>
<head>
<title><fmt:message key="groupchat.admins.title"/></title>
<meta name="subPageID" content="muc-sysadmin"/>
<meta name="extraParams" content="<%= "mucname="+URLEncoder.encode(mucname, "UTF-8") %>"/>
<meta name="helpPage" content="edit_group_chat_service_administrators.html"/>
</head>
<body>

<p>
<fmt:message key="groupchat.admins.introduction" />
<fmt:message key="groupchat.service.settings_affect" /> <b><a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucname, "UTF-8") %>"><%= StringUtils.escapeHTMLTags(mucname) %></a></b>
</p>

<%  if ("true".equals(request.getParameter("deletesuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="groupchat.admins.user_removed" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if ("true".equals(request.getParameter("addsuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="groupchat.admins.user_added" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if ("true".equals(request.getParameter("success"))) { %>

    <div class="jive-success">
        <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
            <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
                <td class="jive-icon-label">
                    <fmt:message key="groupchat.admins.settings.saved_successfully"/>
                </td></tr>
            </tbody>
        </table>
    </div><br>

<%  } else if (errors.size() > 0) {  
        if (delete) {
            userJID = null; // mask group jid on error
        }
%>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="groupchat.admins.error_adding" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>


<!-- BEGIN 'Administrators' -->
<form action="muc-sysadmins.jsp?add" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="mucname" value="<%= StringUtils.escapeForXML(mucname) %>" />
    <div class="jive-contentBoxHeader">
        <fmt:message key="groupchat.admins.legend" />
    </div>
    <div class="jive-contentBox">
        <p>
        <label for="groupJIDs"><fmt:message key="groupchat.admins.add_group" /></label><br/>
        <select name="groupNames" size="6" multiple style="width:400px;font-family:verdana,arial,helvetica,sans-serif;font-size:8pt;" id="groupJIDs">
        <%  for (Group g : webManager.getGroupManager().getGroups()) {	%>
            <option value="<%= URLEncoder.encode(g.getName(), "UTF-8") %>"
             <%= (StringUtils.contains(groupNames, g.getName()) ? "selected" : "") %>
             ><%= StringUtils.escapeHTMLTags(g.getName()) %></option>
        <%  } %>
        </select>
        </p>
        <label for="userJIDtf"><fmt:message key="groupchat.admins.label_add_admin" /></label>
        <input type="text" name="userJID" size="30" maxlength="100" value="<%= (userJID != null ? StringUtils.escapeForXML(userJID) : "") %>"
         id="userJIDtf">
        <input type="submit" value="<fmt:message key="groupchat.admins.add" />">
        <br><br>

        <div class="jive-table" style="width:400px;">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
                <tr>
                    <th width="99%"><fmt:message key="groupchat.admins.column_user" /></th>
                    <th width="1%" nowrap><fmt:message key="groupchat.admins.column_remove" /></th>
                </tr>
            </thead>
            <tbody>
                <%  if (mucService.getSysadmins().size() == 0) { %>

                    <tr>
                        <td colspan="2">
                            <fmt:message key="groupchat.admins.no_admins" />
                        </td>
                    </tr>

                <%  } %>

                <%  for (JID jid : mucService.getSysadmins()) {
                    boolean isGroup = GroupJID.isGroup(jid);
                    String jidDisplay = isGroup ? ((GroupJID)jid).getGroupName() : jid.toString();
                %>
                    <tr>
                        <td width="99%">
                          <% if (isGroup) { %>
                            <img src="images/group.gif" width="16" height="16" align="top" title="<fmt:message key="groupchat.admins.group" />" alt="<fmt:message key="groupchat.admins.group" />"/>
                          <% } else { %>
                            <img src="images/user.gif" width="16" height="16" align="top" title="<fmt:message key="groupchat.admins.user" />" alt="<fmt:message key="groupchat.admins.user" />"/>
                          <% } %>
                          <a href="<%= isGroup ? "group-edit.jsp?group=" + URLEncoder.encode(jidDisplay) : "user-properties.jsp?username=" + URLEncoder.encode(jid.getNode()) %>">
                          <%= StringUtils.escapeForXML(jidDisplay)%>
                          </a>
                        </td>
                        <td width="1%" align="center">
                            <a href="muc-sysadmins.jsp?userJID=<%= URLEncoder.encode(jid.toString()) %>&delete=true&mucname=<%= URLEncoder.encode(mucname, "UTF-8") %>&amp;csrf=<%= URLEncoder.encode(csrfParam) %>"
                             title="<fmt:message key="groupchat.admins.dialog.title" />"
                             onclick="return confirm('<fmt:message key="groupchat.admins.dialog.text" />');"
                             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                        </td>
                    </tr>

                <%  } %>
            </tbody>
            </table>
        </div>
    </div>
</form>
<!-- END 'Administrators' -->

<br>
<!-- BEGIN 'Permission Policy' -->
<form action="muc-sysadmins.jsp?passwordPolicy" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="mucname" value="<%= StringUtils.escapeForXML(mucname) %>" />
    <div class="jive-contentBoxHeader">
        <fmt:message key="groupchat.admins.passwordpolicy.legend" />
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
            <tr>
                <td width="1%">
                    <input type="radio" name="requirePassword" value="false" id="pp01"
                        <%= ((!mucService.isPasswordRequiredForSysadminsToJoinRoom()) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="pp01"><fmt:message key="groupchat.admins.passwordpolicy.join-without-password.legend" /></label>
                </td>
            </tr>
            <tr>
                <td width="1%">
                    <input type="radio" name="requirePassword" value="true" id="pp02"
                        <%= ((mucService.isPasswordRequiredForSysadminsToJoinRoom()) ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="pp02"><fmt:message key="groupchat.admins.passwordpolicy.join-requires-password.legend" /></label>
                </td>
            </tr>
            </tbody>
        </table>
    </div>
    <input type="submit" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END 'Permission Policy' -->

</body>
</html>
