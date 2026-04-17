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
package org.jivesoftware.util.channelbinding;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.security.spec.PSSParameterSpec;
import java.security.AlgorithmParameters;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TLS Server End Point channel binding extraction.
 *
 * These tests verify the correct extraction, normalization, and resolution of channel binding data
 * for the server end point type as defined in RFC 5929.
 */
public class TlsServerEndPointChannelBindingProviderTest
{
    // The provider under test
    private final TlsServerEndPointChannelBindingProvider provider = new TlsServerEndPointChannelBindingProvider();

    // ----------- Server-side (local certificates) tests -----------

    /**
     * Should return empty when no local certificates are present (server-side).
     */
    @Test
    void testServerSide_noLocalCertificates_returnsEmpty()
    {
        // Setup test fixture
        final SSLSession session = mock(SSLSession.class);
        when(session.getLocalCertificates()).thenReturn(null);
        final SSLEngine engine = mock(SSLEngine.class);
        when(engine.getSession()).thenReturn(session);
        when(engine.getUseClientMode()).thenReturn(false);

        // Execute system under test
        final Optional<byte[]> result = provider.getChannelBinding(engine);

        // Verify results
        assertFalse(result.isPresent(), "Should be empty when no local certificates are present (server-side)");
    }

    /**
     * Should return the expected digest when a valid X509 local certificate is present (server-side).
     */
    @Test
    void testServerSide_withLocalX509Certificate_returnsDigest() throws Exception
    {
        // Setup test fixture
        final SSLSession session = mock(SSLSession.class);
        final X509Certificate cert = mock(X509Certificate.class);
        when(cert.getSigAlgName()).thenReturn("SHA256withRSA");
        when(cert.getEncoded()).thenReturn(new byte[] {10, 20, 30});
        when(session.getLocalCertificates()).thenReturn(new Certificate[] { cert });
        final SSLEngine engine = mock(SSLEngine.class);
        when(engine.getSession()).thenReturn(session);
        when(engine.getUseClientMode()).thenReturn(false);
        final byte[] expected = MessageDigest.getInstance("SHA-256").digest(new byte[] {10, 20, 30});

        // Execute system under test
        final Optional<byte[]> result = provider.getChannelBinding(engine);

        // Verify results
        assertTrue(result.isPresent(), "Should be present when X509 certificate is present (server-side)");
        assertArrayEquals(expected, result.get(), "Digest should match expected SHA-256 digest (server-side)");
    }

    /**
     * Should return empty when the local certificate is not X509 (server-side).
     */
    @Test
    void testServerSide_localCertificateNotX509_returnsEmpty()
    {
        // Setup test fixture
        final SSLSession session = mock(SSLSession.class);
        final Certificate cert = mock(Certificate.class); // Not X509
        when(session.getLocalCertificates()).thenReturn(new Certificate[] { cert });
        final SSLEngine engine = mock(SSLEngine.class);
        when(engine.getSession()).thenReturn(session);
        when(engine.getUseClientMode()).thenReturn(false);

        // Execute system under test
        final Optional<byte[]> result = provider.getChannelBinding(engine);

        // Verify results
        assertFalse(result.isPresent(), "Should be empty when local certificate is not X509 (server-side)");
    }

    // ----------- Client-side (peer certificates) tests -----------

    /**
     * Should return empty when no peer certificates are present (client-side).
     */
    @Test
    void testClientSide_noPeerCertificates_returnsEmpty() throws SSLPeerUnverifiedException
    {
        // Setup test fixture
        final SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenReturn(null);
        final SSLEngine engine = mock(SSLEngine.class);
        when(engine.getSession()).thenReturn(session);
        when(engine.getUseClientMode()).thenReturn(true);

        // Execute system under test
        final Optional<byte[]> result = provider.getChannelBinding(engine);

        // Verify results
        assertFalse(result.isPresent(), "Should be empty when no peer certificates are present (client-side)");
    }

