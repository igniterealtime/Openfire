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

package org.jivesoftware.openfire.net;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal SCRAM-SHA-1 client (RFC 5802) for tests that drive {@link SASLAuthentication} through a whole exchange,
 * without channel binding.
 *
 * @author Matthew Vivian
 */
final class ScramSha1TestClient
{
    private static final Pattern SERVER_FIRST = Pattern.compile("r=([^,]+),s=([^,]+),i=(\\d+).*");

    private final String username;
    private final String clientNonce;

    ScramSha1TestClient(final String username, final String clientNonce)
    {
        this.username = username;
        this.clientNonce = clientNonce;
    }

    /**
     * @return the client-first message, Base64-encoded.
     */
    String clientFirst()
    {
        return Base64.getEncoder().encodeToString(("n,," + clientFirstBare()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the client-final message for a password, from the server-first message.
     *
     * @param serverFirstBase64 the server-first message, Base64-encoded.
     * @param password the password to prove.
     * @return the client-final message, Base64-encoded.
     */
    String clientFinal(final String serverFirstBase64, final String password) throws Exception
    {
        final String serverFirst = new String(Base64.getDecoder().decode(serverFirstBase64), StandardCharsets.UTF_8);
        final Matcher matcher = SERVER_FIRST.matcher(serverFirst);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a server-first message: " + serverFirst);
        }
        final byte[] salt = Base64.getDecoder().decode(matcher.group(2));
        final int iterations = Integer.parseInt(matcher.group(3));
        final byte[][] keys = keys(password, salt, iterations);

        final String finalWithoutProof = "c=biws,r=" + matcher.group(1);
        final String authMessage = clientFirstBare() + "," + serverFirst + "," + finalWithoutProof;
        final byte[] clientSignature = hmac(keys[1], authMessage.getBytes(StandardCharsets.UTF_8));
        final byte[] proof = keys[0].clone();
        for (int i = 0; i < proof.length; i++) {
            proof[i] ^= clientSignature[i];
        }
        return Base64.getEncoder().encodeToString((finalWithoutProof + ",p=" + Base64.getEncoder().encodeToString(proof)).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Derives the SCRAM-SHA-1 keys for a password.
     *
     * @return the client key, the stored key and the server key, in that order.
     */
    static byte[][] keys(final String password, final byte[] salt, final int iterations) throws Exception
    {
        final byte[] saltedPassword = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, 160)).getEncoded();
        final byte[] clientKey = hmac(saltedPassword, "Client Key".getBytes(StandardCharsets.UTF_8));
        final byte[] storedKey = MessageDigest.getInstance("SHA-1").digest(clientKey);
        final byte[] serverKey = hmac(saltedPassword, "Server Key".getBytes(StandardCharsets.UTF_8));
        return new byte[][]{clientKey, storedKey, serverKey};
    }

    private String clientFirstBare()
    {
        return "n=" + username + ",r=" + clientNonce;
    }

    private static byte[] hmac(final byte[] key, final byte[] data) throws Exception
    {
        final Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        return mac.doFinal(data);
    }
}
