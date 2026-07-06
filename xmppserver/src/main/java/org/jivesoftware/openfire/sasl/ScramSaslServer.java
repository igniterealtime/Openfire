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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.crypto.Mac;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.xml.bind.DatatypeConverter;

import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.DefaultAuthProvider;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.ScramUtils;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the server side of the SCRAM SASL exchange as defined in RFC 5802, including the channel binding (-PLUS)
 * variants defined there and profiled for other hash functions in RFC 7677.
 *
 * The exchange logic in this class is hash-function agnostic. Concrete subclasses bind a specific hash function by
 * implementing the small set of abstract methods: the base mechanism name (which doubles as the credential storage
 * key), the HMAC and message digest algorithm names, the default iteration count, and the server-side secret used to
 * derive indistinguishable fake credentials for non-existent users.
 *
 * @author Richard Midwinter, Guus der Kinderen
 */
public abstract class ScramSaslServer implements SaslServer
{
    private static final Logger Log = LoggerFactory.getLogger(ScramSaslServer.class);

    /**
     * The name of the negotiated property through which the channel binding type that was used during authentication
     * is exposed.
     */
    public static final String PROPNAME_CHANNELBINDINGTYPE = "channelbindingtype";

    private static final Pattern
        CLIENT_FIRST_MESSAGE = Pattern.compile("^(([pny])=?([^,]*),([^,]*),)(m?=?[^,]*,?n=([^,]*),r=([^,]*),?.*)$"),
        CLIENT_FINAL_MESSAGE = Pattern.compile("(c=([^,]*),r=([^,]*)),p=(.*)$");

    private final ChannelBindingProviderManager channelBindingProviderManager;
    private final Set<String> serverSupportedSaslMechanismNames;

    private final boolean isPlusMechanism;
    private final Map<String, ?> props;
    private String username;
    private State state = State.INITIAL;
    private String nonce;
    private String serverFirstMessage;
    private String clientFirstMessageBare;
    private final SecureRandom random = new SecureRandom();
    private byte[] expectedChannelBindingPayloadInFinalClientMessage;
    private String gs2CbindName;

    private enum State {
        INITIAL,
        IN_PROGRESS,
        COMPLETE;
    }

    protected ScramSaslServer(final boolean isPlusMechanism, final Map<String, ?> props, final ChannelBindingProviderManager channelBindingProviderManager, final Set<String> serverSupportedSaslMechanismNames)
    {
        this.isPlusMechanism = isPlusMechanism;
        this.props = props;
        this.channelBindingProviderManager = channelBindingProviderManager;
        this.serverSupportedSaslMechanismNames = serverSupportedSaslMechanismNames;
    }

    /**
     * The IANA-registered name of the base (non-PLUS) mechanism implemented by this server, for example
     * {@code SCRAM-SHA-1}. This value is also the key under which SCRAM credentials for this mechanism are stored:
     * the -PLUS variant shares the credential of the base mechanism.
     *
     * @return A non-null string representing the IANA-registered (base) mechanism name.
     */
    protected abstract String getMechanismBaseName();

    /**
     * The JCA name of the HMAC algorithm that corresponds to this mechanism's hash function, for example
     * {@code HmacSHA1}.
     *
     * @return the HMAC algorithm name.
     */
    protected abstract String getHmacAlgorithmName();

    /**
     * The JCA name of the message digest that corresponds to this mechanism's hash function, for example
     * {@code SHA-1}. Used to compute {@code H(ClientKey)} when verifying the client proof.
     *
     * @return the message digest algorithm name.
     */
    protected abstract String getDigestAlgorithmName();

    /**
     * The iteration count to advertise when no per-user value is available.
     *
     * @return the default iteration count for this mechanism.
     */
    protected abstract int getDefaultIterationCount();

    /**
     * A server-side secret from which deterministic fake credentials are derived for non-existent users, so that
     * authentication processing for non-existing users is indistinguishable from that of existing users.
     *
     * @return the server secret for this mechanism.
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3258">OF-3258: Guard against user enumeration in ScramSha1SaslServer</a>
     */
    protected abstract String getNonExistentUserSecret();

