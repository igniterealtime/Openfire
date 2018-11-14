<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.Connection" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.component.ExternalComponentConfiguration" %>
<%@ page import="org.jivesoftware.openfire.component.ExternalComponentManager" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionConfiguration" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionListener" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionManagerImpl" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.util.ModificationNotAllowedException" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="gnu.inet.encoding.StringprepException" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page errorPage="error.jsp" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%
    final ConnectionType connectionType = ConnectionType.COMPONENT;
    final ConnectionManagerImpl manager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();

    final ConnectionConfiguration plaintextConfiguration  = manager.getListener( connectionType, false ).generateConnectionConfiguration();
    final ConnectionConfiguration legacymodeConfiguration = manager.getListener( connectionType, true  ).generateConnectionConfiguration();

    final Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    boolean update = request.getParameter( "update" ) != null;
    boolean permissionUpdate = request.getParameter( "permissionUpdate" ) != null;
    String configToDelete = ParamUtils.getParameter( request, "deleteConf" );
    boolean componentAllowed = request.getParameter( "componentAllowed" ) != null;
    boolean componentBlocked = request.getParameter( "componentBlocked" ) != null;

    if (update || permissionUpdate || configToDelete != null || componentAllowed || componentBlocked) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            permissionUpdate = false;
            configToDelete = null;
            componentAllowed = false;
            componentBlocked = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);


    if ( update && errors.isEmpty() )
    {
        // plaintext
        final boolean plaintextEnabled      = ParamUtils.getBooleanParameter( request, "plaintext-enabled" );
        final int plaintextTcpPort          = ParamUtils.getIntParameter( request, "plaintext-tcpPort", plaintextConfiguration.getPort() );

        // legacymode
        final boolean legacymodeEnabled      = ParamUtils.getBooleanParameter( request, "legacymode-enabled" );
        final int legacymodeTcpPort          = ParamUtils.getIntParameter( request, "legacymode-tcpPort", legacymodeConfiguration.getPort() );

        // Apply
        final ConnectionListener plaintextListener  = manager.getListener( connectionType, false );
        final ConnectionListener legacymodeListener = manager.getListener( connectionType, true  );

        plaintextListener.enable( plaintextEnabled );
        plaintextListener.setPort( plaintextTcpPort );
        legacymodeListener.enable( legacymodeEnabled );
        legacymodeListener.setPort( legacymodeTcpPort );

        // Log the event
        webManager.logEvent( "Updated connection settings for " + connectionType, "plain: enabled=" + plaintextEnabled + ", port=" + plaintextTcpPort + "\nlegacy: enabled=" + legacymodeEnabled+ ", port=" + legacymodeTcpPort+ "\n" );
        response.sendRedirect( "connection-settings-external-components.jsp?success=true" );
        return;
    }

    // Process Permission update configuration change.

    if ( permissionUpdate && errors.isEmpty() )
    {
        final String defaultSecret = ParamUtils.getParameter( request, "defaultSecret" );
        final String permissionFilter = ParamUtils.getParameter( request, "permissionFilter" );
        if ( defaultSecret == null || defaultSecret.trim().isEmpty() )
        {
            errors.put( "defaultSecret", "" );
        }
        else
        {
            try
            {
                ExternalComponentManager.setPermissionPolicy( permissionFilter );
                ExternalComponentManager.setDefaultSecret( defaultSecret );

                // Log the event
                webManager.logEvent( "set external component permission policy", "filter = " + permissionFilter );
                response.sendRedirect( "connection-settings-external-components.jsp?success=true" );
                return;
            }
            catch ( ModificationNotAllowedException e )
            {
                errors.put( "permission", e.getMessage() );
            }
        }
    }

    // Process removal of a blacklist or whitelist item.

    if ( configToDelete != null && !configToDelete.trim().isEmpty() && errors.isEmpty() )
    {
        try
        {
            ExternalComponentManager.deleteConfiguration( configToDelete );

            // Log the event
            webManager.logEvent( "deleted a external component configuration", "config is " + configToDelete );
            response.sendRedirect( "connection-settings-external-components.jsp?success=delete" );
            return;
        }
        catch ( ModificationNotAllowedException e )
        {
            errors.put( "delete", e.getMessage() );
        }
    }

    // Process addition to whitelist.
    String subdomain = ParamUtils.getParameter( request, "subdomain" ); // shared with blacklist.
    if ( subdomain != null )
    {
        subdomain = subdomain.trim();
        try {
            subdomain = JID.domainprep(subdomain);
            // Remove the hostname if the user is not sending just the subdomain.
            subdomain = subdomain.replace( "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain(), "" );
        } catch (Exception e) {
            errors.put("subdomain", e.getMessage());
        }
    }
    if ( componentAllowed && errors.isEmpty() )
    {
        final String secret = ParamUtils.getParameter( request, "secret" );

        // Validate params
        if ( subdomain == null || subdomain.trim().isEmpty() )
        {
            errors.put( "subdomain", "" );
        }
        if ( secret == null || secret.trim().isEmpty() )
        {
            errors.put( "secret", "" );
        }

        // If no errors, continue:
        if ( errors.isEmpty() )
        {
            final ExternalComponentConfiguration configuration = new ExternalComponentConfiguration( subdomain, false, ExternalComponentConfiguration.Permission.allowed, secret );
            try
            {
                ExternalComponentManager.allowAccess( configuration );

                // Log the event
                webManager.logEvent( "allowed external component access", "configuration = " + configuration );
                response.sendRedirect( "connection-settings-external-components.jsp?success=allow" );
                return;
            }
            catch ( ModificationNotAllowedException e )
            {
                errors.put( "allow", e.getMessage() );
            }
        }
    }

    // Process addition to blacklist.

    if ( componentBlocked && errors.isEmpty() )
    {
        if ( subdomain == null || subdomain.trim().isEmpty() )
        {
            errors.put( "subdomain", "" );
        }

        // If no errors, continue:
        if ( errors.isEmpty() )
        {
            try
            {
                ExternalComponentManager.blockAccess( subdomain );

                // Log the event
                webManager.logEvent( "blocked external component access", "subdomain = " + subdomain );
                response.sendRedirect( "connection-settings-external-components.jsp?success=block" );
                return;
            }
            catch ( ModificationNotAllowedException e )
            {
                errors.put( "block", e.getMessage() );
            }
        }
    }
    pageContext.setAttribute( "errors",                  errors );
    pageContext.setAttribute( "plaintextConfiguration",  plaintextConfiguration );
    pageContext.setAttribute( "legacymodeConfiguration", legacymodeConfiguration );

    pageContext.setAttribute( "defaultSecret", ExternalComponentManager.getDefaultSecret() );
    pageContext.setAttribute( "permissionFilter", ExternalComponentManager.getPermissionPolicy() );
    pageContext.setAttribute( "allowedComponents", ExternalComponentManager.getAllowedComponents() );
    pageContext.setAttribute( "blockedComponents", ExternalComponentManager.getBlockedComponents() );
%>
<html>
<head>
    <title><fmt:message key="component.settings.title"/></title>
    <meta name="pageID" content="external-components-settings"/>
    <script type="text/javascript">
        // Displays or hides the configuration block for a particular connection type, based on the status of the
        // 'enable' checkbox for that connection type.
        function applyDisplayable( connectionType )
        {
            var configBlock, enabled;

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
            applyDisplayable( "legacymode" );
        };
    </script>
</head>
<body>

<c:choose>
    <c:when test="${not empty param.success and empty errors}">
        <admin:infoBox type="success">
            <c:choose>
                <c:when test="${param.success eq 'allow'}"><fmt:message key="component.settings.confirm.allowed"/></c:when>
                <c:when test="${param.success eq 'block'}"><fmt:message key="component.settings.confirm.blocked"/></c:when>
                <c:when test="${param.success eq 'delete'}"><fmt:message key="component.settings.confirm.deleted"/></c:when>
                <c:otherwise><fmt:message key="component.settings.confirm.updated"/></c:otherwise>
            </c:choose>
        </admin:infoBox>
    </c:when>
    <c:otherwise>
        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${err.key eq 'defaultSecret'}"><fmt:message key="component.settings.valid.defaultSecret"/></c:when>
                    <c:when test="${err.key eq 'subdomain'}"><fmt:message key="component.settings.valid.subdomain"/></c:when>
                    <c:when test="${err.key eq 'secret'}"><fmt:message key="component.settings.valid.secret"/></c:when>
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
    <fmt:message key="component.settings.info">
        <fmt:param value="<a href='component-session-summary.jsp'>" />
        <fmt:param value="</a>" />
    </fmt:message>
</p>

<form action="connection-settings-external-components.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <fmt:message key="component.settings.plaintext.boxtitle" var="plaintextboxtitle"/>
    <admin:contentBox title="${plaintextboxtitle}">

        <p><fmt:message key="component.settings.plaintext.info"/></p>

        <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="middle">
                <td colspan="2"><input type="checkbox" name="plaintext-enabled" id="plaintext-enabled" onclick="applyDisplayable('plaintext')" ${plaintextConfiguration.enabled ? 'checked' : ''}/><label for="plaintext-enabled"><fmt:message key="component.settings.plaintext.label_enable"/></label></td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap><label for="plaintext-tcpPort"><fmt:message key="ports.port"/></label></td>
                <td width="99%"><input type="text" name="plaintext-tcpPort" id="plaintext-tcpPort" value="${plaintextConfiguration.port}"/></td>
            </tr>
            <tr valign="middle">
                <td colspan="2"><a href="./connection-settings-advanced.jsp?connectionType=COMPONENT&connectionMode=plain"><fmt:message key="ssl.settings.client.label_custom_info"/>...</a></td>
            </tr>
        </table>

    </admin:contentBox>

    <fmt:message key="component.settings.legacymode.boxtitle" var="legacymodeboxtitle"/>
    <admin:contentBox title="${legacymodeboxtitle}">

        <p><fmt:message key="component.settings.legacymode.info"/></p>

        <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="middle">
                <td colspan="2"><input type="checkbox" name="legacymode-enabled" id="legacymode-enabled" onclick="applyDisplayable('legacymode')" ${legacymodeConfiguration.enabled ? 'checked' : ''}/><label for="legacymode-enabled"><fmt:message key="component.settings.legacymode.label_enable"/></label></td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap><label for="legacymode-tcpPort"><fmt:message key="ports.port"/></label></td>
                <td width="99%"><input type="text" name="legacymode-tcpPort" id="legacymode-tcpPort" value="${legacymodeConfiguration.port}"></td>
            </tr>
            <tr valign="middle">
                <td colspan="2"><a href="./connection-settings-advanced.jsp?connectionType=COMPONENT&connectionMode=legacy"><fmt:message key="ssl.settings.client.label_custom_info"/>...</a></td>
            </tr>
        </table>

    </admin:contentBox>

    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>

<!-- BEGIN 'Allowed to Connect' -->
<fmt:message key="component.settings.allowed" var="allowedTitle" />
<admin:contentBox title="${allowedTitle}">
    <form action="connection-settings-external-components.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
        <table cellpadding="3" cellspacing="0" border="0" width="100%" >
            <tr valign="top">
                <td colspan="2">
                    <label for="defaultSecret"><fmt:message key="component.settings.defaultSecret" /></label>&nbsp;
                    <input type="text" size="15" maxlength="70" name="defaultSecret" id="defaultSecret" value="${fn:escapeXml(defaultSecret)}"/>
                </td>
            </tr>

            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="permissionFilter" value="blacklist" id="rb03" ${permissionFilter eq "blacklist" ? "checked" : ""}>
                </td>
                <td width="99%">
                    <label for="rb03">
                        <b><fmt:message key="component.settings.anyone" /></b> - <fmt:message key="component.settings.anyone_info" />
                    </label>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%">
                    <input type="radio" name="permissionFilter" value="whitelist" id="rb04" ${permissionFilter eq "whitelist" ? "checked" : ""}>
                </td>
                <td width="99%" nowrap>
                    <label for="rb04">
                        <b><fmt:message key="component.settings.whitelist" /></b> - <fmt:message key="component.settings.whitelist_info" />
                    </label>
                </td>
            </tr>
        </table>

        <br/>

        <input type="submit" name="permissionUpdate" value="<fmt:message key="global.save_settings" />">
    </form>

    <br>

    <table class="jive-table" cellpadding="0" cellspacing="0" border="0">
        <tr>
            <th width="1%">&nbsp;</th>
            <th width="50%" nowrap><fmt:message key="component.settings.subdomain" /></th>
            <th width="49%" nowrap><fmt:message key="component.settings.secret" /></th>
            <th width="10%" nowrap><fmt:message key="global.delete" /></th>
        </tr>
        <c:choose>
            <c:when test="${empty allowedComponents}">
                <tr>
                    <td align="center" colspan="7"><fmt:message key="component.settings.empty_list" /></td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach var="component" varStatus="status" items="${allowedComponents}">
                    <tr class="${ ( (status.index + 1) % 2 ) eq 0 ? 'jive-even' : 'jive-odd'}">
                        <td>${ status.index + 1}</td>
                        <td><c:out value="${component.subdomain}"/></td>
                        <td><c:out value="${component.secret}"/></td>
                        <td align="center" style="border-right:1px #ccc solid;">
                            <c:url var="deleteurl" value="connection-settings-external-components.jsp">
                                <c:param name="deleteConf" value="${component.subdomain}"/>
                                <c:param name="csrf" value="${csrf}"/>
                            </c:url>
                            <a href="#" onclick="if (confirm('<fmt:message key="component.settings.confirm_delete" />')) { location.replace('${deleteurl}'); } "
                               title="<fmt:message key="global.click_delete" />"><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                        </td>
                    </tr>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </table>

    <br/>

    <form action="connection-settings-external-components.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
        <table cellpadding="3" cellspacing="1" border="0">
            <tr>
                <td nowrap width="1%">
                    <label for="componentAllowedSubdomain"><fmt:message key="component.settings.subdomain" /></label>
                </td>
                <td>
                    <input type="text" size="40" name="subdomain" id="componentAllowedSubdomain" value="${fn:escapeXml(param.containsKey('componentAllowed') and not empty errors ? param[ 'subdomain' ] : '')}"/>
                </td>
                <td nowrap width="1%">
                    <label for="componentAllowedSecret"><fmt:message key="component.settings.secret" /></label>
                </td>
                <td>
                    <input type="text" size="15" name="secret" id="componentAllowedSecret" value="${fn:escapeXml(param.containsKey('componentAllowed') and not empty errors ? param[ 'secret' ] : '')}"/>
                </td>
            </tr>
            <tr align="center">
                <td colspan="4">
                    <input type="submit" name="componentAllowed" value="<fmt:message key="component.settings.allow" />">
                </td>
            </tr>
        </table>
    </form>
</admin:contentBox>
<!-- END 'Allowed to Connect' -->

<!-- BEGIN 'Not Allowed to Connect' -->
<fmt:message key="component.settings.disallowed" var="disallowedTitle"/>
<admin:contentBox title="${disallowedTitle}">
    <p><fmt:message key="component.settings.disallowed.info" /></p>
    <table class="jive-table" cellpadding="3" cellspacing="0" border="0" >
        <tr>
            <th width="1%">&nbsp;</th>
            <th width="89%" nowrap><fmt:message key="component.settings.subdomain" /></th>
            <th width="10%" nowrap><fmt:message key="global.delete" /></th>
        </tr>
        <c:choose>
            <c:when test="${empty blockedComponents}">
                <tr>
                    <td align="center" colspan="7"><fmt:message key="component.settings.empty_list" /></td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach var="component" varStatus="status" items="${blockedComponents}">
                    <tr class="${ ( (status.index + 1) % 2 ) eq 0 ? 'jive-even' : 'jive-odd'}">
                        <td>${ status.index + 1}</td>
                        <td><c:out value="${component.subdomain}"/></td>
                        <td align="center" style="border-right:1px #ccc solid;">
                            <c:url var="deleteurl" value="connection-settings-external-components.jsp">
                                <c:param name="deleteConf" value="${component.subdomain}"/>
                                <c:param name="csrf" value="${csrf}"/>
                            </c:url>
                            <a href="#" onclick="if (confirm('<fmt:message key="component.settings.confirm_delete" />')) { location.replace('${deleteurl}'); } "
                               title="<fmt:message key="global.click_delete" />"><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                        </td>
                    </tr>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </table>

    <br/>

    <form action="connection-settings-external-components.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
        <table cellpadding="3" cellspacing="1" border="0">
            <tr>
                <td nowrap width="1%">
                    <label for="disallowedSubdomain"><fmt:message key="component.settings.subdomain" /></label>
                </td>
                <td>
                    <input type="text" size="40" name="subdomain" id="disallowedSubdomain"/>&nbsp;
                    <input type="submit" name="componentBlocked" value="<fmt:message key="component.settings.block" />">
                </td>
            </tr>
        </table>
    </form>

</admin:contentBox>
<!-- END 'Not Allowed to Connect' -->

</body>
</html>
