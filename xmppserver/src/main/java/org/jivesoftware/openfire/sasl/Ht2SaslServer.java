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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Implementation of the HT2-* family of SASL mechanisms for FAST (XEP-0484),
 * as defined in draft-ietf-kitten-sasl-ht-02, supporting all hash (SHA-256, SHA-512) and
 * channel-binding (NONE, UNIQ, ENDP, EXPR) variants.
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
 * <p>Channel-binding data is resolved by the base class {@link AbstractHtSaslServer} before
 * {@link #doEvaluateResponse} is called. Unlike HT-*, the channel-binding bytes are incorporated
 * into the HMAC computation. Authentication is rejected if channel-binding data cannot be
 * retrieved — matching the SCRAM-SHA-1-PLUS behaviour.</p>
 *
 * @see AbstractHtSaslServer
 * @see HtSaslServer
 */
public class Ht2SaslServer extends AbstractHtSaslServer {

    private static final Logger Log = LoggerFactory.getLogger(Ht2SaslServer.class);

    private byte[] responderHashedToken = null;

    /**
     * Constructs an {@code Ht2SaslServer} for the given mechanism name.
     *
     * <p>The mechanism name must follow the pattern {@code HT2-{HASH}-{CBTYPE}}, e.g.
     * {@code HT2-SHA-256-NONE} or {@code HT2-SHA-512-UNIQ}.</p>
     *
     * @param mechanismName the SASL mechanism name (cannot be null)
     * @param props         the SASL properties map, which must contain the {@link LocalSession}
     *                      instance under {@code LocalSession.class.getCanonicalName()} for
     *                      UNIQ/ENDP/EXPR channel-binding variants (cannot be null)
     */
    public Ht2SaslServer(@Nonnull final String mechanismName, @Nonnull final Map<String, ?> props) {
        super(mechanismName, props);
    }

    /**
     * Evaluates the client's initiator message (mechanism-specific part).
     *
     * <p>Called by {@link AbstractHtSaslServer#evaluateResponse} after guard checks and
     * channel-binding resolution. The {@code channelBindingData} bytes are incorporated into
     * the HMAC computation performed by {@link FastTokenManager#validateTokenHt2}.</p>
     *
     * <p>Expected format: {@code authcid NUL extra-initiator-values NUL initiator-hashed-token}</p>
     *
     * @param response           the client's initiator message bytes (never null or empty)
     * @param channelBindingData the resolved channel-binding bytes (empty for NONE variants)
     * @return the responder success message: {@code NUL extra-responder-values NUL responder-hashed-token}
     * @throws SaslException if authentication fails
     */
    @Override
    protected byte[] doEvaluateResponse(final byte[] response, final byte[] channelBindingData) throws SaslException {
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

        // channelBindingData resolved by the base class; passed directly to validateTokenHt2
        // so the HMAC covers real channel-binding bytes (unlike HT-*).

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
