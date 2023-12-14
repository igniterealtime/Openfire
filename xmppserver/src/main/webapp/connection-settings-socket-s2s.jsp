<%--
  -
  - Copyright (C) 2016-2023 Ignite Realtime Foundation. All rights reserved.
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
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionConfiguration" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionListener" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.server.RemoteServerManager" %>
<%@ page import="org.jivesoftware.openfire.server.RemoteServerConfiguration" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.openfire.ConnectionManager" %>
<%@ page errorPage="error.jsp" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%
    final ConnectionType connectionType = ConnectionType.SOCKET_S2S;
    final ConnectionManager manager = XMPPServer.getInstance().getConnectionManager();

    pageContext.setAttribute("permissionPolicy", RemoteServerManager.getPermissionPolicy().toString());

    final ConnectionConfiguration plaintextConfiguration = manager.getListener( connectionType, false ).generateConnectionConfiguration();
    final ConnectionConfiguration directtlsConfiguration = manager.getListener( connectionType, true  ).generateConnectionConfiguration();

    boolean update = request.getParameter( "update" ) != null;
    boolean closeSettings = request.getParameter( "closeSettings" ) != null;
    boolean serverAllowed = request.getParameter( "serverAllowed" ) != null;
    boolean serverBlocked = request.getParameter( "serverBlocked" ) != null;
    boolean permissionUpdate = request.getParameter( "permissionUpdate" ) != null;
    String configToDelete = ParamUtils.getParameter( request, "deleteConf" );

    final Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update || closeSettings || serverAllowed || serverBlocked || permissionUpdate || configToDelete != null) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            closeSettings = false;
            serverAllowed = false;
            serverBlocked = false;
            configToDelete = null;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if ( update && errors.isEmpty() )
    {
        // plaintext
        final boolean plaintextEnabled = ParamUtils.getBooleanParameter( request, "plaintext-enabled" );
        final int plaintextTcpPort = ParamUtils.getIntParameter( request, "plaintext-tcpPort", plaintextConfiguration.getPort() );

        // Direct TLS
        final boolean directtlsEnabled      = ParamUtils.getBooleanParameter( request, "directtls-enabled" );
        final int directtlsTcpPort          = ParamUtils.getIntParameter( request, "directtls-tcpPort", directtlsConfiguration.getPort() );

        // Apply
        final ConnectionListener plaintextListener = manager.getListener( connectionType, false );
        final ConnectionListener directtlsListener = manager.getListener( connectionType, true  );

        plaintextListener.enable( plaintextEnabled );
        plaintextListener.setPort( plaintextTcpPort );

        directtlsListener.enable( directtlsEnabled );
        directtlsListener.setPort( directtlsTcpPort );

        // Log the event
        webManager.logEvent( "Updated connection settings for " + connectionType, "plain: enabled=" + plaintextEnabled + ", port=" + plaintextTcpPort + "\nDirect TLS: enabled=" + directtlsEnabled+ ", port=" + directtlsTcpPort+ "\n" );
        response.sendRedirect( "connection-settings-socket-s2s.jsp?success=update" );
    }
    else if ( permissionUpdate && errors.isEmpty() )
    {
        final String permissionFilter = ParamUtils.getParameter( request, "permissionFilter" );
        RemoteServerManager.setPermissionPolicy(permissionFilter);
        webManager.logEvent( "Updated s2s permission policy to: " + permissionFilter, null);
        response.sendRedirect( "connection-settings-socket-s2s.jsp?success=update" );
    }
    else if ( closeSettings && errors.isEmpty() )
    {
        // TODO below is the 'idle connection' handing. This should go into the connection configuration, like all other configuration.
        final boolean closeEnabled = ParamUtils.getBooleanParameter( request, "closeEnabled" );

        // Handle an update of the kicking task settings
        if (!closeEnabled) {
            // Disable kicking users by setting a value of -1
            webManager.getSessionManager().setServerSessionIdleTime( -1 );

            // Log the event
            webManager.logEvent( "disabled s2s idle kick", null );
            response.sendRedirect( "connection-settings-socket-s2s.jsp?success=idle" );
            return;
        }

        // do validation
        final String idletime = ParamUtils.getParameter( request, "idletime" );
        int idle = 0;
        if ( idletime == null )
        {
            errors.put( "idletime", "idletime" );
        }
        else
        {
            // Try to obtain an int from the provided strings
            if ( errors.isEmpty() )
            {
                try
                {
                    idle = Integer.parseInt( idletime ) * 1000 * 60;
                }
                catch ( NumberFormatException e )
                {
                    errors.put( "idletime", "idletime" );
                }

                if ( idle < 0 )
                {
                    errors.put( "idletime", "idletime" );
                }
            }
        }

        if ( errors.isEmpty() )
        {
            webManager.getSessionManager().setServerSessionIdleTime( idle );

            // Log the event
            webManager.logEvent( "updated s2s idle kick", "timeout = " + idle );
            response.sendRedirect( "connection-settings-socket-s2s.jsp?success=idle" );
            return;
        }
    }
    else if ( serverAllowed && errors.isEmpty() )
    {
        final String domain = ParamUtils.getParameter( request, "domain" );
        final String remotePort = ParamUtils.getParameter( request, "remotePort" );

        int intRemotePort = 0;
        // Validate params
        try
        {
            StringUtils.validateDomainName( domain );
        }
        catch ( IllegalArgumentException iae )
        {
            errors.put( "domain", "" );
        }
        if ( remotePort == null || remotePort.trim().length() == 0 || "0".equals( remotePort ) )
        {
            errors.put( "remotePort", "" );
        }
        else
        {
            try
            {
                intRemotePort = Integer.parseInt( remotePort );
            }
            catch ( NumberFormatException e )
            {
                errors.put( "remotePort", "" );
            }
        }

        // If no errors, continue:
        if ( errors.isEmpty() )
        {
            final RemoteServerConfiguration configuration = new RemoteServerConfiguration( domain );
            configuration.setRemotePort( intRemotePort );
            configuration.setPermission( RemoteServerConfiguration.Permission.allowed );
            RemoteServerManager.allowAccess( configuration );

            // Log the event
            webManager.logEvent( "added s2s access for " + domain, "domain = " + domain + "\nport = " + intRemotePort );
            response.sendRedirect( "connection-settings-socket-s2s.jsp?success=allow" );
        }
    }
    else if ( serverBlocked && errors.isEmpty() )
    {
        final String domain = ParamUtils.getParameter( request, "domain" );

        // Validate params
        try
        {
            StringUtils.validateDomainName( domain );
        }
        catch ( IllegalArgumentException iae )
        {
            errors.put( "domain", "" );
        }

        // If no errors, continue:
        if ( errors.isEmpty() )
        {
            RemoteServerManager.blockAccess( domain );

            // Log the event
            webManager.logEvent( "blocked s2s access for " + domain, "domain = " + domain );
            response.sendRedirect( "connection-settings-socket-s2s.jsp?success=block" );
        }
    }
    else if ( configToDelete != null && configToDelete.trim().length() != 0 && errors.isEmpty() )
    {
        RemoteServerManager.deleteConfiguration( configToDelete );

        // Log the event
        webManager.logEvent( "deleted s2s configuration", "config to delete = " + configToDelete );
        response.sendRedirect( "connection-settings-socket-s2s.jsp?success=delete" );
    }

    pageContext.setAttribute( "errors",                 errors );
    pageContext.setAttribute( "plaintextConfiguration", plaintextConfiguration );
    pageContext.setAttribute( "directtlsConfiguration", directtlsConfiguration );
    // pageContext.setAttribute( "clientIdle",              JiveGlobals.getIntProperty(     ConnectionSettings.Client.IDLE_TIMEOUT,    6*60*1000 ) );
    // pageContext.setAttribute( "pingIdleClients",         JiveGlobals.getBooleanProperty( ConnectionSettings.Client.KEEP_ALIVE_PING, true) );

    pageContext.setAttribute( "allowedServers", RemoteServerManager.getAllowedServers() );
    pageContext.setAttribute( "blockedServers", RemoteServerManager.getBlockedServers() );
