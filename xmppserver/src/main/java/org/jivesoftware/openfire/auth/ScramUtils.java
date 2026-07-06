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

package org.jivesoftware.openfire.auth;

import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.sasl.SaslException;

/**
 * A utility class that provides methods that are useful for dealing with Salted Challenge Response Authentication
 * Mechanism (SCRAM).
 *
 * The HMAC algorithm to be used is provided as an argument (in the form of a JCA standard name, such as
 * {@code HmacSHA1} or {@code HmacSHA256}), allowing these utilities to serve any SCRAM mechanism.
 *
 * @author Richard Midwinter
 */
public class ScramUtils {

    public static final int DEFAULT_ITERATION_COUNT = 4096;

    private ScramUtils() {}

    /**
     * Computes a salted password ({@code Hi(password, salt, iterations)} as defined in RFC 5802), using the provided
     * HMAC algorithm.
     *
     * @param salt the salt.
     * @param password the password.
     * @param iters the iteration count.
     * @param hmacAlgorithm the JCA name of the HMAC algorithm to use (for example: {@code HmacSHA1}).
     * @return the salted password.
     * @throws SaslException if the HMAC could not be initialized.
     */
    public static byte[] createSaltedPassword(byte[] salt, String password, int iters, String hmacAlgorithm) throws SaslException
    {
        final Mac mac = createHmac(password.getBytes(StandardCharsets.UTF_8), hmacAlgorithm);
        mac.update(salt);
        mac.update(new byte[]{0, 0, 0, 1});
        byte[] result = mac.doFinal();

        byte[] previous = null;
        for (int i = 1; i < iters; i++) {
            mac.update(previous != null ? previous : result);
            previous = mac.doFinal();
            for (int x = 0; x < result.length; x++) {
                result[x] ^= previous[x];
            }
        }

        return result;
    }

    /**
     * Computes an HMAC over the UTF-8 bytes of the provided string, using the provided HMAC algorithm.
     *
     * @param key the key.
     * @param string the value to compute the HMAC over.
     * @param hmacAlgorithm the JCA name of the HMAC algorithm to use (for example: {@code HmacSHA1}).
     * @return the computed HMAC.
     * @throws SaslException if the HMAC could not be initialized.
     */
    public static byte[] computeHmac(final byte[] key, final String string, final String hmacAlgorithm) throws SaslException
    {
        final Mac mac = createHmac(key, hmacAlgorithm);
        mac.update(string.getBytes(StandardCharsets.UTF_8));
        return mac.doFinal();
    }

    /**
     * Creates an initialized {@link Mac} instance for the provided HMAC algorithm.
     *
     * @param keyBytes the key.
     * @param hmacAlgorithm the JCA name of the HMAC algorithm to use (for example: {@code HmacSHA1}).
     * @return an initialized Mac.
     * @throws SaslException if the HMAC could not be initialized.
     */
    public static Mac createHmac(final byte[] keyBytes, final String hmacAlgorithm) throws SaslException
    {
        try {
            final SecretKeySpec key = new SecretKeySpec(keyBytes, hmacAlgorithm);
            final Mac mac = Mac.getInstance(hmacAlgorithm);
            mac.init(key);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SaslException("Unable to create an initialized Mac instance for algorithm '" + hmacAlgorithm + "'.", e);
        }
    }

    /**
     * Computes a SHA-1 salted password ({@code Hi(password, salt, iterations)} as defined in RFC 5802).
     *
     * @param salt the salt.
     * @param password the password.
     * @param iters the iteration count.
     * @return the salted password.
     * @throws SaslException if the HMAC could not be initialized.
     * @deprecated Use {@link #createSaltedPassword(byte[], String, int, String)}, providing an explicit HMAC algorithm.
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    public static byte[] createSaltedPassword(byte[] salt, String password, int iters) throws SaslException
    {
        return createSaltedPassword(salt, password, iters, ScramSha1SaslServer.HMAC_ALGORITHM_NAME);
    }

    /**
     * Computes an HMAC-SHA-1 over the UTF-8 bytes of the provided string.
     *
     * @param key the key.
     * @param string the value to compute the HMAC over.
     * @return the computed HMAC.
     * @throws SaslException if the HMAC could not be initialized.
     * @deprecated Use {@link #computeHmac(byte[], String, String)}, providing an explicit HMAC algorithm.
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    public static byte[] computeHmac(final byte[] key, final String string) throws SaslException
    {
        return computeHmac(key, string, ScramSha1SaslServer.HMAC_ALGORITHM_NAME);
    }

    /**
     * Creates an initialized {@link Mac} instance for HMAC-SHA-1.
     *
     * @param keyBytes the key.
     * @return an initialized Mac.
     * @throws SaslException if the HMAC could not be initialized.
     * @deprecated Use {@link #createHmac(byte[], String)}, providing an explicit HMAC algorithm.
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    public static Mac createSha1Hmac(final byte[] keyBytes) throws SaslException
    {
        return createHmac(keyBytes, ScramSha1SaslServer.HMAC_ALGORITHM_NAME);
    }
}
