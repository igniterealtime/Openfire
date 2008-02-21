<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.session.ComponentSession,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils"
    errorPage="error.jsp"
%>
<%@ page import="java.text.NumberFormat" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    String jid = ParamUtils.getParameter(request, "jid");

    // Handle a "go back" click:
    if (request.getParameter("back") != null) {
        response.sendRedirect("component-session-summary.jsp");
        return;
    }

    // Get the session & address objects
    SessionManager sessionManager = webManager.getSessionManager();
    ComponentSession componentSession = sessionManager.getComponentSession(jid);

    // Number dateFormatter for all numbers on this page:
    NumberFormat numFormatter = NumberFormat.getNumberInstance();
%>

<html>
    <head>
        <title><fmt:message key="component.session.details.title"/></title>
        <meta name="pageID" content="component-session-summary"/>
    </head>
    <body>

<p>
<fmt:message key="component.session.details.info">
    <fmt:param value="<%= "<b>"+jid+"</b>" %>" />
</fmt:message>

</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">
            <fmt:message key="component.session.details.title" />
        </th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.domain" />
        </td>
        <td>
            <%= componentSession.getAddress() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.name" />
        </td>
        <td>
            <%= componentSession.getExternalComponent().getName() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.category" />:
        </td>
        <td>
            <%= componentSession.getExternalComponent().getCategory() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.type" />:
        </td>
        <td>
            <% if ("gateway".equals(componentSession.getExternalComponent().getCategory())) {
                if ("msn".equals(componentSession.getExternalComponent().getType())) { %>
                <img src="images/msn.gif" width="16" height="16" border="0" alt="MSN">&nbsp;
             <% }
                else if ("aim".equals(componentSession.getExternalComponent().getType())) { %>
                <img src="images/aim.gif" width="16" height="16" border="0" alt="AIM">&nbsp;
             <% }
                else if ("yahoo".equals(componentSession.getExternalComponent().getType())) { %>
                <img src="images/yahoo.gif" width="22" height="16" border="0" alt="Yahoo!">&nbsp;
             <% }
                else if ("icq".equals(componentSession.getExternalComponent().getType())) { %>
                <img src="images/icq.gif" width="16" height="16" border="0" alt="ICQ">&nbsp;
             <% }
            }
            %>
            <%= componentSession.getExternalComponent().getType() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.creation" />
        </td>
        <td>
            <%= JiveGlobals.formatDateTime(componentSession.getCreationDate()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.last_active" />
        </td>
        <td>
            <%= JiveGlobals.formatDateTime(componentSession.getLastActiveDate()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.statistics" />
        </td>
        <td>
            <fmt:message key="session.details.received" />
            <%= numFormatter.format(componentSession.getNumClientPackets()) %>/<%= numFormatter.format(componentSession.getNumServerPackets()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.hostname" />
        </td>
        <td>
            <%= componentSession.getHostAddress() %>
            /
            <%= componentSession.getHostName() %>
        </td>
    </tr>
</tbody>
</table>
</div>
<br>

<form action="component-session-details.jsp">
<center>
<input type="submit" name="back" value="<fmt:message key="session.details.back_button" />">
</center>
</form>

    </body>
</html>