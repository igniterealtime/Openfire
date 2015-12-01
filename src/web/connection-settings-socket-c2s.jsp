<%@ page import="org.jivesoftware.openfire.spi.ConnectionConfiguration" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionManagerImpl" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionListener" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.Connection" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.openfire.session.ConnectionSettings" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page errorPage="error.jsp" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%
    final ConnectionType connectionType = ConnectionType.SOCKET_C2S;
    final ConnectionManagerImpl manager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();

    final ConnectionConfiguration plaintextConfiguration  = manager.getListener( connectionType, false ).generateConnectionConfiguration();
    final ConnectionConfiguration legacymodeConfiguration = manager.getListener( connectionType, true  ).generateConnectionConfiguration();

    final boolean update = request.getParameter( "update" ) != null;
    final Map<String, String> errors = new HashMap<>();

    if ( update && errors.isEmpty() )
    {
        // plaintext
        final boolean plaintextEnabled      = ParamUtils.getBooleanParameter( request, "plaintext-enabled" );
        final int plaintextTcpPort          = ParamUtils.getIntParameter( request, "plaintext-tcpPort", plaintextConfiguration.getPort() );
        final int plaintextReadBuffer       = ParamUtils.getIntParameter( request, "plaintext-readBuffer", plaintextConfiguration.getMaxBufferSize() );
        final String plaintextTlsPolicyText = ParamUtils.getParameter( request, "plaintext-tlspolicy", true );
        final Connection.TLSPolicy plaintextTlsPolicy;
        if ( plaintextTlsPolicyText == null || plaintextTlsPolicyText.isEmpty() ) {
            plaintextTlsPolicy = plaintextConfiguration.getTlsPolicy();
        } else {
            plaintextTlsPolicy = Connection.TLSPolicy.valueOf( plaintextTlsPolicyText );
        }
        final String plaintextMutualAuthenticationText = ParamUtils.getParameter( request, "plaintext-mutualauthentication", true );
        final Connection.ClientAuth plaintextMutualAuthentication;
        if ( plaintextMutualAuthenticationText == null || plaintextMutualAuthenticationText.isEmpty() ) {
            plaintextMutualAuthentication = plaintextConfiguration.getClientAuth();
        } else {
            plaintextMutualAuthentication = Connection.ClientAuth.valueOf( plaintextMutualAuthenticationText );
        }
        final int plaintextListenerMaxThreads = ParamUtils.getIntParameter( request, "plaintext-maxThreads", plaintextConfiguration.getMaxThreadPoolSize() );
        final boolean plaintextAcceptSelfSignedCertificates = ParamUtils.getBooleanParameter( request, "plaintext-accept-self-signed-certificates" );
        final boolean plaintextVerifyCertificateValidity = ParamUtils.getBooleanParameter( request, "plaintext-verify-certificate-validity" );

        // legacymode
        final boolean legacymodeEnabled      = ParamUtils.getBooleanParameter( request, "legacymode-enabled" );
        final int legacymodeTcpPort          = ParamUtils.getIntParameter( request, "legacymode-tcpPort", legacymodeConfiguration.getPort() );
        final int legacymodeReadBuffer       = ParamUtils.getIntParameter( request, "legacymode-readBuffer", legacymodeConfiguration.getMaxBufferSize() );
        final String legacymodeMutualAuthenticationText = ParamUtils.getParameter( request, "legacymode-mutualauthentication", true );
        final Connection.ClientAuth legacymodeMutualAuthentication;
        if ( legacymodeMutualAuthenticationText == null || legacymodeMutualAuthenticationText.isEmpty() ) {
            legacymodeMutualAuthentication = legacymodeConfiguration.getClientAuth();
        } else {
            legacymodeMutualAuthentication = Connection.ClientAuth.valueOf( legacymodeMutualAuthenticationText );
        }
        final int legacymodeListenerMaxThreads = ParamUtils.getIntParameter( request, "legacymode-maxThreads", legacymodeConfiguration.getMaxThreadPoolSize() );
        final boolean legacymodeAcceptSelfSignedCertificates = ParamUtils.getBooleanParameter( request, "legacymode-accept-self-signed-certificates" );
        final boolean legacymodeVerifyCertificateValidity = ParamUtils.getBooleanParameter( request, "legacymode-verify-certificate-validity" );

        // Apply
        final ConnectionListener plaintextListener  = manager.getListener( connectionType, false );
        final ConnectionListener legacymodeListener = manager.getListener( connectionType, true  );

        plaintextListener.enable( plaintextEnabled );
        plaintextListener.setPort( plaintextTcpPort );
        // TODO: plaintextListener.setMaxBufferSize( plaintextReadBuffer );
        plaintextListener.setTLSPolicy( plaintextTlsPolicy );
        plaintextListener.setClientAuth( plaintextMutualAuthentication );
        // TODO: plaintextListener.setMaxThreadPoolSize( plaintextListenerMaxThreads);
        plaintextListener.setAcceptSelfSignedCertificates( plaintextAcceptSelfSignedCertificates );
        plaintextListener.setVerifyCertificateValidity( plaintextVerifyCertificateValidity );

        legacymodeListener.enable( legacymodeEnabled );
        legacymodeListener.setPort( legacymodeTcpPort );
        // TODO: legacymodeListener.setMaxBufferSize( legacymodeReadBuffer );
        legacymodeListener.setClientAuth( legacymodeMutualAuthentication );
        // TODO:  legacymodeListener.setMaxThreadPoolSize( legacymodeListenerMaxThreads);
        legacymodeListener.setAcceptSelfSignedCertificates( legacymodeAcceptSelfSignedCertificates );
        legacymodeListener.setVerifyCertificateValidity( legacymodeVerifyCertificateValidity );

        // Log the event
        webManager.logEvent( "Updated connection settings for " + connectionType, "Applied configuration to plain-text as well as legacy-mode connection listeners." );
        response.sendRedirect( "connection-settings-socket-c2s.jsp?success=true" );


        // TODO below is the 'idle connection' handing. This should go into the connection configuration, like all other configuration.
        final int clientIdle = 1000* ParamUtils.getIntParameter(request, "clientIdle", -1);
        final boolean idleDisco = ParamUtils.getBooleanParameter(request, "idleDisco");
        final boolean pingIdleClients = ParamUtils.getBooleanParameter(request, "pingIdleClients");

        if (!idleDisco) {
            JiveGlobals.setProperty( ConnectionSettings.Client.IDLE_TIMEOUT, "-1" );
        } else {
            JiveGlobals.setProperty( ConnectionSettings.Client.IDLE_TIMEOUT, String.valueOf( clientIdle ) );
        }
        JiveGlobals.setProperty( ConnectionSettings.Client.KEEP_ALIVE_PING, String.valueOf( pingIdleClients ) );

        webManager.logEvent("set server property " + ConnectionSettings.Client.IDLE_TIMEOUT, ConnectionSettings.Client.IDLE_TIMEOUT + " = " + clientIdle);
        webManager.logEvent("set server property " + ConnectionSettings.Client.KEEP_ALIVE_PING, ConnectionSettings.Client.KEEP_ALIVE_PING + " = " + pingIdleClients);

        return;
    }

    pageContext.setAttribute( "errors",                  errors );
    pageContext.setAttribute( "plaintextConfiguration",  plaintextConfiguration );
    pageContext.setAttribute( "legacymodeConfiguration", legacymodeConfiguration );
    pageContext.setAttribute( "clientIdle",              JiveGlobals.getIntProperty(     ConnectionSettings.Client.IDLE_TIMEOUT,    6*60*1000 ) );
    pageContext.setAttribute( "pingIdleClients",         JiveGlobals.getBooleanProperty( ConnectionSettings.Client.KEEP_ALIVE_PING, true) );


