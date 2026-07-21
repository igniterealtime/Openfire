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
import org.jivesoftware.openfire.fast.FastTokenManager.Ht2ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of the {@code HT2-SHA-256-NONE} SASL mechanism for FAST (XEP-0484),
 * as defined in draft-ietf-kitten-sasl-ht.
 *
 * <p>The initiator message format (NUL-byte separated) is:
 * <pre>authcid NUL extra-initiator-values NUL initiator-hashed-token</pre>
 * where {@code initiator-hashed-token = HMAC-SHA-256(token, "Initiator" || cb-data || extra-initiator-values)}.
 * For the NONE (no channel binding) variant, {@code cb-data} is an empty byte sequence.</p>
 *
 * <p>On success the server sends back a success message:
 * <pre>NUL extra-responder-values NUL responder-hashed-token</pre>
 * where {@code responder-hashed-token = HMAC-SHA-256(token, "Responder" || cb-data || extra-responder-values)}.
 * This provides mutual authentication.</p>
 *
 * <p>This is a two-message mechanism: the client sends one message; the server evaluates it
 * and returns the responder proof via {@link #evaluateResponse(byte[])}.</p>
 */
public class Ht2Sha256NoneSaslServer implements SaslServer {

    private static final Logger Log = LoggerFactory.getLogger(Ht2Sha256NoneSaslServer.class);

    /** The SASL mechanism name. */
    public static final String MECHANISM_NAME = FastTokenManager.HT2_SHA_256_NONE;

    /** Empty channel-binding data for the NONE variant. */
    private static final byte[] EMPTY_CB_DATA = new byte[0];

    private boolean complete = false;
    private String authorizationId = null;
    private FastToken rotatedToken = null;
    private byte[] responderHashedToken = null;

    /**
     * Constructs a new {@code Ht2Sha256NoneSaslServer}.
     */
    public Ht2Sha256NoneSaslServer() {
    }

    @Override
    public String getMechanismName() {
        return MECHANISM_NAME;
    }

    /**
     * Evaluates the client's initiator message.
     *
     * Expected format: {@code authcid NUL extra-initiator-values NUL initiator-hashed-token}
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
            throw new SaslException(MECHANISM_NAME + ": empty initiator message");
        }

        // Parse: authcid NUL extra-initiator-values NUL initiator-hashed-token
        final int firstNul = indexOf(response, (byte) 0, 0);
        if (firstNul < 0) {
            throw new SaslException(MECHANISM_NAME + ": malformed initiator message (missing first NUL)");
        }
        final int secondNul = indexOf(response, (byte) 0, firstNul + 1);
        if (secondNul < 0) {
            throw new SaslException(MECHANISM_NAME + ": malformed initiator message (missing second NUL)");
        }

        final String authcid = new String(response, 0, firstNul, StandardCharsets.UTF_8);
        final String extraInitiatorValues = new String(response, firstNul + 1, secondNul - firstNul - 1, StandardCharsets.UTF_8);
        final int tokenStart = secondNul + 1;
        final int tokenLength = response.length - tokenStart;
        if (tokenLength <= 0) {
            throw new SaslException(MECHANISM_NAME + ": malformed initiator message (missing initiator-hashed-token)");
        }
        final byte[] initiatorHashedToken = new byte[tokenLength];
        System.arraycopy(response, tokenStart, initiatorHashedToken, 0, tokenLength);

        Log.debug("{}: evaluating response for user '{}', extra-initiator-values='{}'", MECHANISM_NAME, authcid, extraInitiatorValues);

        if (authcid.isEmpty()) {
            throw new SaslException(MECHANISM_NAME + ": empty authcid");
        }

        // Validate via FastTokenManager (also rotates on success, computes responder HMAC).
        final Ht2ValidationResult result = FastTokenManager.validateTokenHt2(
            authcid, MECHANISM_NAME, initiatorHashedToken, EMPTY_CB_DATA, extraInitiatorValues, "");
        if (result == null) {
            complete = true;
            throw new SaslException(MECHANISM_NAME + ": invalid or expired token for user '" + authcid + "'");
        }

        authorizationId = authcid;
        rotatedToken = result.getRotatedToken();
        responderHashedToken = result.getResponderHashedToken();
        complete = true;
        Log.debug("{}: authentication successful for user '{}'", MECHANISM_NAME, authcid);

        // Build success message: NUL extra-responder-values NUL responder-hashed-token
        // We send no extra responder values, so: 0x00 0x00 <responderHashedToken>
        final byte[] successMsg = new byte[2 + responderHashedToken.length];
        successMsg[0] = 0x00; // NUL before extra-responder-values
        successMsg[1] = 0x00; // NUL after extra-responder-values (empty)
        System.arraycopy(responderHashedToken, 0, successMsg, 2, responderHashedToken.length);
        return successMsg;
    }

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
        throw new SaslException(MECHANISM_NAME + " does not support integrity/confidentiality");
    }

    @Override
    public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws SaslException {
        throw new SaslException(MECHANISM_NAME + " does not support integrity/confidentiality");
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
        responderHashedToken = null;
    }

    /**
     * Returns the index of the first occurrence of {@code target} in {@code array} starting at
     * {@code fromIndex}, or {@code -1} if not found.
     */
    private static int indexOf(final byte[] array, final byte target, final int fromIndex) {
        for (int i = fromIndex; i < array.length; i++) {
            if (array[i] == target) {
                return i;
            }
        }
        return -1;
    }
}
