<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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

<%@ page import="org.jivesoftware.admin.AdminConsole"%>
<%@ page import="org.jivesoftware.openfire.Connection"%>
<%@ page import="org.jivesoftware.openfire.JMXManager" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.filetransfer.proxy.FileTransferProxy" %>
<%@ page import="org.jivesoftware.openfire.keystore.IdentityStore" %>
<%@ page import="org.jivesoftware.openfire.mediaproxy.MediaProxyService" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionListener" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.openfire.update.Update" %>
<%@ page import="org.jivesoftware.openfire.update.UpdateManager" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="java.util.List" %>
<%@ page import="org.jivesoftware.openfire.net.DNSUtil" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.io.InputStream" %>
<%@ page import="org.apache.http.HttpHost" %>
<%@ page import="org.apache.http.conn.routing.HttpRoutePlanner" %>
<%@ page import="org.apache.http.impl.conn.DefaultProxyRoutePlanner" %>
<%@ page import="org.apache.http.impl.conn.DefaultRoutePlanner" %>
<%@ page import="org.apache.http.impl.client.CloseableHttpClient" %>
<%@ page import="org.apache.http.impl.client.HttpClients" %>
<%@ page import="org.apache.http.client.methods.CloseableHttpResponse" %>
<%@ page import="org.apache.http.client.methods.HttpGet" %>
<%@ page import="java.io.InputStreamReader" %>
<%@ page import="org.jivesoftware.util.MemoryUsageMonitor" %>
<%@ page import="org.jivesoftware.openfire.ConnectionManager" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionManagerImpl" %>
<%@ page import="org.jivesoftware.admin.servlet.BlogPostServlet" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%-- Define page bean for header and sidebar --%>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%  // Simple logout code
    if ("true".equals(request.getParameter("logout"))) {
        session.removeAttribute("jive.admin.authToken");
        response.sendRedirect("index.jsp");
        return;
    }
%>

<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out); %>
<% // Get parameters //
    boolean serverOn = (webManager.getXMPPServer() != null);

    ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
    FileTransferProxy fileTransferProxy = XMPPServer.getInstance().getFileTransferProxy();
    MediaProxyService mediaProxyService = XMPPServer.getInstance().getMediaProxyService();

    boolean rssEnabled = BlogPostServlet.ENABLED.getValue();;

    pageContext.setAttribute("memoryUsageAfterLastGC", MemoryUsageMonitor.getInstance().getMemoryUsageAfterLastGC());
%>

<html>
    <head>
        <title><fmt:message key="index.title"/></title>
        <meta name="pageID" content="server-settings"/>
        <meta name="helpPage" content="about_the_server.html"/>
        <% if (rssEnabled) { %>
        <script>
            const xhr = new XMLHttpRequest();
            xhr.open("GET", "getblogposts", true);
            xhr.onload = (e) => {
                if (xhr.readyState === 4) {
                    let news = document.getElementById("jive-latest-activity");
                    if (xhr.status === 200) {
                        let items = JSON.parse(xhr.responseText).items;
                        if (items.length > 0) {
                            for (let i = 0; i < items.length; i++) {
                                let h5 = document.createElement("h5");
                                h5.innerHTML = "<a href='" + items[i].link + "' target='_blank'>" + items[i].title + "</a>, <span class='jive-blog-date'>" + items[i].date + "</span>";
                                news.appendChild(h5);
                            }
                        } else {
                            let span = document.createElement("span");
                            span.innerHTML = '<fmt:message key="index.cs_blog.unavailable" />';
                            news.appendChild(span);
                        }
                    } else {
                        let span = document.createElement("span");
                        span.innerHTML = '<fmt:message key="index.cs_blog.unavailable" />';
                        news.appendChild(span);
                    }
                }
            };
            xhr.onerror = (e) => {
                let news = document.getElementById("jive-latest-activity");
                let span = document.createElement("span");
                span.innerHTML = '<fmt:message key="index.cs_blog.unavailable" />';
                news.appendChild(span);
            };
            xhr.send(null);
        </script>
        <% } %>
    </head>
    <body>

