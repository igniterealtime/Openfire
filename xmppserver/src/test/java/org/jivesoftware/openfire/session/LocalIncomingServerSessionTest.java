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
package org.jivesoftware.openfire.session;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.*;
import org.jivesoftware.openfire.net.BlockingAcceptingMode;
import org.jivesoftware.openfire.net.DNSUtil;
import org.jivesoftware.openfire.net.SocketAcceptThread;
import org.jivesoftware.openfire.net.SocketReader;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify if an inbound server-to-server socket connection can be created (and where applicable:
 * encrypted and authenticated), verifying the implementation of {@link LocalIncomingServerSession}
 *
 * These tests assume the following constants:
 * - TLS certificate validation is implemented correctly.
 * - The domain name in the certificate matches that of the server.
 *
 * This implementation uses instances of {@link RemoteInitiatingServerDummy} to represent the remote server that
 * initiates a server-to-server connection.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @author Alex Gidman, alex.gidman@surevine.com
 */
@ExtendWith(MockitoExtension.class)
public class LocalIncomingServerSessionTest
{
    private RemoteInitiatingServerDummy remoteInitiatingServerDummy;
    private File tmpIdentityStoreFile;
    private IdentityStore identityStore;
    private File tmpTrustStoreFile;
    private TrustStore trustStore;

    /**
     * Prepares the local server for operation. This mostly involves preparing the test fixture by mocking parts of the
     * API that {@link LocalOutgoingServerSession#createOutgoingSession(DomainPair, int)} uses when establishing a
     * connection.
     */
    @BeforeEach
    public void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        XMPPServer.setInstance(xmppServer);

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

        final SessionManager sessionManager = new SessionManager(); // This is the system under test. We do not want to use a mock for this test!
        sessionManager.initialize(xmppServer);
        doReturn(sessionManager).when(xmppServer).getSessionManager();

