<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.openfire.Connection" %>
<%@ page import="org.jivesoftware.openfire.spi.*" %>
<%@ page import="java.util.*" %>
<%@ page errorPage="error.jsp" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%
    boolean update = request.getParameter( "update" ) != null;

    final Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    pageContext.setAttribute( "errors", errors );

    ConnectionType connectionType = null;
    try {
        connectionType = ConnectionType.valueOf( ParamUtils.getParameter( request, "connectionType" ) );
        pageContext.setAttribute( "connectionType", connectionType );
    } catch (RuntimeException ex) {
        errors.put( "connectionType", ex.getMessage() );
    }

    final String connectionModeParam = ParamUtils.getParameter( request, "connectionMode" );
    if ( "plain".equalsIgnoreCase( connectionModeParam ) || "legacy".equalsIgnoreCase( connectionModeParam ) ) {
        pageContext.setAttribute( "connectionMode", connectionModeParam.toLowerCase() );
    } else {
        errors.put( "connectionMode", "Unrecognized connection mode." );
    }

    final ConnectionManagerImpl manager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
    final boolean startInSslMode = "legacy".equalsIgnoreCase( connectionModeParam );

    if ( update && errors.isEmpty() )
    {
        final ConnectionConfiguration configuration = manager.getListener( connectionType, startInSslMode ).generateConnectionConfiguration();

        // Read and parse parameters
        final boolean enabled = ParamUtils.getBooleanParameter( request, "enabled" );
        final int tcpPort = ParamUtils.getIntParameter( request, "tcpPort", configuration.getPort() );
        final int readBuffer  = ParamUtils.getIntParameter( request, "readBuffer", -1 );
        final String tlsPolicyText = ParamUtils.getParameter( request, "tlspolicy", true );
        final Connection.TLSPolicy tlsPolicy;
        if ( tlsPolicyText == null || tlsPolicyText.isEmpty() ) {
            tlsPolicy = configuration.getTlsPolicy();
        } else {
            tlsPolicy = Connection.TLSPolicy.valueOf( tlsPolicyText );
        }
        final String mutualAuthenticationText = ParamUtils.getParameter( request, "mutualauthentication", true );
        final Connection.ClientAuth mutualAuthentication;
        if ( mutualAuthenticationText == null || mutualAuthenticationText.isEmpty() ) {
            mutualAuthentication = configuration.getClientAuth();
        } else {
            mutualAuthentication = Connection.ClientAuth.valueOf( mutualAuthenticationText );
        }

        final Enumeration<String> parameterNames = request.getParameterNames();
        final Set<String> protocols = new TreeSet<>();
        while ( parameterNames.hasMoreElements() )
        {
            final String parameterName = parameterNames.nextElement();
            if ( parameterName.startsWith( "protocol-" ) )
            {
                protocols.add( parameterName.substring( "protocols-".length() -1 ) );
            }
        }

        final String[] cipherSuites = ParamUtils.getParameters( request, "cipherSuitesEnabled" );
        final int listenerMaxThreads = ParamUtils.getIntParameter( request, "maxThreads", configuration.getMaxThreadPoolSize() );
        final boolean acceptSelfSignedCertificates = ParamUtils.getBooleanParameter( request, "accept-self-signed-certificates" );
        final boolean verifyCertificateValidity = ParamUtils.getBooleanParameter( request, "verify-certificate-validity" );


        // Apply new configuration
        final ConnectionListener listener = manager.getListener( connectionType, startInSslMode );

        listener.enable( enabled );
        listener.setPort( tcpPort );
        // TODO: listener.setMaxBufferSize( readBuffer );
        listener.setTLSPolicy( tlsPolicy );
        listener.setClientAuth( mutualAuthentication );
        // TODO: listener.setMaxThreadPoolSize( listenerMaxThreads);
        listener.setEncryptionProtocols( protocols );
        listener.setEncryptionCipherSuites( cipherSuites );
        listener.setAcceptSelfSignedCertificates( acceptSelfSignedCertificates );
        listener.setVerifyCertificateValidity( verifyCertificateValidity );

        // Log the event
        webManager.logEvent( "Updated connection settings for " + connectionType + " (mode: " + connectionModeParam + ")", configuration.toString() );
    }

    if ( errors.isEmpty() ) {
        // Refresh the configuration (in case new settings were applied)
        final ConnectionConfiguration configuration = manager.getListener( connectionType, startInSslMode ).generateConnectionConfiguration();
        if ( configuration == null ) {
            errors.put( "configuration", null );
        } else {
            pageContext.setAttribute( "configuration", configuration );
        }
    }

    pageContext.setAttribute( "supportedProtocols", EncryptionArtifactFactory.getSupportedProtocols() );
    pageContext.setAttribute( "supportedCipherSuites", EncryptionArtifactFactory.getSupportedCipherSuites() );

