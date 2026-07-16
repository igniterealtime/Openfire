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
     * @deprecated This tests an implementation that shall be removed. When it is, this test can be deleted.
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
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
     * @deprecated This tests an implementation that shall be removed. When it is, this test can be deleted.
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
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
     * @deprecated This tests an implementation that shall be removed. When it is, this test can be deleted.
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
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

    /**
     * Verifies the implementation of {@link ScramUtils#createSaltedPassword(byte[], String, int, String)} by using
     * SCRAM-SHA-1(-PLUS) test vectors that are provided by the XSF.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha1CreateSaltedPassword() throws Exception
    {
        // Setup test fixture.
        final byte[] salt = StringUtils.decodeHex("4125c247e43ab1e93c6dff76");
        final String password = "pencil";
        final int iterations = 4096;
        final String hmacAlgorithm = "HmacSHA1";

        // Execute system under test.
        final byte[] result = ScramUtils.createSaltedPassword(salt, password, iterations, hmacAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("1d96ee3a529b5a5f9e47c01f229a2cb8a6e15f7d"), result);
    }

    /**
     * Verifies the implementation of {@link ScramUtils#computeHmac(byte[], String, String)} by using SCRAM-SHA-1(-PLUS)
     * test vectors that are provided by the XSF.
     *
     * This test uses the test vectors identified as the 'client key'.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha1ComputeHmac() throws Exception
    {
        // Setup test fixture.
        final byte[] key = StringUtils.decodeHex("1d96ee3a529b5a5f9e47c01f229a2cb8a6e15f7d"); // 'salted password' from the test vectors.
        final String value = "Client Key";
        final String hmacAlgorithm = "HmacSHA1";

        // Execute system under test.
        final byte[] result = ScramUtils.computeHmac(key, value, hmacAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("e234c47bf6c36696dd6d852b99aaa2ba26555728"), result); // test against 'client key' from the test vectors.
    }

    /**
     * Verifies the implementation of {@link ScramUtils#computeHmac(byte[], String, String)} by using
     * SCRAM-SHA-1(-PLUS) test vectors that are provided by the XSF.
     *
     * This test uses the test vectors identified as the 'server key'.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha1ComputeHmac2() throws Exception
    {
        // Setup test fixture.
        final byte[] key = StringUtils.decodeHex("1d96ee3a529b5a5f9e47c01f229a2cb8a6e15f7d"); // 'salted password' from the test vectors.
        final String value = "Server Key";
        final String hmacAlgorithm = "HmacSHA1";

        // Execute system under test.
        final byte[] result = ScramUtils.computeHmac(key, value, hmacAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("0fe09258b3ac852ba502cc62ba903eaacdbf7d31"), result); // test against 'server key' from the test vectors.
    }

    /**
     * Verifies the implementation of {@link ScramUtils#deriveScramKeys(byte[], String, int, String, String)}
     * by using SCRAM-SHA-1(-PLUS) test vectors that are provided by the XSF.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha1DeriveScramKeys() throws Exception
    {
        // Setup test fixture.
        final byte[] salt = StringUtils.decodeHex("4125c247e43ab1e93c6dff76");
        final String password = "pencil";
        final int iterations = 4096;
        final String hmacAlgorithm = "HmacSHA1";
        final String digestAlgorithm = "SHA-1";

        // Execute system under test.
        final ScramUtils.ScramKeys result = ScramUtils.deriveScramKeys(salt, password, iterations, hmacAlgorithm, digestAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("e9d94660c39d65c38fbad91c358f14da0eef2bd6"), result.storedKey);
        assertArrayEquals(StringUtils.decodeHex("0fe09258b3ac852ba502cc62ba903eaacdbf7d31"), result.serverKey);
    }

    /**
     * Verifies the implementation of {@link ScramUtils#createSaltedPassword(byte[], String, int, String)} by using
     * SCRAM-SHA-256(-PLUS) test vectors that are provided by the XSF.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha256CreateSaltedPassword() throws Exception
    {
        // Setup test fixture.
        final byte[] salt = StringUtils.decodeHex("5b6d99689d12358eeca04b141236fa81");
        final String password = "pencil";
        final int iterations = 4096;
        final String hmacAlgorithm = "HmacSHA256";

        // Execute system under test.
        final byte[] result = ScramUtils.createSaltedPassword(salt, password, iterations, hmacAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("c4a49510323ab4f952cac1fa99441939e78ea74d6be81ddf7096e87513dc615d"), result);
    }

    /**
     * Verifies the implementation of {@link ScramUtils#computeHmac(byte[], String, String)} by using
     * SCRAM-SHA-256(-PLUS) test vectors that are provided by the XSF.
     *
     * This test uses the test vectors identified as the 'client key'.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha256ComputeHmac() throws Exception
    {
        // Setup test fixture.
        final byte[] key = StringUtils.decodeHex("c4a49510323ab4f952cac1fa99441939e78ea74d6be81ddf7096e87513dc615d"); // 'salted password' from the test vectors.
        final String value = "Client Key";
        final String hmacAlgorithm = "HmacSHA256";

        // Execute system under test.
        final byte[] result = ScramUtils.computeHmac(key, value, hmacAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("a60fc923d67e8644a92d16b96eda5ef4656b0c725c484374be25535576996e8b"), result); // test against 'client key' from the test vectors.
    }

    /**
     * Verifies the implementation of {@link ScramUtils#computeHmac(byte[], String, String)} by using
     * SCRAM-SHA-256(-PLUS) test vectors that are provided by the XSF.
     *
     * This test uses the test vectors identified as the 'server key'.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha256ComputeHmac2() throws Exception
    {
        // Setup test fixture.
        final byte[] key = StringUtils.decodeHex("c4a49510323ab4f952cac1fa99441939e78ea74d6be81ddf7096e87513dc615d"); // 'salted password' from the test vectors.
        final String value = "Server Key";
        final String hmacAlgorithm = "HmacSHA256";

        // Execute system under test.
        final byte[] result = ScramUtils.computeHmac(key, value, hmacAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("c1f3cbc1c13a9d35a14c0990eed97629ea225863e566a4314ab99f3f00e5d9d5"), result); // test against 'server key' from the test vectors.
    }

    /**
     * Verifies the implementation of {@link ScramUtils#deriveScramKeys(byte[], String, int, String, String)}
     * by using SCRAM-SHA-256(-PLUS) test vectors that are provided by the XSF.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha256DeriveScramKeys() throws Exception
    {
        // Setup test fixture.
        final byte[] salt = StringUtils.decodeHex("5b6d99689d12358eeca04b141236fa81");
        final String password = "pencil";
        final int iterations = 4096;
        final String hmacAlgorithm = "HmacSHA256";
        final String digestAlgorithm = "SHA-256";

        // Execute system under test.
        final ScramUtils.ScramKeys result = ScramUtils.deriveScramKeys(salt, password, iterations, hmacAlgorithm, digestAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("586e5df283e6dceb5c3e791d8b8528ec191e664045ce971792e2e6b5bb13e2a6"), result.storedKey);
        assertArrayEquals(StringUtils.decodeHex("c1f3cbc1c13a9d35a14c0990eed97629ea225863e566a4314ab99f3f00e5d9d5"), result.serverKey);
    }

    /**
     * Verifies the implementation of {@link ScramUtils#createSaltedPassword(byte[], String, int, String)} by using
     * SCRAM-SHA-512(-PLUS) test vectors that are provided by the XSF.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha512CreateSaltedPassword() throws Exception
    {
        // Setup test fixture.
        final byte[] salt = StringUtils.decodeHex("5b6d99689d12358eeca04b141236fa81");
        final String password = "pencil";
        final int iterations = 4096;
        final String hmacAlgorithm = "HmacSHA512";

        // Execute system under test.
        final byte[] result = ScramUtils.createSaltedPassword(salt, password, iterations, hmacAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("f16efe1be67f1d09502ebd5ed9262fddffba5a377ab4f0b687e5ed5ba0f50686b8a4ae166476da8ab3b951d2fa9238b63998f45461bc33a464814949cec9631d"), result);
    }

    /**
     * Verifies the implementation of {@link ScramUtils#computeHmac(byte[], String, String)} by using
     * SCRAM-SHA-512(-PLUS) test vectors.
     *
     * This test uses the test vectors identified as the 'client key'.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha512ComputeHmac() throws Exception
    {
        // Setup test fixture.
        final byte[] key = StringUtils.decodeHex("f16efe1be67f1d09502ebd5ed9262fddffba5a377ab4f0b687e5ed5ba0f50686b8a4ae166476da8ab3b951d2fa9238b63998f45461bc33a464814949cec9631d"); // 'salted password' from the test vectors.
        final String value = "Client Key";
        final String hmacAlgorithm = "HmacSHA512";

        // Execute system under test.
        final byte[] result = ScramUtils.computeHmac(key, value, hmacAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("f81d81a6c0467dbd957885e9f3a0387a99e989171077fc3b8d134b710907c424917b624f885a3667838762858a893a4fce39043aa57fa07388a5aaa6fdbac920"), result); // test against 'client key' from the test vectors.
    }

    /**
     * Verifies the implementation of {@link ScramUtils#computeHmac(byte[], String, String)} by using
     * SCRAM-SHA-512(-PLUS) test vectors.
     *
     * This test uses the test vectors identified as the 'server key'.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha512ComputeHmac2() throws Exception
    {
        // Setup test fixture.
        final byte[] key = StringUtils.decodeHex("f16efe1be67f1d09502ebd5ed9262fddffba5a377ab4f0b687e5ed5ba0f50686b8a4ae166476da8ab3b951d2fa9238b63998f45461bc33a464814949cec9631d"); // 'salted password' from the test vectors.
        final String value = "Server Key";
        final String hmacAlgorithm = "HmacSHA512";

        // Execute system under test.
        final byte[] result = ScramUtils.computeHmac(key, value, hmacAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("8d91db6230b5687874fe129bc7206e1858c3ae08e02934f57ac03b6b05a229c459d28ff46f5c9611e6c179256490215ec1ff759cb0df285db89af0f99e613aac"), result); // test against 'server key' from the test vectors.
    }

    /**
     * Verifies the implementation of {@link ScramUtils#deriveScramKeys(byte[], String, int, String, String)}
     * by using SCRAM-SHA-512(-PLUS) test vectors that are provided by the XSF.
     *
     * @see <a href="https://wiki.xmpp.org/web/SASL_Authentication_and_SCRAM">XSF SCRAM test vectors.</a>
     */
    @Test
    public void testScramSha512DeriveScramKeys() throws Exception
    {
        // Setup test fixture.
        final byte[] salt = StringUtils.decodeHex("5b6d99689d12358eeca04b141236fa81");
        final String password = "pencil";
        final int iterations = 4096;
        final String hmacAlgorithm = "HmacSHA512";
        final String digestAlgorithm = "SHA-512";

        // Execute system under test.
        final ScramUtils.ScramKeys result = ScramUtils.deriveScramKeys(salt, password, iterations, hmacAlgorithm, digestAlgorithm);

        // Verify results.
        assertArrayEquals(StringUtils.decodeHex("e8002e6f7d3ae446119b216933644dc2a2be7869eb918b8459b5e7d7d2ec12606aceef106825cd735170a675fd3611f684affad1dce3f43a0ee43bd590e1dbbe"), result.storedKey);
        assertArrayEquals(StringUtils.decodeHex("8d91db6230b5687874fe129bc7206e1858c3ae08e02934f57ac03b6b05a229c459d28ff46f5c9611e6c179256490215ec1ff759cb0df285db89af0f99e613aac"), result.serverKey);
    }
}