%>
<html>
<head>
    <title><fmt:message key="server2server.settings.title"/></title>
    <meta name="pageID" content="server2server-settings"/>
    <script>
        // Displays or hides the configuration block for a particular connection type, based on the status of the
        // 'enable' checkbox for that connection type.
        function applyDisplayable( connectionType )
        {
            let configBlock, enabled;

            // Select the right configuration block and enable or disable it as defined by the the corresponding checkbox.
            configBlock = document.getElementById( connectionType + "-config" );
            enabled     = document.getElementById( connectionType + "-enabled" ).checked;

            if ( ( configBlock != null ) && ( enabled != null ) )
            {
                if ( enabled )
                {
                    configBlock.style.display = "block";
                }
                else
                {
                    configBlock.style.display = "none";
                }
            }
        }

        // Ensure that the various elements are set properly when the page is loaded.
        window.onload = function()
        {
            applyDisplayable( "plaintext" );
            applyDisplayable( "directtls" );
        };
    </script>
</head>
<body>

<c:choose>
    <c:when test="${not empty param.success and empty errors}">
        <admin:infoBox type="success">
            <c:choose>
                <c:when test="${param.success eq 'idle'}"><fmt:message key="server2server.settings.update"/></c:when>
                <c:when test="${param.success eq 'allow'}"><fmt:message key="server2server.settings.confirm.allowed"/></c:when>
                <c:when test="${param.success eq 'block'}"><fmt:message key="server2server.settings.confirm.blocked"/></c:when>
                <c:when test="${param.success eq 'delete'}"><fmt:message key="server2server.settings.confirm.deleted"/></c:when>
                <c:otherwise><fmt:message key="server2server.settings.confirm.updated"/></c:otherwise>
            </c:choose>
        </admin:infoBox>
    </c:when>
    <c:otherwise>
        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${err.key eq 'idletime'}"><fmt:message key="server2server.settings.valid.idle_minutes"/></c:when>
                    <c:when test="${err.key eq 'domain'}"><fmt:message key="server2server.settings.valid.domain"/></c:when>
                    <c:when test="${err.key eq 'remotePort'}"><fmt:message key="server2server.settings.valid.remotePort"/></c:when>
                    <c:otherwise>
                        <c:if test="${not empty err.value}">
                            <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                        </c:if>
                        (<c:out value="${err.key}"/>)
                    </c:otherwise>
                </c:choose>
            </admin:infobox>
        </c:forEach>
    </c:otherwise>