%>
<html>
<head>
    <title><fmt:message key="client.connections.settings.title"/></title>
    <meta name="pageID" content="client-connections-settings"/>
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

<c:if test="${param.success and empty errors}">
    <admin:infoBox type="success"><fmt:message key="client.connections.settings.confirm.updated" /></admin:infoBox>
</c:if>

<p>
    <fmt:message key="client.connections.settings.info">
        <fmt:param value="<a href=\"session-summary.jsp\">" />
        <fmt:param value="</a>" />
    </fmt:message>
</p>

<form action="connection-settings-socket-c2s.jsp" method="post">

    <admin:contentBox title="Plain-text (with STARTTLS) connections">

        <p>Openfire can accept plain-text connections, which, depending on the policy that is configured here, can be upgraded to encrypted connections (using the STARTTLS protocol).</p>

        <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="middle">
                <td>
                    <input type="checkbox" name="plaintext-enabled" id="plaintext-enabled" onclick="applyDisplayable('plaintext')" ${plaintextConfiguration.enabled ? 'checked' : ''}/><label for="plaintext-enabled">Enabled</label>
                </td>
            </tr>
        </table>

        <div id="plaintext-config">

            <br/>

            <h4>TCP settings</h4>
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td width="1%" nowrap><label for="plaintext-tcpPort">Port number</label></td>
                    <td width="99%"><input type="text" name="plaintext-tcpPort" id="plaintext-tcpPort" value="${plaintextConfiguration.port}"/></td>
                </tr>
                <tr valign="middle">
                    <td width="1%" nowrap><label for="plaintext-readBuffer">Read buffer</label></td>
                    <td width="99%"><input type="text" name="plaintext-readBuffer" id="plaintext-readBuffer" value="${plaintextConfiguration.maxBufferSize}" readonly/> (in bytes)</td>
                </tr>
            </table>

            <br/>

            <h4>STARTTLS policy</h4>
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td>
                        <input type="radio" name="plaintext-tlspolicy" value="disabled" id="plaintext-tlspolicy-disabled" ${plaintextConfiguration.tlsPolicy.name() eq 'disabled' ? 'checked' : ''}/>
                        <label for="plaintext-tlspolicy-disabled"><b>Disabled</b> - Encryption is not allowed.</label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td>
                        <input type="radio" name="plaintext-tlspolicy" value="optional" id="plaintext-tlspolicy-optional" ${plaintextConfiguration.tlsPolicy.name() eq 'optional' ? 'checked' : ''}/>
                        <label for="plaintext-tlspolicy-optional"><b>Optional</b> - Encryption may be used, but is not required.</label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td>
                        <input type="radio" name="plaintext-tlspolicy" value="required" id="plaintext-tlspolicy-required" ${plaintextConfiguration.tlsPolicy.name() eq 'required' ? 'checked' : ''}/>
                        <label for="plaintext-tlspolicy-required"><b>Required</b> - Connections cannot be established unless they are encrypted.</label>
                    </td>
                </tr>
            </table>

            <br/>

            <h4>Mutual Authentication</h4>
            <p>In addition to requiring peers to use encryption (which will force them to verify the security certificates of this Openfire instance) an additional level of security can be enabled. With this option, the server can be configured to verify certificates that are to be provided by the peers. This is commonly referred to as 'mutual authentication'.</p>
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td>
                        <input type="radio" name="plaintext-mutualauthentication" value="disabled" id="plaintext-mutualauthentication-disabled" ${plaintextConfiguration.clientAuth.name() eq 'disabled' ? 'checked' : ''}/>
                        <label for="plaintext-mutualauthentication-disabled"><b>Disabled</b> - Peer certificates are not verified.</label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td>
                        <input type="radio" name="plaintext-mutualauthentication" value="wanted" id="plaintext-mutualauthentication-wanted" ${plaintextConfiguration.clientAuth.name() eq 'wanted' ? 'checked' : ''}/>
                        <label for="plaintext-mutualauthentication-wanted"><b>Wanted</b> - Peer certificates are verified, but only when they are presented by the peer.</label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td>
                        <input type="radio" name="plaintext-mutualauthentication" value="needed" id="plaintext-mutualauthentication-needed" ${plaintextConfiguration.clientAuth.name() eq 'needed' ? 'checked' : ''}/>
                        <label for="plaintext-mutualauthentication-needed"><b>Needed</b> - A connection cannot be established if the peer does not present a valid certificate.</label>
                    </td>
                </tr>
            </table>

            <br/>

            <h4>Certificate chain checking</h4>
            <p>These options configure some aspects of the verification/validation of the certificates that are presented by peers while setting up encrypted connections.</p>
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td>
                        <input type="checkbox" name="plaintext-accept-self-signed-certificates" id="plaintext-accept-self-signed-certificates" ${plaintextConfiguration.acceptSelfSignedCertificates ? 'checked' : ''}/><label for="plaintext-accept-self-signed-certificates">Allow peer certificates to be self-signed.</label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td>
                        <input type="checkbox" name="plaintext-verify-certificate-validity" id="plaintext-verify-certificate-validity" ${plaintextConfiguration.verifyCertificateValidity ? 'checked' : ''}/><label for="plaintext-verify-certificate-validity">Verify that the certificate is currently valid (based on the 'notBefore' and 'notAfter' values of the certificate).</label>
                    </td>
                </tr>
            </table>

            <br/>

            <h4>Miscellaneous settings</h4>
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td width="1%" nowrap><label for="plaintext-maxThreads">Maximum worker threads</label></td>
                    <td width="99%"><input type="text" name="plaintext-maxThreads" id="plaintext-maxThreads" value="${plaintextConfiguration.maxThreadPoolSize}" readonly/></td>
                </tr>
            </table>

        </div>

    </admin:contentBox>

    <admin:contentBox title="Encrypted (legacy-mode) connections">

        <p>Connections of this type are established using encryption immediately (as opposed to using STARTTLS). This type of connectivity is commonly referred to as the "legacy" method of establishing encrypted communications.</p>

        <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="middle">
                <td><input type="checkbox" name="legacymode-enabled" id="legacymode-enabled" onclick="applyDisplayable('legacymode')" ${legacymodeConfiguration.enabled ? 'checked' : ''}/><label for="legacymode-enabled">Enabled</label></td>
            </tr>
        </table>

        <div id="legacymode-config">

            <br/>

            <h4>TCP settings</h4>
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td width="1%" nowrap><label for="legacymode-tcpPort">Port number</label></td>
                    <td width="99%"><input type="text" name="legacymode-tcpPort" id="legacymode-tcpPort" value="${legacymodeConfiguration.port}"></td>
                </tr>
                <tr valign="middle">
                    <td width="1%" nowrap><label for="legacymode-readBuffer">Read buffer</label></td>
                    <td width="99%"><input type="text" name="legacymode-readBuffer" id="legacymode-readBuffer" value="${legacymodeConfiguration.maxBufferSize}" readonly/> (in bytes)</td>
                </tr>
            </table>

            <br/>

            <h4>Mutual Authentication</h4>
            <p>In addition to requiring peers to use encryption (which will force them to verify the security certificates of this Openfire instance) an additional level of security can be enabled. With this option, the server can be configured to verify certificates that are to be provided by the peers. This is commonly referred to as 'mutual authentication'.</p>
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td>
                        <input type="radio" name="legacymode-mutualauthentication" value="disabled" id="legacymode-mutualauthentication-disabled" ${legacymodeConfiguration.clientAuth.name() eq 'disabled' ? 'checked' : ''}/>
                        <label for="legacymode-mutualauthentication-disabled"><b>Disabled</b> - Peer certificates are not verified.</label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td>
                        <input type="radio" name="legacymode-mutualauthentication" value="wanted" id="legacymode-mutualauthentication-wanted" ${legacymodeConfiguration.clientAuth.name() eq 'optional' ? 'checked' : ''}/>
                        <label for="legacymode-mutualauthentication-wanted"><b>Wanted</b> - Peer certificates are verified, but only when they are presented by the peer.</label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td>
                        <input type="radio" name="legacymode-mutualauthentication" value="needed" id="legacymode-mutualauthentication-needed" ${legacymodeConfiguration.clientAuth.name() eq 'required' ? 'checked' : ''}/>
                        <label for="legacymode-mutualauthentication-needed"><b>Needed</b> - A connection cannot be established if the peer does not present a valid certificate.</label>
                    </td>
                </tr>
            </table>

            <br/>

            <h4>Certificate chain checking</h4>
            <p>These options configure some aspects of the verification/validation of the certificates that are presented by peers while setting up encrypted connections.</p>
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td>
                        <input type="checkbox" name="legacymode-accept-self-signed-certificates" id="legacymode-accept-self-signed-certificates" ${legacymodeConfiguration.acceptSelfSignedCertificates ? 'checked' : ''}/><label for="legacymode-accept-self-signed-certificates">Allow peer certificates to be self-signed.</label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td>
                        <input type="checkbox" name="legacymode-verify-certificate-validity" id="legacymode-verify-certificate-validity" ${legacymodeConfiguration.verifyCertificateValidity ? 'checked' : ''}/><label for="legacymode-verify-certificate-validity">Verify that the certificate is currently valid (based on the 'notBefore' and 'notAfter' values of the certificate).</label>
                    </td>
                </tr>
            </table>

            <br/>

            <h4>Miscellaneous settings</h4>
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td width="1%" nowrap><label for="legacymode-maxThreads">Maximum worker threads</label></td>
                    <td width="99%"><input type="text" name="legacymode-maxThreads" id="legacymode-maxThreads" value="${legacymodeConfiguration.maxThreadPoolSize}" readonly/></td>
                </tr>
            </table>

        </div>

    </admin:contentBox>

    <!-- BEGIN 'Idle Connection Policy' -->
    <c:set var="idleTitle">
        <fmt:message key="client.connections.settings.idle.title" />
    </c:set>
    <admin:contentBox title="${idleTitle}">
        <p><fmt:message key="client.connections.settings.idle.info" /></p>
        <table cellpadding="3" cellspacing="0" border="0" width="100%">
            <tbody>
            <tr valign="top">
                <td width="1%" nowrap class="c1">
                    <input type="radio" name="idleDisco" value="false" ${clientIdle le 0 ? 'checked' : ''} id="IDL01">
                </td>
                <td width="99%"><label for="IDL01"><fmt:message key="client.connections.settings.idle.disable" /></label></td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap class="c1">
                    <input type="radio" name="idleDisco" value="true" ${clientIdle gt 0 ? 'checked' : ''} id="IDL02">
                </td>
                <td width="99%">
                    <label for="IDL02"><fmt:message key="client.connections.settings.idle.enable" /></label>
                    <br />
                    <c:if test="${clientIdle gt 0}">
                        <c:set var="seconds">
                            <fmt:parseNumber integerOnly="true">${clientIdle div 1000}</fmt:parseNumber>
                        </c:set>
                    </c:if>
                    <input type="text" name="clientIdle" value="${clientIdle gt 0 ? seconds : ''}" size="5" maxlength="5">&nbsp;<fmt:message key="global.seconds" />
                    <c:if test="${not empty errors['clientIdle']}">
                        <br/>
                        <span class="jive-error-text">
                            <fmt:message key="client.connections.settings.idle.valid_timeout" />.
                        </span>
                    </c:if>
                </td>
            </tr>
            <tr><td colspan="2">&nbsp;</td></tr>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <p><fmt:message key="client.connections.settings.ping.info" />
                        <fmt:message key="client.connections.settings.ping.footnote" /></p>
                    <table cellpadding="3" cellspacing="0" border="0" width="100%">
                        <tbody>
                        <tr valign="top">
                            <td width="1%" nowrap class="c1">
                                <input type="radio" name="pingIdleClients" value="true" ${pingIdleClients ? 'checked' : ''} id="PNG01">
                            </td>
                            <td width="99%"><label for="PNG01"><fmt:message key="client.connections.settings.ping.enable" /></label></td>
                        </tr>
                        <tr valign="top">
                            <td width="1%" nowrap class="c1">
                                <input type="radio" name="pingIdleClients" value="false" ${pingIdleClients ? '' : 'checked'} id="PNG02">
                            </td>
                            <td width="99%"><label for="PNG02"><fmt:message key="client.connections.settings.ping.disable" /></label></td>
                        </tr>
                        </tbody>
                    </table>
                </td>
            </tr>
            </tbody>
        </table>
    </admin:contentBox>

    <!-- END 'Idle Connection Policy' -->

    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
</body>
</html>