<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.openfire.lockout.LockOutFlag"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.lockout.LockOutManager" %>
<%@ page import="org.jivesoftware.openfire.security.SecurityAuditManager" %>
<%@ page import="org.jivesoftware.openfire.session.ClientSession" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="org.xmpp.packet.StreamError" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.Date" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean unlock = request.getParameter("unlock") != null;
    boolean lock = request.getParameter("lock") != null;
    String username = ParamUtils.getParameter(request,"username");
    Integer startdelay = ParamUtils.getIntParameter(request,"startdelay",-1); // -1 is immediate, -2 custom
    Integer duration = ParamUtils.getIntParameter(request,"duration",-1); // -1 is infinite, -2 custom
    if (startdelay == -2) {
        startdelay = ParamUtils.getIntParameter(request,"startdelay_custom", -1);
    }
    if (duration == -2) {
        duration = ParamUtils.getIntParameter(request,"duration_custom", -1);   
    }

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-properties.jsp?username=" + URLEncoder.encode(username, "UTF-8"));
        return;
    }

    // Handle a user lockout:
    if (lock) {
        Date startTime = null;
        if (startdelay != -1) {
            startTime = new Date(new Date().getTime() + startdelay*60000);
        }
        Date endTime = null;
        if (duration != -1) {
            if (startTime != null) {
                endTime = new Date(startTime.getTime() + duration*60000);
            }
            else {
                endTime = new Date(new Date().getTime() + duration*60000);
            }
        }
        // Lock out the user
        webManager.getLockOutManager().disableAccount(username, startTime, endTime);
        if (!SecurityAuditManager.getSecurityAuditProvider().blockUserEvents()) {
            // Log the event
            webManager.logEvent("locked out user "+username, "start time = "+startTime+", end time = "+endTime);
        }
        // Close the user's connection if the lockout is immedate
        if (webManager.getLockOutManager().isAccountDisabled(username)) {
            final StreamError error = new StreamError(StreamError.Condition.not_authorized);
            for (ClientSession sess : webManager.getSessionManager().getSessions(username) )
            {
                sess.deliverRawText(error.toXML());
                sess.close();
            }
            // Disabled your own user account, force login
            if (username.equals(webManager.getAuthToken().getUsername())){
                session.removeAttribute("jive.admin.authToken");
                response.sendRedirect("login.jsp");
                return;
            }
        }
        // Done, so redirect
        response.sendRedirect("user-properties.jsp?username=" + URLEncoder.encode(username, "UTF-8") + "&locksuccess=1");
        return;
    }

    // Handle a user unlock:
    if (unlock) {
        // Unlock the user's account
        webManager.getLockOutManager().enableAccount(username);
        if (!SecurityAuditManager.getSecurityAuditProvider().blockUserEvents()) {
            // Log the event
            webManager.logEvent("unlocked user "+username, null);
        }
        // Done, so redirect
        response.sendRedirect("user-properties.jsp?username=" + URLEncoder.encode(username, "UTF-8") + "&unlocksuccess=1");
        return;
    }

%>

<html>
    <head>
        <title><fmt:message key="user.lockout.title"/></title>
        <meta name="subPageID" content="user-lockout"/>
        <meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, "UTF-8") %>"/>
    </head>
    <body>

<% if (LockOutManager.getLockOutProvider().isReadOnly()) { %>
<div class="error">
    <fmt:message key="user.read_only"/>
</div>
<% } %>

