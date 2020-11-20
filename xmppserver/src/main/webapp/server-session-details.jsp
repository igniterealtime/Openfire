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

<%@ page import="org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.session.IncomingServerSession,
                 org.jivesoftware.openfire.session.OutgoingServerSession,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 java.text.NumberFormat"
    errorPage="error.jsp"
%>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>
<%@ page import="org.jivesoftware.openfire.session.LocalSession" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

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
    List<OutgoingServerSession> outSessions = sessionManager.getOutgoingServerSessions(hostname);

    // Number dateFormatter for all numbers on this page:
    NumberFormat numFormatter = NumberFormat.getNumberInstance();
    final boolean clusteringEnabled = ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting();
    final Logger Log = LoggerFactory.getLogger("server-session-details.jsp");
%>

<html>
    <head>
        <title><fmt:message key="server.session.details.title"/></title>
        <meta name="pageID" content="server-session-summary"/>
    </head>
    <body>

<p>
<fmt:message key="server.session.details.info">
    <fmt:param value="<b>${fn:escapeXml(hostname)}</b>" />
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
        <% if (!inSessions.isEmpty() && outSessions.isEmpty()) { %>
            <img src="images/incoming_32x16.gif" width="32" height="16" border="0" title="<fmt:message key='server.session.connection.incoming' />" alt="<fmt:message key='server.session.connection.incoming' />">
            <fmt:message key="server.session.connection.incoming" />
        <% } else if (inSessions.isEmpty() && !outSessions.isEmpty()) { %>
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
        <% try {
            if (!inSessions.isEmpty()) { %>
                <%= inSessions.get(0).getHostAddress() %>
                /
                <%= inSessions.get(0).getHostName() %>
            <% } else if (!outSessions.isEmpty()) { %>
                <%= outSessions.get(0).getHostAddress() %>
                /
                <%= outSessions.get(0).getHostName() %>
            <% }
           } catch (java.net.UnknownHostException e) { %>
                Invalid session/connection
        <% } %>
        </td>
    </tr>
</tbody>
</table>

</div>
<br>
    <%  // Show Software Version if there is :
       try {
        if (!inSessions.get(0).getSoftwareVersion().isEmpty()) {
    %>
        <div class="jive-table">
            <table cellpadding="3" cellspacing="1" border="0" width="100%">
                <thead>
                    <tr>
                        <th colspan="2">
                            <fmt:message key="session.details.software_version"/>
                        </th>
                    </tr>
                </thead>
                <tbody>
                    <% 
                        Map<String, String> treeMap = new TreeMap<String, String>(inSessions.get(0).getSoftwareVersion());
                        for (Map.Entry<String, String> entry : treeMap.entrySet()){ %>
                            <tr>
                                <td class="c1">
                                    <%= StringUtils.escapeHTMLTags(entry.getKey().substring(0, 1).toUpperCase()+""+entry.getKey().substring(1)) %>:
                                </td>
                                <td>
                                    <%= StringUtils.escapeHTMLTags(entry.getValue())%>
                                </td>
                            </tr>
                        <% 
                        }
                    %>
                </tbody>
            </table>
        </div>
    <%  } 
    } catch (Exception e) { 
       Log.error(e.getMessage(), e);%>
        Invalid session/connection
    <%} %>
<br>


<%  // Show details of the incoming sessions'
    if (!inSessions.isEmpty()) {
%>
<b><fmt:message key="server.session.details.incoming_session" /></b>
<div class="jive-table">
    <table cellpadding="3" cellspacing="1" border="0" width="100%">
        <tr>
            <th width="35%" colspan="2" nowrap><fmt:message key="server.session.details.streamid" /></th>
            <% if (clusteringEnabled) { %>
            <th width="1%" nowrap><fmt:message key="server.session.details.node"/></th>
            <% } %>
            <th width="10%" nowrap><fmt:message key="server.session.details.authentication"/></th>
            <th width="10%" nowrap><fmt:message key="server.session.details.cipher"/></th>
            <th width="1%" nowrap><fmt:message key="server.session.label.creation" /></th>
            <th width="1%" nowrap><fmt:message key="server.session.label.last_active" /></th>
            <th width="1%" nowrap><fmt:message key="server.session.details.incoming_statistics" /></th>
            <th width="1%" nowrap><fmt:message key="server.session.details.outgoing_statistics" /></th>
        </tr>
<%
    for (IncomingServerSession inSession : inSessions) {
%>
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
        <% if (clusteringEnabled) { %>
        <td nowrap><% if (inSession instanceof LocalSession) { %><fmt:message key="server.session.details.local"/><% } else { %><fmt:message key="server.session.details.remote"/><% } %></td>
        <% } %>
        <td nowrap><% if (inSession.isUsingServerDialback()) { %><fmt:message key="server.session.details.dialback"/><% } else { %><fmt:message key="server.session.details.tlsauth"/><% } %></td>
        <td><%= inSession.getCipherSuiteName() %></td>
        <td nowrap><%= sameCreationDay ? JiveGlobals.formatTime(creationDate) : JiveGlobals.formatDateTime(creationDate) %></td>
        <td nowrap><%= sameActiveDay ? JiveGlobals.formatTime(lastActiveDate) : JiveGlobals.formatDateTime(lastActiveDate) %></td>
        <td align="center" nowrap><%= numFormatter.format(inSession.getNumClientPackets()) %></td>
        <td align="center" nowrap><%= numFormatter.format(inSession.getNumServerPackets()) %></td>
    </tr>
<%  } %>
    </table>
</div>

<br>
<%  } %>

<%  // Show details of the outgoing sessions
    if (!outSessions.isEmpty()) {
%>
<b><fmt:message key="server.session.details.outgoing_session" /></b>
<div class="jive-table">
    <table cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr>
        <th width="35%" colspan="2" nowrap><fmt:message key="server.session.details.streamid" /></th>
        <% if (clusteringEnabled) { %>
        <th width="1%" nowrap><fmt:message key="server.session.details.node"/></th>
        <% } %>
        <th width="10%" nowrap><fmt:message key="server.session.details.authentication"/></th>
        <th width="10%" nowrap><fmt:message key="server.session.details.cipher"/></th>
        <th width="1%" nowrap><fmt:message key="server.session.label.creation" /></th>
        <th width="1%" nowrap><fmt:message key="server.session.label.last_active" /></th>
        <th width="1%" nowrap><fmt:message key="server.session.details.incoming_statistics" /></th>
        <th width="1%" nowrap><fmt:message key="server.session.details.outgoing_statistics" /></th>
    </tr>
<%
    for (OutgoingServerSession outSession : outSessions) {
%>
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
        <% if (clusteringEnabled) { %>
        <td nowrap><% if (outSession instanceof LocalSession) { %><fmt:message key="server.session.details.local"/><% } else { %><fmt:message key="server.session.details.remote"/><% } %></td>
        <% } %>
        <td nowrap><% if (outSession.isUsingServerDialback()) { %><fmt:message key="server.session.details.dialback"/><% } else { %><fmt:message key="server.session.details.tlsauth"/><% } %></td>
        <td><%= outSession.getCipherSuiteName() %></td>
        <td nowrap><%= sameCreationDay ? JiveGlobals.formatTime(creationDate) : JiveGlobals.formatDateTime(creationDate) %></td>
        <td nowrap><%= sameActiveDay ? JiveGlobals.formatTime(lastActiveDate) : JiveGlobals.formatDateTime(lastActiveDate) %></td>
        <td align="center" nowrap><%= numFormatter.format(outSession.getNumClientPackets()) %></td>
        <td align="center" nowrap><%= numFormatter.format(outSession.getNumServerPackets()) %></td>
    </tr>
<%  } %>
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
