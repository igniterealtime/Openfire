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
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify if an outbound server-to-server socket connection can be created (and where applicable:
 * encrypted and authenticated), verifying the implementation of {@link LocalOutgoingServerSession#createOutgoingSession(DomainPair, int)}
 *
 * This implementation uses instances of {@link RemoteServerDummy} to represent the remote server to which a connection
 * is being made.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@RunWith(MockitoJUnitRunner.class)
public class LocalOutgoingServerSessionTest
{
    private RemoteServerDummy remoteServerDummy;

    /**
     * Prepares the local server for operation. This mostly involves preparing the test fixture by mocking parts of the
     * API that {@link LocalOutgoingServerSession#createOutgoingSession(DomainPair, int)} uses when establishing a
     * connection.
     */
    @BeforeClass
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        XMPPServer.setInstance(xmppServer);

        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));

        // Use a temporary file to hold the identity store that is used by the tests.
        final CertificateStoreManager certificateStoreManager = mock(CertificateStoreManager.class, withSettings().lenient());
        final File tmpIdentityStoreFile = new File(tmpDir, "unittest-identitystore-" + System.currentTimeMillis() + ".jks");
        tmpIdentityStoreFile.deleteOnExit();
        final CertificateStoreConfiguration identityStoreConfig = new CertificateStoreConfiguration("jks", tmpIdentityStoreFile, "secret".toCharArray(), tmpDir);
        final IdentityStore identityStore = new IdentityStore(identityStoreConfig, true);
        identityStore.ensureDomainCertificate();
        doReturn(identityStore).when(certificateStoreManager).getIdentityStore(any());

        // Use a temporary file to hold the trust store that is used by the tests.
        final File tmpTrustStoreFile = new File(tmpDir, "unittest-truststore-" + System.currentTimeMillis() + ".jks");
        tmpTrustStoreFile.deleteOnExit();
        final CertificateStoreConfiguration trustStoreConfig = new CertificateStoreConfiguration("jks", tmpTrustStoreFile, "secret".toCharArray(), tmpDir);
        final TrustStore trustStore = new TrustStore(trustStoreConfig, true);
        doReturn(trustStore).when(certificateStoreManager).getTrustStore(any());

        // Mock the connection configuration.
        doReturn(certificateStoreManager).when(xmppServer).getCertificateStoreManager();

        final ConnectionManagerImpl connectionManager = Fixtures.mockConnectionManager();
        final ConnectionListener connectionListener = Fixtures.mockConnectionListener();
        doAnswer(new ConnectionConfigurationAnswer(identityStoreConfig, trustStoreConfig)).when(connectionListener).generateConnectionConfiguration();
        doReturn(Set.of(connectionListener)).when(connectionManager).getListeners(any(ConnectionType.class));
        doReturn(connectionListener).when(connectionManager).getListener(any(ConnectionType.class), anyBoolean());
        doReturn(connectionManager).when(xmppServer).getConnectionManager();
    }

    /**
     * Dynamically generate a ConnectionConfiguration answer, as used by the Mock ConnectionListener.
     *
     * A dynamic answer is needed, as the value of the ConnectionSettings.Server.TLS_POLICY property needs to be
     * evaluated at run-time (this value is changed in the setup of many of the unit tests in this file).
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
            return new ConnectionConfiguration(ConnectionType.SOCKET_S2S, true, 10, -1, Connection.ClientAuth.wanted, null, 9999, tlsPolicy, identityStoreConfig, trustStoreConfig, true, true, protocols, suites, Connection.CompressionPolicy.optional);
        }
    }

    @Before
    public void setUp() throws Exception
    {
        remoteServerDummy = new RemoteServerDummy();
        remoteServerDummy.open();

        Fixtures.clearExistingProperties();
        DNSUtil.setDnsOverride(Map.of(RemoteServerDummy.XMPP_DOMAIN, new DNSUtil.HostAddress("localhost", remoteServerDummy.getPort(), false)));
    }

    @After
    public void tearDown() throws Exception
    {
        DNSUtil.setDnsOverride(null);

        if (remoteServerDummy != null) {
            remoteServerDummy.close();
            remoteServerDummy = null;
        }

        Fixtures.clearExistingProperties();
    }

    /**
     * When:
     * - the remote server identifies itself using a certificate chain that is valid and uses a trusted CA.
     * - the local server allows for Dialback to be used (this should not matter, as it should not be used).
     * 
     * Verify that the local server can set up an outbound connection to a remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the SASL EXTERNAL mechanism.
     */
    @Test
    public void testOutbound_PeerUsesSignedCert_allowingDialback() throws Exception
    {
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
            remoteServerDummy.preparePKIX();
            final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
            final X509Certificate caCert = chain[chain.length-1];
            trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results.
            assertNotNull(result);
            assertFalse(result.isClosed());
            assertTrue(result.isEncrypted());
            assertTrue(result.isAuthenticated());
            assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, result.getAuthenticationMethod());
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
        }
    }

    /**
     * A variant of {@link #testOutbound_PeerUsesSignedCert_allowingDialback()} in which the local
     * server does not allow Dialback to be used (instead of allowing for it).
     *
     * This should not affect the outcome of the test, as Dialback should not be used.
     *
     * @see #testOutbound_PeerUsesSignedCert_allowingDialback()
     */
    @Test
    public void testOutbound_PeerUsesSignedCert_disallowingDialback() throws Exception
    {
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
            remoteServerDummy.preparePKIX();
            final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
            final X509Certificate caCert = chain[chain.length-1];
            trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results.
            assertNotNull(result);
            assertFalse(result.isClosed());
            assertTrue(result.isEncrypted());
            assertTrue(result.isAuthenticated());
            assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, result.getAuthenticationMethod());
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
        }
    }

    /**
     * When:
     * - the remote server identifies itself using a certificate chain that has an expired end-entity certificate.
     * - the local server allows for Dialback to be used.
     *
     * Verify that the local server can set up an outbound connection to the remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the Dialback mechanism.
     *
     * Per RFC 6120 section 13.7.2, a session MUST be terminated when the server is presented with a certificate to
     * determine if it is a 'trusted certificate' for encryption and/or authentication, that fails to validate. However,
     * RFC 7590 Section 3.4 states: "In particular for XMPP server-to-server interactions, it can be reasonable for XMPP
     * server implementations to accept encrypted but unauthenticated connections when Server Dialback keys [XEP-0220]
     * are used." In short: if Dialback is allowed, unauthenticated TLS encryption is better than no encryption.
     *
     * In contact of this test, using an invalid certificate chain should cause TLS encryption to succeed, but TLS
     * authentication (SASL EXTERNAL) to fail. As Dialback is available, this should be used for authentication,
     * resulting in an outbound connection that is encrypted (using TLS) and authenticated (using Dialback).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSignedCertWithExpiredEndEntity_allowingDialback() throws Exception
    {
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
            remoteServerDummy.setUseExpiredEndEntityCertificate(true);
            remoteServerDummy.preparePKIX();
            final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
            final X509Certificate caCert = chain[chain.length-1];
            trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results.
            assertNotNull(result);
            assertFalse(result.isClosed());
            assertTrue(result.isEncrypted());
            assertTrue(result.isAuthenticated());
            assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
        }
    }

    /**
     * When:
     * - the remote server identifies itself using a certificate chain that has an expired end-entity certificate.
     * - the local server does not allow Dialback to be used.
     *
     * Verify that the local server cannot set up an outbound connection to the remote server.
     *
     * As the certificate chain of the peer that we're connecting to is not valid, it cannot be used to authenticate
     * the connection using SASL EXTERNAL. As Dialback is disabled, no authentication mechanism are available. This
     * should cause the outbound connection attempt to fail.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSignedCertWithExpiredEndEntity_disallowingDialback() throws Exception
    {
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
            remoteServerDummy.setUseExpiredEndEntityCertificate(true);
            remoteServerDummy.preparePKIX();
            final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
            final X509Certificate caCert = chain[chain.length-1];
            trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results.
            assertNull(result);
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
        }
    }

    /**
     * When:
     * - the remote server identifies itself using a certificate chain that has an expired intermediate certificate.
     * - the local server does allow Dialback to be used.
     *
     * Verify that the local server can set up an outbound connection to the remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the Dialback mechanism.
     *
     * Per RFC 6120 section 13.7.2, a session MUST be terminated when the server is presented with a certificate to
     * determine if it is a 'trusted certificate' for encryption and/or authentication, that fails to validate. However,
     * RFC 7590 Section 3.4 states: "In particular for XMPP server-to-server interactions, it can be reasonable for XMPP
     * server implementations to accept encrypted but unauthenticated connections when Server Dialback keys [XEP-0220]
     * are used." In short: if Dialback is allowed, unauthenticated TLS encryption is better than no encryption.
     *
     * In contact of this test, using an invalid certificate chain should cause TLS encryption to succeed, but TLS
     * authentication (SASL EXTERNAL) to fail. As Dialback is available, this should be used for authentication,
     * resulting in an outbound connection that is encrypted (using TLS) and authenticated (using Dialback).
     *
     * @see #testOutbound_PeerUsesSignedCertWithExpiredEndEntity_allowingDialback()
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSignedCertWithExpiredIntermediate_allowingDialback() throws Exception
    {
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
            remoteServerDummy.setUseExpiredIntermediateCertificate(true);
            remoteServerDummy.preparePKIX();
            final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
            final X509Certificate caCert = chain[chain.length-1];
            trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results.
            assertNotNull(result);
            assertFalse(result.isClosed());
            assertTrue(result.isEncrypted());
            assertTrue(result.isAuthenticated());
            assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
        }
    }

    /**
     * When:
     * - the remote server identifies itself using a certificate chain that has an expired intermediate certificate.
     * - the local server does not allow Dialback to be used.
     *
     * Verify that the local server cannot set up an outbound connection to the remote server.
     *
     * As the certificate chain of the peer that we're connecting to is not valid, it cannot be used to authenticate
     * the connection using SASL EXTERNAL. As Dialback is disabled, no authentication mechanism are available. This
     * should cause the outbound connection attempt to fail.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSignedCertWithExpiredIntermediate_disallowingDialback() throws Exception
    {
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
            remoteServerDummy.setUseExpiredIntermediateCertificate(true);
            remoteServerDummy.preparePKIX();
            final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
            final X509Certificate caCert = chain[chain.length-1];
            trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results.
            assertNull(result);
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
        }
    }

    /**
     * When:
     * - the remote server identifies itself using a certificate chain that has an expired root certificate.
     * - the local server does allow Dialback to be used.
     *
     * Verify that the local server can set up an outbound connection to the remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the Dialback mechanism.
     *
     * Per RFC 6120 section 13.7.2, a session MUST be terminated when the server is presented with a certificate to
     * determine if it is a 'trusted certificate' for encryption and/or authentication, that fails to validate. However,
     * RFC 7590 Section 3.4 states: "In particular for XMPP server-to-server interactions, it can be reasonable for XMPP
     * server implementations to accept encrypted but unauthenticated connections when Server Dialback keys [XEP-0220]
     * are used." In short: if Dialback is allowed, unauthenticated TLS encryption is better than no encryption.
     *
     * In contact of this test, using an invalid certificate chain should cause TLS encryption to succeed, but TLS
     * authentication (SASL EXTERNAL) to fail. As Dialback is available, this should be used for authentication,
     * resulting in an outbound connection that is encrypted (using TLS) and authenticated (using Dialback).
     *
     * @see #testOutbound_PeerUsesSignedCertWithExpiredEndEntity_allowingDialback()
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSignedCertWithExpiredRoot_allowingDialback() throws Exception
    {
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
            remoteServerDummy.setUseExpiredRootCertificate(true);
            remoteServerDummy.preparePKIX();
            final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
            final X509Certificate caCert = chain[chain.length-1];
            trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results.
            assertNotNull(result);
            assertFalse(result.isClosed());
            assertTrue(result.isEncrypted());
            assertTrue(result.isAuthenticated());
            assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
        }
    }

    /**
     * When:
     * - the remote server identifies itself using a certificate chain that has an expired root certificate.
     * - the local server does not allow Dialback to be used.
     *
     * Verify that the local server cannot set up an outbound connection to the remote server.
     *
     * As the certificate chain of the peer that we're connecting to is not valid, it cannot be used to authenticate
     * the connection using SASL EXTERNAL. As Dialback is disabled, no authentication mechanism are available. This
     * should cause the outbound connection attempt to fail.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSignedCertWithExpiredRoot_disallowingDialback() throws Exception
    {
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
            remoteServerDummy.setUseExpiredRootCertificate(true);
            remoteServerDummy.preparePKIX();
            final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
            final X509Certificate caCert = chain[chain.length-1];
            trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results.
            assertNull(result);
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
        }
    }

    /**
     * When:
     * - the remote server identifies itself using a certificate chain that identifies an XMPP domain different from what the remote server uses in its 'from' stream attribute.
     * - the local server allows for Dialback to be used.
     *
     * Verify that the local server can set up an outbound connection to the remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the Dialback mechanism.
     *
     * Per RFC 6120 section 13.7.2, a session MUST be terminated when the server is presented with a certificate to
     * determine if it is a 'trusted certificate' for encryption and/or authentication, that fails to validate. However,
     * RFC 7590 Section 3.4 states: "In particular for XMPP server-to-server interactions, it can be reasonable for XMPP
     * server implementations to accept encrypted but unauthenticated connections when Server Dialback keys [XEP-0220]
     * are used." In short: if Dialback is allowed, unauthenticated TLS encryption is better than no encryption.
     *
     * In contact of this test, using an invalid certificate chain should cause TLS encryption to succeed, but TLS
     * authentication (SASL EXTERNAL) to fail. As Dialback is available, this should be used for authentication,
     * resulting in an outbound connection that is encrypted (using TLS) and authenticated (using Dialback).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2590">OF-2590</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSignedCertWithWrongDomain_allowingDialback() throws Exception
    {
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
            remoteServerDummy.setUseWrongNameInCertificate(true);
            remoteServerDummy.preparePKIX();
            final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
            final X509Certificate caCert = chain[chain.length-1];
            trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results.
            assertNotNull(result);
            assertFalse(result.isClosed());
            assertTrue(result.isEncrypted());
            assertTrue(result.isAuthenticated());
            assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
        }
    }

    /**
     * When:
     * - the remote server identifies itself using a certificate chain that identifies an XMPP domain different from what the remote server uses in its 'from' stream attribute.
     * - the local server does not allow for Dialback to be used.
     *
     * Verify that the local server cannot set up an outbound connection to the remote server.
     *
     * As the certificate chain of the peer that we're connecting to is not valid, it cannot be used to authenticate
     * the connection using SASL EXTERNAL. As Dialback is disabled, no authentication mechanism are available. This
     * should cause the outbound connection attempt to fail.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2590">OF-2590</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSignedCertWithWrongDomain_disallowingDialback() throws Exception
    {
        final TrustStore trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore(ConnectionType.SOCKET_S2S);
        try {
            // Setup test fixture.
            JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, "true");
            JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
            remoteServerDummy.setUseWrongNameInCertificate(true);
            remoteServerDummy.preparePKIX();
            final X509Certificate[] chain = remoteServerDummy.getGeneratedPKIX().getCertificateChain();
            final X509Certificate caCert = chain[chain.length-1];
            trustStore.installCertificate("unit-test", KeystoreTestUtils.toPemFormat(caCert));

            final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
            final int port = remoteServerDummy.getPort();

            // Execute system under test.
            final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

            // Verify results.
            assertNull(result);
        } finally {
            // Teardown test fixture.
            trustStore.delete("unit-test");
        }
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate.
     * - the local server is configured to allow self-signed certificates.
     * - the local server allows for Dialback to be used (this should not matter, as it should not be used).
     *
     * Verify that the local server can set up an outbound connection to the remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the SASL EXTERNAL mechanism.
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCert_allowingSelfSigned_allowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "true");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNotNull(result);
        assertFalse(result.isClosed());
        assertTrue(result.isEncrypted());
        assertTrue(result.isAuthenticated());
        assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, result.getAuthenticationMethod());
    }

    /**
     * A variant of {@link #testOutbound_PeerUsesSignedCert_allowingDialback()} in which the local
     * server does not allow Dialback to be used (instead of allowing for it).
     *
     * This should not affect the outcome of the test, as Dialback should not be used.
     *
     * @see #testOutbound_PeerUsesSignedCert_allowingDialback()
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCert_allowingSelfSigned_disallowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "true");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNotNull(result);
        assertFalse(result.isClosed());
        assertTrue(result.isEncrypted());
        assertTrue(result.isAuthenticated());
        assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, result.getAuthenticationMethod());
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate.
     * - the local server is configured to not allow self-signed certificates.
     * - the local server allows for Dialback to be used.
     *
     * Verify that the local server can set up an outbound connection to the remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the Dialback mechanism.
     *
     * Per RFC 6120 section 13.7.2, a session MUST be terminated when the server is presented with a certificate to
     * determine if it is a 'trusted certificate' for encryption and/or authentication, that fails to validate. However,
     * RFC 7590 Section 3.4 states: "In particular for XMPP server-to-server interactions, it can be reasonable for XMPP
     * server implementations to accept encrypted but unauthenticated connections when Server Dialback keys [XEP-0220]
     * are used." In short: if Dialback is allowed, unauthenticated TLS encryption is better than no encryption.
     *
     * In contact of this test, using an invalid certificate chain should cause TLS encryption to succeed, but TLS
     * authentication (SASL EXTERNAL) to fail. As Dialback is available, this should be used for authentication,
     * resulting in an outbound connection that is encrypted (using TLS) and authenticated (using Dialback).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCert_disallowingSelfSigned_allowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "false");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNotNull(result);
        assertFalse(result.isClosed());
        assertTrue(result.isEncrypted());
        assertTrue(result.isAuthenticated());
        assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate.
     * - the local server is configured to not allow self-signed certificates.
     * - the local server does not allow for Dialback to be used.
     *
     * Verify that the local server can not set up an outbound connection to the remote server.
     *
     * As the certificate chain of the peer that we're connecting to is not valid, it cannot be used to authenticate
     * the connection using SASL EXTERNAL. As Dialback is disabled, no authentication mechanism are available. This
     * should cause the outbound connection attempt to fail.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCert_disallowingSelfSigned_disallowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "false");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNull(result);
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate that is expired.
     * - the local server is configured to allow self-signed certificates.
     * - the local server allows for Dialback to be used.
     *
     * Verify that the local server can set up an outbound connection to the remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the Dialback mechanism.
     *
     * Per RFC 6120 section 13.7.2, a session MUST be terminated when the server is presented with a certificate to
     * determine if it is a 'trusted certificate' for encryption and/or authentication, that fails to validate. However,
     * RFC 7590 Section 3.4 states: "In particular for XMPP server-to-server interactions, it can be reasonable for XMPP
     * server implementations to accept encrypted but unauthenticated connections when Server Dialback keys [XEP-0220]
     * are used." In short: if Dialback is allowed, unauthenticated TLS encryption is better than no encryption.
     *
     * In contact of this test, using an invalid certificate chain should cause TLS encryption to succeed, but TLS
     * authentication (SASL EXTERNAL) to fail. As Dialback is available, this should be used for authentication,
     * resulting in an outbound connection that is encrypted (using TLS) and authenticated (using Dialback).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCertWithExpiredEndEntity_allowingSelfSigned_allowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "true");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.setUseExpiredEndEntityCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNotNull(result);
        assertFalse(result.isClosed());
        assertTrue(result.isEncrypted());
        assertTrue(result.isAuthenticated());
        assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate that is expired.
     * - the local server is configured to allow self-signed certificates.
     * - the local server does not allow for Dialback to be used.
     *
     * Verify that the local server can not set up an outbound connection to the remote server.
     *
     * As the certificate chain of the peer that we're connecting to is not valid, it cannot be used to authenticate
     * the connection using SASL EXTERNAL. As Dialback is disabled, no authentication mechanism are available. This
     * should cause the outbound connection attempt to fail.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCertWithExpiredEndEntity_allowingSelfSigned_disallowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "true");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.setUseExpiredEndEntityCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNull(result);
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate that is expired.
     * - the local server is configured to not allow self-signed certificates.
     * - the local server allows for Dialback to be used.
     *
     * Verify that the local server can set up an outbound connection to the remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the Dialback mechanism.
     *
     * Per RFC 6120 section 13.7.2, a session MUST be terminated when the server is presented with a certificate to
     * determine if it is a 'trusted certificate' for encryption and/or authentication, that fails to validate. However,
     * RFC 7590 Section 3.4 states: "In particular for XMPP server-to-server interactions, it can be reasonable for XMPP
     * server implementations to accept encrypted but unauthenticated connections when Server Dialback keys [XEP-0220]
     * are used." In short: if Dialback is allowed, unauthenticated TLS encryption is better than no encryption.
     *
     * In contact of this test, using an invalid certificate chain should cause TLS encryption to succeed, but TLS
     * authentication (SASL EXTERNAL) to fail. As Dialback is available, this should be used for authentication,
     * resulting in an outbound connection that is encrypted (using TLS) and authenticated (using Dialback).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCertWithExpiredEndEntity_disallowingSelfSigned_allowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "false");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.setUseExpiredEndEntityCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNotNull(result);
        assertFalse(result.isClosed());
        assertTrue(result.isEncrypted());
        assertTrue(result.isAuthenticated());
        assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate that is expired.
     * - the local server is configured to not allow self-signed certificates.
     * - the local server does not allow for Dialback to be used.
     *
     * Verify that the local server can not set up an outbound connection to the remote server.
     *
     * As the certificate chain of the peer that we're connecting to is not valid, it cannot be used to authenticate
     * the connection using SASL EXTERNAL. As Dialback is disabled, no authentication mechanism are available. This
     * should cause the outbound connection attempt to fail.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCertWithExpiredEndEntity_disallowingSelfSigned_disallowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "false");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.setUseExpiredEndEntityCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNull(result);
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate that identifies an XMPP domain different from what the remote server uses in its 'from' stream attribute.
     * - the local server is configured to allow self-signed certificates.
     * - the local server allows for Dialback to be used.
     *
     * Verify that the local server can set up an outbound connection to the remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the Dialback mechanism.
     *
     * Per RFC 6120 section 13.7.2, a session MUST be terminated when the server is presented with a certificate to
     * determine if it is a 'trusted certificate' for encryption and/or authentication, that fails to validate. However,
     * RFC 7590 Section 3.4 states: "In particular for XMPP server-to-server interactions, it can be reasonable for XMPP
     * server implementations to accept encrypted but unauthenticated connections when Server Dialback keys [XEP-0220]
     * are used." In short: if Dialback is allowed, unauthenticated TLS encryption is better than no encryption.
     *
     * In contact of this test, using an invalid certificate chain should cause TLS encryption to succeed, but TLS
     * authentication (SASL EXTERNAL) to fail. As Dialback is available, this should be used for authentication,
     * resulting in an outbound connection that is encrypted (using TLS) and authenticated (using Dialback).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCertWithWrongDomain_allowingSelfSigned_allowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "true");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.setUseWrongNameInCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNotNull(result);
        assertFalse(result.isClosed());
        assertTrue(result.isEncrypted());
        assertTrue(result.isAuthenticated());
        assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate that identifies an XMPP domain different from what the remote server uses in its 'from' stream attribute.
     * - the local server is configured to allow self-signed certificates.
     * - the local server does not allow for Dialback to be used.
     *
     * Verify that the local server can not set up an outbound connection to the remote server.
     *
     * As the certificate chain of the peer that we're connecting to is not valid, it cannot be used to authenticate
     * the connection using SASL EXTERNAL. As Dialback is disabled, no authentication mechanism are available. This
     * should cause the outbound connection attempt to fail.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCertWithWrongDomain_allowingSelfSigned_disallowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "true");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.setUseWrongNameInCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNull(result);
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate that identifies an XMPP domain different from what the remote server uses in its 'from' stream attribute.
     * - the local server is configured to not allow self-signed certificates.
     * - the local server allows for Dialback to be used.
     *
     * Verify that the local server can set up an outbound connection to the remote server that:
     * - is encrypted.
     * - is authenticated (by the remote peer) using the Dialback mechanism.
     *
     * Per RFC 6120 section 13.7.2, a session MUST be terminated when the server is presented with a certificate to
     * determine if it is a 'trusted certificate' for encryption and/or authentication, that fails to validate. However,
     * RFC 7590 Section 3.4 states: "In particular for XMPP server-to-server interactions, it can be reasonable for XMPP
     * server implementations to accept encrypted but unauthenticated connections when Server Dialback keys [XEP-0220]
     * are used." In short: if Dialback is allowed, unauthenticated TLS encryption is better than no encryption.
     *
     * In contact of this test, using an invalid certificate chain should cause TLS encryption to succeed, but TLS
     * authentication (SASL EXTERNAL) to fail. As Dialback is available, this should be used for authentication,
     * resulting in an outbound connection that is encrypted (using TLS) and authenticated (using Dialback).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2590">OF-2590</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCertWithWrongDomain_disallowingSelfSigned_allowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "false");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.setUseWrongNameInCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNotNull(result);
        assertFalse(result.isClosed());
        assertTrue(result.isEncrypted());
        assertTrue(result.isAuthenticated());
        assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
    }

    /**
     * When:
     * - the remote server identifies itself using a self-signed certificate that identifies an XMPP domain different from what the remote server uses in its 'from' stream attribute.
     * - the local server is configured to not allow self-signed certificates.
     * - the local server does not allow for Dialback to be used.
     *
     * Verify that the local server can not set up an outbound connection to the remote server.
     *
     * As the certificate chain of the peer that we're connecting to is not valid, it cannot be used to authenticate
     * the connection using SASL EXTERNAL. As Dialback is disabled, no authentication mechanism are available. This
     * should cause the outbound connection attempt to fail.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2590">OF-2590</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591</a>
     */
    @Test
    public void testOutbound_PeerUsesSelfSignedCertWithWrongDomain_disallowingSelfSigned_disallowingDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.required.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, "false");
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");
        remoteServerDummy.setUseSelfSignedCertificate(true);
        remoteServerDummy.setUseWrongNameInCertificate(true);
        remoteServerDummy.preparePKIX();

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNull(result);
    }

    /**
     * Verifies that an authenticated but unencrypted connection can be established when:
     *
     * <ul>
     * <li>the local server is configured to not do TLS
     * <li>Dialback authentication is enabled
     * </ul>
     *
     * Verifying that the connection is NOT encrypted, but authenticated using Dialback.
     */
    @Test
    public void testOutbound_withoutEncryption_withDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.disabled.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNotNull(result);
        assertFalse(result.isClosed());
        assertFalse(result.isEncrypted());
        assertTrue(result.isAuthenticated());
        assertEquals(ServerSession.AuthenticationMethod.DIALBACK, result.getAuthenticationMethod());
    }

    /**
     * Verifies that connection can NOT be established when:
     *
     * <ul>
     * <li>the local server is configured to not do TLS
     * <li>Dialback authentication is disabled
     * </ul>
     *
     * Without TLS and Dialback, no authentication of the connection can occur.
     */
    @Test
    public void testOutbound_withoutEncryption_withoutDialback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_POLICY, Connection.TLSPolicy.disabled.toString());
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "false");

        final DomainPair domainPair = new DomainPair(Fixtures.XMPP_DOMAIN, RemoteServerDummy.XMPP_DOMAIN);
        final int port = remoteServerDummy.getPort();

        // Execute system under test.
        final LocalOutgoingServerSession result = LocalOutgoingServerSession.createOutgoingSession(domainPair, port);

        // Verify results.
        assertNull(result);
    }

    // TODO: add tests for direct-TLS
    // TODO: have tests that use Dialback after TLS (encryption and/or authentication?) was attempted, but failed.
    // TODO: Openfire does something that I can't quite fathom yet: when Dialback is disabled, but Self-Signed certs are accepted, Dialback is still acceptable? Test for this!
    // TODO: add tests for piggybacking a new outbound connection over a pre-exising connection maybe?

}
