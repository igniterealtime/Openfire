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
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.jivesoftware.openfire.fast.FastRequest;
import org.jivesoftware.openfire.fast.FastToken;
import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.fast.FastSessionState;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.sasl.AnonymousSaslServer;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.sasl.MechanismName;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.openfire.sasl.SaslMechanismCatalog;
import org.jivesoftware.openfire.sasl.SaslMechanismEligibility;
import org.jivesoftware.openfire.sasl.ScramSaslServer;
import org.jivesoftware.openfire.sasl.task.Sasl2Negotiation;
import org.jivesoftware.openfire.sasl.task.Sasl2TaskManager;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.openfire.streammanagement.MalformedResumeRequestException;
import org.jivesoftware.openfire.streammanagement.ResumeRequest;
import org.jivesoftware.openfire.streammanagement.Sasl2ResumeResult;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Base64;
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

    /**
     * Consumed by {@link SaslMechanismEligibility}, which decides whether the SASL EXTERNAL mechanism can be offered to
     * a client session. The property is declared here because {@link SystemProperty} registers by key in a global
     * registry, so it cannot be moved without changing when that registration happens.
     */
    public static final SystemProperty<Boolean> SKIP_PEER_CERT_REVALIDATION_CLIENT = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.external.client.skip-cert-revalidation")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    /**
     * Controls if the SASL SCRAM Downgrade Protection feature, as specified in XEP-0474, is enabled. When enabled,
     * Openfire will add a hash value to the optional {@code h} attribute of {@code server-first-message} that is sent
     * to peers when they perform SCRAM-based authentication. This can help them prevent downgrade attacks.
     */
    public static final SystemProperty<Boolean> SSDP_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("sasl.scram.downgrade-protection.enabled")
        .setDefaultValue(Boolean.TRUE)
        .setDynamic(Boolean.TRUE)
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
     * Enable (or disable) SASL2. When disabled, it means that SASL2 is not advertised in features, primarily.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
     */
    public static final SystemProperty<Boolean> ENABLE_SASL2 = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.sasl2")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    /**
     * Require TLS for SASL2. This is currently on by default, and means that SASL2 is not advertised in features without TLS.
     * This setting also governs the no-channel-binding FAST mechanisms ({@code HT-*-NONE} and
     * {@code HT2-*-NONE}). Disabling it allows those mechanisms on a connection that Openfire does not identify as
     * TLS-encrypted. This is intended only for deployments in which another layer provides equivalent transport
     * security, such as a trusted encrypted network where TLS termination or use by Openfire is undesirable.
     * Channel-binding FAST variants continue to require TLS and their corresponding channel-binding data.
     * Administrators that disable this safeguard are responsible for preventing observation and replay of FAST
     * authentication traffic.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
     * @see <a href="https://xmpp.org/extensions/xep-0484.html">XEP-0484: Fast Authentication Streamlining Tokens</a>
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
    public static final String SASL_CHANNEL_BINDING_NAMESPACE = "urn:xmpp:sasl-cb:0";

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

    /**
     * Session Data property name used to store the password authentication attempt in progress: the
     * {@link UserLoginLimitManager.Reservation} of the attempt, set by a password mechanism (through
     * {@link #admitPasswordAttempt(LocalSession, String)}) once it knows the username and the attempt is admitted by the
     * user login limits. A success uses it to give the attempt back. It is cleared at the start of every attempt, on
     * every failure and on success, so an attempt cannot inherit the reservation of an earlier one.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2279">OF-2279: Login limits for user accounts</a>
     */
    public static final String SASL_LOGIN_ATTEMPT = "Sasl.login-attempt";

    /**
     * Session Data property name used to store which SASL mechanisms were advertised by the server to the peer as being
     * available for the session that is performing SASL authentication. The value is expected to be a Set of Strings,
     * if any mechanisms were advertised from Openfire to the peer.
     *
     * Instead of using this value directly, consider using {@link SaslMechanismEligibility#getAdvertisableSASLMechanisms(LocalSession)},
     * {@link #setAdvertisedSASLMechanisms(LocalSession, Set)}, or {@link #getAdvertisedSASLMechanisms(LocalSession)}
     * which encapsulate the business-logic related to this constant.
     */
    public static final String AVAILABLE_MECHANISMS_FOR_SESSION = "SaslMechanismsOfferedByServer";

    /**
     * Session Data property name used to store which channel bindings were advertised by the server to the peer as being
     * available for the session that is performing SASL authentication. The value is expected to be a Set of Strings,
     * if any bindings were advertised from Openfire to the peer.
     *
     * Instead of using this value directly, consider using {@link #getAdvertisedChannelBindingTypes(LocalSession)} or
     * {@link #setAdvertisedChannelBindingTypes(LocalSession, Set)} which encapsulate the business-logic related to this
     * constant.
     */
    public static final String AVAILABLE_CHANNEL_BINDING_TYPES_FOR_SESSION = "ChannelBindingTypesOfferedByServer";

    /**
     * Session Data property name used, on the temporary session that is negotiating a SASL2 authentication, to hand
     * off the pre-existing session that was resumed inline (XEP-0198 § 9.2) to the caller of {@link #handle(LocalSession, Element, boolean)}.
     *
     * The resumed session itself cannot be represented in {@link Status}, so this session data, together with
     * {@link Status#authenticatedResumed}, is used instead to communicate the outcome. A caller (typically
     * {@code StanzaHandler}) is expected to switch to using the referenced session, and to remove this session data.
     */
    public static final String SASL2_RESUMED_SESSION = "Sasl2.resumed-session";

    /**
     * Session Data property name used to store a parsed inline XEP-0198 resume request (see
     * {@link ResumeRequest}), between the moment it is parsed from the SASL2 {@code <authenticate/>} element
     * and the moment SASL authentication succeeds and the request can be acted on.
     */
    private static final String SASL2_RESUME_REQUEST = "Sasl2.resume-request";

    /**
     * Session Data property name used, by StanzaHandler, to mark that a SASL2 {@code <authenticate/>} that requested
     * Bind2 resource binding is either still awaiting the outcome of the asynchronous {@link SessionManager#bindResource}
     * call it triggered, or has already completed successfully. StanzaHandler sets this when it dispatches such
     * a request (at that point, {@code Status.authenticatedAwaitingFeatures} is all it knows: the bind itself has
     * not yet resolved), and treats its presence, like {@code sasl2AuthenticationCompleted}, as a reason to refuse
     * any further {@code <authenticate/>} on the same stream, per XEP-0388 § 4.8.
     *
     * Unlike {@code sasl2AuthenticationCompleted}, this is not durable: {@link #abortSasl2} removes it if the bind
     * ultimately fails, since the peer was never sent {@code <success/>} and is permitted to retry (up to the
     * configured limit, enforced by {@link SaslOutcome#authenticationFailed}). Once the bind succeeds, this is left
     * set for the lifetime of the session; nothing subsequently promotes it into {@code sasl2AuthenticationCompleted}.
     * See OF-3362.
     *
     * <b>Not a record of Bind2 usage.</b> This flag answers "is a Bind2 attempt in flight or done on this stream,
     * such that another {@code <authenticate/>} must be refused?" — it is {@code true} throughout the asynchronous
     * {@link SessionManager#bindResource} call, before the outcome is known. For "was this session's resource
     * actually bound via Bind2?", a fact that is knowable only after success and is never set on failure or on a
     * skipped bind (e.g. inline XEP-0198 resume), see {@link #BIND2_USED}.
     */
    static final String SASL2_BIND2_NEGOTIATION_ACTIVE_OR_DONE = "Sasl2.bind2-negotiation-active-or-done";

    /**
     * Session Data property name used to record that a SASL2 negotiation, from the first valid SASL2 element received
     * until a terminal outcome (success, failure, or abort), is currently in progress for a session.
     */
    static final String SASL2_NEGOTIATION_IN_PROGRESS = "Sasl2.negotiation-in-progress";

    /**
     * Session Data property name used to record that a session's resource was bound via an inline XEP-0386 Bind 2
     * request during SASL2 authentication, as opposed to legacy IQ-based binding. Set only once binding has actually
     * completed successfully; never set for sessions that bind via legacy IQ, and never set when an inline XEP-0198
     * resume made the inlined bind2 request moot.
     *
     * Contrast with {@link #SASL2_BIND2_NEGOTIATION_ACTIVE_OR_DONE}, which is set earlier (before the outcome of the
     * bind is known) and for a different purpose (blocking concurrent {@code <authenticate/>} on the stream). During
     * the asynchronous {@link SessionManager#bindResource} call, that flag is {@code true} while this one is still
     * {@code false}; the two are not interchangeable.
     */
    public static final String BIND2_USED = "Bind2.used";

    /**
     * Session Data property name holding the set of XML namespaces of inline feature requests that
     * were successfully negotiated via a XEP-0386 Bind 2 request, if any.
     */
    public static final String BIND2_INLINE_FEATURES = "Bind2.inline-features";

    /**
     * Controls whether the SCRAM mechanisms that are advertised to a client are tailored to the user that is expected
     * to authenticate.
     *
     * When enabled, the identity that a client claims in the 'from' attribute of its stream header is used to look up
     * which SCRAM mechanisms that user has credentials for, so that no mechanism is offered that cannot succeed.
     *
     * When disabled, every session is offered the mechanisms that any user can be assumed to hold, and no per-user
     * lookup is performed. This removes both the (small) signal that the tailored response gives an unauthenticated
     * peer about which users exist, and the credential lookup that such a peer can otherwise trigger. The cost is that
     * a user holding credentials for a stronger mechanism is not offered it unless every user holds it.
     *
     * Consumed by {@link SaslMechanismEligibility}. The property is declared here because {@link SystemProperty}
     * registers by key in a global registry, so it cannot be moved without changing when that registration happens.
     */
    public static final SystemProperty<Boolean> SCRAM_MECHANISMS_PER_USER = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.scram.mechanisms-per-user")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    static
    {
        // Historically this class registered Openfire's SASL provider and loaded the mechanism configuration when it
        // was first loaded. Preserve that timing for code that depends on it.
        SaslMechanismCatalog.initialize();
    }

    public enum ElementType
    {
        ABORT,
        AUTH,
        AUTHENTICATE,
        RESPONSE,
        CHALLENGE,
        FAILURE,
        NEXT,       // XEP-0388 § 2.5: the peer selects a task.
        TASK_DATA,  // XEP-0388 § 2.5: task payload, in either direction.
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
                return ElementType.valueOf( name.toUpperCase().replace('-', '_') );
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
        authenticated,

        /**
         * SASL negotiation has been successful, but the response (including stream features) will be
         * delivered asynchronously (e.g. when Bind2 resource binding completes). The caller must not
         * send stream features itself.
         */
        authenticatedAwaitingFeatures,

        /**
         * SASL2 negotiation has been successful by inline-resuming a pre-existing session (XEP-0198 § 9.2). The
         * {@code <success/>} response (including the {@code <resumed/>} element) has already been delivered, over
         * the resumed session, by {@link SASLAuthentication}. The caller must adopt the session referenced by
         * {@link #SASL2_RESUMED_SESSION} session data, and must NOT send stream features, per XEP-0198 § 9.2.
         */
        authenticatedResumed
    }

    /**
     * Records a set of SASL mechanism names as having been advertised for/to the given session.
     *
     * Some SASL mechanism implementations depend on this information. Notably, the SASL-SCRAM-SHA* mechanisms depend
     * on it to detect channel binding downgrades.
     *
     * Implementations of {@link LocalSession} should call this method when SASL mechanisms are advertised to a session.
     *
     * @param session the session for which to record advertised SASL mechanisms (cannot be null).
     * @param advertisedMechanisms the advertised SASL mechanism names
     */
    public static void setAdvertisedSASLMechanisms(@Nonnull final LocalSession session, final Set<String> advertisedMechanisms)
    {
        session.setSessionData(SASLAuthentication.AVAILABLE_MECHANISMS_FOR_SESSION, Set.copyOf(advertisedMechanisms));
    }

    /**
     * Returns the set of SASL mechanism names that has previously been advertised for/to the given session as being
     * available for use for that session.
     *
     * When advertisement has not (yet) happened when this method is invoked, an empty Optional is returned.
     *
     * @param session the session for which to obtain SASL mechanism names (cannot be null).
     * @return a set of mechanism names that have been advertised for/to the session.
     */
    public static Optional<Set<String>> getAdvertisedSASLMechanisms(@Nonnull final LocalSession session )
    {
        final Object sessionData = session.getSessionData(SASLAuthentication.AVAILABLE_MECHANISMS_FOR_SESSION);

        if (sessionData != null && !(sessionData instanceof Set)) {
            Log.warn("Unexpected object (not a Set) found in session data under key '{}' of session '{}': {}", SASLAuthentication.AVAILABLE_MECHANISMS_FOR_SESSION, session, sessionData);
            return Optional.empty();
        }
        //noinspection unchecked
        return Optional.ofNullable((Set<String>) sessionData);
    }

    /**
     * Records a set of channel binding types as having been advertised for/to the given session.
     *
     * Some SASL mechanism implementations depend on this information. Notably, the SASL-SCRAM-SHA* mechanisms depend
     * on it to detect channel binding downgrades.
     *
     * Implementations of {@link LocalSession} should call this method when channel bindings are advertised to a session.
     *
     * @param session the session for which to record advertised channel bindings (cannot be null).
     * @param advertisedChannelBindingTypes the advertised channel binding types
     */
    public static void setAdvertisedChannelBindingTypes(@Nonnull final LocalSession session, final Set<String> advertisedChannelBindingTypes)
    {
        session.setSessionData(SASLAuthentication.AVAILABLE_CHANNEL_BINDING_TYPES_FOR_SESSION, Set.copyOf(advertisedChannelBindingTypes));
    }

    /**
     * Returns the set of channel binding types that has previously been advertised for/to the given session as being
     * available for use for that session.
     *
     * When advertisement has not (yet) happened when this method is invoked, an empty Optional is returned.
     *
     * @param session the session for which to obtain channel binding types (cannot be null).
     * @return a set of channel binding types that have been advertised for/to the session.
     */
    public static Optional<Set<String>> getAdvertisedChannelBindingTypes(@Nonnull final LocalSession session )
    {
        final Object sessionData = session.getSessionData(SASLAuthentication.AVAILABLE_CHANNEL_BINDING_TYPES_FOR_SESSION);

        if (sessionData != null && !(sessionData instanceof Set)) {
            Log.warn("Unexpected object (not a Set) found in session data under key '{}' of session '{}': {}", SASLAuthentication.AVAILABLE_CHANNEL_BINDING_TYPES_FOR_SESSION, session, sessionData);
            return Optional.empty();
        }
        //noinspection unchecked
        return Optional.ofNullable((Set<String>) sessionData);
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
        Log.trace("handle() invoked: usingSASL2={}, element={}, session={}", usingSASL2, doc.getName(), session);
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
                session.setSessionData(SASL2_NEGOTIATION_IN_PROGRESS, Boolean.TRUE);
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

                    if (MechanismName.isFast(mechanismName) && !usingSASL2) {
                        throw new SaslFailureException(Failure.INVALID_MECHANISM,
                            "FAST mechanisms can only be negotiated with SASL2.");
                    }

                    // See if the mechanism is supported by configuration as well as by implementation.
                    if ( !SaslMechanismCatalog.isEnabled(mechanismName)
                        && !(FastTokenManager.ENABLE_FAST.getValue() && MechanismName.isFast(mechanismName)) )
                    {
                        throw new SaslFailureException( Failure.INVALID_MECHANISM, "The configuration of Openfire does not contain or allow the mechanism." );
                    }

                    // Enforce session-specific eligibility (as advertised in stream features) See OF-3273.
                    final Set<String> advertisedMechanisms = (MechanismName.isFast(mechanismName)
                        ? FastSessionState.getAdvertisedMechanisms(session) : getAdvertisedSASLMechanisms(session))
                        .orElseThrow(() -> {
                            Log.warn("No advertised SASL mechanisms detected for session. This can happen if SASL authentication is attempted before the applicable mechanism names are advertised, or if the session has not properly recorded the SASL mechanism names that are advertised to it. Both suggest a bug in Openfire (or possible the client). Affected session: {}", session);
                            return new SaslFailureException(Failure.INVALID_MECHANISM, "The mechanism is not available for this session.");
                        });

                    if ( !advertisedMechanisms.contains( mechanismName ) )
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

                    saslServer = Sasl.createSaslServer( mechanismName, "xmpp", serverName, props, new XMPPCallbackHandler(session) );
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

                    // Clear the reservation of any earlier password attempt, so that this attempt cannot inherit it.
                    session.removeSessionData(SASL_LOGIN_ATTEMPT);
                    // Clear any unexecuted bind2-request
                    session.removeSessionData("bind2-request");
                    session.removeSessionData("user-agent-info");
                    session.removeSessionData(SASL2_RESUME_REQUEST);
                    // Clear the outcome of any preceding attempt, so that a mechanism without channel binding cannot
                    // observe the channel binding type of an earlier one.
                    session.removeSessionData("SaslMechanism");
                    session.removeSessionData("ChannelBindingType");
                    FastSessionState.clearRequest(session);
                    Sasl2TaskManager.getInstance().reset(session);

                    if (usingSASL2 && session instanceof LocalClientSession clientSession) {
                        UserAgentInfo userAgentInfo = null;
                        Element userAgentElement = doc.element("user-agent");
                        if (userAgentElement != null) {
                            userAgentInfo = UserAgentInfo.extract(userAgentElement);
                            if (userAgentInfo != null) {
                                // Store the user agent info in the session
                                session.setSessionData("user-agent-info", userAgentInfo);
                            }
                        }

                        // XEP-0484 § 3.1 & § 3.2: one of several requests related to Fast Authentication Streamlining Tokens.
                        final FastRequest fastRequest = FastRequest.from(doc, mechanismName, userAgentInfo == null ? null : userAgentInfo.getId(), clientSession);
                        if (fastRequest != null) {
                            fastRequest.applyTo(session);
                        }

                        // XEP-0198 § 9.2: an inline stream resumption request.
                        final ResumeRequest resumeRequest;
                        try {
                            resumeRequest = ResumeRequest.fromSasl2Authenticate(doc);
                        } catch (final MalformedResumeRequestException e) {
                            throw new SaslFailureException(Failure.MALFORMED_REQUEST, e.getMessage());
                        }
                        if (resumeRequest != null) {
                            session.setSessionData(SASL2_RESUME_REQUEST, resumeRequest);
                        }

                        // XEP-0386 § 3.2: a resource binding request.
                        final Bind2Request bind2Request = Bind2Request.from(doc);
                        if (bind2Request != null) {
                            session.setSessionData("bind2-request", bind2Request);
                        }

                        // XEP-0388 § 2.5: let task providers record any task-related request that the peer inlined.
                        Sasl2TaskManager.getInstance().onAuthenticateElement(session, doc, mechanismName);
                    }

                    // intended fall-through
                case RESPONSE:
                    if ( saslServer == null )
                    {
                        if ( usingSASL2 && Sasl2TaskManager.getInstance().getNegotiation( session ) != null )
                        {
                            // The SASL exchange has completed and a task negotiation is in progress. A <response/>
                            // is not part of that flow. This is a peer-triggerable condition, so it is reported as a
                            // protocol error rather than as an unexpected internal state.
                            throw new SaslFailureException( Failure.MALFORMED_REQUEST, "A <response/> element was received while a SASL2 task negotiation is in progress." );
                        }
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
                        SaslOutcome.sendChallenge( session, challenge, usingSASL2 );
                        return Status.needResponse;
                    }

                    // Password mechanisms check the lock when the username becomes known; that check is reused here
                    // rather than repeated, as LockOutManager does not cache that an account is not locked (OF-2279).
                    // Other mechanisms, and an authorization identity other than the username, are checked here.
                    if (saslServer.getAuthorizationID() != null && !isKnownNotLocked(session, saslServer.getAuthorizationID())
                        && LockOutManager.getInstance().isAccountDisabled(saslServer.getAuthorizationID())) {
                        // Interception!  This person is locked out, fail instead!
                        Log.info("SASL authentication for account '{}' succeeded, but the account is locked out. Failing the authentication attempt for session '{}'.", saslServer.getAuthorizationID(), session);
                        LockOutManager.getInstance().recordFailedLogin(saslServer.getAuthorizationID());
                        // A password mechanism that found the lock when the username became known has refused already,
                        // with account-disabled, before checking the password. Reaching this point after a password
                        // exchange (the early check could not be completed, the lock was set during the exchange, or the
                        // authorization identity is another account) means the password was right, so the answer must
                        // be the same as for a wrong one. With no password exchange (EXTERNAL), it reveals nothing.
                        throw new SaslFailureException(session.getSessionData(SASL_LOGIN_ATTEMPT) instanceof UserLoginLimitManager.Reservation
                            ? Failure.NOT_AUTHORIZED : Failure.ACCOUNT_DISABLED);
                    }
                    recordSuccessfulPasswordAttempt(session);

                    // The SASL exchange itself has succeeded. Record its outcome on the session before any
                    // post-authentication task runs: tasks are allowed to inspect the mechanism and channel binding.
                    session.removeSessionData( "SaslServer" );
                    session.removeSessionData( SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY );
                    session.setSessionData("SaslMechanism", saslServer.getMechanismName());
                    if (MechanismName.requiresChannelBinding(saslServer.getMechanismName())) {
                        session.setSessionData("ChannelBindingType", saslServer.getNegotiatedProperty(ScramSaslServer.PROPNAME_CHANNELBINDINGTYPE));
                    }

                    // XEP-0388 § 2.5: one or more tasks may have to be performed before authentication can be
                    // reported as successful. When a task is offered, a <continue/> has been sent (carrying the
                    // mechanism's success data) and the negotiation is suspended until the peer selects a task.
                    // Nothing that marks the session as authenticated may happen until every task has completed.
                    if (usingSASL2 && Sasl2TaskManager.getInstance().offerTasks(session, saslServer.getAuthorizationID(), saslServer.getMechanismName(), challenge)) {
                        return Status.needResponse;
                    }

                    // Check before calling authenticationSuccessful whether a Bind2 request is pending; if so, the
                    // response and stream features will be delivered asynchronously.
                    final boolean hasBind2Request = usingSASL2 && session.getSessionData("bind2-request") != null;
                    authenticationSuccessful( session, saslServer.getAuthorizationID(), saslServer.getMechanismName(), challenge, usingSASL2 );

                    if (usingSASL2 && session.getSessionData(SASL2_RESUMED_SESSION) != null) {
                        // XEP-0198 § 9.2: the session was resumed inline. The <success/> (with <resumed/>) has
                        // already been delivered, over the resumed session, by authenticationSuccessful(). The
                        // caller must adopt that session and must not send stream features.
                        return Status.authenticatedResumed;
                    }
                    return hasBind2Request ? Status.authenticatedAwaitingFeatures : Status.authenticated;

                case NEXT: // intended fall-through
                case TASK_DATA:
                    if ( !usingSASL2 )
                    {
                        throw new IllegalStateException( "Unexpected data received while negotiating SASL authentication. SASL2 tasks require SASL2. Name of the offending root element: " + doc.getName() + " Namespace: " + doc.getNamespaceURI() );
                    }
                    if ( Sasl2TaskManager.getInstance().handleTaskElement( session, doc ) == Sasl2TaskManager.Outcome.AWAITING_PEER )
                    {
                        return Status.needResponse;
                    }
                    return completeSasl2AfterTasks( session );

                default:
                    throw new IllegalStateException( "Unexpected data received while negotiating SASL authentication. Name of the offending root element: " + doc.getName() + " Namespace: " + doc.getNamespaceURI() );
            }
        }
        catch ( SaslException ex )
        {
            Log.debug( "SASL negotiation failed for session: {}", session, ex );
            final Failure failure = failureOf( ex );

            // The attempt was counted when it was admitted, so a failure only clears its reservation.
            session.removeSessionData(SASL_LOGIN_ATTEMPT);
            if (usingSASL2) {
                abortSasl2(session, failure);
            } else {
                SaslOutcome.authenticationFailed(session, failure, usingSASL2);
                session.removeSessionData("SaslServer");
            }
            return Status.failed;
        }
        catch( Exception ex )
        {
            Log.warn( "An unexpected exception occurred during SASL negotiation. Affected session: {}", session, ex );
            session.removeSessionData(SASL_LOGIN_ATTEMPT);
            if (usingSASL2) {
                abortSasl2(session, Failure.NOT_AUTHORIZED);
            } else {
                SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, usingSASL2);
                session.removeSessionData("SaslServer");
            }
            return Status.failed;
        }
    }

    /**
     * Returns the failure to answer a SASL exception with: that of the exception, or of the first exception in its
     * causes that carries one (a mechanism wraps an exception thrown by its callback handler, such as the refusal of
     * {@link #admitPasswordAttempt(LocalSession, String)}); or not-authorized if none does.
     */
    @Nonnull
    public static Failure failureOf(@Nonnull final SaslException ex)
    {
        // Bounded, so that a cycle in the causes cannot loop forever.
        Throwable cause = ex;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof SaslFailureException failureException && failureException.getFailure() != null) {
                return failureException.getFailure();
            }
        }
        return Failure.NOT_AUTHORIZED;
    }

    /**
     * Returns the failure to refuse an attempt with, once the user login limits have refused it: account-disabled when
     * the username is locked out from every IP address (its account is locked, or the per-username limit has blocked
     * it), and otherwise not-authorized, the same answer as for a wrong password. Either way the answer does not depend
     * on the password, which has not been checked.
     *
     * @param username the username of the refused attempt.
     * @return the failure to answer the attempt with.
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2279">OF-2279: Login limits for user accounts</a>
     */
    @Nonnull
    public static Failure refusalFailure(@Nonnull final String username)
    {
        try {
            return UserLoginLimitManager.getInstance().isLockedOut(username) ? Failure.ACCOUNT_DISABLED : Failure.NOT_AUTHORIZED;
        } catch (final RuntimeException e) {
            Log.debug("Unable to determine whether {} is locked out; answering its refused attempt not-authorized.", UserLoginLimitManager.forLog(username), e);
            return Failure.NOT_AUTHORIZED;
        }
    }

    /**
     * Applies the user login limits to a password authentication attempt, as soon as its mechanism knows the username
     * and before the credentials are evaluated. If the account is locked, or a login limit blocks the attempt, the
     * attempt is refused without its password being checked: with account-disabled if the username is locked out from
     * every IP address, and otherwise with not-authorized (see {@link #refusalFailure(String)}). Otherwise, with a session, the
     * attempt is counted against the limits (see {@link UserLoginLimitManager#tryAcquireAttempt(String, String)}) and
     * its reservation is recorded on the session (see {@link #SASL_LOGIN_ATTEMPT}), so that a success can give the
     * attempt back; an attempt that does not succeed, for whatever reason, stays counted. Without a session a success
     * could not give the attempt back, so the attempt is checked but not counted.
     *
     * Password mechanisms (PLAIN, DIGEST-MD5, SCRAM) call this; mechanisms whose failures are not counted check
     * {@link UserLoginLimitManager#isLoginAllowed(String, String)} themselves.
     *
     * @param session the session that is authenticating, or null if the mechanism has none (then the attempt is checked
     *                without an address, and not counted).
     * @param username the username the peer provided.
     * @throws SaslFailureException with {@link Failure#ACCOUNT_DISABLED} or {@link Failure#NOT_AUTHORIZED} if the
     *                              attempt is refused.
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2279">OF-2279: Login limits for user accounts</a>
     */
    public static void admitPasswordAttempt(@Nullable final LocalSession session, @Nonnull final String username) throws SaslFailureException
    {
        final UserLoginLimitManager loginLimits = UserLoginLimitManager.getInstance();
        if (session == null) {
            if (!loginLimits.isAttemptAllowed(username, null)) {
                Log.debug("Refusing the authentication attempt for '{}' before evaluating its credentials, as the account is locked or a login limit applies.", UserLoginLimitManager.forLog(username));
                throw new SaslFailureException(refusalFailure(username), "The account is locked or a login limit applies.");
            }
            return;
        }
        session.removeSessionData(SASL_LOGIN_ATTEMPT);
        final UserLoginLimitManager.Reservation reservation = loginLimits.tryAcquireAttempt(username, getHostAddress(session));
        if (reservation == null) {
            Log.debug("Refusing the authentication attempt for '{}' before evaluating its credentials, as the account is locked or a login limit applies. Session: {}", UserLoginLimitManager.forLog(username), session);
            throw new SaslFailureException(refusalFailure(username), "The account is locked or a login limit applies.");
        }
        session.setSessionData(SASL_LOGIN_ATTEMPT, reservation);
    }

    /**
     * Returns the address of the peer of a session, for the user login limits, or null when it cannot be determined
     * (in which case only the per-username limit applies).
     *
     * @param session the session, or null.
     * @return the peer's address, or null.
     */
    @Nullable
    public static String getHostAddress(@Nullable final LocalSession session)
    {
        if (session == null) {
            return null;
        }
        try {
            return session.getHostAddress();
        } catch (final UnknownHostException | RuntimeException e) {
            Log.debug("Unable to determine the IP address of session '{}'; only the per-username login limit applies to it.", session, e);
            return null;
        }
    }

    /**
     * Returns whether this attempt's password mechanism already found, when the username became known, that the
     * account is not locked (see {@link UserLoginLimitManager#isKnownNotLocked(UserLoginLimitManager.Reservation, String)}),
     * so that the check after a successful exchange need not look it up again. False when no such check was made in
     * this attempt (for example for EXTERNAL), or it was for another username.
     */
    private static boolean isKnownNotLocked(@Nonnull final LocalSession session, @Nonnull final String username)
    {
        return session.getSessionData(SASL_LOGIN_ATTEMPT) instanceof UserLoginLimitManager.Reservation reservation
            && UserLoginLimitManager.getInstance().isKnownNotLocked(reservation, username);
    }

    /**
     * Records a successful password authentication against the user login limits, and clears the attempted username
     * from the session.
     */
    private static void recordSuccessfulPasswordAttempt(@Nonnull final LocalSession session)
    {
        final Object reservation = session.removeSessionData(SASL_LOGIN_ATTEMPT);
        if (reservation instanceof UserLoginLimitManager.Reservation admitted) {
            UserLoginLimitManager.getInstance().recordSuccessfulLogin(admitted);
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
            Log.trace("SASL2 is not permitted for session '{}': SASL2 is disabled by configuration.", session);
            return Optional.of(Failure.NOT_AUTHORIZED);
        }
        if (SASL2_REQUIRE_TLS.getValue() && !session.isEncrypted()) {
            Log.trace("SASL2 is not permitted for session '{}': TLS is required for SASL2, but the session is not encrypted.", session);
            return Optional.of(Failure.ENCRYPTION_REQUIRED);
        }
        return Optional.empty();
    }

    /**
     * Returns whether a SASL2 negotiation is currently in progress for the given session, per
     * {@link #SASL2_NEGOTIATION_IN_PROGRESS}.
     *
     * @param session the session to check (cannot be null).
     * @return true if a SASL2 negotiation is in progress, false otherwise.
     */
    public static boolean isSasl2NegotiationInProgress(@Nonnull final LocalSession session)
    {
        return Boolean.TRUE.equals(session.getSessionData(SASL2_NEGOTIATION_IN_PROGRESS));
    }

    /**
     * Concludes a SASL2 negotiation whose tasks have all completed: applies the deferred authentication outcome,
     * which delivers {@code <success/>} and performs any inlined Bind2 or XEP-0198 resumption.
     *
     * @param session the session that is authenticating (cannot be null).
     * @return the status of the negotiation.
     */
    private static Status completeSasl2AfterTasks(@Nonnull final LocalSession session)
    {
        final Sasl2Negotiation negotiation = Sasl2TaskManager.getInstance().endNegotiation(session, true);
        if (negotiation == null) {
            Log.warn("Unable to conclude the SASL2 negotiation of session '{}': no task negotiation state is present.", session);
            abortSasl2(session, Failure.TEMPORARY_AUTH_FAILURE);
            return Status.failed;
        }
        try {
            final boolean hasBind2Request = session.getSessionData("bind2-request") != null;
            authenticationSuccessful(session, negotiation.getAuthorizationIdentity(), negotiation.getSaslMechanismName(), negotiation.getFinalAdditionalData(), true);

            if (session.getSessionData(SASL2_RESUMED_SESSION) != null) {
                return Status.authenticatedResumed;
            }
            return hasBind2Request ? Status.authenticatedAwaitingFeatures : Status.authenticated;
        } catch (final Exception e) {
            Log.warn("An exception occurred while concluding the SASL2 negotiation of session '{}' after its tasks completed.", session, e);
            abortSasl2(session, Failure.TEMPORARY_AUTH_FAILURE);
            return Status.failed;
        }
    }

    /**
     * Delivers the stream features that apply once a session has authenticated.
     *
     * @param clientSession the session (cannot be null).
     */
    private static void deliverPostAuthenticationStreamFeatures(@Nonnull final LocalClientSession clientSession)
    {
        final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
        final List<Element> specificFeatures = clientSession.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            specificFeatures.forEach(features::add);
        }
        clientSession.deliverRawText(features.asXML());
    }

    /**
     * Verifies that the given X.509 certificate is valid for the specified hostname. The certificate's
     * server identities are checked against the hostname, with support for wildcard certificates.
     * A wildcard identity (e.g. {@code *.example.com}) matches any direct subdomain of the base domain.
     *
     * @param trustedCert the X.509 certificate to verify (cannot be null)
     * @param hostname    the hostname to verify the certificate against (cannot be null)
     * @return {@code true} if the certificate is valid for the given hostname; {@code false} otherwise
     * @deprecated Moved to {@link CertificateManager#verifyCertificate(X509Certificate, String)}
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public static boolean verifyCertificate(X509Certificate trustedCert, String hostname)
    {
        return CertificateManager.verifyCertificate(trustedCert, hostname);
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
     * @deprecated Moved to {@link CertificateManager#verifyCertificates(Certificate[], String, boolean)}
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public static boolean verifyCertificates(Certificate[] chain, String hostname, boolean isS2S)
    {
        return CertificateManager.verifyCertificates(chain, hostname, isS2S);
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
    static void authenticationSuccessful(final LocalSession session, final String username, final String mechanismName, final byte[] successData, final boolean usingSASL2)
    {
        // The identity to report back to the peer. For clients this is a bare JID; for anonymous clients, the node-part is
        // the session's generated resource (see LocalClientSession#getAnonymousUsername). Must be resolved before the
        // session transitions to an authenticated state.
        final String authorizationIdentity;
        final AuthToken clientAuthToken;

        if (session instanceof LocalClientSession clientSession) {
            final String node;
            if (username == null) {
                node = clientSession.getAnonymousUsername();
                clientAuthToken = AuthToken.generateAnonymousToken();
            } else {
                clientAuthToken = AuthToken.generateUserToken(username);
                node = clientAuthToken.getUsername(); // Normalized: strips any domain-part from the authzid.
            }
            authorizationIdentity = new JID(node, XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null, true).toString();
            // Do not retain an authentication token until all synchronous SASL2 success work,
            // including requested FAST token persistence, has completed successfully.
            if (!usingSASL2) {
                clientSession.setAuthToken(clientAuthToken);
            }
        }
        else if (session instanceof LocalIncomingServerSession serverSession) {
            clientAuthToken = null;
            authorizationIdentity = username;
            serverSession.addValidatedDomain(username);
            serverSession.setAuthenticationMethod(ServerSession.AuthenticationMethod.fromSaslMechanismName(mechanismName));
            Log.info("Inbound Server {} authenticated using SASL mechanism {}", username, mechanismName);
        }
        else {
            clientAuthToken = null;
            authorizationIdentity = username;
        }

        if (!usingSASL2) {
            Log.debug("Sending SASL success response for user '{}'.", username);
            SaslOutcome.sendSuccess(session, successData);
            return;
        }

        // The remainder of this method is specific to SASL2.
        Log.debug("Processing SASL2 request for user '{}'.", username);
        if (session instanceof LocalClientSession clientSession) {
            // XEP-0484: determine if a FAST token should be issued.
            // A token is issued when:
            //   (a) the client included <request-token> with a valid mechanism, OR
            //   (b) this was a FAST authentication and invalidate was NOT requested (token rotation).
            // If invalidate=true was requested, delete the existing token and do not rotate.
            final boolean fastInvalidate = FastSessionState.isInvalidateRequested(session);
            final String fastRequestedMechanism = FastSessionState.getRequestedMechanism(session);
            final boolean isFastAuth = MechanismName.isFast(mechanismName);
            final String authenticatedClientId = FastSessionState.getAuthenticatedClientId(session);
            final String requestingClientId = FastSessionState.getClientId(session);

            FastToken fastToken = null;
            if (fastInvalidate) {
                // Client requested token invalidation: delete the token used for this auth, do not rotate.
                if (username != null) {
                    if (isFastAuth && authenticatedClientId != null) {
                        FastTokenManager.invalidateToken(username, mechanismName, authenticatedClientId);
                        Log.debug("FAST token invalidated for user '{}' per client request.", username);
                    }
                }
                // Still issue a new token if the client also sent <request-token>.
                if (fastRequestedMechanism != null && username != null) {
                    fastToken = issueFastToken(username, requestingClientId, fastRequestedMechanism);
                    Log.debug("FAST token (re-)issued for user '{}' mechanism '{}' after invalidation+request.", username, fastRequestedMechanism);
                }
            } else if (fastRequestedMechanism != null && username != null) {
                // Client requested a new FAST token (e.g. during initial password auth).
                fastToken = issueFastToken(username, requestingClientId, fastRequestedMechanism);
                Log.debug("FAST token issued for user '{}' mechanism '{}'.", username, fastRequestedMechanism);
            } else if (isFastAuth && username != null) {
                // FAST authentication: the SaslServer already rotated the token internally;
                // retrieve the new token from the SaslServer's rotatedToken field if accessible,
                // or issue a fresh token here for inclusion in the <success/>.
                // The rotated token is stored by HtSaslServer/Ht2SaslServer via AbstractHtSaslServer.
                // We expose it via the "RotatedToken" session data key set by AbstractHtSaslServer.
                fastToken = FastSessionState.getRotatedToken(session);
                Log.debug("FAST token rotated for user '{}'.", username);
            }
            FastSessionState.clearAuthenticationAttempt(session);
            final FastToken finalFastToken = fastToken;

            // SASL authentication has completed, but resource binding has not (yet). This one-argument form
            // records the identity without transitioning the session to AUTHENTICATED, which is exactly the state
            // that inline XEP-0198 resumption (below) and StreamManager#allowResume() require.
            clientSession.setAuthToken(clientAuthToken);

            // XEP-0198 § 9.2: an inline resume request, if present, must be processed before any Bind2 request.
            Element resumeFailedElement = null;
            final ResumeRequest resumeRequest = (ResumeRequest) session.removeSessionData(SASL2_RESUME_REQUEST);
            if (resumeRequest != null) {
                Log.debug("Processing inline resume request for user '{}'.", username);
                final Sasl2ResumeResult resumeResult = clientSession.getStreamManager().processSasl2Resume(resumeRequest);
                if (resumeResult.isSuccess()) {
                    final LocalClientSession resumedSession = resumeResult.getResumedSession();
                    assert resumedSession != null; // Per contract of Sasl2ResumeResult
                    final JID resumedAddress = resumedSession.getAddress();
                    final Element success = SaslOutcome.buildSasl2SuccessElement(successData, resumedAddress.toBareJID(), resumedAddress.getResource(), finalFastToken);
                    success.add(resumeResult.getResultElement());

                    // Signal to the caller (typically StanzaHandler) that it must adopt the resumed session, and
                    // must not deliver a fresh set of post-authentication stream features (XEP-0198 § 9.2). This is
                    // recorded before the response is delivered: the connection now belongs to the resumed session,
                    // so the caller must adopt it even when delivering that response fails.
                    session.setSessionData(SASL2_RESUMED_SESSION, resumedSession);

                    // The connection has already been transferred to the resumed session (by processSasl2Resume(),
                    // through StreamManager and LocalSession#reattachForSasl2()). It must be delivered to, and only
                    // to, that session; the temporary session is being discarded.
                    try {
                        resumedSession.deliverRawText(success.asXML());
                        resumedSession.completeSasl2Resume(resumeRequest.getH());
                        session.removeSessionData(SASL2_NEGOTIATION_IN_PROGRESS);
                    } catch (final Exception e) {
                        // The connection is no longer the temporary session's to fail on: a SASL failure cannot be
                        // reported over it, and the resumed session cannot be left half-resumed. Close it instead.
                        Log.warn("An exception occurred while completing an inline stream resumption for user '{}'. Closing the resumed session.", username, e);
                        resumedSession.close(new StreamError(StreamError.Condition.internal_server_error, "An error occurred while resuming the stream."));
                    }

                    // If resumption succeeds, resource binding (and any inlined Bind2 request) is skipped entirely: a
                    // resumed session already has a resource bound.
                    Log.debug("Inline resume request for user '{}' processed successfully.", username);
                    if (session.getSessionData("bind2-request") != null) {
                        Log.debug("Inline resume for user '{}' succeeded; ignoring the inlined bind2 request per XEP-0198 §9.2.", username);
                    }
                    return;
                }
                // Resumption failed: fall through to the normal Bind2 (or plain) success flow below, embedding
                // the <failed/> element in the response, as required by XEP-0198 § 9.2.1.
                Log.debug("Inline resume request for user '{}' failed.", username);
                resumeFailedElement = resumeResult.getResultElement();
            }
            final Element finalResumeFailedElement = resumeFailedElement;

            // Resumption was not requested or has failed: fall through to the normal Bind2 (or plain) success flow.
            final Bind2Request bind2Request = (Bind2Request) session.getSessionData("bind2-request");
            if (bind2Request != null && clientSession.getStatus() != Session.Status.AUTHENTICATED) {
                Log.debug("Processing bind2 request for user '{}'.", username);
                clientSession.removeSessionData("bind2-request");
                final UserAgentInfo userAgentInfo = (UserAgentInfo) session.getSessionData("user-agent-info");
                final String resource = bind2Request.generateResourceString(userAgentInfo);
                final JID preBindAddress = clientSession.getAddress();

                if (clientAuthToken.isAnonymous()) {
                    // An anonymous session needs no conflict resolution: its node-part and resource are both the session's
                    // own generated identifier, so no other session can hold the same full JID. SessionManager#bindResource
                    // documents this and dereferences the (null) username, so it must not be used here. Note that this
                    // discards the resource that Bind2 generated; XEP-0386 leaves the assigned resource to the server.
                    clientSession.setAnonymousAuth();
                    final JID bound = clientSession.getAddress();
                    completeSasl2Bind2(clientSession, bind2Request, successData, finalFastToken, bound.toBareJID(), bound.getResource(), preBindAddress, finalResumeFailedElement);
                    Log.debug("Bind2 request for anonymous user '{}' processed successfully.", username);
                } else {
                    // A non-anonymous session performs regular resource binding.
                    final String bareJid = new JID(clientAuthToken.getUsername(), XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null, true).toString();
                    SessionManager.getInstance().bindResource(clientSession, clientAuthToken, resource)
                        .whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                Log.warn("An exception occurred while binding resource '{}' for session '{}' during SASL2+Bind2 authentication.", resource, clientSession, throwable);
                            }
                            if (throwable != null || result != SessionManager.BindResult.BOUND) {
                                Log.warn("Unable to bind resource '{}' for session '{}' during SASL2+Bind2 authentication. Bind result: {}", resource, clientSession, result);
                                abortSasl2(clientSession, Failure.TEMPORARY_AUTH_FAILURE);
                                return;
                            }
                            // bindResource() already installs the auth token (two-argument form, which also transitions the session to AUTHENTICATED); no need to set it again here.
                            completeSasl2Bind2(clientSession, bind2Request, successData, finalFastToken, bareJid, resource, preBindAddress, finalResumeFailedElement);
                            // OF-3442: Only now that the client has the result can it receive the stanzas of a detached session that the bind replaced.
                            SessionManager.getInstance().handleStanzasOfReplacedDetachedSession(clientSession);
                            Log.debug("Bind2 request for user '{}' processed successfully.", username);
                        });
                }
            } else {
                Log.debug("No bind2 request, or session already authenticated for user '{}'; sending <success/> synchronously without <bound/>.", username);
                final Element success = SaslOutcome.buildSasl2SuccessElement(successData, authorizationIdentity, null, finalFastToken);
                if (finalResumeFailedElement != null) {
                    success.add(finalResumeFailedElement);
                }
                session.deliverRawText(success.asXML());
                session.removeSessionData(SASL2_NEGOTIATION_IN_PROGRESS);
            }
        } else {
            Log.debug("Non-client session (e.g. server) for user '{}'; sending <success/> synchronously.", username);
            final Element success = SaslOutcome.buildSasl2SuccessElement(successData, authorizationIdentity, null, null);
            session.deliverRawText(success.asXML());
            session.removeSessionData(SASL2_NEGOTIATION_IN_PROGRESS);
        }
    }

    private static FastToken issueFastToken(final String username, final String clientId, final String mechanism) {
        // A request that was accepted is part of the SASL2 operation. Do not report authentication
        // success when the requested credential could not be created and persisted.
        return FastTokenManager.issueToken(username, clientId, mechanism);
    }

    /**
     * Aborts the SASL2 authentication process for a given session and handles the failure scenario.
     *
     * @param session The LocalSession object representing the session. Must not be null.
     * @param failure The Failure object representing the reason for the authentication failure. Must not be null.
     */
    private static void abortSasl2(@Nullable final LocalSession session, @Nonnull final Failure failure)
    {
        if (session == null) {
            Log.warn("Unable to report SASL2 failure ({}): session is unexpectedly null.", failure);
            return;
        }
        Log.debug("Aborting SASL2 negotiation for session '{}' with failure: {}.", session, failure);
        if (session instanceof LocalClientSession clientSession) {
            clientSession.setAuthToken(null);
        }
        session.removeSessionData("bind2-request");
        session.removeSessionData("user-agent-info");
        session.removeSessionData(SASL2_RESUME_REQUEST);
        session.removeSessionData(SASL2_RESUMED_SESSION);
        // OF-3362: a failed Bind2 bind was never reported to the peer as <success/>, so it must not be left looking
        // like a completed negotiation - see SASL2_BIND2_PENDING_OR_SUCCEEDED.
        session.removeSessionData(SASL2_BIND2_NEGOTIATION_ACTIVE_OR_DONE);
        session.removeSessionData("SaslServer");
        session.removeSessionData(SASL_LOGIN_ATTEMPT);
        session.removeSessionData(SASL2_NEGOTIATION_IN_PROGRESS);
        Sasl2TaskManager.getInstance().endNegotiation(session, false);
        FastSessionState.clearAuthenticationAttempt(session);
        SaslOutcome.authenticationFailed(session, failure, true);
    }

    /**
     * Completes a SASL2 negotiation for which a resource has been bound: renders and delivers {@code <success/>},
     * then the post-authentication stream features.
     * <p>
     * Failure is handled differently either side of the {@code <success/>} write. Before it, the peer has not been
     * told anything, so the bind is undone and the negotiation fails. After it, authentication genuinely succeeded
     * and the session is live and routable. A failure then is a stream-level problem rather than a SASL one.
     *
     * @param clientSession The LocalClientSession object representing the client session. Must not be null.
     * @param bind2Request The Bind2Request object representing the bind request. Must not be null.
     * @param successData The byte array representing the success data.
     * @param fastToken The FastToken if one was issued.
     * @param authorizationIdentity the bare JID authorization identity (e.g. user@domain or uuid@domain for anonymous).
     * @param resource the bound resource, or null if no resource was bound.
     * @param preBindAddress The session's address prior to the binding attempt. Must not be null.
     * @param resumeFailedElement the {@code <failed/>} element from a failed inline XEP-0198 resume attempt that
     *                            preceded this bind, or {@code null} if no resume was attempted.
     */
    private static void completeSasl2Bind2(@Nonnull final LocalClientSession clientSession,
                                           @Nonnull final Bind2Request bind2Request,
                                           final byte[] successData,
                                           final FastToken fastToken,
                                           final String authorizationIdentity,
                                           final String resource,
                                           @Nonnull final JID preBindAddress,
                                           @Nullable final Element resumeFailedElement)
    {
        boolean successDelivered = false;
        try
        {
            final Element success = SaslOutcome.buildSasl2SuccessElement(successData, authorizationIdentity, resource, fastToken);
            if (resumeFailedElement != null) {
                // XEP-0198 § 9.2.1: a failed inline resume is reported alongside (and before) <bound/>.
                success.add(resumeFailedElement);
            }
            bind2Request.processFeatureRequests(clientSession, success);
            clientSession.deliverRawText(success.asXML());
            successDelivered = true;
            clientSession.removeSessionData(SASL2_NEGOTIATION_IN_PROGRESS);

            clientSession.setSessionData(BIND2_USED, Boolean.TRUE);
            // Note: SASL2_BIND2_NEGOTIATION_ACTIVE_OR_DONE was already set by StanzaHandler before this method ran;
            // it is not cleared here because success, not just this bind's completion, is what leaves it set.
            if (!bind2Request.getNegotiatedFeatureNamespaces().isEmpty()) {
                clientSession.setSessionData(BIND2_INLINE_FEATURES, bind2Request.getNegotiatedFeatureNamespaces());
            }

            SessionEventDispatcher.dispatchEvent(clientSession, SessionEventDispatcher.EventType.resource_bound);

            // Deliver stream features now that <success/> has been sent.
            deliverPostAuthenticationStreamFeatures(clientSession);
        }
        catch (final Exception e)
        {
            if (successDelivered) {
                Log.warn("An exception occurred after SASL2+Bind2 success was delivered to '{}'. The session is authenticated and bound, so it is closed with a stream error rather than failed.", clientSession, e);
                clientSession.close(new StreamError(StreamError.Condition.internal_server_error, "An error occurred while completing resource binding."));
            } else {
                Log.warn("An exception occurred while processing SASL2+Bind2 for '{}'. Undoing the resource binding.", clientSession, e);
                unwindBind(clientSession, preBindAddress);
                abortSasl2(clientSession, Failure.TEMPORARY_AUTH_FAILURE);
            }
        }
    }

    /**
     * Reverses the session state installed by a successful resource binding, returning the session to the
     * pre-binding state in which another SASL2 negotiation can be attempted.
     *
     * @param clientSession The LocalClientSession object representing the client session. Must not be null.
     * @param preBindAddress The session's address prior to the binding attempt. Must not be null.
     */
    private static void unwindBind(@Nonnull final LocalClientSession clientSession, @Nonnull final JID preBindAddress)
    {
        // removeSession reads the auth token to decide which session-destroyed event to fire, so it must run before
        // abortSasl2 clears that token - otherwise a named session is reported as an anonymous one.
        SessionManager.getInstance().removeSession(clientSession);
        clientSession.setStatus(Session.Status.CONNECTED);
        clientSession.setAddress(preBindAddress);
    }

    /**
     * Adds a new SASL mechanism to the list of supported SASL mechanisms by the server. The
     * new mechanism will be offered to clients and connection managers as stream features.<p>
     * <p>
     * Note: this method simply registers the SASL mechanism to be advertised as a supported
     * mechanism by Openfire. Actual SASL handling is done by Java itself, so you must add
     * the provider to Java.
     *
     * @param mechanismName the name of the new SASL mechanism (cannot be null or an empty String).
     * @deprecated Moved to {@link SaslMechanismCatalog#addSupportedMechanism(String)}
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public static void addSupportedMechanism(String mechanismName) {
        SaslMechanismCatalog.addSupportedMechanism(mechanismName);
    }

    /**
     * Removes a SASL mechanism from the list of supported SASL mechanisms by the server.
     *
     * @param mechanismName the name of the SASL mechanism to remove (cannot be null or empty, not case-sensitive).
     * @deprecated Moved to {@link SaslMechanismCatalog#removeSupportedMechanism(String)}
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public static void removeSupportedMechanism(String mechanismName) {
        SaslMechanismCatalog.removeSupportedMechanism(mechanismName);
    }

    /**
     * Returns the list of supported SASL mechanisms by the server. Note that Java may have
     * support for more mechanisms but some of them may not be returned since a special setup
     * is required that might be missing. Use {@link SaslMechanismCatalog#addSupportedMechanism(String)} to add
     * new SASL mechanisms.
     *
     * @return the set of supported SASL mechanisms by the server.
     * @deprecated Moved to {@link SaslMechanismCatalog#getSupportedMechanisms()}
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public static Set<String> getSupportedMechanisms() {
        return SaslMechanismCatalog.getSupportedMechanisms();
    }

    /**
     * Returns a collection of mechanism names for which the JVM has an implementation available.
     * <p>
     * Note that this need not (and likely will not) correspond with the list of mechanisms that is offered to XMPP
     * peer entities, which is provided by #getSupportedMechanisms.
     *
     * @return a collection of SASL mechanism names (never null, possibly empty)
     * @deprecated Moved to {@link SaslMechanismCatalog#getImplementedMechanisms()}
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public static Set<String> getImplementedMechanisms()
    {
        return SaslMechanismCatalog.getImplementedMechanisms();
    }

    /**
     * Returns a collection of SASL mechanism names that forms the source pool from which the mechanisms that are
     * eventually being offered to peers are obtained.
     *
     * When a mechanism is not returned by this method, it will never be offered, but when a mechanism is returned
     * by this method, there is no guarantee that it will be offered.
     *
     * Apart from being returned in this method, an implementation must be available (see {@link SaslMechanismCatalog#getImplementedMechanisms()}
     * and configuration or other characteristics of this server must not prevent a particular mechanism from being
     * used (see @{link {@link SaslMechanismCatalog#getSupportedMechanisms()}}.
     *
     * @return A collection of mechanisms that are considered for use in this instance of Openfire.
     * @deprecated Moved to {@link SaslMechanismCatalog#getEnabledMechanisms()}
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public static List<String> getEnabledMechanisms()
    {
        return SaslMechanismCatalog.getEnabledMechanisms();
    }

    /**
     * Sets the collection of mechanism names that the system administrator allows to be used.
     *
     * @param mechanisms A collection of mechanisms that are considered for use in this instance of Openfire. Null to reset the default setting.
     * @see SaslMechanismCatalog#getEnabledMechanisms()
     * @deprecated Moved to {@link SaslMechanismCatalog#setEnabledMechanisms(List)}
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public static void setEnabledMechanisms( List<String> mechanisms )
    {
        SaslMechanismCatalog.setEnabledMechanisms(mechanisms);
    }
}
