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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.*;
import org.jivesoftware.openfire.net.DNSUtil;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.JiveGlobals;
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
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;

import static org.jivesoftware.openfire.session.ExpectedOutcome.ConnectionState.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify if an outbound server-to-server socket connection can be created (and where applicable:
 * encrypted and authenticated), verifying the implementation of {@link LocalOutgoingServerSession#createOutgoingSession(DomainPair, int)}
 *
 * These tests assume the following constants:
 * - TLS certificate validation is implemented correctly.
 * - The domain name in the certificate matches that of the server.
 *
 * This implementation uses instances of {@link RemoteServerDummy} to represent the remote server to which a connection
 * is being made.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @author Alex Gidman, alex.gidman@surevine.com
 */
@ExtendWith(MockitoExtension.class)
public class LocalOutgoingServerSessionParameterizedTest
{
    private RemoteServerDummy remoteServerDummy;
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
    public  void setUpClass() throws Exception {
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

        final ConnectionManagerImpl connectionManager = Fixtures.mockConnectionManager();
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
        remoteServerDummy = new RemoteServerDummy();
        remoteServerDummy.open();
        DNSUtil.setDnsOverride(Map.of(RemoteServerDummy.XMPP_DOMAIN, new DNSUtil.HostAddress("localhost", remoteServerDummy.getPort(), false)));
    }

    @AfterEach
    public void tearDown() throws Exception
    {
        tmpIdentityStoreFile.delete();
        tmpTrustStoreFile.delete();
        identityStore = null;
        trustStore = null;
        DNSUtil.setDnsOverride(null);

        if (remoteServerDummy != null) {
            remoteServerDummy.close();
            remoteServerDummy = null;
        }

        Fixtures.clearExistingProperties();
    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void doTest(final ServerSettings localServerSettings, final ServerSettings remoteServerSettings)
        throws Exception
    {
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        final IdentityStore identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore(ConnectionType.SOCKET_S2S);

        try {
            // Setup test fixture.

            // Remote server TLS policy.
            remoteServerDummy.setEncryptionPolicy(remoteServerSettings.encryptionPolicy);

            // Remote server dialback
            remoteServerDummy.setDisableDialback(!remoteServerSettings.dialbackSupported);

            // Remote server certificate state
            switch (remoteServerSettings.certificateState) {
                case INVALID:
                    remoteServerDummy.setUseExpiredEndEntityCertificate(true);
                    // Intended fall-through
                case VALID:
                    remoteServerDummy.preparePKIX();

                    // Install in local server's truststore.
                    final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
                    final X509Certificate caCert = chain[chain.length-1];
                    trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));
                    break;
                case MISSING:
                    break;
                default:
                    throw new IllegalStateException("Unsupported remote certificate state");
            }

            // Local server TLS policy.
            final Connection.TLSPolicy localTlsPolicy;
            switch (localServerSettings.encryptionPolicy) {
                case REQUIRED:
                    localTlsPolicy = Connection.TLSPolicy.required;
                    break;
                case OPTIONAL:
                    localTlsPolicy = Connection.TLSPolicy.optional;
                    break;
                case DISABLED:
                    localTlsPolicy = Connection.TLSPolicy.disabled;
                    break;
                default:
                    throw new IllegalStateException("Unsupported local TLS policy");
            }
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, localTlsPolicy.toString());

            // Local server dialback.
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, localServerSettings.dialbackSupported ? "true" : "false");

            // Local server certificate state
            switch (localServerSettings.certificateState) {
                case MISSING:
                    // Do not install domain certificate.
                    break;
                case INVALID:
                    // Insert an expired certificate into the identity store
                    identityStore.installCertificate(Fixtures.invalidX509Certificate, Fixtures.invalidX509CertificatePrivateKey, "");
                    break;
                case VALID:
                    // Generate a valid certificate and insert into identity store
                    identityStore.ensureDomainCertificate();
                    break;
            }

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results
            final ExpectedOutcome.ConnectionState expected = generateExpectedOutcome(localServerSettings, remoteServerSettings).getConnectionState();
            System.out.println("Expect: " + expected);
            switch (expected)
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
                    break;
                case ENCRYPTED_WITH_SASLEXTERNAL_AUTH:
                    assertNotNull(result);
                    assertFalse(result.isClosed());
                    assertTrue(result.isEncrypted());
                    assertTrue(result.isAuthenticated());
                    assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, result.getAuthenticationMethod());
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
            for (final boolean dialbackSupported : Set.of(true, false)) {
                for (final ServerSettings.EncryptionPolicy tlsPolicy : ServerSettings.EncryptionPolicy.values()) {
                    final ServerSettings serverSettings = new ServerSettings(certificateState, dialbackSupported, tlsPolicy);
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

    /**
     * Given the configuration of the initiating and receiving server, returns the expected outcome of an outbound
     * server-to-server connection attempt.
     *
     * @param initiatingServer Configuration of the local server
     * @param receivingServer Configuration of the remote server
     * @return the expected outcome
     */
    public static ExpectedOutcome generateExpectedOutcome(final ServerSettings initiatingServer, final ServerSettings receivingServer) {
        final ExpectedOutcome expectedOutcome = new ExpectedOutcome();

        switch (initiatingServer.encryptionPolicy) {
            case DISABLED: // <-- Initiating server's encryption policy.
                switch (receivingServer.encryptionPolicy) {
                    case DISABLED: // Intended fall-through: if one peer disables TLS, it won't be used in any circumstances.
                    case OPTIONAL:
                        // The certificate status of both peers is irrelevant, as TLS won't happen.
                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                            expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK_AUTH, "although TLS is not available (so it cannot be used for encryption or authentication), Dialback is available, which allows the Initiating Entity to be authenticated by the Receiving Entity.");
                        } else {
                            expectedOutcome.set(NO_CONNECTION, "TLS and Dialback aren't available, making it impossible for the Initiating Entity to be authenticated by the Receiving Entity.");
                        }
                        break;
                    case REQUIRED:
                        expectedOutcome.set(NO_CONNECTION, "one peer requires encryption while the other disables encryption. This cannot work.");
                        break;
                }
                break;

            case OPTIONAL: // <-- Initiating server's encryption policy.
                switch (receivingServer.encryptionPolicy) {
                    case DISABLED:
                        // The certificate status of both peers is irrelevant, as TLS won't happen.
                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                            expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK_AUTH, "TLS is not available, so it cannot be used for encryption or authentication. Dialback is available, which allows the Initiating Entity to be authenticated by the Receiving Entity.");
                        } else {
                            expectedOutcome.set(NO_CONNECTION, "TLS and Dialback aren't available, making it impossible for the Initiating Entity to be authenticated by the Receiving Entity.");
                        }
                        break;
                    case OPTIONAL:
                        switch (receivingServer.certificateState) {
                            case MISSING:
                                if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                    // TODO: should we take into account a manual configuration of an ANON cypher suite, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication?
                                    expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity does not provides a TLS certificate. As ANON cypher suites are expected to be unavailable, Initiating Entity cannot negotiate TLS, so that cannot be used for encryption or authentication. Dialback is available, so authentication can occur.");
                                } else {
                                    expectedOutcome.set(NO_CONNECTION, "Receiving Entity does not provides a TLS certificate, Initiating Entity cannot negotiate TLS. With TLS and Dialback unavailable, authentication cannot occur (even if usage of an ANON cypher suite would make TLS-for-encryption possible)");
                                }
                                break;
                            case INVALID:
                                // TODO Is this possibly an allowable OF-2591 edge-case? Worry about DOWNGRADE ATTACK VECTOR?
                                // possibly: expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK), // Encryption is configured to be OPTIONAL, so maybe allowable?
                                expectedOutcome.set(NO_CONNECTION, "the Initiating Entity will fail to negotiate TLS, as the Receiving Entity's certificate is not valid.");
                                break;
                            case VALID:
                                switch (initiatingServer.certificateState) {
                                    case MISSING:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity, but Dialback is available for that purpose.");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity. As Dialback is also not available, authentication cannot occur.");
                                        }
                                        break;
                                    case INVALID:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            // TODO: should the Initiating Entity be allowed to authenticate using Dialback over an encrypted TLS connection - or should TLS fail hard when invalid certs are used (the client _could_ opt to not send those...)? Is this possibly an allowable OF-2591 edge-case, or is this a DOWNGRADE ATTACK vector?
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS.");
                                            // possibly: expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK), // fail TLS but allow unencrypted. Perhaps better to fail connecting, to not give false sense of encryption security? DOWNGRADE ATTACK VECTOR? Encryption is configured to be OPTIONAL, so maybe allowable?
                                            // possibly: expectedOutcome.set(ENCRYPTED_WITH_DIALBACK), // do not fail TLS with invalid client cert, as it's usable for encryption even if it's not used for authentication. DOWNGRADE ATTACK VECTOR?
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS. Even if Receiving Entity would negotiate TLS for encryption, it can't use Initiating Entity's invalid cert for Authentication. As Dialback is also not available, authentication cannot occur.");
                                        }
                                        break;
                                    case VALID:
                                        // valid certs all around. Dialback is not needed.
                                        expectedOutcome.set(ENCRYPTED_WITH_SASLEXTERNAL_AUTH, "TLS is usable for both encryption and authentication.");
                                        break;
                                }
                                break;
                        }
                        break;
                    case REQUIRED:
                        switch (receivingServer.certificateState) {
                            case MISSING:
                                expectedOutcome.set(NO_CONNECTION, "Receiving Entity requires encryption, but it does not provide a TLS certificate. As ANON cypher suites are expected to be unavailable, the Initiating Entity cannot negotiate TLS and therefor the required encrypted connection cannot be established.");
                                // TODO: should we take into account a manual configuration of an ANON cypher suite, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication?
                                break;
                            case INVALID:
                                expectedOutcome.set(NO_CONNECTION, "Receiving Entity requires encryption, but it does provides an invalid TLS certificate. The Initiating Entity cannot negotiate TLS and therefor the required encrypted connection cannot be established.");
                                // TODO: should we allow TLS to be used anyway, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication? THIS INTRODUCES DOWNGRADE ATTACK VECTOR.
                                break;
                            case VALID:
                                // TODO - AG We need to check the initiating servers certificate
                                switch (initiatingServer.certificateState) {
                                    case MISSING:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity, but Dialback is available for that purpose.");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity. As Dialback is also not available, authentication cannot occur.");
                                        }
                                        break;
                                    case INVALID:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            // TODO: should the Initiating Entity be allowed to authenticate using Dialback over an encrypted TLS connection - or should TLS fail hard when invalid certs are used (the client _could_ opt to not send those...)? Is this possibly an allowable OF-2591 edge-case, or is this a DOWNGRADE ATTACK vector?
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS.");
                                            // possibly: expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK), // fail TLS but allow unencrypted. Perhaps better to fail connecting, to not give false sense of encryption security? DOWNGRADE ATTACK VECTOR? Encryption is configured to be OPTIONAL, so maybe allowable?
                                            // possibly: expectedOutcome.set(ENCRYPTED_WITH_DIALBACK), // do not fail TLS with invalid client cert, as it's usable for encryption even if it's not used for authentication. DOWNGRADE ATTACK VECTOR?
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS. Even if Receiving Entity would negotiate TLS for encryption, it can't use Initiating Entity's invalid cert for Authentication. As Dialback is also not available, authentication cannot occur.");
                                        }
                                        break;
                                    case VALID:
                                        // valid certs all around. Dialback is not needed.
                                        expectedOutcome.set(ENCRYPTED_WITH_SASLEXTERNAL_AUTH, "TLS is usable for both encryption and authentication.");
                                        break;
                                }
                                break;
                        }
                        break;
                }
                break;

            case REQUIRED: // <-- Initiating server's encryption policy.
                switch (receivingServer.encryptionPolicy) {
                    case DISABLED:
                        expectedOutcome.set(NO_CONNECTION, "one peer requires encryption, the other disables encryption. This cannot work.");
                        break;
                    case OPTIONAL:
                        switch (receivingServer.certificateState) {
                            case MISSING:
                                expectedOutcome.set(NO_CONNECTION, "Receiving Entity does not provide a TLS certificate. As ANON cypher suites are expected to be unavailable, the Initiating Entity cannot negotiate TLS and therefor the required encrypted connection cannot be established.");
                                // TODO: should we take into account a manual configuration of an ANON cypher suite, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication?
                                break;
                            case INVALID:
                                expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides an invalid TLS certificate. The Initiating Entity cannot negotiate TLS and therefor the required encrypted connection cannot be established.");
                                // TODO: should we allow TLS to be used anyway, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication? THIS INTRODUCES DOWNGRADE ATTACK VECTOR.
                                break;
                            case VALID:
                                switch (initiatingServer.certificateState) {
                                    case MISSING:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity, but Dialback is available for that purpose.");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity. As Dialback is also not available, authentication cannot occur.");
                                        }
                                        break;
                                    case INVALID:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            // TODO: should the Initiating Entity be allowed to authenticate using Dialback over an encrypted TLS connection - or should TLS fail hard when invalid certs are used (the client _could_ opt to not send those...)? Is this possibly an allowable OF-2591 edge-case, or is this a DOWNGRADE ATTACK vector?
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS.");
                                            // AG: this is correct I think. The language in 13.7.2 states in block capitals if a certificate is present it MUST attempt validation; if the validation fails, the connection terminates. See RFC2119 to confirm this is an absolute requirement.
                                            // possibly: expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK), // fail TLS but allow unencrypted. Perhaps better to fail connecting, to not give false sense of encryption security? DOWNGRADE ATTACK VECTOR? Encryption is configured to be OPTIONAL, so maybe allowable?
                                            // possibly: expectedOutcome.set(ENCRYPTED_WITH_DIALBACK), // do not fail TLS with invalid client cert, as it's usable for encryption even if it's not used for authentication. DOWNGRADE ATTACK VECTOR?
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS. Even if Receiving Entity would negotiate TLS for encryption, it can't use Initiating Entity's invalid cert for Authentication. As Dialback is also not available, authentication cannot occur.");
                                        }
                                        break;
                                    case VALID:
                                        // valid certs all around. Dialback is not needed.
                                        expectedOutcome.set(ENCRYPTED_WITH_SASLEXTERNAL_AUTH, "TLS is usable for both encryption and authentication.");
                                        break;
                                }
                                break;
                        }
                        break;
                    case REQUIRED:
                        switch (receivingServer.certificateState) {
                            case MISSING:
                                expectedOutcome.set(NO_CONNECTION, "Receiving Entity does not provide a TLS certificate. As ANON cypher suites are expected to be unavailable, the Initiating Entity cannot negotiate TLS and therefor the required encrypted connection cannot be established.");
                                // TODO: should we take into account a manual configuration of an ANON cypher suite, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication?
                                break;
                            case INVALID:
                                expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides an invalid TLS certificate. The Initiating Entity cannot negotiate TLS and therefor the required encrypted connection cannot be established.");
                                // TODO: should we allow TLS to be used anyway, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication? THIS INTRODUCES DOWNGRADE ATTACK VECTOR.
                                // AG: This is the expected behaviour, OF-2555 suggests this is not the current behaviour of Openfire as "if validation fails but Dialback is available", the connection is made.
                                break;
                            case VALID:
                                switch (initiatingServer.certificateState) {
                                    case MISSING:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Initiating Entity can negotiate encryption, but does not provide a certificate. SASL EXTERNAL cannot be used, but Dialback is available, so authentication can occur.");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity can negotiate encryption, but does not provide a certificate. As Dialback is not available, authentication cannot occur. Connection cannot be established.");
                                        }
                                        break;
                                    case INVALID:
                                        // TODO: should the Receiving Entity be allowed to authenticate using Dialback? Is this possibly an allowable OF-2591 edge-case?
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            // TODO: should the Initiating Entity be allowed to authenticate using Dialback over an encrypted TLS connection - or should TLS fail hard when invalid certs are used (the client _could_ opt to not send those...)? Is this possibly an allowable OF-2591 edge-case, or is this a DOWNGRADE ATTACK vector?
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS.");
                                            // AG: this is correct I think. The language in 13.7.2 states in block capitals if a certificate is present it MUST attempt validation; if the validation fails, the connection terminates. See RFC2119 to confirm this is an absolute requirement.
                                            // possibly: expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK), // fail TLS but allow unencrypted. Perhaps better to fail connecting, to not give false sense of encryption security? DOWNGRADE ATTACK VECTOR? Encryption is configured to be OPTIONAL, so maybe allowable?
                                            // possibly: expectedOutcome.set(ENCRYPTED_WITH_DIALBACK), // do not fail TLS with invalid client cert, as it's usable for encryption even if it's not used for authentication. DOWNGRADE ATTACK VECTOR?
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS. Even if Receiving Entity would negotiate TLS for encryption, it can't use Initiating Entity's invalid cert for Authentication. As Dialback is also not available, authentication cannot occur.");
                                        }
                                        break;
                                    case VALID:
                                        expectedOutcome.set(ENCRYPTED_WITH_SASLEXTERNAL_AUTH, "Initiating Entity can establish encryption and authenticate using TLS.");
                                        break;
                                }
                                break;
                        }
                        break;
                }
                break;
        }

        return expectedOutcome;
    }


}
