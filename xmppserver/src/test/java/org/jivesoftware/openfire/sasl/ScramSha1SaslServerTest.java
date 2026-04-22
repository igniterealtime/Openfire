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

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
public class ScramSha1SaslServerTest
{
    private MockedStatic<AuthFactory> authFactory;

    @BeforeEach
    public void setupStaticMock() {
        authFactory = Mockito.mockStatic(AuthFactory.class);
    }

    @AfterEach
    public void teardownStaticMock() {
        if (authFactory != null) {
            authFactory.close();
        }
    }

    /**
     * Implements the example on <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM</a>
     */
    @Test
    public void testSuccess() throws Exception
    {
        // Setup test fixture
        final SecretKeyFactory HmacSHA1Factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

        final String hardCodedClientNonce = "fyko+d2lbbFgONRv9qkxdawL";
        final String hardCodedSalt = "QSXCR+Q6sek8bf92";
        final int hardCodedIterations = 4096;
        final String hardCodedPassword = "pencil";
        final String hardCodedClientKey = "Client Key";
        final String hardCodedServerKey = "Server Key";

        authFactory.when(() -> AuthFactory.getSalt(any())).thenReturn(hardCodedSalt);
        authFactory.when(() -> AuthFactory.getIterations(any())).thenReturn(hardCodedIterations);
        authFactory.when(() -> AuthFactory.getPassword(any())).thenReturn(hardCodedPassword);
        authFactory.when(() -> AuthFactory.getStoredKey(any())).thenReturn(DatatypeConverter.printBase64Binary(StringUtils.decodeHex("e9d94660c39d65c38fbad91c358f14da0eef2bd6")));
        authFactory.when(() -> AuthFactory.getServerKey(any())).thenReturn(DatatypeConverter.printBase64Binary(StringUtils.decodeHex("0fe09258b3ac852ba502cc62ba903eaacdbf7d31")));

        // Setup test fixture: prepare initial client message.
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        final byte[] initialMessage = ("n,,n=user,r=" + hardCodedClientNonce).getBytes(StandardCharsets.UTF_8);

        // Execute system under test: getting the first server message.
        final String firstServerResponse = new String(server.evaluateResponse(initialMessage), StandardCharsets.UTF_8); // r=%s,s=%s,i=%d

        // Verify result (first server message should match a pattern, and contain a number of properties)
        final Matcher firstServerResponseMatcher = Pattern.compile("r=([^,]*),s=([^,]*),i=(.*)$").matcher(firstServerResponse);
        assertTrue(firstServerResponseMatcher.matches(), "First server message does not match expected pattern.");
        final String serverNonce = firstServerResponseMatcher.group(1);
        assertTrue(serverNonce != null && !serverNonce.isBlank(), "First server message should contain a non-empty server nonce (but did not)");
        assertTrue(serverNonce.startsWith(hardCodedClientNonce), "First server message should contain a server nonce that starts with the client nonce, but did not.");


        byte[] salt = null;
        try {
            salt = DatatypeConverter.parseBase64Binary(firstServerResponseMatcher.group(2));
            assertEquals(hardCodedSalt, firstServerResponseMatcher.group(2), "First server message should include the 'salt' value configured for this unit test (but did not)");
        } catch (IllegalArgumentException e) {
            fail("First server message should contain a valid 'salt' value (but did not).");
        }

        int iterations = -1;
        try {
            iterations = Integer.parseInt(firstServerResponseMatcher.group(3));
            assertEquals(hardCodedIterations, iterations, "First server message should include the 'iterations' value configured for this unit test (but did not)");
        } catch (NumberFormatException e) {
            fail("First server message should contain a valid 'iterations' value (but did not).");
        }

        // Setup test fixture: prepare second client message.
        final String clientFinalMessageBare = "c=biws,r=" + serverNonce;

        final KeySpec saltedPasswordSpec = new PBEKeySpec(hardCodedPassword.toCharArray(), salt, iterations, 20*8);
        final byte[] saltedPassword = HmacSHA1Factory.generateSecret(saltedPasswordSpec).getEncoded();

        final byte[] clientKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(hardCodedClientKey);
        final byte[] storedKey = StringUtils.decodeHex(StringUtils.hash(clientKey, "SHA-1"));
        final String authMessage = new String(initialMessage, StandardCharsets.UTF_8).substring(3) + "," + firstServerResponse + "," + clientFinalMessageBare;

        final byte[] clientSignature = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, storedKey).hmac(authMessage);
        final byte[] clientProof = new byte[clientKey.length];
        for (int i=0; i<clientKey.length; i++) {
            clientProof[i] = (byte) (clientKey[i] ^ clientSignature[i]);
        }

