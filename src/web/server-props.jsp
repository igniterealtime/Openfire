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
                 org.jivesoftware.messenger.XMPPServerInfo,
                 org.jivesoftware.messenger.ServerPort,
                 org.jivesoftware.admin.AdminPageBean,
                 java.util.*,
                 org.jivesoftware.messenger.XMPPServer,
                 java.net.InetAddress,
                 org.jivesoftware.messenger.JiveGlobals"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%
    // Get parameters
    String serverName = ParamUtils.getParameter(request,"serverName");
    int port = ParamUtils.getIntParameter(request,"port",-1);
    int sslPort = ParamUtils.getIntParameter(request,"sslPort",-1);
    int embeddedPort = ParamUtils.getIntParameter(request,"embeddedPort",-1);
    boolean sslEnabled = ParamUtils.getBooleanParameter(request,"sslEnabled");
    boolean save = request.getParameter("save") != null;
    boolean defaults = request.getParameter("defaults") != null;
    boolean cancel = request.getParameter("cancel") != null;

    if (cancel) {
        response.sendRedirect("index.jsp");
        return;
    }

    if (defaults) {
        serverName = InetAddress.getLocalHost().getHostName();
        port = 5222;
        sslPort = 5223;
        embeddedPort = 9090;
        sslEnabled = true;
        save = true;
    }

    XMPPServer server = admin.getXMPPServer();
    Map errors = new HashMap();
    if (save) {
        if (serverName == null) {
            errors.put("serverName","");
        }
        if (port < 1) {
            errors.put("port","");
        }
        if (sslPort < 1 && sslEnabled) {
            errors.put("sslPort","");
        }
        if (embeddedPort < 1) {
            errors.put("embeddedPort","");
        }
        if (port > 0 && sslPort > 0) {
            if (port == sslPort) {
                errors.put("portsEqual","");
            }
        }
        if (errors.size() == 0) {
            server.getServerInfo().setName(serverName);
            JiveGlobals.setProperty("xmpp.socket.plain.port", String.valueOf(port));
            JiveGlobals.setProperty("embedded-web.port", String.valueOf(embeddedPort));
            JiveGlobals.setProperty("xmpp.socket.ssl.active", String.valueOf(sslEnabled));
            JiveGlobals.setProperty("xmpp.socket.ssl.port", String.valueOf(sslPort));
            response.sendRedirect("server-props.jsp?success=true");
            return;
        }
    }

    if (errors.size() == 0) {
        serverName = server.getServerInfo().getName();
        sslEnabled = "true".equals(JiveGlobals.getProperty("xmpp.socket.ssl.active"));
        try { port = Integer.parseInt(JiveGlobals.getProperty("xmpp.socket.plain.port")); } catch (Exception ignored) {}
        try { embeddedPort = Integer.parseInt(JiveGlobals.getProperty("embedded-web.port")); } catch (Exception ignored) {}
        try { sslPort = Integer.parseInt(JiveGlobals.getProperty("xmpp.socket.ssl.port")); } catch (Exception ignored) {}
    }
%>

<%  // Title of this page and breadcrumbs
    String title = "Edit Server Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Server Properties", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Edit", "server-props.jsp"));
    pageinfo.setPageID("server-settings");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<style type="text/css">
.c1 {
    width : 30%;
}
</style>

<p>
Use the form below to edit server properties.
</p>

<%  if ("true".equals(request.getParameter("success"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Server properties updated successfully. You'll need to <b>restart</b> the server to have
        the changes take effect.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="server-props.jsp" name="editform" method="post">

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">
            Server Properties
        </th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">
            Server Name:
        </td>
        <td class="c2">
            <input type="text" name="serverName" value="<%= (serverName != null) ? serverName : "" %>"
             size="30" maxlength="40">
            <%  if (errors.containsKey("serverName")) { %>
                <br>
                <span class="jive-error-text">
                Please enter a valid server host name or
                <a href="#" onclick="document.editform.serverName.value='<%= InetAddress.getLocalHost().getHostName() %>';"
                 >restore the default</a>.
                </span>
            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
             Port:
        </td>
        <td class="c2">
            <input type="text" name="port" value="<%= (port > 0 ? String.valueOf(port) : "") %>"
             size="5" maxlength="5">
            <%  if (errors.containsKey("port")) { %>
                <br>
                <span class="jive-error-text">
                Please enter a valid port number or
                <a href="#" onclick="document.editform.port.value='5222';"
                 >restore the default</a>.
                </span>
            <%  } else if (errors.containsKey("portsEqual")) { %>
                <br>
                <span class="jive-error-text">
                Error -- this port and the SSL port can not be equal.
                </span>
            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
             SSL Enabled:
        </td>
        <td class="c2">
            <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td>
                        <input type="radio" name="sslEnabled" value="true" <%= (sslEnabled ? "checked" : "") %>
                         id="SSL01">
                    </td>
                    <td><label for="SSL01">Enabled</label></td>
                </tr>
                <tr>
                    <td>
                        <input type="radio" name="sslEnabled" value="false" <%= (!sslEnabled ? "checked" : "") %>
                         id="SSL02">
                    </td>
                    <td><label for="SSL02">Disabled</label></td>
                </tr>
            </tbody>
            </table>
        </td>
    </tr>
    <tr>
        <td class="c1">
             SSL Port:
        </td>
        <td class="c2">
            <input type="text" name="sslPort" value="<%= (sslPort > 0 ? String.valueOf(sslPort) : "") %>"
             size="5" maxlength="5">
            <%  if (errors.containsKey("sslPort")) { %>
                <br>
                <span class="jive-error-text">
                Please enter a valid SSL port number or
                <a href="#" onclick="document.editform.sslPort.value='5223';"
                 >restore the default</a>.
                </span>
            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
             Admin Console Port:
        </td>
        <td class="c2">
            <input type="text" name="embeddedPort" value="<%= (embeddedPort > 0 ? String.valueOf(embeddedPort) : "") %>"
             size="5" maxlength="5">
            <%  if (errors.containsKey("embeddedPort")) { %>
                <br>
                <span class="jive-error-text">
                Please enter a valid port number or
                <a href="#" onclick="document.editform.embeddedPort.value='9090';"
                 >restore the default</a>.
                </span>
            <%  } %>
        </td>
    </tr>
</tbody>
<tfoot>
    <tr>
        <td colspan="2">
            <input type="submit" name="save" value="Save">
            <input type="submit" name="defaults" value="Restore Defaults">
            <input type="submit" name="cancel" value="Cancel">
        </td>
    </tr>
</tfoot>
</table>
</div>

</form>

<jsp:include page="bottom.jsp" flush="true" />
