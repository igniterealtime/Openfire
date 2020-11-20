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
                 org.jivesoftware.openfire.session.ComponentSession,
                 org.jivesoftware.openfire.session.Session,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.ParamUtils,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="java.util.Calendar"%>
<%@ page import="java.util.Collection"%>
<%@ page import="java.util.Date"%>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>
<%@ page import="org.jivesoftware.openfire.session.LocalSession" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%!
    final int DEFAULT_RANGE = 15;
    final int[] RANGE_PRESETS = {15, 25, 50, 75, 100};
%>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<% // Get parameters
    int start = ParamUtils.getIntParameter(request, "start", 0);
    int range = ParamUtils
            .getIntParameter(request, "range", admin.getRowsPerPage("component-session-summary", DEFAULT_RANGE));
    boolean close = ParamUtils.getBooleanParameter(request, "close");
    String jid = ParamUtils.getParameter(request, "jid");

    if (request.getParameter("range") != null) {
        admin.setRowsPerPage("component-session-summary", range);
    }

    // Get the session manager
    SessionManager sessionManager = admin.getSessionManager();

    Collection<ComponentSession> sessions = sessionManager.getComponentSessions();

    // Get the session count
    int sessionCount = sessions.size();

    // Close the external component connection
    if (close) {
        try {
            Session sess = sessionManager.getComponentSession(jid);
            if (sess != null) {
                sess.close();
            }
            // Log the event
            admin.logEvent("closed component session for "+jid, null);
            // wait one second
            Thread.sleep(1000L);
        }
        catch (Exception ignored) {
            // Session might have disappeared on its own
        }
        // redirect back to this page
        response.sendRedirect("component-session-summary.jsp?close=success");
        return;
    }
    // paginator vars
    int numPages = (int) Math.ceil((double) sessionCount / (double) range);
    int curPage = (start / range) + 1;
    int maxIndex = Math.min(start + range, sessionCount);

    final boolean clusteringEnabled = ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting();
%>

<html>
    <head>
        <title><fmt:message key="component.session.summary.title"/></title>
        <meta name="pageID" content="component-session-summary"/>
    </head>
    <body>

<%  if ("success".equals(request.getParameter("close"))) { %>

    <p class="jive-success-text">
    <fmt:message key="component.session.summary.close" />
    </p>

<%  } %>

<p>
<fmt:message key="component.session.summary.active" />: <b><%= sessions.size() %></b>

<%  if (numPages > 1) { %>

    - <fmt:message key="global.showing" /> <%= (start+1) %>-<%= (start+range) %>

<%  } %>
 - <fmt:message key="component.session.summary.sessions_per_page" />:
<select size="1" onchange="location.href='component-session-summary.jsp?start=0&range=' + this.options[this.selectedIndex].value;">

    <% for (int aRANGE_PRESETS : RANGE_PRESETS) { %>

    <option value="<%= aRANGE_PRESETS %>"
            <%= (aRANGE_PRESETS == range ? "selected" : "") %>><%= aRANGE_PRESETS %>
    </option>

    <% } %>