    /**
     * Should return the expected digest when a valid X509 peer certificate is present (client-side).
     */
    @Test
    void testClientSide_withPeerX509Certificate_returnsDigest() throws Exception
    {
        // Setup test fixture
        final SSLSession session = mock(SSLSession.class);
        final X509Certificate cert = mock(X509Certificate.class);
        when(cert.getSigAlgName()).thenReturn("SHA256withRSA");
        when(cert.getEncoded()).thenReturn(new byte[] {40, 50, 60});
        when(session.getPeerCertificates()).thenReturn(new Certificate[] { cert });
        final SSLEngine engine = mock(SSLEngine.class);
        when(engine.getSession()).thenReturn(session);
        when(engine.getUseClientMode()).thenReturn(true);
        final byte[] expected = MessageDigest.getInstance("SHA-256").digest(new byte[] {40, 50, 60});

        // Execute system under test
        final Optional<byte[]> result = provider.getChannelBinding(engine);

        // Verify results
        assertTrue(result.isPresent(), "Should be present when X509 peer certificate is present (client-side)");
        assertArrayEquals(expected, result.get(), "Digest should match expected SHA-256 digest (client-side)");
    }

    /**
     * Should return empty when the peer certificate is not X509 (client-side).
     */
    @Test
    void testClientSide_peerCertificateNotX509_returnsEmpty() throws SSLPeerUnverifiedException
    {
        // Setup test fixture
        final SSLSession session = mock(SSLSession.class);
        final Certificate cert = mock(Certificate.class); // Not X509
        when(session.getPeerCertificates()).thenReturn(new Certificate[] { cert });
        final SSLEngine engine = mock(SSLEngine.class);
        when(engine.getSession()).thenReturn(session);
        when(engine.getUseClientMode()).thenReturn(true);

        // Execute system under test
        final Optional<byte[]> result = provider.getChannelBinding(engine);

        // Verify results
        assertFalse(result.isPresent(), "Should be empty when peer certificate is not X509 (client-side)");
    }

    /**
     * Should return null if extractPssHashAlgorithm returns null.
     */
    @Test
    void testResolveServerEndPointHashAlgorithmPSS_null()
    {
        // Setup test fixture
        final X509Certificate cert = mock(X509Certificate.class);

        // Execute system under test
        final String result = provider.resolveServerEndPointHashAlgorithmPSS(cert);

        // Verify results
        assertNull(result, "Should return null when extractPssHashAlgorithm returns null (e.g., null sigAlgParams)");
    }

    /**
     * Verifies that extractPssHashAlgorithm returns null if sigAlgParams is null or if an exception is thrown.
     */
    @Test
    void testExtractPssHashAlgorithm_nullOrException()
    {
        // Setup test fixture
        final X509Certificate certNull = mock(X509Certificate.class);
        when(certNull.getSigAlgParams()).thenReturn(null);
        final X509Certificate certException = mock(X509Certificate.class);
        when(certException.getSigAlgParams()).thenThrow(new RuntimeException("fail"));

        // Execute system under test & Verify results
        assertNull(provider.extractPssHashAlgorithm(certNull), "Should return null when sigAlgParams is null");
        assertNull(provider.extractPssHashAlgorithm(certException), "Should return null when sigAlgParams throws an exception");
    }

    /**
     * Verifies that extractPssHashAlgorithm returns the correct hash algorithm when valid PSS parameters are present.
     */
    @Test
    void testExtractPssHashAlgorithm_happyFlow() throws Exception
    {
        // Setup test fixture
        final X509Certificate cert = mock(X509Certificate.class);
        final PSSParameterSpec pssSpec = new PSSParameterSpec("SHA-512", "MGF1", PSSParameterSpec.DEFAULT.getMGFParameters(), 32, 1);
        final AlgorithmParameters ap = AlgorithmParameters.getInstance("RSASSA-PSS");
        ap.init(pssSpec);
        final byte[] params = ap.getEncoded();
        when(cert.getSigAlgParams()).thenReturn(params);

        // Execute system under test
        final String result = provider.extractPssHashAlgorithm(cert);

        // Verify results
        assertEquals("SHA-512", result, "Should return the correct digest algorithm from PSS parameters");
    }

    /**
     * Verifies normalization: "SHA256" becomes "SHA-256".
     */
    @Test
    void testNormalizeServerEndPointHashAlgorithmName_SHA256()
    {
        // Execute & Verify results
        assertEquals("SHA-256", provider.normalizeServerEndPointHashAlgorithmName("SHA256"), "Should normalize 'SHA256' to 'SHA-256'");
    }

    /**
     * Verifies normalization: "SHA-256" becomes "SHA-256".
     */
    @Test
    void testNormalizeServerEndPointHashAlgorithmName_SHA_256()
    {
        // Execute & Verify results
        assertEquals("SHA-256", provider.normalizeServerEndPointHashAlgorithmName("SHA-256"), "Should normalize 'SHA-256' to 'SHA-256'");
    }

