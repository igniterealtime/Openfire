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
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
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
 * <h3>HT-* vs HT2-* differences</h3>
 * <p>The two mechanism families share identical channel-binding handling but differ in:</p>
 * <ul>
 *   <li><b>Message format</b>: HT uses comma-separated {@code cb-name,authzid,token};
 *       HT2 uses NUL-separated {@code authcid NUL extra-values NUL hashed-token}.</li>
 *   <li><b>Token verification</b>: HT compares a raw hash; HT2 uses HMAC with
 *       {@code "Initiator"}/{{@code "Responder"}} labels and incorporates channel-binding data
 *       into the HMAC input.</li>
 *   <li><b>Mutual authentication</b>: HT returns an empty byte array on success; HT2 returns
 *       a {@code NUL extra-responder-values NUL responder-hashed-token} success message.</li>
 * </ul>
 * <p>Even when the client sends no extra values in an HT2 exchange, the two families are
 * <em>not</em> interchangeable because of the different token-verification schemes above.</p>
 */
abstract class AbstractHtSaslServer implements SaslServer {

    private static final Logger Log = LoggerFactory.getLogger(AbstractHtSaslServer.class);

    /** The SASL mechanism name (e.g. {@code HT-SHA-256-NONE} or {@code HT2-SHA-512-EXPR}). */
    protected final String mechanismName;

    /**
     * The SASL properties map; must contain a {@link LocalSession} under
     * {@code LocalSession.class.getCanonicalName()} for channel-binding variants.
     */
    protected final Map<String, ?> props;

    protected boolean complete = false;
    protected String authorizationId = null;
    protected FastToken rotatedToken = null;

    /**
     * Constructs an {@code AbstractHtSaslServer} with the given mechanism name and properties map.
     *
     * @param mechanismName the SASL mechanism name (cannot be null)
     * @param props         the SASL properties map (cannot be null)
     */
    protected AbstractHtSaslServer(@Nonnull final String mechanismName, @Nonnull final Map<String, ?> props) {
        this.mechanismName = mechanismName;
        this.props = props;
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
     * @return mechanism-specific success bytes (empty for HT-*, responder proof for HT2-*)
     * @throws SaslException if authentication fails
     */
    @Override
    public final byte[] evaluateResponse(final byte[] response) throws SaslException {
        if (complete) {
            throw new SaslException("Authentication already complete");
        }
        if (response == null || response.length == 0) {
            throw new SaslException(mechanismName + ": empty initiator message");
        }
        final byte[] channelBindingData = resolveChannelBindingData();
        return doEvaluateResponse(response, channelBindingData);
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
            default:     cbTypeName = null; break; // NONE — no channel binding
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
}
