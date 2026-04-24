/*
 * Copyright (C) 2015 Surevine Ltd, 2016-2026 Ignite Realtime Foundation. All rights reserved
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.xml.bind.DatatypeConverter;

import com.google.common.annotations.VisibleForTesting;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.DefaultAuthProvider;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.ScramUtils;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the SCRAM-SHA-1 server-side mechanism.
 *
 * @author Richard Midwinter
 */
public class ScramSha1SaslServer implements SaslServer {

    /**
     * Stores a server-side secret used when handling authentication attempts for non-existing users in SCRAM-SHA-1 (-PLUS).
     *
     * Prefer to use #getInitializedServerSecretForNonExistentUsers() instead of accessing this property directly, as
     * the method will make sure that the one-time initialization that's required for usage will occur.
     *
     * @see #getServerSecretForNonExistentUsers()
     */
    @VisibleForTesting
    static final SystemProperty<String> SERVER_SECRET_NONEXISTENT_USERS = SystemProperty.Builder.ofType(String.class)
        .setKey("sasl.scram-sha-1.server-secret.nonexistent-users")
        .setEncrypted(true)
        .setDynamic(Boolean.TRUE)
        .build();

    /**
     * Retrieves a server-side secret used when handling authentication attempts for non-existing users in
     * SCRAM-SHA-1 (-PLUS).
     *
     * This method ensures that the one-time initialization that is required for usage will occur.
     *
     * Instead of failing immediately, the server derives deterministic, fake SCRAM credentials (such as stored keys,
     * server keys, and where applicable salt values) based on this secret. This ensures that authentication processing
     * for non-existing users is indistinguishable from that of existing users.
     *
     * This mechanism helps protect against user enumeration attacks by preventing observable differences in behavior
     * between existing and non-existing accounts.
     *
     * Changing (rotating) this value will cause different derived values to be generated for non-existing users.
     * This does not affect authentication of existing users but can invalidate consistency of ongoing or repeated
     * authentication attempts for non-existing users.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3258">OF-3258: Guard against user enumeration in ScramSha1SaslServer</a>
     */
    public synchronized static String getServerSecretForNonExistentUsers()
    {
        // OF-3258: Ensure a consistent but unpredictable server secret is available.
        final String serverSecret = SERVER_SECRET_NONEXISTENT_USERS.getValue();
        if (serverSecret == null || serverSecret.trim().isEmpty()) {
            SERVER_SECRET_NONEXISTENT_USERS.setValue(StringUtils.randomString(29));
        }
        return SERVER_SECRET_NONEXISTENT_USERS.getValue();
    }

    public static final SystemProperty<Integer> ITERATION_COUNT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("sasl.scram-sha-1.iteration-count")
        .setDefaultValue(ScramUtils.DEFAULT_ITERATION_COUNT)
        .setDynamic(Boolean.TRUE)
        .build();
    private static final Logger Log = LoggerFactory.getLogger(ScramSha1SaslServer.class);
    private static final Pattern
            CLIENT_FIRST_MESSAGE = Pattern.compile("^(([pny])=?([^,]*),([^,]*),)(m?=?[^,]*,?n=([^,]*),r=([^,]*),?.*)$"),
            CLIENT_FINAL_MESSAGE = Pattern.compile("(c=([^,]*),r=([^,]*)),p=(.*)$");

    private String username;
    private State state = State.INITIAL;
    private String nonce;
    private String serverFirstMessage;
    private String clientFirstMessageBare;
    private SecureRandom random = new SecureRandom();

    private enum State {
        INITIAL,
        IN_PROGRESS,
        COMPLETE;
    }
    
    public ScramSha1SaslServer() {
    }

    /**
     * Returns the IANA-registered mechanism name of this SASL server.
     * ("SCRAM-SHA-1").
     * @return A non-null string representing the IANA-registered mechanism name.
     */
    @Override
    public String getMechanismName() {
        return "SCRAM-SHA-1";
    }

