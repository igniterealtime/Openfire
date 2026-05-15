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
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.channelbinding.ChannelBindingProvider;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests that verify the implementation of {@link ScramSha1SaslServer}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ScramSha1SaslServerTest extends AbstractScramSaslServerTest
{
    private MockedStatic<AuthFactory> authFactory;

    /**
     * Initializes the static mock for AuthFactory before each test.
     */
    @BeforeEach
    public void setupStaticMock() {
        authFactory = Mockito.mockStatic(AuthFactory.class);
    }

    /**
     * Releases the static mock for AuthFactory after each test.
     */
    @AfterEach
    public void teardownStaticMock() {
        if (authFactory != null) {
            authFactory.close();
        }
    }

    /**
     * Configures the AuthFactory mock to return the canonical test fixtures for salt, iterations,
     * password, stored key, and server key used by SCRAM-SHA-1 unit tests.
     */
    @Override
    protected void setupCanonicalAuthData()
    {
        authFactory.when(() -> AuthFactory.getSalt(any())).thenReturn(ScramSha1TestFixtures.SALT);
        authFactory.when(() -> AuthFactory.getIterations(any())).thenReturn(ScramSha1TestFixtures.ITERATIONS);
        authFactory.when(() -> AuthFactory.getPassword(any())).thenReturn(ScramSha1TestFixtures.PASSWORD);
        authFactory.when(() -> AuthFactory.getStoredKey(any())).thenReturn(DatatypeConverter.printBase64Binary(StringUtils.decodeHex(ScramSha1TestFixtures.STORED_KEY_BASE64)));
        authFactory.when(() -> AuthFactory.getServerKey(any())).thenReturn(DatatypeConverter.printBase64Binary(StringUtils.decodeHex(ScramSha1TestFixtures.SERVER_KEY_BASE64)));
    }

    /**
     * Computes a valid SCRAM-SHA-1 client proof for a given SCRAM exchange state.
     *
     * @param initialMessage      the raw bytes of the initial client message sent during the first exchange step
     * @param firstServerResponse the server's first response containing nonce, salt, and iterations
     * @param firstExchangeResult the parsed result of the first server response
     * @return the Base64-encoded client proof
     * @throws Exception if key derivation or HMAC computation fails
     */
    @Override
    protected String createValidProof(final byte[] initialMessage, final String firstServerResponse, final FirstExchangeResult firstExchangeResult) throws Exception
    {
        final SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        final KeySpec spec = new PBEKeySpec(ScramSha1TestFixtures.PASSWORD.toCharArray(), firstExchangeResult.salt, firstExchangeResult.iterations, 160);
        final byte[] saltedPassword = factory.generateSecret(spec).getEncoded();

        final byte[] clientKey       = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(ScramSha1TestFixtures.CLIENT_KEY);
        final byte[] storedKey       = StringUtils.decodeHex(StringUtils.hash(clientKey, "SHA-1"));
        final String clientFinalBare = "c=biws,r=" + firstExchangeResult.serverNonce;
        final String authMessage     = "n=" + ScramSha1TestFixtures.USER + ",r=" + ScramSha1TestFixtures.CLIENT_NONCE + "," + firstServerResponse + "," + clientFinalBare;
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
        return new ScramSha1SaslServer(isPlusMechanism, new HashMap<>(), new ChannelBindingProviderManager(), ScramSha1TestFixtures.SUPPORTED_MECHANISMS);
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

        final KeySpec saltedPasswordSpec = new PBEKeySpec(ScramSha1TestFixtures.PASSWORD.toCharArray(), firstExchangeResult.salt, firstExchangeResult.iterations, 20*8);
        final byte[] saltedPassword = pbkdf2Factory.generateSecret(saltedPasswordSpec).getEncoded();

        final byte[] clientKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(ScramSha1TestFixtures.CLIENT_KEY);
        final byte[] storedKey = StringUtils.decodeHex(StringUtils.hash(clientKey, "SHA-1"));
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

        final ChannelBindingProviderManager channelBindingProviderManager = new ChannelBindingProviderManager();
        final ChannelBindingProvider serverEndPointProvider = mock(ChannelBindingProvider.class);
        when(serverEndPointProvider.getType()).thenReturn("tls-server-end-point");
        when(serverEndPointProvider.getChannelBinding(any())).thenReturn(Optional.of(channelBindingData));

        final LocalSession mockSession = mock(LocalSession.class);
        final Connection mockConnection = mock(Connection.class);
        when(mockConnection.getChannelBindingData(channelBindingType)).thenReturn(Optional.of(channelBindingData));
        when(mockSession.getConnection()).thenReturn(mockConnection);

        channelBindingProviderManager.addProvider(serverEndPointProvider);

        setupCanonicalAuthData();

        // Setup test fixture: prepare initial client message with channel binding.
        final Map<String, Object> props = new HashMap<>();
        props.put(LocalSession.class.getCanonicalName(), mockSession);
        final ScramSha1SaslServer server = new ScramSha1SaslServer(true, props, channelBindingProviderManager, ScramSha1TestFixtures.SUPPORTED_MECHANISMS);
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

        final KeySpec saltedPasswordSpec = new PBEKeySpec(ScramSha1TestFixtures.PASSWORD.toCharArray(), firstExchangeResult.salt, firstExchangeResult.iterations, 20*8);
        final byte[] saltedPassword = pbkdf2Factory.generateSecret(saltedPasswordSpec).getEncoded();

        final byte[] clientKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(ScramSha1TestFixtures.CLIENT_KEY);
        final byte[] storedKey = StringUtils.decodeHex(StringUtils.hash(clientKey, "SHA-1"));
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
        assertEquals(ScramSha1TestFixtures.MECHANISM, mechanismName, "Non-PLUS mechanism should return 'SCRAM-SHA-1' as its name");
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
        assertEquals(ScramSha1TestFixtures.PLUS_MECHANISM, mechanismName, "PLUS mechanism should return 'SCRAM-SHA-1-PLUS' as its name");
    }
}
