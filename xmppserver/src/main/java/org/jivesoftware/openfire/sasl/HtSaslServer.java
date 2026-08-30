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
import java.util.Map;

/**
 * Implementation of the HT-* family of SASL mechanisms for FAST (XEP-0484),
 * supporting all hash (SHA-256, SHA-512, SHA3-512) and channel-binding
 * (NONE, UNIQ, ENDP, EXPR) variants.
 *
 * <p>The HT-09 initial response is {@code authcid NUL initiator-hashed-token}.</p>
 *
 * <p>This is a single-round-trip mechanism: the client sends the initial response and
 * the server returns a responder HMAC on success for mutual authentication.</p>
 *
 * <p>Channel-binding data is resolved by the base class {@link AbstractHtSaslServer} before
 * this class's {@link #doEvaluateResponse} is called. For channel-binding variants the data
 * is incorporated into both HMAC proofs.</p>
 *
 * @see AbstractHtSaslServer
 * @see Ht2SaslServer
 */
public class HtSaslServer extends AbstractHtSaslServer {

    private static final Logger Log = LoggerFactory.getLogger(HtSaslServer.class);

    /**
     * Constructs an {@code HtSaslServer} for the given mechanism name.
     *
     * <p>The mechanism name must follow the pattern {@code HT-{HASH}-{CBTYPE}}, e.g.
     * {@code HT-SHA-256-NONE}, {@code HT-SHA-512-UNIQ}, or {@code HT-SHA3-512-EXPR}.</p>
     *
     * @param mechanismName the SASL mechanism name (cannot be null)
     * @param props         the SASL properties map, which must contain the {@link LocalSession}
     *                      instance under {@code LocalSession.class.getCanonicalName()} for
     *                      UNIQ/ENDP/EXPR channel-binding variants (cannot be null)
     */
    public HtSaslServer(@Nonnull final String mechanismName, @Nonnull final Map<String, ?> props) {
        super(mechanismName, props);
    }

    HtSaslServer(final String mechanismName, final Map<String, ?> props, final HashedTokenValidator validator) {
        super(mechanismName, props, validator);
    }

    /**
     * Evaluates the client's initial response (mechanism-specific part).
     *
     * <p>Called by {@link AbstractHtSaslServer#evaluateResponse} after guard checks and
     * channel-binding resolution. The {@code channelBindingData} bytes have already been
     * fetched from the live TLS session (or are empty for NONE variants) and are incorporated
     * into both HT-* HMAC proofs.</p>
     *
     * <p>Expected format: {@code authcid NUL initiator-hashed-token}.</p>
     *
     * @param response           the client's initial response bytes (never null or empty)
     * @param channelBindingData the resolved channel-binding bytes (empty for NONE variants)
     * @return the responder HMAC
     * @throws SaslException if authentication fails
     */
    @Override
    protected byte[] doEvaluateResponse(final byte[] response, final byte[] channelBindingData) throws SaslException {
        // HT-09: authcid NUL initiator-hashed-token.
        final int separator = indexOf(response, (byte) 0, 0);
        if (separator <= 0) {
            throw new SaslException(mechanismName + ": malformed initiator message");
        }
        final String username = decodeUtf8(response, 0, separator, "authcid");
        if (separator > 255) {
            throw new SaslException(mechanismName + ": authcid exceeds 255 octets");
        }
        final int tokenStart = separator + 1;
        final int tokenLength = response.length - tokenStart;
        if (tokenLength <= 0) {
            throw new SaslException(mechanismName + ": malformed initial response (missing token)");
        }
        final byte[] tokenBytes = new byte[tokenLength];
        System.arraycopy(response, tokenStart, tokenBytes, 0, tokenLength);

        Log.debug("{}: evaluating response for user '{}'", mechanismName, username);

        if (username.isEmpty()) {
            throw new SaslException(mechanismName + ": empty username");
        }

        final Ht2ValidationResult result = tokenValidator.validate(
            username, mechanismName, tokenBytes, channelBindingData, "", "");
        if (result == null) {
            throw new SaslException(mechanismName + ": invalid or expired token for user '" + username + "'");
        }
        if (result.isExpired()) {
            throw new SaslFailureException(Failure.CREDENTIALS_EXPIRED);
        }

        authorizationId = username;
        rotatedToken = result.getRotatedToken();
        recordAuthenticatedClient(result.getClientId());
        complete = true;
        Log.debug("{}: authentication successful for user '{}'", mechanismName, username);
        return result.getResponderHashedToken();
    }
}
