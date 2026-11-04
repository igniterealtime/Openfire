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
package org.jivesoftware.openfire.server.diagnostics;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.keystore.CertificateStoreConfiguration;
import org.jivesoftware.openfire.keystore.KeystoreTestUtils;
import org.jivesoftware.openfire.keystore.OpenfireX509TrustManager;
import org.jivesoftware.openfire.keystore.TrustStore;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Certificate;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateRevokedException;
import java.security.cert.CRLReason;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link CertificateVerdicts}, which says which property of the remote server's certificate made this server
 * reject it, so that the connection test can name it.
 *
 * Handshake rejections are produced by the trust manager that Openfire uses for TLS, so that the exceptions classified
 * are those a real handshake produces.
 *
 * @author Matthew Vivian
 */
public class CertificateVerdictsTest
{
    private static final String DOMAIN = "remote.example.org";

    private File trustStoreFile;
    private TrustStore trustStore;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() throws Exception
    {
        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        trustStoreFile = new File(tmpDir, "unittest-certificateverdicts-" + System.nanoTime() + ".jks");
        trustStoreFile.deleteOnExit();
        trustStore = new TrustStore(new CertificateStoreConfiguration("jks", trustStoreFile, "secret".toCharArray(), tmpDir), true);
    }

    @AfterEach
    public void tearDown()
    {
        trustStoreFile.delete();
    }

    // Rejections during the TLS handshake.

    @Test
    public void testExpiredIntermediateRejectedInHandshakeIsUntrustedWithoutItsDate() throws Exception
    {
        // Setup test fixture: the server's own certificate is valid, but an intermediate in its chain has expired.
        final X509Certificate[] chain = KeystoreTestUtils.generateCertificateChainWithExpiredIntermediateCert(DOMAIN).getCertificateChain();
        final Throwable failure = handshakeFailure(chain, trusting(root(chain)));

        // Execute system under test.
        final Certificate result = CertificateVerdicts.ofHandshakeRejection(failure);

        // Verify results.
        assertEquals(Certificate.untrusted(), result, "the expiry message names the remote server's own certificate, so an expired intermediate must not be reported with the intermediate's date as if it were the server's");
    }

    @Test
    public void testExpiredCertificateRejectedInHandshakeIsExpiredWithItsEndDate() throws Exception
    {
        // Setup test fixture.
        final X509Certificate[] chain = KeystoreTestUtils.generateCertificateChainWithExpiredEndEntityCert(DOMAIN).getCertificateChain();
        final Throwable failure = handshakeFailure(chain, trusting(root(chain)));

        // Execute system under test.
        final Certificate result = CertificateVerdicts.ofHandshakeRejection(failure);

        // Verify results.
        assertEquals(Certificate.expired(chain[0].getNotAfter().toInstant()), result, "an expired certificate must be reported as expired, with the end of its validity period");
    }

    @Test
    public void testNotYetValidCertificateRejectedInHandshakeIsNotYetValidWithItsStartDate() throws Exception
    {
        // Setup test fixture.
        final X509Certificate[] chain = KeystoreTestUtils.generateCertificateChainWithNotYetValidEndEntityCert(DOMAIN).getCertificateChain();
        final Throwable failure = handshakeFailure(chain, trusting(root(chain)));

        // Execute system under test.
        final Certificate result = CertificateVerdicts.ofHandshakeRejection(failure);

        // Verify results.
        assertEquals(Certificate.notYetValid(chain[0].getNotBefore().toInstant()), result, "a certificate that is not valid yet must be reported as such, with the start of its validity period");
    }

    @Test
    public void testCertificateFromUnknownAuthorityRejectedInHandshakeIsUntrusted() throws Exception
    {
        // Setup test fixture: a valid chain, but its root is not in the trust store.
        final X509Certificate[] chain = KeystoreTestUtils.generateValidCertificateChain(DOMAIN).getCertificateChain();
        final Throwable failure = handshakeFailure(chain, trusting());

        // Execute system under test.
        final Certificate result = CertificateVerdicts.ofHandshakeRejection(failure);

        // Verify results.
        assertEquals(Certificate.untrusted(), result, "a certificate whose issuer is not trusted must be reported as untrusted");
    }

    @Test
    public void testRevokedCertificateIsRevoked()
    {
        // Setup test fixture: revocation needs a CRL or OCSP responder, so the rejection is constructed in the shape that
        // the certification path validator gives it.
        final CertificateRevokedException revoked = new CertificateRevokedException(new Date(), CRLReason.KEY_COMPROMISE, new javax.security.auth.x500.X500Principal("CN=issuer"), Map.of());
        final Throwable failure = new SSLHandshakeException("PKIX path validation failed");
        failure.initCause(new CertificateException(new CertPathValidatorException("Certificate has been revoked", revoked, null, -1, CertPathValidatorException.BasicReason.REVOKED)));

        // Execute system under test.
        final Certificate result = CertificateVerdicts.ofHandshakeRejection(failure);

        // Verify results.
        assertEquals(Certificate.revoked(), result, "a revoked certificate must be reported as revoked");
    }