<%
    UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
    Update serverUpdate = updateManager.getServerUpdate();
    pageContext.setAttribute( "serverUpdate", serverUpdate ); %>

    <c:if test="${not empty serverUpdate}">
        <div class="warning">
            <table >
                <tbody>
                <tr>
                    <td class="jive-icon-label">
                        <b><fmt:message key="index.update.alert" /></b><br/><br/>
                    </td>
                </tr>
                <tr>
                    <td>
                        <span><fmt:message key="index.update.info">
                            <fmt:param value="${serverUpdate.latestVersion}" />
                            <fmt:param value="<a href=\"${serverUpdate.URL}\">" />
                            <fmt:param value="</a>"/>
                            <fmt:param value="<a href=\"${serverUpdate.changelog}\">"/>
                            <fmt:param value="</a>"/>
                        </fmt:message></span>
                    </td>
                </tr>
                </tbody>
            </table>
        </div>
        <br>
    </c:if>

<style>
.bar TD {
    padding : 0;
}
#jive-latest-activity .jive-bottom-line {
    padding-top: 10px;
    border-bottom : 1px #e8a400 solid;
    }
#jive-latest-activity {
    border: 1px #E8A400 solid;
    background-color: #FFFBE2;
    font-family: Lucida Grande, Arial, Helvetica, sans-serif;
    font-size: 9pt;
    padding: 0 10px 10px 10px;
    margin-bottom: 10px;
    min-height: 280px;
    -moz-border-radius: 4px;
    width: 95%;
    margin-right: 20px;
    }
#jive-latest-activity h4 {
    font-size: 13pt;
    margin: 15px 0 4px 0;
    }
#jive-latest-activity h5 {
    font-size: 9pt;
    font-weight: normal;
    margin: 15px 0 5px 5px;
    padding: 0;
    }
#jive-latest-activity .jive-blog-date {
    font-size: 8pt;
    white-space: nowrap;
    }
#jive-latest-activity .jive-feed-icon {
    float: right;
    padding-top: 10px;
    }
.info-header {
    background-color: #eee;
    font-size: 10pt;
}
.info-table {
    margin-right: 12px;
}
.info-table .c1 {
    text-align: right;
    vertical-align: top;
    color: #666;
    font-weight: bold;
    font-size: 9pt;
    white-space: nowrap;
}
.info-table .c2 {
    font-size: 9pt;
    width: 90%;
}
</style>

