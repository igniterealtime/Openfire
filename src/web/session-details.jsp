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

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 java.text.DateFormat,
                 java.text.NumberFormat,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.user.User,
                 org.xmpp.packet.JID,
                 org.xmpp.packet.Presence,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<%  // Get parameters
    String jid = ParamUtils.getParameter(request, "jid");

    // Handle a "go back" click:
    if (request.getParameter("back") != null) {
        response.sendRedirect("session-summary.jsp");
        return;
    }

    // Get the session & address objects
    SessionManager sessionManager = webManager.getSessionManager();
    JID address = new JID(jid);
    ClientSession currentSess = sessionManager.getSession(address);
    boolean isAnonymous = address.getNode() == null || "".equals(address.getNode());

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

    // Date dateFormatter for all dates on this page:
    DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT);

    // Number dateFormatter for all numbers on this page:
    NumberFormat numFormatter = NumberFormat.getNumberInstance();
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("session.details.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "session-details.jsp"));
    pageinfo.setPageID("session-summary");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
<fmt:message key="session.details.info">
    <fmt:param value="<%= "<b>"+address.toString()+"</b>" %>" />
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
            <%= address.toString() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.username" />
        </td>
        <td>
            <%  String n = address.getNode(); %>
            <%  if (n == null || "".equals(n)) { %>

                <i> <fmt:message key="session.details.anonymous" /> </i> - <%= address.getResource()==null?"":address.getResource() %>

            <%  } else { %>

                <a href="user-properties.jsp?username=<%= n %>"><%= n %></a>
                - <%= address.getResource()==null?"":address.getResource() %>

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
                } else if (status == Session.STATUS_STREAMING) {
            %>

                <fmt:message key="session.details.streaming" />

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
                    statusTxt = " -- " + statusTxt;
                }
                else {
                    statusTxt = "";
                }
                if (show == Presence.Show.away) {
            %>

                <img src="images/bullet-yellow-14x14.gif" width="14" height="14" border="0" title="Away">
                <fmt:message key="session.details.away" /> <%= statusTxt %>

            <%
                } else if (show == Presence.Show.chat) {
            %>
                <img src="images/bullet-green-14x14.gif" width="14" height="14" border="0" title="Available to Chat">
                <fmt:message key="session.details.chat_available" /> <%= statusTxt %>
            <%
                } else if (show == Presence.Show.dnd) {
            %>

                <img src="images/bullet-red-14x14.gif" width="14" height="14" border="0" title="Do not Disturb">
                <fmt:message key="session.details.not_disturb" /> <%= statusTxt %>

            <%
                } else if (show == null) {
            %>

                <img src="images/bullet-green-14x14.gif" width="14" height="14" border="0" title="Online">
                <fmt:message key="session.details.online" /> <%= statusTxt %>

            <%
                } else if (show == Presence.Show.xa) {
            %>

                <img src="images/bullet-yellow-14x14.gif" width="14" height="14" border="0" title="Extended Away">
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
            <fmt:message key="session.details.session_created" />:
        </td>
        <td>
            <%= dateFormatter.format(currentSess.getCreationDate()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.last_active" />:
        </td>
        <td>
            <%= dateFormatter.format(currentSess.getLastActiveDate()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.statistics" />:
        </td>
        <td>
            <fmt:message key="session.details.received" />:
            <%= numFormatter.format(currentSess.getNumClientPackets()) %>/<%= numFormatter.format(currentSess.getNumServerPackets()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.hostname" />:
        </td>
        <td>
            <%= currentSess.getConnection().getInetAddress().getHostAddress() %>
            /
            <%= currentSess.getConnection().getInetAddress().getHostName() %>
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
        <th><fmt:message key="session.details.status" /></th>
        <th nowrap colspan="2"><fmt:message key="session.details.if_presence" /></th>
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
            <tr class="jive-current"><td><img src="images/blank.gif" width="12" height="12" border="0"></td></tr>
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
<input type="hidden" name="jid" value="<%= jid %>">
<center>
<%--<%  if (!isAnonymous && presenceManager.isAvailable(user)) { %>--%>
<%----%>
<%--    <input type="submit" name="message" value="Message this Session">--%>
<%----%>
<%--<%  } %>--%>
<input type="submit" name="back" value="Back to Summary">
</center>
</form>

<jsp:include page="bottom.jsp" flush="true" />