</select>
</p>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />:
    [
    <%  for (int i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="component-session-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<p>
<fmt:message key="component.session.summary.info">
    <fmt:param value="<a href=\"connection-settings-external-components.jsp\">" />
    <fmt:param value="</a>" />
</fmt:message>
</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="component.session.label.domain" /></th>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="component.session.label.name" /></th>
        <th nowrap><fmt:message key="component.session.label.category" /></th>
        <th nowrap><fmt:message key="component.session.label.type" /></th>
        <% if (clusteringEnabled) { %>
        <th nowrap><fmt:message key="component.session.label.node" /></th>
        <% } %>
        <th nowrap><fmt:message key="component.session.label.creation" /></th>
        <th nowrap><fmt:message key="component.session.label.last_active" /></th>
        <th nowrap><fmt:message key="component.session.label.close_connect" /></th>
    </tr>
</thead>
<tbody>
    <%  // Check if no out/in connection to/from a remote server exists
        if (sessions.isEmpty()) {
    %>
        <tr>
            <td colspan="9">

                <fmt:message key="component.session.summary.not_session" />

            </td>
        </tr>

    <%  } %>

    <%  int count = 0;
        sessions = new ArrayList<ComponentSession>(sessions).subList(start, maxIndex);
        for (ComponentSession componentSession : sessions) {
            count++;
    %>
    <tr class="jive-<%= (((count % 2) == 0) ? "even" : "odd") %>">
        <td width="1%" nowrap><%= count %></td>
        <td width="43%" nowrap>
            <a href="component-session-details.jsp?jid=<%= URLEncoder.encode(componentSession.getAddress().toString(), "UTF-8") %>" title="<fmt:message key="session.row.cliked" />"><%= componentSession.getAddress() %></a>
        </td>
        <td width="1%">
            <%  if (componentSession.isSecure()) {
                if (componentSession.getPeerCertificates() != null && componentSession.getPeerCertificates().length > 0) { %>
            <img src="images/lock_both.gif" width="16" height="16" border="0" title="<fmt:message key='session.row.cliked_ssl' /> (mutual authentication)" alt="<fmt:message key='session.row.cliked_ssl' /> (mutual authentication)">
            <%      } else { %>
            <img src="images/lock.gif" width="16" height="16" border="0" title="<fmt:message key='session.row.cliked_ssl' />" alt="<fmt:message key='session.row.cliked_ssl' />">
            <%      }
            } else { %>
            <img src="images/blank.gif" width="1" height="1" alt="">
            <%     } %>
        </td>
        <td width="15%" nowrap>
            <%= StringUtils.escapeHTMLTags(componentSession.getExternalComponent().getName()) %>
        </td>
        <td width="10%" nowrap>
            <%= StringUtils.escapeHTMLTags(componentSession.getExternalComponent().getCategory()) %>
        </td>
        <td width="10%" nowrap>
            <table border="0">
            <tr valign="center">
            <% if ("gateway".equals(componentSession.getExternalComponent().getCategory())) {
                if ("msn".equals(componentSession.getExternalComponent().getType())) { %>
                <td><img src="images/msn.gif" width="16" height="16" border="0" alt="MSN"></td>
             <% }
                else if ("aim".equals(componentSession.getExternalComponent().getType())) { %>
                <td><img src="images/aim.gif" width="16" height="16" border="0" alt="AIM"></td>
             <% }
                else if ("yahoo".equals(componentSession.getExternalComponent().getType())) { %>
                <td><img src="images/yahoo.gif" width="22" height="16" border="0" alt="Yahoo!"></td>
             <% }
                else if ("icq".equals(componentSession.getExternalComponent().getType())) { %>
                <td><img src="images/icq.gif" width="16" height="16" border="0" alt="ICQ"></td>
             <% }
                else if ("irc".equals(componentSession.getExternalComponent().getType())) { %>
                <td><img src="images/irc.gif" width="16" height="16" border="0" alt="IRC"></td>
             <% }
               }
            %>
            <td><%= StringUtils.escapeHTMLTags(componentSession.getExternalComponent().getType()) %></td>
            </tr></table>
        </td>
        <% if (clusteringEnabled) { %>
        <td>
            <% if (componentSession instanceof LocalSession) { %>
            <fmt:message key="component.session.local" />
            <% } else { %>
            <fmt:message key="component.session.remote" />
            <% } %>
        </td>
        <% } %>
        <%  Date creationDate = componentSession.getCreationDate();
            Calendar creationCal = Calendar.getInstance();
            creationCal.setTime(creationDate);

            Date lastActiveDate = componentSession.getLastActiveDate();
            Calendar lastActiveCal = Calendar.getInstance();
            lastActiveCal.setTime(lastActiveDate);

            Calendar nowCal = Calendar.getInstance();

            boolean sameCreationDay = nowCal.get(Calendar.DAY_OF_YEAR) == creationCal.get(Calendar.DAY_OF_YEAR) && nowCal.get(Calendar.YEAR) == creationCal.get(Calendar.YEAR);
            boolean sameActiveDay = nowCal.get(Calendar.DAY_OF_YEAR) == lastActiveCal.get(Calendar.DAY_OF_YEAR) && nowCal.get(Calendar.YEAR) == lastActiveCal.get(Calendar.YEAR);
        %>
        <td width="9%" nowrap>
            <%= sameCreationDay ? JiveGlobals.formatTime(creationDate) : JiveGlobals.formatDateTime(creationDate) %>
        </td>
        <td width="9%" nowrap>
            <%= sameActiveDay ? JiveGlobals.formatTime(lastActiveDate) : JiveGlobals.formatDateTime(lastActiveDate) %>
        </td>

        <td width="1%" nowrap align="center" style="border-right:1px #ccc solid;">
            <a href="component-session-summary.jsp?jid=<%= URLEncoder.encode(componentSession.getAddress().toString(), "UTF-8") %>&close=true"
             title="<fmt:message key="session.row.cliked_kill_session" />"
             onclick="return confirm('<fmt:message key="session.row.confirm_close" />');"
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
        </td>
    </tr>
    <%  } %>

</tbody>
</table>
</div>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />:
    [
    <%  for (int i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="component-session-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<br>
<p>
<fmt:message key="component.session.summary.last_update" />: <%= JiveGlobals.formatDateTime(new Date()) %>
</p>

    </body>
</html>