<p>
<fmt:message key="index.title.info" />
</p>
<table style="width: 100%">
    <td>

        <!-- <div class="jive-table"> -->
        <table class="info-table">
        <thead>
            <tr>
                <th colspan="2" class="info-header"><fmt:message key="index.properties" /></th>
            </tr>
        </thead>
        <tbody>

            <%  if (serverOn) { %>

                 <tr>
                    <td class="c1"><fmt:message key="index.uptime" /></td>
                    <td class="c2">
                        <%
                            long now = System.currentTimeMillis();
                            long lastStarted = webManager.getXMPPServer().getServerInfo().getLastStarted().getTime();
                            long uptime = now - lastStarted;
                            String uptimeDisplay = StringUtils.getElapsedTime(uptime);
                        %>

                        <%  if (uptimeDisplay != null) { %>
                            <%= uptimeDisplay %> -- started
                        <%  } %>

                        <%= JiveGlobals.formatDateTime(webManager.getXMPPServer().getServerInfo().getLastStarted()) %>
                    </td>
                </tr>

            <%  } %>

            <tr>
                <td class="c1"><fmt:message key="index.version" /></td>
                <td class="c2">
                    <%= AdminConsole.getAppName() %>
                    <%= AdminConsole.getVersionString() %>
                </td>
            </tr>
            <tr>
                <td class="c1"><fmt:message key="index.home" /></td>
                <td class="c2">
                    <%= JiveGlobals.getHomePath() %>
                </td>
            </tr>
            <tr>
                <td class="c1">
                    <fmt:message key="index.server_name" />
                </td>
                <td class="c2">
                    <% final IdentityStore identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( ConnectionType.SOCKET_C2S ); %>
                    <% try { %>
                    <% if (!identityStore.containsDomainCertificate()) {%>
                    <img src="images/warning-16x16.gif" width="12" height="12" alt="<fmt:message key="index.certificate-warning" />" title="<fmt:message key="index.certificate-warning" />">&nbsp;
                    <% } %>
                    <% } catch (Exception e) { %>
                    <img src="images/error-16x16.gif" width="12" height="12" alt="<fmt:message key="index.certificate-error" />" title="<fmt:message key="index.certificate-error" />">&nbsp;
                    <% } %>
                    <c:out value="${webManager.serverInfo.XMPPDomain}"/>
                    <% try { String whatevs = JID.domainprep(webManager.getXMPPServer().getServerInfo().getXMPPDomain()); } catch (Exception e) { %>
                    <img src="images/error-16x16.gif" width="12" height="12" alt="<fmt:message key="index.domain-stringprep-error" />" title="<fmt:message key="index.domain-stringprep-error" />">&nbsp;
                    <% } %>
                </td>
            </tr>
            <tr><td>&nbsp;</td></tr>
        </tbody>
        <thead>
            <tr>
                <th colspan="2" class="info-header"><fmt:message key="index.environment" /></th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td class="c1"><fmt:message key="index.jvm" /></td>
                <td class="c2">
                    <%
                        String vmName = System.getProperty("java.vm.name");
                        if (vmName == null) {
                            vmName = "";
                        }
                        else {
                            vmName = " -- " + vmName;
                        }
                    %>
                    <%= System.getProperty("java.version") %> <%= System.getProperty("java.vendor") %><%= vmName %>
                </td>
            </tr>
            <tr>
                <td class="c1"><fmt:message key="index.app" /></td>
                <td class="c2">
                    <%= application.getServerInfo() %>
                </td>
            </tr>
            <tr>
                <td class="c1">
                    <fmt:message key="index.host_name" />
                </td>
                <td class="c2">
                    <c:out value="${webManager.serverInfo.hostname}"/>
                    <% try { String whatevs = JID.domainprep(webManager.getXMPPServer().getServerInfo().getHostname()); } catch (Exception e) { %>
                    <img src="images/error-16x16.gif" width="12" height="12" alt="<fmt:message key="index.hostname-stringprep-error" />" title="<fmt:message key="index.hostname-stringprep-error" />">&nbsp;
                    <% } %>
                    <%  // Determine if the DNS configuration for this XMPP domain needs to be evaluated.
                        final String xmppDomain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
                        final String hostname = XMPPServer.getInstance().getServerInfo().getHostname();
                        boolean dnsIssue = false;
                        if ( !xmppDomain.equalsIgnoreCase( hostname ) )
                        {
                            dnsIssue = true;
                            final List<DNSUtil.WeightedHostAddress> dnsSrvRecords = DNSUtil.srvLookup( "xmpp-client", "tcp", xmppDomain );
                            for ( final DNSUtil.WeightedHostAddress dnsSrvRecord : dnsSrvRecords )
                            {
                                if ( hostname.equalsIgnoreCase( dnsSrvRecord.getHost() ) )
                                {
                                    dnsIssue = false;
                                    break;
                                }
                            }
                        }
                        if ( dnsIssue ) {
                        %>
                        <img src="images/warning-16x16.gif" width="12" height="12" alt="DNS configuration appears to be missing or incorrect.">
                            <fmt:message key="index.dns-warning">
                                <fmt:param><a href='dns-check.jsp'></fmt:param>
                                <fmt:param></a></fmt:param>
                            </fmt:message>
                        <%
                        }
                    %>
                </td>
            </tr>
            <tr>
                <td class="c1"><fmt:message key="index.os" /></td>
                <td class="c2">
                    <%= System.getProperty("os.name") %> / <%= System.getProperty("os.arch") %>
                </td>
            </tr>
            <tr>
                <td class="c1"><fmt:message key="index.local" /></td>
                <td class="c2">
                    <%= JiveGlobals.getLocale() %> / <%= JiveGlobals.getTimeZone().getDisplayName(JiveGlobals.getLocale()) %>
                    (<%= (JiveGlobals.getTimeZone().getRawOffset()/1000/60/60) %> GMT)
                </td>
            </tr>
            <tr>
                <td class="c1"><fmt:message key="index.process_owner" /></td>
                <td class="c2"><%= System.getProperty("user.name") %></td>
            </tr>
            <tr>
                <td class="c1"><fmt:message key="index.memory" /></td>
                <td>

                <table style="width: 300px">
                <tr>
                    <td style="vertical-align: middle">
                        <div class="bar">
                        <table style="border:1px #666 solid; width: 100%">
                        <tr>
                            <c:choose>
                                <c:when test="${memoryUsageAfterLastGC.percent >= 90}">
                                    <td style="height: 1em; width: ${memoryUsageAfterLastGC.percent}%; background-image: url('images/percent-bar-used-high.gif'); background-size: cover"></td>
                                </c:when>
                                <c:when test="${memoryUsageAfterLastGC.percent > 0}">
                                    <td style="height: 1em; width: ${memoryUsageAfterLastGC.percent}%; background-image: url('images/percent-bar-used-low.gif'); background-size: cover"></td>
                                </c:when>
                            </c:choose>
                            <td style="height: 1em; width: ${100 - memoryUsageAfterLastGC.percent}%; background-image: url('images/percent-bar-left.gif'); background-size: cover"></td>
                        </tr>
                        </table>
                        </div>
                    </td>
                    <td style="width: 1%; white-space: nowrap">
                        <div style="padding-left:6px;" class="c2">
                            <fmt:message key="index.memory_usage_description">
                                <fmt:param><fmt:formatNumber type="number" minFractionDigits="2" maxFractionDigits="2" value="${memoryUsageAfterLastGC.usedMemory}"/></fmt:param>
                                <fmt:param><fmt:formatNumber type="number" minFractionDigits="2" maxFractionDigits="2" value="${memoryUsageAfterLastGC.maximumMemory}"/></fmt:param>
                                <fmt:param><fmt:formatNumber type="number" minFractionDigits="1" maxFractionDigits="1" value="${memoryUsageAfterLastGC.percentUsed}"/></fmt:param>
                            </fmt:message>
                        </div>
                    </td>
                </tr>
                </table>
                </td>
            </tr>
        </tbody>
        </table>
        <!-- </div> -->
    </td>
    <% if (rssEnabled) { %>
    <td style="vertical-align: top; width: 40%">
        <div id="jive-latest-activity">
            <a href="<%= BlogPostServlet.URL.getValue() %>" class="jive-feed-icon"><img src="images/feed-icon-16x16.gif" alt="" style="border:0;" /></a>
            <h4><fmt:message key="index.cs_blog" /></h4>
            <div class="jive-bottom-line"></div>
        </div>
    </td>
    <% } %>
