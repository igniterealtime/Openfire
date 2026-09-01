/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.fast.FastToken;
import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.fast.FastSessionState;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract base class shared by FAST HT-* and HT2-* SASL server implementations.
 *
 * <p>Provides the common state ({@code complete}, {@code authorizationId}, {@code rotatedToken}),
 * the boilerplate {@link SaslServer} methods that are identical across all HT variants
 * ({@link #isComplete()}, {@link #getAuthorizationID()}, {@link #getRotatedToken()},
 * {@link #unwrap}, {@link #wrap}, {@link #getNegotiatedProperty}, {@link #dispose}), and the
 * shared {@link #evaluateResponse(byte[])} entry-point which handles guard checks and channel-
 * binding resolution before delegating to the mechanism-specific
 * {@link #doEvaluateResponse(byte[], byte[])} hook.</p>
 *
 * <p>Concrete subclasses must implement {@link #getMechanismName()} and
 * {@link #doEvaluateResponse(byte[], byte[])}.</p>
 *
 * HT-09 and HT2 share HMAC verification, mutual authentication and channel-binding handling.
 * HT2 additionally carries authenticated key/value fields and wraps the responder proof in its
 * extended response framing.
 */
abstract class AbstractHtSaslServer implements SaslServer {

    private static final Logger Log = LoggerFactory.getLogger(AbstractHtSaslServer.class);

    /** The SASL mechanism name (e.g. {@code HT-SHA-256-NONE} or {@code HT2-SHA-512-EXPR}). */
    protected final String mechanismName;

    /**
     * The SASL properties map; must contain a {@link LocalSession} under {@code LocalSession.class.getCanonicalName()}
     */
    protected final Map<String, ?> props;
    protected final HashedTokenValidator tokenValidator;

    protected boolean complete = false;
    protected String authorizationId = null;
    protected FastToken rotatedToken = null;

    protected final void recordAuthenticatedClient(final String clientId) {
        final Object value = props.get(LocalSession.class.getCanonicalName());
        if (clientId != null && value instanceof LocalSession session) {
            FastSessionState.setAuthenticatedClientId(session, clientId);
        }
    }

    /**
     * Constructs an {@code AbstractHtSaslServer} with the given mechanism name and properties map.
     *
     * @param mechanismName the SASL mechanism name (cannot be null)
     * @param props         the SASL properties map (cannot be null)
     */
    protected AbstractHtSaslServer(@Nonnull final String mechanismName, @Nonnull final Map<String, ?> props) {
        this(mechanismName, props, (username, mechanism, proof, cb, initiator, responder) -> {
            final Object value = props.get(LocalSession.class.getCanonicalName());
            final LocalSession session = value instanceof LocalSession s ? s : null;
            final String expectedUsername = session != null ? FastSessionState.getExpectedUsername(session) : null;
            if (expectedUsername == null || !expectedUsername.equals(username)) {
                Log.debug("Rejecting FAST authentication. Token claims different username ('{}') than stream's 'from' header ('{}').", username, expectedUsername);
                throw new SaslFailureException("Invalid FAST token", null, Failure.NOT_AUTHORIZED);
            }
            // Check the claimed identity before the lockout check: otherwise an authcid that does not match
            // the stream's 'from' could be used to record failed logins against an arbitrary account.
            if (LockOutManager.getInstance().isAccountDisabled(username)) {
                LockOutManager.getInstance().recordFailedLogin(username);
                Log.debug("Rejecting FAST authentication for disabled account '{}'.", username);
                throw new SaslFailureException("Invalid FAST token", null, Failure.NOT_AUTHORIZED);
            }
            final Long replayCount = FastSessionState.getReplayCount(session);
            final String clientId = FastSessionState.getClientId(session);
            if (clientId == null) {
                return null;
            }
            return FastTokenManager.validateTokenHt2(username, clientId, mechanism, proof, cb, initiator, responder, replayCount);
        });
    }

    protected AbstractHtSaslServer(@Nonnull final String mechanismName, @Nonnull final Map<String, ?> props,
                                   @Nonnull final HashedTokenValidator tokenValidator) {
        this.mechanismName = mechanismName;
        this.props = props;
        this.tokenValidator = tokenValidator;
    }

    @FunctionalInterface
    interface HashedTokenValidator {
        FastTokenManager.Ht2ValidationResult validate(String username, String mechanism, byte[] proof,
            byte[] channelBindingData, String initiatorValues, String responderValues) throws SaslException;
    }

    @Override
    public String getMechanismName() {
        return mechanismName;
    }

    /**
     * Evaluates the client's response.
     *
     * <p>This method handles the common guard checks (already-complete, null/empty response) and
     * resolves channel-binding data for the mechanism's CB variant before delegating to
     * {@link #doEvaluateResponse(byte[], byte[])}.</p>
     *
     * @param response the client response bytes
     * @return mechanism-specific success bytes (the responder HMAC for HT-*, or the framed
     * responder proof for HT2-*)
     * @throws SaslException if authentication fails
     */
    @Override
    public final byte[] evaluateResponse(final byte[] response) throws SaslException {
        if (complete) {
            throw new SaslException("Authentication already complete");
        }
        if (response == null || response.length == 0) {
            final SaslException failure = new SaslException(mechanismName + ": empty initiator message");
            if (mechanismName.startsWith("HT2-")) {
                throw new SaslFailureException(failure.getMessage(), failure, Failure.NOT_AUTHORIZED);
            }
            throw failure;
        }
        final byte[] result;
        final byte[] channelBindingData = resolveChannelBindingData();
        result = doEvaluateResponse(response, channelBindingData);
        // After successful evaluation, store the rotated token in the session so that
        // SASLAuthentication can include it in the SASL2 <success/> element (XEP-0484).
        if (complete && rotatedToken != null) {
            final LocalSession session = (LocalSession) props.get(LocalSession.class.getCanonicalName());
            if (session != null) {
                FastSessionState.setRotatedToken(session, rotatedToken);
            }
        }
        return result;
    }

    /**
     * Resolves the channel-binding data for this mechanism.
     *
     * <p>The channel-binding type is derived from the mechanism name suffix:
     * {@code -UNIQ} → {@code tls-unique}, {@code -ENDP} → {@code tls-server-end-point},
     * {@code -EXPR} → {@code tls-exporter}, {@code -NONE} → no channel binding (empty array).</p>
     *
     * <p>For non-NONE variants the server verifies that the required binding type is available,
     * retrieves the actual bytes from the live TLS session, and throws {@link SaslException} if
     * they cannot be obtained — matching the SCRAM-SHA-1-PLUS behaviour.</p>
     *
     * @return the channel-binding bytes (never null; empty array for NONE variants)
     * @throws SaslException if channel-binding data is required but cannot be retrieved
     */
    protected byte[] resolveChannelBindingData() throws SaslException {
        final String cbSuffix = mechanismName.substring(mechanismName.lastIndexOf('-') + 1);
        final String cbTypeName;
        switch (cbSuffix) {
            case "UNIQ": cbTypeName = "tls-unique"; break;
            case "ENDP": cbTypeName = "tls-server-end-point"; break;
            case "EXPR": cbTypeName = "tls-exporter"; break;
            case "NONE": cbTypeName = null; break;
            default: throw new SaslException(mechanismName + ": unknown channel-binding suffix");
        }
        if (cbTypeName == null) {
            return new byte[0];
        }
        final ChannelBindingProviderManager cbManager = ChannelBindingProviderManager.getInstance();
        if (!cbManager.supportsChannelBinding(cbTypeName)) {
            throw new SaslException(mechanismName + ": server does not support channel binding type '" + cbTypeName + "'");
        }
        final LocalSession session = (LocalSession) props.get(LocalSession.class.getCanonicalName());
        if (session == null || session.getConnection() == null) {
            throw new SaslException(mechanismName + ": local session not found in properties");
        }
        final Optional<byte[]> cbDataOpt = session.getConnection().getChannelBindingData(cbTypeName);
        if (cbDataOpt.isEmpty()) {
            Log.debug("{}: unable to retrieve channel binding data for '{}'. Rejecting authentication.", mechanismName, cbTypeName);
            throw new SaslException(mechanismName + ": unable to retrieve channel binding data for '" + cbTypeName + "'");
        }
        Log.debug("{}: channel binding data retrieved successfully for type '{}'", mechanismName, cbTypeName);
        return cbDataOpt.get();
    }

    /**
     * Performs the mechanism-specific evaluation of the client's response.
     *
     * <p>Called by {@link #evaluateResponse(byte[])} after guard checks and channel-binding
     * resolution. Subclasses parse the message, validate the token, and return the success bytes.
     *
     * @param response           the client response bytes (never null or empty)
     * @param channelBindingData the resolved channel-binding bytes (empty array for NONE variants)
     * @return mechanism-specific success bytes
     * @throws SaslException if authentication fails
     */
    protected abstract byte[] doEvaluateResponse(byte[] response, byte[] channelBindingData) throws SaslException;

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public String getAuthorizationID() {
        if (!complete) {
            throw new IllegalStateException("Authentication not yet complete");
        }
        return authorizationId;
    }

    /**
     * Returns the rotated FAST token produced after successful authentication, or {@code null}
     * if authentication has not completed successfully.
     *
     * @return the rotated {@link FastToken}, or {@code null}
     */
    public FastToken getRotatedToken() {
        return rotatedToken;
    }

    @Override
    public byte[] unwrap(final byte[] incoming, final int offset, final int len) throws SaslException {
        throw new SaslException(getMechanismName() + " does not support integrity/confidentiality");
    }

    @Override
    public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws SaslException {
        throw new SaslException(getMechanismName() + " does not support integrity/confidentiality");
    }

    @Override
    public Object getNegotiatedProperty(final String propName) {
        return null;
    }

    @Override
    public void dispose() throws SaslException {
        complete = false;
        authorizationId = null;
        rotatedToken = null;
    }

    /**
     * Returns the index of the first occurrence of {@code target} in {@code array} starting at
     * {@code fromIndex}, or {@code -1} if not found.
     */
    protected static int indexOf(final byte[] array, final byte target, final int fromIndex) {
        for (int i = fromIndex; i < array.length; i++) {
            if (array[i] == target) {
                return i;
            }
        }
        return -1;
    }

    protected final String decodeUtf8(final byte[] value, final int offset, final int length, final String field)
        throws SaslException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value, offset, length)).toString();
        } catch (final CharacterCodingException e) {
            throw new SaslException(mechanismName + ": invalid UTF-8 in " + field, e);
        }
    }

    /**
     * Converts the {@code authcid} from a FAST initiator message into a normalized local username.
     *
     * The value is expected to be a bare username, but a domain-qualified form ({@code username@domain}) is also
     * accepted, provided that the domain matches the domain of this server. In both cases the returned value is the
     * stringprep'ed node, which is the form that the caller compares against the username claimed in the stream's
     * 'from' attribute.
     *
     * @param value the raw authcid as sent by the client (cannot be null)
     * @return the normalized local username (never null)
     * @throws SaslException if the value cannot be prepared as a username, or names another domain
     */
    protected String decodeAuthcId(@Nonnull final String value) throws SaslException
    {
        if (value.contains("@"))
        {
            // Provided value is `username@domain`
            final JID claimedAuthcId;
            try {
                claimedAuthcId = new JID(value);
            } catch (final IllegalArgumentException e) {
                throw new SaslException(mechanismName + ": invalid authcid", e);
            }
            final LocalSession session = (LocalSession) props.get(LocalSession.class.getCanonicalName());
            if (session == null) {
                throw new SaslException(mechanismName + ": invalid authcid (unable to validate domain)");
            }
            if (!claimedAuthcId.getDomain().equals(session.getServerName())) {
                throw new SaslException(mechanismName + ": invalid authcid (domain mismatch)");
            }
            final String node = claimedAuthcId.getNode();
            if (node == null) {
                throw new SaslException(mechanismName + ": invalid authcid");
            }
            return node;
        }
        else
        {
            // Provided value is `username` (without domain)
            try {
                return JID.nodeprep(value);
            } catch (final IllegalArgumentException e) {
                throw new SaslException(mechanismName + ": invalid authcid", e);
            }
        }
    }
}
