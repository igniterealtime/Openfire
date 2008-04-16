<%--
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.openfire.SessionManager"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.clearspace.ClearspaceManager" %>
<%@ page import="org.jivesoftware.openfire.session.ComponentSession" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Date" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%

    boolean test = request.getParameter("test") != null;
    boolean configure = request.getParameter("configure") != null;
    boolean csAdmin = request.getParameter("csAdmin") != null;
    String testPage = "setup/setup-clearspace-integration_test.jsp";
    ClearspaceManager manager = ClearspaceManager.getInstance();
    boolean configured = false;

    if (configure) {
        configured = manager.configClearspace();
    }

    // Checks if CS and OF are currently connected
    boolean connectedCS = manager.isClearspaceConnected();
    boolean connectedOF = manager.isOpenfireConnected();

    if (csAdmin) {
        String username = webManager.getUser().getUsername();
        String secret = manager.getSharedSecret();
        String uri = manager.getConnectionURI();
        String nonce = manager.getNonce();

        if (connectedOF && username != null && secret != null && uri != null) {
            // Redirect to the admin console of Clearspace.
            response.sendRedirect(uri + "admin/login.jsp?login=true&username=" + username + "&secret=" +
                    StringUtils.hash(username + ":" + secret + ":" + nonce) + "&nonce=" + nonce);
            return;
        }
    }

    Date creationDate = null;
    Date lastActivity = null;
    int numServerPackets = 0;
    int numClientPackets = 0;
    int numComponents = 0;
    Collection<ComponentSession> componentSessions = null;

    // If connected collects stats from Clearspace sessions
    if (connectedCS && connectedOF) {
        SessionManager sessionManager = webManager.getSessionManager();
        componentSessions = sessionManager.getComponentSessions();
        for (ComponentSession cs : componentSessions) {
            // All Clearspace sessions start with "clearspace"
            if (cs.getAddress().getDomain().startsWith("clearspace")) {
                if (creationDate == null || cs.getCreationDate().before(creationDate)) {
                    creationDate = cs.getCreationDate();
                }
                if (lastActivity == null || cs.getLastActiveDate().after(lastActivity)) {
                    lastActivity = cs.getLastActiveDate();
                }
                numClientPackets += cs.getNumClientPackets();
                numServerPackets += cs.getNumServerPackets();
                numComponents++;
                break;
            }
        }
    }

    // Number dateFormatter for all numbers on this page:
    NumberFormat numFormatter = NumberFormat.getNumberInstance();
    
%>

<html>
<head>
<title><fmt:message key="clearspace.info.title"/></title>
<meta name="pageID" content="clearspace-info"/>

<style type="text/css" title="setupStyle" media="screen">
    @import "style/lightbox.css";
    @import "style/ldap.css";
</style>

<script language="JavaScript" type="text/javascript" src="js/prototype.js"></script>
<script language="JavaScript" type="text/javascript" src="js/scriptaculous.js"></script>
<script language="JavaScript" type="text/javascript" src="js/lightbox.js"></script>
<script language="javascript" type="text/javascript" src="js/tooltips/domLib.js"></script>
<script language="javascript" type="text/javascript" src="js/tooltips/domTT.js"></script>
<script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
</head>

<body>

<% if (test) { %>

    <a href="<%= testPage%>" id="lbmessage" title="<fmt:message key="global.test" />" style="display:none;"></a>
    <script type="text/javascript">
        function loadMsg() {
            var lb = new lightbox(document.getElementById('lbmessage'));
            lb.activate();
        }
        setTimeout('loadMsg()', 250);
    </script>

<% } %>

