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
package org.jivesoftware.util;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jivesoftware.openfire.keystore.KeystoreTestUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests that verify the functionality of {@link S2STestService}.
 */
public class S2STestServiceTest
{
    /** Two line breaks in a row, which is an empty line. */
    private static final Pattern EMPTY_LINE = Pattern.compile("\\R\\R");

    @Test
    public void testCertificateOutputIsTheCertificateInPem() throws Exception
    {
        // Setup test fixture.
        final X509Certificate certificate = KeystoreTestUtils.generateValidCertificateChain("remote.example.org").getCertificateChain()[0];

        // Execute system under test.
        final String output = S2STestService.formatCertificates(new Certificate[] { certificate });

        // Verify results.
        final int begin = output.indexOf("-----BEGIN CERTIFICATE-----");
        final int end = output.indexOf("-----END CERTIFICATE-----") + "-----END CERTIFICATE-----".length();
        assertTrue(begin >= 0 && end > begin, "the output must contain a PEM block");
        final Certificate parsed = CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(output.substring(begin, end).getBytes(StandardCharsets.US_ASCII)));
        assertEquals(certificate, parsed, "the PEM block must hold the certificate itself, not only its public key");
        assertTrue(output.contains("remote.example.org"), "the output must still show the subject for reading at a glance");
    }

    /**
     * Verifies that the PEM block has no empty line, whatever the length of the certificate. OpenSSL refuses a block
     * with an empty line before the END marker, which a naive wrap at 64 characters produces when the base64 text is
     * an exact multiple of 64 characters long. Java's CertificateFactory accepts such a block, so parsing it back, as
     * the other test does, cannot catch this.
     *
     * The certificates are signed with Ed25519, whose signature always has the same length, so each extra character
     * in the subject name adds one byte to the encoding (a few more where a length field grows), and 96 consecutive
     * name lengths cover every length of the encoding modulo 48 (48 bytes being 64 base64 characters).
     */
    @Test
    public void testCertificateOutputHasNoEmptyLineInThePemBlockForAnyLength() throws Exception
    {
        // Setup test fixture.
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final Set<Integer> remaindersCovered = new HashSet<>();
        for (int nameLength = 1; nameLength <= 96; nameLength++) {
            final X509Certificate certificate = selfSignedCertificate(keyPair, "a".repeat(nameLength));
            remaindersCovered.add(certificate.getEncoded().length % 48);

            // Execute system under test.
            final String output = S2STestService.formatCertificates(new Certificate[] { certificate });

            // Verify results.
            final int begin = output.indexOf("-----BEGIN CERTIFICATE-----");
            final int end = output.indexOf("-----END CERTIFICATE-----");
            assertTrue(begin >= 0 && end > begin, "the output must contain a PEM block");
            final String block = output.substring(begin, end);
            assertFalse(EMPTY_LINE.matcher(block).find(), "no line in the PEM block may be empty (encoded length " + certificate.getEncoded().length + ")");
        }
        assertEquals(48, remaindersCovered.size(), "the certificates must cover every encoded length modulo 48");
    }

    private static X509Certificate selfSignedCertificate(final KeyPair keyPair, final String commonName) throws Exception
    {
        final X500Name name = new X500Name("CN=" + commonName);
        final Date notBefore = new Date(0);
        final Date notAfter = new Date(86_400_000L);
        return new JcaX509CertificateConverter().getCertificate(
            new JcaX509v3CertificateBuilder(name, BigInteger.ONE, notBefore, notAfter, name, keyPair.getPublic())
                .build(new JcaContentSignerBuilder("Ed25519").build(keyPair.getPrivate())));
    }
}
