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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of the {@code HT-SHA-256-NONE} SASL mechanism for FAST (XEP-0484).
 *
 * The initial-response format per XEP-0484 is:
 * <pre>cb-name ',' authzid ',' token</pre>
 * where {@code cb-name} is {@code none} for the NONE (no channel binding) variant,
 * {@code authzid} is the authorization identity (username), and {@code token} is the
 * raw FAST token bytes.
 *
 * This is a single-round-trip mechanism: the client sends the initial response and
 * the server either accepts or rejects it.
 *
 * @see AbstractHtSaslServer
 */
public class HtSha256NoneSaslServer extends AbstractHtSaslServer {

    private static final Logger Log = LoggerFactory.getLogger(HtSha256NoneSaslServer.class);

    /** The SASL mechanism name. */
    public static final String MECHANISM_NAME = FastTokenManager.HT_SHA_256_NONE;

    @Override
    public String getMechanismName() {
        return MECHANISM_NAME;
    }

    /**
     * Evaluates the client's initial response.
     *
     * Expected format: {@code none,<username>,<raw-token-bytes>}
     * The fields are separated by commas. The token field is the raw bytes (not Base64-encoded
     * at this level — Base64 decoding is handled by the SASL dispatch layer before calling here).
     *
     * @param response the client's initial response bytes
     * @return an empty byte array on success (no server challenge needed)
     * @throws SaslException if authentication fails
     */
    @Override
    public byte[] evaluateResponse(final byte[] response) throws SaslException {
        if (complete) {
            throw new SaslException("Authentication already complete");
        }

        if (response == null || response.length == 0) {
            throw new SaslException(MECHANISM_NAME + ": empty initial response");
        }

        // Parse: cb-name ',' authzid ',' token-bytes
        // The first two fields are UTF-8 text; the token is raw bytes after the second comma.
        final int firstComma = indexOf(response, (byte) ',', 0);
        if (firstComma < 0) {
            throw new SaslException(MECHANISM_NAME + ": malformed initial response (missing first comma)");
        }
        final int secondComma = indexOf(response, (byte) ',', firstComma + 1);
        if (secondComma < 0) {
            throw new SaslException(MECHANISM_NAME + ": malformed initial response (missing second comma)");
        }

        final String cbName = new String(response, 0, firstComma, StandardCharsets.UTF_8);
        final String username = new String(response, firstComma + 1, secondComma - firstComma - 1, StandardCharsets.UTF_8);
        final int tokenStart = secondComma + 1;
        final int tokenLength = response.length - tokenStart;
        if (tokenLength <= 0) {
            throw new SaslException(MECHANISM_NAME + ": malformed initial response (missing token)");
        }
        final byte[] tokenBytes = new byte[tokenLength];
        System.arraycopy(response, tokenStart, tokenBytes, 0, tokenLength);

        Log.debug("{}: evaluating response for user '{}', cb-name='{}'", MECHANISM_NAME, username, cbName);

        if (username.isEmpty()) {
            throw new SaslException(MECHANISM_NAME + ": empty username");
        }

        // Validate the token via FastTokenManager (also rotates on success).
        final FastToken newToken = FastTokenManager.validateToken(username, MECHANISM_NAME, tokenBytes);
        if (newToken == null) {
            complete = true;
            throw new SaslException(MECHANISM_NAME + ": invalid or expired token for user '" + username + "'");
        }

        authorizationId = username;
        rotatedToken = newToken;
        complete = true;
        Log.debug("{}: authentication successful for user '{}'", MECHANISM_NAME, username);
        return new byte[0];
    }
}