%>
<c:set var="connectionTypeTranslation">
    <c:choose>
        <c:when test="${connectionType eq 'SOCKET_S2S'}">
            <fmt:message key="connection-type.socket-s2s"/>
        </c:when>
        <c:when test="${connectionType eq 'SOCKET_C2S'}">
            <fmt:message key="connection-type.socket-c2s"/>
        </c:when>
        <c:when test="${connectionType eq 'BOSH_C2S'}">
            <fmt:message key="connection-type.bosh-c2s"/>
        </c:when>
        <c:when test="${connectionType eq 'WEBADMIN'}">
            <fmt:message key="connection-type.webadmin"/>
        </c:when>
        <c:when test="${connectionType eq 'COMPONENT'}">
            <fmt:message key="connection-type.component"/>
        </c:when>
        <c:when test="${connectionType eq 'CONNECTION_MANAGER'}">
            <fmt:message key="connection-type.connection-manager"/>
        </c:when>
        <c:otherwise>
            <fmt:message key="connection-type.unspecified"/>
        </c:otherwise>
    </c:choose>
</c:set>
<c:set var="connectionModeTranslation">
    <c:choose>
        <c:when test="${connectionMode eq 'plain'}">
            <fmt:message key="connection-mode.plain"/>
        </c:when>
        <c:when test="${connectionMode eq 'legacy'}">
            <fmt:message key="connection-mode.legacy"/>
        </c:when>
        <c:otherwise>
            <fmt:message key="connection-mode.unspecified"/>
        </c:otherwise>
    </c:choose>
</c:set>
<html>
<head>
    <c:choose>
        <c:when test="${connectionType eq 'SOCKET_S2S'}">
            <title><fmt:message key="server2server.settings.title"/></title>
            <meta name="pageID" content="server2server-settings"/>
        </c:when>
        <c:when test="${connectionType eq 'SOCKET_C2S'}">
            <title><fmt:message key="client.connections.settings.title"/></title>
            <meta name="pageID" content="client-connections-settings"/>
        </c:when>
        <c:when test="${connectionType eq 'COMPONENT'}">
            <title><fmt:message key="component.settings.title"/></title>
            <meta name="pageID" content="external-components-settings"/>
        </c:when>
        <c:when test="${connectionType eq 'CONNECTION_MANAGER'}">
            <title><fmt:message key="connection-manager.settings.title"/></title>
            <meta name="pageID" content="connection-managers-settings"/>
        </c:when>
    </c:choose>
    <%--<meta name="subPageID" content="connection-settings-advanced"/>--%>
    <script type="text/javascript">
        // Displays or hides the configuration blocks, based on the status of selected settings.
        function applyDisplayable()
        {
            var tlsConfigs, displayValue, i, len;

            displayValue = ( document.getElementById( "tlspolicy-disabled" ).checked ? "none" : "block" );

            // Select the right configuration block and enable or disable it as defined by the the corresponding checkbox.
            tlsConfigs = document.getElementsByClassName( "tlsconfig" );
            for ( i = 0, len = tlsConfigs.length; i < len; i++ )
            {
                // Hide or show the info block (as well as it's title, which is the previous sibling element)
                tlsConfigs[ i ].parentElement.style.display = displayValue;
                tlsConfigs[ i ].parentElement.previousSibling.style.display = displayValue;
            }
        }

        // Marks all options in a select element as 'selected' (useful prior to form submission)
        function selectAllOptions( selectedId )
        {
            var select, i, len;

            select = document.getElementById( selectedId );

            for ( i = 0, len = select.options.length; i < len; i++ )
            {
                select.options[ i ].selected = true;
            }
        }

        // Moves selected option values from one select element to another.
        function moveSelectedFromTo( from, to )
        {
            var selected, i, len;

            selected = getSelectValues( document.getElementById( from ) );

            for ( i = 0, len = selected.length; i < len; i++ )
            {
                document.getElementById( to ).appendChild( selected[ i ] );
            }
        }

        // Return an array of the selected options. argument is an HTML select element
        function getSelectValues( select )
        {
            var i, len, result;

            result = [];

            for ( i = 0, len = select.options.length; i < len; i++ )
            {
                if ( select.options[ i ].selected )
                {
                    result.push( select.options[ i ] );
                }
            }
            return result;
        }

        // Ensure that the various elements are set properly when the page is loaded.
        window.onload = function()
        {
            applyDisplayable();
        };
    </script>

