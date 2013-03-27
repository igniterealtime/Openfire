<%--
  -	$Revision$
  -	$Date$
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

<%@ page import="org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.openfire.ConnectionManager,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.JMXManager,
                 java.net.InetAddress,
                 java.util.HashMap"
%>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%
    // Get parameters
    String serverName = ParamUtils.getParameter(request, "serverName");
    int port = ParamUtils.getIntParameter(request, "port", -1);
    int sslPort = ParamUtils.getIntParameter(request, "sslPort", -1);
    int embeddedPort = ParamUtils.getIntParameter(request, "embeddedPort", -1);
    int embeddedSecurePort = ParamUtils.getIntParameter(request, "embeddedSecurePort", -1);
    boolean sslEnabled = ParamUtils.getBooleanParameter(request, "sslEnabled");
    int componentPort = ParamUtils.getIntParameter(request, "componentPort", -1);
    int serverPort = ParamUtils.getIntParameter(request, "serverPort", -1);
    boolean jmxEnabled = ParamUtils.getBooleanParameter(request, "jmxEnabled");
    boolean jmxSecure = ParamUtils.getBooleanParameter(request, "jmxSecure");
    int jmxPort = ParamUtils.getIntParameter(request, "jmxPort", -1);
    boolean save = request.getParameter("save") != null;
    boolean defaults = request.getParameter("defaults") != null;
    boolean cancel = request.getParameter("cancel") != null;

    if (cancel) {
        response.sendRedirect("index.jsp");
        return;
    }

    if (defaults) {
        serverName = InetAddress.getLocalHost().getHostName();
        port = ConnectionManager.DEFAULT_PORT;
        sslPort = ConnectionManager.DEFAULT_SSL_PORT;
        componentPort = ConnectionManager.DEFAULT_COMPONENT_PORT;
        serverPort = ConnectionManager.DEFAULT_SERVER_PORT;
        embeddedPort = 9090;
        embeddedSecurePort = 9091;
        sslEnabled = true;
        jmxEnabled = false;
        jmxSecure = true;
        jmxPort = JMXManager.DEFAULT_PORT;
        save = true;
    }

    XMPPServer server = webManager.getXMPPServer();
    ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
    Map<String, String> errors = new HashMap<String, String>();
    if (save) {
        if (serverName == null) {
            errors.put("serverName", "");
        }
        if (port < 1) {
            errors.put("port", "");
        }
        if (sslPort < 1 && sslEnabled) {
            errors.put("sslPort", "");
        }
        if (componentPort < 1) {
            errors.put("componentPort", "");
        }
        if (serverPort < 1) {
            errors.put("serverPort", "");
        }
        if (XMPPServer.getInstance().isStandAlone()) {
            if (embeddedPort < 1) {
                errors.put("embeddedPort", "");
            }
            if (embeddedSecurePort < 1) {
                errors.put("embeddedSecurePort", "");
            }
            if (embeddedPort > 0 && embeddedSecurePort > 0) {
                if (embeddedPort == embeddedSecurePort) {
                    errors.put("embeddedPortsEqual", "");
                }
            }
        } else {
            embeddedPort = -1;
            embeddedSecurePort = -1;
        }
        if (port > 0 && sslPort > 0) {
            if (port == sslPort) {
                errors.put("portsEqual", "");
            }
        }
        if (jmxPort < 1 && jmxEnabled) {
            errors.put("jmxPort", "");
        }
        if (errors.size() == 0) {
            boolean needRestart = false;
            if (!serverName.equals(server.getServerInfo().getXMPPDomain())) {
                server.getServerInfo().setXMPPDomain(serverName);
                needRestart = true;
            }
            connectionManager.setClientListenerPort(port);
            connectionManager.enableClientSSLListener(sslEnabled);
            connectionManager.setClientSSLListenerPort(sslPort);
            connectionManager.setComponentListenerPort(componentPort);
            connectionManager.setServerListenerPort(serverPort);
            if (!String.valueOf(embeddedPort).equals(JiveGlobals.getXMLProperty("adminConsole.port"))) {
                JiveGlobals.setXMLProperty("adminConsole.port", String.valueOf(embeddedPort));
                needRestart = true;
            }
            if (!String.valueOf(embeddedSecurePort).equals(JiveGlobals.getXMLProperty("adminConsole.securePort"))) {
                JiveGlobals.setXMLProperty("adminConsole.securePort", String.valueOf(embeddedSecurePort));
                needRestart = true;
            }
            JMXManager.setEnabled(jmxEnabled);
            JMXManager.setSecure(jmxSecure);
            JMXManager.setPort(jmxPort);

            // Log the event
            webManager.logEvent("edit server properties", "serverName = "+serverName+"\nport = "+port+"\nsslPort = "+sslPort+"\ncomponentPort = "+componentPort+"\nserverPort = "+serverPort+"\nembeddedPort = "+embeddedPort+"\nembeddedSecurePort = "+embeddedSecurePort);
            if (needRestart) {
                response.sendRedirect("server-props.jsp?success=true&restart=true");
            } else {
                response.sendRedirect("server-props.jsp?success=true");
            }
            return;
        }
    } else {
        serverName = server.getServerInfo().getXMPPDomain();
        sslEnabled = connectionManager.isClientSSLListenerEnabled();
        port = connectionManager.getClientListenerPort();
        sslPort = connectionManager.getClientSSLListenerPort();
        componentPort = connectionManager.getComponentListenerPort();
        serverPort = connectionManager.getServerListenerPort();
        try {
            embeddedPort = Integer.parseInt(JiveGlobals.getXMLProperty("adminConsole.port"));
        } catch (Exception ignored) {
        }
        try {
            embeddedSecurePort = Integer.parseInt(JiveGlobals.getXMLProperty("adminConsole.securePort"));
        } catch (Exception ignored) {
        }
        jmxEnabled = JMXManager.isEnabled();
        jmxSecure = JMXManager.isSecure();
        jmxPort = JMXManager.getPort();
    }
