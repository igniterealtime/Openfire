<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software.
  - Use is subject to license terms.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.openfire.muc.*,
                 org.xmpp.packet.IQ,
                 org.dom4j.Element,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    String roomName = ParamUtils.getParameter(request,"roomName");
    String affiliation = ParamUtils.getParameter(request,"affiliation");
    String userJID = ParamUtils.getParameter(request,"userJID");

    boolean add = request.getParameter("add") != null;
    boolean addsuccess = request.getParameter("addsuccess") != null;
    boolean deletesuccess = request.getParameter("deletesuccess") != null;
    boolean delete = ParamUtils.getBooleanParameter(request,"delete");

    // Load the room object
    MUCRoom room = webManager.getMultiUserChatServer().getChatRoom(roomName);

    if (room == null) {
        // The requested room name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-room-summary.jsp");
        return;
    }

    Map errors = new HashMap();
    // Handle an add
    if (add) {
        // do validation
        if (userJID == null || userJID.indexOf('@') == -1) {
            errors.put("userJID","userJID");
        }

        if (errors.size() == 0) {
            try {
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
                // done, return
                response.sendRedirect("muc-room-affiliations.jsp?addsuccess=true&roomName="+URLEncoder.encode(roomName, "UTF-8"));
                return;
            }
            catch (ConflictException e) {
                errors.put("ConflictException","ConflictException");
            }
            catch (NotAllowedException e) {
                errors.put("NotAllowedException","NotAllowedException");
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
        response.sendRedirect("muc-room-affiliations.jsp?deletesuccess=true&roomName="+URLEncoder.encode(roomName, "UTF-8"));
        return;
        }
        catch (ConflictException e) {
            errors.put("ConflictException","ConflictException");
        }
    }
%>

<html>
    <head>
        <title><fmt:message key="muc.room.affiliations.title"/></title>
        <meta name="subPageID" content="muc-room-affiliations"/>
        <meta name="extraParams" content="<%= "roomName="+URLEncoder.encode(roomName, "UTF-8") %>"/>
        <meta name="helpPage" content="edit_group_chat_room_user_permissions.html"/>
    </head>
    <body>

<p>
<fmt:message key="muc.room.affiliations.info" />
<b><a href="muc-room-edit-form.jsp?roomName=<%= URLEncoder.encode(room.getName(), "UTF-8") %>"><%= room.getName() %></a></b>.
<fmt:message key="muc.room.affiliations.info_detail" />
</p>

<%  if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
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
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
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
<input type="hidden" name="roomName" value="<%= roomName %>">

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
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= user %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomName=<%= URLEncoder.encode(roomName, "UTF-8") %>&userJID=<%= user %>&delete=true&affiliation=owner"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
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
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= user %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomName=<%= URLEncoder.encode(roomName, "UTF-8") %>&userJID=<%= user %>&delete=true&affiliation=admin"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
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
                    String nickname = room.getReservedNickname(user);
                    nickname = (nickname == null ? "" : " (" + nickname + ")");
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= user %><%=  nickname %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomName=<%= URLEncoder.encode(roomName, "UTF-8") %>&userJID=<%= user %>&delete=true&affiliation=member"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
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
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= user %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomName=<%= URLEncoder.encode(roomName, "UTF-8") %>&userJID=<%= user %>&delete=true&affiliation=outcast"
                     title="<fmt:message key="global.click_delete" />"
                     onclick="return confirm('<fmt:message key="muc.room.affiliations.confirm_removed" />');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
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