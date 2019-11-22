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

<%@ page import="org.dom4j.Element,
                 org.jivesoftware.openfire.muc.ConflictException,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 org.jivesoftware.openfire.muc.NotAllowedException,
                 org.jivesoftware.openfire.group.Group,
                 org.jivesoftware.openfire.group.GroupJID,
                 org.jivesoftware.openfire.group.GroupManager,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.util.StringUtils,
                 org.xmpp.packet.IQ"
    errorPage="error.jsp"
%>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.muc.CannotBeInvitedException" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    JID roomJID = new JID(ParamUtils.getParameter(request,"roomJID"));
    String affiliation = ParamUtils.getParameter(request,"affiliation");
    String userJID = ParamUtils.getParameter(request,"userJID");
    String nickName = ParamUtils.getParameter(request,"nickName");
    String roomName = roomJID.getNode();
    String[] groupNames = ParamUtils.getParameters(request, "groupNames");
    final String affName = ParamUtils.getStringParameter(request, "affName", "");

    boolean add = request.getParameter("add") != null;
    boolean addsuccess = request.getParameter("addsuccess") != null;
    boolean deletesuccess = request.getParameter("deletesuccess") != null;
    boolean delete = ParamUtils.getBooleanParameter(request,"delete");

    // Load the room object
    MUCRoom room = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomName);

    if (room == null) {
        // The requested room name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-room-summary.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
        return;
    }

    Map<String,String> errors = new HashMap<String,String>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (add) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            add = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    // Handle an add
    if (add) {
        // do validation
        if (userJID == null && groupNames == null) {
            errors.put("userJID","userJID");
        }

        if (errors.size() == 0) {
            try {
                ArrayList<String> memberJIDs = new ArrayList<String>();
                if (userJID != null) {
                    // Escape username
                    if (userJID.indexOf('@') == -1) {
                        String username = JID.escapeNode(userJID);
                        String domain = webManager.getXMPPServer().getServerInfo().getXMPPDomain();
                        userJID = username + '@' + domain;
                    }
                    else {
                        String username = JID.escapeNode(userJID.substring(0, userJID.indexOf('@')));
                        String rest = userJID.substring(userJID.indexOf('@'), userJID.length());
                        userJID = username + rest.trim();
                    }
                    memberJIDs.add(userJID);
                }
                if (groupNames != null) {
                    // create a group JID for each group
                    for (String groupName : groupNames) {
                        GroupJID groupJID = new GroupJID(URLDecoder.decode(groupName, "UTF-8"));
                        memberJIDs.add(groupJID.toString());
                    }
                }
                IQ iq = new IQ(IQ.Type.set);
                Element frag = iq.setChildElement("query", "http://jabber.org/protocol/muc#admin");
                for (String memberJID : memberJIDs){
                    Element item = frag.addElement("item");
                    item.addAttribute("affiliation", affiliation);
                    item.addAttribute("jid", memberJID);
                    if(nickName != null){
                        item.addAttribute("nick", nickName);
                    }else{
                        //set the name of the user as the nickname of the member
                        if(userJID != null){
                            JID jid = new JID(userJID);
                            item.addAttribute("nick",jid.getNode());
                        }
                    }
                }
                // Send the IQ packet that will modify the room's configuration
                room.getIQAdminHandler().handleIQ(iq, room.getRole());
                // Log the event
                for (String memberJID : memberJIDs) {
                    webManager.logEvent("set MUC affilation to "+affiliation+" for "+memberJID+" in "+roomName, null);
                }
                // done, return
                response.sendRedirect("muc-room-affiliations.jsp?addsuccess=true&roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
                return;
            }
            catch (ConflictException e) {
                errors.put("ConflictException","ConflictException");
            }
            catch (NotAllowedException e) {
                errors.put("NotAllowedException","NotAllowedException");
            }
            catch (CannotBeInvitedException e) {
                errors.put("CannotBeInvitedException", "CannotBeInvitedException");
            }
        }
    }

    if (delete) {
        // Remove the user from the allowed list
        IQ iq = new IQ(IQ.Type.set);
        Element frag = iq.setChildElement("query", "http://jabber.org/protocol/muc#admin");
        Element item = frag.addElement("item");
        item.addAttribute("affiliation", "none");
        item.addAttribute("jid", userJID);
        try {
        // Send the IQ packet that will modify the room's configuration
        room.getIQAdminHandler().handleIQ(iq, room.getRole());
        // done, return
        response.sendRedirect("muc-room-affiliations.jsp?deletesuccess=true&roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
        return;
        }
        catch (ConflictException e) {
            errors.put("ConflictException","ConflictException");
        }
        catch (CannotBeInvitedException e) {
                errors.put("CannotBeInvitedException", "CannotBeInvitedException");
        }
        userJID = null; // hide group/user JID
    }
%>

<html>
    <head>
        <title><fmt:message key="muc.room.affiliations.title"/></title>
        <meta name="subPageID" content="muc-room-affiliations"/>
        <meta name="extraParams" content="<%= "roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>"/>
        <meta name="helpPage" content="edit_group_chat_room_user_permissions.html"/>
    </head>
    <body>

<p>
<fmt:message key="muc.room.affiliations.info" />
<b><a href="muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"><%= room.getJID().toBareJID() %></a></b>.
<fmt:message key="muc.room.affiliations.info_detail" />
</p>

<%  if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <%  if (errors.containsKey("ConflictException")) { %>

        <fmt:message key="muc.room.affiliations.error_removing_user" />

        <%  } else if (errors.containsKey("NotAllowedException")) { %>

        <fmt:message key="muc.room.affiliations.error_banning_user" />

        <%  } else { %>

        <fmt:message key="muc.room.affiliations.error_adding_user" />

        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (addsuccess || deletesuccess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <%  if (addsuccess) { %>

            <fmt:message key="muc.room.affiliations.user_added" />

        <%  } else if (deletesuccess) { %>

            <fmt:message key="muc.room.affiliations.user_removed" />

        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="muc-room-affiliations.jsp?add" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
<input type="hidden" name="roomJID" value="<%= roomJID.toBareJID() %>">

<fieldset>
    <legend><fmt:message key="muc.room.affiliations.permission" /></legend>
    <div>
    <p>
    <label for="groupJIDs"><fmt:message key="muc.room.affiliations.add_group" /></label><br/>
    <select name="groupNames" size="6" multiple style="width:400px;font-family:verdana,arial,helvetica,sans-serif;font-size:8pt;" id="groupJIDs">
    <%  for (Group g : webManager.getGroupManager().getGroups()) {	%>
        <option value="<%= URLEncoder.encode(g.getName(), "UTF-8") %>"
         <%= (StringUtils.contains(groupNames, g.getName()) ? "selected" : "") %>
         ><%= StringUtils.escapeHTMLTags(g.getName()) %></option>
    <%  } %>
    </select>
    </p>
    <p>
        <select name="affiliation"  onchange='location.href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&affName="+ this.options[this.selectedIndex].value;'>
            <option value="owner"<%= affName.equals("owner") ? " selected='selected'" : "" %>><fmt:message key="muc.room.affiliations.owner" /></option>
            <option value="admin"<%= affName.equals("admin") ? " selected='selected'" : "" %>><fmt:message key="muc.room.affiliations.admin" /></option>
            <option value="member"<%= affName.equals("member") ? " selected='selected'" : "" %>><fmt:message key="muc.room.affiliations.member" /></option>
            <option value="outcast"<%= affName.equals("outcast") ? " selected='selected'" : "" %>><fmt:message key="muc.room.affiliations.outcast" /></option>
        </select>
    </p>
    <p>
        <label for="memberJID"><fmt:message key="muc.room.affiliations.add_jid" /></label>
        <input type="text" name="userJID" size="30" maxlength="255" value="<%= (userJID != null ? StringUtils.escapeHTMLTags(userJID) : "") %>" id="memberJID">
    </p>
    <% if(!affName.isEmpty() && affName.equals("member")) {%>
        <p>
            <label for="memberJID"><fmt:message key="muc.room.affiliations.add_jid_nickname" /></label>
            <input type="text" name="nickName" size="30" maxlength="255" value="<%= (nickName != null ? StringUtils.escapeHTMLTags(nickName) : "") %>" id="memberJID">
        </p>
    <%  } %>
    <p>
        <input type="submit" value="<fmt:message key="global.add" />">
    </p>

    <div class="jive-table" style="width:400px;">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th colspan="2"><fmt:message key="muc.room.affiliations.user" /></th>
            <th width="1%"><fmt:message key="global.delete" /></th>
        </tr>
    </thead>
    <tbody>
    <%-- Add owners section --%>
            <tr>
                <td colspan="2"><b><fmt:message key="muc.room.affiliations.room_owner" /></b></td>
                <td>&nbsp;</td>
            </tr>

        <%  if (room.getOwners().isEmpty()) { %>
            <tr>
                <td colspan="2" align="center"><i><fmt:message key="muc.room.affiliations.no_users" /></i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                ArrayList<JID> owners = new ArrayList<JID>(room.getOwners());
                Collections.sort(owners);
                for (JID user : owners) {
                    boolean isGroup = GroupJID.isGroup(user);
                    String username = JID.unescapeNode(user.getNode());
                    String userDisplay = isGroup ? ((GroupJID)user).getGroupName() : username + '@' + user.getDomain();
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                  <% if (isGroup) { %>
                    <img src="images/group.gif" width="16" height="16" align="top" title="<fmt:message key="muc.room.affiliations.group" />" alt="<fmt:message key="muc.room.affiliations.group" />"/>
                  <% } else { %>
                    <img src="images/user.gif" width="16" height="16" align="top" title="<fmt:message key="muc.room.affiliations.user" />" alt="<fmt:message key="muc.room.affiliations.user" />"/>
                  <% } %>
                    <a href="<%= isGroup ? "group-edit.jsp?group=" + URLEncoder.encode(userDisplay) : "user-properties.jsp?username=" + URLEncoder.encode(user.getNode()) %>">
                    <%= StringUtils.escapeHTMLTags(userDisplay) %></a>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= URLEncoder.encode(user.toString()) %>&delete=true&affiliation=owner&csrf=${csrf}"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                </td>
            </tr>
        <%  } } %>
    <%-- Add admins section --%>
            <tr>
                <td colspan="2"><b><fmt:message key="muc.room.affiliations.room_admin" /></b></td>
                <td>&nbsp;</td>
            </tr>

        <%  if (room.getAdmins().isEmpty()) { %>
            <tr>
                <td colspan="2" align="center"><i><fmt:message key="muc.room.affiliations.no_users" /></i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                ArrayList<JID> admins = new ArrayList<JID>(room.getAdmins());
                Collections.sort(admins);
                for (JID user : admins) {
                    boolean isGroup = GroupJID.isGroup(user);
                    String username = JID.unescapeNode(user.getNode());
                    String userDisplay = isGroup ? ((GroupJID)user).getGroupName() : username + '@' + user.getDomain();
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                  <% if (isGroup) { %>
                    <img src="images/group.gif" width="16" height="16" align="top" title="<fmt:message key="muc.room.affiliations.group" />" alt="<fmt:message key="muc.room.affiliations.group" />"/>
                  <% } else { %>
                    <img src="images/user.gif" width="16" height="16" align="top" title="<fmt:message key="muc.room.affiliations.user" />" alt="<fmt:message key="muc.room.affiliations.user" />"/>
                  <% } %>
                    <a href="<%= isGroup ? "group-edit.jsp?group=" + URLEncoder.encode(userDisplay) : "user-properties.jsp?username=" + URLEncoder.encode(user.getNode()) %>">
                    <%= StringUtils.escapeHTMLTags(userDisplay) %></a>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= URLEncoder.encode(user.toString()) %>&delete=true&affiliation=admin&csrf=${csrf}"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                </td>
            </tr>
        <%  } } %>
    <%-- Add members section --%>
            <tr>
                <td colspan="2"><b><fmt:message key="muc.room.affiliations.room_member" /></b></td>
                <td>&nbsp;</td>
            </tr>

        <%  if (room.getMembers().isEmpty()) { %>
            <tr>
                <td colspan="2" align="center"><i><fmt:message key="muc.room.affiliations.no_users" /></i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                ArrayList<JID> members = new ArrayList<JID>(room.getMembers());
                Collections.sort(members);
                for (JID user : members) {
                    boolean isGroup = GroupJID.isGroup(user);
                    String username = JID.unescapeNode(user.getNode());
                    String userDisplay = isGroup ? ((GroupJID)user).getGroupName() : username + '@' + user.getDomain();
                    String nickname = room.getReservedNickname(user);
                    nickname = (nickname == null ? "" : " (" + nickname + ")");
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                  <% if (isGroup) { %>
                    <img src="images/group.gif" width="16" height="16" align="top" title="<fmt:message key="muc.room.affiliations.group" />" alt="<fmt:message key="muc.room.affiliations.group" />"/>
                  <% } else { %>
                    <img src="images/user.gif" width="16" height="16" align="top" title="<fmt:message key="muc.room.affiliations.user" />" alt="<fmt:message key="muc.room.affiliations.user" />"/>
                  <% } %>
                    <a href="<%= isGroup ? "group-edit.jsp?group=" + URLEncoder.encode(userDisplay) : "user-properties.jsp?username=" + URLEncoder.encode(user.getNode()) %>">
                    <%= StringUtils.escapeHTMLTags(userDisplay) %></a><%= StringUtils.escapeHTMLTags(nickname) %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= URLEncoder.encode(user.toString()) %>&delete=true&affiliation=member&csrf=${csrf}"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                </td>
            </tr>
        <%  } } %>
    <%-- Add outcasts section --%>
            <tr>
                <td colspan="2"><b><fmt:message key="muc.room.affiliations.room_outcast" /></b></td>
                <td>&nbsp;</td>
            </tr>

        <%  if (room.getOutcasts().isEmpty()) { %>
            <tr>
                <td colspan="2" align="center"><i><fmt:message key="muc.room.affiliations.no_users" /></i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                ArrayList<JID> outcasts = new ArrayList<JID>(room.getOutcasts());
                Collections.sort(outcasts);
                for (JID user : outcasts) {
                    boolean isGroup = GroupJID.isGroup(user);
                    String username = JID.unescapeNode(user.getNode());
                    String userDisplay = isGroup ? ((GroupJID)user).getGroupName() : username + '@' + user.getDomain();
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                  <% if (isGroup) { %>
                    <img src="images/group.gif" width="16" height="16" align="top" title="<fmt:message key="muc.room.affiliations.group" />" alt="<fmt:message key="muc.room.affiliations.group" />"/>
                  <% } else { %>
                    <img src="images/user.gif" width="16" height="16" align="top" title="<fmt:message key="muc.room.affiliations.user" />" alt="<fmt:message key="muc.room.affiliations.user" />"/>
                  <% } %>
                    <a href="<%= isGroup ? "group-edit.jsp?group=" + URLEncoder.encode(userDisplay) : "user-properties.jsp?username=" + URLEncoder.encode(user.getNode()) %>">
                    <%= StringUtils.escapeHTMLTags(userDisplay) %></a>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= URLEncoder.encode(user.toString()) %>&delete=true&affiliation=outcast&csrf=${csrf}"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                </td>
            </tr>
        <%  } } %>
    </tbody>
    </table>
    </div>
    </div>
</fieldset>

</form>

    </body>
</html>
