/*
 * Copyright (C) 2023-2025 Ignite Realtime Foundation. All rights reserved.
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

import org.awaitility.Awaitility;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.CertificateStoreConfiguration;
import org.jivesoftware.openfire.keystore.CertificateStoreManager;
import org.jivesoftware.openfire.keystore.IdentityStore;
import org.jivesoftware.openfire.keystore.KeystoreTestUtils;
import org.jivesoftware.openfire.keystore.TrustStore;
import org.jivesoftware.openfire.net.DNSUtil;
import org.jivesoftware.openfire.net.SrvRecord;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.Key;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static Key FIXTURE_VALID_KEY;
    private static Certificate[] FIXTURE_VALID_CERTIFICATE_CHAIN;
    private RemoteInitiatingServerDummy remoteInitiatingServerDummy;
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
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        XMPPServer.setInstance(xmppServer);

        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));

        // Use a temporary file to hold the identity store that is used by the tests.
        final CertificateStoreManager certificateStoreManager = mock(CertificateStoreManager.class, withSettings().strictness(Strictness.LENIENT));
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
            remoteInitiatingServerDummy.disconnect();
            remoteInitiatingServerDummy = null;
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
        AbstractRemoteServerDummy.log("Executing test:\n - Local Server (Recipient, System Under Test) Settings: " + localServerSettings + "\n - Remote Server (Initiator, dummy/mock server) Settings: " + remoteServerSettings + "\nExpected outcome: " + expected.getConnectionState());

        ConnectionListener connectionListener = null;
        try {
            AbstractRemoteServerDummy.log("Setup fixture: (start setting up fixture)");

            // Setup test fixture.
            AbstractRemoteServerDummy.log("Setup fixture: remote server TLS policy.");
            remoteInitiatingServerDummy.setEncryptionPolicy(remoteServerSettings.encryptionPolicy);

            AbstractRemoteServerDummy.log("Setup fixture: remote server dialback.");
            remoteInitiatingServerDummy.setDisableDialback(!remoteServerSettings.dialbackSupported);

            AbstractRemoteServerDummy.log("Setup fixture: remote server certificate state.");
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

            AbstractRemoteServerDummy.log("Setup fixture: local server TLS policy.");
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, localServerSettings.encryptionPolicy.toString());

            AbstractRemoteServerDummy.log("Setup fixture: local server dialback.");
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, localServerSettings.dialbackSupported ? "true" : "false");

            AbstractRemoteServerDummy.log("Setup fixture: local server certificate state.");
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

            AbstractRemoteServerDummy.log("Setup fixture: remote server init");
            remoteInitiatingServerDummy.init();
            if (remoteInitiatingServerDummy.getDialbackAuthoritativeServerPort() > 0) {
                DNSUtil.setDnsOverride(Map.of(RemoteInitiatingServerDummy.XMPP_DOMAIN, new SrvRecord("localhost", remoteInitiatingServerDummy.getDialbackAuthoritativeServerPort(), false)));
            }
            AbstractRemoteServerDummy.log("Setup fixture: (done with setting up fixture)");

            // execute system under test.
            AbstractRemoteServerDummy.log("Execute system under test: (start with execution)");
            JiveGlobals.setProperty(ConnectionSettings.Server.OLD_SSLPORT, String.valueOf(findFreeLocalPort()));
            connectionListener = new ConnectionListener(ConnectionType.SOCKET_S2S,
                ConnectionSettings.Server.OLD_SSLPORT,
                ConnectionManagerImpl.DEFAULT_SERVER_SSL_PORT,
                ConnectionSettings.Server.ENABLE_OLD_SSLPORT,
                "xmpp.server.processing.threads",
                null,
                JiveGlobals.getProperty(ConnectionSettings.Server.TLS_POLICY),
                ConnectionSettings.Server.AUTH_PER_CLIENTCERT_POLICY,
                null,
                identityStore.getConfiguration(),
                trustStore.getConfiguration(),
                ConnectionSettings.Server.COMPRESSION_SETTINGS);
            AbstractRemoteServerDummy.log("Execute system under test: starting connection listener");
            connectionListener.start();

            AbstractRemoteServerDummy.log("Execute system under test: mocking connection manager");
            final ConnectionManager connectionManager = Fixtures.mockConnectionManager();
            doReturn(Set.of(connectionListener)).when(connectionManager).getListeners(any(ConnectionType.class));
            doReturn(connectionListener).when(connectionManager).getListener(any(ConnectionType.class), anyBoolean());
            doReturn(connectionManager).when(XMPPServer.getInstance()).getConnectionManager();

            AbstractRemoteServerDummy.log("Execute system under test: make the remote server connect.");
            remoteInitiatingServerDummy.connect(connectionListener.getPort());
            AbstractRemoteServerDummy.log("Execute system under test: start connecting, block until done.");
            remoteInitiatingServerDummy.blockUntilDone(1, TimeUnit.MINUTES);
            AbstractRemoteServerDummy.log("Execute system under test: done connecting.");

            AbstractRemoteServerDummy.log("Execute system under test: get the incoming server session object.");
            final List<StreamID> receivedStreamIDs = remoteInitiatingServerDummy.getReceivedStreamIDs();
            final StreamID lastReceivedID;
            synchronized (receivedStreamIDs) {
                lastReceivedID = receivedStreamIDs.isEmpty() ? null : receivedStreamIDs.get(receivedStreamIDs.size() - 1);
            }
            LocalIncomingServerSession result = lastReceivedID == null ? null : XMPPServer.getInstance().getSessionManager().getIncomingServerSession(lastReceivedID);
            AbstractRemoteServerDummy.log("Execute system under test: (done with execution)");

            // Verify results
            AbstractRemoteServerDummy.log("Verify results (start)");
            AbstractRemoteServerDummy.log("Expect: " + expected.getConnectionState() + ", Result: " + result);
            switch (expected.getConnectionState())
            {
                case NO_CONNECTION:
                    if (result == null) {
                        assertNull(result, "No incoming session should be present when no connection is expected."); // Yes, this is silly.
                    } else {
                        assertFalse(result.isAuthenticated(), "Unexpectedly authenticated session when no connection is expected.");
                    }
                    break;
                case NON_ENCRYPTED_WITH_DIALBACK_AUTH:
                    Awaitility.await()
                        .atMost(2, TimeUnit.SECONDS)
                        .untilAsserted(() -> {
                            final LocalIncomingServerSession session = getLatestIncomingServerSession(receivedStreamIDs);
                            assertNotNull(session, "Expected an incoming session to be established.");
                            assertFalse(session.isEncrypted(), "Session unexpectedly encrypted in non-encrypted dialback scenario.");
                            assertTrue(session.isAuthenticated(), "Session should be authenticated through dialback.");
                            assertEquals(ServerSession.AuthenticationMethod.DIALBACK, session.getAuthenticationMethod(), "Expected dialback authentication method.");
                        });
                    break;
                case ENCRYPTED_WITH_DIALBACK_AUTH:
                    Awaitility.await()
                        .atMost(2, TimeUnit.SECONDS)
                        .untilAsserted(() -> {
                            final LocalIncomingServerSession session = getLatestIncomingServerSession(receivedStreamIDs);
                            assertEncryptedAuthenticatedSession(session, ServerSession.AuthenticationMethod.DIALBACK);
                        });

                    // Assertions that are specific to OF-1913:
                    assertStreamMetadataForTlsRestart();
                    break;
                case ENCRYPTED_WITH_SASLEXTERNAL_AUTH:
                    Awaitility.await()
                        .atMost(2, TimeUnit.SECONDS)
                        .untilAsserted(() -> {
                            final LocalIncomingServerSession session = getLatestIncomingServerSession(receivedStreamIDs);
                            assertEncryptedAuthenticatedSession(session, ServerSession.AuthenticationMethod.SASL_EXTERNAL);
                        });

                    // Assertions that are specific to OF-1913:
                    assertStreamMetadataForTlsRestart();
                    break;
            }
            AbstractRemoteServerDummy.log("Expectation met.");
            AbstractRemoteServerDummy.log("Verify results (done)");
        } finally {
            // Teardown test fixture.
            AbstractRemoteServerDummy.log("Teardown test fixture (start)");
            trustStore.delete("unit-test");
            if (connectionListener != null) {
                connectionListener.stop();
            }
            AbstractRemoteServerDummy.log("Teardown test fixture (done)");
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
                    if (tlsPolicy == Connection.TLSPolicy.directTLS) {
                        continue; // TODO add support for DirectTLS in this unit test!
                    }
                    final ServerSettings serverSettings = new ServerSettings(tlsPolicy, certificateState,true, dialbackSupported); // TODO add support for both strict certificate validation settings.
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

    /**
     * Returns a local TCP port number that is likely to be available to use.
     *
     * Note that during the execution of this method, the port is briefly in use. Also: there is no guarantee that after
     * this method returns, another process does not immediately start using the port that is returned.
     *
     * @return A TCP port number
     */
    public static int findFreeLocalPort() throws IOException
    {
        try (ServerSocket serverSocket = new ServerSocket(0)){
            return serverSocket.getLocalPort();
        }
    }

    /**
     * Resolves the most recently observed local incoming server session.
     *
     * The primary lookup path uses stream IDs observed by the remote dummy. A domain-based fallback is used to reduce
     * race-condition sensitivity when stream ID bookkeeping temporarily lags session registration.
     *
     * @param receivedStreamIDs stream IDs observed by the remote dummy while establishing the connection.
     * @return the most recently available local incoming server session, or {@code null} when no session is available.
     */
    private static LocalIncomingServerSession getLatestIncomingServerSession(final List<StreamID> receivedStreamIDs)
    {
        synchronized (receivedStreamIDs) {
            for (int i = receivedStreamIDs.size() - 1; i >= 0; i--) {
                final LocalIncomingServerSession session = XMPPServer.getInstance().getSessionManager().getIncomingServerSession(receivedStreamIDs.get(i));
                if (session != null) {
                    return session;
                }
            }
        }

        // Fall back to domain-based lookup in case stream ID bookkeeping temporarily lags session registration.
        final List<IncomingServerSession> domainSessions = XMPPServer.getInstance().getSessionManager().getIncomingServerSessions(RemoteInitiatingServerDummy.XMPP_DOMAIN);
        for (int i = domainSessions.size() - 1; i >= 0; i--) {
            final IncomingServerSession session = domainSessions.get(i);
            if (session instanceof LocalIncomingServerSession) {
                return (LocalIncomingServerSession) session;
            }
        }
        return null;
    }

    /**
     * Asserts that a session is encrypted, authenticated with the expected mechanism, and exposes a TLS protocol name.
     *
     * @param session the session under test.
     * @param expectedAuthenticationMethod the expected authentication method.
     */
    private static void assertEncryptedAuthenticatedSession(
        final LocalIncomingServerSession session,
        final ServerSession.AuthenticationMethod expectedAuthenticationMethod
    )
    {
        assertNotNull(session, "Expected an incoming session to be established.");
        assertTrue(session.isEncrypted(), "Session should be encrypted.");
        assertTrue(session.isAuthenticated(), "Session should be authenticated.");
        assertEquals(expectedAuthenticationMethod, session.getAuthenticationMethod(), "Unexpected authentication method.");

        final Connection connection = session.getConnection();
        assertNotNull(connection, "Expected a connection to be associated with the incoming session.");
        assertEquals(Optional.of("TLSv1.3"), connection.getTLSProtocolName(), "Unexpected TLS protocol on the established session.");
    }

    /**
     * Verifies metadata captured by the remote dummy for scenarios that require TLS stream restart.
     */
    private void assertStreamMetadataForTlsRestart()
    {
        Awaitility.await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                final List<StreamID> streamIDs = synchronizedSnapshot(remoteInitiatingServerDummy.getReceivedStreamIDs());
                final List<String> streamToValues = synchronizedSnapshot(remoteInitiatingServerDummy.getReceivedStreamToValues());
                final List<String> streamFromValues = synchronizedSnapshot(remoteInitiatingServerDummy.getReceivedStreamFromValues());

                assertEquals(2, streamIDs.size(), "Expected exactly two stream IDs due to TLS stream restart.");
                assertNotEquals(streamIDs.get(0), streamIDs.get(1), "Expected different stream IDs before and after TLS stream restart.");
                assertEquals(2, streamToValues.size(), "Expected two 'to' stream attribute values due to stream restart.");
                assertEquals(2, streamFromValues.size(), "Expected two 'from' stream attribute values due to stream restart.");
            });
    }

    /**
     * Creates a stable snapshot copy from a synchronized list.
     *
     * @param list the synchronized list to copy.
     * @param <T> list value type.
     * @return a detached snapshot of the provided list.
     */
    private static <T> List<T> synchronizedSnapshot(final List<T> list)
    {
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }
}
