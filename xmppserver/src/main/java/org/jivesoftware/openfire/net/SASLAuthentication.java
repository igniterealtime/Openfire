/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.net;

import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.keystore.CertificateStoreManager;
import org.jivesoftware.openfire.keystore.TrustStore;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.sasl.AnonymousSaslServer;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.sasl.JiveSharedSecretSaslServer;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.IncomingServerSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SASLAuthentication is responsible for returning the available SASL mechanisms to use and for
 * actually performing the SASL authentication.<p>
 *
 * The list of available SASL mechanisms is determined by:
 * <ol>
 *      <li>The type of {@link org.jivesoftware.openfire.user.UserProvider} being used since
 *      some SASL mechanisms require the server to be able to retrieve user passwords</li>
 *      <li>Whether anonymous logins are enabled or not.</li>
 *      <li>Whether shared secret authentication is enabled or not.</li>
 *      <li>Whether the underlying connection has been secured or not.</li>
 * </ol>
 *
 * @author Hao Chen
 * @author Gaston Dombiak
 */
public class SASLAuthentication {

    private static final Logger Log = LoggerFactory.getLogger(SASLAuthentication.class);

    public static final SystemProperty<Boolean> SKIP_PEER_CERT_REVALIDATION_CLIENT = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.external.client.skip-cert-revalidation")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    // http://stackoverflow.com/questions/8571501/how-to-check-whether-the-string-is-base64-encoded-or-not
    // plus an extra regex alternative to catch a single equals sign ('=', see RFC 6120 6.4.2)
    private static final Pattern BASE64_ENCODED = Pattern.compile("^(=|([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==))$");

    private static final String SASL_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-sasl";

    private static Set<String> mechanisms = new HashSet<>();

    static
    {
        // Add (proprietary) Providers of SASL implementation to the Java security context.
        Security.addProvider( new org.jivesoftware.openfire.sasl.SaslProvider() );

        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("sasl.mechs");
        JiveGlobals.migrateProperty("sasl.gssapi.debug");
        JiveGlobals.migrateProperty("sasl.gssapi.config");
        JiveGlobals.migrateProperty("sasl.gssapi.useSubjectCredsOnly");

        initMechanisms();

        org.jivesoftware.util.PropertyEventDispatcher.addListener( new PropertyEventListener()
        {
            @Override
            public void propertySet( String property, Map<String, Object> params )
            {
                if ("sasl.mechs".equals( property ) )
                {
                    initMechanisms();
                }
            }

            @Override
            public void propertyDeleted( String property, Map<String, Object> params )
            {
                if ("sasl.mechs".equals( property ) )
                {
                    initMechanisms();
                }
            }

            @Override
            public void xmlPropertySet( String property, Map<String, Object> params )
            {}

            @Override
            public void xmlPropertyDeleted( String property, Map<String, Object> params )
            {}
        } );
    }

    public enum ElementType
    {
        ABORT,
        AUTH,
        RESPONSE,
        CHALLENGE,
        FAILURE,
        UNDEF;

        public static ElementType valueOfCaseInsensitive( String name )
        {
            if ( name == null || name.isEmpty() ) {
                return UNDEF;
            }
            try
            {
                return ElementType.valueOf( name.toUpperCase() );
            }
            catch ( Throwable t )
            {
                return UNDEF;
            }
        }
    }

    public enum Status
    {
        /**
         * Entity needs to respond last challenge. Session is still negotiatingSASL authentication.
         */
        needResponse,

        /**
         * SASL negotiation has failed. The entity may retry a few times before the connection is closed.
         */
        failed,

        /**
         * SASL negotiation has been successful.
         */
        authenticated
    }

    /**
     * Returns a string with the valid SASL mechanisms available for the specified session. If
     * the session's connection is not secured then only include the SASL mechanisms that don't
     * require TLS.
     *
     * @param session The current session
     *
     * @return a string with the valid SASL mechanisms available for the specified session.
     */
    public static String getSASLMechanisms( LocalSession session )
    {
        if ( session instanceof ClientSession )
        {
            final Element result = getSASLMechanismsElement( (ClientSession) session );
            return result == null ? "" : result.asXML();
        }
        else if ( session instanceof LocalIncomingServerSession )
        {
            final Element result = getSASLMechanismsElement( (LocalIncomingServerSession) session );
            return result == null ? "" : result.asXML();
        }
        else
        {
            Log.debug( "Unable to determine SASL mechanisms that are applicable to session '{}'. Unrecognized session type.", session );
            return "";
        }
    }