</table>

<br>

<div id="jive-title"><fmt:message key="index.server_port" /></div>
<div class="jive-table">
<table>
<thead>
    <tr>
        <th style="width: 100px;"><fmt:message key="ports.interface" /></th>
        <th style="width: 1px;"><fmt:message key="ports.port" /></th>
        <th style="width: 1px;">&nbsp;</th>
        <th style="width: 130px;"><fmt:message key="ports.type" /></th>
        <th><fmt:message key="ports.description" /></th>
    </tr>
</thead>
<tbody>

<%
    for ( ConnectionListener connectionListener : connectionManager.getListeners() )
    {
        if ( !connectionListener.isEnabled() )
        {
            continue;
        }

        pageContext.setAttribute( "connectionListener", connectionListener );

        final String interfaceName;
        if (connectionListener.getBindAddress() == null || connectionListener.getBindAddress().isAnyLocalAddress() ) {
            interfaceName = LocaleUtils.getLocalizedString("ports.all_ports");
        } else {
            interfaceName = connectionListener.getBindAddress().getHostName();
        }
%>

    <tr>
        <td><%= interfaceName %></td>
        <td><%= connectionListener.getPort() %></td>
        <td>
            <% if ( connectionListener.getTLSPolicy().equals( Connection.TLSPolicy.disabled ) ) { %>
            <img src="images/blank.gif" width="1" height="1" alt=""/>
            <% } else { %>
            <img src="images/lock.gif" alt="<fmt:message key="ports.secure.alt" />" title="<fmt:message key="ports.secure.alt" />"/>
            <% } %>
        </td>
        <td>
            <%
                final String typeName;
                switch ( connectionListener.getType() ) {
                    case SOCKET_C2S:
                        typeName = LocaleUtils.getLocalizedString("ports.client_to_server");
                        break;
                    case SOCKET_S2S:
                        typeName = LocaleUtils.getLocalizedString("ports.server_to_server");
                        break;
                    case COMPONENT:
                        typeName = LocaleUtils.getLocalizedString("ports.external_components");
                        break;
                    case CONNECTION_MANAGER:
                        typeName = LocaleUtils.getLocalizedString("ports.connection_manager");
                        break;
                    case WEBADMIN:
                        typeName = LocaleUtils.getLocalizedString("ports.admin_console");
                        break;
                    case BOSH_C2S:
                        typeName = LocaleUtils.getLocalizedString("ports.http_bind");
                        break;
                    default:
                        typeName = "(unspecified)";
                        break;
                }
            %>
            <%=typeName%>
        </td>
        <td>
            <c:choose>
                <c:when test="${connectionListener.type eq 'SOCKET_C2S' and connectionListener.TLSPolicy ne 'directTLS'}">
                    <fmt:message key="ports.client_to_server.desc"/>
                    <fmt:message key="ports.plaintext.desc">
                        <fmt:param><a href='connection-settings-socket-c2s.jsp'></fmt:param>
                        <fmt:param></a></fmt:param>
                    </fmt:message>
                </c:when>
                <c:when test="${connectionListener.type eq 'SOCKET_C2S' and connectionListener.TLSPolicy eq 'directTLS'}">
                    <fmt:message key="ports.client_to_server.desc_old_ssl"/>
                    <fmt:message key="ports.directtls.desc">
                        <fmt:param><a href='connection-settings-socket-c2s.jsp'></fmt:param>
                        <fmt:param></a></fmt:param>
                    </fmt:message>
                </c:when>
                <c:when test="${connectionListener.type eq 'SOCKET_S2S' and connectionListener.TLSPolicy ne 'directTLS'}" >
                    <fmt:message key="ports.server_to_server.desc"/>
                    <fmt:message key="ports.plaintext.desc">
                        <fmt:param><a href='connection-settings-socket-s2s.jsp'></fmt:param>
                        <fmt:param></a></fmt:param>
                    </fmt:message>
                </c:when>
                <c:when test="${connectionListener.type eq 'SOCKET_S2S' and connectionListener.TLSPolicy eq 'directTLS'}" >
                    <fmt:message key="ports.server_to_server.desc"/>
                    <fmt:message key="ports.directtls.desc">
                        <fmt:param><a href='connection-settings-socket-s2s.jsp'></fmt:param>
                        <fmt:param></a></fmt:param>
                    </fmt:message>
                </c:when>
                <c:when test="${connectionListener.type eq 'COMPONENT' and connectionListener.TLSPolicy ne 'directTLS'}">
                    <fmt:message key="ports.external_components.desc"/>
                    <fmt:message key="ports.plaintext.desc">
                        <fmt:param><a href='connection-settings-external-components.jsp'></fmt:param>
                        <fmt:param></a></fmt:param>
                    </fmt:message>
                </c:when>
                <c:when test="${connectionListener.type eq 'COMPONENT' and connectionListener.TLSPolicy eq 'directTLS'}">
                    <fmt:message key="ports.external_components.desc_old_ssl"/>
                    <fmt:message key="ports.directtls.desc">
                        <fmt:param><a href='connection-settings-external-components.jsp'></fmt:param>
                        <fmt:param></a></fmt:param>
                    </fmt:message>
                </c:when>
                <c:when test="${connectionListener.type eq 'CONNECTION_MANAGER' and connectionListener.TLSPolicy ne 'directTLS'}">
                    <fmt:message key="ports.connection_manager.desc"/>
                    <fmt:message key="ports.plaintext.desc">
                        <fmt:param><a href='connection-managers-settings.jsp'></fmt:param>
                        <fmt:param></a></fmt:param>
                    </fmt:message>
                </c:when>
                <c:when test="${connectionListener.type eq 'CONNECTION_MANAGER' and connectionListener.TLSPolicy eq 'directTLS'}">
                    <fmt:message key="ports.connection_manager.desc_old_ssl"/>
                    <fmt:message key="ports.directtls.desc">
                        <fmt:param><a href='connection-managers-settings.jsp'></fmt:param>
                        <fmt:param></a></fmt:param>
                    </fmt:message>
                </c:when>
                <c:when test="${connectionListener.type eq 'WEBADMIN' and connectionListener.TLSPolicy ne 'directTLS'}">
                    <fmt:message key="ports.admin_console.desc_unsecured"/>
                </c:when>
                <c:when test="${connectionListener.type eq 'WEBADMIN' and connectionListener.TLSPolicy eq 'directTLS'}">
                    <fmt:message key="ports.admin_console.desc_secured"/>
                </c:when>
                <c:when test="${connectionListener.type eq 'BOSH_C2S' and connectionListener.TLSPolicy ne 'directTLS'}">
                    <fmt:message key="ports.http_bind.desc_unsecured"/>
                </c:when>
                <c:when test="${connectionListener.type eq 'BOSH_C2S' and connectionListener.TLSPolicy eq 'directTLS'}">
                    <fmt:message key="ports.http_bind.desc_secured"/>
                </c:when>
            </c:choose>
        </td>
    </tr>
<%
    }

    final String interfaceName;
    if (((ConnectionManagerImpl)connectionManager).getListenAddress() == null || ((ConnectionManagerImpl)connectionManager).getListenAddress().isAnyLocalAddress() ) {
        interfaceName = LocaleUtils.getLocalizedString("ports.all_ports");
    } else {
        interfaceName = ((ConnectionManagerImpl)connectionManager).getListenAddress().getHostName();
    }