</head>
<body>

<!-- Display all errors -->
<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'connectionType'}"><fmt:message key="connection.advanced.settings.error.connectiontype"/></c:when>
            <c:when test="${err.key eq 'connectionMode'}"><fmt:message key="connection.advanced.settings.error.connectionmode"/></c:when>
            <c:otherwise>
                <c:if test="${not empty err.value}">
                    <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                </c:if>
                (<c:out value="${err.key}"/>)
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:forEach>

<!-- Display all success reports, but only if there were no errors. -->
<c:if test="${param.success and empty errors}">
    <admin:infoBox type="success">
        <c:choose>
            <c:when test="${connectionType eq 'SOCKET_S2S'}">
                <fmt:message key="server2server.settings.update" />
            </c:when>
            <c:when test="${connectionType eq 'SOCKET_C2S'}">
                <fmt:message key="client.connections.settings.confirm.updated" />
            </c:when>
            <c:when test="${connectionType eq 'COMPONENT'}">
                <fmt:message key="component.settings.confirm.updated" />
            </c:when>
            <c:when test="${connectionType eq 'CONNECTION_MANAGER'}">
                <fmt:message key="connection-manager.settings.confirm.updated" />
            </c:when>
        </c:choose>
    </admin:infoBox>
</c:if>

<!-- Introduction at the top of the page -->
<p>
    <fmt:message key="connection.advanced.settings.info">
        <fmt:param value="${connectionModeTranslation} ${connectionTypeTranslation}" />
    </fmt:message>
</p>

