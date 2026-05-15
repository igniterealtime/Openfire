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

import javax.annotation.Nonnull;
import javax.xml.bind.DatatypeConverter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Holds the parsed outcome of the server's first SCRAM exchange message.
 *
 * Instances are created via the factory method fromFirstServerResponse(String) which parses the "r=...,s=...,i=..."
 * response line and extracts the individual fields.
 */
public class FirstExchangeResult
{
    /** The combined client-and-server nonce returned by the server. */
    final String serverNonce;

    /** The salt value returned by the server, decoded from Base64. */
    final byte[] salt;

    /** The PBKDF2 iteration count returned by the server. */
    final int iterations;

    /**
     * Creates a new FirstExchangeResult with the given field values.
     *
     * @param serverNonce the combined nonce from the server's first message
     * @param salt        the decoded salt bytes from the server's first message
     * @param iterations  the PBKDF2 iteration count from the server's first message
     */
    private FirstExchangeResult(String serverNonce, byte[] salt, int iterations)
    {
        this.serverNonce = serverNonce;
        this.salt = salt;
        this.iterations = iterations;
    }

    /**
     * Parses the first server response and returns a composite result
     * <p>
     * This method will throw AssertionFailedError when the first server response does not match the expected pattern.
     *
     * @param firstServerResponse the response from the server after the first exchange
     * @return the parsed result
     */
    @Nonnull
    public static FirstExchangeResult fromFirstServerResponse(final String firstServerResponse)
    {
        final Matcher m = Pattern.compile("r=([^,]*),s=([^,]*),i=(.*)$").matcher(firstServerResponse);
        assertTrue(m.matches(), "First server response did not match expected pattern");
        final String serverNonce = m.group(1);
        final byte[] salt;
        try {
            salt = DatatypeConverter.parseBase64Binary(m.group(2));
        } catch (IllegalArgumentException e) {
            fail("First server message should contain a valid 'salt' value (but did not).", e);
            return null; // appeasing the compiler: this line will never be executed.
        }
        final int iterations;
        try {
            iterations = Integer.parseInt(m.group(3));
        } catch (NumberFormatException e) {
            fail("First server message should contain a valid 'iterations' value (but did not).", e);
            return null; // appeasing the compiler: this line will never be executed.
        }
        return new FirstExchangeResult(serverNonce, salt, iterations);
    }
}