        final ConnectionManager connectionManager = Fixtures.mockConnectionManager();
        final ConnectionListener connectionListener = Fixtures.mockConnectionListener();
        doAnswer(new ConnectionConfigurationAnswer(identityStoreConfig, trustStoreConfig)).when(connectionListener).generateConnectionConfiguration();
        doReturn(Set.of(connectionListener)).when(connectionManager).getListeners(any(ConnectionType.class));
        doReturn(connectionListener).when(connectionManager).getListener(any(ConnectionType.class), anyBoolean());
        doReturn(connectionManager).when(xmppServer).getConnectionManager();
        setUp();
    }

    /**
     * Dynamically generate a ConnectionConfiguration answer, as used by the Mock ConnectionListener.
     * <p>
     * A dynamic answer is needed, as the value of the ConnectionSettings.Server.TLS_POLICY property needs to be
     * evaluated at run-time (this value is changed in the setup of many of the unit tests in this file).
     * </p>
     */
    private static class ConnectionConfigurationAnswer implements Answer {

        private CertificateStoreConfiguration identityStoreConfig;
        private CertificateStoreConfiguration trustStoreConfig;

        private ConnectionConfigurationAnswer(CertificateStoreConfiguration identityStoreConfig, CertificateStoreConfiguration trustStoreConfig)
        {
            this.identityStoreConfig = identityStoreConfig;
            this.trustStoreConfig = trustStoreConfig;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable
        {
            final Connection.TLSPolicy tlsPolicy = Connection.TLSPolicy.valueOf(JiveGlobals.getProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.optional.toString()));
            final Set<String> suites = Set.of("TLS_AES_256_GCM_SHA384","TLS_AES_128_GCM_SHA256","TLS_CHACHA20_POLY1305_SHA256","TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384","TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256","TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256","TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384","TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256","TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256","TLS_DHE_RSA_WITH_AES_256_GCM_SHA384","TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256","TLS_DHE_DSS_WITH_AES_256_GCM_SHA384","TLS_DHE_RSA_WITH_AES_128_GCM_SHA256","TLS_DHE_DSS_WITH_AES_128_GCM_SHA256","TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384","TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384","TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256","TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256","TLS_DHE_RSA_WITH_AES_256_CBC_SHA256","TLS_DHE_DSS_WITH_AES_256_CBC_SHA256","TLS_DHE_RSA_WITH_AES_128_CBC_SHA256","TLS_DHE_DSS_WITH_AES_128_CBC_SHA256","TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384","TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384","TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256","TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256","TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384","TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384","TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256","TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256","TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA","TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA","TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA","TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA","TLS_DHE_RSA_WITH_AES_256_CBC_SHA","TLS_DHE_DSS_WITH_AES_256_CBC_SHA","TLS_DHE_RSA_WITH_AES_128_CBC_SHA","TLS_DHE_DSS_WITH_AES_128_CBC_SHA","TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA","TLS_ECDH_RSA_WITH_AES_256_CBC_SHA","TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA","TLS_ECDH_RSA_WITH_AES_128_CBC_SHA","TLS_RSA_WITH_AES_256_GCM_SHA384","TLS_RSA_WITH_AES_128_GCM_SHA256","TLS_RSA_WITH_AES_256_CBC_SHA256","TLS_RSA_WITH_AES_128_CBC_SHA256","TLS_RSA_WITH_AES_256_CBC_SHA","TLS_RSA_WITH_AES_128_CBC_SHA","TLS_EMPTY_RENEGOTIATION_INFO_SCSV");
            final Set<String> protocols = Set.of("TLSv1.2");
            return new ConnectionConfiguration(ConnectionType.SOCKET_S2S, true, 10, -1, Connection.ClientAuth.wanted, null, 9999, tlsPolicy, identityStoreConfig, trustStoreConfig, true, true, protocols, suites, Connection.CompressionPolicy.optional, true );
        }
    }

    public void setUp() throws Exception
    {
        remoteInitiatingServerDummy = new RemoteInitiatingServerDummy(Fixtures.XMPP_DOMAIN);
    }

    @AfterEach
    public void tearDown() throws Exception
    {
        tmpIdentityStoreFile.delete();
        tmpTrustStoreFile.delete();
        identityStore = null;
        trustStore = null;
        DNSUtil.setDnsOverride(null);

        if (remoteInitiatingServerDummy != null) {
            //remoteInitiatingServerDummy.close();
            remoteInitiatingServerDummy = null;
        }

        Fixtures.clearExistingProperties();
    }

    /**
     * Unit test in which Openfire reacts to an inbound server-to-server connection attempt.
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
    public void incomingTest(final ServerSettings localServerSettings, final ServerSettings remoteServerSettings)
        throws Exception
    {
        final ExpectedOutcome expected = ExpectedOutcome.generateExpectedOutcome(remoteServerSettings, localServerSettings);
        if (RemoteInitiatingServerDummy.doLog) System.out.println("Executing test:\n - Local Server, Recipient, System Under Test Settings: " + localServerSettings + "\n - Remote Server, Initiator, dummy/mock server Settings: " + remoteServerSettings + "\nExpected outcome: " + expected.getConnectionState());

        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        final IdentityStore identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.

            // Remote server TLS policy.
            remoteInitiatingServerDummy.setEncryptionPolicy(remoteServerSettings.encryptionPolicy);

            // Remote server dialback
            remoteInitiatingServerDummy.setDisableDialback(!remoteServerSettings.dialbackSupported);

            // Remote server certificate state
            switch (remoteServerSettings.certificateState) {
                case INVALID:
                    remoteInitiatingServerDummy.setUseExpiredEndEntityCertificate(true);
                    // Intended fall-through
                case VALID:
                    remoteInitiatingServerDummy.preparePKIX();

                    // Install in local server's truststore.
                    final X509Certificate[] chain = remoteInitiatingServerDummy.getGeneratedPKIX().getCertificateChain();
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
                    // Generate a valid certificate and insert into identity store
                    identityStore.ensureDomainCertificate();
                    break;
            }

            remoteInitiatingServerDummy.init();
            if (remoteInitiatingServerDummy.getDialbackAuthoritativeServerPort() > 0) {
                DNSUtil.setDnsOverride(Map.of(RemoteInitiatingServerDummy.XMPP_DOMAIN, new DNSUtil.HostAddress("localhost", remoteInitiatingServerDummy.getDialbackAuthoritativeServerPort(), false)));
            }

            // execute system under test.
            final SocketAcceptThread socketAcceptThread = new SocketAcceptThread(0, null, false);
            socketAcceptThread.setDaemon(true);
            socketAcceptThread.setPriority(Thread.MAX_PRIORITY);
            socketAcceptThread.start();

            // now, make the remote server connect.
            remoteInitiatingServerDummy.connect(socketAcceptThread.getPort());
            remoteInitiatingServerDummy.blockUntilDone(1, TimeUnit.MINUTES);

            // get the incoming server session object.
            final LocalIncomingServerSession result;
            BlockingAcceptingMode mode = ((BlockingAcceptingMode) socketAcceptThread.getAcceptingMode());
            final SocketReader socketReader = mode == null ? null : mode.getLastReader();
            result = socketReader == null ? null : (LocalIncomingServerSession) socketReader.getSession();

            // Verify results
            if (RemoteInitiatingServerDummy.doLog) System.out.println("Expect: " + expected.getConnectionState() + ", Result: " + result);
            switch (expected.getConnectionState())
            {
                case NO_CONNECTION:
                    if (result == null) {
                        assertNull(result); // Yes, this is silly.
                    } else {
                        assertFalse(result.isAuthenticated());
                    }
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
                    break;
                case ENCRYPTED_WITH_SASLEXTERNAL_AUTH:
                    assertNotNull(result);
                    assertFalse(result.isClosed());
                    assertTrue(result.isEncrypted());
                    assertTrue(result.isAuthenticated());
                    assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, result.getAuthenticationMethod());
                    break;
            }
            if (RemoteInitiatingServerDummy.doLog) System.out.println("Expectation met.");
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
            remoteInitiatingServerDummy.disconnect();
        }
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
                    if (tlsPolicy == Connection.TLSPolicy.legacyMode) {
                        continue; // TODO add support for DirectTLS in this unit test!
                    }
                    final ServerSettings serverSettings = new ServerSettings(tlsPolicy, certificateState, dialbackSupported);
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

        return result;
    }
}