    /**
     * Evaluates the response data and generates a challenge.
     *
     * If a response is received from the client during the authentication
     * process, this method is called to prepare an appropriate next
     * challenge to submit to the client. The challenge is null if the
     * authentication has succeeded and no more challenge data is to be sent
     * to the client. It is non-null if the authentication must be continued
     * by sending a challenge to the client, or if the authentication has
     * succeeded but challenge data needs to be processed by the client.
     * {@code isComplete()} should be called
     * after each call to {@code evaluateResponse()},to determine if any further
     * response is needed from the client.
     *
     * @param response The non-null (but possibly empty) response sent
     * by the client.
     *
     * @return The possibly null challenge to send to the client.
     * It is null if the authentication has succeeded and there is
     * no more challenge data to be sent to the client.
     * @exception SaslException If an error occurred while processing
     * the response or generating a challenge.
     */
    @Override
    public byte[] evaluateResponse(final byte[] response) throws SaslException {
        try {
            byte[] challenge;
            switch (state)
            {
                case INITIAL:
                    challenge = generateServerFirstMessage(response);
                    state = State.IN_PROGRESS;
                    break;
                case IN_PROGRESS:
                    challenge = generateServerFinalMessage(response);
                    state = State.COMPLETE;
                    break;
                case COMPLETE:
                    if (response == null || response.length == 0)
                    {
                        challenge = new byte[0];
                        break;
                    }
                default:
                    throw new SaslException("No response expected in state " + state);

            }
            return challenge;
        } catch (RuntimeException ex) {
           throw new SaslException("Unexpected exception while evaluating SASL response.", ex);
        }
    }