    @Test
    public void testOnlyCertificateFailuresAreCertificateRejections() throws Exception
    {
        // Setup test fixture.
        final X509Certificate[] chain = KeystoreTestUtils.generateValidCertificateChain(DOMAIN).getCertificateChain();
        final Throwable certificateRejection = handshakeFailure(chain, trusting());
        final Throwable protocolFailure = new SSLHandshakeException("Received fatal alert: handshake_failure");

        // Execute system under test and verify results.
        assertTrue(CertificateVerdicts.isCertificateRejection(certificateRejection), "a handshake stopped by this server's trust manager is a certificate rejection");
        assertFalse(CertificateVerdicts.isCertificateRejection(protocolFailure), "a handshake that failed for another reason is not a certificate rejection");
    }

    // Rejections after the handshake, by the identity check.

    @Test
    public void testNoCertificatesIsNotReceived()
    {
        assertEquals(Certificate.notReceived(), CertificateVerdicts.ofRejectedChain(null, DOMAIN, trustStore), "no certificates must be reported as not received");
        assertEquals(Certificate.notReceived(), CertificateVerdicts.ofRejectedChain(new java.security.cert.Certificate[0], DOMAIN, trustStore), "an empty chain must be reported as not received");
    }

    @Test
    public void testCertificateNotInTrustStoreIsUntrusted() throws Exception
    {
        // Setup test fixture.
        final X509Certificate[] chain = KeystoreTestUtils.generateValidCertificateChain(DOMAIN).getCertificateChain();

        // Execute system under test.
        final Certificate result = CertificateVerdicts.ofRejectedChain(chain, DOMAIN, trustStore);

        // Verify results.
        assertEquals(Certificate.untrusted(), result, "a chain that the trust store cannot vouch for must be reported as untrusted");
    }

    @Test
    public void testExplainingAnUntrustedChainLogsNoWarning() throws Exception
    {
        // Setup test fixture: the connection code has already checked this chain and logged why it failed; the verdict
        // only explains that decision, so repeating the warning would log it twice for every such connection.
        final X509Certificate[] chain = KeystoreTestUtils.generateValidCertificateChain(DOMAIN).getCertificateChain();
        final LoggerContext context = LoggerContext.getContext(false);
        final Configuration config = context.getConfiguration();
        final StringWriter captured = new StringWriter();
        final String name = "certificate-verdicts-test-" + System.nanoTime();
        final Appender appender = WriterAppender.createAppender(PatternLayout.createDefaultLayout(config), null, captured, name, false, true);
        appender.start();
        config.addAppender(appender);
        config.getRootLogger().addAppender(appender, Level.WARN, null);
        context.updateLoggers();

        // Execute system under test.
        final Certificate result;
        try {
            result = CertificateVerdicts.ofRejectedChain(chain, DOMAIN, trustStore);
        } finally {
            config.getRootLogger().removeAppender(name);
            config.getAppenders().remove(name);
            appender.stop();
            context.updateLoggers();
        }

        // Verify results.
        assertEquals(Certificate.untrusted(), result, "the verdict must still be untrusted");
        assertFalse(captured.toString().contains("validating certificate chain"), "explaining a rejected chain must not log the trust store's warning again: " + captured);
    }

    @Test
    public void testTrustedCertificateForAnotherDomainIsNameMismatchListingItsNames() throws Exception
    {
        // Setup test fixture.
        final X509Certificate[] chain = KeystoreTestUtils.generateValidCertificateChain("other.example.org").getCertificateChain();
        trustStore.installCertificate("ca", KeystoreTestUtils.toPemFormat(root(chain)));

        // Execute system under test.
        final Certificate result = CertificateVerdicts.ofRejectedChain(chain, DOMAIN, trustStore);

        // Verify results.
        assertEquals(Certificate.nameMismatch(List.of("other.example.org")), result, "a trusted certificate issued for another domain must be reported as a name mismatch, naming the domains it is issued for");
    }

    @Test
    public void testExpiredCertificateAcceptedByHandshakeIsExpired() throws Exception
    {
        // Setup test fixture: validity checking during the handshake can be switched off by configuration; the identity
        // check still rejects an expired certificate.
        final X509Certificate[] chain = KeystoreTestUtils.generateCertificateChainWithExpiredEndEntityCert(DOMAIN).getCertificateChain();
        trustStore.installCertificate("ca", KeystoreTestUtils.toPemFormat(root(chain)));

        // Execute system under test.
        final Certificate result = CertificateVerdicts.ofRejectedChain(chain, DOMAIN, trustStore);

        // Verify results.
        assertEquals(Certificate.expired(chain[0].getNotAfter().toInstant()), result, "an expired certificate must be reported as expired, with the end of its validity period");
    }

    private static X509Certificate root(final X509Certificate[] chain)
    {
        return chain[chain.length - 1];
    }

    private static OpenfireX509TrustManager trusting(final X509Certificate... anchors) throws Exception
    {
        final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        for (int i = 0; i < anchors.length; i++) {
            store.setCertificateEntry("anchor" + i, anchors[i]);
        }
        return new OpenfireX509TrustManager(store, false, true, false);
    }

    /**
     * Runs the trust manager's check as the TLS handshake does, and wraps the rejection as the handshake does.
     */
    private static Throwable handshakeFailure(final X509Certificate[] chain, final OpenfireX509TrustManager trustManager)
    {
        final CertificateException rejection = assertThrows(CertificateException.class, () -> trustManager.checkServerTrusted(chain, "RSA"), "the fixture must be rejected by the trust manager");
        final SSLHandshakeException failure = new SSLHandshakeException(rejection.getMessage());
        failure.initCause(rejection);
        return failure;
    }
}
