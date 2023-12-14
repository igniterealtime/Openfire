<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.util.StringUtils,
                 org.xmpp.packet.IQ"
    errorPage="error.jsp"
%>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="org.jivesoftware.openfire.muc.CannotBeInvitedException" %>
<%@ page import="java.util.*" %>
<%@ page import="java.util.stream.Collectors" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="admin" uri="admin" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    JID roomJID = new JID(ParamUtils.getParameter(request,"roomJID"));
    String affiliation = ParamUtils.getParameter(request,"affiliation");
    String userJID = ParamUtils.getParameter(request,"userJID");
    String nickName = ParamUtils.getParameter(request,"nickName");
    String roomName = roomJID.getNode();
    String[] groupNames = ParamUtils.getParameters(request, "groupNames");

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

    Map<String,String> errors = new HashMap<>();
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

        if (errors.isEmpty()) {
            try {
                List<JID> memberJIDs = new ArrayList<>();
                if (userJID != null) {
                    final JID addMe;
                    // Escape username
                    if (userJID.indexOf('@') == -1) {
                        String username = JID.escapeNode(userJID);
                        String domain = webManager.getXMPPServer().getServerInfo().getXMPPDomain();
                        addMe = new JID(username, domain, null);
                        userJID = username + '@' + domain;
                    }
                    else {
                        String username = JID.escapeNode(userJID.substring(0, userJID.indexOf('@')));
                        String rest = userJID.substring(userJID.indexOf('@') + 1);
                        addMe = new JID(username, rest.trim(), null);
                        userJID = username + rest.trim();
                    }
                    memberJIDs.add(addMe);
                }
                if (groupNames != null) {
                    // create a group JID for each group
                    for (String groupName : groupNames) {
                        GroupJID groupJID = new GroupJID(URLDecoder.decode(groupName, "UTF-8"));
                        memberJIDs.add(groupJID);
                    }
                }
                IQ iq = new IQ(IQ.Type.set);
                Element frag = iq.setChildElement("query", "http://jabber.org/protocol/muc#admin");
                for (JID memberJID : memberJIDs){
                    Element item = frag.addElement("item");
                    item.addAttribute("affiliation", affiliation);
                    item.addAttribute("jid", memberJID.toString());
                    // Set the name of the user as the nickname of the member when no nickname is provided.
                    item.addAttribute("nick", nickName != null ? nickName : memberJID.getNode());
                }
                // Send the IQ packet that will modify the room's configuration
                room.getIQAdminHandler().handleIQ(iq, room.getRole());
                webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).syncChatRoom(room);

                // Log the event
                for (JID memberJID : memberJIDs) {
                    if (memberJID instanceof GroupJID) {
                        webManager.logEvent("set MUC affiliation to " + affiliation + " for members of group " + ((GroupJID) memberJID).getGroupName() + " in " + roomName, null);
                    } else {
                        webManager.logEvent("set MUC affiliation to " + affiliation + " for " + memberJID + " in " + roomName, null);
                    }
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
        webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).syncChatRoom(room);

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

    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("addsuccess", addsuccess);
    pageContext.setAttribute("deletesuccess", deletesuccess);
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

<c:choose>
    <c:when test="${not empty errors}">
        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                    <c:when test="${err.key eq 'ConflictException'}"><fmt:message key="muc.room.affiliations.error_removing_user" /></c:when>
                    <c:when test="${err.key eq 'NotAllowedException'}"><fmt:message key="muc.room.affiliations.error_banning_user" /></c:when>
                    <c:otherwise><fmt:message key="muc.room.affiliations.error_adding_user" /></c:otherwise>
                </c:choose>
            </admin:infobox>
        </c:forEach>
    </c:when>
    <c:when test="${addsuccess}">
        <admin:infobox type="success">
            <fmt:message key="muc.room.affiliations.user_added" />
        </admin:infobox>
    </c:when>
    <c:when test="${deletesuccess}">
        <admin:infobox type="success">
            <fmt:message key="muc.room.affiliations.user_removed" />
        </admin:infobox>
    </c:when>
</c:choose>

<form action="muc-room-affiliations.jsp?add" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
<input type="hidden" name="roomJID" value="<%= roomJID.toBareJID() %>">

<fieldset>
    <legend><fmt:message key="muc.room.affiliations.permission" /></legend>
    <div>
    <p>
    <label for="groupJIDs"><fmt:message key="muc.room.affiliations.add_group" /></label><br/>
    <select name="groupNames" size="6" multiple style="width:400px;font-family:verdana,arial,helvetica,sans-serif;font-size:8pt;" id="groupJIDs">
    <% final List<Group> groups = webManager.getGroupManager().getGroups().stream()
            .sorted(Comparator.comparing(g->g.getName().toLowerCase()))
            .collect(Collectors.toList());
        for (Group g : groups) {	%>
        <option value="<%= URLEncoder.encode(g.getName(), "UTF-8") %>"
         <%= (StringUtils.contains(groupNames, g.getName()) ? "selected" : "") %>
         ><%= StringUtils.escapeHTMLTags(g.getName()) %></option>
    <%  } %>
    </select>
    </p>
    <p>
        <select name="affiliation" onchange="document.getElementById('memberNickParagraph').style.visibility = (this.selectedIndex === 2 ? 'visible' : 'hidden')">
            <option value="owner"><fmt:message key="muc.room.affiliations.owner" /></option>
            <option value="admin"><fmt:message key="muc.room.affiliations.admin" /></option>
            <option value="member"><fmt:message key="muc.room.affiliations.member" /></option>
            <option value="outcast"><fmt:message key="muc.room.affiliations.outcast" /></option>
        </select>
    </p>
    <p>
        <label for="memberJID"><fmt:message key="muc.room.affiliations.add_jid" /></label>
        <input type="text" name="userJID" size="30" maxlength="255" value="<%= (userJID != null ? StringUtils.escapeHTMLTags(userJID) : "") %>" id="memberJID">
    </p>
    <p id="memberNickParagraph" style="visibility: hidden">
        <label for="memberNick"><fmt:message key="muc.room.affiliations.add_jid_nickname" /></label>
        <input type="text" name="nickName" size="30" maxlength="255" value="<%= (nickName != null ? StringUtils.escapeHTMLTags(nickName) : "") %>" id="memberNick">
    </p>
    <p>
        <input type="submit" value="<fmt:message key="global.add" />">
    </p>

    <div class="jive-table" style="width:400px;">
    <table>
    <thead>
        <tr>
            <th colspan="2"><fmt:message key="muc.room.affiliations.user" /></th>
            <th style="width: 1%"><fmt:message key="global.delete" /></th>
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
                <td colspan="2" style="text-align: center"><i><fmt:message key="muc.room.affiliations.no_users" /></i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                ArrayList<JID> owners = new ArrayList<>(room.getOwners());
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
                    <img src="images/group.gif" title="<fmt:message key="muc.room.affiliations.group" />" alt="<fmt:message key="muc.room.affiliations.group" />"/>
                  <% } else { %>
                    <img src="images/user.gif" title="<fmt:message key="muc.room.affiliations.user" />" alt="<fmt:message key="muc.room.affiliations.user" />"/>
                  <% } %>
                    <a href="<%= isGroup ? "group-edit.jsp?group=" + URLEncoder.encode(userDisplay, "UTF-8") : "user-properties.jsp?username=" + URLEncoder.encode(user.getNode(), "UTF-8") %>">
                    <%= StringUtils.escapeHTMLTags(userDisplay) %></a>
                </td>
                <td style="width: 1%; text-align: center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= URLEncoder.encode(user.toString(), "UTF-8") %>&delete=true&affiliation=owner&csrf=${csrf}"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" alt=""></a>
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
                <td colspan="2" style="text-align: center"><i><fmt:message key="muc.room.affiliations.no_users" /></i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                ArrayList<JID> admins = new ArrayList<>(room.getAdmins());
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
                    <img src="images/group.gif" title="<fmt:message key="muc.room.affiliations.group" />" alt="<fmt:message key="muc.room.affiliations.group" />"/>
                  <% } else { %>
                    <img src="images/user.gif" title="<fmt:message key="muc.room.affiliations.user" />" alt="<fmt:message key="muc.room.affiliations.user" />"/>
                  <% } %>
                    <a href="<%= isGroup ? "group-edit.jsp?group=" + URLEncoder.encode(userDisplay, "UTF-8") : "user-properties.jsp?username=" + URLEncoder.encode(user.getNode(), "UTF-8") %>">
                    <%= StringUtils.escapeHTMLTags(userDisplay) %></a>
                </td>
                <td style="width: 1%; text-align: center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= URLEncoder.encode(user.toString(), "UTF-8") %>&delete=true&affiliation=admin&csrf=${csrf}"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" alt=""></a>
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
                <td colspan="2" style="text-align: center"><i><fmt:message key="muc.room.affiliations.no_users" /></i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                ArrayList<JID> members = new ArrayList<>(room.getMembers());
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
                    <img src="images/group.gif" title="<fmt:message key="muc.room.affiliations.group" />" alt="<fmt:message key="muc.room.affiliations.group" />"/>
                  <% } else { %>
                    <img src="images/user.gif" title="<fmt:message key="muc.room.affiliations.user" />" alt="<fmt:message key="muc.room.affiliations.user" />"/>
                  <% } %>
                    <a href="<%= isGroup ? "group-edit.jsp?group=" + URLEncoder.encode(userDisplay, "UTF-8") : "user-properties.jsp?username=" + URLEncoder.encode(user.getNode(), "UTF-8") %>">
                    <%= StringUtils.escapeHTMLTags(userDisplay) %></a><%= StringUtils.escapeHTMLTags(nickname) %>
                </td>
                <td style="width: 1%; text-align: center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= URLEncoder.encode(user.toString(), "UTF-8") %>&delete=true&affiliation=member&csrf=${csrf}"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" alt=""></a>
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
                <td colspan="2" style="text-align: center"><i><fmt:message key="muc.room.affiliations.no_users" /></i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                ArrayList<JID> outcasts = new ArrayList<>(room.getOutcasts());
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
                    <img src="images/group.gif" title="<fmt:message key="muc.room.affiliations.group" />" alt="<fmt:message key="muc.room.affiliations.group" />"/>
                  <% } else { %>
                    <img src="images/user.gif" title="<fmt:message key="muc.room.affiliations.user" />" alt="<fmt:message key="muc.room.affiliations.user" />"/>
                  <% } %>
                    <a href="<%= isGroup ? "group-edit.jsp?group=" + URLEncoder.encode(userDisplay, "UTF-8") : "user-properties.jsp?username=" + URLEncoder.encode(user.getNode(), "UTF-8") %>">
                    <%= StringUtils.escapeHTMLTags(userDisplay) %></a>
                </td>
                <td style="width: 1%; text-align: center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= URLEncoder.encode(user.toString(), "UTF-8") %>&delete=true&affiliation=outcast&csrf=${csrf}"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" alt=""></a>
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
