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

import javax.annotation.Nonnull;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Implementation of the HT-* family of SASL mechanisms for FAST (XEP-0484),
 * supporting all hash (SHA-256, SHA-512) and channel-binding (NONE, UNIQ, ENDP, EXPR) variants.
 *
 * <p>The initial-response format is:
 * <pre>cb-name ',' authzid ',' token</pre>
 * where {@code cb-name} identifies the channel-binding type used by the client,
 * {@code authzid} is the authorization identity (username), and {@code token} is the
 * raw FAST token bytes.</p>
 *
 * <p>This is a single-round-trip mechanism: the client sends the initial response and
 * the server either accepts or rejects it, returning an empty byte array on success.</p>
 *
 * <p>Channel-binding data is resolved by the base class {@link AbstractHtSaslServer} before
 * this class's {@link #doEvaluateResponse} is called. For channel-binding variants the data
 * is verified to exist, but the token hash itself does not incorporate it (unlike HT2-*).</p>
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
     * {@code HT-SHA-256-NONE} or {@code HT-SHA-512-UNIQ}.</p>
     *
     * @param mechanismName the SASL mechanism name (cannot be null)
     * @param props         the SASL properties map, which must contain the {@link LocalSession}
     *                      instance under {@code LocalSession.class.getCanonicalName()} for
     *                      UNIQ/ENDP/EXPR channel-binding variants (cannot be null)
     */
    public HtSaslServer(@Nonnull final String mechanismName, @Nonnull final Map<String, ?> props) {
        super(mechanismName, props);
    }

    /**
     * Evaluates the client's initial response (mechanism-specific part).
     *
     * <p>Called by {@link AbstractHtSaslServer#evaluateResponse} after guard checks and
     * channel-binding resolution. The {@code channelBindingData} bytes have already been
     * fetched from the live TLS session (or are empty for NONE variants); they are not
     * incorporated into the HT-* token hash but are verified to exist for CB variants.</p>
     *
     * <p>Expected format: {@code cb-name,authzid,token-bytes}
     * where the token is the raw FAST token bytes.</p>
     *
     * @param response           the client's initial response bytes (never null or empty)
     * @param channelBindingData the resolved channel-binding bytes (empty for NONE variants)
     * @return an empty byte array on success (HT-* has no server challenge)
     * @throws SaslException if authentication fails
     */
    @Override
    protected byte[] doEvaluateResponse(final byte[] response, final byte[] channelBindingData) throws SaslException {
        // Parse: cb-name ',' authzid ',' token-bytes
        // The first two fields are UTF-8 text; the token is raw bytes after the second comma.
        final int firstComma = indexOf(response, (byte) ',', 0);
        if (firstComma < 0) {
            throw new SaslException(mechanismName + ": malformed initial response (missing first comma)");
        }
        final int secondComma = indexOf(response, (byte) ',', firstComma + 1);
        if (secondComma < 0) {
            throw new SaslException(mechanismName + ": malformed initial response (missing second comma)");
        }

        final String cbName = new String(response, 0, firstComma, StandardCharsets.UTF_8);
        final String username = new String(response, firstComma + 1, secondComma - firstComma - 1, StandardCharsets.UTF_8);
        final int tokenStart = secondComma + 1;
        final int tokenLength = response.length - tokenStart;
        if (tokenLength <= 0) {
            throw new SaslException(mechanismName + ": malformed initial response (missing token)");
        }
        final byte[] tokenBytes = new byte[tokenLength];
        System.arraycopy(response, tokenStart, tokenBytes, 0, tokenLength);

        Log.debug("{}: evaluating response for user '{}', cb-name='{}'", mechanismName, username, cbName);

        if (username.isEmpty()) {
            throw new SaslException(mechanismName + ": empty username");
        }

        // For channel-binding variants the base class has already verified CB data is available
        // and returned it as channelBindingData. For HT-* the hash does not incorporate those bytes
        // (unlike HT2-*), but we still require a non-empty cb-name from the client.
        if (channelBindingData.length > 0 && (cbName == null || cbName.isEmpty())) {
            throw new SaslException(mechanismName + ": channel binding required but client sent empty cb-name");
        }

        // Validate the token via FastTokenManager (also rotates on success).
        final FastToken newToken = FastTokenManager.validateToken(username, mechanismName, tokenBytes);
        if (newToken == null) {
            complete = true;
            throw new SaslException(mechanismName + ": invalid or expired token for user '" + username + "'");
        }

        authorizationId = username;
        rotatedToken = newToken;
        complete = true;
        Log.debug("{}: authentication successful for user '{}'", mechanismName, username);
        return new byte[0];
    }
}
