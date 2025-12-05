/*
 * Copyright (C) 2015 Surevine Ltd, 2016-2018 Ignite Realtime Foundation. All rights reserved
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

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.sasl.SaslException;

/**
 * A utility class that provides methods that are useful for dealing with
 * Salted Challenge Response Authentication Mechanism (SCRAM).
 * 
 * @author Richard Midwinter
 */
public class ScramUtils {
    
    public static final int DEFAULT_ITERATION_COUNT = 4096;

    private ScramUtils() {}

    public static byte[] createSaltedPassword(byte[] salt, String password, int iters, String algorithm) throws SaslException {
        Mac mac = createHmac(password.getBytes(StandardCharsets.UTF_8), algorithm);
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
    
    public static byte[] computeHmac(final byte[] key, final String string, String algorithm)
            throws SaslException {
        Mac mac = createHmac(key, algorithm);
        mac.update(string.getBytes(StandardCharsets.UTF_8));
        return mac.doFinal();
    }

    public static Mac createHmac(final byte[] keyBytes, String algorithm)
            throws SaslException {
        try {
            String hmacAlgorithm = getHmacAlgorithm(algorithm);
            SecretKeySpec key = new SecretKeySpec(keyBytes, hmacAlgorithm);
            Mac mac = Mac.getInstance(hmacAlgorithm);
            mac.init(key);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SaslException(e.getMessage(), e);
        }
    }
    
    private static String getHmacAlgorithm(String hashAlgorithm) {
        return "Hmac" + hashAlgorithm.toUpperCase().replace("-", "");
    }

    // Keep backward compatibility methods for existing SHA-1 usage
    public static byte[] createSaltedPassword(byte[] salt, String password, int iters) throws SaslException {
        return createSaltedPassword(salt, password, iters, "SHA-1");
    }
    
    public static byte[] computeHmac(final byte[] key, final String string) throws SaslException {
        return computeHmac(key, string, "SHA-1");
    }

    public static Mac createSha1Hmac(final byte[] keyBytes) throws SaslException {
        return createHmac(keyBytes, "SHA-1");
    }
}
