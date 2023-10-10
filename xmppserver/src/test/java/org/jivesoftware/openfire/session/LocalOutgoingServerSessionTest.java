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
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.*;
import org.jivesoftware.openfire.net.DNSUtil;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionListener;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.security.Key;
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

        final ConnectionManager connectionManager = Fixtures.mockConnectionManager();
        final ConnectionListener connectionListener = Fixtures.mockConnectionListener();
        final RoutingTable routingTable = Fixtures.mockRoutingTable();
        doAnswer(new ConnectionConfigurationAnswer(identityStoreConfig, trustStoreConfig)).when(connectionListener).generateConnectionConfiguration();
        doReturn(Set.of(connectionListener)).when(connectionManager).getListeners(any(ConnectionType.class));
        doReturn(connectionListener).when(connectionManager).getListener(any(ConnectionType.class), anyBoolean());
        doReturn(connectionManager).when(xmppServer).getConnectionManager();
        doReturn(routingTable).when(xmppServer).getRoutingTable();

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
            final Set<String> protocols = Set.of("TLSv1.3", "TLSv1.2");
            return new ConnectionConfiguration(ConnectionType.SOCKET_S2S, true, 10, -1, Connection.ClientAuth.wanted, null, 9999, tlsPolicy, identityStoreConfig, trustStoreConfig, true, true, protocols, suites, Connection.CompressionPolicy.optional, true );
        }
    }

    public void setUp() throws Exception
    {
        remoteReceivingServerDummy = new RemoteReceivingServerDummy();
        remoteReceivingServerDummy.open();
        DNSUtil.setDnsOverride(Map.of(RemoteReceivingServerDummy.XMPP_DOMAIN, new DNSUtil.HostAddress("localhost", remoteReceivingServerDummy.getPort(), false)));
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
        if (RemoteReceivingServerDummy.doLog) System.out.println("Executing test:\n - Local Server (Openfire, System under test) Settings: " + localServerSettings + "\n - Remote Server (dummy/mock server) Settings: " + remoteServerSettings + "\nExpected outcome: " + expected.getConnectionState());

        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        JiveGlobals.setProperty("xmpp.server.session.initialise-timeout", Long.toString(1));

        try {
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

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results
            if (RemoteReceivingServerDummy.doLog) System.out.println("Expect: " + expected.getConnectionState() + ", Result: " + result);
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
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
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
            System.out.println("Test [" + i++ + "]: " + arguments.get()[0] + ", " + arguments.get()[1]);
        }
        return result;
    }
}
