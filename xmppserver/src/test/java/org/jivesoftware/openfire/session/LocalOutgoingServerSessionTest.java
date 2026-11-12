/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.session;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.*;
import org.jivesoftware.openfire.net.DNSUtil;
import org.jivesoftware.openfire.net.SrvRecord;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.jivesoftware.openfire.server.diagnostics.AttemptRecorder;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.security.Key;
import java.time.Duration;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify if an outbound server-to-server socket connection can be created (and where applicable:
 * encrypted and authenticated), verifying the implementation of {@link LocalOutgoingServerSession#createOutgoingSession(DomainPair, int)}
 *
 * These tests assume the following constants:
 * - TLS certificate validation is implemented correctly.
 * - The domain name in the certificate matches that of the server.
 *
 * This implementation uses instances of {@link RemoteReceivingServerDummy} to represent the remote server to which a connection
 * is being made.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @author Alex Gidman, alex.gidman@surevine.com
 */
@ExtendWith(MockitoExtension.class)
public class LocalOutgoingServerSessionTest
{
    /**
     * How long the tests that expect no stream wait for one, kept short so that they finish quickly.
     */
    private static final Duration SHORT_INITIALISE_TIMEOUT = Duration.ofSeconds(2);

    private static Key FIXTURE_VALID_KEY;
    private static Certificate[] FIXTURE_VALID_CERTIFICATE_CHAIN;
    private RemoteReceivingServerDummy remoteReceivingServerDummy;
    private File tmpIdentityStoreFile;
    private IdentityStore identityStore;
    private File tmpTrustStoreFile;
    private TrustStore trustStore;

    /**
     * Generates (one time) content for a 'valid' identity store: a private key and associated (self-signed) certificate
     * chain.
     *
     * This generated artifacts are stored in static fields, intended to be re-used by different tests. This prevents
     * each test from having to generate them, which saves around 66% of the CPU time that's consumed by the tests.
     */
    @BeforeAll
    public static void generateValidKeyAndCert() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        JiveGlobals.setProperty("xmpp.socket.netty.graceful-shutdown.quiet-period", "0");
        JiveGlobals.setProperty("xmpp.socket.netty.graceful-shutdown.timeout", "0");
        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        XMPPServer.setInstance(xmppServer);

        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        final File tmpValidIdentityStoreFile = new File(tmpDir, "unittest-identitystorevalid-" + System.currentTimeMillis() + ".jks");
        tmpValidIdentityStoreFile.deleteOnExit();
        try {
            final CertificateStoreConfiguration identityStoreConfig = new CertificateStoreConfiguration("jks", tmpValidIdentityStoreFile, "secret".toCharArray(), tmpDir);
            final IdentityStore validStore = new IdentityStore(identityStoreConfig, true);
            validStore.ensureDomainCertificate();
            final Enumeration<String> aliases = validStore.getStore().aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (validStore.getStore().isKeyEntry(alias)) {
                    FIXTURE_VALID_KEY = validStore.getStore().getKey(alias, "secret".toCharArray());
                    FIXTURE_VALID_CERTIFICATE_CHAIN = validStore.getStore().getCertificateChain(alias);
                    return;
                }
            }
        } finally {
            tmpValidIdentityStoreFile.delete();
        }
    }

    /**
     * Prepares the local server for operation. This mostly involves preparing the test fixture by mocking parts of the
     * API that {@link LocalOutgoingServerSession#createOutgoingSession(DomainPair, int)} uses when establishing a
     * connection.
     */
    @BeforeEach
    public void setUpEach() throws Exception {
        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));

        // Use a temporary file to hold the identity store that is used by the tests.
        final CertificateStoreManager certificateStoreManager = mock(CertificateStoreManager.class, withSettings().lenient());
        tmpIdentityStoreFile = new File(tmpDir, "unittest-identitystore-" + System.currentTimeMillis() + ".jks");
        tmpIdentityStoreFile.deleteOnExit();
        final CertificateStoreConfiguration identityStoreConfig = new CertificateStoreConfiguration("jks", tmpIdentityStoreFile, "secret".toCharArray(), tmpDir);
        identityStore = new IdentityStore(identityStoreConfig, true);
        doReturn(identityStore).when(certificateStoreManager).getIdentityStore(any());

        // Use a temporary file to hold the trust store that is used by the tests.
        tmpTrustStoreFile = new File(tmpDir, "unittest-truststore-" + System.currentTimeMillis() + ".jks");
        tmpTrustStoreFile.deleteOnExit();
        final CertificateStoreConfiguration trustStoreConfig = new CertificateStoreConfiguration("jks", tmpTrustStoreFile, "secret".toCharArray(), tmpDir);
        trustStore = new TrustStore(trustStoreConfig, true);
        doReturn(trustStore).when(certificateStoreManager).getTrustStore(any());

        // Mock the connection configuration.
        doReturn(certificateStoreManager).when(xmppServer).getCertificateStoreManager();

        final ConnectionManager connectionManager = Fixtures.mockConnectionManager();
        final ConnectionListener connectionListener = Fixtures.mockConnectionListener();
        final RoutingTable routingTable = Fixtures.mockRoutingTable();
        doAnswer(new ConnectionConfigurationAnswer(identityStoreConfig, trustStoreConfig)).when(connectionListener).generateConnectionConfiguration();
        doReturn(Set.of(connectionListener)).when(connectionManager).getListeners(any(ConnectionType.class));
        doReturn(connectionListener).when(connectionManager).getListener(any(ConnectionType.class), anyBoolean());
        doReturn(connectionManager).when(xmppServer).getConnectionManager();
        doReturn(routingTable).when(xmppServer).getRoutingTable();

        // Expose the singleton only after stubs are ready: lingering Netty threads from the prior test call getInstance() during cleanup, racing stub setup and corrupting Mockito's InvocationContainerImpl.
        XMPPServer.setInstance(xmppServer);

        setUp();
    }

    /**
     * Dynamically generate a ConnectionConfiguration answer, as used by the Mock ConnectionListener.
     * <p>
     * A dynamic answer is needed, as the value of the ConnectionSettings.Server.TLS_POLICY property needs to be
     * evaluated at run-time (this value is changed in the setup of many of the unit tests in this file).
     * </p>
     */
    private static class ConnectionConfigurationAnswer implements Answer<ConnectionConfiguration> {

        private CertificateStoreConfiguration identityStoreConfig;
        private CertificateStoreConfiguration trustStoreConfig;

        private ConnectionConfigurationAnswer(CertificateStoreConfiguration identityStoreConfig, CertificateStoreConfiguration trustStoreConfig)
        {
            this.identityStoreConfig = identityStoreConfig;
            this.trustStoreConfig = trustStoreConfig;
        }

        @Override
        public ConnectionConfiguration answer(InvocationOnMock invocation) throws Throwable
        {
            final Connection.TLSPolicy tlsPolicy = Connection.TLSPolicy.valueOf(JiveGlobals.getProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.optional.toString()));
            final Set<String> suites = Set.of("TLS_AES_256_GCM_SHA384","TLS_AES_128_GCM_SHA256","TLS_CHACHA20_POLY1305_SHA256","TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384","TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256","TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256","TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384","TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256","TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256","TLS_DHE_RSA_WITH_AES_256_GCM_SHA384","TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256","TLS_DHE_DSS_WITH_AES_256_GCM_SHA384","TLS_DHE_RSA_WITH_AES_128_GCM_SHA256","TLS_DHE_DSS_WITH_AES_128_GCM_SHA256","TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384","TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384","TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256","TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256","TLS_DHE_RSA_WITH_AES_256_CBC_SHA256","TLS_DHE_DSS_WITH_AES_256_CBC_SHA256","TLS_DHE_RSA_WITH_AES_128_CBC_SHA256","TLS_DHE_DSS_WITH_AES_128_CBC_SHA256","TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384","TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384","TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256","TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256","TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384","TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384","TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256","TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256","TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA","TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA","TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA","TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA","TLS_DHE_RSA_WITH_AES_256_CBC_SHA","TLS_DHE_DSS_WITH_AES_256_CBC_SHA","TLS_DHE_RSA_WITH_AES_128_CBC_SHA","TLS_DHE_DSS_WITH_AES_128_CBC_SHA","TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA","TLS_ECDH_RSA_WITH_AES_256_CBC_SHA","TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA","TLS_ECDH_RSA_WITH_AES_128_CBC_SHA","TLS_RSA_WITH_AES_256_GCM_SHA384","TLS_RSA_WITH_AES_128_GCM_SHA256","TLS_RSA_WITH_AES_256_CBC_SHA256","TLS_RSA_WITH_AES_128_CBC_SHA256","TLS_RSA_WITH_AES_256_CBC_SHA","TLS_RSA_WITH_AES_128_CBC_SHA","TLS_EMPTY_RENEGOTIATION_INFO_SCSV");
            final Set<String> protocols = Set.of("TLSv1.3", "TLSv1.2");
            final boolean verifyValidity = JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_CERTIFICATE_VERIFY_VALIDITY, true);
            return new ConnectionConfiguration(ConnectionType.SOCKET_S2S, true, 10, -1, Connection.ClientAuth.wanted, null, 9999, tlsPolicy, identityStoreConfig, trustStoreConfig, true, verifyValidity, false, protocols, suites, Connection.CompressionPolicy.optional, true );
        }
    }

    public void setUp() throws Exception
    {
        remoteReceivingServerDummy = new RemoteReceivingServerDummy();
        remoteReceivingServerDummy.open();
        DNSUtil.setDnsOverride(Map.of(RemoteReceivingServerDummy.XMPP_DOMAIN, new SrvRecord("localhost", remoteReceivingServerDummy.getPort(), false)));
    }

    @AfterEach
    public void tearDown() throws Exception
    {
        tmpIdentityStoreFile.delete();
        tmpTrustStoreFile.delete();
        identityStore = null;
        trustStore = null;
        DNSUtil.setDnsOverride(null);

        if (remoteReceivingServerDummy != null) {
            remoteReceivingServerDummy.close();
            remoteReceivingServerDummy = null;
        }

        Fixtures.clearExistingProperties(
            Set.of(
                "xmpp.domain",
                "xmpp.socket.netty.graceful-shutdown.quiet-period",
                "xmpp.socket.netty.graceful-shutdown.timeout"
            ));
    }

    @AfterAll
    public static void tearDownClass() {
        Fixtures.clearExistingProperties();
    }
    

    /**
     * Unit test in which Openfire initiates an outgoing server-to-server connection.
     *
     * This test is parameterized, meaning that the configuration of both the local server and the remote mock server is
     * passed as an argument to this method. These configurations are used to both initialize and execute the test, but
     * also to calculate the expected outcome of the test, given the provided configuration. This expected outcome is
     * asserted by the test implementation
     *
     * @param localServerSettings Server settings for the system under test (the 'local' server).
     * @param remoteServerSettings Server settings for the mock server that is used as a peer in this test.
     */
    @ParameterizedTest
    @MethodSource("arguments")
    public void outgoingTest(final ServerSettings localServerSettings, final ServerSettings remoteServerSettings)
        throws Exception
    {
        final ExpectedOutcome expected = ExpectedOutcome.generateExpectedOutcome(localServerSettings, remoteServerSettings);
        AbstractRemoteServerDummy.log("Executing test:\n - Local Server (Initiator, Openfire, System under test) Settings: " + localServerSettings + "\n - Remote Server (Recipient, dummy/mock server) Settings: " + remoteServerSettings + "\nExpected outcome: " + expected.getConnectionState());

        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        // Avoid CI jitter causing intermittent setup timeouts.
        JiveGlobals.setProperty("xmpp.server.session.initialise-timeout", Long.toString(10));

        try {
            AbstractRemoteServerDummy.log("Setup fixture: (start setting up fixture)");
            // Setup test fixture.

            // Remote server TLS policy.
            remoteReceivingServerDummy.setEncryptionPolicy(remoteServerSettings.encryptionPolicy);

            // Remote server dialback
            remoteReceivingServerDummy.setDisableDialback(!remoteServerSettings.dialbackSupported);

            // Remote server certificate state
            switch (remoteServerSettings.certificateState) {
                case INVALID:
                    remoteReceivingServerDummy.setUseExpiredEndEntityCertificate(true);
                    // Intended fall-through
                case VALID:
                    remoteReceivingServerDummy.preparePKIX();

                    // Install in local server's truststore.
                    final X509Certificate[] chain = remoteReceivingServerDummy.getGeneratedPKIX().getCertificateChain();
                    final X509Certificate caCert = chain[chain.length-1];
                    trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));
                    break;
                case MISSING:
                    break;
                default:
                    throw new IllegalStateException("Unsupported remote certificate state");
            }

            // Local server TLS policy.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, localServerSettings.encryptionPolicy.toString());

            // Local server dialback.
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, localServerSettings.dialbackSupported ? "true" : "false");

            // Local server certificate state
            switch (localServerSettings.certificateState) {
                case MISSING:
                    // Do not install domain certificate.
                    break;
                case INVALID:
                    // Insert an expired certificate into the identity store
                    identityStore.installCertificate(Fixtures.expiredX509Certificate, Fixtures.privateKeyForExpiredCert, "");
                    break;
                case VALID:
                    // Adds a valid certificate into identity store
                    identityStore.getStore().setKeyEntry( "selfsignedkey", FIXTURE_VALID_KEY, "secret".toCharArray(), FIXTURE_VALID_CERTIFICATE_CHAIN);
                    break;
            }

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteReceivingServerDummy.XMPP_DOMAIN);
            final int port = remoteReceivingServerDummy.getPort();
            AbstractRemoteServerDummy.log("Setup fixture: (done with setting up fixture)");

            // Execute system under test.
            AbstractRemoteServerDummy.log("Execute system under test: (start with execution)");
            AttemptRecorder recorder = new AttemptRecorder(domainPair.getLocal(), domainPair.getRemote());
            LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port, recorder);
            // Retry once if null is returned unexpectedly: transient connection failures (e.g. Netty bootstrap race) should not permanently fail the test.
            if (result == null && expected.getConnectionState() != ExpectedOutcome.ConnectionState.NO_CONNECTION) {
                AbstractRemoteServerDummy.log("Execute system under test: first attempt returned null unexpectedly; retrying.");
                recorder = new AttemptRecorder(domainPair.getLocal(), domainPair.getRemote());
                result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port, recorder);
            }
            AbstractRemoteServerDummy.log("Execute system under test: (done with execution)");

            // Verify results
            AbstractRemoteServerDummy.log("Verify results (start)");
            AbstractRemoteServerDummy.log("Expect: " + expected.getConnectionState() + ", Result: " + result);
            switch (expected.getConnectionState())
            {
                case NO_CONNECTION:
                    assertNull(result);
                    break;
                case NON_ENCRYPTED_WITH_DIALBACK_AUTH:
                    assertNotNull(result);
                    assertFalse(result.isClosed());
                    assertFalse(result.isEncrypted());
                    assertTrue(result.isAuthenticated());
                    assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
                    break;
                case ENCRYPTED_WITH_DIALBACK_AUTH:
                    assertNotNull(result);
                    assertFalse(result.isClosed());
                    assertTrue(result.isEncrypted());
                    assertTrue(result.isAuthenticated());
                    assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
                    assertEquals( "TLSv1.3", result.getConnection().getTLSProtocolName().get());
                    break;
                case ENCRYPTED_WITH_SASLEXTERNAL_AUTH:
                    assertNotNull(result);
                    assertFalse(result.isClosed());
                    assertTrue(result.isEncrypted());
                    assertTrue(result.isAuthenticated());
                    assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, result.getAuthenticationMethod());
                    assertEquals( "TLSv1.3", result.getConnection().getTLSProtocolName().get());
                    break;
            }
            final AttemptRecorder.Outcome expectedOutcome = result == null ? AttemptRecorder.Outcome.FAILED : AttemptRecorder.Outcome.NEW_SESSION;
            assertEquals(expectedOutcome, recorder.getOutcome(), "the attempt's recorder must record whether a new session resulted");
            assertNotNull(recorder.getStarted(), "the attempt's recorder must record when the attempt started");
            assertTrue(recorder.snapshot().getDns().isPresent(), "every attempt must record what DNS returned");
            if (result != null) {
                assertEquals(AttemptMilestones.Tcp.Outcome.CONNECTED, recorder.snapshot().getTcp().orElseThrow().outcome(), "an attempt that produced a session must record its TCP connection");
            }
            assertStreamTlsAndCertificateMilestones(localServerSettings, remoteServerSettings, recorder.snapshot());
            assertAuthenticationMilestone(expected.getConnectionState(), localServerSettings, remoteServerSettings, recorder.snapshot());
            if (result == null) {
                assertFailureRecordedWhereItHappened(recorder.snapshot());
            }
            AbstractRemoteServerDummy.log("Verify results (done)");
        } finally {
            // Teardown test fixture.
            AbstractRemoteServerDummy.log("Teardown test fixture (start)");
            trustStore.delete("unit-test");
            AbstractRemoteServerDummy.log("Teardown test fixture (start)");
        }
    }

    @Test
    public void testRemoteCertificateFromUnknownAuthorityIsRecordedAsUntrusted() throws Exception
    {
        // Setup test fixture: a valid chain whose authority is not in the local trust store.
        remoteReceivingServerDummy.preparePKIX();

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false);

        // Verify results.
        assertEquals(AttemptMilestones.Tls.certificateRejected(), recorder.snapshot().getTls().orElse(null), "the handshake is stopped by this server's trust manager, so TLS must be recorded as stopped by a certificate rejection");
        assertEquals(AttemptMilestones.Certificate.untrusted(), recorder.snapshot().getCertificate().orElse(null), "a certificate from an authority that is not trusted must be recorded as untrusted");
    }

    @Test
    public void testAcceptingSelfSignedCertificatesDoesNotMakeOneValid() throws Exception
    {
        // Setup test fixture: a self-signed certificate that the trust store does not hold, with self-signed
        // certificates accepted for server-to-server connections.
        remoteReceivingServerDummy.setUseSelfSignedCertificate(true);
        remoteReceivingServerDummy.preparePKIX();
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "true");
        final AttemptRecorder recorder;
        try {
            // Execute system under test.
            recorder = attemptOverOptionalTls(false);
        } finally {
            JiveGlobals.deleteProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS);
        }

        // Verify results.
        assertEquals(AttemptMilestones.Tls.Outcome.NEGOTIATED, recorder.snapshot().getTls().map(AttemptMilestones.Tls::outcome).orElse(null), "accepting self-signed certificates lets the handshake complete");
        assertEquals(AttemptMilestones.Certificate.untrusted(), recorder.snapshot().getCertificate().orElse(null), "the check for certificate login ignores the setting, so the certificate must still be recorded as untrusted");
    }

    @Test
    public void testNotVerifyingValidityDatesDoesNotMakeAnExpiredCertificateValid() throws Exception
    {
        // Setup test fixture: an expired certificate from a trusted authority, with validity dates not verified.
        remoteReceivingServerDummy.setUseExpiredEndEntityCertificate(true);
        remoteReceivingServerDummy.preparePKIX();
        final X509Certificate[] chain = trustRemoteAuthority();
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_CERTIFICATE_VERIFY_VALIDITY, "false");
        final AttemptRecorder recorder;
        try {
            // Execute system under test.
            recorder = attemptOverOptionalTls(false);
        } finally {
            JiveGlobals.deleteProperty(ConnectionSettings.Server.TLS_CERTIFICATE_VERIFY_VALIDITY);
        }

        // Verify results.
        assertEquals(AttemptMilestones.Tls.Outcome.NEGOTIATED, recorder.snapshot().getTls().map(AttemptMilestones.Tls::outcome).orElse(null), "not verifying validity dates lets the handshake complete");
        assertEquals(AttemptMilestones.Certificate.expired(chain[0].getNotAfter().toInstant()), recorder.snapshot().getCertificate().orElse(null), "the check for certificate login ignores the setting, so the certificate must still be recorded as expired, with its end date");
    }

    @Test
    public void testReusedSessionIsRecordedWithoutMilestones()
    {
        // Setup test fixture: an outgoing session that already authenticates this domain pair.
        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteReceivingServerDummy.XMPP_DOMAIN);
        final OutgoingServerSession existing = mock(OutgoingServerSession.class, withSettings().lenient());
        doReturn(true).when(existing).checkOutgoingDomainPair(domainPair);
        final SessionManager sessionManager = XMPPServer.getInstance().getSessionManager();
        doReturn(existing).when(sessionManager).getOutgoingServerSession(domainPair);
        final AttemptRecorder recorder = new AttemptRecorder(domainPair.getLocal(), domainPair.getRemote());

        // Execute system under test.
        final DomainAuthResult result = LocalOutgoingServerSession.authenticateDomain(domainPair, recorder);

        // Verify results.
        assertTrue(result.isSuccess(), "an existing session for the domain pair must be used");
        assertEquals(AttemptRecorder.Outcome.REUSED_SESSION, recorder.getOutcome(), "an attempt that made no connection must be recorded as reusing a session");
        final AttemptMilestones recorded = recorder.snapshot();
        assertTrue(recorded.getDns().isEmpty() && recorded.getTcp().isEmpty() && recorded.getStream().isEmpty() && recorded.getTls().isEmpty() && recorded.getCertificate().isEmpty() && recorded.getAuthentication().isEmpty(), "no connection step took place, so none may be recorded, or the checklist would show passes this attempt did not earn");
    }

    @Test
    public void testFailureToDescribeMilestonesDoesNotChangeTheResult()
    {
        // Setup test fixture: an outgoing session that already authenticates this domain pair, and a recorder that
        // fails when asked to describe what it recorded.
        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteReceivingServerDummy.XMPP_DOMAIN);
        final OutgoingServerSession existing = mock(OutgoingServerSession.class, withSettings().lenient());
        doReturn(true).when(existing).checkOutgoingDomainPair(domainPair);
        final SessionManager sessionManager = XMPPServer.getInstance().getSessionManager();
        doReturn(existing).when(sessionManager).getOutgoingServerSession(domainPair);
        final AttemptRecorder recorder = spy(new AttemptRecorder(domainPair.getLocal(), domainPair.getRemote()));
        doThrow(new IllegalStateException("Simulated recording failure")).when(recorder).describe();

        // Execute system under test.
        final DomainAuthResult result = LocalOutgoingServerSession.authenticateDomain(domainPair, recorder);

        // Verify results.
        assertTrue(result.isSuccess(), "a failure while recording must not change the outcome of the connection attempt");
        assertTrue(recorder.getLoggedProblems().isPresent(), "the diagnostic log must still be recorded, so that anyone waiting for the attempt to finish is released");
    }

    @Test
    public void testFailureToFinishTheRecordDoesNotChangeTheResult()
    {
        // Setup test fixture: an outgoing session that already authenticates this domain pair, and a recorder that
        // fails when told how the attempt ended.
        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteReceivingServerDummy.XMPP_DOMAIN);
        final OutgoingServerSession existing = mock(OutgoingServerSession.class, withSettings().lenient());
        doReturn(true).when(existing).checkOutgoingDomainPair(domainPair);
        final SessionManager sessionManager = XMPPServer.getInstance().getSessionManager();
        doReturn(existing).when(sessionManager).getOutgoingServerSession(domainPair);
        final AttemptRecorder recorder = spy(new AttemptRecorder(domainPair.getLocal(), domainPair.getRemote()));
        doThrow(new IllegalStateException("Simulated recording failure")).when(recorder).finish(any());

        // Execute system under test.
        final DomainAuthResult result = LocalOutgoingServerSession.authenticateDomain(domainPair, recorder);

        // Verify results.
        assertTrue(result.isSuccess(), "a failure while recording must not change the outcome of the connection attempt");
    }

    @Test
    public void testExpiredRemoteCertificateIsRecordedWithItsEndDate() throws Exception
    {
        // Setup test fixture.
        remoteReceivingServerDummy.setUseExpiredEndEntityCertificate(true);
        remoteReceivingServerDummy.preparePKIX();
        final X509Certificate[] chain = trustRemoteAuthority();

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false);

        // Verify results.
        assertEquals(AttemptMilestones.Tls.certificateRejected(), recorder.snapshot().getTls().orElse(null), "the handshake is stopped by this server's trust manager, so TLS must be recorded as stopped by a certificate rejection");
        assertEquals(AttemptMilestones.Certificate.expired(chain[0].getNotAfter().toInstant()), recorder.snapshot().getCertificate().orElse(null), "an expired certificate must be recorded as expired, with the end of its validity period");
    }

    @Test
    public void testNotYetValidRemoteCertificateIsRecordedWithItsStartDate() throws Exception
    {
        // Setup test fixture.
        remoteReceivingServerDummy.setPKIX(KeystoreTestUtils.generateCertificateChainWithNotYetValidEndEntityCert(RemoteReceivingServerDummy.XMPP_DOMAIN));
        final X509Certificate[] chain = trustRemoteAuthority();

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false);

        // Verify results.
        assertEquals(AttemptMilestones.Tls.certificateRejected(), recorder.snapshot().getTls().orElse(null), "the handshake is stopped by this server's trust manager, so TLS must be recorded as stopped by a certificate rejection");
        assertEquals(AttemptMilestones.Certificate.notYetValid(chain[0].getNotBefore().toInstant()), recorder.snapshot().getCertificate().orElse(null), "a certificate that is not valid yet must be recorded as such, with the start of its validity period");
    }

    @Test
    public void testRemoteCertificateForAnotherDomainIsRecordedAsNameMismatchAfterTls() throws Exception
    {
        // Setup test fixture: a trusted, current certificate, issued for another domain.
        remoteReceivingServerDummy.setPKIX(KeystoreTestUtils.generateValidCertificateChain("other.example.org"));
        trustRemoteAuthority();

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false);

        // Verify results.
        assertEquals(AttemptMilestones.Tls.Outcome.NEGOTIATED, recorder.snapshot().getTls().map(AttemptMilestones.Tls::outcome).orElse(null), "the trust manager does not check names, so TLS must be recorded as negotiated");
        assertEquals(AttemptMilestones.Certificate.nameMismatch(List.of("other.example.org")), recorder.snapshot().getCertificate().orElse(null), "a certificate issued for another domain must be recorded as a name mismatch, naming the domain it is issued for");
        assertEquals(AttemptRecorder.Outcome.FAILED, recorder.getOutcome(), "with strict certificate validation, a name mismatch must stop the attempt");
    }

    @Test
    public void testWarningLoggedOutsideTheDiagnosticLogIsCountedForTheAttempt() throws Exception
    {
        // Setup test fixture: a certificate issued for another domain, with strict validation off, so that the
        // connection carries on with Server Dialback after the connection handler logs a warning about the certificate.
        remoteReceivingServerDummy.setPKIX(KeystoreTestUtils.generateValidCertificateChain("other.example.org"));
        trustRemoteAuthority();
        JiveGlobals.setProperty(ConnectionSettings.Server.STRICT_CERTIFICATE_VALIDATION, "false");
        final AttemptRecorder recorder;
        try {
            // Execute system under test.
            recorder = attemptOverOptionalTls(true);
        } finally {
            JiveGlobals.deleteProperty(ConnectionSettings.Server.STRICT_CERTIFICATE_VALIDATION);
        }
        recorder.diagnosticLog(List.of());

        // Verify results.
        assertEquals(AttemptRecorder.Outcome.NEW_SESSION, recorder.getOutcome(), "without strict validation, the connection must carry on with Server Dialback");
        assertTrue(recorder.getLoggedProblems().orElseThrow().warnings() >= 1, "the warning the connection handler logs about the certificate must count for this attempt, or the log check would pass a connection that logged one");
    }

    @Test
    public void testStreamErrorFromRemoteIsRecordedWithItsCondition() throws Exception
    {
        // Setup test fixture: the remote refuses the stream, as a server does that does not serve the requested domain.
        remoteReceivingServerDummy.setStreamErrorAfterHeader("host-unknown");

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false);

        // Verify results.
        assertEquals(AttemptMilestones.Stream.streamError("host-unknown"), recorder.snapshot().getStream().orElse(null), "a stream refused by the remote must be recorded with the condition it sent");
        assertTrue(recorder.snapshot().getTls().isEmpty(), "the attempt ended before TLS, so nothing must be recorded for TLS");
    }

    @Test
    public void testRemoteClosingBeforeTheStreamIsRecordedAsClosed() throws Exception
    {
        // Setup test fixture: the remote accepts the connection, then closes it when this server opens a stream, as a
        // service other than XMPP can do.
        remoteReceivingServerDummy.setStreamResponse(AbstractRemoteServerDummy.StreamResponse.CLOSE);

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false, SHORT_INITIALISE_TIMEOUT);

        // Verify results.
        assertEquals(AttemptMilestones.Stream.closed(), recorder.snapshot().getStream().orElse(null), "a remote that closes the connection before opening a stream must be recorded as having closed it");
        assertEquals(AttemptRecorder.Outcome.FAILED, recorder.getOutcome(), "no session can be made without a stream");
    }

    @Test
    public void testSilentRemoteIsRecordedAsTimedOut() throws Exception
    {
        // Setup test fixture: the remote accepts the connection and never answers.
        remoteReceivingServerDummy.setStreamResponse(AbstractRemoteServerDummy.StreamResponse.STAY_SILENT);

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false, SHORT_INITIALISE_TIMEOUT);

        // Verify results.
        assertEquals(AttemptMilestones.Stream.timedOut(SHORT_INITIALISE_TIMEOUT), recorder.snapshot().getStream().orElse(null), "a remote that never opens a stream must be recorded as timed out, with how long this server waited");
    }

    @Test
    public void testStreamHeaderWithoutIdIsRecorded() throws Exception
    {
        // Setup test fixture: the remote answers with a stream header that has no stream ID (OF-2692).
        remoteReceivingServerDummy.setStreamResponse(AbstractRemoteServerDummy.StreamResponse.ANSWER_WITHOUT_ID);

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false, SHORT_INITIALISE_TIMEOUT);

        // Verify results.
        assertEquals(AttemptMilestones.Stream.missingId(), recorder.snapshot().getStream().orElse(null), "a stream header without a stream ID must be recorded as such, as this server closes the connection for it");
    }

    @Test
    public void testStartTlsRefusedByRemoteIsRecordedAsRefused() throws Exception
    {
        // Setup test fixture: the remote offers STARTTLS but answers the request with a failure.
        remoteReceivingServerDummy.setRefuseStartTls(true);
        remoteReceivingServerDummy.preparePKIX();
        trustRemoteAuthority();

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false);

        // Verify results.
        assertEquals(AttemptMilestones.Tls.refused(), recorder.snapshot().getTls().orElse(null), "a STARTTLS request answered with a failure must be recorded as refused");
    }

    /**
     * Installs the authority that issued the remote dummy's certificate in the local trust store.
     *
     * @return the remote dummy's certificate chain.
     */
    private X509Certificate[] trustRemoteAuthority() throws Exception
    {
        final X509Certificate[] chain = remoteReceivingServerDummy.getGeneratedPKIX().getCertificateChain();
        trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(chain[chain.length - 1]));
        return chain;
    }

    @Test
    public void testSaslExternalIsRecordedAsCertificateLogin() throws Exception
    {
        // Setup test fixture.
        remoteReceivingServerDummy.preparePKIX();
        trustRemoteAuthority();

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(true);

        // Verify results.
        assertEquals(AttemptMilestones.Authentication.saslExternal(), recorder.snapshot().getAuthentication().orElse(null), "a successful SASL EXTERNAL exchange must be recorded as certificate login");
    }

    @Test
    public void testDialbackAfterExternalRefusedIsRecordedWithItsReason() throws Exception
    {
        // Setup test fixture: the remote offers SASL EXTERNAL but refuses it, as a server does that does not trust this server's certificate.
        remoteReceivingServerDummy.setRefuseSaslExternal(true);
        remoteReceivingServerDummy.preparePKIX();
        trustRemoteAuthority();

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(true);

        // Verify results.
        assertEquals(AttemptMilestones.Authentication.dialbackAfterExternalRefused(), recorder.snapshot().getAuthentication().orElse(null), "dialback used because the remote refused certificate login must be recorded with that reason");
        assertEquals(AttemptRecorder.Outcome.NEW_SESSION, recorder.getOutcome(), "the dialback fallback must still establish a session");
    }

    @Test
    public void testExternalRefusedWithDialbackDisabledIsRecordedAsSuch() throws Exception
    {
        // Setup test fixture.
        remoteReceivingServerDummy.setRefuseSaslExternal(true);
        remoteReceivingServerDummy.preparePKIX();
        trustRemoteAuthority();

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false);

        // Verify results.
        assertEquals(AttemptMilestones.Authentication.externalRefusedDialbackDisabled(), recorder.snapshot().getAuthentication().orElse(null), "a refused certificate login with no dialback to fall back to must be recorded as such");
        assertEquals(AttemptRecorder.Outcome.FAILED, recorder.getOutcome(), "with nothing to fall back to, the attempt must fail");
    }

    @Test
    public void testDialbackRefusedIsRecordedAsRefused() throws Exception
    {
        // Setup test fixture: no TLS, so dialback is the only method, and the remote refuses it.
        remoteReceivingServerDummy.setEncryptionPolicy(Connection.TLSPolicy.disabled);
        remoteReceivingServerDummy.setRefuseDialback(true);

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(true);

        // Verify results.
        assertEquals(AttemptMilestones.Authentication.dialbackRefused(), recorder.snapshot().getAuthentication().orElse(null), "a dialback key answered with 'invalid' must be recorded as dialback refused");
    }

    @Test
    public void testNoUsableMechanismIsRecorded() throws Exception
    {
        // Setup test fixture: no TLS, so no certificate login, and no dialback on the remote.
        remoteReceivingServerDummy.setEncryptionPolicy(Connection.TLSPolicy.disabled);
        remoteReceivingServerDummy.setDisableDialback(true);

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(true);

        // Verify results.
        assertEquals(AttemptMilestones.Authentication.noCommonMechanism(), recorder.snapshot().getAuthentication().orElse(null), "a remote that offers no method this server can use must be recorded as such");
    }

    @Test
    public void testStreamErrorDuringAuthenticationIsRecordedAgainstAuthentication() throws Exception
    {
        // Setup test fixture.
        remoteReceivingServerDummy.setStreamErrorOnAuth("not-authorized");
        remoteReceivingServerDummy.preparePKIX();
        trustRemoteAuthority();

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(false);

        // Verify results.
        assertEquals(AttemptMilestones.Authentication.streamError("not-authorized"), recorder.snapshot().getAuthentication().orElse(null), "a stream error in answer to authentication must fail authentication, with its condition");
        assertEquals(AttemptMilestones.Stream.opened(RemoteReceivingServerDummy.XMPP_DOMAIN), recorder.snapshot().getStream().orElse(null), "the stream opened, so a later stream error must not fail the stream check");
    }

    @Test
    public void testFailedDialbackFallbackAfterTlsFailureIsRecorded() throws Exception
    {
        // Setup test fixture: the remote has no certificate, so the handshake fails; this server then tries dialback over
        // a new plain connection, which the remote refuses.
        remoteReceivingServerDummy.setRefuseDialback(true);

        // Execute system under test.
        final AttemptRecorder recorder = attemptOverOptionalTls(true);

        // Verify results.
        assertEquals(AttemptMilestones.Tls.Outcome.HANDSHAKE_FAILED, recorder.snapshot().getTls().map(AttemptMilestones.Tls::outcome).orElse(null), "the handshake with a remote that has no certificate must be recorded as failed");
        assertEquals(AttemptMilestones.Authentication.dialbackFallbackFailed(), recorder.snapshot().getAuthentication().orElse(null), "a dialback fallback over a plain connection that fails must be recorded as such");
    }

    /**
     * Makes one outbound attempt with TLS optional on both sides, a valid local certificate and strict certificate
     * validation.
     *
     * @param dialbackEnabled whether Server Dialback is enabled locally; disable it so that a certificate problem is not
     *                        hidden by a fallback.
     * @return the recorder of the attempt.
     */
    private AttemptRecorder attemptOverOptionalTls(final boolean dialbackEnabled) throws Exception
    {
        return attemptOverOptionalTls(dialbackEnabled, Duration.ofSeconds(10));
    }

    /**
     * Makes one outbound attempt as {@link #attemptOverOptionalTls(boolean)} does, waiting at most the given time for a
     * session.
     *
     * @param dialbackEnabled whether Server Dialback is enabled locally.
     * @param initialiseTimeout how long this server waits for the session; whole seconds.
     * @return the recorder of the attempt.
     */
    private AttemptRecorder attemptOverOptionalTls(final boolean dialbackEnabled, final Duration initialiseTimeout) throws Exception
    {
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        JiveGlobals.setProperty("xmpp.server.session.initialise-timeout", Long.toString(initialiseTimeout.toSeconds()));
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.optional.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, Boolean.toString(dialbackEnabled));
        identityStore.getStore().setKeyEntry("selfsignedkey", FIXTURE_VALID_KEY, "secret".toCharArray(), FIXTURE_VALID_CERTIFICATE_CHAIN);
        try {
            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteReceivingServerDummy.XMPP_DOMAIN);
            final AttemptRecorder recorder = new AttemptRecorder(domainPair.getLocal(), domainPair.getRemote());
            final LocalOutgoingServerSession session = LocalOutgoingServerSession.createOutgoingSession(domainPair, remoteReceivingServerDummy.getPort(), recorder);
            if (session != null) {
                session.close();
            }
            return recorder;
        } finally {
            trustStore.delete("unit-test");
        }
    }

    /**
     * Verifies what the attempt recorded about the stream, TLS and the remote certificate (checks 4 to 6 of the S2S
     * connection test), given what each side supports. The remote dummy always opens a stream; whether TLS is set up,
     * and what is recorded about the certificate, follows from the TLS policies and the remote's certificate.
     */
    private static void assertStreamTlsAndCertificateMilestones(final ServerSettings local, final ServerSettings remote, final AttemptMilestones recorded)
    {
        assertEquals(AttemptMilestones.Stream.opened(RemoteReceivingServerDummy.XMPP_DOMAIN), recorded.getStream().orElse(null), "the stream the remote opened must be recorded with the name it answered as");

        final AttemptMilestones.Tls tls = recorded.getTls().orElse(null);
        final AttemptMilestones.Certificate certificate = recorded.getCertificate().orElse(null);
        final boolean bothSupportTls = local.encryptionPolicy != Connection.TLSPolicy.disabled && remote.encryptionPolicy != Connection.TLSPolicy.disabled;
        if (local.encryptionPolicy == Connection.TLSPolicy.required && remote.encryptionPolicy == Connection.TLSPolicy.disabled) {
            assertEquals(AttemptMilestones.Tls.notOffered(), tls, "a remote that does not offer TLS to a server that requires it must be recorded as not offering it");
            assertNull(certificate, "without TLS there is no certificate to judge");
        } else if (local.encryptionPolicy == Connection.TLSPolicy.disabled && remote.encryptionPolicy == Connection.TLSPolicy.required) {
            assertEquals(AttemptMilestones.Tls.requiredButDisabled(), tls, "a remote that requires TLS from a server whose policy disables it must be recorded as such, as this server then closes the connection");
            assertNull(certificate, "without TLS there is no certificate to judge");
        } else if (!bothSupportTls) {
            assertEquals(AttemptMilestones.Tls.unencrypted(), tls, "when either side cannot use TLS, the connection must be recorded as unencrypted");
            assertNull(certificate, "without TLS there is no certificate to judge");
        } else {
            switch (remote.certificateState) {
                case VALID -> {
                    if (local.certificateState == ServerSettings.CertificateState.INVALID) {
                        // Over TLS 1.3, this server finishes the handshake before the remote has checked this server's
                        // certificate; the remote's rejection then arrives as an alert.
                        assertEquals(AttemptMilestones.Tls.Outcome.HANDSHAKE_FAILED, tls == null ? null : tls.outcome(), "a remote that rejects this server's expired certificate must be recorded as failing the handshake, not as negotiated");
                        // The handshake completed on this side, so the remote certificate may have been judged before the alert arrived.
                        assertTrue(certificate == null || certificate.equals(AttemptMilestones.Certificate.valid()), "the remote certificate is valid, so it may be recorded as valid or not at all, but was: " + certificate);
                        return;
                    }
                    assertEquals(AttemptMilestones.Tls.Outcome.NEGOTIATED, tls == null ? null : tls.outcome(), "TLS with a remote that has a valid certificate must be recorded as negotiated");
                    assertEquals("TLSv1.3", tls.protocol(), "the negotiated protocol must be recorded");
                    assertNotNull(tls.cipherSuite(), "the negotiated cipher suite must be recorded");
                    assertEquals(AttemptMilestones.Certificate.valid(), certificate, "a trusted, current certificate for the remote domain must be recorded as valid");
                }
                case INVALID -> {
                    assertEquals(AttemptMilestones.Tls.certificateRejected(), tls, "a handshake stopped because the remote certificate is expired must be recorded as a certificate rejection");
                    assertEquals(AttemptMilestones.Certificate.Outcome.EXPIRED, certificate == null ? null : certificate.outcome(), "the expired remote certificate must be recorded as expired");
                }
                case MISSING -> {
                    assertEquals(AttemptMilestones.Tls.Outcome.HANDSHAKE_FAILED, tls == null ? null : tls.outcome(), "a handshake with a remote that has no certificate must be recorded as failed");
                    assertNull(certificate, "a handshake that failed before any certificate was presented has no certificate to judge");
                }
            }
        }
    }

    /**
     * Verifies what the attempt recorded about authentication (check 7 of the S2S connection test) for every
     * combination that results in a session. The remote dummy offers SASL EXTERNAL only over TLS, and only to a peer
     * that presents a current certificate, so dialback over TLS is always used because EXTERNAL was not offered. Dialback
     * without TLS is either the same, or the fallback over a new plain connection after a failed TLS handshake.
     */
    private static void assertAuthenticationMilestone(final ExpectedOutcome.ConnectionState expected, final ServerSettings local, final ServerSettings remote, final AttemptMilestones recorded)
    {
        final AttemptMilestones.Authentication authentication = recorded.getAuthentication().orElse(null);
        switch (expected) {
            case ENCRYPTED_WITH_SASLEXTERNAL_AUTH ->
                assertEquals(AttemptMilestones.Authentication.saslExternal(), authentication, "a session authenticated with SASL EXTERNAL must be recorded as certificate login");
            case ENCRYPTED_WITH_DIALBACK_AUTH ->
                assertEquals(AttemptMilestones.Authentication.dialbackAfterExternalNotOffered(), authentication, "dialback over TLS is used here only because the remote did not offer SASL EXTERNAL, and must be recorded with that reason");
            case NON_ENCRYPTED_WITH_DIALBACK_AUTH -> {
                final boolean handshakeFailed = recorded.getTls().map(tls -> tls.outcome() == AttemptMilestones.Tls.Outcome.HANDSHAKE_FAILED).orElse(false);
                assertEquals(handshakeFailed ? AttemptMilestones.Authentication.dialback() : AttemptMilestones.Authentication.dialbackAfterExternalNotOffered(), authentication,
                    "dialback without TLS must be recorded as the fallback after a failed handshake, or as used because SASL EXTERNAL was not offered");
            }
            case NO_CONNECTION -> {
                // The reasons vary with where the attempt stopped; the focused tests cover each.
            }
        }
    }

    /**
     * Verifies that a failed attempt recorded why it failed at the point where the connection code decided it. The
     * remote dummy always answers, so in this matrix every failure is a decision one of the two sides made. When the
     * first failure on record (the one the S2S connection test reports) is a close or a wait that the recorder filled in
     * afterwards, a failure path in the connection code did not record its outcome, and the test would give a vague or
     * wrong diagnosis for it. A later step can still time out, as Server Dialback over a new connection can be under way
     * after a failed TLS handshake. See ADR-007.
     */
    private static void assertFailureRecordedWhereItHappened(final AttemptMilestones recorded)
    {
        // In the order of the checks.
        final List<Enum<?>> failures = new ArrayList<>();
        recorded.getStream().map(AttemptMilestones.Stream::outcome).filter(outcome -> outcome != AttemptMilestones.Stream.Outcome.OPENED).ifPresent(failures::add);
        recorded.getTls().map(AttemptMilestones.Tls::outcome).filter(outcome -> outcome != AttemptMilestones.Tls.Outcome.NEGOTIATED && outcome != AttemptMilestones.Tls.Outcome.UNENCRYPTED).ifPresent(failures::add);
        recorded.getCertificate().map(AttemptMilestones.Certificate::outcome).filter(outcome -> outcome != AttemptMilestones.Certificate.Outcome.VALID).ifPresent(failures::add);
        final Set<AttemptMilestones.Authentication.Outcome> authenticated = EnumSet.of(
            AttemptMilestones.Authentication.Outcome.SASL_EXTERNAL, AttemptMilestones.Authentication.Outcome.DIALBACK,
            AttemptMilestones.Authentication.Outcome.DIALBACK_AFTER_EXTERNAL_NOT_OFFERED, AttemptMilestones.Authentication.Outcome.DIALBACK_AFTER_EXTERNAL_REFUSED);
        recorded.getAuthentication().map(AttemptMilestones.Authentication::outcome).filter(outcome -> !authenticated.contains(outcome)).ifPresent(failures::add);

        assertFalse(failures.isEmpty(), "a failed attempt must record the step that failed");
        final Set<Enum<?>> filledIn = Set.of(
            AttemptMilestones.Stream.Outcome.CLOSED, AttemptMilestones.Stream.Outcome.TIMED_OUT,
            AttemptMilestones.Tls.Outcome.CLOSED_BEFORE_SET_UP, AttemptMilestones.Tls.Outcome.TIMED_OUT, AttemptMilestones.Tls.Outcome.NOT_SET_UP_IN_TIME,
            AttemptMilestones.Authentication.Outcome.CLOSED, AttemptMilestones.Authentication.Outcome.CLOSED_AFTER_EXTERNAL_ACCEPTED, AttemptMilestones.Authentication.Outcome.TIMED_OUT);
        assertFalse(filledIn.contains(failures.get(0)), "a failed attempt must record why it failed where the connection code decided it, not only a close or wait filled in afterwards, but recorded: " + List.of(recorded.getStream(), recorded.getTls(), recorded.getCertificate(), recorded.getAuthentication()));
    }

    /**
     * Provides the arguments for the method that implements the unit test.
     * @return Unit test arguments
     */
    private static Iterable<Arguments> arguments() {
        final Collection<Arguments> result = new LinkedList<>();

        final Set<ServerSettings> localServerSettings = new LinkedHashSet<>();
        final Set<ServerSettings> remoteServerSettings = new LinkedHashSet<>();

        for (final ServerSettings.CertificateState certificateState : ServerSettings.CertificateState.values()) {
            for (final boolean dialbackSupported : List.of(true, false)) {
                for (final Connection.TLSPolicy tlsPolicy : Connection.TLSPolicy.values()) {
                    if (tlsPolicy == Connection.TLSPolicy.directTLS) {
                        continue; // TODO add support for DirectTLS in this unit test!
                    }
                    final ServerSettings serverSettings = new ServerSettings(tlsPolicy, certificateState, true, dialbackSupported); // TODO add support for both strict certificate validation settings.
                    localServerSettings.add(serverSettings);
                    remoteServerSettings.add(serverSettings);
                }
            }
        }

        for (final ServerSettings local : localServerSettings) {
            for (final ServerSettings remote : remoteServerSettings) {
                result.add(Arguments.arguments(local, remote));
            }
        }

        // Not all test-runners easily identify the parameters that are used to run each test iteration. Those that do
        // not, typically show a number. By outputting the numbered arguments, they can be cross-referenced with any
        // failed test case.
        int i = 1;
        for (Arguments arguments : result) {
            AbstractRemoteServerDummy.log("Test [" + i++ + "]: " + arguments.get()[0] + ", " + arguments.get()[1]);
        }
        return result;
    }
}
