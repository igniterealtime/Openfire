/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.StringUtils;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.security.sasl.SaslException;
import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests that verify the implementation of {@link ScramSha1SaslServer}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ScramSha1SaslServerTest extends AbstractScramSaslServerTest
{
    /**
     * Configures the AuthFactory mock to return the canonical test fixtures for salt, iterations,
     * password, stored key, and server key used by SCRAM-SHA-1 unit tests.
     */
    @Override
    protected void setupCanonicalAuthData()
    {
        authFactory.when(() -> AuthFactory.getSalt(anyString(), eq(ScramSha1SaslServer.MECHANISM_NAME))).thenReturn(ScramSha1TestFixtures.SALT);
        authFactory.when(() -> AuthFactory.getIterations(anyString(), eq(ScramSha1SaslServer.MECHANISM_NAME))).thenReturn(ScramSha1TestFixtures.ITERATIONS);
        authFactory.when(() -> AuthFactory.getPassword(any())).thenReturn(ScramSha1TestFixtures.PASSWORD);
        authFactory.when(() -> AuthFactory.getStoredKey(anyString(), eq(ScramSha1SaslServer.MECHANISM_NAME))).thenReturn(DatatypeConverter.printBase64Binary(StringUtils.decodeHex(ScramSha1TestFixtures.STORED_KEY_HEX)));
        authFactory.when(() -> AuthFactory.getServerKey(anyString(), eq(ScramSha1SaslServer.MECHANISM_NAME))).thenReturn(DatatypeConverter.printBase64Binary(StringUtils.decodeHex(ScramSha1TestFixtures.SERVER_KEY_HEX)));
    }

    /**
     * Computes a valid SCRAM-SHA-1 client proof for a given SCRAM exchange state.
     *
     * @param initialMessage      the raw bytes of the initial client message sent during the first exchange step
     * @param firstServerResponse the server's first response containing nonce, salt, and iterations
     * @param firstExchangeResult the parsed result of the first server response
     * @param clientFinalMessageWithoutProof  the exact client-final-message-without-proof string (e.g.
     *                                         "c=biws,r=<nonce>" or "c=biws,r=<nonce>,x=ignored") that the proof
     *                                         must be computed over; callers use this to verify that extensions
     *                                         are correctly folded into AuthMessage, not merely tolerated at parsing
     * @return the Base64-encoded client proof
     * @throws Exception if key derivation or HMAC computation fails
     */
    @Override
    protected String createValidProof(final byte[] initialMessage, final String firstServerResponse, final FirstExchangeResult firstExchangeResult, final String clientFinalMessageWithoutProof) throws Exception
    {
        final SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        final KeySpec spec = new PBEKeySpec(ScramSha1TestFixtures.PASSWORD.toCharArray(), firstExchangeResult.salt, firstExchangeResult.iterations, expectedProofLengthBytes()*8); // SCRAM derives the salted password at the hash's native output length, which equals the proof length
        final byte[] saltedPassword = factory.generateSecret(spec).getEncoded();

        final byte[] clientKey       = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(ScramSha1TestFixtures.CLIENT_KEY);
        final byte[] storedKey       = StringUtils.decodeHex(StringUtils.hash(clientKey, ScramSha1SaslServer.DIGEST_ALGORITHM_NAME));
        final String authMessage     = "n=" + ScramSha1TestFixtures.USER + ",r=" + ScramSha1TestFixtures.CLIENT_NONCE + "," + firstServerResponse + "," + clientFinalMessageWithoutProof;
        final byte[] clientSignature = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, storedKey).hmac(authMessage);

        final byte[] clientProof = new byte[clientKey.length];
        for (int i = 0; i < clientKey.length; i++) {
            clientProof[i] = (byte) (clientKey[i] ^ clientSignature[i]);
        }

        return Base64.getEncoder().encodeToString(clientProof);
    }

    /**
     * Returns the username used throughout SCRAM-SHA-1 unit tests.
     *
     * @return the test username
     */
    @Override
    protected String username()
    {
        return ScramSha1TestFixtures.USER;
    }

    /**
     * Returns the client nonce used throughout SCRAM-SHA-1 unit tests.
     *
     * @return the test client nonce
     */
    @Override
    protected String clientNonce()
    {
        return ScramSha1TestFixtures.CLIENT_NONCE;
    }

    /**
     * Returns the expected byte length of a SCRAM-SHA-1 client proof (20 bytes for SHA-1).
     *
     * @return expected proof length in bytes
     */
    @Override
    protected int expectedProofLengthBytes()
    {
        return 20;
    }

    /**
     * Creates a new ScramSha1SaslServer instance configured for either the plain or PLUS variant.
     *
     * @param isPlusMechanism true to create a SCRAM-SHA-1-PLUS server, false for SCRAM-SHA-1
     * @return a new ScramSha1SaslServer
     */
    @Nonnull
    @Override
    protected ScramSha1SaslServer newServer(final boolean isPlusMechanism)
    {
        return new ScramSha1SaslServer(isPlusMechanism, new HashMap<>(), ScramSha1TestFixtures.SUPPORTED_MECHANISMS, ScramSha1TestFixtures.SUPPORTED_CHANNEL_BINDING_TYPES);
    }

    /**
     * Creates a new ScramSha1SaslServer instance configured for either the plain or PLUS variant.
     *
     * @param isPlusMechanism true to create a SCRAM-SHA-1-PLUS server, false for SCRAM-SHA-1
     * @param advertisedMechanismNames The names of SASL mechanisms that are advertised to the peer.
     */
    protected ScramSha1SaslServer newServer(boolean isPlusMechanism, Set<String> advertisedMechanismNames)
    {
        return new ScramSha1SaslServer(isPlusMechanism, new HashMap<>(), advertisedMechanismNames, ScramSha1TestFixtures.SUPPORTED_CHANNEL_BINDING_TYPES);
    }

    /**
     * Creates a new ScramSha1SaslServer instance with specific advertised mechanisms and channel-binding types.
     *
     * @param isPlusMechanism true to create a SCRAM-SHA-1-PLUS server, false for SCRAM-SHA-1
     * @param advertisedMechanismNames The names of SASL mechanisms that are advertised to the peer.
     * @param advertisedChannelBindingTypes The names of channel binding types that are advertised to the peer.
     */
    @Nonnull
    @Override
    protected ScramSha1SaslServer newServer(final boolean isPlusMechanism, final Set<String> advertisedMechanismNames, final Set<String> advertisedChannelBindingTypes)
    {
        return new ScramSha1SaslServer(isPlusMechanism, new HashMap<>(), advertisedMechanismNames, advertisedChannelBindingTypes);
    }

    /**
     * Returns the SHA-1 downgrade protection hashes for the XEP-0474 test vectors.
     */
    @Override
    protected Map<SsdpTestVector, String> expectedDowngradeProtectionHashes()
    {
        return ScramSha1TestFixtures.SSDP_HASHES;
    }

    /**
     * Implements the example on <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM</a>
     */
    @Test
    public void testSuccess() throws Exception
    {
        // Setup test fixture
        final SecretKeyFactory pbkdf2Factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        setupCanonicalAuthData();

        // Setup test fixture: prepare initial client message.
        final ScramSha1SaslServer server = newServer(false);
        final String gs2Header = "n,,";
        final byte[] initialMessage = createClientInitialMessage(gs2Header, ScramSha1TestFixtures.USER, ScramSha1TestFixtures.CLIENT_NONCE);

        // Execute system under test: getting the first server message.
        final String firstServerResponse = new String(server.evaluateResponse(initialMessage), StandardCharsets.UTF_8); // r=%s,s=%s,i=%d

        // Verify result (first server message should match a pattern, and contain a number of properties)
        final FirstExchangeResult firstExchangeResult = FirstExchangeResult.fromFirstServerResponse(firstServerResponse);
        assertTrue(firstExchangeResult.serverNonce != null && !firstExchangeResult.serverNonce.isBlank(), "First server message should contain a non-empty server nonce (but did not)");
        assertTrue(firstExchangeResult.serverNonce.startsWith(ScramSha1TestFixtures.CLIENT_NONCE), "First server message should contain a server nonce that starts with the client nonce, but did not.");
        assertArrayEquals(DatatypeConverter.parseBase64Binary(ScramSha1TestFixtures.SALT), firstExchangeResult.salt, "First server message should include the 'salt' value configured for this unit test (but did not)");
        assertEquals(ScramSha1TestFixtures.ITERATIONS, firstExchangeResult.iterations, "First server message should include the 'iterations' value configured for this unit test (but did not)");

        // Setup test fixture: prepare second client message.
        final String clientFinalMessageBare = "c=biws,r=" + firstExchangeResult.serverNonce;

        final KeySpec saltedPasswordSpec = new PBEKeySpec(ScramSha1TestFixtures.PASSWORD.toCharArray(), firstExchangeResult.salt, firstExchangeResult.iterations, expectedProofLengthBytes()*8); // SCRAM derives the salted password at the hash's native output length, which equals the proof length
        final byte[] saltedPassword = pbkdf2Factory.generateSecret(saltedPasswordSpec).getEncoded();

        final byte[] clientKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(ScramSha1TestFixtures.CLIENT_KEY);
        final byte[] storedKey = StringUtils.decodeHex(StringUtils.hash(clientKey, ScramSha1SaslServer.DIGEST_ALGORITHM_NAME));
        final String authMessage = new String(initialMessage, StandardCharsets.UTF_8).substring(gs2Header.length()) + "," + firstServerResponse + "," + clientFinalMessageBare;

        final byte[] clientSignature = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, storedKey).hmac(authMessage);
        final byte[] clientProof = new byte[clientKey.length];
        for (int i=0; i<clientKey.length; i++) {
            clientProof[i] = (byte) (clientKey[i] ^ clientSignature[i]);
        }

        final byte[] serverKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(ScramSha1TestFixtures.SERVER_KEY);
        final byte[] serverSignature = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, serverKey).hmac(authMessage);
        final String clientFinalMessage = clientFinalMessageBare + ",p=" + Base64.getEncoder().encodeToString(clientProof);

        try {
            // Execute system under test: getting the final server message.
            final byte[] serverFinalMessage = server.evaluateResponse(clientFinalMessage.getBytes(StandardCharsets.UTF_8));

            // Verify result: final server message should contain the calculated server signature.
            assertEquals(StringUtils.encodeHex(serverSignature), StringUtils.encodeHex(DatatypeConverter.parseBase64Binary(new String(serverFinalMessage, StandardCharsets.UTF_8).substring(2))));
        } catch (SaslException e) {
            fail("Authentication should not fail (but it did)");
        }
    }

    /**
     * Implements a successful SCRAM-SHA-1-PLUS exchange with channel binding.
     */
    @Test
    public void testSuccessPlus() throws Exception
    {
        // Setup test fixture
        final SecretKeyFactory pbkdf2Factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        final String channelBindingType = "tls-server-end-point";
        final byte[] channelBindingData = "mocked-channel-binding-data".getBytes(StandardCharsets.UTF_8);

        final LocalSession mockSession = mock(LocalSession.class);
        final Connection mockConnection = mock(Connection.class);
        when(mockConnection.getChannelBindingData(channelBindingType)).thenReturn(Optional.of(channelBindingData));
        when(mockSession.getConnection()).thenReturn(mockConnection);

        setupCanonicalAuthData();

        // Setup test fixture: prepare initial client message with channel binding.
        final Map<String, Object> props = new HashMap<>();
        props.put(LocalSession.class.getCanonicalName(), mockSession);
        final ScramSha1SaslServer server = new ScramSha1SaslServer(true, props, ScramSha1TestFixtures.SUPPORTED_MECHANISMS, ScramSha1TestFixtures.SUPPORTED_CHANNEL_BINDING_TYPES);
        final String gs2Header = "p=" + channelBindingType + ",,";
        final byte[] initialMessage = createClientInitialMessage(gs2Header, ScramSha1TestFixtures.USER, ScramSha1TestFixtures.CLIENT_NONCE);

        // Execute system under test: getting the first server message.
        final String firstServerResponse = new String(server.evaluateResponse(initialMessage), StandardCharsets.UTF_8);

        // Verify result (first server message should match a pattern, and contain a number of properties)
        final FirstExchangeResult firstExchangeResult = FirstExchangeResult.fromFirstServerResponse(firstServerResponse);
        assertTrue(firstExchangeResult.serverNonce != null && !firstExchangeResult.serverNonce.isBlank(), "First server message should contain a non-empty server nonce (but did not)");
        assertTrue(firstExchangeResult.serverNonce.startsWith(ScramSha1TestFixtures.CLIENT_NONCE), "First server message should contain a server nonce that starts with the client nonce, but did not.");
        assertArrayEquals(DatatypeConverter.parseBase64Binary(ScramSha1TestFixtures.SALT), firstExchangeResult.salt, "First server message should include the 'salt' value configured for this unit test (but did not)");
        assertEquals(ScramSha1TestFixtures.ITERATIONS, firstExchangeResult.iterations, "First server message should include the 'iterations' value configured for this unit test (but did not)");

        // Setup test fixture: prepare second client message with channel binding.
        final byte[] gs2HeaderBytes = gs2Header.getBytes(StandardCharsets.UTF_8);
        final byte[] cbindInput = new byte[gs2HeaderBytes.length + channelBindingData.length];
        System.arraycopy(gs2HeaderBytes, 0, cbindInput, 0, gs2HeaderBytes.length);
        System.arraycopy(channelBindingData, 0, cbindInput, gs2HeaderBytes.length, channelBindingData.length);
        final String clientFinalMessageBare = "c=" + Base64.getEncoder().encodeToString(cbindInput) + ",r=" + firstExchangeResult.serverNonce;

        final KeySpec saltedPasswordSpec = new PBEKeySpec(ScramSha1TestFixtures.PASSWORD.toCharArray(), firstExchangeResult.salt, firstExchangeResult.iterations, expectedProofLengthBytes()*8); // SCRAM derives the salted password at the hash's native output length, which equals the proof length
        final byte[] saltedPassword = pbkdf2Factory.generateSecret(saltedPasswordSpec).getEncoded();

        final byte[] clientKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(ScramSha1TestFixtures.CLIENT_KEY);
        final byte[] storedKey = StringUtils.decodeHex(StringUtils.hash(clientKey, ScramSha1SaslServer.DIGEST_ALGORITHM_NAME));
        final String authMessage = new String(initialMessage, StandardCharsets.UTF_8).substring(gs2Header.length()) + "," + firstServerResponse + "," + clientFinalMessageBare;

        final byte[] clientSignature = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, storedKey).hmac(authMessage);
        final byte[] clientProof = new byte[clientKey.length];
        for (int i=0; i<clientKey.length; i++) {
            clientProof[i] = (byte) (clientKey[i] ^ clientSignature[i]);
        }

        final byte[] serverKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(ScramSha1TestFixtures.SERVER_KEY);
        final byte[] serverSignature = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, serverKey).hmac(authMessage);
        final String clientFinalMessage = clientFinalMessageBare + ",p=" + Base64.getEncoder().encodeToString(clientProof);

        try {
            // Execute system under test: getting the final server message.
            final byte[] serverFinalMessage = server.evaluateResponse(clientFinalMessage.getBytes(StandardCharsets.UTF_8));

            // Verify result: final server message should contain the calculated server signature.
            assertEquals(StringUtils.encodeHex(serverSignature), StringUtils.encodeHex(DatatypeConverter.parseBase64Binary(new String(serverFinalMessage, StandardCharsets.UTF_8).substring(2))));
        } catch (SaslException e) {
            fail("Authentication should not fail (but it did)");
        }
    }

    /**
     * Verifies the mechanism name for non-PLUS instances.
     */
    @Test
    void getMechanismName_returnsScramSha1_forNonPlusMechanism()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = newServer(false);

        // Execute system under test
        final String mechanismName = server.getMechanismName();

        // Verify result
        assertEquals(ScramSha1SaslServer.MECHANISM_NAME, mechanismName, "Non-PLUS mechanism should return 'SCRAM-SHA-1' as its name");
    }

    /**
     * Verifies the mechanism name for PLUS instances.
     */
    @Test
    void getMechanismName_returnsScramSha1Plus_forPlusMechanism()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = newServer(true);

        // Execute system under test
        final String mechanismName = server.getMechanismName();

        // Verify result
        assertEquals(ScramSha1SaslServer.MECHANISM_NAME+"-PLUS", mechanismName, "PLUS mechanism should return 'SCRAM-SHA-1-PLUS' as its name");
    }

    /**
     * Verifies that a refused SCRAM attempt (a locked account, or a blocked username or address) goes through the same
     * steps as an attempt for a user that does not exist: a normal server-first message with a fake salt and the
     * default iteration count, then a failure at client-final whatever the proof. It performs the same salt and
     * iteration lookups, so that it takes as long, but never looks up a key. Otherwise anyone could learn, without a
     * password, that an existing account is locked (OF-2279).
     */
    @Test
    public void testRefusedAttemptLooksLikeUnknownUser() throws Exception
    {
        // Setup test fixture.
        final String username = "lockeduser";
        // The username has no account, for both exchanges.
        authFactory.when(() -> AuthFactory.getSalt(eq(username), anyString())).thenThrow(new UserNotFoundException());
        authFactory.when(() -> AuthFactory.getIterations(eq(username), anyString())).thenThrow(new UserNotFoundException());
        authFactory.when(() -> AuthFactory.getStoredKey(eq(username), anyString())).thenThrow(new UserNotFoundException());
        authFactory.when(() -> AuthFactory.getServerKey(eq(username), anyString())).thenThrow(new UserNotFoundException());
        final LocalSession session = mock(LocalSession.class);
        when(session.getHostAddress()).thenReturn("192.0.2.1");
        when(loginLimits.tryAcquireAttempt(username, "192.0.2.1")).thenReturn(null);
        final Map<String, Object> props = new HashMap<>();
        props.put(LocalSession.class.getCanonicalName(), session);
        final ScramSha1SaslServer refused = new ScramSha1SaslServer(false, props, ScramSha1TestFixtures.SUPPORTED_MECHANISMS, ScramSha1TestFixtures.SUPPORTED_CHANNEL_BINDING_TYPES);

        // Execute system under test: the refused exchange.
        final FirstExchangeResult refusedFirst = FirstExchangeResult.fromFirstServerResponse(new String(refused.evaluateResponse(createClientInitialMessage("n,,", username, ScramSha1TestFixtures.CLIENT_NONCE)), StandardCharsets.UTF_8));
        final SaslException refusedFinal = assertThrows(SaslException.class, () -> refused.evaluateResponse(finalMessageWithProof(refusedFirst.serverNonce, new byte[expectedProofLengthBytes()])), "a refused attempt must fail at client-final");

        // Verify result: the refused exchange looked up the salt and iteration count, as an unknown user's exchange
        // does (so that a refusal takes as long), and no key, so that no proof could be verified.
        authFactory.verify(() -> AuthFactory.getSalt(eq(username), anyString()), times(1).description("a refused attempt must look up the salt, as an unknown user's attempt does"));
        authFactory.verify(() -> AuthFactory.getIterations(eq(username), anyString()), times(1).description("a refused attempt must look up the iteration count, as an unknown user's attempt does"));
        authFactory.verify(() -> AuthFactory.getStoredKey(anyString(), anyString()), never().description("a refused attempt must not look up the stored key"));
        authFactory.verify(() -> AuthFactory.getServerKey(anyString(), anyString()), never().description("a refused attempt must not look up the server key"));
        authFactory.verify(() -> AuthFactory.setPassword(anyString(), anyString()), never().description("a refused attempt must not regenerate credentials"));
        verify(session, never().description("a refused attempt must not be recorded")).setSessionData(eq(SASLAuthentication.SASL_LOGIN_ATTEMPT), any());

        // Execute system under test: the same username, not refused.
        final ScramSha1SaslServer unknown = newServer(false);
        final FirstExchangeResult unknownFirst = FirstExchangeResult.fromFirstServerResponse(new String(unknown.evaluateResponse(createClientInitialMessage("n,,", username, ScramSha1TestFixtures.CLIENT_NONCE)), StandardCharsets.UTF_8));
        final SaslException unknownFinal = assertThrows(SaslException.class, () -> unknown.evaluateResponse(finalMessageWithProof(unknownFirst.serverNonce, new byte[expectedProofLengthBytes()])), "an unknown user must fail at client-final");

        // Verify result: the two exchanges look the same.
        assertArrayEquals(unknownFirst.salt, refusedFirst.salt, "a refused attempt must be given the salt an unknown user gets");
        assertEquals(unknownFirst.iterations, refusedFirst.iterations, "a refused attempt must be given the iteration count an unknown user gets");
        assertEquals(unknownFinal.getMessage(), refusedFinal.getMessage(), "a refused attempt must fail at client-final as an unknown user does");
    }

    /**
     * Verifies that a refused SCRAM attempt for an existing account is sent the same server-first message (the
     * account's own salt and iteration count) as an attempt that is not refused, so that a refusal (of one address, for
     * example) does not reveal that the account exists or that a block applies. No key is looked up, so no proof can be
     * verified, and the attempt fails at client-final (OF-2279).
     */
    @Test
    public void testRefusedAttemptForExistingUserSendsTheAccountsSaltAndIterations() throws Exception
    {
        // Setup test fixture.
        setupCanonicalAuthData();
        final ScramSha1SaslServer refused = refusedServer(ScramSha1TestFixtures.USER);

        // Execute system under test: the refused exchange.
        final FirstExchangeResult refusedFirst = FirstExchangeResult.fromFirstServerResponse(new String(refused.evaluateResponse(createClientInitialMessage("n,,", ScramSha1TestFixtures.USER, ScramSha1TestFixtures.CLIENT_NONCE)), StandardCharsets.UTF_8));
        assertThrows(SaslException.class, () -> refused.evaluateResponse(finalMessageWithProof(refusedFirst.serverNonce, new byte[expectedProofLengthBytes()])), "a refused attempt must fail at client-final");

        // Verify result: no key was looked up and nothing was changed for the refused exchange.
        authFactory.verify(() -> AuthFactory.getStoredKey(anyString(), anyString()), never().description("a refused attempt must not look up the stored key"));
        authFactory.verify(() -> AuthFactory.getServerKey(anyString(), anyString()), never().description("a refused attempt must not look up the server key"));
        authFactory.verify(() -> AuthFactory.setPassword(anyString(), anyString()), never().description("a refused attempt must not change credentials"));

        // Execute system under test: the same account, not refused.
        final FirstExchangeResult allowedFirst = FirstExchangeResult.fromFirstServerResponse(new String(newServer(false).evaluateResponse(createClientInitialMessage("n,,", ScramSha1TestFixtures.USER, ScramSha1TestFixtures.CLIENT_NONCE)), StandardCharsets.UTF_8));

        // Verify result: the two server-first messages carry the same salt and iteration count.
        assertArrayEquals(allowedFirst.salt, refusedFirst.salt, "a refused attempt for an existing account must be sent the account's own salt");
        assertEquals(allowedFirst.iterations, refusedFirst.iterations, "a refused attempt for an existing account must be sent the account's own iteration count");
    }

    /**
     * Verifies that a refused SCRAM attempt for an account that has no SCRAM salt is sent a fake salt and never causes
     * one to be created: creating it would reset the account's password, which a refused attempt must not do.
     */
    @Test
    public void testRefusedAttemptForAccountWithoutSaltDoesNotCreateOne() throws Exception
    {
        // Setup test fixture.
        final String username = "nosalt";
        authFactory.when(() -> AuthFactory.getSalt(eq(username), anyString())).thenReturn(null);
        authFactory.when(() -> AuthFactory.getIterations(eq(username), anyString())).thenReturn(ScramSha1TestFixtures.ITERATIONS);
        final ScramSha1SaslServer refused = refusedServer(username);

        // Execute system under test.
        final FirstExchangeResult refusedFirst = FirstExchangeResult.fromFirstServerResponse(new String(refused.evaluateResponse(createClientInitialMessage("n,,", username, ScramSha1TestFixtures.CLIENT_NONCE)), StandardCharsets.UTF_8));

        // Verify result.
        assertTrue(refusedFirst.salt.length > 0, "a refused attempt must still be sent a salt");
        assertEquals(ScramSha1TestFixtures.ITERATIONS, refusedFirst.iterations, "a refused attempt must be sent the account's own iteration count");
        authFactory.verify(() -> AuthFactory.getPassword(anyString()), never().description("a refused attempt must not read the password to create a salt"));
        authFactory.verify(() -> AuthFactory.setPassword(anyString(), anyString()), never().description("a refused attempt must not reset the password to create a salt"));
    }

    private ScramSha1SaslServer refusedServer(final String username) throws Exception
    {
        final LocalSession session = mock(LocalSession.class);
        when(session.getHostAddress()).thenReturn("192.0.2.1");
        when(loginLimits.tryAcquireAttempt(username, "192.0.2.1")).thenReturn(null);
        final Map<String, Object> props = new HashMap<>();
        props.put(LocalSession.class.getCanonicalName(), session);
        return new ScramSha1SaslServer(false, props, ScramSha1TestFixtures.SUPPORTED_MECHANISMS, ScramSha1TestFixtures.SUPPORTED_CHANNEL_BINDING_TYPES);
    }

    private static byte[] finalMessageWithProof(final String serverNonce, final byte[] proof)
    {
        return ("c=biws,r=" + serverNonce + ",p=" + Base64.getEncoder().encodeToString(proof)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Verifies that a SCRAM attempt is counted against the login limits when its username is known, and records the
     * decoded username on the session so that a success can give the attempt back (OF-2279).
     */
    @Test
    public void testAttemptIsCountedAndRecordsUsername() throws Exception
    {
        // Setup test fixture.
        setupCanonicalAuthData();
        final LocalSession session = mock(LocalSession.class);
        when(session.getHostAddress()).thenReturn("192.0.2.1");
        final Map<String, Object> props = new HashMap<>();
        props.put(LocalSession.class.getCanonicalName(), session);
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, props, ScramSha1TestFixtures.SUPPORTED_MECHANISMS, ScramSha1TestFixtures.SUPPORTED_CHANNEL_BINDING_TYPES);

        // Execute system under test.
        server.evaluateResponse(createClientInitialMessage("n,,", ScramSha1TestFixtures.USER, ScramSha1TestFixtures.CLIENT_NONCE));

        // Verify result.
        verify(loginLimits, times(1).description("the attempt must be counted against the login limits with the session's address")).tryAcquireAttempt(ScramSha1TestFixtures.USER, "192.0.2.1");
        verify(session, times(1).description("the attempt's reservation must be recorded on the session")).setSessionData(eq(SASLAuthentication.SASL_LOGIN_ATTEMPT), any());
    }
}
