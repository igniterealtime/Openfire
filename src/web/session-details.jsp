<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.openfire.PresenceManager,
                 org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.session.ClientSession,
                 org.jivesoftware.openfire.user.User,
                 org.jivesoftware.openfire.user.UserManager,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                 java.text.NumberFormat,
                 java.util.Collection"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters
    String jid = ParamUtils.getParameter(request, "jid");

    // Handle a "go back" click:
    if (request.getParameter("back") != null) {
        response.sendRedirect("session-summary.jsp");
        return;
    }

    // Get the session & address objects
    SessionManager sessionManager = webManager.getSessionManager();
    JID address = new JID(jid);
    org.jivesoftware.openfire.session.ClientSession currentSess = sessionManager.getSession(address);
    boolean isAnonymous = webManager.getXMPPServer().isLocal(address) &&
            !UserManager.getInstance().isRegisteredUser(address.getNode());

    // Get a presence manager
    PresenceManager presenceManager = webManager.getPresenceManager();

    // Get user object
    User user = null;
    if (!isAnonymous) {
        user = webManager.getUserManager().getUser(address.getNode());
    }

    // Handle a "message" click:
    if (request.getParameter("message") != null) {
        response.sendRedirect("user-message.jsp?username=" + URLEncoder.encode(user.getUsername(), "UTF-8"));
        return;
    }

    // See if there are multiple sessions for this user:
    Collection<ClientSession> sessions = null;
    int sessionCount = sessionManager.getSessionCount(address.getNode());
    if (!isAnonymous && sessionCount > 1) {
        sessions = sessionManager.getSessions(address.getNode());
    }

    // Number dateFormatter for all numbers on this page:
    NumberFormat numFormatter = NumberFormat.getNumberInstance();
%>

<html>
    <head>
        <title><fmt:message key="session.details.title"/></title>
        <meta name="pageID" content="session-summary"/>
    </head>
    <body>

<p>
<fmt:message key="session.details.info">
    <fmt:param value="<%= "<b>" + StringUtils.escapeForXML(address.toString()) + "</b>" %>" />
    <fmt:param value="<%= address.getNode() == null ? "" : "<b>"+address.getNode()+"</b>" %>" />
</fmt:message>

