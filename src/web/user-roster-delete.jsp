<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.xmpp.packet.JID,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.roster.Roster" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String username = ParamUtils.getParameter(request, "username");
    String jid = ParamUtils.getParameter(request, "jid");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-roster.jsp?username=" + URLEncoder.encode(username, "UTF-8"));
        return;
    }

    // Load the user's roster object
    Roster roster = webManager.getRosterManager().getRoster(username);

    // Handle a roster item delete:
    if (delete) {
        // Delete the roster item
        roster.deleteRosterItem(new JID(jid), false);
        // Log the event
        webManager.logEvent("deleted roster item from "+username, "roster item:\njid = "+jid);
        // Done, so redirect
        response.sendRedirect("user-roster.jsp?username="+URLEncoder.encode(username, "UTF-8")+"&deletesuccess=true");
        return;
    }
%>

<html>
    <head>
        <title><fmt:message key="user.roster.delete.title"/></title>
        <meta name="subPageID" content="user-roster"/>
        <meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, "UTF-8") %>"/>
    </head>
    <body>

    <p>
    <fmt:message key="user.roster.delete.info">
        <fmt:param value="<%= "<b>"+jid+"</b>" %>" />
        <fmt:param value="<%= "<b>"+username+"</b>" %>" />
    </fmt:message>
    </p>

    <form action="user-roster-delete.jsp">
    <input type="hidden" name="username" value="<%= username %>">
    <input type="hidden" name="jid" value="<%= jid %>">
    <input type="submit" name="delete" value="<fmt:message key="user.roster.delete.delete" />">
    <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
    </form>

    </body>
</html>