%>
    <%
        if (fileTransferProxy.isProxyEnabled()) {
    %>
    <tr>
        <td><%= interfaceName %></td>
        <td><%= fileTransferProxy.getProxyPort() %></td>
        <td><img src="images/blank.gif" width="1" height="1" alt=""></td>
        <td><fmt:message key="ports.file_proxy" /></td>
        <td><fmt:message key="ports.file_proxy.desc" /></td>
    </tr>
    <% } %>
    <%
        if (mediaProxyService.isEnabled()) {
    %>
    <tr>
        <td><%= interfaceName %></td>
        <td><%= mediaProxyService.getMinPort() %> - <%= mediaProxyService.getMaxPort() %></td>
        <td><img src="images/blank.gif" width="1" height="1" alt=""></td>
        <td><fmt:message key="ports.media_proxy" /></td>
        <td><fmt:message key="ports.media_proxy.desc" /></td>
    </tr>
    <% } %>
    <%
        if (JMXManager.isEnabled()) {
    %>
    <tr>
        <td><%= interfaceName %></td>
        <td><%= JMXManager.getPort() %></td>
        <td><% if (JMXManager.isSecure()) {
            %><img src="images/user.gif" alt="<fmt:message key="ports.jmx_console.alt" />" title="<fmt:message key="ports.jmx_console.alt" />"/><%
        } else {
            %><img src="images/blank.gif" width="1" height="1" alt=""><% }
        %></td>
        <td><fmt:message key="ports.jmx_console" /></td>
        <td><fmt:message key="ports.jmx_console.desc" /></td>
    </tr>
    <% } %>
</tbody>
</table>
</div>
<br>

<form action="server-props.jsp">
<input type="submit" value="<fmt:message key="global.edit_properties" />">
</form>

    </body>
</html>