    /**
     * Returns the IANA-registered mechanism name of this SASL server, which is the base mechanism name with a
     * {@code -PLUS} suffix for the channel binding variant.
     *
     * @return A non-null string representing the IANA-registered mechanism name.
     */
    @Override
    public String getMechanismName() {
        return isPlusMechanism ? getMechanismBaseName() + "-PLUS" : getMechanismBaseName();
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
                    throw new SaslException("Unexpected response after authentication completed");
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
        final byte[] gs2_header = extractRawGS2Header(response); // Using raw header to prevent any normalization issues that might pop up when using something like: gs2Header.getBytes(StandardCharsets.UTF_8);
//        String gs2Header = m.group(1);
        String gs2CbindFlag = m.group(2);
        gs2CbindName = m.group(3);
//        String authzId = m.group(4);
        clientFirstMessageBare = m.group(5);
        username = m.group(6);
        String clientNonce = m.group(7);

        if (username == null || username.isEmpty()) {
            throw new SaslException("Invalid first client message: Username cannot be empty");
        }
        if (clientNonce == null || clientNonce.isEmpty()) {
            throw new SaslException("Invalid first client message: Client nonce cannot be empty");
        }

        // https://www.rfc-editor.org/rfc/rfc5802.html#section-6: If the flag is set to "y" and the server supports
        // channel binding, the server MUST fail authentication. This is because if the client sets the channel binding
        // flag to "y", then the client must have believed that the server did not support channel binding -- if the
        // server did in fact support channel binding, then this is an indication that there has been a downgrade attack
        // (e.g., an attacker changed the server's mechanism list to exclude the -PLUS suffixed SCRAM mechanism name(s)).
        final boolean clientSupportsChannelBindingButThinksServerDoesNot = "y".equals(gs2CbindFlag);
        final boolean serverSupportsChannelBinding = serverSupportedSaslMechanismNames.stream().anyMatch(mechanism -> mechanism.endsWith("-PLUS"));
        if (clientSupportsChannelBindingButThinksServerDoesNot && serverSupportsChannelBinding) {
            throw new SaslException("Client supports channel binding, but thinks the server does not (while it does). Rejecting authentication to prevent downgrade attack.");
        }

        final boolean clientRequiresChannelBinding = "p".equals(gs2CbindFlag);
        if (clientRequiresChannelBinding && !isPlusMechanism) {
            throw new SaslException("Client requires channel binding, but is not using a -PLUS mechanism. Rejecting authentication.");
        }

        if (isPlusMechanism)
        {
            if (!clientRequiresChannelBinding) {
                throw new SaslException("Channel binding required for -PLUS. Rejecting authentication.");
            }

            if (!serverSupportsChannelBinding) {
                throw new SaslException("Client requires channel binding, but server does not support channel binding. Rejecting authentication.");
            }

            // https://www.rfc-editor.org/rfc/rfc5802.html#section-6: If the channel binding flag was "p" and the server
            // does not support the indicated channel binding type, then the server MUST fail authentication.
            if (gs2CbindName == null || gs2CbindName.isEmpty() || !channelBindingProviderManager.supportsChannelBinding(gs2CbindName)) {
                throw new SaslException("Client requires channel binding, but server does not support the indicated channel binding type '" + gs2CbindName + "'. Rejecting authentication.");
            }

            // Prepare channel binding data.
            final LocalSession session = (LocalSession) props.get(LocalSession.class.getCanonicalName());
            if (session == null || session.getConnection() == null) {
                throw new SaslException("Local session not found in properties. Rejecting authentication.");
            }
            final Optional<byte[]> channelBindingData = session.getConnection().getChannelBindingData(gs2CbindName);
            if (channelBindingData.isEmpty()) {
                Log.debug("Unable to retrieve channel binding data for '{}'. Rejecting authentication.", gs2CbindName);
                throw new SaslException("Unable to retrieve channel binding data for '" + gs2CbindName + "'. Rejecting authentication.");
            }

            // In the final client message, we expect to find a combination of the gs2 header and channel binding data.
            final byte[] cb_data = channelBindingData.get();
            expectedChannelBindingPayloadInFinalClientMessage = new byte[gs2_header.length + cb_data.length];
            System.arraycopy(gs2_header, 0, expectedChannelBindingPayloadInFinalClientMessage, 0        , gs2_header.length);
            System.arraycopy(cb_data,    0, expectedChannelBindingPayloadInFinalClientMessage, gs2_header.length, cb_data.length);
        } else {
            // If this is _not_ a -PLUS mechanism, we still need to verify the channel binding payload in the final client message.
            // In that case, it should not have trailing channel binding data.
            expectedChannelBindingPayloadInFinalClientMessage = gs2_header;
        }

        nonce = clientNonce + UUID.randomUUID().toString();

        serverFirstMessage = String.format("r=%s,s=%s,i=%d", nonce, DatatypeConverter.printBase64Binary(getOrCreateSalt(username)), getIterations(username));
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

        // client-final-message regex: (c=([^,]*),r=([^,]*)),p=(.*)$")
        final String clientFinalMessageWithoutProof = m.group(1); // (c=([^,]*),r=([^,]*))
        final String channelBinding = m.group(2);                 // c=([^,]*)
        final String clientNonce = m.group(3);                    // r=([^,]*)
        final String proof = m.group(4);                          // p=(.*)

        if (proof == null || proof.isEmpty()) {
            throw new SaslException("Invalid client final message: missing proof attribute");
        }

        if (channelBinding == null || channelBinding.isEmpty()) {
            throw new SaslException("Invalid client final message: missing channel binding attribute");
        }

        if (clientNonce == null || clientNonce.isEmpty()) {
            throw new SaslException("Invalid client final message: missing nonce attribute");
        }

        // Verify nonce: RFC 5802 §5: must equal client_nonce (from initial client response) + server_nonce (from initial server response)
        if (!nonce.equals(clientNonce)) { // Constant-time operation is important for keys, not for public protocol values like nonces.
            // Possible replay or tampering
            throw new SaslException("Invalid client final message: incorrect nonce attribute value");
        }

        // Verify channel binding payload.
        final byte[] decodedChannelBinding = DatatypeConverter.parseBase64Binary(channelBinding);
        if (!Arrays.equals(expectedChannelBindingPayloadInFinalClientMessage, decodedChannelBinding)) {
            throw new SaslException("Invalid client final message: channel binding payload does not match expected payload");
        }

        try {
            String authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageWithoutProof;
            byte[] storedKey = getOrFakeStoredKey(username);
            byte[] serverKey = getOrFakeServerKey(username);

            byte[] clientSignature = ScramUtils.computeHmac(storedKey, authMessage, getHmacAlgorithmName());
            byte[] serverSignature = ScramUtils.computeHmac(serverKey, authMessage, getHmacAlgorithmName());

            byte[] clientKey = clientSignature.clone();
            byte[] decodedProof = DatatypeConverter.parseBase64Binary(proof);
            if (decodedProof.length != clientKey.length) {
                throw new SaslException("Invalid proof length: expected " + clientKey.length + " bytes, got " + decodedProof.length);
            }
            for (int i = 0; i < clientKey.length; i++) {
                clientKey[i] ^= decodedProof[i];
            }

            if (!MessageDigest.isEqual(storedKey, MessageDigest.getInstance(getDigestAlgorithmName()).digest(clientKey))) {
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
            throw new IllegalStateException(getMechanismName() + " authentication not completed");
        }
    }

    /**
     * Unwraps a byte array received from the client. SCRAM supports no security layer.
     *
     * @throws SaslException if attempted to use this method.
     */
    @Override
    public byte[] unwrap(byte[] incoming, int offset, int len)
        throws SaslException {
        if (isComplete()) {
            throw new IllegalStateException(getMechanismName() + " does not support integrity or privacy");
        } else {
            throw new IllegalStateException(getMechanismName() + " authentication not completed");
        }
    }

    /**
     * Wraps a byte array to be sent to the client. SCRAM supports no security layer.
     *
     * @throws SaslException if attempted to use this method.
     */
    @Override
    public byte[] wrap(byte[] outgoing, int offset, int len)
        throws SaslException {
        if (isComplete()) {
            throw new IllegalStateException(getMechanismName() + " does not support integrity or privacy");
        } else {
            throw new IllegalStateException(getMechanismName() + " authentication not completed");
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
            } else if (isPlusMechanism && propName.equals(PROPNAME_CHANNELBINDINGTYPE)) {
                return gs2CbindName;
            } else {
                return null;
            }
        } else {
            throw new IllegalStateException(getMechanismName() + " authentication not completed");
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
        nonce = null;
        serverFirstMessage = null;
        clientFirstMessageBare = null;
        expectedChannelBindingPayloadInFinalClientMessage = null;
        gs2CbindName = null;
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
    protected byte[] getOrCreateSalt(final String username)
    {
        try
        {
            final String saltBase64 = AuthFactory.getSalt(username, getMechanismBaseName());
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

        final String newSalt = AuthFactory.getSalt(username, getMechanismBaseName());
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
            final byte[] key = getNonExistentUserSecret().getBytes(StandardCharsets.UTF_8);
            final byte[] result = new byte[length];

            int offset = 0;
            int counter = 0;

            while (offset < length)
            {
                // Domain separation + counter to expand output deterministically
                final byte[] block = ScramUtils.computeHmac(key, "fake-salt-for-" + username + ":" + counter, getHmacAlgorithmName());
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
            return AuthFactory.getIterations(username, getMechanismBaseName());
        } catch (UserNotFoundException e) {
            return getDefaultIterationCount();
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
    protected byte[] getOrFakeServerKey(String username) {
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
        final String serverKey = AuthFactory.getServerKey(username, getMechanismBaseName());
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
    protected byte[] getOrFakeStoredKey(final String username) {
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
        final String storedKey = AuthFactory.getStoredKey(username, getMechanismBaseName());
        if (storedKey == null) {
            return null;
        } else {
            return DatatypeConverter.parseBase64Binary( storedKey );
        }
    }

    /**
     * Generate a fake key to guard against timing attacks (see OF-3257).
     *
     * The fake key is derived using this mechanism's HMAC algorithm, so that its length matches the length of a real
     * key for this mechanism.
     *
     * @param input a string input for which to generate a fake key
     * @return a fake key
     */
    private byte[] generateFakeKey(String input)
    {
        try {
            return ScramUtils.computeHmac(
                getNonExistentUserSecret().getBytes(StandardCharsets.UTF_8),
                input,
                getHmacAlgorithmName()
            );
        } catch (SaslException e) {
            int fallbackLength;
            try {
                fallbackLength = Mac.getInstance(getHmacAlgorithmName()).getMacLength();
            } catch (NoSuchAlgorithmException ignored) {
                fallbackLength = 24;
            }
            final byte[] fallback = new byte[fallbackLength];
            random.nextBytes(fallback);
            return fallback;
        }
    }

    /**
     * Extracts the raw GS2 header from a SCRAM client-first-message byte array.
     *
     * The GS2 header is defined in RFC 5802 as:
     * <pre>
     * gs2-header = gs2-cbind-flag "," [authzid] ","
     * </pre>
     * and always terminates with a trailing comma.
     *
     * This method performs a byte-level scan of the input and returns a copy of the original byte array from index
     * {@code 0} up to and including the second comma (i.e., the full GS2 header including its trailing comma).
     *
     * No character decoding or normalization is performed. This ensures that the returned GS2 header is byte-for-
     * byte identical to the original input, which is required for correct -PLUS channel binding validation in SCRAM
     * mechanisms.
     *
     * @param data the raw SCRAM client-first-message bytes
     * @return a byte array containing the complete GS2 header including the trailing comma
     * @throws SaslException if the input does not contain a valid GS2 header
     */
    protected static byte[] extractRawGS2Header(final byte[] data) throws SaslException
    {
        // The GS2 header ends at the second comma.
        int commaCount = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == ',') {
                commaCount++;
                if (commaCount == 2) {
                    return Arrays.copyOfRange(data, 0, i+1); // +1 to include the comma itself.
                }
            }
        }
        throw new SaslException("Invalid GS2 header format");
    }
}
