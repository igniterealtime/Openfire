<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

<%@ page import="org.jivesoftware.openfire.PresenceManager,
                 org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.session.ClientSession,
                 org.jivesoftware.openfire.session.LocalClientSession,
                 org.jivesoftware.openfire.user.User,
                 org.jivesoftware.openfire.user.UserManager,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.CookieUtils,
                 java.text.NumberFormat,
                 java.util.Collection"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.nio.NettyConnection" %>
<%@ page import="org.jivesoftware.openfire.websocket.WebSocketConnection" %>
<%@ page import="org.jivesoftware.openfire.http.HttpSession" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.entitycaps.EntityCapabilities" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters
    String jid = ParamUtils.getParameter(request, "jid");

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");
    boolean close = request.getParameter("close") != null;

    if (close) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            close = false;
        }
    }

    // Get the user manager
    SessionManager sessionManager = webManager.getSessionManager();

    // Close a connection if requested
    if (close) {
        JID address = new JID(jid);
        try {
            Session sess = sessionManager.getSession(address);
            if (sess instanceof LocalClientSession) {
                ((LocalClientSession) sess).getStreamManager().formalClose();
            }
            sess.close();
            // Log the event
            webManager.logEvent("closed session for address "+address, null);
            // wait one second
            Thread.sleep(250L);
        }
        catch (Exception e) {
            // Session might have disappeared on its own
            LoggerFactory.getLogger("session-details.jsp").warn("Unable to manually close session for address: {}", address, e);
        }
        // Redirect back to this page
        response.sendRedirect("session-summary.jsp?close=success");
        return;
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    // Handle a "go back" click:
    if (request.getParameter("back") != null) {
        response.sendRedirect("session-summary.jsp");
        return;
    }

    boolean showCaps = request.getParameter("show") != null;

    // Get the session & address objects
    JID address = new JID(jid);
    org.jivesoftware.openfire.session.ClientSession currentSess = sessionManager.getSession(address);
    boolean isAnonymous = webManager.getXMPPServer().isLocal(address) &&
            !UserManager.getInstance().isRegisteredUser(address, false);

    // No current session found
    if (currentSess == null) {
        response.sendRedirect("session-summary.jsp");
        return;
    }

    // Get a presence manager
    PresenceManager presenceManager = webManager.getPresenceManager();

    // Get user object
    User user = null;
    if (!isAnonymous) {
        user = webManager.getUserManager().getUser(address.getNode());
    }

    // Handle a "message" click:
    if (request.getParameter("message") != null) {
        if (csrfCookie != null && csrfParam != null && csrfCookie.getValue().equals(csrfParam)) {
            response.sendRedirect("user-message.jsp?username=" + URLEncoder.encode(user.getUsername(), "UTF-8"));
            return;
        }
    }

    // See if there are multiple sessions for this user:
    Collection<ClientSession> sessions = null;
    int sessionCount = sessionManager.getSessionCount(address.getNode());
    if (!isAnonymous && sessionCount > 1) {
        sessions = sessionManager.getSessions(address.getNode());
    }

    // Number dateFormatter for all numbers on this page:
    NumberFormat numFormatter = NumberFormat.getNumberInstance();

    final boolean clusteringEnabled = ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting();

    pageContext.setAttribute("address", address);
    pageContext.setAttribute("clusteringEnabled", ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting() );
%>

<html>
    <head>
        <title><fmt:message key="session.details.title"/></title>
        <meta name="pageID" content="session-summary"/>
    </head>
    <body>

<p>
<fmt:message key="session.details.info">
    <fmt:param value="<b>${fn:escapeXml(address)}</b>" />
    <fmt:param value="<b>${empty address.node ? '' : fn:escapeXml(address)}</b>" />
</fmt:message>

</p>

<div class="jive-table">
<table>
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
            <%= StringUtils.escapeHTMLTags(address.toString()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.username" />
        </td>
        <td>
            <%  String n = address.getNode(); %>
            <%  if (isAnonymous) { %>

                <i> <fmt:message key="session.details.anonymous" /> </i> - <%= address.getResource()==null?"":StringUtils.escapeHTMLTags(address.getResource()) %>

            <%  } else { %>

                <a href="user-properties.jsp?username=<%= URLEncoder.encode(n, "UTF-8") %>"><%= StringUtils.escapeHTMLTags(JID.unescapeNode(n)) %></a>
                - <%= address.getResource()==null?"":StringUtils.escapeForXML(address.getResource()) %>

            <%  } %>
        </td>
    </tr>
    <% if (clusteringEnabled) { %>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.node" />
        </td>
        <td>
            <% if (currentSess instanceof LocalClientSession) { %>
             <fmt:message key="session.details.local" />
            <% } else { %>
             <fmt:message key="session.details.remote" />
            <% } %>
        </td>
    </tr>
    <% } %>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.status" />:
        </td>
        <td>
            <%
                Session.Status status = currentSess.getStatus();
                if (status == Session.Status.CLOSED) {
            %>
                <fmt:message key="session.details.close" />

            <%
                } else if (status == Session.Status.CONNECTED) {
            %>

                <fmt:message key="session.details.connect" />

            <%
                } else if (status == Session.Status.AUTHENTICATED) {
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
                    statusTxt = " -- " + StringUtils.escapeHTMLTags(statusTxt);
                }
                else {
                    statusTxt = "";
                }
                if (!currentSess.getPresence().isAvailable()) {
            %>
                <img src="images/user-clear-16x16.gif" title="<fmt:message key="user.properties.offline" />" alt="<fmt:message key="user.properties.offline" />">
                <fmt:message key="user.properties.offline" />
            <%
                } else if (show == Presence.Show.away) {
            %>
                <img src="images/im_away.gif" title="<fmt:message key="session.details.away" />" alt="<fmt:message key="session.details.away" />">
                <fmt:message key="session.details.away" /> <%= statusTxt %>

            <%
                } else if (show == Presence.Show.chat) {
            %>
                <img src="images/im_free_chat.gif" title="<fmt:message key="session.details.chat_available" />" alt="<fmt:message key="session.details.chat_available" />">
                <fmt:message key="session.details.chat_available" /> <%= statusTxt %>
            <%
                } else if (show == Presence.Show.dnd) {
            %>

                <img src="images/im_dnd.gif" title="<fmt:message key="session.details.not_disturb" />" alt="<fmt:message key="session.details.not_disturb" />">
                <fmt:message key="session.details.not_disturb" /> <%= statusTxt %>

            <%
                } else if (show == null) {
            %>

                <img src="images/im_available.gif" title="<fmt:message key="session.details.online" />" alt="<fmt:message key="session.details.online" />">
                <fmt:message key="session.details.online" /> <%= statusTxt %>

            <%
                } else if (show == Presence.Show.xa) {
            %>

                <img src="images/im_away.gif" title="<fmt:message key="session.details.extended" />" alt="<fmt:message key="session.details.extended" />">
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
            <fmt:message key="session.details.received-and-transmitted" />
            <%= numFormatter.format(currentSess.getNumClientPackets()) %>/<%= numFormatter.format(currentSess.getNumServerPackets()) %>
        </td>
    </tr>
</tbody>
</table>
</div>

    <br/>

    <div class="jive-table">
        <table>
            <thead>
            <tr>
                <th colspan="2">
                    <fmt:message key="session.details.connection" />
                </th>
            </tr>
            </thead>
            <tbody>
                <%
                    if (currentSess instanceof LocalClientSession) {
                    LocalClientSession s = (LocalClientSession)currentSess;
                %>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.connection-type"/>:
                    </td>
                    <td>
                    <% if (s.isDetached()) {
                    %><fmt:message key="session.details.sm-detached"/><%
                    } else if (s.getConnection() instanceof NettyConnection) {
                    %>TCP<%
                    } else if (s.getConnection() instanceof WebSocketConnection) {
                    %>WebSocket<%
                    } else if (s.getConnection() instanceof HttpSession.HttpVirtualConnection) {
                    %>BOSH<%
                    } else {
                    %>Unknown<%
                        }
                    %>
                    </td>
                </tr>
                <% } %>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.hostname" />
                    </td>
                    <td>
                        <%
                            if (currentSess instanceof LocalClientSession && ((LocalClientSession) currentSess).isDetached()) { %>
                        <fmt:message key="session.details.sm-detached"/>
                        <% } else {
                            try { %>
                        <%= StringUtils.escapeHTMLTags(currentSess.getHostAddress()) %> / <%=StringUtils.escapeHTMLTags(currentSess.getHostName())%>
                        <% } catch (java.net.UnknownHostException e) { %>
                        Invalid session/connection
                        <% }
                        } %>
                    </td>
                </tr>
                <%
                    if (currentSess instanceof LocalClientSession) {
                        LocalClientSession s = (LocalClientSession)currentSess;
                        if (s.getConnection() != null && s.getConnection().getConfiguration() != null) {
                %>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.connection-local-port"/>:
                    </td>
                    <td>
                        <%=s.getConnection().getConfiguration().getPort()%>
                    </td>
                </tr>
                <% } } %>
            </tbody>
        </table>
    </div>

    <br>

    <div class="jive-table">
        <table style="width: 100%">
            <thead>
                <tr>
                    <th colspan="2">
                        <fmt:message key="session.details.security"/>
                    </th>
                </tr>
            </thead>
            <tbody>
                <% if (currentSess.isEncrypted()) { %>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.tls_version" />:
                    </td>
                    <td>
                        <%=StringUtils.escapeHTMLTags(currentSess.getTLSProtocolName())%>
                    </td>
                </tr>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.cipher" />:
                    </td>
                    <td>
                        <%=StringUtils.escapeHTMLTags(currentSess.getCipherSuiteName())%>
                    </td>
                </tr>
                <% } %>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.anon-status"/>:
                    </td>
                    <td>
                        <% if (currentSess.isAnonymousUser()) { %>
                        <fmt:message key="session.details.anon-true" />
                        <% } else { %>
                        <fmt:message key="session.details.anon-false" />
                        <% } %>
                    </td>
                </tr>
                <% if (currentSess instanceof LocalSession && ((LocalSession) currentSess).getSessionData("SaslMechanism") != null) { %>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.sasl-mechanism"/>:
                    </td>
                    <td>
                        <%=StringUtils.escapeHTMLTags(((LocalSession) currentSess).getSessionData("SaslMechanism").toString())%>
                    </td>
                </tr>
                <% } %>
            </tbody>
        </table>
    </div>

    <%  // Show Software Version if there is :
        if (!currentSess.getSoftwareVersion().isEmpty()) {
    %>
    <br>

    <div class="jive-table">
        <table style="width: 100%">
            <thead>
            <tr>
                <th colspan="2">
                    <fmt:message key="session.details.software_version"/>
                </th>
            </tr>
            </thead>
            <tbody>
            <%
                Map<String, String> treeMap = new TreeMap<>(currentSess.getSoftwareVersion());
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
    <%  } %>

    <br>

    <div class="jive-table">
        <table style="width: 100%">
            <thead>
                <tr>
                    <th colspan="2">
                        <fmt:message key="session.details.features"/>
                    </th>
                </tr>
            </thead>
            <tbody>
                <% if (currentSess instanceof LocalClientSession) {
                    LocalClientSession s = (LocalClientSession)currentSess; %>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.sm-status"/>:
                    </td>
                    <td>
                        <%
                            if (s.isDetached()) {
                        %><fmt:message key="session.details.sm-detached"/><%
                    } else if (s.getStreamManager().isEnabled()) {
                        if (s.getStreamManager().getResume()) {
                    %><fmt:message key="session.details.sm-resume"/><%
                    } else {
                    %><fmt:message key="session.details.sm-enabled"/><%
                        }
                    } else {
                    %><fmt:message key="session.details.sm-disabled"/><%
                        }
                    %>
                    </td>
                </tr>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.csi-status"/>:
                    </td>
                    <td>
                        <% if (s.getCsiManager().isActive()) { %>
                        <fmt:message key="session.details.csi-active"/>
                        <% } else { %>
                        <fmt:message key="session.details.csi-inactive"/>
                        (<fmt:message key="session.details.csi-delayed-stanzas"/>: <%= s.getCsiManager().getDelayQueueSize() %>)
                        <% } %>
                    </td>
                </tr>
                <% } %>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.cc-status"/>:
                    </td>
                    <td>
                        <% if (currentSess.isMessageCarbonsEnabled()) { %>
                        <fmt:message key="session.details.cc-enabled" />
                        <% } else { %>
                        <fmt:message key="session.details.cc-disabled" />
                        <% } %>
                    </td>
                </tr>
                <tr>
                    <td class="c1">
                        <fmt:message key="session.details.flomr-status"/>:
                    </td>
                    <td>
                        <% if (currentSess.isOfflineFloodStopped()) { %>
                        <fmt:message key="session.details.flomr-enabled" />
                        <% } else { %>
                        <fmt:message key="session.details.flomr-disabled" />
                        <% } %>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>

<%
    final EntityCapabilities caps = XMPPServer.getInstance().getEntityCapabilitiesManager().getEntityCapabilities(address);
    if (showCaps) {
        // Show any registered entity capabilities.
        if ( caps != null && (!caps.getIdentities().isEmpty() || !caps.getFeatures().isEmpty())) {
%>
<br>

<div class="jive-table">
    <table style="width: 100%">
        <thead>
        <tr>
            <th colspan="2">
                <fmt:message key="session.details.entity_capabilities"/>
            </th>
        </tr>
        </thead>
        <tbody>
        <%
            if ( !caps.getIdentities().isEmpty() ) {
            // Use a TreeSet to force natural ordering, which makes things easier to read.
            for (final String identity : new TreeSet<>(caps.getIdentities())) { %>
        <tr>
            <td class="c1">
                <fmt:message key="session.details.entity_capabilities.identity"/>:
            </td>
            <td>
                <%= StringUtils.escapeHTMLTags(identity)%>
            </td>
        </tr>
        <%
            } }
        %>
        <%
            // Use a TreeSet to force natural ordering, which makes things easier to read.
            if ( !caps.getFeatures().isEmpty() ) {
            for (final String feature : new TreeSet<>(caps.getFeatures())) { %>
        <tr>
            <td class="c1">
                <fmt:message key="session.details.entity_capabilities.feature"/>:
            </td>
            <td>
                <%= StringUtils.escapeHTMLTags(feature)%>
            </td>
        </tr>
        <%
            } }
        %>
        </tbody>
    </table>
</div>
<%  } } %>

<%  // Show a list of multiple user sessions if there is more than 1 session:
    if (sessionCount > 1) {
%>
    <br>

    <p>
    <b><fmt:message key="session.details.multiple_session" /></b>
    </p>

    <div class="jive-table">
    <table style="width: 100%">
    <tr>
        <th>&nbsp;</th>
        <th><fmt:message key="session.details.name" /></th>
        <th nowrap><fmt:message key="session.details.version" /></th>
        <c:if test="${clusteringEnabled}">
            <th nowrap><fmt:message key="session.details.node" /></th>
        </c:if>
        <th nowrap colspan="2"><fmt:message key="session.details.status" /></th>
        <th nowrap colspan="2"><fmt:message key="session.details.if_presence" /></th>
        <th nowrap title="<fmt:message key="session.details.received" />">
            <fmt:message key="session.details.received-abbreviation" />
        </th>
        <th nowrap title="<fmt:message key="session.details.transmitted" />">
            <fmt:message key="session.details.transmitted-abbreviation" />
        </th>
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

    <table>
    <tr>
        <td style="width: 1%; white-space: nowrap">

            <div class="jive-table">
            <table>
            <tr class="jive-current"><td><img src="images/blank.gif" width="12" height="12" alt=""></td></tr>
            </table>
            </div>

        </td>
        <td>

            &nbsp; = <fmt:message key="session.details.session_detail" />

        </td>
    </tr>
    </table>

<%  } %>

<br>

<form action="session-details.jsp" type="post">
<input type="hidden" name="jid" value="<%= StringUtils.escapeForXML(jid) %>">
<input type="hidden" name="csrf" value="<%=csrfParam%>"/>
<div style="text-align: center;">
    <% if (showCaps) { %>
    <input type="submit" name="hide" value="<fmt:message key="session.details.hide-extended" />">
    <% } else if (caps != null && (!caps.getIdentities().isEmpty() || !caps.getFeatures().isEmpty())) { %>
    <input type="submit" name="show" value="<fmt:message key="session.details.show-extended" />">
    <% } %>
    <input type="submit" name="back" value="<fmt:message key="session.details.back_button" />">
    <button name="close" style="padding-left: 24px;background-repeat: no-repeat;background-image: url(images/delete-16x16.gif);background-position-y: center; background-color: lightyellow;border-width: 1px;" onclick="return confirm('<fmt:message key="session.row.confirm_close" />');"><fmt:message key="session.row.click_kill_session" /></button>

</div>
</form>

    </body>
</html>
