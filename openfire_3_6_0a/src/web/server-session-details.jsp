<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.session.IncomingServerSession,
                 org.jivesoftware.openfire.session.OutgoingServerSession,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                java.text.NumberFormat"
    errorPage="error.jsp"
%>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters
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

<html>
    <head>
        <title><fmt:message key="server.session.details.title"/></title>
        <meta name="pageID" content="server-session-summary"/>
    </head>
    <body>

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
            <img src="images/incoming_32x16.gif" width="32" height="16" border="0" title="<fmt:message key='server.session.connection.incoming' />" alt="<fmt:message key='server.session.connection.incoming' />">
            <fmt:message key="server.session.connection.incoming" />
        <% } else if (inSessions.isEmpty() && outSession != null) { %>
            <img src="images/outgoing_32x16.gif" width="32" height="16" border="0" title="<fmt:message key='server.session.connection.outgoing' />" alt="<fmt:message key='server.session.connection.outgoing' />">
            <fmt:message key="server.session.connection.outgoing" />
        <% } else { %>
            <img src="images/both_32x16.gif" width="32" height="16" border="0" title="<fmt:message key='server.session.connection.both' />" alt="<fmt:message key='server.session.connection.both' />">
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
            <%= inSessions.get(0).getHostAddress() %>
            /
            <%= inSessions.get(0).getHostName() %>
        <% } else if (outSession != null) { %>
            <%= outSession.getHostAddress() %>
            /
            <%= outSession.getHostName() %>
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
        <th width="35%" colspan="2"><fmt:message key="server.session.details.streamid" /></th>
        <th width="20%"><fmt:message key="server.session.label.creation" /></th>
        <th width="20%"><fmt:message key="server.session.label.last_active" /></th>
        <th width="25%" nowrap><fmt:message key="server.session.details.incoming_statistics" /></th>
    </tr>
    <tr>
        <%  if (inSession.isSecure()) { %>
            <td width="1%">
                <img src="images/lock.gif" width="16" height="16" border="0" alt="">
            </td>
         <% } else { %>
            <td width="1%"><img src="images/blank.gif" width="1" height="1" alt=""></td>
         <% } %>
        <%
            Date creationDate = inSession.getCreationDate();
            Date lastActiveDate = inSession.getLastActiveDate();

            Calendar creationCal = Calendar.getInstance();
            creationCal.setTime(creationDate);

            Calendar lastActiveCal = Calendar.getInstance();
            lastActiveCal.setTime(lastActiveDate);

            Calendar nowCal = Calendar.getInstance();

            boolean sameCreationDay = nowCal.get(Calendar.DAY_OF_YEAR) == creationCal.get(Calendar.DAY_OF_YEAR) && nowCal.get(Calendar.YEAR) == creationCal.get(Calendar.YEAR);
            boolean sameActiveDay = nowCal.get(Calendar.DAY_OF_YEAR) == lastActiveCal.get(Calendar.DAY_OF_YEAR) && nowCal.get(Calendar.YEAR) == lastActiveCal.get(Calendar.YEAR);
        %>
        <td><%= inSession.getStreamID()%></td>
        <td align="center"><%= sameCreationDay ? JiveGlobals.formatTime(creationDate) : JiveGlobals.formatDateTime(creationDate) %></td>
        <td align="center"><%= sameActiveDay ? JiveGlobals.formatTime(lastActiveDate) : JiveGlobals.formatDateTime(lastActiveDate) %></td>
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
        <th width="35%" colspan="2"><fmt:message key="server.session.details.streamid" /></th>
        <th width="20%"><fmt:message key="server.session.label.creation" /></th>
        <th width="20%"><fmt:message key="server.session.label.last_active" /></th>
        <th width="25%" nowrap><fmt:message key="server.session.details.outgoing_statistics" /></th>
    </tr>
    <tr>
        <%  if (outSession.isSecure()) { %>
        <td width="1%">
            <img src="images/lock.gif" width="16" height="16" border="0" alt="">
        </td>
         <% } else { %>
        <td width="1%"><img src="images/blank.gif" width="1" height="1" alt=""></td>
         <% } %>
        <%
            Date creationDate = outSession.getCreationDate();
            Date lastActiveDate = outSession.getLastActiveDate();

            Calendar creationCal = Calendar.getInstance();
            creationCal.setTime(creationDate);

            Calendar lastActiveCal = Calendar.getInstance();
            lastActiveCal.setTime(lastActiveDate);

            Calendar nowCal = Calendar.getInstance();

            boolean sameCreationDay = nowCal.get(Calendar.DAY_OF_YEAR) == creationCal.get(Calendar.DAY_OF_YEAR) && nowCal.get(Calendar.YEAR) == creationCal.get(Calendar.YEAR);
            boolean sameActiveDay = nowCal.get(Calendar.DAY_OF_YEAR) == lastActiveCal.get(Calendar.DAY_OF_YEAR) && nowCal.get(Calendar.YEAR) == lastActiveCal.get(Calendar.YEAR);
        %>
        <td><%= outSession.getStreamID()%></td>
        <td align="center"><%= sameCreationDay ? JiveGlobals.formatTime(creationDate) : JiveGlobals.formatDateTime(creationDate) %></td>
        <td align="center"><%= sameActiveDay ? JiveGlobals.formatTime(lastActiveDate) : JiveGlobals.formatDateTime(lastActiveDate) %></td>
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

    </body>
</html>