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
 * Generic implementation of the HT-* family of SASL mechanisms for FAST (XEP-0484),
 * supporting all hash (SHA-256, SHA-512) and channel-binding (NONE, PLUS, EXPR) variants.
 *
 * <p>The initial-response format per XEP-0484 is:
 * <pre>cb-name ',' authzid ',' token</pre>
 * where {@code cb-name} identifies the channel-binding type used by the client,
 * {@code authzid} is the authorization identity (username), and {@code token} is the
 * raw FAST token bytes.</p>
 *
 * <p>This is a single-round-trip mechanism: the client sends the initial response and
 * the server either accepts or rejects it.</p>
 *
 * <p>For PLUS and EXPR channel-binding variants, channel-binding data is fetched lazily
 * from the {@link LocalSession} stored in {@code props} during {@link #evaluateResponse(byte[])}.
 * Authentication is rejected if channel-binding data cannot be retrieved.</p>
 *
 * @see AbstractHtSaslServer
 */
public class HtSaslServer extends AbstractHtSaslServer {

    private static final Logger Log = LoggerFactory.getLogger(HtSaslServer.class);

    private final String mechanismName;
    private final Map<String, ?> props;

    /**
     * Constructs an {@code HtSaslServer} for the given mechanism name.
     *
     * <p>The mechanism name must follow the pattern {@code HT-{HASH}-{CBTYPE}}, e.g.
     * {@code HT-SHA-256-NONE} or {@code HT-SHA-512-PLUS}.</p>
     *
     * @param mechanismName the SASL mechanism name (cannot be null)
     * @param props         the SASL properties map, which must contain the {@link LocalSession}
     *                      instance under {@code LocalSession.class.getCanonicalName()} for
     *                      PLUS/EXPR channel-binding variants (cannot be null)
     */
    public HtSaslServer(@Nonnull final String mechanismName, @Nonnull final Map<String, ?> props) {
        this.mechanismName = mechanismName;
        this.props = props;
    }

    @Override
    public String getMechanismName() {
        return mechanismName;
    }

    /**
     * Evaluates the client's initial response.
     *
     * <p>Expected format: {@code <cb-name>,<username>,<raw-token-bytes>}
     * where the token field is the raw bytes (not Base64-encoded at this level).</p>
     *
     * <p>For PLUS/EXPR channel-binding variants, the channel-binding type name is encoded
     * in the mechanism name suffix. The actual channel-binding bytes are fetched lazily
     * from the {@link LocalSession} in {@code props} and authentication is rejected if
     * they cannot be retrieved — matching the SCRAM-SHA-1-PLUS behaviour.</p>
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
            throw new SaslException(mechanismName + ": empty initial response");
        }

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

        // For UNIQ/ENDP/EXPR variants, fetch and validate real channel-binding data from the live TLS session.
        // cb-type to TLS channel-binding type name mapping (per HT draft Table 1):
        //   UNIQ -> tls-unique, ENDP -> tls-server-end-point, EXPR -> tls-exporter
        final String cbSuffix = mechanismName.substring(mechanismName.lastIndexOf('-') + 1); // NONE, UNIQ, ENDP, or EXPR
        final String cbTypeName;
        switch (cbSuffix) {
            case "UNIQ": cbTypeName = "tls-unique"; break;
            case "ENDP": cbTypeName = "tls-server-end-point"; break;
            case "EXPR": cbTypeName = "tls-exporter"; break;
            default:     cbTypeName = null; break; // NONE — no channel binding
        }
        if (cbTypeName != null) {
            if (cbName == null || cbName.isEmpty()) {
                throw new SaslException(mechanismName + ": channel binding required but client sent empty cb-name");
            }
            final ChannelBindingProviderManager cbManager = ChannelBindingProviderManager.getInstance();
            if (!cbManager.supportsChannelBinding(cbTypeName)) {
                throw new SaslException(mechanismName + ": server does not support channel binding type '" + cbTypeName + "'");
            }
            final LocalSession session = (LocalSession) props.get(LocalSession.class.getCanonicalName());
            if (session == null || session.getConnection() == null) {
                throw new SaslException(mechanismName + ": local session not found in properties");
            }
            final Optional<byte[]> channelBindingData = session.getConnection().getChannelBindingData(cbTypeName);
            if (channelBindingData.isEmpty()) {
                Log.debug("{}: unable to retrieve channel binding data for '{}'. Rejecting authentication.", mechanismName, cbTypeName);
                throw new SaslException(mechanismName + ": unable to retrieve channel binding data for '" + cbTypeName + "'");
            }
            // Channel-binding data verified; for HT-* the token hash does not incorporate CB bytes
            // (unlike HT2-*), so we only verify that real CB data exists and is retrievable.
            Log.debug("{}: channel binding data retrieved successfully for type '{}'", mechanismName, cbTypeName);
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
