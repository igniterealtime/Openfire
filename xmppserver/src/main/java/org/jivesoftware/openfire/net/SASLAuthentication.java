/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2026 Ignite Realtime Foundation. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
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
import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Base64;
import java.util.LinkedList;
import java.util.regex.Pattern;

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

    // TODO how is this different from a singular entry in APPROVED_REALMS? Should these two properties be folded into eachother?
    public static final SystemProperty<String> REALM = SystemProperty.Builder.ofType(String.class)
        .setKey("sasl.realm")
        .setDynamic(true)
        .setDefaultValue(null)
        .build();

    public static final SystemProperty<List<String>> APPROVED_REALMS = SystemProperty.Builder.ofType(List.class)
        .setKey("sasl.approvedRealms")
        .setDefaultValue(Collections.emptyList())
        .setDynamic(true)
        .buildList(String.class);

    public static final SystemProperty<Boolean> PROXY_AUTH = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("sasl.proxyAuth")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    public static final SystemProperty<Boolean> SKIP_PEER_CERT_REVALIDATION_CLIENT = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.external.client.skip-cert-revalidation")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    /**
     * Require the peer to provide an authorization identity through SASL (typically in the Initial Response) when authenticating
     * an inbound S2S connection that uses the EXTERNAL SASL mechanism.
     *
     * This is not required by the XMPP protocol specification, but it was required by Openfire versions prior to release 4.8.0.
     * This configuration option is added to allow for backwards compatibility.
     */
    public static final SystemProperty<Boolean> EXTERNAL_S2S_REQUIRE_AUTHZID = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.external.server.require-authzid")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    /**
     * Send an authorization identity in the Initial Response when attempting to authenticate using the SASL EXTERNAL
     * mechanism with a remote XMPP domain. Sending the authzid in this manner is not required by the XMPP protocol
     * specification, but is recommended in XEP-0178 for compatibility with older server implementations.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0178.html">XEP-0178: Best Practices for Use of SASL EXTERNAL with Certificates</a>
     */
    public static final SystemProperty<Boolean> EXTERNAL_S2S_SKIP_SENDING_AUTHZID = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.external.server.skip-sending-authzid")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    /**
     * Enable (or disable) SASL2. This is currently off by default, and means that SASL2 is not advertised in features, primarily.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
     */
    public static final SystemProperty<Boolean> ENABLE_SASL2 = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.sasl2")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    /**
     * Require TLS for SASL2. This is currently on by default, and means that SASL2 is not advertised in features without TLS.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
     */
    public static final SystemProperty<Boolean> SASL2_REQUIRE_TLS = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.sasl2.require-tls")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    // http://stackoverflow.com/questions/8571501/how-to-check-whether-the-string-is-base64-encoded-or-not
    // plus an extra regex alternative to catch a single equals sign ('=', see RFC 6120 6.4.2)
    private static final Pattern BASE64_ENCODED = Pattern.compile("^(=|([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==))$");

    public static final String SASL_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-sasl";
    public static final String SASL2_NAMESPACE = "urn:xmpp:sasl:2";

    /**
     * Java's SaslServer does not allow for null values. This makes it hard to distinguish between an empty (initial)
     * responses (represented in XMPP as a single equals sign character '=', as per RFC-6120 section 6.4.2), and a
     * missing/absent response. This can be problematic when a SASL mechanism implementation is to act differently on each
     * scenario (like the EXTERNAL mechanism, that is to challenge for an authzid when no initial response is provided,
     * but which is to use the stream's 'from' attribute value when the initial response is empty). To work around this
     * shortcoming in Java's SASL implementation, this class will add a session attribute using a key that has the name
     * of this constant's value when it detects a Sasl response that is present, but empty.
     *
     * @see <a href="https://igniterealtime.atlassian.net/jira/software/c/projects/OF/issues/OF-2514">OF-2514: Differentiate between missing and empty initial SASL response</a>
     */
    public static final String SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY = "Sasl.last-response-was-provided-but-empty";

    private static Set<String> mechanisms = new HashSet<>();

    static
    {
        // Add (proprietary) Providers of SASL implementation to the Java security context.
        if (Security.getProvider( "JiveSoftware" ) == null) {
            Security.addProvider(new org.jivesoftware.openfire.sasl.SaslProvider());
        }

        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("sasl.mechs");
        JiveGlobals.migrateProperty("sasl.gssapi.debug");
        JiveGlobals.migrateProperty("sasl.gssapi.config");
        JiveGlobals.migrateProperty("sasl.gssapi.useSubjectCredsOnly");

        initMechanisms();

        PropertyEventDispatcher.addListener( new PropertyEventListener()
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
        AUTHENTICATE,
        RESPONSE,
        CHALLENGE,
        FAILURE,
        UNDEF;

        /**
         * Returns the ElementType corresponding to the given name, performing a case-insensitive lookup.
         * Returns {@link #UNDEF} if the name is null, empty, or does not match any known element type.
         *
         * @param name the element name to look up (may be null or empty)
         * @return the matching ElementType, or {@link #UNDEF} if no match is found
         */
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
     * Returns a list of XML elements representing the SASL mechanism features that are applicable to the given session.
     * The returned elements are suitable for inclusion in the stream features element sent to the peer.
     * Both SASL (RFC 6120) and SASL2 (XEP-0388) feature elements may be included, depending on configuration.
     * An empty list is returned if the session is already authenticated or if the session type is not recognized.
     *
     * @param session the local session for which to determine applicable SASL mechanism feature elements (cannot be null)
     * @return a list of XML elements representing SASL mechanism features; never null, possibly empty
     */
    public static List<Element> getSASLMechanisms( @Nonnull LocalSession session )
    {
        final List<Element> features = new LinkedList<>();
        // Never list these if the session is already authenticated.
        if (session.isAuthenticated()) return features;

        if ( session instanceof ClientSession )
        {
            final Element sasl1Mechs = getSASLMechanismsElement( (ClientSession) session, false );
            if (sasl1Mechs != null) {
                features.add(sasl1Mechs);
            }
            if (checkSASL2Permitted(session).isEmpty())
            {
                final Element sasl2Mechs = getSASLMechanismsElement((ClientSession) session, true);
                if (sasl2Mechs != null) {
                    features.add(sasl2Mechs);
                }
            }
        }
        else if ( session instanceof LocalIncomingServerSession )
        {
            final Element sasl1Mechs = getSASLMechanismsElement( (LocalIncomingServerSession) session, false );
            if (sasl1Mechs != null) {
                features.add(sasl1Mechs);
            }
            if (checkSASL2Permitted(session).isEmpty())
            {
                final Element sasl2Mechs = getSASLMechanismsElement((LocalIncomingServerSession) session, true);
                if (sasl2Mechs != null) {
                    features.add(sasl2Mechs);
                }
            }
        }
        else
        {
            Log.debug( "Unable to determine SASL mechanisms that are applicable to session '{}'. Unrecognized session type.", session );
        }

        return features;
    }

    /**
     * Returns an XML element advertising the SASL mechanisms available to the given client session.
     * The element will be in either the SASL (RFC 6120) or SASL2 (XEP-0388) namespace depending on
     * the {@code usingSASL2} parameter. The EXTERNAL mechanism is only included if the session is
     * encrypted and the peer has a trusted certificate. May return {@code null} if the resulting
     * element would be empty and the {@code sasl.client.suppressEmpty} property is set to {@code true}.
     *
     * @param session    the client session for which to generate the mechanisms element (cannot be null)
     * @param usingSASL2 {@code true} to generate a SASL2 {@code <authentication>} element;
     *                   {@code false} to generate a SASL1 {@code <mechanisms>} element
     * @return an XML element listing the available SASL mechanisms, or {@code null} if the element
     *         would be empty and suppression of empty elements is configured
     */
    public static Element getSASLMechanismsElement( ClientSession session, boolean usingSASL2 )
    {
        final Set<String> availableMechanisms = getAvailableMechanismsForClientSession(session);

        final Namespace namespace = new Namespace("", usingSASL2 ? SASL2_NAMESPACE : SASL_NAMESPACE );
        final QName qName = new QName(usingSASL2 ? "authentication" : "mechanisms", namespace);
        final Element result = DocumentHelper.createElement( qName );
        for (final String mech : availableMechanisms) {
            final Element mechanism = result.addElement("mechanism");
            mechanism.setText(mech);
        }
        if ( usingSASL2 )
        {
            Element inlineElement = result.addElement("inline");
            inlineElement.add(Bind2Request.featureElement());
            // Element sm = inlineElement.addElement(...);
        }

        // OF-2072: Return null instead of an empty element, if so configured.
        if ( (usingSASL2 || JiveGlobals.getBooleanProperty("sasl.client.suppressEmpty", false)) && availableMechanisms.isEmpty() ) {
            return null;
        }

        return result;
    }

    /**
     * Returns an XML element advertising the SASL mechanisms available to the given incoming server session.
     * The element will be in either the SASL (RFC 6120) or SASL2 (XEP-0388) namespace depending on
     * the {@code usingSASL2} parameter. The EXTERNAL mechanism is only offered if the session is
     * encrypted and the peer has a trusted certificate that matches the session's default identity.
     * May return {@code null} if the resulting element would be empty and the
     * {@code sasl.server.suppressEmpty} property is set to {@code true}.
     *
     * @param session    the incoming server session for which to generate the mechanisms element (cannot be null)
     * @param usingSASL2 {@code true} to generate a SASL2 {@code <authentication>} element in the SASL2 namespace;
     *                   {@code false} to generate a SASL1 {@code <mechanisms>} element
     * @return an XML element listing the available SASL mechanisms, or {@code null} if the element
     *         would be empty and suppression of empty elements is configured
     */
    public static Element getSASLMechanismsElement( LocalIncomingServerSession session, boolean usingSASL2 )
    {
        final Set<String> availableMechanisms = getAvailableMechanismsForServerSession(session);

        // OF-2072: Return null instead of an empty element, if so configured.
        // For SASL2, always null.
        if ((usingSASL2 || JiveGlobals.getBooleanProperty("sasl.server.suppressEmpty", false)) && availableMechanisms.isEmpty()) {
            return null;
        }

        final Namespace namespace = new Namespace("", usingSASL2 ? SASL2_NAMESPACE : SASL_NAMESPACE );
        final QName qName = new QName(usingSASL2 ? "authentication" : "mechanisms", namespace);
        final Element result = DocumentHelper.createElement( qName );
        for (final String mech : availableMechanisms) {
            final Element mechanism = result.addElement("mechanism");
            mechanism.setText(mech);
        }

        return result;
    }

    // emptyNull indicates whether a zero-length string is just a zero-length string, or if it's null.
    // If emptyNull is false, the presence or absence of the element indicates null, whereas
    // if it's true (for auth in SASL1) there's a "=" to indicate genuine empty strings.
    @VisibleForTesting
    static byte[] decodeData(Element doc, boolean emptyNull) throws SaslFailureException {
        // Decode any data that is provided in the client response.
        if (doc == null) {
            if (emptyNull) {
                // I think this is only for SASL1 where there is a DIGEST-MD5 SASL-IR.
                return new byte[0];
            }
            return null;
        }
        final String encoded = doc.getTextTrim();
        final byte[] decoded;
        if ( encoded == null )
        {
            decoded = null;
        }
        else if ( encoded.isEmpty() )
        {
            if (emptyNull)
            {
                decoded = null;
            }
            else
            {
                decoded = new byte[0];
            }
        }
        else if ( encoded.equals("=") )
        {
            if (!emptyNull)
            {
                throw new SaslFailureException(Failure.INCORRECT_ENCODING);
            }
            decoded = new byte[0];
        }
        else
        {
            // TODO: We shouldn't depend on regex-based validation. Instead, use a proper decoder implementation and handle any exceptions that it throws.
            if ( !BASE64_ENCODED.matcher( encoded ).matches() )
            {
                throw new SaslFailureException( Failure.INCORRECT_ENCODING );
            }
            decoded = Base64.getDecoder().decode(encoded.getBytes(StandardCharsets.UTF_8));
        }
        return decoded;
    }

    /**
     * Handles the SASL authentication packet. The entity may be sending an initial
     * authentication request or a response to a challenge made by the server. The returned
     * value indicates whether the authentication has finished either successfully or not or
     * if the entity is expected to send a response to a challenge.
     *
     * @param session     the session that is authenticating with the server.
     * @param doc         the stanza sent by the authenticating entity.
     * @param usingSASL2  {@code true} if the authentication is being performed using SASL2 (XEP-0388);
     *                    {@code false} if using standard SASL (RFC 6120)
     * @return value that indicates whether the authentication has finished either successfully
     *         or not or if the entity is expected to send a response to a challenge.
     */
    public static Status handle(LocalSession session, Element doc, boolean usingSASL2)
    {
        try
        {
            if (usingSASL2)
            {
                // SASL2
                final Optional<Failure> ineligible = checkSASL2Permitted(session);
                if (ineligible.isPresent()) {
                    throw new SaslFailureException(ineligible.get(), "SASL2 is not permitted for this session.");
                }
                if (!SASL2_NAMESPACE.equals(doc.getNamespaceURI())) {
                    throw new IllegalStateException("Unexpected data received while negotiating SASL2 authentication. Offending root element: " + doc.getName() + " Namespace: " + doc.getNamespaceURI());
                }
            }
            else
            {
                // SASL1
                if (!SASL_NAMESPACE.equals(doc.getNamespaceURI()))
                {
                    throw new IllegalStateException("Unexpected data received while negotiating SASL authentication. Offending root element: " + doc.getName() + " Namespace: " + doc.getNamespaceURI());
                }
            }

            ElementType elementType = ElementType.valueOfCaseInsensitive(doc.getName());

            if (elementType == ElementType.AUTHENTICATE) {
                if (!usingSASL2) {
                    throw new IllegalStateException("Unexpected data received while negotiating SASL2 authentication. Name of the offending root element: " + doc.getName() + " Namespace: " + doc.getNamespaceURI());
                }
            } else if (elementType == ElementType.AUTH && usingSASL2) {
                throw new IllegalStateException( "Unexpected data received while negotiating SASL2 authentication. Name of the offending root element: " + doc.getName() + " Namespace: " + doc.getNamespaceURI() );
            }

            Element data = doc;
            boolean emptyNull = false; // This is only true for SASL1 "auth" and "success".
            SaslServer saslServer = (SaslServer) session.getSessionData( "SaslServer" ); // This may be null at this point.
            switch (elementType)
            {
                case ABORT:
                    throw new SaslFailureException( Failure.ABORTED );

                case AUTHENTICATE: // intended fall-through
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

                    // Enforce session-specific eligibility (as advertised in stream features) See OF-3273.
                    if ( !getAvailableMechanismsForSession( session ).contains( mechanismName ) )
                    {
                        throw new SaslFailureException( Failure.INVALID_MECHANISM, "The mechanism is not available for this session." );
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

                    saslServer = Sasl.createSaslServer( mechanismName, "xmpp", serverName, props, new XMPPCallbackHandler() );
                    if ( saslServer == null )
                    {
                        throw new SaslFailureException( Failure.INVALID_MECHANISM, "There is no provider that can provide a SASL server for the desired mechanism and properties." );
                    }

                    session.setSessionData( "SaslServer", saslServer );

                    if (elementType == ElementType.AUTHENTICATE)
                    {
                        data = doc.element("initial-response");
                    }
                    else
                    {
                        emptyNull = true;
                    }

                    if ( mechanismName.equals( "DIGEST-MD5" ) )
                    {
                        // RFC2831 (DIGEST-MD5) says the client MAY provide data in the initial response. Java SASL does
                        // not (currently) support this and throws an exception. For XMPP, such data violates
                        // the RFC, so we just strip any initial token.
                        if (data != null) data = null;
                    }
                    if (usingSASL2 && session instanceof LocalClientSession) {
                        Element userAgentElement = doc.element("user-agent");
                        if (userAgentElement != null) {
                            UserAgentInfo userAgentInfo = UserAgentInfo.extract(userAgentElement);
                            if (userAgentInfo != null) {
                                // Store the user agent info in the session
                                session.setSessionData("user-agent-info", userAgentInfo);
                            }
                        }
                        Bind2Request bind2Request = Bind2Request.from(doc);
                        if (bind2Request != null) {
                            session.setSessionData("bind2-request", bind2Request);
                        }
                    }

                    // intended fall-through
                case RESPONSE:
                    if ( saslServer == null )
                    {
                        // Client sends response without a preceding auth?
                        throw new IllegalStateException( "A SaslServer instance was not initialized and/or stored on the session." );
                    }

                    // Decode any data that is provided in the client response.
                    byte[] decoded = decodeData( data, emptyNull );

                    session.removeSessionData( SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY );
                    if ( decoded == null )
                    {
                        decoded = new byte[0];
                    }
                    else if ( decoded.length == 0 )
                    {
                        session.setSessionData(SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY, Boolean.TRUE);
                    }

                    // Process client response.
                    final byte[] challenge = saslServer.evaluateResponse( decoded ); // Either a challenge or success data. Note that Java SASL cannot handle a null here.

                    if ( !saslServer.isComplete() )
                    {
                        // Not complete: client is challenged for additional steps.
                        sendChallenge( session, challenge, usingSASL2 );
                        return Status.needResponse;
                    }

                    if (saslServer.getAuthorizationID() != null && LockOutManager.getInstance().isAccountDisabled(saslServer.getAuthorizationID())) {
                        // Interception!  This person is locked out, fail instead!
                        LockOutManager.getInstance().recordFailedLogin(saslServer.getAuthorizationID());
                        throw new SaslFailureException(Failure.ACCOUNT_DISABLED);
                    }

                    // Success! Any mechanism-specific verification (such as certificate checks for EXTERNAL) is
                    // performed by the SaslServer implementation.
                    authenticationSuccessful( session, saslServer.getAuthorizationID(), saslServer.getMechanismName(), challenge, usingSASL2 );
                    session.removeSessionData( "SaslServer" );
                    session.removeSessionData( SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY );
                    session.setSessionData("SaslMechanism", saslServer.getMechanismName());
                    if (requiresChannelBinding(saslServer.getMechanismName())) {
                        session.setSessionData("ChannelBindingType", saslServer.getNegotiatedProperty(ScramSha1SaslServer.PROPNAME_CHANNELBINDINGTYPE));
                    }
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
            authenticationFailed( session, failure, usingSASL2 );
            session.removeSessionData( "SaslServer" );
            return Status.failed;
        }
        catch( Exception ex )
        {
            Log.warn( "An unexpected exception occurred during SASL negotiation. Affected session: {}", session, ex );
            authenticationFailed( session, Failure.NOT_AUTHORIZED, usingSASL2 );
            session.removeSessionData( "SaslServer" );
            return Status.failed;
        }
    }

    /**
     * Determines whether SASL2 may be used for the given session at this moment, returning the reason it cannot if
     * applicable.
     *
     * This is the single source of truth for SASL2 eligibility: it governs both whether SASL2 is advertised in stream
     * features and whether an inbound SASL2 authentication request is processed, so a peer cannot drive a negotiation
     * that was never offered.
     *
     * @param session the session for which SASL2 eligibility is evaluated (cannot be null).
     * @return an empty Optional if SASL2 is permitted; otherwise the {@link Failure} describing why it is not.
     */
    @VisibleForTesting
    static Optional<Failure> checkSASL2Permitted(@Nonnull final LocalSession session)
    {
        if (!ENABLE_SASL2.getValue()) {
            return Optional.of(Failure.NOT_AUTHORIZED);
        }
        if (SASL2_REQUIRE_TLS.getValue() && !session.isEncrypted()) {
            return Optional.of(Failure.ENCRYPTION_REQUIRED);
        }
        return Optional.empty();
    }

    /**
     * Verifies that the given X.509 certificate is valid for the specified hostname. The certificate's
     * server identities are checked against the hostname, with support for wildcard certificates.
     * A wildcard identity (e.g. {@code *.example.com}) matches any direct subdomain of the base domain.
     *
     * @param trustedCert the X.509 certificate to verify (cannot be null)
     * @param hostname    the hostname to verify the certificate against (cannot be null)
     * @return {@code true} if the certificate is valid for the given hostname; {@code false} otherwise
     */
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

    /**
     * Verifies that the end-entity certificate in the given certificate chain is trusted and valid
     * for the specified hostname. The appropriate trust store is selected based on whether this is
     * a server-to-server (S2S) or client-to-server (C2S) connection.
     *
     * @param chain    the certificate chain to verify; the end-entity certificate will be extracted
     *                 and checked against the trust store (may be null or empty, in which case
     *                 verification will fail)
     * @param hostname the hostname that the certificate must be valid for (cannot be null)
     * @param isS2S    {@code true} if this is a server-to-server connection (uses the S2S trust store);
     *                 {@code false} if this is a client-to-server connection (uses the C2S trust store)
     * @return {@code true} if a trusted end-entity certificate is found in the chain and it is valid
     *         for the given hostname; {@code false} otherwise
     */
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

    private static void sendElement(Session session, String element, byte[] data, boolean usingSASL2) {
        final Element reply = DocumentHelper.createElement(QName.get(element, usingSASL2 ? SASL2_NAMESPACE : SASL_NAMESPACE));
        if (data != null) {
            String data_b64 = Base64.getEncoder().encodeToString(data).trim();
            if (data_b64.isEmpty()) {
                // Empty-payload sentinel. Only meaningful for SASL1; unreachable on the SASL2 path, whose sole caller here is <challenge>, which is never sent with empty/missing data.
                data_b64 = "=";
            }
            reply.addText(data_b64);
        }
        session.deliverRawText(reply.asXML());
    }

    private static void sendChallenge(Session session, byte[] challenge, boolean usingSASL2) {
        sendElement(session, "challenge", challenge, usingSASL2);
    }

    /**
     * Processes a successful SASL authentication.
     *
     * For client sessions, generates an authentication token. For inbound server sessions, marks the domain as
     * validated and records the authentication method used.
     *
     * @param session the authenticated session (cannot be null).
     * @param username the authorized identity from SASL (can be null for anonymous).
     * @param mechanismName the name of the SASL mechanism that was used (cannot be null).
     * @param successData mechanism-specific success data (can be null).
     * @param usingSASL2 are we using SASL2?
     */
    @VisibleForTesting
    static void authenticationSuccessful(LocalSession session, String username, String mechanismName, byte[] successData, boolean usingSASL2)
    {
        // The identity to report back to the peer. For clients this is a bare JID; for anonymous clients, the node-part is
        // the session's generated resource (see LocalClientSession#getAnonymousUsername). Must be resolved before the
        // session transitions to an authenticated state.
        final String authorizationIdentity;

        if (session instanceof LocalClientSession clientSession) {
            final AuthToken authToken;
            final String node;
            if (username == null) {
                node = clientSession.getAnonymousUsername();
                authToken = AuthToken.generateAnonymousToken();
            } else {
                authToken = AuthToken.generateUserToken(username);
                node = authToken.getUsername(); // Normalized: strips any domain-part from the authzid.
            }
            authorizationIdentity = new JID(node, XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null, true).toString();
            clientSession.setAuthToken(authToken);
        }
        else if (session instanceof LocalIncomingServerSession serverSession) {
            authorizationIdentity = username;
            serverSession.addValidatedDomain(username);
            serverSession.setAuthenticationMethod(ServerSession.AuthenticationMethod.fromSaslMechanismName(mechanismName));
            Log.info("Inbound Server {} authenticated using SASL mechanism {}", username, mechanismName);
        }
        else {
            authorizationIdentity = username;
        }

        if (usingSASL2) {
            final Element success = DocumentHelper.createElement( new QName( "success", new Namespace( "", SASL2_NAMESPACE ) ) );
            if (successData != null && successData.length > 0) {
                String data_b64 = Base64.getEncoder().encodeToString(successData).trim();
                Element additionalData = success.addElement("additional-data");
                additionalData.setText(data_b64);
            }
            success.addElement("authorization-identifier").setText(authorizationIdentity);
            session.deliverRawText(success.asXML());
        } else {
            sendElement(session, "success", successData, false);
        }

        if (usingSASL2) {
            if (session instanceof LocalClientSession clientSession) {
                final Bind2Request bind2Request = (Bind2Request) session.getSessionData("bind2-request");
                if (bind2Request != null && clientSession.getStatus() != Session.Status.AUTHENTICATED) {
                    session.setSessionData("bind2-request", null);
                    final UserAgentInfo userAgentInfo = (UserAgentInfo) session.getSessionData("user-agent-info");
                    final String resource = bind2Request.generateResourceString(userAgentInfo);
                    final AuthToken authToken = clientSession.getAuthToken();
                    final byte[] finalSuccessData = successData;
                    SessionManager.getInstance().bindResource(clientSession, authToken, resource)
                        .whenComplete((result, throwable) -> {
                            final boolean bound = throwable == null && result == SessionManager.BindResult.BOUND;
                            final Element success = buildSasl2SuccessElement(finalSuccessData, username, bound ? resource : null);
                            if (bound) {
                                bind2Request.processFeatureRequests(session, success);
                            }
                            session.deliverRawText(success.asXML());
                            if (bound) {
                                SessionEventDispatcher.dispatchEvent(session, SessionEventDispatcher.EventType.resource_bound);
                            }
                        });
                    return; // Response is sent asynchronously from the completion stage.
                }
            }
            // No Bind2 request, or session already authenticated: send <success/> synchronously without <bound/>.
            final Element success = buildSasl2SuccessElement(successData, username, null);
            session.deliverRawText(success.asXML());
        } else {
            sendElement(session, "success", successData, usingSASL2);
        }
    }

    /**
     * Builds a SASL2 &lt;success/&gt; element.
     *
     * @param successData optional mechanism-specific success data (can be null).
     * @param username the authorized identity.
     * @param resource the bound resource, or null if no resource was bound.
     * @return the &lt;success/&gt; element.
     */
    private static Element buildSasl2SuccessElement(byte[] successData, String username, String resource) {
        final Element success = DocumentHelper.createElement(new QName("success", new Namespace("", SASL2_NAMESPACE)));
        if (successData != null && successData.length > 0) {
            final String data_b64 = Base64.getEncoder().encodeToString(successData).trim();
            success.addElement("additional-data").setText(data_b64);
        }
        final StringBuilder authId = new StringBuilder(username != null ? username : "");
        authId.append('@').append(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
        if (resource != null) {
            authId.append('/').append(resource);
        }
        success.addElement("authorization-identifier").setText(authId.toString());
        if (resource != null) {
            // Add <bound/> element to indicate successful resource binding (XEP-0386).
            // TODO: SHOULD add MAM metadata element here.
            success.addElement(new QName("bound", new Namespace("", "urn:xmpp:bind:0")));
        }
        return success;
    }

    private static void authenticationFailed(LocalSession session, Failure failure, boolean usingSASL2) {
        final Element reply = DocumentHelper.createElement(QName.get("failure", usingSASL2 ? SASL2_NAMESPACE : SASL_NAMESPACE));
        if (usingSASL2) {
            // SASL2 still uses the original SASL namespace for failure reasons.
            reply.addElement(failure.toString(), SASL_NAMESPACE);
        } else {
            reply.addElement(failure.toString());
        }
        session.deliverRawText(reply.asXML());
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
            session.markNonResumable();
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
     * @param mechanismName the name of the SASL mechanism to remove (cannot be null or empty, not case-sensitive).
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
     * @return the set of supported SASL mechanisms by the server.
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

            if (requiresChannelBinding(mechanism) && ChannelBindingProviderManager.getInstance().getSupportedChannelBindingTypes().isEmpty()) {
                Log.trace( "Cannot support '{}' as there's no implementation available for channel binding.", mechanism );
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
                        Log.trace( "Cannot support '{}' as the AuthProvider that's in use does not support password retrieval.", mechanism );
                        it.remove();
                    }
                    break;

                case "SCRAM-SHA-1": // intended fall-through
                case "SCRAM-SHA-1-PLUS":
                    if ( !AuthFactory.supportsScram() )
                    {
                        Log.trace( "Cannot support '{}' as the AuthProvider that's in use does not support SCRAM.", mechanism );
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
     * Returns the set of SASL mechanisms available for the given session.
     *
     * @param session the session (cannot be null).
     * @return a set of available mechanism names for the session (never null, possibly empty).
     */
    @VisibleForTesting
    static Set<String> getAvailableMechanismsForSession( final LocalSession session )
    {
        if ( session instanceof ClientSession )
        {
            return getAvailableMechanismsForClientSession( (ClientSession) session );
        }
        else if ( session instanceof LocalIncomingServerSession )
        {
            return getAvailableMechanismsForServerSession( (LocalIncomingServerSession) session );
        }
        else
        {
            return Collections.emptySet();
        }
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
        return JiveGlobals.getListProperty("sasl.mechs", Arrays.asList( "ANONYMOUS","PLAIN","DIGEST-MD5","CRAM-MD5","SCRAM-SHA-1","SCRAM-SHA-1-PLUS","JIVE-SHAREDSECRET","GSSAPI","EXTERNAL" ) );
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

    /**
     * Returns {@code true} if the given SASL mechanism name requires channel binding.
     * Channel-binding mechanisms follow the naming convention of appending {@code -PLUS} to the
     * base mechanism name (e.g. {@code SCRAM-SHA-1-PLUS}).
     *
     * @param mechanismName the SASL mechanism name to check (cannot be null)
     * @return {@code true} if the mechanism requires channel binding; {@code false} otherwise
     */
    @VisibleForTesting
    static boolean requiresChannelBinding(@Nonnull final String mechanismName) {
        return mechanismName.endsWith("-PLUS");
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

    /**
     * Determines and returns the set of SASL mechanisms that are available for a given client session. This includes
     * mechanisms from the list of supported mechanisms, applying additional checks to ensure mechanism-specific
     * requirements (e.g., encryption and certificate validation) are met.
     *
     * @param session The client session for which available SASL mechanisms need to be determined.
     *                Must not be null.
     * @return A set of available SASL mechanism names for the specified client session.
     *         Will never be null but might be empty if no mechanisms are available.
     */
    private static Set<String> getAvailableMechanismsForClientSession(@Nonnull final ClientSession session )
    {
        final Connection connection = ( (LocalClientSession) session ).getConnection();
        assert connection != null; // While the client is performing a SASL negotiation, the connection can't be null.
        final Set<String> result = new HashSet<>();
        for (String mech : getSupportedMechanisms()) {
            if (mech.equals("EXTERNAL")) {
                boolean trustedCert = false;
                if (session.isEncrypted()) {
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
            if (requiresChannelBinding(mech)) {
                // Prevent offering channel binding if the Connection implementation does not support it.
                if (connection.getSupportedChannelBindingTypes().isEmpty()) {
                    continue; // Do not offer channel-binding variants.
                }
                // Channel binding would be a binding to TLS, thus encryption is required for channel binding.
                if (!session.isEncrypted()) { // This ought to be redundant, as getSupportedChannelBindingTypes() will return an empty set if not encrypted.
                    continue;
                }
            }
            result.add(mech);
        }
        return result;
    }

    /**
     * Determines the set of available SASL mechanisms for the given server session. This method checks the session's
     * encryption status and examines the trust relationship to determine if specific mechanisms (such as SASL EXTERNAL)
     * can be offered.
     *
     * @param session the server session for which the available mechanisms are to be determined.
     *                Must not be null.
     * @return a set of SASL mechanism names that can be offered for the specified session.
     *         If no mechanisms are available, an empty set is returned.
     */
    private static Set<String> getAvailableMechanismsForServerSession(@Nonnull final LocalIncomingServerSession session)
    {
        final Set<String> result = new HashSet<>();

        // Check if EXTERNAL is enabled in the supported mechanisms configuration
        if (!getSupportedMechanisms().contains("EXTERNAL")) {
            return result;
        }

        if (session.isEncrypted()) {
            final Connection connection   = session.getConnection();
            final TrustStore trustStore   = connection.getConfiguration().getTrustStore();
            final X509Certificate trusted = trustStore.getEndEntityCertificate( session.getConnection().getPeerCertificates() );

            boolean haveTrustedCertificate = trusted != null;
            if (trusted != null && session.getDefaultIdentity() != null) {
                haveTrustedCertificate = verifyCertificate(trusted, session.getDefaultIdentity());
            }
            if (haveTrustedCertificate) {
                // Offer SASL EXTERNAL only if TLS has already been negotiated and the peer has a trusted cert.
                result.add("EXTERNAL");
            }
        }
        return result;
    }

    /**
     * Appends to a list of stream features channel binding type capability announcements, if needed.
     *
     * The necessity is based on the other features already in the list, notably the advertised SASL mechanisms. Channel
     * binding types that are available are added when-and-only-when these mechanisms include a channel-binding-capable
     * mechanism.
     *
     * @param features The advertised features, that at the very least should include advertised SASL mechanisms.
     * @see <a href="https://xmpp.org/extensions/xep-0440.html">XEP-0440: SASL Channel-Binding Type Capability</a>
     */
    public static void appendChannelBindingCapabilityIfNeeded(final List<Element> features)
    {
        // Iterate a snapshot: we add to 'features' inside the loop.
        final List<Element> saslFeatures = features.stream()
            .filter(SASLAuthentication::isSaslAuthenticationFeature)
            .toList();

        for (final Element saslFeature : saslFeatures) {
            ChannelBindingProviderManager.getInstance()
                .getSASLChannelBindingTypeCapabilityElement(saslFeature)
                .ifPresent(features::add);
        }
    }

    /**
     * Verifies if the provided XML element is a SASL authentication feature.
     *
     * Specifically, this method checks if the element equals a {@code <feature>} child element that is either
     * <ul>
     *     <li>a SASL(1) feature: {@code <mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>}; or:</li>
     *     <li>A SASL2 feature: {@code <authentication xmlns='urn:xmpp:sasl:2'>}</li>
     * </ul>
     *
     * @param element The element (presumably a child element of {@code <feature>}) to check
     * @return true if the element is a SASL authentication feature
     */
    private static boolean isSaslAuthenticationFeature(final Element element)
    {
        return ("mechanisms".equals(element.getName()) && SASL_NAMESPACE.equals(element.getNamespaceURI()))
            || ("authentication".equals(element.getName()) && SASL2_NAMESPACE.equals(element.getNamespaceURI()));
    }
}