</c:choose>

<p>
    <fmt:message key="server2server.settings.info">
        <fmt:param value="<a href='server-session-summary.jsp'>" />
        <fmt:param value="</a>" />
    </fmt:message>
</p>

<form action="connection-settings-socket-s2s.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <fmt:message key="server2server.settings.boxtitle" var="boxtitle"/>
    <admin:contentBox title="${boxtitle}">

        <p><fmt:message key="server2server.settings.boxinfo"/></p>

        <table>
            <tr>
                <td colspan="2"><input type="checkbox" name="plaintext-enabled" id="plaintext-enabled" onclick="applyDisplayable('plaintext')" ${plaintextConfiguration.enabled ? 'checked' : ''}/><label for="plaintext-enabled"><fmt:message key="server2server.settings.label_enable"/></label></td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><label for="plaintext-tcpPort"><fmt:message key="ports.port"/></label></td>
                <td><input type="text" name="plaintext-tcpPort" id="plaintext-tcpPort" value="${plaintextConfiguration.port}"/></td>
            </tr>
            <tr>
                <td colspan="2"><a href="./connection-settings-advanced.jsp?connectionType=SOCKET_S2S&connectionMode=plain"><fmt:message key="ssl.settings.server.label_custom_info"/>...</a></td>
            </tr>
        </table>

    </admin:contentBox>

    <fmt:message key="ssl.settings.server.directtls.boxtitle" var="directtlsboxtitle"/>
    <admin:contentBox title="${directtlsboxtitle}">

        <p><fmt:message key="ssl.settings.server.directtls.info"/></p>

        <table>
            <tr>
                <td colspan="2"><input type="checkbox" name="directtls-enabled" id="directtls-enabled" onclick="applyDisplayable('directtls')" ${directtlsConfiguration.enabled ? 'checked' : ''}/><label for="directtls-enabled"><fmt:message key="ssl.settings.server.directtls.label_enable"/></label></td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><label for="directtls-tcpPort"><fmt:message key="ports.port"/></label></td>
                <td><input type="text" name="directtls-tcpPort" id="directtls-tcpPort" value="${directtlsConfiguration.port}"></td>
            </tr>
            <tr>
                <td colspan="2"><a href="./connection-settings-advanced.jsp?connectionType=SOCKET_S2S&connectionMode=directtls"><fmt:message key="ssl.settings.server.label_custom_info"/>...</a></td>
            </tr>
        </table>

    </admin:contentBox>

    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>