    /**
     * Verifies normalization: "SHA512" becomes "SHA-512".
     */
    @Test
    void testNormalizeServerEndPointHashAlgorithmName_SHA512()
    {
        // Execute & Verify results
        assertEquals("SHA-512", provider.normalizeServerEndPointHashAlgorithmName("SHA512"), "Should normalize 'SHA512' to 'SHA-512'");
    }

    /**
     * Verifies normalization: "SHA3-256" becomes "SHA3-256".
     */
    @Test
    void testNormalizeServerEndPointHashAlgorithmName_SHA3_256()
    {
        // Execute & Verify results
        assertEquals("SHA3-256", provider.normalizeServerEndPointHashAlgorithmName("SHA3-256"), "Should normalize 'SHA3-256' to 'SHA3-256'");
    }

    /**
     * Verifies normalization: "MD5" becomes "MD5".
     */
    @Test
    void testNormalizeServerEndPointHashAlgorithmName_MD5()
    {
        // Execute & Verify results
        assertEquals("MD5", provider.normalizeServerEndPointHashAlgorithmName("MD5"), "Should normalize 'MD5' to 'MD5'");
    }

    /**
     * Verifies normalization: "sha1" becomes "SHA-1".
     */
    @Test
    void testNormalizeServerEndPointHashAlgorithmName_sha1()
    {
        // Execute & Verify results
        assertEquals("SHA-1", provider.normalizeServerEndPointHashAlgorithmName("sha1"), "Should normalize 'sha1' to 'SHA-1'");
    }

    /**
     * Verifies normalization: "SHA384" becomes "SHA-384".
     */
    @Test
    void testNormalizeServerEndPointHashAlgorithmName_SHA384()
    {
        // Execute & Verify results
        assertEquals("SHA-384", provider.normalizeServerEndPointHashAlgorithmName("SHA384"), "Should normalize 'SHA384' to 'SHA-384'");
    }

    /**
     * Verifies normalization: "SHA3-512" becomes "SHA3-512".
     */
    @Test
    void testNormalizeServerEndPointHashAlgorithmName_SHA3_512()
    {
        // Execute & Verify results
        assertEquals("SHA3-512", provider.normalizeServerEndPointHashAlgorithmName("SHA3-512"), "Should normalize 'SHA3-512' to 'SHA3-512'");
    }

    // ----------- Hash algorithm resolution tests -----------

    /**
     * Verifies resolution: "SHA256withRSA" becomes "SHA-256".
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_SHA256withRSA()
    {
        assertEquals("SHA-256", provider.resolveServerEndPointHashAlgorithm("SHA256withRSA"), "Resolution of 'SHA256withRSA' did not yield 'SHA-256'.");
    }

    /**
     * Verifies resolution: "SHA512withECDSA" becomes "SHA-512".
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_SHA512withECDSA()
    {
        assertEquals("SHA-512", provider.resolveServerEndPointHashAlgorithm("SHA512withECDSA"), "Resolution of 'SHA512withECDSA' did not yield 'SHA-512'.");
    }

    /**
     * Verifies resolution: "SHA1withRSA" becomes fallback "SHA-256".
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_SHA1withRSA_fallback()
    {
        assertEquals("SHA-256", provider.resolveServerEndPointHashAlgorithm("SHA1withRSA"), "Resolution of weak hash 'SHA1withRSA' did not yield fallback 'SHA-256'.");
    }

    /**
     * Verifies resolution: "MD5withRSA" becomes fallback "SHA-256".
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_MD5withRSA_fallback()
    {
        assertEquals("SHA-256", provider.resolveServerEndPointHashAlgorithm("MD5withRSA"), "Resolution of weak hash 'MD5withRSA' did not yield fallback 'SHA-256'.");
    }

    /**
     * Verifies resolution: "SHA3-256withECDSA" becomes "SHA3-256".
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_SHA3_256withECDSA()
    {
        assertEquals("SHA3-256", provider.resolveServerEndPointHashAlgorithm("SHA3-256withECDSA"), "Resolution of 'SHA3-256withECDSA' did not yield 'SHA3-256'.");
    }

    /**
     * Verifies resolution: "RSASSA-PSS" becomes null (no 'with').
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_RSASSA_PSS_null()
    {
        assertNull(provider.resolveServerEndPointHashAlgorithm("RSASSA-PSS"), "Resolution of 'RSASSA-PSS' (no 'with') did not yield null as expected.");
    }

    /**
     * Verifies resolution: null becomes null.
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_null_null()
    {
        assertNull(provider.resolveServerEndPointHashAlgorithm(null), "Resolution of null algorithm name did not yield null as expected.");
    }

    /**
     * Verifies resolution: "SHA256" becomes null (no 'with').
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_SHA256_null()
    {
        assertNull(provider.resolveServerEndPointHashAlgorithm("SHA256"), "Resolution of 'SHA256' (no 'with') did not yield null as expected.");
    }

    /**
     * Verifies resolution: "SHA256with" becomes null (missing key algorithm).
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_SHA256with_null()
    {
        assertNull(provider.resolveServerEndPointHashAlgorithm("SHA256with"), "Resolution of 'SHA256with' (missing key algorithm) did not yield null as expected.");
    }

    /**
     * Verifies resolution: "withRSA" becomes null (missing hash algorithm).
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_withRSA_null()
    {
        assertNull(provider.resolveServerEndPointHashAlgorithm("withRSA"), "Resolution of 'withRSA' (missing hash algorithm) did not yield null as expected.");
    }

    /**
     * Verifies that substituteWeakServerEndPointHashAlgorithm substitutes MD5 with SHA-256.
     */
    @Test
    void testSubstituteWeakServerEndPointHashAlgorithm_MD5()
    {
        assertEquals("SHA-256", provider.substituteWeakServerEndPointHashAlgorithm("MD5"), "MD5 should be substituted with SHA-256");
    }