%>

<html>
    <head>
        <title><fmt:message key="server.props.title"/></title>
        <meta name="pageID" content="server-settings"/>
    </head>
    <body>

<style type="text/css">
.c1 {
    width : 30%;
}
</style>

<p>
<fmt:message key="server.props.info" />
</p>

<%  if ("true".equals(request.getParameter("success"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <%  if ("true".equals(request.getParameter("restart"))) { %>
            <fmt:message key="server.props.update" /> <b><fmt:message key="global.restart" /></b> <fmt:message key="server.props.update2" /> <a href="index.jsp"><fmt:message key="global.server_status" /></a>).
        <%  } else { %>
            <fmt:message key="server.props.update.norestart" />.
        <%  } %>
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
            <fmt:message key="server.props.property" />
        </th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">
            <fmt:message key="server.props.name" />
        </td>
        <td class="c2">
            <input type="text" name="serverName" value="<%= (serverName != null) ? serverName : "" %>"
             size="30" maxlength="150">
            <%  if (errors.containsKey("serverName")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.valid_hostname" />
                <a href="#" onclick="document.editform.serverName.value='<%= InetAddress.getLocalHost().getHostName() %>';"
                 ><fmt:message key="server.props.valid_hostname1" /></a>.
                </span>
            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
             <fmt:message key="server.props.server_port" />
        </td>
        <td class="c2">
            <input type="text" name="serverPort" value="<%= (serverPort > 0 ? String.valueOf(serverPort) : "") %>"
             size="5" maxlength="5">
            <%  if (errors.containsKey("serverPort")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.valid_port" />
                <a href="#" onclick="document.editform.serverPort.value='<%=ConnectionManager.DEFAULT_SERVER_PORT%>';"
                 ><fmt:message key="server.props.valid_port1" /></a>.
                </span>
            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
             <fmt:message key="server.props.component_port" />
        </td>
        <td class="c2">
            <input type="text" name="componentPort" value="<%= (componentPort > 0 ? String.valueOf(componentPort) : "") %>"
             size="5" maxlength="5">
            <%  if (errors.containsKey("componentPort")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.valid_port" />
                <a href="#" onclick="document.editform.componentPort.value='<%=ConnectionManager.DEFAULT_COMPONENT_PORT%>';"
                 ><fmt:message key="server.props.valid_port1" /></a>.
                </span>
            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
             <fmt:message key="server.props.port" />
        </td>
        <td class="c2">
            <input type="text" name="port" value="<%= (port > 0 ? String.valueOf(port) : "") %>"
             size="5" maxlength="5">
            <%  if (errors.containsKey("port")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.valid_port" />
                <a href="#" onclick="document.editform.port.value='<%=ConnectionManager.DEFAULT_PORT%>';"
                 ><fmt:message key="server.props.valid_port1" /></a>.
                </span>
            <%  } else if (errors.containsKey("portsEqual")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.error_port" />
                </span>
            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
              <fmt:message key="server.props.ssl" />
        </td>
        <td class="c2">
            <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td>
                        <input type="radio" name="sslEnabled" value="true" <%= (sslEnabled ? "checked" : "") %>
                         id="SSL01">
                    </td>
                    <td><label for="SSL01"><fmt:message key="server.props.enable" /></label></td>
                </tr>
                <tr>
                    <td>
                        <input type="radio" name="sslEnabled" value="false" <%= (!sslEnabled ? "checked" : "") %>
                         id="SSL02">
                    </td>
                    <td><label for="SSL02"><fmt:message key="server.props.disable" /></label></td>
                </tr>
            </tbody>
            </table>
        </td>
    </tr>
    <tr>
        <td class="c1">
             <fmt:message key="server.props.ssl_port" />
        </td>
        <td class="c2">
            <input type="text" name="sslPort" value="<%= (sslPort > 0 ? String.valueOf(sslPort) : "") %>"
             size="5" maxlength="5">
            <%  if (errors.containsKey("sslPort")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.ssl_valid" />
                <a href="#" onclick="document.editform.sslPort.value='<%=ConnectionManager.DEFAULT_SSL_PORT%>';"
                 ><fmt:message key="server.props.ssl_valid1" /></a>.
                </span>
            <%  } %>
        </td>
    </tr>
<% if (XMPPServer.getInstance().isStandAlone()){ %>
    <tr>
        <td class="c1">
            <fmt:message key="server.props.admin_port" />
        </td>
        <td class="c2">
            <input type="text" name="embeddedPort" value="<%= (embeddedPort > 0 ? String.valueOf(embeddedPort) : "") %>"
             size="5" maxlength="5">
            <%  if (errors.containsKey("embeddedPort")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.valid_port" />
                <a href="#" onclick="document.editform.embeddedPort.value='9090';"
                 ><fmt:message key="server.props.valid_port1" /></a>.
                </span>
            <%  } else if (errors.containsKey("embeddedPortsEqual")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.error_port" />
                </span>
            <%  } %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.props.admin_secure_port" />
        </td>
        <td class="c2">
            <input type="text" name="embeddedSecurePort" value="<%= (embeddedSecurePort > 0 ? String.valueOf(embeddedSecurePort) : "") %>"
             size="5" maxlength="5">
            <%  if (errors.containsKey("embeddedSecurePort")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.valid_port" />
                <a href="#" onclick="document.editform.embeddedSecurePort.value='9091';"
                 ><fmt:message key="server.props.valid_port1" /></a>.
                </span>
            <%  } %>
        </td>
    </tr>
<% } %>
    <tr>
        <td class="c1">
              <fmt:message key="server.props.jmx_enabled" />
        </td>
        <td class="c2">
            <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td>
                        <input type="radio" name="jmxEnabled" value="true" <%= (jmxEnabled ? "checked" : "") %>
                         id="JMX01">
                    </td>
                    <td><label for="JMX01"><fmt:message key="server.props.enable" /></label></td>
                </tr>
                <tr>
                    <td>
                        <input type="radio" name="jmxEnabled" value="false" <%= (!jmxEnabled ? "checked" : "") %>
                         id="JMX02">
                    </td>
                    <td><label for="JMX02"><fmt:message key="server.props.disable" /></label></td>
                </tr>
            </tbody>
            </table>
        </td>
    </tr>
    <tr>
        <td class="c1">
              <fmt:message key="server.props.jmx_secure" />
        </td>
        <td class="c2">
            <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td>
                        <input type="radio" name="jmxSecure" value="true" <%= (jmxSecure ? "checked" : "") %>
                         id="JMX03">
                    </td>
                    <td><label for="JMX03"><fmt:message key="server.props.enable" /></label></td>
                </tr>
                <tr>
                    <td>
                        <input type="radio" name="jmxSecure" value="false" <%= (!jmxSecure ? "checked" : "") %>
                         id="JMX04">
                    </td>
                    <td><label for="JMX04"><fmt:message key="server.props.disable" /></label></td>
                </tr>
            </tbody>
            </table>
        </td>
    </tr>
    <tr>
        <td class="c1">
             <fmt:message key="server.props.jmx_port" />
        </td>
        <td class="c2">
            <input type="text" name="jmxPort" value="<%= (jmxPort > 0 ? String.valueOf(jmxPort) : "") %>"
             size="5" maxlength="5">
            <%  if (errors.containsKey("jmxPort")) { %>
                <br>
                <span class="jive-error-text">
                <fmt:message key="server.props.jmx_valid" />
                <a href="#" onclick="document.editform.jmxPort.value='<%=java.rmi.registry.Registry.REGISTRY_PORT%>';"
                 ><fmt:message key="server.props.jmx_valid1" /></a>.
                </span>
            <%  } %>
        </td>
    </tr>
</tbody>
<tfoot>
    <tr>
        <td colspan="2">
            <input type="submit" name="save" value="<fmt:message key="global.save_properties" />">
            <input type="submit" name="defaults" value="<fmt:message key="global.restore_defaults" />">
            <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
        </td>
    </tr>
</tfoot>
</table>
</div>

</form>

    </body>
</html>