<% if (!connectedCS || !connectedOF) { %>
<div class="error">
    <fmt:message key="clearspace.info.status.disconnected.error"/>
</div>

<% if (configure && !configured) { %>

<div class="error">
    <fmt:message key="clearspace.info.status.config.error"/>
</div>

<% } %>

<p>
<% if (!connectedCS && !connectedOF) { %>
<h3><fmt:message key="clearspace.info.status.disconnected.of_and_cs.title"/></h3>
<fmt:message key="clearspace.info.status.disconnected.of_and_cs.description">
    <fmt:param value="<%= "<a href='clearspace-connection.jsp'>" %>" />
    <fmt:param value="<%= "</a>" %>" />
</fmt:message>

<% } else if (!connectedCS) { %>
<h3><fmt:message key="clearspace.info.status.disconnected.cs.title"/></h3>
<fmt:message key="clearspace.info.status.disconnected.cs.description">
    <fmt:param value="<%= "<a href='clearspace-connection.jsp'>" %>" />
    <fmt:param value="<%= "</a>" %>" />
</fmt:message>
<% } else if (!connectedOF) { %>
<h3><fmt:message key="clearspace.info.status.disconnected.of.title"/></h3>
<fmt:message key="clearspace.info.status.disconnected.of.description">
    <fmt:param value="<%= "<a href='clearspace-connection.jsp'>" %>" />
    <fmt:param value="<%= "</a>" %>" />
</fmt:message>
<% } %>
<p>
<fmt:message key="clearspace.info.status.disconnected.buttons.description"/>    
</p>
<form action="clearspace-info.jsp" method="post">
    <!-- BEGIN jive-buttons -->
    <div class="jive-buttons">

        <!-- BEGIN right-aligned buttons -->
        <div align="left">

            <input type="Submit" name="test" value="<fmt:message key="clearspace.info.status.test" />" id="jive-clearspace-test" border="0">

            <input type="Submit" name="configure" value="<fmt:message key="clearspace.info.status.configure" />" id="jive-clearspace-configure" border="0">
        </div>
        <!-- END right-aligned buttons -->

    </div>
    <!-- END jive-buttons -->

</form>

<% } else { %>
<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">
            <fmt:message key="clearspace.info.table.title" />
        </th>
    </tr>
</thead>
<tbody>
    <% if (numComponents > 1) { %>
    <tr>
        <td class="c1">
            <fmt:message key="clearspace.info.label.num_components" />
        </td>
        <td>
            <%= numFormatter.format(numComponents) %>
        </td>
    </tr>
    <% } %>
    <tr>
        <td class="c1">
            <fmt:message key="clearspace.info.label.creation" />
        </td>
        <td>
            <%= JiveGlobals.formatDateTime(creationDate) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="clearspace.info.label.last_active" />
        </td>
        <td>
            <%= JiveGlobals.formatDateTime(lastActivity) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="clearspace.info.label.statistics" />
        </td>
        <td>
            <fmt:message key="clearspace.info.label.received" />
            <%= numFormatter.format(numClientPackets) %>/<%= numFormatter.format(numServerPackets) %>
        </td>
    </tr>
    <% boolean first = true;
    for (ComponentSession cs : componentSessions) {
        if (first) {
            first = false;  %>
    <tr>
        <td rowsapn="<%= componentSessions.size() %>" class="c1">
            <fmt:message key="clearspace.info.label.hostname" />
        </td>
        <td>
            <%= cs.getHostAddress() %>
            /
            <%= cs.getHostName() %>
        </td>
    </tr>
     <% } else { %>
    <tr>
        <td>
            <%= cs.getHostAddress() %>
            /
            <%= cs.getHostName() %>
        </td>
    </tr>
    <% } %>
</tbody>
</table>
</div>

<% } %>

    <h3>Clearspace Admin Console</h3>
    <br>
    <p>Click the following button to go to Clearspace Admin console</p>
    <form action="clearspace-info.jsp" method="post">
        <!-- BEGIN jive-buttons -->
        <div class="jive-buttons">

            <!-- BEGIN right-aligned buttons -->
            <div align="left">

                <input type="Submit" name="csAdmin" value="<fmt:message key="clearspace.info.status.admin" />" id="jive-clearspace-admin" border="0">

            </div>
            <!-- END right-aligned buttons -->

        </div>
        <!-- END jive-buttons -->

    </form>

    


<% } %>

</body>
</html>