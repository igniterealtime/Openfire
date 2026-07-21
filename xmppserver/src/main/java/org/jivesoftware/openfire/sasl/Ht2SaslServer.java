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

import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.fast.FastTokenManager.Ht2ValidationResult;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Generic implementation of the HT2-* family of SASL mechanisms for FAST (XEP-0484),
 * as defined in draft-ietf-kitten-sasl-ht, supporting all hash (SHA-256, SHA-512) and
 * channel-binding (NONE, PLUS, EXPR) variants.
 *
 * <p>The initiator message format (NUL-byte separated) is:
 * <pre>authcid NUL extra-initiator-values NUL initiator-hashed-token</pre>
 * where {@code initiator-hashed-token = HMAC(token, "Initiator" || cb-data || extra-initiator-values)}
 * and the HMAC algorithm is derived from the mechanism name (e.g. HmacSHA256 for SHA-256 variants).
 * For the NONE (no channel binding) variant, {@code cb-data} is an empty byte sequence.</p>
 *
 * <p>On success the server sends back a success message:
 * <pre>NUL extra-responder-values NUL responder-hashed-token</pre>
 * where {@code responder-hashed-token = HMAC(token, "Responder" || cb-data || extra-responder-values)}.
 * This provides mutual authentication.</p>
 *
 * <p>This is a two-message mechanism: the client sends one message; the server evaluates it
 * and returns the responder proof via {@link #evaluateResponse(byte[])}.</p>
 *
 * <p>For PLUS and EXPR channel-binding variants, channel-binding data is fetched lazily
 * from the {@link LocalSession} stored in {@code props} during {@link #evaluateResponse(byte[])}.
 * Authentication is rejected if channel-binding data cannot be retrieved — matching the
 * SCRAM-SHA-1-PLUS behaviour.</p>
 *
 * @see AbstractHtSaslServer
 */
public class Ht2SaslServer extends AbstractHtSaslServer {

    private static final Logger Log = LoggerFactory.getLogger(Ht2SaslServer.class);

    private final String mechanismName;
    private final Map<String, ?> props;

    private byte[] responderHashedToken = null;

    /**
     * Constructs an {@code Ht2SaslServer} for the given mechanism name.
     *
     * <p>The mechanism name must follow the pattern {@code HT2-{HASH}-{CBTYPE}}, e.g.
     * {@code HT2-SHA-256-NONE}, {@code HT2-SHA-512-PLUS}.</p>
     *
     * @param mechanismName the SASL mechanism name (cannot be null)
     * @param props         the SASL properties map, which must contain the {@link LocalSession}
     *                      instance under {@code LocalSession.class.getCanonicalName()} for
     *                      PLUS/EXPR channel-binding variants (cannot be null)
     */
    public Ht2SaslServer(@Nonnull final String mechanismName, @Nonnull final Map<String, ?> props) {
        this.mechanismName = mechanismName;
        this.props = props;
    }

    @Override
    public String getMechanismName() {
        return mechanismName;
    }

    /**
     * Evaluates the client's initiator message.
     *
     * <p>Expected format: {@code authcid NUL extra-initiator-values NUL initiator-hashed-token}</p>
     *
     * <p>For PLUS/EXPR channel-binding variants, the channel-binding type name is encoded in the
     * mechanism name suffix. The actual channel-binding bytes are fetched lazily from the
     * {@link LocalSession} in {@code props} and authentication is rejected if they cannot be
     * retrieved — matching the SCRAM-SHA-1-PLUS behaviour. The fetched bytes are also passed to
     * {@link FastTokenManager#validateTokenHt2} so the HMAC covers real channel-binding data.</p>
     *
     * @param response the client's initiator message bytes
     * @return the responder success message: {@code NUL extra-responder-values NUL responder-hashed-token}
     * @throws SaslException if authentication fails
     */
    @Override
    public byte[] evaluateResponse(final byte[] response) throws SaslException {
        if (complete) {
            throw new SaslException("Authentication already complete");
        }

        if (response == null || response.length == 0) {
            throw new SaslException(mechanismName + ": empty initiator message");
        }

        // Parse: authcid NUL extra-initiator-values NUL initiator-hashed-token
        final int firstNul = indexOf(response, (byte) 0, 0);
        if (firstNul < 0) {
            throw new SaslException(mechanismName + ": malformed initiator message (missing first NUL)");
        }
        final int secondNul = indexOf(response, (byte) 0, firstNul + 1);
        if (secondNul < 0) {
            throw new SaslException(mechanismName + ": malformed initiator message (missing second NUL)");
        }

        final String authcid = new String(response, 0, firstNul, StandardCharsets.UTF_8);
        final String extraInitiatorValues = new String(response, firstNul + 1, secondNul - firstNul - 1, StandardCharsets.UTF_8);
        final int tokenStart = secondNul + 1;
        final int tokenLength = response.length - tokenStart;
        if (tokenLength <= 0) {
            throw new SaslException(mechanismName + ": malformed initiator message (missing initiator-hashed-token)");
        }
        final byte[] initiatorHashedToken = new byte[tokenLength];
        System.arraycopy(response, tokenStart, initiatorHashedToken, 0, tokenLength);

        Log.debug("{}: evaluating response for user '{}', extra-initiator-values='{}'", mechanismName, authcid, extraInitiatorValues);

        if (authcid.isEmpty()) {
            throw new SaslException(mechanismName + ": empty authcid");
        }

        // Resolve channel-binding data lazily from the live TLS session, matching SCRAM-SHA-1-PLUS.
        final byte[] channelBindingData;
        final String cbSuffix = mechanismName.substring(mechanismName.lastIndexOf('-') + 1); // NONE, PLUS, or EXPR
        if ("PLUS".equals(cbSuffix) || "EXPR".equals(cbSuffix)) {
            // The HT2 spec requires the cb-type name to appear in extra-initiator-values or be
            // implicit in the mechanism name; here we derive it from the suffix for PLUS/EXPR.
            // Use tls-unique for PLUS, tls-exporter for EXPR (per standard naming).
            final String cbTypeName = "PLUS".equals(cbSuffix) ? "tls-unique" : "tls-exporter";
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
            channelBindingData = cbDataOpt.get();
            Log.debug("{}: channel binding data retrieved successfully for type '{}'", mechanismName, cbTypeName);
        } else {
            channelBindingData = new byte[0];
        }

        // Validate via FastTokenManager (also rotates on success, computes responder HMAC).
        final Ht2ValidationResult result = FastTokenManager.validateTokenHt2(
            authcid, mechanismName, initiatorHashedToken, channelBindingData, extraInitiatorValues, "");
        if (result == null) {
            complete = true;
            throw new SaslException(mechanismName + ": invalid or expired token for user '" + authcid + "'");
        }

        authorizationId = authcid;
        rotatedToken = result.getRotatedToken();
        responderHashedToken = result.getResponderHashedToken();
        complete = true;
        Log.debug("{}: authentication successful for user '{}'", mechanismName, authcid);

        // Build success message: NUL extra-responder-values NUL responder-hashed-token
        // We send no extra responder values, so: 0x00 0x00 <responderHashedToken>
        final byte[] successMsg = new byte[2 + responderHashedToken.length];
        successMsg[0] = 0x00; // NUL before extra-responder-values
        successMsg[1] = 0x00; // NUL after extra-responder-values (empty)
        System.arraycopy(responderHashedToken, 0, successMsg, 2, responderHashedToken.length);
        return successMsg;
    }

    @Override
    public void dispose() throws SaslException {
        super.dispose();
        responderHashedToken = null;
    }
}