<br/>

<!-- BEGIN 'Idle Connection Settings' -->
<form action="connection-settings-socket-s2s.jsp?closeSettings" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <fmt:message key="server2server.settings.close_settings" var="idleTitle"/>
    <admin:contentBox title="${idleTitle}">
        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="closeEnabled" value="true" id="rb04" ${webManager.sessionManager.serverSessionIdleTime gt -1 ? 'checked' : ''}>
                </td>
                <td>
                    <c:if test="${webManager.sessionManager.serverSessionIdleTime gt -1}">
                        <fmt:parseNumber integerOnly="true" var="minutes">${webManager.sessionManager.serverSessionIdleTime div 60000}</fmt:parseNumber>
                    </c:if>

                    <label for="rb04"><fmt:message key="server2server.settings.close_session" /></label>
                    <input type="text" name="idletime" id="idletime" size="5" maxlength="5" onclick="this.form.closeEnabled[0].checked=true;" value="${webManager.sessionManager.serverSessionIdleTime le -1 ? 30 : minutes}">
                    <label for="idletime"><fmt:message key="global.minutes" /></label>.
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="closeEnabled" value="false" id="rb03" ${webManager.sessionManager.serverSessionIdleTime gt -1 ? '' : 'checked'}>
                </td>
                <td>
                    <label for="rb03"><fmt:message key="server2server.settings.never_close" /></label>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <input type="submit" value="<fmt:message key="global.save_settings" />">

</form>
<!-- END 'Idle Connection Settings' -->

<br/>