        final byte[] serverKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(hardCodedServerKey);
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
        final SecretKeyFactory HmacSHA1Factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        final String hardCodedClientNonce = "fyko+d2lbbFgONRv9qkxdawL";
        final String hardCodedSalt = "QSXCR+Q6sek8bf92";
        final int hardCodedIterations = 4096;
        final String hardCodedPassword = "pencil";
        final String hardCodedClientKey = "Client Key";
        final String hardCodedServerKey = "Server Key";
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

        authFactory.when(() -> AuthFactory.getSalt(any())).thenReturn(hardCodedSalt);
        authFactory.when(() -> AuthFactory.getIterations(any())).thenReturn(hardCodedIterations);
        authFactory.when(() -> AuthFactory.getPassword(any())).thenReturn(hardCodedPassword);
        authFactory.when(() -> AuthFactory.getStoredKey(any())).thenReturn(DatatypeConverter.printBase64Binary(StringUtils.decodeHex("e9d94660c39d65c38fbad91c358f14da0eef2bd6")));
        authFactory.when(() -> AuthFactory.getServerKey(any())).thenReturn(DatatypeConverter.printBase64Binary(StringUtils.decodeHex("0fe09258b3ac852ba502cc62ba903eaacdbf7d31")));

        // Setup test fixture: prepare initial client message with channel binding.
        final Map<String, Object> props = new HashMap<>();
        props.put(LocalSession.class.getCanonicalName(), mockSession);
        final ScramSha1SaslServer server = new ScramSha1SaslServer(true, props, channelBindingProviderManager, Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        final String gs2Header = "p=" + channelBindingType + ",,";
        final byte[] initialMessage = (gs2Header + "n=user,r=" + hardCodedClientNonce).getBytes(StandardCharsets.UTF_8);

        // Execute system under test: getting the first server message.
        final String firstServerResponse = new String(server.evaluateResponse(initialMessage), StandardCharsets.UTF_8);

        // Verify result (first server message should match a pattern, and contain a number of properties)
        final Matcher firstServerResponseMatcher = Pattern.compile("r=([^,]*),s=([^,]*),i=(.*)$").matcher(firstServerResponse);
        assertTrue(firstServerResponseMatcher.matches(), "First server message does not match expected pattern.");
        final String serverNonce = firstServerResponseMatcher.group(1);
        assertTrue(serverNonce != null && !serverNonce.isBlank(), "First server message should contain a non-empty server nonce (but did not)");
        assertTrue(serverNonce.startsWith(hardCodedClientNonce), "First server message should contain a server nonce that starts with the client nonce, but did not.");

        byte[] salt = null;
        try {
            salt = DatatypeConverter.parseBase64Binary(firstServerResponseMatcher.group(2));
            assertEquals(hardCodedSalt, firstServerResponseMatcher.group(2), "First server message should include the 'salt' value configured for this unit test (but did not)");
        } catch (IllegalArgumentException e) {
            fail("First server message should contain a valid 'salt' value (but did not).");
        }

        int iterations = -1;
        try {
            iterations = Integer.parseInt(firstServerResponseMatcher.group(3));
            assertEquals(hardCodedIterations, iterations, "First server message should include the 'iterations' value configured for this unit test (but did not)");
        } catch (NumberFormatException e) {
            fail("First server message should contain a valid 'iterations' value (but did not).");
        }

        // Setup test fixture: prepare second client message with channel binding.
        final String gs2HeaderBase64 = Base64.getEncoder().encodeToString(gs2Header.getBytes(StandardCharsets.UTF_8));
        final String channelBindingBase64 = Base64.getEncoder().encodeToString(channelBindingData);
        final String clientFinalMessageBare = "c=" + gs2HeaderBase64 + channelBindingBase64 + ",r=" + serverNonce;

        final KeySpec saltedPasswordSpec = new PBEKeySpec(hardCodedPassword.toCharArray(), salt, iterations, 20*8);
        final byte[] saltedPassword = HmacSHA1Factory.generateSecret(saltedPasswordSpec).getEncoded();

        final byte[] clientKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(hardCodedClientKey);
        final byte[] storedKey = StringUtils.decodeHex(StringUtils.hash(clientKey, "SHA-1"));
        final String authMessage = new String(initialMessage, StandardCharsets.UTF_8).substring(gs2Header.length()) + "," + firstServerResponse + "," + clientFinalMessageBare;

        final byte[] clientSignature = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, storedKey).hmac(authMessage);
        final byte[] clientProof = new byte[clientKey.length];
        for (int i=0; i<clientKey.length; i++) {
            clientProof[i] = (byte) (clientKey[i] ^ clientSignature[i]);
        }

