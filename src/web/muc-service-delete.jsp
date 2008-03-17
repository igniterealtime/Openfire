<%--
  -	$Revision: 9934 $
  -	$Date: 2008-02-18 23:21:33 -0500 (Mon, 18 Feb 2008) $
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software.
  - Use is subject to license terms.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String mucname = ParamUtils.getParameter(request,"mucname");
    String reason = ParamUtils.getParameter(request,"reason");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Load the room object
    MultiUserChatService muc = webManager.getMultiUserChatManager().getMultiUserChatService(mucname);

    // Handle a room delete:
    if (delete) {
        // Delete the rooms in the service
        if (muc != null) {
            for (MUCRoom room : muc.getChatRooms()) {
                // If the room still exists then destroy it
                room.destroyRoom(null, reason);
            }
            // Log the event
            webManager.logEvent("destroyed MUC service "+mucname, "reason = "+reason);
            // Remove the service itself
            webManager.getMultiUserChatManager().removeMultiUserChatService(mucname);
        }
        // Done, so redirect
        response.sendRedirect("muc-service-summary.jsp?deletesuccess=true");
        return;
    }
%>

<html>
    <head>
        <title><fmt:message key="muc.service.delete.title"/></title>
        <meta name="subPageID" content="muc-service-delete"/>
        <meta name="extraParams" content="<%= "mucname="+URLEncoder.encode(mucname, "UTF-8") %>"/>
    </head>
    <body>

<p>
<fmt:message key="muc.service.delete.info" />
<b><a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucname, "UTF-8") %>"><%= mucname %></a></b>
<fmt:message key="muc.service.delete.detail" />
</p>

<form action="muc-service-delete.jsp">
<input type="hidden" name="mucname" value="<%= mucname %>">

<fieldset>
    <legend><fmt:message key="muc.service.delete.destructon_title" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td class="c1">
                <fmt:message key="muc.service.delete.service_name" />
            </td>
            <td>
                <%= mucname %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="muc.service.delete.reason" />
            </td>
            <td>
                <input type="text" size="50" maxlength="150" name="reason">
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="delete" value="<fmt:message key="muc.service.delete.destroy_service" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

    </body>
</html>