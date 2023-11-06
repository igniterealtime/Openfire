/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.util.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Verifies the implementation of {@link ScramUtils}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class ScramUtilsTest
{
    /**
     * Verifies the implementation of {@link ScramUtils#createSaltedPassword(byte[], String, int)} by using test vectors
     * that are provided by the XSF.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testCreateSaltedPassword() throws Exception
    {
        // Setup test fixture.
        final byte[] salt = StringUtils.decodeHex("4125c247e43ab1e93c6dff76");
        final String password = "pencil";
        final int iterations = 4096;

        // Execute system under test.
        final byte[] result = ScramUtils.createSaltedPassword(salt, password, iterations);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("1d96ee3a529b5a5f9e47c01f229a2cb8a6e15f7d"), result);
    }

    /**
     * Verifies the implementation of {@link ScramUtils#computeHmac(byte[], String)} by using test vectors that are
     * provided by the XSF.
     *
     * This test uses the test vectors identified as the 'client key'.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testComputeHmac() throws Exception
    {
        // Setup test fixture.
        final byte[] key = StringUtils.decodeHex("1d96ee3a529b5a5f9e47c01f229a2cb8a6e15f7d"); // 'salted password' from the test vectors.
        final String value = "Client Key";

        // Execute system under test.
        final byte[] result = ScramUtils.computeHmac(key, value);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("e234c47bf6c36696dd6d852b99aaa2ba26555728"), result); // test against 'client key' from the test vectors.
    }

    /**
     * Verifies the implementation of {@link ScramUtils#computeHmac(byte[], String)} by using test vectors that are
     * provided by the XSF.
     *
     * This test uses the test vectors identified as the 'server key'.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testComputeHmac2() throws Exception
    {
        // Setup test fixture.
        final byte[] key = StringUtils.decodeHex("1d96ee3a529b5a5f9e47c01f229a2cb8a6e15f7d"); // 'salted password' from the test vectors.
        final String value = "Server Key";

        // Execute system under test.
        final byte[] result = ScramUtils.computeHmac(key, value);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("0fe09258b3ac852ba502cc62ba903eaacdbf7d31"), result); // test against 'server key' from the test vectors.
    }
}