<%
    LockOutFlag flag = LockOutManager.getInstance().getDisabledStatus(username);
    if (flag != null) {
        // User is locked out
%>

<p>
<fmt:message key="user.lockout.locked">
    <fmt:param value="<%= "<b><a href='user-properties.jsp?username="+URLEncoder.encode(username, "UTF-8")+"'>"+JID.unescapeNode(username)+"</a></b>" %>"/>
</fmt:message>
<% if (flag.getStartTime() != null) { %><fmt:message key="user.lockout.locked2"><fmt:param value="<%= flag.getStartTime().toString() %>"/></fmt:message> <% } %>
<% if (flag.getStartTime() != null && flag.getEndTime() != null) { %> <fmt:message key="user.lockout.lockedand" /> <% } %> 
<% if (flag.getEndTime() != null) { %><fmt:message key="user.lockout.locked3"><fmt:param value="<%= flag.getEndTime().toString() %>"/></fmt:message> <% } %>
</p>

<form action="user-lockout.jsp">
    <input type="hidden" name="username" value="<%= username %>">
    <input type="submit" name="unlock" value="<fmt:message key="user.lockout.unlock" />">
    <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

<%
    }
    else {
        // User is not locked out
%>

<p>
<fmt:message key="user.lockout.info" />
<b><a href="user-properties.jsp?username=<%= URLEncoder.encode(username, "UTF-8") %>"><%= JID.unescapeNode(username) %></a></b>
<fmt:message key="user.lockout.info1" />
</p>

<c:if test="${webManager.user.username == param.username}">
    <p class="jive-warning-text">
    <fmt:message key="user.lockout.warning" /> <b><fmt:message key="user.lockout.warning2" /></b> <fmt:message key="user.lockout.warning3" />
    </p>
</c:if>

<form action="user-lockout.jsp">
    <% if (LockOutManager.getLockOutProvider().isDelayedStartSupported()) { %>
    <b><fmt:message key="user.lockout.time.startdelay" /></b><br />
    <input type="radio" name="startdelay" value="-1" checked="checked" /> <fmt:message key="user.lockout.time.immediate" /><br />
    <input type="radio" name="startdelay" value="60" /> <fmt:message key="user.lockout.time.in" /> <fmt:message key="user.lockout.time.1hour" /><br />
    <input type="radio" name="startdelay" value="1440" /> <fmt:message key="user.lockout.time.in" /> <fmt:message key="user.lockout.time.1day" /><br />
    <input type="radio" name="startdelay" value="10080" /> <fmt:message key="user.lockout.time.in" /> <fmt:message key="user.lockout.time.1week" /><br />
    <input type="radio" name="startdelay" value="-2" /> <fmt:message key="user.lockout.time.in" /> <input type="text" size="5" maxlength="10" name="starydelay_custom" /> <fmt:message key="user.lockout.time.minutes"/><br />
    <br />
    <% } %>
    <% if (LockOutManager.getLockOutProvider().isTimeoutSupported()) { %>
    <b><fmt:message key="user.lockout.time.duration" /></b><br />
    <input type="radio" name="duration" value="-1" checked="checked" /> <fmt:message key="user.lockout.time.forever" /><br />
    <input type="radio" name="duration" value="60" /> <fmt:message key="user.lockout.time.for" /> <fmt:message key="user.lockout.time.1hour" /><br />
    <input type="radio" name="duration" value="1440" /> <fmt:message key="user.lockout.time.for" /> <fmt:message key="user.lockout.time.1day" /><br />
    <input type="radio" name="duration" value="10080" /> <fmt:message key="user.lockout.time.for" /> <fmt:message key="user.lockout.time.1week" /><br />
    <input type="radio" name="duration" value="-2" /> <fmt:message key="user.lockout.time.for" /> <input type="text" size="5" maxlength="10" name="duration_custom" /> <fmt:message key="user.lockout.time.minutes"/><br />
    <br />
    <% } %>
    <input type="hidden" name="username" value="<%= username %>">
    <input type="submit" name="lock" value="<fmt:message key="user.lockout.lock" />">
    <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

<%
    }
%>

<%  // Disable the form if a read-only user provider.
    if (LockOutManager.getLockOutProvider().isReadOnly()) { %>

<script language="Javascript" type="text/javascript">
  function disable() {
    var limit = document.forms[0].elements.length;
    for (i=0;i<limit;i++) {
      document.forms[0].elements[i].disabled = true;
    }
  }
  disable();
</script>
    <% } %>

    </body>
</html>