    /**
     * First response returns:
     *   - the nonce (client nonce appended with our own random UUID)
     *   - the salt
     *   - the number of iterations
     */
    private byte[] generateServerFirstMessage(final byte[] response) throws SaslException {
        String clientFirstMessage = new String(response, StandardCharsets.UTF_8);
        Matcher m = CLIENT_FIRST_MESSAGE.matcher(clientFirstMessage);
        if (!m.matches()) {
            throw new SaslException("Invalid first client message");
        }
//        String gs2Header = m.group(1);
//        String gs2CbindFlag = m.group(2);
//        String gs2CbindName = m.group(3);
//        String authzId = m.group(4);
        clientFirstMessageBare = m.group(5);
        username = m.group(6);
        String clientNonce = m.group(7);
        nonce = clientNonce + UUID.randomUUID().toString();

        serverFirstMessage = String.format("r=%s,s=%s,i=%d", nonce, DatatypeConverter.printBase64Binary(getOrCreateSalt(username)),
                getIterations(username));
        return serverFirstMessage.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Final response returns the server signature.
     */
    private byte[] generateServerFinalMessage(final byte[] response) throws SaslException {
        String clientFinalMessage = new String(response, StandardCharsets.UTF_8);
        Matcher m = CLIENT_FINAL_MESSAGE.matcher(clientFinalMessage);
        if (!m.matches()) {
            throw new SaslException("Invalid client final message");
        }

        String clientFinalMessageWithoutProof = m.group(1);
//        String channelBinding = m.group(2);
        String clientNonce = m.group(3);
        String proof = m.group(4);

        if (!nonce.equals(clientNonce)) { // Constant-time operation is important for keys, not for public protocol values like nonces.
            throw new SaslException("Client final message has incorrect nonce value");
        }

        try {
            String authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageWithoutProof;
            byte[] storedKey = getOrFakeStoredKey(username);
            byte[] serverKey = getOrFakeServerKey(username);

            byte[] clientSignature = ScramUtils.computeHmac(storedKey, authMessage);
            byte[] serverSignature = ScramUtils.computeHmac(serverKey, authMessage);
            
            byte[] clientKey = clientSignature.clone();
            byte[] decodedProof = DatatypeConverter.parseBase64Binary(proof);
            for (int i = 0; i < clientKey.length; i++) {
                clientKey[i] ^= decodedProof[i];
            }

            if (!MessageDigest.isEqual(storedKey, MessageDigest.getInstance("SHA-1").digest(clientKey))) {
                throw new SaslException("Authentication failed for: '"+username+"'");
            }
            return ("v=" + DatatypeConverter.printBase64Binary(serverSignature))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            throw new SaslException(e.getMessage(), e);
        }
    }

    /**
     * Determines whether the authentication exchange has completed.
     * This method is typically called after each invocation of
     * {@code evaluateResponse()} to determine whether the
     * authentication has completed successfully or should be continued.
     * @return true if the authentication exchange has completed; false otherwise.
     */
    @Override
    public boolean isComplete() {
        return state == State.COMPLETE;
    }

    /**
     * Reports the authorization ID in effect for the client of this
     * session.
     * This method can only be called if isComplete() returns true.
     * @return The authorization ID of the client.
     * @exception IllegalStateException if this authentication session has not completed
     */
    @Override
    public String getAuthorizationID() {
        if (isComplete()) {
            return username;
        } else {
            throw new IllegalStateException("SCRAM-SHA-1 authentication not completed");
        }
    }

    /**
     * Unwraps a byte array received from the client. SCRAM-SHA-1 supports no security layer.
     * 
     * @throws SaslException if attempted to use this method.
     */
    @Override
    public byte[] unwrap(byte[] incoming, int offset, int len)
        throws SaslException {
        if (isComplete()) {
            throw new IllegalStateException("SCRAM-SHA-1 does not support integrity or privacy");
        } else {
            throw new IllegalStateException("SCRAM-SHA-1 authentication not completed");
        }
    }

    /**
     * Wraps a byte array to be sent to the client. SCRAM-SHA-1 supports no security layer.
     *
     * @throws SaslException if attempted to use this method.
     */
    @Override
    public byte[] wrap(byte[] outgoing, int offset, int len)
        throws SaslException {
        if (isComplete()) {
            throw new IllegalStateException("SCRAM-SHA-1 does not support integrity or privacy");
        } else {
            throw new IllegalStateException("SCRAM-SHA-1 authentication not completed");
        }
    }

    /**
     * Retrieves the negotiated property.
     * This method can be called only after the authentication exchange has
     * completed (i.e., when {@code isComplete()} returns true); otherwise, an
     * {@code IllegalStateException} is thrown.
     *
     * @param propName the property
     * @return The value of the negotiated property. If null, the property was
     * not negotiated or is not applicable to this mechanism.
     * @exception IllegalStateException if this authentication exchange has not completed
     */
    @Override
    public Object getNegotiatedProperty(String propName) {
        if (isComplete()) {
            if (propName.equals(Sasl.QOP)) {
                return "auth";
            } else {
                return null;
            }
        } else {
            throw new IllegalStateException("SCRAM-SHA-1 authentication not completed");
        }
    }

     /**
      * Disposes of any system resources or security-sensitive information
      * the SaslServer might be using. Invoking this method invalidates
      * the SaslServer instance. This method is idempotent.
      * @throws SaslException If a problem was encountered while disposing
      * the resources.
      */
    @Override
    public void dispose() throws SaslException {
        username = null;
        state = State.INITIAL;
    }

    /**
     * Retrieve the salt for a given username.
     *
     * When a salt does not currently exist for an existing user, but a password is set, that value is used to create
     * and persist a new salt for that user.
     *
     * Returns a username-specific salt if the user doesn't exist to mimic an invalid password. This also guards against
     * user enumeration attacks.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3258">OF-3258: Guard against user enumeration in ScramSha1SaslServer</a>
     */
    @VisibleForTesting
    byte[] getOrCreateSalt(final String username)
    {
        try
        {
            final String saltBase64 = AuthFactory.getSalt(username);
            if (saltBase64 == null) {
                return handleMissingSalt(username);
            }

            return decodeSalt(saltBase64);
        }
        catch (UserNotFoundException e)
        {
            Log.debug("User '{}' not found. Returning fake salt.", username, e);
            return generateFakeSalt(username);
        }
        catch (UnsupportedOperationException | ConnectionException | InternalUnauthenticatedException e) {
            Log.warn("Exception in SCRAM.getSalt() for user '{}'", username, e);
            return generateFakeSalt(username);
        }
    }

    /**
     * When no salt is found for the user, but a (plain-text) password is available, we can generate a salt by updating
     * the password to the same value (this should trigger a re-hashing of the password).
     *
     * @param username The user for whom to generate a salt
     * @return A salt
     * @throws UserNotFoundException when the password could not be loaded for this user.
     * @throws InternalUnauthenticatedException when there's an authentication issue with connecting to the user-provider
     * @throws ConnectionException when there's an issue with connecting to the user-provider
     * @throws UnsupportedOperationException when a plain-text password cannot be retrieved for this user.
     */
    private byte[] handleMissingSalt(String username) throws UserNotFoundException, InternalUnauthenticatedException, ConnectionException, UnsupportedOperationException
    {
        Log.debug("No salt found for '{}', regenerating.", username);

        final String password = AuthFactory.getPassword(username);
        if (password == null) {
            // No password available. This is likely an issue with the provider, which should have thrown a
            // UserNotFoundException or UnsupportedOperationException. Both of those will cause the same fallback
            // handling, so this code can generate either to cause that same fallback behavior.
            throw new UserNotFoundException("No password available for user '" + username + "'");
        }
        AuthFactory.setPassword(username, password);

        final String newSalt = AuthFactory.getSalt(username);
        if (newSalt == null) {
            Log.debug("Salt regeneration failed for '{}'", username);
            return generateFakeSalt(username);
        }
        return decodeSalt(newSalt);
    }

    /**
     * Decode a base64-encoded salt.
     *
     * @param base64Salt The base64-encoded salt to decode
     * @return The decoded salt as a byte array
     */
    private byte[] decodeSalt(@Nonnull final String base64Salt)
    {
        return DatatypeConverter.parseBase64Binary(base64Salt);
    }

    /**
     * Generate a fake salt to guard against user enumeration attacks (see OF-3258).
     *
     * The returned salt is a deterministic but cryptographically unpredictable value derived from the username and a
     * server-side secret. The returned value is always exactly {@link DefaultAuthProvider#SALT_LENGTH} bytes long.
     *
     * @param username The username for which to generate a fake salt
     * @return a fake salt of length {@link DefaultAuthProvider#SALT_LENGTH}.
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3258">OF-3258: Guard against user enumeration in ScramSha1SaslServer</a>
     */
    private byte[] generateFakeSalt(String username)
    {
        final int length = DefaultAuthProvider.SALT_LENGTH;

        try
        {
            final byte[] key = getServerSecretForNonExistentUsers().getBytes(StandardCharsets.UTF_8);
            final byte[] result = new byte[length];

            int offset = 0;
            int counter = 0;

            while (offset < length)
            {
                // Domain separation + counter to expand output deterministically
                final byte[] block = ScramUtils.computeHmac(key, "fake-salt-for-" + username + ":" + counter);
                final int toCopy = Math.min(block.length, length - offset);
                System.arraycopy(block, 0, result, offset, toCopy);

                offset += toCopy;
                counter++;
            }

            return result;
        }
        catch (SaslException e)
        {
            // Give up trying to be deterministic. Return a random salt.
            final byte[] salt = new byte[length];
            random.nextBytes(salt);
            return salt;
        }
    }
    
    /**
     * Retrieve the iteration count from the database for a given username.
     */
    private int getIterations(final String username) {
        try {
            return AuthFactory.getIterations(username);
        } catch (UserNotFoundException e) {
            return ITERATION_COUNT.getValue();
        }
    }

    /**
     * Retrieve the server key from the database for a given username, but returns a fake key if none is found.
     *
     * Returning a fake key helps guard against timing attacks: instead of short-circuiting the operation,
     * a fake key is generated to ensure consistent response times and prevent potential timing attacks.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3257">OF-3257: Guard against timing attacks in ScramSha1SaslServer</a>
     */
    @VisibleForTesting
    byte[] getOrFakeServerKey(String username) {
        try {
            byte[] key = getServerKey(username);
            if (key != null) {
                return key;
            }
        } catch (UserNotFoundException ignored) {
            // fall through
        }
        return generateFakeKey("server-key-" + username);
    }

    /**
     * Retrieve the server key from the database for a given username.
     */
    private byte[] getServerKey(final String username) throws UserNotFoundException {
        final String serverKey = AuthFactory.getServerKey(username);
        if (serverKey == null) {
            return null;
        } else {
            return DatatypeConverter.parseBase64Binary( serverKey );
        }
    }

    /**
     * Retrieve the stored key from the database for a given username, but returns a fake key if none is found.
     *
     * Returning a fake key helps guard against timing attacks: instead of short-circuiting the operation,
     * a fake key is generated to ensure consistent response times and prevent potential timing attacks.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3257">OF-3257: Guard against timing attacks in ScramSha1SaslServer</a>
     */
    @VisibleForTesting
    byte[] getOrFakeStoredKey(final String username) {
        try {
            byte[] key = getStoredKey(username);
            if (key != null) {
                return key;
            }
        } catch (UserNotFoundException ignored) {
            // fall through
        }
        return generateFakeKey("stored-key-" + username);
    }

    /**
     * Retrieve the stored key from the database for a given username.
     */
    private byte[] getStoredKey(final String username) throws UserNotFoundException {
        final String storedKey = AuthFactory.getStoredKey(username);
        if (storedKey == null) {
            return null;
        } else {
            return DatatypeConverter.parseBase64Binary( storedKey );
        }
    }

    /**
     * Generate a fake key to guard against timing attacks (see OF-3258).
     *
     * @param input a string input for which to generate a fake key
     * @return a fake key
     */
    private byte[] generateFakeKey(String input)
    {
        try {
            return ScramUtils.computeHmac(
                getServerSecretForNonExistentUsers().getBytes(StandardCharsets.UTF_8),
                input
            );
        } catch (SaslException e) {
            byte[] fallback = new byte[24];
            random.nextBytes(fallback);
            return fallback;
        }
    }
}
