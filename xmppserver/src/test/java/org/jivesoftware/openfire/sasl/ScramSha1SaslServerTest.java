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
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.util.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.security.sasl.SaslException;
import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;

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
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>());
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
}