<!-- BEGIN 'Allowed to Connect' -->
<fmt:message key="server2server.settings.allowed" var="allowedTitle"/>
<admin:contentBox title="${allowedTitle}">
    <form action="connection-settings-socket-s2s.jsp" method="post">
        <input type="hidden" name="csrf" value="${csrf}">
        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="permissionFilter" value="blacklist" id="rb05" ${permissionPolicy eq 'blacklist'? 'checked' : '' }>
                </td>
                <td>
                    <label for="rb05">
                        <b><fmt:message key="server2server.settings.anyone" /></b> - <fmt:message key="server2server.settings.anyone_info" />
                    </label>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="permissionFilter" value="whitelist" id="rb06" ${permissionPolicy eq 'whitelist'? 'checked' : ''}>
                </td>
                <td>
                    <label for="rb06">
                        <b><fmt:message key="server2server.settings.whitelist" /></b> - <fmt:message key="server2server.settings.whitelist_info" />
                    </label>
                </td>
            </tr>
        </table>
        <br/>
        <input type="submit" name="permissionUpdate" value="<fmt:message key="global.save_settings" />">
        <br/><br/>
    </form>

    <form action="connection-settings-socket-s2s.jsp" method="post">
        <input type="hidden" name="csrf" value="${csrf}">
        <table class="jive-table">
            <tr>
                <th style="width: 1%">&nbsp;</th>
                <th style="width: 70%; white-space: nowrap"><fmt:message key="server2server.settings.domain" /></th>
                <th style="width: 19%; white-space: nowrap"><fmt:message key="server2server.settings.remotePort" /></th>
                <th style="width: 10%; text-align: center"><fmt:message key="global.delete" /></th>
            </tr>
            <c:choose>
                <c:when test="${empty allowedServers}">
                    <tr>
                        <td style="text-align: center" colspan="7"><fmt:message key="server2server.settings.empty_list" /></td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <c:forEach var="server" varStatus="status" items="${allowedServers}">
                        <tr>
                            <td>${ status.index + 1}</td>
                            <td><c:out value="${server.domain}"/></td>
                            <td><c:out value="${server.remotePort}"/></td>
                            <td style="border-right:1px #ccc solid; text-align: center">
                                <c:url var="deleteurl" value="connection-settings-socket-s2s.jsp">
                                    <c:param name="deleteConf" value="${server.domain}"/>
                                    <c:param name="csrf" value="${csrf}"/>
                                </c:url>
                                <a href="#" onclick="if (confirm('<fmt:message key="server2server.settings.confirm_delete" />')) { location.replace('${deleteurl}'); } "
                                   title="<fmt:message key="global.click_delete" />"
                                        ><img src="images/delete-16x16.gif" alt=""></a>
                            </td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </table>
        <br/>
        <table>
            <tr>
                <td nowrap>
                    <label for="domainAllowed"><fmt:message key="server2server.settings.domain" /></label>
                    <input type="text" size="40" name="domain" id="domainAllowed" value="${param.serverAllowed ? param.domain : ''}"/>
                    &nbsp;
                    <label for="remotePort"><fmt:message key="server2server.settings.remotePort" /></label>
                    <input type="text" size="5" name="remotePort" id="remotePort" value="${param.serverAllowed ? param.remotePort : '5269'}"/>
                    <input type="submit" name="serverAllowed" value="<fmt:message key="server2server.settings.allow" />">
                </td>
            </tr>
        </table>
    </form>

</admin:contentBox>
<!-- END 'Allowed to Connect' -->

<!-- BEGIN 'Not Allowed to Connect' -->
<fmt:message key="server2server.settings.disallowed" var="disallowedTitle"/>
<admin:contentBox title="${disallowedTitle}">
    <table><tr><td>
        <fmt:message key="server2server.settings.disallowed.info" />
    </td></tr></table>
    <p>
    <table class="jive-table">
        <tr>
            <th style="width: 1%;">&nbsp;</th>
            <th style="width: 89%; white-space: nowrap"><fmt:message key="server2server.settings.domain" /></th>
            <th style="width: 10%; text-align: center"><fmt:message key="global.delete" /></th>
        </tr>
        <c:choose>
            <c:when test="${empty blockedServers}">
                <tr>
                    <td style="text-align: center" colspan="7"><fmt:message key="server2server.settings.empty_list" /></td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach var="server" varStatus="status" items="${blockedServers}">
                    <tr>
                        <td>${ status.index + 1}</td>
                        <td><c:out value="${server.domain}"/></td>
                        <td style="border-right:1px #ccc solid; text-align: center">
                                <c:url var="deleteurl" value="connection-settings-socket-s2s.jsp">
                                    <c:param name="deleteConf" value="${server.domain}"/>
                                    <c:param name="csrf" value="${csrf}"/>
                                </c:url>
                            <a href="#" onclick="if (confirm('<fmt:message key="server2server.settings.confirm_delete" />')) { location.replace('${deleteurl}'); } "
                               title="<fmt:message key="global.click_delete" />"
                                    ><img src="images/delete-16x16.gif" alt=""></a>
                        </td>
                    </tr>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </table>
    <br>
    <form action="connection-settings-socket-s2s.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <label for="domainBlocked"><fmt:message key="server2server.settings.domain" /></label>
                </td>
                <td>
                    <input type="text" size="40" name="domain" id="domainBlocked" value="${param.serverBlocked ? param.domain : ''}"/>&nbsp;
                    <input type="submit" name="serverBlocked" value="<fmt:message key="server2server.settings.block" />">
                </td>
            </tr>
        </table>
    </form>
</admin:contentBox>
<!-- END 'Not Allowed to Connect' -->

</body>
</html>
