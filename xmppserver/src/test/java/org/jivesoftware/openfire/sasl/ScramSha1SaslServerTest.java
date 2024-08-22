/*
 * Copyright (C) 2023-2024 Ignite Realtime Foundation. All rights reserved.
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.security.sasl.SaslException;
import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.any;

/**
 * Unit tests that verify the implementation of {@link ScramSha1SaslServer}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ScramSha1SaslServerTest
{
    private MockedStatic<AuthFactory> authFactory;

    @Before
    public void setupStaticMock() {
        authFactory = Mockito.mockStatic(AuthFactory.class);
    }

    @After
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
        final ScramSha1SaslServer server = new ScramSha1SaslServer();
        final byte[] initialMessage = ("n,,n=user,r=" + hardCodedClientNonce).getBytes(StandardCharsets.UTF_8);

        // Execute system under test: getting the first server message.
        final String firstServerResponse = new String(server.evaluateResponse(initialMessage), StandardCharsets.UTF_8); // r=%s,s=%s,i=%d

        // Verify result (first server message should match a pattern, and contain a number of properties)
        final Matcher firstServerResponseMatcher = Pattern.compile("r=([^,]*),s=([^,]*),i=(.*)$").matcher(firstServerResponse);
        if (!firstServerResponseMatcher.matches()) {
            fail("First server message does not match expected pattern.");
        }
        final String serverNonce = firstServerResponseMatcher.group(1);
        assertTrue("First server message should contain a non-empty server nonce (but did not)", serverNonce != null && !serverNonce.isBlank());
        assertTrue("First server message should contain a server nonce that starts with the client nonce, but did not.", serverNonce.startsWith(hardCodedClientNonce));


        byte[] salt = null;
        try {
            salt = DatatypeConverter.parseBase64Binary(firstServerResponseMatcher.group(2));
            assertEquals("First server message should include the 'salt' value configured for this unit test (but did not)", hardCodedSalt, firstServerResponseMatcher.group(2));
        } catch (IllegalArgumentException e) {
            fail("First server message should contain a valid 'salt' value (but did not).");
        }

        int iterations = -1;
        try {
            iterations = Integer.parseInt(firstServerResponseMatcher.group(3));
            assertEquals("First server message should include the 'iterations' value configured for this unit test (but did not)", hardCodedIterations, iterations);
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
}
