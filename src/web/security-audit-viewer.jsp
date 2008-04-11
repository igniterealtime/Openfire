<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils"
%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.jivesoftware.openfire.security.SecurityAuditManager" %>
<%@ page import="org.jivesoftware.openfire.security.SecurityAuditEvent" %>
<%@ page import="java.util.Date" %>
<%@ page import="org.jivesoftware.openfire.security.AuditWriteOnlyException" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.text.ParseException" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%!
    final int DEFAULT_RANGE = 15;
    final int[] RANGE_PRESETS = {15, 25, 50, 75, 100};
    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yy HH:mm:ss");
    SimpleDateFormat shortDateFormat = new SimpleDateFormat("MM/dd/yy");
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="security.audit.viewer.title"/></title>
        <meta name="pageID" content="security-audit-viewer"/>
    </head>
    <body>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("security-audit-viewer", DEFAULT_RANGE));
    String username = null;
    String usernameParam = ParamUtils.getParameter(request,"username");
    if (usernameParam != null && !usernameParam.equals("")) {
        username = usernameParam;
    }
    Date startTime = null;
    String startTimeParam = ParamUtils.getParameter(request, "startdate");
    String startDateSetting = null;
    if (startTimeParam != null) {
        try {
            startTime = dateFormat.parse(startTimeParam+" 00:00:00");
            startDateSetting = shortDateFormat.format(startTime);
        }
        catch (ParseException e) {
            // Bad date, ignore
        }
    }
    Date endTime = null;
    String endTimeParam = ParamUtils.getParameter(request, "enddate");
    String endDateSetting = null;
    if (endTimeParam != null) {
        try {
            endTime = dateFormat.parse(endTimeParam+" 23:59:59");
            endDateSetting = shortDateFormat.format(endTime);
        }
        catch (ParseException e) {
            // Bad date, ignore
        }
    }

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("security-audit-viewer", range);
    }

    // Get the presence manager
    SecurityAuditManager auditManager = webManager.getSecurityAuditManager();

    if (!SecurityAuditManager.getSecurityAuditProvider().isWriteOnly()) {
%>

<p>
    <fmt:message key="security.audit.viewer.description" />
</p>

<form action="security-audit-viewer.jsp" method="post">
<p>
    <strong><fmt:message key="security.audit.viewer.events_to_show" /></strong>:
    <select size="1" name="range">

    <% for (int aRANGE_PRESETS : RANGE_PRESETS) { %>

    <option value="<%= aRANGE_PRESETS %>"
            <%= (aRANGE_PRESETS == range ? "selected" : "") %>><%= aRANGE_PRESETS %>
    </option>

    <% } %>

    </select>
    &nbsp;&nbsp;
    <strong><fmt:message key="security.audit.viewer.username" /></strong>:
    <input type="text" size="30" maxlength="150" name="username" value="<%= username != null ? username : "" %>"/>
    <br/>
    <strong><fmt:message key="security.audit.viewer.date_range"/></strong>:
    <fmt:message key="security.audit.viewer.date_range.start"/>:
    <input type="text" size="15" maxlength="15" name="startdate" value="<%= startDateSetting != null ? startDateSetting : "" %>"/> (<fmt:message key="security.audit.viewer.date_range.use"/>)
    &nbsp;
    <fmt:message key="security.audit.viewer.date_range.end"/>:
    <input type="text" size="15" maxlength="15" name="enddate" value="<%= endDateSetting != null ? endDateSetting : "" %>"/> (<fmt:message key="security.audit.viewer.date_range.use"/>)
    &nbsp;&nbsp;&nbsp;
    <input type="submit" name="search" value="<fmt:message key="security.audit.viewer.search"/>" />
</p>
</form>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th nowrap><fmt:message key="security.audit.viewer.id" /></th>
        <th nowrap><fmt:message key="security.audit.viewer.username" /></th>
        <th nowrap><fmt:message key="security.audit.viewer.node" /></th>
        <th nowrap><fmt:message key="security.audit.viewer.event" /></th>
        <th nowrap><fmt:message key="security.audit.viewer.timestamp" /></th>
    </tr>
</thead>
<tbody>

<%  // Print the list of users
    try {
        Collection<SecurityAuditEvent> events = auditManager.getEvents(username, start, range, startTime, endTime);
        if (events.isEmpty()) {
%>
    <tr>
        <td align="center" colspan="5">
            <fmt:message key="security.audit.viewer.no_logs" />
        </td>
    </tr>

<%
        }
        int i = start;
        for (SecurityAuditEvent event : events) {
            i++;
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>" valign="top">
        <td width="1%">
            <%= event.getMsgID() %>
        </td>
        <td width="10%">
            <a href="user-properties.jsp?username=<%= URLEncoder.encode(event.getUsername(), "UTF-8") %>"><%= JID.unescapeNode(event.getUsername()) %></a>
        </td>
        <td width="15%">
            <%= event.getNode() %>
        </td>
        <td width="59%">
            <%= event.getSummary() %>
            <% if (event.getDetails() != null) { %>
            &nbsp; <a href="" onclick="if (document.getElementById('details<%= event.getMsgID() %>').style.display == 'none') { document.getElementById('details<%= event.getMsgID() %>').style.display = 'block'; document.getElementById('label<%= event.getMsgID() %>').innerHTML = '<%= LocaleUtils.getLocalizedString("security.audit.viewer.hide_details")%>'; return false;} else { document.getElementById('details<%= event.getMsgID() %>').style.display = 'none'; document.getElementById('label<%= event.getMsgID() %>').innerHTML = '<%= LocaleUtils.getLocalizedString("security.audit.viewer.show_details")%>'; return false;}" id="label<%= event.getMsgID() %>"><fmt:message key="security.audit.viewer.show_details" /></a><br/>
            <pre id="details<%= event.getMsgID() %>" style="display:none; margin: 0px; padding: 1px;"><%= event.getDetails() %></pre>
            <% } %>
        </td>
        <td width="15%">
            <%= JiveGlobals.formatDateTime(event.getEventStamp()) %>
        </td>
    </tr>

<%
        }
    }
    catch (AuditWriteOnlyException e) {
        // This should never occur, so we're ignoring.
    }
%>
</tbody>
</table>
</div>
    <% } else { %>
<div>
    <fmt:message key="security.audit.viewer.write_only"/>
    <% if (SecurityAuditManager.getSecurityAuditProvider().getAuditURL() != null) { %>
    <fmt:message key="security.audit.viewer.view_url"/>
    <br />
    <br />
    <strong><fmt:message key="security.audit.viewer.view_url.url" /></strong>: <a target="_new" href="<%= SecurityAuditManager.getSecurityAuditProvider().getAuditURL() %>"><%= SecurityAuditManager.getSecurityAuditProvider().getAuditURL() %></a>
    <% } %>
</div>
    <% } %>

    </body>
</html>