        final byte[] serverKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac(hardCodedServerKey);
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
     * Verifies GS2 header extraction when an authzid is present.
     */
    @Test
    void extractsGs2Header_withAuthzId() throws Exception
    {
        // Setup test fixture
        final byte[] input = "p=tls,,n=user,r=abc123,rest".getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSha1SaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals("p=tls,,", new String(result, StandardCharsets.UTF_8));
    }

    /**
     * Verifies GS2 header extraction when no authzid is present.
     */
    @Test
    void extractsGs2Header_withoutAuthzId() throws Exception
    {
        // Setup test fixture
        final byte[] input = "n,,n=user,r=abc123,rest".getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSha1SaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals("n,,", new String(result, StandardCharsets.UTF_8));
    }

    /**
     * Ensures the GS2 header includes a trailing comma as specified.
     */
    @Test
    void includesTrailingComma_exactlyAsSpecified() throws Exception
    {
        // Setup test fixture
        final byte[] input = "p=tls,,n=user,r=abc123".getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSha1SaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals(',', result[result.length - 1], "GS2 header must end with a comma");
    }

    /**
     * Ensures GS2 header extraction preserves the exact bytes, with no re-encoding.
     */
    @Test
    void preservesExactBytes_noReEncoding() throws Exception
    {
        // Setup test fixture
        final byte[] input = "p=tls,,n=user,r=abc123".getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSha1SaslServer.extractRawGS2Header(input);

        // Verify result
        byte[] expected = Arrays.copyOfRange(input, 0, result.length);
        assertArrayEquals(expected, result, "Must be exact prefix of original bytes");
    }

    /**
     * Verifies that an exception is thrown when the GS2 header does not contain a second comma.
     */
    @Test
    void throwsException_whenNoSecondComma()
    {
        // Setup test fixture
        final byte[] input = "p=tls,n=user".getBytes(StandardCharsets.UTF_8);

        // Execute System under test & Verify result
        assertThrows(SaslException.class, () ->
            ScramSha1SaslServer.extractRawGS2Header(input));
    }

    /**
     * Verifies that the minimal valid GS2 header is handled correctly.
     */
    @Test
    void handlesMinimalValidGs2Header() throws Exception
    {
        // Setup test fixture
        final byte[] input = "n,,rest".getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSha1SaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals("n,,", new String(result, StandardCharsets.UTF_8));
    }

    /**
     * Ensures GS2 header extraction stops at the second comma only.
     */
    @Test
    void stopsAtSecondComma_only() throws Exception
    {
        // Setup test fixture
        final byte[] input = "p=tls,,n=user,r=abc,extra,stuff".getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSha1SaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals("p=tls,,", new String(result, StandardCharsets.UTF_8));
    }

    /**
     * Verifies the mechanism name for non-PLUS instances.
     */
    @Test
    void getMechanismName_returnsScramSha1_forNonPlusMechanism()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test
        final String mechanismName = server.getMechanismName();

