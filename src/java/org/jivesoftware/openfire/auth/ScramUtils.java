/*
 * Copyright 2015 Surevine Ltd
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

    public static byte[] createSaltedPassword(byte[] salt, String password, int iters) throws SaslException {
        Mac mac = createSha1Hmac(password.getBytes(StandardCharsets.UTF_8));
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
    
    public static byte[] computeHmac(final byte[] key, final String string)
            throws SaslException {
        Mac mac = createSha1Hmac(key);
        mac.update(string.getBytes(StandardCharsets.UTF_8));
        return mac.doFinal();
    }

    public static Mac createSha1Hmac(final byte[] keyBytes)
            throws SaslException {
        try {
            SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(key);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SaslException(e.getMessage(), e);
        }
    }
}
