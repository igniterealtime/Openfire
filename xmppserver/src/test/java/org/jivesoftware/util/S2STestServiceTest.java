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

import org.jivesoftware.openfire.keystore.KeystoreTestUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests that verify the functionality of {@link S2STestService}.
 */
public class S2STestServiceTest
{
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
}