</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">
            <fmt:message key="session.details.title" />
        </th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.session_id" />
        </td>
        <td>
            <%= StringUtils.escapeForXML(address.toString()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.username" />
        </td>
        <td>
            <%  String n = address.getNode(); %>
            <%  if (isAnonymous) { %>

                <i> <fmt:message key="session.details.anonymous" /> </i> - <%= address.getResource()==null?"":StringUtils.escapeForXML(address.getResource()) %>

            <%  } else { %>

                <a href="user-properties.jsp?username=<%= URLEncoder.encode(n, "UTF-8") %>"><%= JID.unescapeNode(n) %></a>
                - <%= address.getResource()==null?"":StringUtils.escapeForXML(address.getResource()) %>

            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.status" />:
        </td>
        <td>
            <%
                int status = currentSess.getStatus();
                if (status == Session.STATUS_CLOSED) {
            %>
                <fmt:message key="session.details.close" />

            <%
                } else if (status == Session.STATUS_CONNECTED) {
            %>

                <fmt:message key="session.details.connect" />

            <%
                } else if (status == Session.STATUS_AUTHENTICATED) {
            %>

                <fmt:message key="session.details.authenticated" />

            <%
                } else {
            %>

                <fmt:message key="session.details.unknown" />

            <%
                }
            %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.presence" />:
        </td>
        <td>
            <%
                Presence.Show show = currentSess.getPresence().getShow();
                String statusTxt = currentSess.getPresence().getStatus();
                if (statusTxt != null) {
                    statusTxt = " -- " + StringUtils.escapeForXML(statusTxt);
                }
                else {
                    statusTxt = "";
                }
                if (show == Presence.Show.away) {
            %>

                <img src="images/im_away.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.away" />" alt="<fmt:message key="session.details.away" />">
                <fmt:message key="session.details.away" /> <%= statusTxt %>

            <%
                } else if (show == Presence.Show.chat) {
            %>
                <img src="images/im_free_chat.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.chat_available" />" alt="<fmt:message key="session.details.chat_available" />">
                <fmt:message key="session.details.chat_available" /> <%= statusTxt %>
            <%
                } else if (show == Presence.Show.dnd) {
            %>

                <img src="images/im_dnd.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.not_disturb" />" alt="<fmt:message key="session.details.not_disturb" />">
                <fmt:message key="session.details.not_disturb" /> <%= statusTxt %>

            <%
                } else if (show == null) {
            %>

                <img src="images/im_available.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.online" />" alt="<fmt:message key="session.details.online" />">
                <fmt:message key="session.details.online" /> <%= statusTxt %>

            <%
                } else if (show == Presence.Show.xa) {
            %>

                <img src="images/im_away.gif" width="16" height="16" border="0" title="<fmt:message key="session.details.extended" />" alt="<fmt:message key="session.details.extended" />">
                <fmt:message key="session.details.extended" /> <%= statusTxt %>

            <%
                } else {
            %>

                <fmt:message key="session.details.unknown" />

            <%
                }
            %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.priority" />
        </td>
        <td>
            <%= currentSess.getPresence().getPriority() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.session_created" />
        </td>
        <td>
            <%= JiveGlobals.formatDateTime(currentSess.getCreationDate()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.last_active" />
        </td>
        <td>
            <%= JiveGlobals.formatDateTime(currentSess.getLastActiveDate()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.statistics" />
        </td>
        <td>
            <fmt:message key="session.details.received" />
            <%= numFormatter.format(currentSess.getNumClientPackets()) %>/<%= numFormatter.format(currentSess.getNumServerPackets()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.hostname" />
        </td>
        <td>
            <%= currentSess.getHostAddress() %>
            /
            <%= currentSess.getHostName() %>
        </td>
    </tr>
</tbody>
</table>
</div>

<%  // Show a list of multiple user sessions if there is more than 1 session:
    if (sessionCount > 1) {
%>
    <p>
    <b><fmt:message key="session.details.multiple_session" /></b>
    </p>

    <div class="jive-table">
    <table cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr>
        <th>&nbsp;</th>
        <th><fmt:message key="session.details.name" /></th>
        <th><fmt:message key="session.details.resource" /></th>
        <th nowrap colspan="2"><fmt:message key="session.details.status" /></th>
        <th nowrap colspan="2"><fmt:message key="session.details.if_presence" /></th>
        <th><fmt:message key="session.details.priority" /></th>
        <th nowrap><fmt:message key="session.details.clientip" /></th>
        <th nowrap><fmt:message key="session.details.close_connect" /></th>
    </tr>

    <%  int count = 0;
        String linkURL = "session-details.jsp";
        for (ClientSession sess : sessions) {
            count++;
            boolean current = sess.getAddress().equals(address);
    %>
        <%@ include file="session-row.jspf" %>

    <%  } %>

    </table>
    </div>

    <br>

    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <tr>
        <td width="1%" nowrap>

            <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0">
            <tr class="jive-current"><td><img src="images/blank.gif" width="12" height="12" border="0" alt=""></td></tr>
            </table>
            </div>

        </td>
        <td width="99%">

            &nbsp; = <fmt:message key="session.details.session_detail" />

        </td>
    </tr>
    </table>

<%  } %>

<br>

<form action="session-details.jsp">
<input type="hidden" name="jid" value="<%= URLEncoder.encode(jid, "UTF-8") %>">
<center>
<%--<%  if (!isAnonymous && presenceManager.isAvailable(user)) { %>--%>
<%----%>
<%--    <input type="submit" name="message" value="Message this Session">--%>
<%----%>
<%--<%  } %>--%>
<input type="submit" name="back" value="<fmt:message key="session.details.back_button" />">   
</center>
</form>

    </body>
</html>