    public static Element getSASLMechanismsElement( ClientSession session )
    {
        final Element result = DocumentHelper.createElement( new QName( "mechanisms", new Namespace( "", SASL_NAMESPACE ) ) );
        for (String mech : getSupportedMechanisms()) {
            if (mech.equals("EXTERNAL")) {
                boolean trustedCert = false;
                if (session.isSecure()) {
                    final Connection connection = ( (LocalClientSession) session ).getConnection();
                    if ( SKIP_PEER_CERT_REVALIDATION_CLIENT.getValue() ) {
                        // Trust that the peer certificate has been validated when TLS got established.
                        trustedCert = connection.getPeerCertificates() != null && connection.getPeerCertificates().length > 0;
                    } else {
                        // Re-evaluate the validity of the peer certificate.
                        final TrustStore trustStore = connection.getConfiguration().getTrustStore();
                        trustedCert = trustStore.isTrusted( connection.getPeerCertificates() );
                    }
                }
                if ( !trustedCert ) {
                    continue; // Do not offer EXTERNAL.
                }
            }
            final Element mechanism = result.addElement("mechanism");
            mechanism.setText(mech);
        }

        // OF-2072: Return null instead of an empty element, if so configured.
        if ( JiveGlobals.getBooleanProperty("sasl.client.suppressEmpty", false) && result.elements().isEmpty() ) {
            return null;
        }

        return result;
    }

    public static Element getSASLMechanismsElement( LocalIncomingServerSession session )
    {
        final Element result = DocumentHelper.createElement( new QName( "mechanisms", new Namespace( "", SASL_NAMESPACE ) ) );
        if (session.isSecure()) {
            final Connection connection   = session.getConnection();
            final TrustStore trustStore   = connection.getConfiguration().getTrustStore();
            final X509Certificate trusted = trustStore.getEndEntityCertificate( session.getConnection().getPeerCertificates() );

            boolean haveTrustedCertificate = trusted != null;
            if (trusted != null && session.getDefaultIdentity() != null) {
                haveTrustedCertificate = verifyCertificate(trusted, session.getDefaultIdentity());
            }
            if (haveTrustedCertificate) {
                // Offer SASL EXTERNAL only if TLS has already been negotiated and the peer has a trusted cert.
                final Element mechanism = result.addElement("mechanism");
                mechanism.setText("EXTERNAL");
            }
        }

        // OF-2072: Return null instead of an empty element, if so configured.
        if ( JiveGlobals.getBooleanProperty("sasl.server.suppressEmpty", false) && result.elements().isEmpty() ) {
            return null;
        }
        return result;
    }