<form action="connection-settings-advanced.jsp?connectionType=${connectionType}&connectionMode=${connectionMode}" onsubmit="selectAllOptions('cipherSuitesEnabled')" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="update" value="true" />

    <fmt:message key="connection.advanced.settings.tcp.boxtitle" var="tcpboxtitle"/>
    <admin:contentBox title="${tcpboxtitle}">
        
        <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="middle">
                <td width="100%" colspan="2"><input type="checkbox" name="enabled" id="enabled" ${configuration.enabled ? 'checked' : ''}/><label for="enabled"><fmt:message key="connection.advanced.settings.tcp.label_enable"/></label></td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap><label for="tcpPort"><fmt:message key="ports.port"/></label></td>
                <td width="99%"><input type="text" name="tcpPort" id="tcpPort" value="${configuration.port}"/></td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap><label for="readBuffer"><fmt:message key="connection.advanced.settings.tcp.label_readbuffer"/></label></td>
                <td width="99%"><input type="text" name="readBuffer" id="readBuffer" value="${configuration.maxBufferSize gt 0 ? configuration.maxBufferSize : ''}" readonly/> <fmt:message key="connection.advanced.settings.tcp.label_readbuffer_suffix"/></td>
            </tr>
        </table>

    </admin:contentBox>

    <c:if test="${connectionMode eq 'plain'}">
        <fmt:message key="connection.advanced.settings.starttls.boxtitle" var="starttlsboxtitle"/>
        <admin:contentBox title="${starttlsboxtitle}">
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td>
                        <input type="radio" name="tlspolicy" value="disabled" id="tlspolicy-disabled" ${configuration.tlsPolicy.name() eq 'disabled' ? 'checked' : ''} onclick="applyDisplayable()"/>
                        <label for="tlspolicy-disabled"><fmt:message key="connection.advanced.settings.starttls.label_disabled"/></label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td>
                        <input type="radio" name="tlspolicy" value="optional" id="tlspolicy-optional" ${configuration.tlsPolicy.name() eq 'optional' ? 'checked' : ''} onclick="applyDisplayable()"/>
                        <label for="tlspolicy-optional"><fmt:message key="connection.advanced.settings.starttls.label_optional"/></label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td>
                        <input type="radio" name="tlspolicy" value="required" id="tlspolicy-required" ${configuration.tlsPolicy.name() eq 'required' ? 'checked' : ''} onclick="applyDisplayable()"/>
                        <label for="tlspolicy-required"><fmt:message key="connection.advanced.settings.starttls.label_required"/></label>
                    </td>
                </tr>
            </table>
        </admin:contentBox>
    </c:if>

    <fmt:message key="connection.advanced.settings.clientauth.boxtitle" var="clientauthboxtitle"/>
    <admin:contentBox title="${clientauthboxtitle}">
        <p><fmt:message key="connection.advanced.settings.clientauth.info"/></p>
        <table cellpadding="3" cellspacing="0" border="0" class="tlsconfig">
            <tr valign="middle">
                <td>
                    <input type="radio" name="mutualauthentication" value="disabled" id="mutualauthentication-disabled" ${configuration.clientAuth.name() eq 'disabled' ? 'checked' : ''}/>
                    <label for="mutualauthentication-disabled"><fmt:message key="connection.advanced.settings.clientauth.label_disabled"/></label>
                </td>
            </tr>
            <tr valign="middle">
                <td>
                    <input type="radio" name="mutualauthentication" value="wanted" id="mutualauthentication-wanted" ${configuration.clientAuth.name() eq 'wanted' ? 'checked' : ''}/>
                    <label for="mutualauthentication-wanted"><fmt:message key="connection.advanced.settings.clientauth.label_wanted"/></label>
                </td>
            </tr>
            <tr valign="middle">
                <td>
                    <input type="radio" name="mutualauthentication" value="needed" id="mutualauthentication-needed" ${configuration.clientAuth.name() eq 'needed' ? 'checked' : ''}/>
                    <label for="mutualauthentication-needed"><fmt:message key="connection.advanced.settings.clientauth.label_needed"/></label>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="connection.advanced.settings.certchain.boxtitle" var="certchainboxtitle"/>
    <admin:contentBox title="${certchainboxtitle}">
        <p><fmt:message key="connection.advanced.settings.certchain.info"/></p>
        <table cellpadding="3" cellspacing="0" border="0" class="tlsconfig">
            <tr valign="middle">
                <td>
                    <input type="checkbox" name="accept-self-signed-certificates" id="accept-self-signed-certificates" ${configuration.acceptSelfSignedCertificates ? 'checked' : ''}/><label for="accept-self-signed-certificates"><fmt:message key="connection.advanced.settings.certchain.label_selfsigned"/></label>
                </td>
            </tr>
            <tr valign="middle">
                <td>
                    <input type="checkbox" name="verify-certificate-validity" id="verify-certificate-validity" ${configuration.verifyCertificateValidity ? 'checked' : ''}/><label for="verify-certificate-validity"><fmt:message key="connection.advanced.settings.certchain.label_validity"/></label>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="connection.advanced.settings.protocols.boxtitle" var="protocolsboxtitle"/>
    <admin:contentBox title="${protocolsboxtitle}">
        <p><fmt:message key="connection.advanced.settings.protocols.info"/></p>
        <table cellpadding="3" cellspacing="0" border="0" class="tlsconfig">
            <c:forEach var="supportedProtocol" items="${supportedProtocols}">
                <c:if test="${supportedProtocol ne 'SSLv2Hello'}">
                    <c:set var="idForForm">protocol-<c:out value="${supportedProtocol}"/></c:set>
                    <c:set var="enabled" value="${configuration.encryptionProtocols.contains(supportedProtocol)}"/>
                    <tr valign="middle">
                        <td>
                            <input type="checkbox" name="${idForForm}" id="${idForForm}" ${enabled ? 'checked' : ''}/><label for="${idForForm}"><c:out value="${supportedProtocol}"/></label>
                        </td>
                    </tr>
                </c:if>
            </c:forEach>
        </table>
        <c:if test="${supportedProtocols.contains( 'SSLv2Hello' )}">
            <br/>
            <c:set var="supportedProtocol" value="SSLv2Hello"/>
            <p><fmt:message key="connection.advanced.settings.protocols.sslv2hello.info"/></p>
            <table cellpadding="3" cellspacing="0" border="0" class="tlsconfig">
                <c:set var="idForForm">protocol-<c:out value="${supportedProtocol}"/></c:set>
                <c:set var="enabled" value="${configuration.encryptionProtocols.contains(supportedProtocol)}"/>
                <tr valign="middle">
                    <td>
                        <input type="checkbox" name="${idForForm}" id="${idForForm}" ${enabled ? 'checked' : ''}/><label for="${idForForm}"><c:out value="${supportedProtocol}"/></label>
                    </td>
                </tr>
            </table>
        </c:if>
    </admin:contentBox>

    <fmt:message key="connection.advanced.settings.ciphersuites.boxtitle" var="ciphersuitesboxtitle"/>
    <admin:contentBox title="${ciphersuitesboxtitle}">
        <p><fmt:message key="connection.advanced.settings.ciphersuites.info"/></p>
        <table cellpadding="3" cellspacing="0" border="0" class="tlsconfig">
            <tr><th><fmt:message key="connection.advanced.settings.ciphersuites.label_enable"/></th><th></th><th><fmt:message key="connection.advanced.settings.ciphersuites.label_supported"/></th></tr>
            <tr>
                <td>
                    <select name="cipherSuitesEnabled" id="cipherSuitesEnabled" size="10" multiple>
                        <c:forEach items="${configuration.encryptionCipherSuites}" var="item">
                            <c:if test="${supportedCipherSuites.contains(item)}">
                                <option><c:out value="${item}"/></option>
                            </c:if>
                        </c:forEach>
                    </select>
                </td>
                <td>
                    <input type="button" onclick="moveSelectedFromTo('cipherSuitesEnabled','cipherSuitesSupported')" value="&gt;&gt;" /><br/>
                    <input type="button" onclick="moveSelectedFromTo('cipherSuitesSupported','cipherSuitesEnabled')" value="&lt;&lt;" />
                </td>
                <td>
                    <select name="cipherSuitesSupported" id="cipherSuitesSupported" size="10" multiple>
                        <c:forEach items="${supportedCipherSuites}" var="item">
                            <c:if test="${not configuration.encryptionCipherSuites.contains(item)}">
                                <option><c:out value="${item}"/></option>
                            </c:if>
                        </c:forEach>
                    </select>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="connection.advanced.settings.misc.boxtitle" var="miscboxtitle"/>
    <admin:contentBox title="${miscboxtitle}">
        <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="middle">
                <td width="1%" nowrap><label for="maxThreads"><fmt:message key="connection.advanced.settings.misc.label_workers"/></label></td>
                <td width="99%"><input type="text" name="maxThreads" id="maxThreads" value="${configuration.maxThreadPoolSize}" readonly/></td>
            </tr>
        </table>
    </admin:contentBox>

    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">

</form>
</body>
</html>
