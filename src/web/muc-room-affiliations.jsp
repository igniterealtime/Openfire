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

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*,
                 java.util.Iterator,
                 org.jivesoftware.messenger.muc.*,
                 org.xmpp.packet.IQ,
                 org.dom4j.Element"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<% admin.init(request, response, session, application, out ); %>

<%  // Get parameters
    String roomName = ParamUtils.getParameter(request,"roomName");
    String affiliation = ParamUtils.getParameter(request,"affiliation");
    String userJID = ParamUtils.getParameter(request,"userJID");

    boolean add = request.getParameter("add") != null;
    boolean addsuccess = request.getParameter("addsuccess") != null;
    boolean deletesuccess = request.getParameter("deletesuccess") != null;
    boolean delete = ParamUtils.getBooleanParameter(request,"delete");

    // Load the room object
    MUCRoom room = admin.getMultiUserChatServer().getChatRoom(roomName);

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
                response.sendRedirect("muc-room-affiliations.jsp?addsuccess=true&roomName="+roomName);
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
        response.sendRedirect("muc-room-affiliations.jsp?deletesuccess=true&roomName="+roomName);
        return;
        }
        catch (ConflictException e) {
            errors.put("ConflictException","ConflictException");
        }
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "User Permissions";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Room Permissions", "muc-room-affiliations.jsp?roomName="+roomName));
    pageinfo.setSubPageID("muc-room-affiliations");
    pageinfo.setExtraParams("roomName="+roomName);
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Below is the list of room owners, administrators, members and outcasts of the the room
<b><a href="muc-room-edit-form.jsp?roomName=<%= room.getName() %>"><%= room.getName() %></a></b>.
Room owners can alter the room configuration, grant ownership and administrative privileges to users
and destroy the room. Room administrators can ban, grant membership and moderator privileges to
users. Room members are the only allowed users to join the room when it is configured as members-only.
Whilst room outcasts are users who have been banned from the room.
</p>

<%  if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <%  if (errors.containsKey("ConflictException")) { %>

        Error removing the user. The room must have at least one owner.

        <%  } else if (errors.containsKey("NotAllowedException")) { %>

        Error banning the user. Owners or Administratos cannot be banned.

        <%  } else { %>

        Error adding the user. Please verify the JID is correct.

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

            User added successfully.

        <%  } else if (deletesuccess) { %>

            User removed successfully.

        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="muc-room-affiliations.jsp?add" method="post">
<input type="hidden" name="roomName" value="<%= roomName %>">

<fieldset>
    <legend>User Permissions</legend>
    <div>
    <p>
    <label for="memberJID">Add User (JID):</label>
    <input type="text" name="userJID" size="30" maxlength="100" value="<%= (userJID != null ? userJID : "") %>" id="memberJID">
    <select name="affiliation">
        <option value="owner">Owner</option>
        <option value="admin">Admin</option>
        <option value="member">Member</option>
        <option value="outcast">Outcast</option>
    </select>
    <input type="submit" value="Add">
    </p>

    <div class="jive-table" style="width:400px;">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th colspan="2">User</th>
            <th width="1%">Delete</th>
        </tr>
    </thead>
    <tbody>
    <%-- Add owners section --%>
            <tr>
                <td colspan="2"><b>Room Owners</b></td>
                <td>&nbsp;</td>
            </tr>

        <%  if (room.getOwners().isEmpty()) { %>
            <tr>
                <td colspan="2" align="center"><i>No Users</i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                for (String user : room.getOwners()) {
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= user %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomName=<%= roomName %>&userJID=<%= user %>&delete=true&affiliation=owner"
                     title="Click to delete..."
                     onclick="return confirm('Are you sure you want to remove this user from the list?');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
                </td>
            </tr>
        <%  } } %>
    <%-- Add admins section --%>
            <tr>
                <td colspan="2"><b>Room Admins</b></td>
                <td>&nbsp;</td>
            </tr>

        <%  if (room.getAdmins().isEmpty()) { %>
            <tr>
                <td colspan="2" align="center"><i>No Users</i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                for (String user : room.getAdmins()) {
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= user %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomName=<%= roomName %>&userJID=<%= user %>&delete=true&affiliation=admin"
                     title="Click to delete..."
                     onclick="return confirm('Are you sure you want to remove this user from the list?');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
                </td>
            </tr>
        <%  } } %>
    <%-- Add members section --%>
            <tr>
                <td colspan="2"><b>Room Members</b></td>
                <td>&nbsp;</td>
            </tr>

        <%  if (room.getMembers().isEmpty()) { %>
            <tr>
                <td colspan="2" align="center"><i>No Users</i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                for (String user : room.getMembers()) {
                    String nickname = room.getReservedNickname(user);
                    nickname = (nickname == null ? "" : " (" + nickname + ")");
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= user %><%=  nickname %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomName=<%= roomName %>&userJID=<%= user %>&delete=true&affiliation=member"
                     title="Click to delete..."
                     onclick="return confirm('Are you sure you want to remove this user from the list?');"
                     ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
                </td>
            </tr>
        <%  } } %>
    <%-- Add outcasts section --%>
            <tr>
                <td colspan="2"><b>Room Outcasts</b></td>
                <td>&nbsp;</td>
            </tr>

        <%  if (room.getOutcasts().isEmpty()) { %>
            <tr>
                <td colspan="2" align="center"><i>No Users</i></td>
                <td>&nbsp;</td>
            </tr>
        <%  }
            else {
                for (String user : room.getOutcasts()) {
        %>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <%= user %>
                </td>
                <td width="1%" align="center">
                    <a href="muc-room-affiliations.jsp?roomName=<%= roomName %>&userJID=<%= user %>&delete=true&affiliation=outcast"
                     title="Click to delete..."
                     onclick="return confirm('Are you sure you want to remove this user from the list?');"
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

<jsp:include page="bottom.jsp" flush="true" />
