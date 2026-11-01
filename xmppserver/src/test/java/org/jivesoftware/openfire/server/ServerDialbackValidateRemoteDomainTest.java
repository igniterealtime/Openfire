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
package org.jivesoftware.openfire.server;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.IncomingServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.cert.Certificate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies how {@link ServerDialback#validateRemoteDomain(Element, org.jivesoftware.openfire.StreamID)} treats the
 * domain that a remote server asks to be authenticated for, once that domain is accepted as serviced (OF-2643).
 *
 * The remote-server permission check and the certificate check are replaced with stubs, as they read the database and
 * the peer's certificates, which are not what these tests are about.
 *
 * @author Matthew Vivian
 */
public class ServerDialbackValidateRemoteDomainTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private SessionManager sessionManager;

    private ServerDialback dialback;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    public void setUp()
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        doReturn(Fixtures.mockRoutingTable()).when(xmppServer).getRoutingTable();
        sessionManager = xmppServer.getSessionManager();
        XMPPServer.setInstance(xmppServer);

        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();
        final Connection connection = mock(Connection.class, withSettings().lenient());
        doReturn(configuration).when(connection).getConfiguration();
        doReturn(new Certificate[0]).when(connection).getPeerCertificates();

        dialback = new ServerDialback(connection, new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN));
    }

    /**
     * Asserts that a request to authenticate for the XMPP domain written in a different case is recognised as a
     * duplicate of an existing session for the XMPP domain, as domain names are compared after normalisation.
     */
    @Test
    public void testExistingSessionIsDetectedForDomainInDifferentCase()
    {
        // Setup test fixture.
        final IncomingServerSession existing = mock(IncomingServerSession.class, withSettings().lenient());
        doReturn(Fixtures.XMPP_DOMAIN).when(existing).getLocalDomain();
        doReturn(List.of(existing)).when(sessionManager).getIncomingServerSessions(REMOTE_DOMAIN);
        doReturn(false).when(sessionManager).isMultipleServerConnectionsAllowed();

        final Element dbResult = DocumentHelper.createElement("result");
        dbResult.addAttribute("from", REMOTE_DOMAIN);
        dbResult.addAttribute("to", Fixtures.XMPP_DOMAIN.toUpperCase(Locale.ROOT));
        dbResult.setText("key");

        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class);
             final MockedStatic<SASLAuthentication> sasl = mockStatic(SASLAuthentication.class))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            sasl.when(() -> SASLAuthentication.verifyCertificates(any(), anyString(), anyBoolean())).thenReturn(true);

            // Execute system under test & verify result.
            final ServerDialbackErrorException exception = assertThrows(ServerDialbackErrorException.class,
                () -> dialback.validateRemoteDomain(dbResult, new BasicStreamIDFactory().createStreamID()),
                "A second session for the same domain, written in a different case, must be refused.");
            assertEquals("resource-constraint", exception.getError().getCondition().toXMPP(), "The refusal must report that a session for the domain already exists.");
        }
    }

    /**
     * Asserts that, once accepted, the domain that a remote server asks to be authenticated for is returned in
     * normalised form, so that the session is created for, and the result is reported from, the normalised domain.
     */
    @Test
    public void testAcceptedDomainIsNormalised() throws Exception
    {
        // Setup test fixture.
        doReturn(List.of()).when(sessionManager).getIncomingServerSessions(REMOTE_DOMAIN);

        final Element dbResult = DocumentHelper.createElement("result");
        dbResult.addAttribute("from", REMOTE_DOMAIN);
        dbResult.addAttribute("to", Fixtures.XMPP_DOMAIN.toUpperCase(Locale.ROOT));
        dbResult.setText("key");

        final String result;
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class);
             final MockedStatic<SASLAuthentication> sasl = mockStatic(SASLAuthentication.class))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            sasl.when(() -> SASLAuthentication.verifyCertificates(any(), anyString(), anyBoolean())).thenReturn(true);

            // Execute system under test.
            result = dialback.validateRemoteDomain(dbResult, new BasicStreamIDFactory().createStreamID());
        }

        // Verify result.
        assertEquals(Fixtures.XMPP_DOMAIN, result, "The accepted domain must be returned in normalised form.");
    }
}
