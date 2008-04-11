<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software.
  - Use is subject to license terms.
--%>

<%@ page import="org.dom4j.Element,
                 org.jivesoftware.openfire.muc.ConflictException,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 org.jivesoftware.openfire.muc.NotAllowedException,
                 org.jivesoftware.util.ParamUtils,
                 org.xmpp.packet.IQ"
    errorPage="error.jsp"
%>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.muc.CannotBeInvitedException" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    JID roomJID = new JID(ParamUtils.getParameter(request,"roomJID"));
    String affiliation = ParamUtils.getParameter(request,"affiliation");
    String userJID = ParamUtils.getParameter(request,"userJID");
    String roomName = roomJID.getNode();

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
    // Handle an add
    if (add) {
        // do validation
        if (userJID == null || userJID.indexOf('@') == -1) {
            errors.put("userJID","userJID");
        }

        if (errors.size() == 0) {
            try {
                // Escape username
                String username = JID.escapeNode(userJID.substring(0, userJID.indexOf('@')));
                String rest = userJID.substring(userJID.indexOf('@'), userJID.length());
                userJID = username + rest;
                IQ iq = new IQ(IQ.Type.set);
                if ("owner".equals(affiliation) || "admin".equals(affiliation)) {
                    Element frag = iq.setChildElement("query", "http://jabber.org/protocol/muc#owner");
                    Element item = frag.addElement("item");
                    item.addAttribute("affiliation", affiliation);
                    item.addAttribute("jid", userJID);
                    // Send the IQ packet that will modify the room's configuration
                    room.getIQOwnerHandler().handleIQ(iq, room.getRole());
                }
                else if ("member".equals(affiliation) || "outcast".equals(affiliation)) {
                    Element frag = iq.setChildElement("query", "http://jabber.org/protocol/muc#admin");
                    Element item = frag.addElement("item");
                    item.addAttribute("affiliation", affiliation);
                    item.addAttribute("jid", userJID);
                    // Send the IQ packet that will modify the room's configuration
                    room.getIQAdminHandler().handleIQ(iq, room.getRole());
                }
                // Log the event
                webManager.logEvent("set MUC affilation to "+affiliation+" for "+userJID+" in "+roomName, null);
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
                errors.put("CannotBeInvitedException", "CannotBeInvitedExcpetion");
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
        room.getIQOwnerHandler().handleIQ(iq, room.getRole());
        // done, return
        response.sendRedirect("muc-room-affiliations.jsp?deletesuccess=true&roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
        return;
        }
        catch (ConflictException e) {
            errors.put("ConflictException","ConflictException");
        }
        catch (CannotBeInvitedException e) {
                errors.put("CannotBeInvitedException", "CannotBeInvitedExcpetion");
        }
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
<input type="hidden" name="roomJID" value="<%= roomJID.toBareJID() %>">

<fieldset>
    <legend><fmt:message key="muc.room.affiliations.permission" /></legend>
    <div>
    <p>
    <label for="memberJID"><fmt:message key="muc.room.affiliations.add_jid" /></label>
    <input type="text" name="userJID" size="30" maxlength="100" value="<%= (userJID != null ? userJID : "") %>" id="memberJID">
    <select name="affiliation">
        <option value="owner"><fmt:message key="muc.room.affiliations.owner" /></option>
        <option value="admin"><fmt:message key="muc.room.affiliations.admin" /></option>
        <option value="member"><fmt:message key="muc.room.affiliations.member" /></option>
        <option value="outcast"><fmt:message key="muc.room.affiliations.outcast" /></option>
    </select>
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
                ArrayList<String> owners = new ArrayList<String>(room.getOwners());
                Collections.sort(owners);
                for (String user : owners) {
                    String username = JID.unescapeNode(user.substring(0, user.indexOf('@')));
                    String rest = user.substring(user.indexOf('@'), user.length());
                    String userDisplay = username + rest;

        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= userDisplay %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= user %>&delete=true&affiliation=owner"
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
                ArrayList<String> admins = new ArrayList<String>(room.getAdmins());
                Collections.sort(admins);
                for (String user : admins) {
                    String username = JID.unescapeNode(user.substring(0, user.indexOf('@')));
                    String rest = user.substring(user.indexOf('@'), user.length());
                    String userDisplay = username + rest;
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= userDisplay %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= user %>&delete=true&affiliation=admin"
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
                ArrayList<String> members = new ArrayList<String>(room.getMembers());
                Collections.sort(members);
                for (String user : members) {
                    String username = JID.unescapeNode(user.substring(0, user.indexOf('@')));
                    String rest = user.substring(user.indexOf('@'), user.length());
                    String userDisplay = username + rest;

                    String nickname = room.getReservedNickname(user);
                    nickname = (nickname == null ? "" : " (" + nickname + ")");
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= userDisplay %><%=  nickname %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= user %>&delete=true&affiliation=member"
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
                ArrayList<String> outcasts = new ArrayList<String>(room.getOutcasts());
                Collections.sort(outcasts);
                for (String user : outcasts) {
                    String username = JID.unescapeNode(user.substring(0, user.indexOf('@')));
                    String rest = user.substring(user.indexOf('@'), user.length());
                    String userDisplay = username + rest;
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= userDisplay %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomJID=<%= URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>&userJID=<%= user %>&delete=true&affiliation=outcast"
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