    /**
     * Handles the SASL authentication packet. The entity may be sending an initial
     * authentication request or a response to a challenge made by the server. The returned
     * value indicates whether the authentication has finished either successfully or not or
     * if the entity is expected to send a response to a challenge.
     *
     * @param session the session that is authenticating with the server.
     * @param doc the stanza sent by the authenticating entity.
     * @return value that indicates whether the authentication has finished either successfully
     *         or not or if the entity is expected to send a response to a challenge.
     */
    public static Status handle(LocalSession session, Element doc)
    {
        try
        {
            if ( !doc.getNamespaceURI().equals( SASL_NAMESPACE ) )
            {
                throw new IllegalStateException( "Unexpected data received while negotiating SASL authentication. Name of the offending root element: " + doc.getName() + " Namespace: " + doc.getNamespaceURI() );
            }

            switch ( ElementType.valueOfCaseInsensitive( doc.getName() ) )
            {
                case ABORT:
                    throw new SaslFailureException( Failure.ABORTED );

                case AUTH:
                    if ( doc.attributeValue( "mechanism" ) == null )
                    {
                        throw new SaslFailureException( Failure.INVALID_MECHANISM, "Peer did not specify a mechanism." );
                    }

                    final String mechanismName = doc.attributeValue( "mechanism" ).toUpperCase();

                    // See if the mechanism is supported by configuration as well as by implementation.
                    if ( !mechanisms.contains( mechanismName ) )
                    {
                        throw new SaslFailureException( Failure.INVALID_MECHANISM, "The configuration of Openfire does not contain or allow the mechanism." );
                    }

                    // OF-477: The SASL implementation requires the fully qualified host name (not the domain name!) of this server,
                    // yet, most of the XMPP implemenations of DIGEST-MD5 will actually use the domain name. To account for that,
                    // here, we'll use the host name, unless DIGEST-MD5 is being negotiated!
                    final XMPPServerInfo serverInfo = XMPPServer.getInstance().getServerInfo();
                    final String serverName = ( mechanismName.equals( "DIGEST-MD5" ) ? serverInfo.getXMPPDomain() : serverInfo.getHostname() );

                    // Construct the configuration properties
                    final Map<String, Object> props = new HashMap<>();
                    props.put( LocalSession.class.getCanonicalName(), session );
                    props.put(Sasl.POLICY_NOANONYMOUS, Boolean.toString(!AnonymousSaslServer.ENABLED.getValue()));
                    props.put( "com.sun.security.sasl.digest.realm", serverInfo.getXMPPDomain() );

                    SaslServer saslServer = Sasl.createSaslServer( mechanismName, "xmpp", serverName, props, new XMPPCallbackHandler() );
                    if ( saslServer == null )
                    {
                        throw new SaslFailureException( Failure.INVALID_MECHANISM, "There is no provider that can provide a SASL server for the desired mechanism and properties." );
                    }

                    session.setSessionData( "SaslServer", saslServer );

                    if ( mechanismName.equals( "DIGEST-MD5" ) )
                    {
                        // RFC2831 (DIGEST-MD5) says the client MAY provide data in the initial response. Java SASL does
                        // not (currently) support this and throws an exception. For XMPP, such data violates
                        // the RFC, so we just strip any initial token.
                        doc.setText( "" );
                    }

                    // intended fall-through
                case RESPONSE:

                    saslServer = (SaslServer) session.getSessionData( "SaslServer" );

                    if ( saslServer == null )
                    {
                        // Client sends response without a preceding auth?
                        throw new IllegalStateException( "A SaslServer instance was not initialized and/or stored on the session." );
                    }

                    // Decode any data that is provided in the client response.
                    final String encoded = doc.getTextTrim();
                    final byte[] decoded;
                    if ( encoded == null || encoded.isEmpty() || encoded.equals("=") ) // java SaslServer cannot handle a null.
                    {
                        decoded = new byte[ 0 ];
                    }
                    else
                    {
                        // TODO: We shouldn't depend on regex-based validation. Instead, use a proper decoder implementation and handle any exceptions that it throws.
                        if ( !BASE64_ENCODED.matcher( encoded ).matches() )
                        {
                            throw new SaslFailureException( Failure.INCORRECT_ENCODING );
                        }

                        decoded = StringUtils.decodeBase64( encoded );
                    }

                    // Process client response.
                    final byte[] challenge = saslServer.evaluateResponse( decoded ); // Either a challenge or success data.

                    if ( !saslServer.isComplete() )
                    {
                        // Not complete: client is challenged for additional steps.
                        sendChallenge( session, challenge );
                        return Status.needResponse;
                    }

                    // Success!
                    if ( session instanceof IncomingServerSession )
                    {
                        // Flag that indicates if certificates of the remote server should be validated.
                        final boolean verify = JiveGlobals.getBooleanProperty( ConnectionSettings.Server.TLS_CERTIFICATE_VERIFY, true );
                        if ( verify )
                        {
                            if ( verifyCertificates( session.getConnection().getPeerCertificates(), saslServer.getAuthorizationID(), true ) )
                            {
                                ( (LocalIncomingServerSession) session ).tlsAuth();
                            }
                            else
                            {
                                throw new SaslFailureException( Failure.NOT_AUTHORIZED, "Server-to-Server certificate verification failed." );
                            }
                        }
                    }

                    authenticationSuccessful( session, saslServer.getAuthorizationID(), challenge );
                    session.removeSessionData( "SaslServer" );
                    return Status.authenticated;

                default:
                    throw new IllegalStateException( "Unexpected data received while negotiating SASL authentication. Name of the offending root element: " + doc.getName() + " Namespace: " + doc.getNamespaceURI() );
            }
        }
        catch ( SaslException ex )
        {
            Log.debug( "SASL negotiation failed for session: {}", session, ex );
            final Failure failure;
            if ( ex instanceof SaslFailureException && ((SaslFailureException) ex).getFailure() != null )
            {
                failure = ((SaslFailureException) ex).getFailure();
            }
            else
            {
                failure = Failure.NOT_AUTHORIZED;
            }
            authenticationFailed( session, failure );
            session.removeSessionData( "SaslServer" );
            return Status.failed;
        }
        catch( Exception ex )
        {
            Log.warn( "An unexpected exception occurred during SASL negotiation. Affected session: {}", session, ex );
            authenticationFailed( session, Failure.NOT_AUTHORIZED );
            session.removeSessionData( "SaslServer" );
            return Status.failed;
        }
    }