        // Verify result
        assertEquals("SCRAM-SHA-1", mechanismName, "Non-PLUS mechanism should return 'SCRAM-SHA-1' as its name");
    }

    /**
     * Verifies the mechanism name for PLUS instances.
     */
    @Test
    void getMechanismName_returnsScramSha1Plus_forPlusMechanism()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(true, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test
        final String mechanismName = server.getMechanismName();

        // Verify result
        assertEquals("SCRAM-SHA-1-PLUS", mechanismName, "PLUS mechanism should return 'SCRAM-SHA-1-PLUS' as its name");
    }

    /**
     * Verifies that a completely malformed first client message is rejected.
     */
    @Test
    void rejectsFirstMessage_invalidFormat()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test & Verify result
        assertThrows(SaslException.class,
            () -> server.evaluateResponse("not-a-valid-scram-message".getBytes(StandardCharsets.UTF_8)),
            "Malformed first client message should be rejected with SaslException");
    }

    /**
     * Verifies that a first client message containing an empty username is rejected.
     */
    @Test
    void rejectsFirstMessage_emptyUsername()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test & Verify result
        assertThrows(SaslException.class,
            () -> server.evaluateResponse("n,,n=,r=clientnonce".getBytes(StandardCharsets.UTF_8)),
            "First client message with empty username should be rejected");
    }

    /**
     * Verifies that a first client message containing an empty client nonce is rejected.
     */
    @Test
    void rejectsFirstMessage_emptyClientNonce()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test & Verify result
        assertThrows(SaslException.class,
            () -> server.evaluateResponse("n,,n=user,r=".getBytes(StandardCharsets.UTF_8)),
            "First client message with empty client nonce should be rejected");
    }

    /**
     * Verifies that a 'p' GS2 channel-binding flag is rejected when using the non-PLUS mechanism.
     */
    @Test
    void rejectsFirstMessage_channelBindingRequestedOnNonPlusMechanism()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test & Verify result
        assertThrows(SaslException.class,
            () -> server.evaluateResponse("p=tls-unique,,n=user,r=clientnonce".getBytes(StandardCharsets.UTF_8)),
            "Channel binding requested on non-PLUS mechanism should be rejected");
    }

    /**
     * Verifies RFC 5802 §6: a 'y' GS2 flag MUST be rejected when the server advertises a -PLUS mechanism,
     * because this is a signal that a downgrade attack may be in progress.
     */
    @Test
    void rejectsFirstMessage_downgradeAttackDetected()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse("y,,n=user,r=clientnonce".getBytes(StandardCharsets.UTF_8)),
            "Downgrade attack (y-flag) should be rejected when -PLUS is advertised");
    }

    /**
     * Verifies that a completely malformed final client message is rejected.
     */
    @Test
    void rejectsFinalMessage_invalidFormat() throws Exception
    {
        // Setup test fixture
        setupSaltAndIterations();
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        server.evaluateResponse("n,,n=user,r=clientnonce".getBytes(StandardCharsets.UTF_8));

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse("not-a-valid-final-message".getBytes(StandardCharsets.UTF_8)),
            "Malformed final client message should be rejected with SaslException");
    }

    /**
     * Verifies that a final client message with an empty proof attribute is rejected.
     */
    @Test
    void rejectsFinalMessage_emptyProof() throws Exception
    {
        // Setup test fixture
        setupSaltAndIterations();
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        final String serverNonce = doFirstExchange(server);

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(("c=biws,r=" + serverNonce + ",p=").getBytes(StandardCharsets.UTF_8)),
            "Final client message with empty proof should be rejected");
    }

    /**
     * Verifies that a final client message with an empty channel binding attribute is rejected.
     */
    @Test
    void rejectsFinalMessage_emptyChannelBinding() throws Exception
    {
        // Setup test fixture
        setupSaltAndIterations();
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        final String serverNonce = doFirstExchange(server);

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(("c=,r=" + serverNonce + ",p=dGVzdA==").getBytes(StandardCharsets.UTF_8)),
            "Final client message with empty channel binding should be rejected");
    }

    /**
     * Verifies that a final client message containing an incorrect nonce is rejected.
     */
    @Test
    void rejectsFinalMessage_incorrectNonce() throws Exception
    {
        // Setup test fixture
        setupSaltAndIterations();
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        doFirstExchange(server);

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse("c=biws,r=completely-wrong-nonce,p=dGVzdA==".getBytes(StandardCharsets.UTF_8)),
            "Final client message with incorrect nonce should be rejected");
    }

    /**
     * Verifies that a final client message carrying an incorrect channel binding value is rejected
     * for a non-PLUS exchange. For non-PLUS, c= must decode to exactly the GS2 header ("n,,"),
     * whose base64 encoding is "biws".
     */
    @Test
    void rejectsFinalMessage_incorrectChannelBindingValue_nonPlusMechanism() throws Exception
    {
        // Setup test fixture
        setupSaltAndIterations();
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        final String serverNonce = doFirstExchange(server);
        final String wrongBinding = Base64.getEncoder().encodeToString("p=tls-unique,,".getBytes(StandardCharsets.UTF_8));

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(("c=" + wrongBinding + ",r=" + serverNonce + ",p=dGVzdA==").getBytes(StandardCharsets.UTF_8)),
            "Final client message with incorrect channel binding value should be rejected");
    }

    /**
     * Verifies that a proof whose decoded length differs from the HMAC-SHA-1 output length (20 bytes)
     * is rejected with a clean SaslException rather than an ArrayIndexOutOfBoundsException.
     */
    @Test
    void rejectsFinalMessage_proofWithWrongLength() throws Exception
    {
        // Setup test fixture
        setupSaltAndIterationsAndKeys();
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        final String serverNonce = doFirstExchange(server);
        final String shortProof = Base64.getEncoder().encodeToString(new byte[10]); // 10 bytes, not 20

        // Execute system under test
        final SaslException ex = assertThrows(SaslException.class, () -> server.evaluateResponse(("c=biws,r=" + serverNonce + ",p=" + shortProof).getBytes(StandardCharsets.UTF_8)),
            "Final client message with proof of wrong length should be rejected");

        // Verify result
        assertTrue(ex.getMessage().contains("proof"), "Exception should mention the proof");
    }

    /**
     * Verifies that a correctly structured final message carrying a wrong (but correctly sized) proof
     * results in an authentication failure rather than a successful login.
     */
    @Test
    void rejectsFinalMessage_incorrectProof() throws Exception
    {
        // Setup test fixture
        setupSaltAndIterationsAndKeys();
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        final String serverNonce = doFirstExchange(server);
        final String wrongProof = Base64.getEncoder().encodeToString(new byte[20]); // 20 zero bytes

        // Execute system under test & Verify result
        assertThrows(SaslException.class,() -> server.evaluateResponse(("c=biws,r=" + serverNonce + ",p=" + wrongProof).getBytes(StandardCharsets.UTF_8)),
            "Final client message with incorrect proof should be rejected");
    }

    /**
     * Verifies that isComplete() returns false before any exchange has taken place.
     */
    @Test
    void isComplete_returnsFalse_initially()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test
        final boolean complete = server.isComplete();

        // Verify result
        assertFalse(complete, "isComplete() should return false before any exchange has taken place");
    }

    /**
     * Verifies that isComplete() returns false after only the first exchange round.
     */
    @Test
    void isComplete_returnsFalse_afterFirstExchangeOnly() throws Exception
    {
        // Setup test fixture
        setupSaltAndIterations();
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        server.evaluateResponse("n,,n=user,r=clientnonce".getBytes(StandardCharsets.UTF_8));

        // Execute system under test
        final boolean complete = server.isComplete();

        // Verify result
        assertFalse(complete, "isComplete() should return false after only the first exchange");
    }

    /**
     * Verifies that a non-empty response submitted after a completed exchange is rejected.
     */
    @Test
    void rejectsNonEmptyResponse_afterExchangeComplete() throws Exception
    {
        // Setup test fixture
        final ScramSha1SaslServer server = completeSuccessfulExchange();

        // Execute system under test
        assertTrue(server.isComplete(), "Server should be complete after successful exchange");

        // Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse("unexpected".getBytes(StandardCharsets.UTF_8)),
            "Non-empty response after exchange complete should be rejected");
    }

    /**
     * Verifies that an empty response submitted after a completed exchange is tolerated
     * (some SASL frameworks send an empty final acknowledgement).
     */
    @Test
    void acceptsEmptyResponse_afterExchangeComplete() throws Exception
    {
        // Setup test fixture
        final ScramSha1SaslServer server = completeSuccessfulExchange();

        // Execute system under test & Verify result
        assertDoesNotThrow(() -> server.evaluateResponse(new byte[0]),
            "Empty response after exchange complete should be tolerated");
    }

    /**
     * Verifies that getAuthorizationID() throws before the exchange completes.
     */
    @Test
    void getAuthorizationID_throwsIllegalStateException_beforeCompletion()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test & Verify result
        assertThrows(IllegalStateException.class, server::getAuthorizationID,
            "getAuthorizationID() before completion should throw IllegalStateException");
    }

    /**
     * Verifies that getAuthorizationID() returns the authenticated username after a successful exchange.
     */
    @Test
    void getAuthorizationID_returnsUsername_afterCompletion() throws Exception
    {
        // Setup test fixture
        final ScramSha1SaslServer server = completeSuccessfulExchange();

        // Execute system under test
        final String authzId = server.getAuthorizationID();

        // Verify result
        assertEquals("user", authzId, "getAuthorizationID() should return the authenticated username after completion");
    }

    /**
     * Verifies that getNegotiatedProperty() throws before the exchange completes.
     */
    @Test
    void getNegotiatedProperty_throwsIllegalStateException_beforeCompletion()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test & Verify result
        assertThrows(IllegalStateException.class, () -> server.getNegotiatedProperty(Sasl.QOP),
            "getNegotiatedProperty() before completion should throw IllegalStateException");
    }

    /**
     * Verifies that getNegotiatedProperty() reports "auth" for QOP after a successful exchange,
     * as SCRAM-SHA-1 provides authentication only (no integrity or confidentiality layer).
     */
    @Test
    void getNegotiatedProperty_returnsAuth_forQOP_afterCompletion() throws Exception
    {
        // Setup test fixture
        final ScramSha1SaslServer server = completeSuccessfulExchange();

        // Execute system under test
        final Object qop = server.getNegotiatedProperty(Sasl.QOP);

        // Verify result
        assertEquals("auth", qop, "getNegotiatedProperty(Sasl.QOP) should return 'auth' after completion");
    }

    /**
     * Verifies that getNegotiatedProperty() returns null for unknown properties after completion.
     */
    @Test
    void getNegotiatedProperty_returnsNull_forUnknownProperty_afterCompletion() throws Exception
    {
        // Setup test fixture
        final ScramSha1SaslServer server = completeSuccessfulExchange();

        // Execute system under test
        final Object unknown = server.getNegotiatedProperty("unknown.property");

        // Verify result
        assertNull(unknown, "getNegotiatedProperty() should return null for unknown properties after completion");
    }

    /**
     * Verifies that unwrap() always throws, as SCRAM-SHA-1 has no security layer.
     */
    @Test
    void unwrap_throwsIllegalStateException_always()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test & Verify result
        assertThrows(IllegalStateException.class, () -> server.unwrap(new byte[]{1, 2, 3}, 0, 3),
            "unwrap() should always throw IllegalStateException as SCRAM-SHA-1 has no security layer");
    }

    /**
     * Verifies that wrap() always throws, as SCRAM-SHA-1 has no security layer.
     */
    @Test
    void wrap_throwsIllegalStateException_always()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test & Verify result
        assertThrows(IllegalStateException.class, () -> server.wrap(new byte[]{1, 2, 3}, 0, 3),
            "wrap() should always throw IllegalStateException as SCRAM-SHA-1 has no security layer");
    }

    /**
     * Verifies that dispose() resets the server to its initial state, making isComplete() return false
     * and preventing getAuthorizationID() from returning stale data.
     */
    @Test
    void dispose_resetsStateAndClearsSensitiveFields() throws Exception
    {
        // Setup test fixture
        final ScramSha1SaslServer server = completeSuccessfulExchange();
        assertTrue(server.isComplete(), "Server should be complete after successful exchange");

        // Execute system under test
        server.dispose();

        // Verify result
        assertFalse(server.isComplete(), "Server should not be complete after dispose()");
        assertThrows(IllegalStateException.class, server::getAuthorizationID,
            "getAuthorizationID() should throw after dispose()");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void setupSaltAndIterations()
    {
        authFactory.when(() -> AuthFactory.getSalt(any())).thenReturn("QSXCR+Q6sek8bf92");
        authFactory.when(() -> AuthFactory.getIterations(any())).thenReturn(4096);
    }

    private void setupSaltAndIterationsAndKeys()
    {
        setupSaltAndIterations();
        authFactory.when(() -> AuthFactory.getStoredKey(any()))
            .thenReturn(DatatypeConverter.printBase64Binary(StringUtils.decodeHex("e9d94660c39d65c38fbad91c358f14da0eef2bd6")));
        authFactory.when(() -> AuthFactory.getServerKey(any()))
            .thenReturn(DatatypeConverter.printBase64Binary(StringUtils.decodeHex("0fe09258b3ac852ba502cc62ba903eaacdbf7d31")));
    }

    /**
     * Performs the first exchange round and returns the composite server nonce.
     */
    private String doFirstExchange(final ScramSha1SaslServer server) throws SaslException
    {
        final String firstServerResponse = new String(
            server.evaluateResponse("n,,n=user,r=clientnonce".getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8);
        final Matcher m = Pattern.compile("r=([^,]*),.+").matcher(firstServerResponse);
        assertTrue(m.matches(), "First server response did not match expected pattern");
        return m.group(1);
    }

    /**
     * Drives a complete successful SCRAM-SHA-1 exchange and returns the completed server instance.
     */
    private ScramSha1SaslServer completeSuccessfulExchange() throws Exception
    {
        final String hardCodedClientNonce = "fyko+d2lbbFgONRv9qkxdawL";
        final String hardCodedPassword    = "pencil";

        setupSaltAndIterationsAndKeys();
        authFactory.when(() -> AuthFactory.getPassword(any())).thenReturn(hardCodedPassword);

        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));
        final byte[] initialMessage = ("n,,n=user,r=" + hardCodedClientNonce).getBytes(StandardCharsets.UTF_8);
        final String firstServerResponse = new String(server.evaluateResponse(initialMessage), StandardCharsets.UTF_8);

        final Matcher m = Pattern.compile("r=([^,]*),s=([^,]*),i=(.*)$").matcher(firstServerResponse);
        assertTrue(m.matches());
        final String serverNonce = m.group(1);
        final byte[] salt        = DatatypeConverter.parseBase64Binary(m.group(2));
        final int    iterations  = Integer.parseInt(m.group(3));

        final SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        final KeySpec spec = new PBEKeySpec(hardCodedPassword.toCharArray(), salt, iterations, 160);
        final byte[] saltedPassword = factory.generateSecret(spec).getEncoded();

        final byte[] clientKey      = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, saltedPassword).hmac("Client Key");
        final byte[] storedKey      = StringUtils.decodeHex(StringUtils.hash(clientKey, "SHA-1"));
        final String clientFinalBare = "c=biws,r=" + serverNonce;
        final String authMessage    = "n=user,r=" + hardCodedClientNonce + "," + firstServerResponse + "," + clientFinalBare;
        final byte[] clientSignature = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, storedKey).hmac(authMessage);

        final byte[] clientProof = new byte[clientKey.length];
        for (int i = 0; i < clientKey.length; i++) {
            clientProof[i] = (byte) (clientKey[i] ^ clientSignature[i]);
        }

        final String clientFinalMessage = clientFinalBare + ",p=" + Base64.getEncoder().encodeToString(clientProof);
        server.evaluateResponse(clientFinalMessage.getBytes(StandardCharsets.UTF_8));
        return server;
    }
}
