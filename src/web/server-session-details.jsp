<%--
  -	$RCSfile: server-session-details.jsp,v $
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 java.text.DateFormat,
                 java.text.NumberFormat,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.user.User,
                 org.xmpp.packet.JID,
                 org.xmpp.packet.Presence,
                 java.net.URLEncoder,
                 org.jivesoftware.messenger.server.IncomingServerSession,
                 org.jivesoftware.messenger.server.OutgoingServerSession"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<%  // Get parameters
    String hostname = ParamUtils.getParameter(request, "hostname");

    // Handle a "go back" click:
    if (request.getParameter("back") != null) {
        response.sendRedirect("server-session-summary.jsp");
        return;
    }

    // Get the session & address objects
    SessionManager sessionManager = webManager.getSessionManager();
    List<IncomingServerSession> inSessions = sessionManager.getIncomingServerSessions(hostname);
    OutgoingServerSession outSession = sessionManager.getOutgoingServerSession(hostname);

    // Number dateFormatter for all numbers on this page:
    NumberFormat numFormatter = NumberFormat.getNumberInstance();
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("server.session.details.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "server-session-details.jsp?hostname=" + hostname));
    pageinfo.setPageID("server-session-summary");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
<fmt:message key="server.session.details.info">
    <fmt:param value="<%= "<b>"+hostname+"</b>" %>" />
</fmt:message>

</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">
            <fmt:message key="server.session.details.title" />
        </th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">
            <fmt:message key="server.session.label.connection" />
        </td>
        <td>
        <% if (!inSessions.isEmpty() && outSession == null) { %>
            <img src="images/incoming_32x16.gif" width="32" height="16" border="0" title="<fmt:message key="server.session.connection.incoming" />">
            <fmt:message key="server.session.connection.incoming" />
        <% } else if (inSessions.isEmpty() && outSession != null) { %>
            <img src="images/outgoing_32x16.gif" width="32" height="16" border="0" title="<fmt:message key="server.session.connection.outgoing" />">
            <fmt:message key="server.session.connection.outgoing" />
        <% } else { %>
            <img src="images/both_32x16.gif" width="32" height="16" border="0" title="<fmt:message key="server.session.connection.both" />">
            <fmt:message key="server.session.connection.both" />
        <% } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.session.details.hostname" />
        </td>
        <td>
        <% if (!inSessions.isEmpty()) { %>
            <%= inSessions.get(0).getConnection().getInetAddress().getHostAddress() %>
            /
            <%= inSessions.get(0).getConnection().getInetAddress().getHostName() %>
        <% } else if (outSession != null) { %>
            <%= outSession.getConnection().getInetAddress().getHostAddress() %>
            /
            <%= outSession.getConnection().getInetAddress().getHostName() %>
        <% } %>
        </td>
    </tr>
</tbody>
</table>
</div>
<br>

<%  // Show details of the incoming sessions
    for (IncomingServerSession inSession : inSessions) {
%>
    <b><fmt:message key="server.session.details.incoming_session" /></b>
    <div class="jive-table">
    <table cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr>
        <th width="35%"><fmt:message key="server.session.details.streamid" /></th>
        <th width="20%"><fmt:message key="server.session.label.creation" /></th>
        <th width="20%"><fmt:message key="server.session.label.last_active" /></th>
        <th width="25%" nowrap><fmt:message key="server.session.details.incoming_statistics" /></th>
    </tr>
    <tr>
        <td><%= inSession.getStreamID()%></td>
        <td align="center"><%= (System.currentTimeMillis() - inSession.getCreationDate().getTime() < 24*60*60*1000) ? JiveGlobals.formatTime(inSession.getCreationDate()) : JiveGlobals.formatDateTime(inSession.getCreationDate()) %></td>
        <td align="center"><%= (System.currentTimeMillis() - inSession.getLastActiveDate().getTime() < 24*60*60*1000) ? JiveGlobals.formatTime(inSession.getLastActiveDate()) : JiveGlobals.formatDateTime(inSession.getLastActiveDate()) %></td>
        <td align="center"><%= numFormatter.format(inSession.getNumClientPackets()) %></td>
    </tr>
    </table>
    </div>

    <br>
<%  } %>

<%  // Show details of the incoming session
    if (outSession != null) {
%>
    <b><fmt:message key="server.session.details.outgoing_session" /></b>
    <div class="jive-table">
    <table cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr>
        <th width="35%"><fmt:message key="server.session.details.streamid" /></th>
        <th width="20%"><fmt:message key="server.session.label.creation" /></th>
        <th width="20%"><fmt:message key="server.session.label.last_active" /></th>
        <th width="25%" nowrap><fmt:message key="server.session.details.outgoing_statistics" /></th>
    </tr>
    <tr>
        <td><%= outSession.getStreamID()%></td>
        <td align="center"><%= (System.currentTimeMillis() - outSession.getCreationDate().getTime() < 24*60*60*1000) ? JiveGlobals.formatTime(outSession.getCreationDate()) : JiveGlobals.formatDateTime(outSession.getCreationDate()) %></td>
        <td align="center"><%= (System.currentTimeMillis() - outSession.getLastActiveDate().getTime() < 24*60*60*1000) ? JiveGlobals.formatTime(outSession.getLastActiveDate()) : JiveGlobals.formatDateTime(outSession.getLastActiveDate()) %></td>
        <td align="center"><%= numFormatter.format(outSession.getNumServerPackets()) %></td>
    </tr>
    </table>
    </div>

    <br>
<%  } %>

<br>

<form action="server-session-details.jsp">
<center>
<input type="submit" name="back" value="<fmt:message key="session.details.back_button" />">
</center>
</form>

<jsp:include page="bottom.jsp" flush="true" />