    /**
     * Verifies that substituteWeakServerEndPointHashAlgorithm substitutes SHA1 with SHA-256.
     */
    @Test
    void testSubstituteWeakServerEndPointHashAlgorithm_SHA1()
    {
        assertEquals("SHA-256", provider.substituteWeakServerEndPointHashAlgorithm("SHA1"), "SHA1 should be substituted with SHA-256");
    }

    /**
     * Verifies that substituteWeakServerEndPointHashAlgorithm substitutes SHA-1 with SHA-256.
     */
    @Test
    void testSubstituteWeakServerEndPointHashAlgorithm_SHA_1()
    {
        assertEquals("SHA-256", provider.substituteWeakServerEndPointHashAlgorithm("SHA-1"), "SHA-1 should be substituted with SHA-256");
    }

    /**
     * Verifies that substituteWeakServerEndPointHashAlgorithm does not substitute SHA-512.
     */
    @Test
    void testSubstituteWeakServerEndPointHashAlgorithm_SHA_512()
    {
        assertEquals("SHA-512", provider.substituteWeakServerEndPointHashAlgorithm("SHA-512"), "SHA-512 should not be substituted");
    }

    /**
     * Verifies that substituteWeakServerEndPointHashAlgorithm does not substitute SHA3-256.
     */
    @Test
    void testSubstituteWeakServerEndPointHashAlgorithm_SHA3_256()
    {
        assertEquals("SHA3-256", provider.substituteWeakServerEndPointHashAlgorithm("SHA3-256"), "SHA3-256 should not be substituted");
    }

    /**
     * Verifies that Ed25519 (no hash) returns null for channel binding hash algorithm.
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_Ed25519_returnsNull()
    {
        assertNull(provider.resolveServerEndPointHashAlgorithm("Ed25519"), "Ed25519 should yield null (undefined) for channel binding hash algorithm");
    }

    /**
     * Verifies that extractPssHashAlgorithm returns null if PSS parameters are garbage (parsing fails).
     */
    @Test
    void testExtractPssHashAlgorithm_garbageParams_returnsNull()
    {
        final X509Certificate cert = mock(X509Certificate.class);
        // Provide invalid/garbage params
        when(cert.getSigAlgParams()).thenReturn(new byte[] {1,2,3,4,5,6,7,8,9});
        assertNull(provider.extractPssHashAlgorithm(cert), "Should return null when PSS parameters are garbage");
    }

    /**
     * Verifies that weird signature names/casing are handled correctly.
     */
    @Test
    void testResolveServerEndPointHashAlgorithm_weirdCasing()
    {
        assertEquals("SHA-256", provider.resolveServerEndPointHashAlgorithm("SHA256WITHRSA"), "Should normalize and resolve weird casing 'SHA256WITHRSA'");
        assertEquals("SHA-256", provider.resolveServerEndPointHashAlgorithm("sha256withrsa"), "Should normalize and resolve lower-case 'sha256withrsa'");
        assertEquals("SHA-256", provider.resolveServerEndPointHashAlgorithm("Sha256WithRsa"), "Should normalize and resolve mixed-case 'Sha256WithRsa'");
    }
}