    public static boolean verifyCertificate(X509Certificate trustedCert, String hostname) {
        for (String identity : CertificateManager.getServerIdentities(trustedCert)) {
            // Verify that either the identity is the same as the hostname, or for wildcarded
            // identities that the hostname ends with .domainspecified or -is- domainspecified.
            if ((identity.startsWith("*.")
                 && (hostname.endsWith(identity.replace("*.", "."))
                     || hostname.equals(identity.replace("*.", ""))))
                    || hostname.equals(identity)) {
                return true;
            }
        }
        return false;
    }

    public static boolean verifyCertificates(Certificate[] chain, String hostname, boolean isS2S) {
        final CertificateStoreManager certificateStoreManager = XMPPServer.getInstance().getCertificateStoreManager();
        final ConnectionType connectionType = isS2S ? ConnectionType.SOCKET_S2S : ConnectionType.SOCKET_C2S;
        final TrustStore trustStore = certificateStoreManager.getTrustStore( connectionType );
        final X509Certificate trusted = trustStore.getEndEntityCertificate( chain );
        if (trusted != null) {
            return verifyCertificate(trusted, hostname);
        }
        return false;
    }

    private static void sendElement(Session session, String element, byte[] data) {
        StringBuilder reply = new StringBuilder(250);
        reply.append("<");
        reply.append(element);
        reply.append(" xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"");
        if (data != null) {
            reply.append(">");
            String data_b64 = StringUtils.encodeBase64(data).trim();
            if ("".equals(data_b64)) {
                data_b64 = "=";
            }
            reply.append(data_b64);
            reply.append("</");
            reply.append(element);
            reply.append(">");
        } else {
            reply.append("/>");
        }
        session.deliverRawText(reply.toString());
    }

    private static void sendChallenge(Session session, byte[] challenge) {
        sendElement(session, "challenge", challenge);
    }

    private static void authenticationSuccessful(LocalSession session, String username,
            byte[] successData) {
        if (username != null && LockOutManager.getInstance().isAccountDisabled(username)) {
            // Interception!  This person is locked out, fail instead!
            LockOutManager.getInstance().recordFailedLogin(username);
            authenticationFailed(session, Failure.ACCOUNT_DISABLED);
            return;
        }
        sendElement(session, "success", successData);
        // We only support SASL for c2s
        if (session instanceof ClientSession) {
            final AuthToken authToken;
            if (username == null) {
                // AuthzId is null, which indicates that authentication was anonymous.
                authToken = AuthToken.generateAnonymousToken();
            } else {
                authToken = AuthToken.generateUserToken(username);
            }
            ((LocalClientSession) session).setAuthToken(authToken);
        }
        else if (session instanceof IncomingServerSession) {
            String hostname = username;
            // Add the validated domain as a valid domain. The remote server can
            // now send packets from this address
            ((LocalIncomingServerSession) session).addValidatedDomain(hostname);
            Log.info("Inbound Server {} authenticated (via TLS)", username);
        }
    }

    private static void authenticationFailed(LocalSession session, Failure failure) {
        StringBuilder reply = new StringBuilder(80);
        reply.append("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"><");
        reply.append(failure.toString());
        reply.append("/></failure>");
        session.deliverRawText(reply.toString());
        // Give a number of retries before closing the connection
        Integer retries = (Integer) session.getSessionData("authRetries");
        if (retries == null) {
            retries = 1;
        }
        else {
            retries = retries + 1;
        }
        session.setSessionData("authRetries", retries);
        if (retries >= JiveGlobals.getIntProperty("xmpp.auth.retries", 3) ) {
            // Close the connection
            Log.debug( "Closing session that failed to authenticate {} times: {}", retries, session );
            session.close();
        }
    }

    /**
     * Adds a new SASL mechanism to the list of supported SASL mechanisms by the server. The
     * new mechanism will be offered to clients and connection managers as stream features.<p>
     *
     * Note: this method simply registers the SASL mechanism to be advertised as a supported
     * mechanism by Openfire. Actual SASL handling is done by Java itself, so you must add
     * the provider to Java.
     *
     * @param mechanismName the name of the new SASL mechanism (cannot be null or an empty String).
     */
    public static void addSupportedMechanism(String mechanismName) {
        if ( mechanismName == null || mechanismName.isEmpty() ) {
            throw new IllegalArgumentException( "Argument 'mechanism' must cannot be null or an empty string." );
        }
        mechanisms.add( mechanismName.toUpperCase() );
        Log.info( "Support added for the '{}' SASL mechanism.", mechanismName.toUpperCase() );
    }

    /**
     * Removes a SASL mechanism from the list of supported SASL mechanisms by the server.
     *
     * @param mechanismName the name of the SASL mechanism to remove (cannot be null or empty, not case sensitive).
     */
    public static void removeSupportedMechanism(String mechanismName) {
        if ( mechanismName == null || mechanismName.isEmpty() ) {
            throw new IllegalArgumentException( "Argument 'mechanism' must cannot be null or an empty string." );
        }

        if ( mechanisms.remove( mechanismName.toUpperCase() ) )
        {
            Log.info( "Support removed for the '{}' SASL mechanism.", mechanismName.toUpperCase() );
        }
    }

    /**
     * Returns the list of supported SASL mechanisms by the server. Note that Java may have
     * support for more mechanisms but some of them may not be returned since a special setup
     * is required that might be missing. Use {@link #addSupportedMechanism(String)} to add
     * new SASL mechanisms.
     *
     * @return the list of supported SASL mechanisms by the server.
     */
    public static Set<String> getSupportedMechanisms()
    {
        // List all mechanism names for which there's an implementation.
        final Set<String> implementedMechanisms = getImplementedMechanisms();

        // Start off with all mechanisms that we intend to support.
        final Set<String> answer = new HashSet<>( mechanisms );

        // Clean up not-available mechanisms.
        for ( final Iterator<String> it = answer.iterator(); it.hasNext(); )
        {
            final String mechanism = it.next();

            if ( !implementedMechanisms.contains( mechanism ) )
            {
                Log.trace( "Cannot support '{}' as there's no implementation available.", mechanism );
                it.remove();
                continue;
            }

            switch ( mechanism )
            {
                case "CRAM-MD5": // intended fall-through
                case "DIGEST-MD5":
                    // Check if the user provider in use supports passwords retrieval. Access to the users passwords will be required by the CallbackHandler.
                    if ( !AuthFactory.supportsPasswordRetrieval() )
                    {
                        Log.trace( "Cannot support '{}' as the AuthFactory that's in use does not support password retrieval.", mechanism );
                        it.remove();
                    }
                    break;

                case "SCRAM-SHA-1":
                    if ( !AuthFactory.supportsScram() )
                    {
                        Log.trace( "Cannot support '{}' as the AuthFactory that's in use does not support SCRAM.", mechanism );
                        it.remove();
                    }
                    break;

                case "ANONYMOUS":
                    if (!AnonymousSaslServer.ENABLED.getValue()) {
                        Log.trace( "Cannot support '{}' as it has been disabled by configuration.", mechanism );
                        it.remove();
                    }
                    break;

                case "JIVE-SHAREDSECRET":
                    if ( !JiveSharedSecretSaslServer.isSharedSecretAllowed() )
                    {
                        Log.trace( "Cannot support '{}' as it has been disabled by configuration.", mechanism );
                        it.remove();
                    }
                    break;

                case "GSSAPI":
                    final String gssapiConfig = JiveGlobals.getProperty( "sasl.gssapi.config" );
                    if ( gssapiConfig != null )
                    {
                        System.setProperty( "java.security.krb5.debug", JiveGlobals.getProperty( "sasl.gssapi.debug", "false" ) );
                        System.setProperty( "java.security.auth.login.config", gssapiConfig );
                        System.setProperty( "javax.security.auth.useSubjectCredsOnly", JiveGlobals.getProperty( "sasl.gssapi.useSubjectCredsOnly", "false" ) );
                    }
                    else
                    {
                        Log.trace( "Cannot support '{}' as the 'sasl.gssapi.config' property has not been defined.", mechanism );
                        it.remove();
                    }
                    break;
            }
        }
        return answer;
    }

    /**
     * Returns a collection of mechanism names for which the JVM has an implementation available.
     * <p>
     * Note that this need not (and likely will not) correspond with the list of mechanisms that is offered to XMPP
     * peer entities, which is provided by #getSupportedMechanisms.
     *
     * @return a collection of SASL mechanism names (never null, possibly empty)
     */
    public static Set<String> getImplementedMechanisms()
    {
        final Set<String> result = new HashSet<>();
        final Enumeration<SaslServerFactory> saslServerFactories = Sasl.getSaslServerFactories();
        while ( saslServerFactories.hasMoreElements() )
        {
            final SaslServerFactory saslServerFactory = saslServerFactories.nextElement();
            Collections.addAll( result, saslServerFactory.getMechanismNames( null ) );
        }
        return result;
    }

    /**
     * Returns a collection of SASL mechanism names that forms the source pool from which the mechanisms that are
     * eventually being offered to peers are obtained.
     **
     * When a mechanism is not returned by this method, it will never be offered, but when a mechanism is returned
     * by this method, there is no guarantee that it will be offered.
     *
     * Apart from being returned in this method, an implementation must be available (see {@link #getImplementedMechanisms()}
     * and configuration or other characteristics of this server must not prevent a particular mechanism from being
     * used (see @{link {@link #getSupportedMechanisms()}}.
     *
     * @return A collection of mechanisms that are considered for use in this instance of Openfire.
     */
    public static List<String> getEnabledMechanisms()
    {
        return JiveGlobals.getListProperty("sasl.mechs", Arrays.asList( "ANONYMOUS","PLAIN","DIGEST-MD5","CRAM-MD5","SCRAM-SHA-1","JIVE-SHAREDSECRET","GSSAPI","EXTERNAL" ) );
    }

    /**
     * Sets the collection of mechanism names that the system administrator allows to be used.
     *
     * @param mechanisms A collection of mechanisms that are considered for use in this instance of Openfire. Null to reset the default setting.
     * @see #getEnabledMechanisms()
     */
    public static void setEnabledMechanisms( List<String> mechanisms )
    {
        JiveGlobals.setProperty( "sasl.mechs", mechanisms );
        initMechanisms();
    }

    private static void initMechanisms()
    {
        final List<String> propertyValues = getEnabledMechanisms();
        mechanisms = new HashSet<>();
        for ( final String propertyValue : propertyValues )
        {
            try
            {
                addSupportedMechanism( propertyValue );
            }
            catch ( Exception ex )
            {
                Log.warn( "An exception occurred while trying to add support for SASL Mechanism '{}':", propertyValue, ex );
            }
        }
    }
}
