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
package org.jivesoftware.openfire.session;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.security.cert.Certificate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies that {@link LocalIncomingServerSession#validateSubsequentDomain(Element)} reports success to the peer from
 * the normalised form of the validated domain (OF-2643).
 *
 * Server Dialback validates the domain through {@link org.jivesoftware.openfire.server.ServerDialback}, which returns
 * the domain as the local XMPP service knows it. This test checks that the {@code <db:result type='valid'>} reply uses
 * that returned value, rather than the form the peer wrote. The remote-server permission check and the certificate
 * check are replaced with stubs, as they read the database and the peer's certificates, which are not what this test
 * is about.
 *
 * @author Matthew Vivian
 */
public class LocalIncomingServerSessionSubsequentDomainTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private Connection connection;

    private LocalIncomingServerSession session;

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
        final SessionManager sessionManager = xmppServer.getSessionManager();
        doReturn(List.of()).when(sessionManager).getIncomingServerSessions(REMOTE_DOMAIN);
        XMPPServer.setInstance(xmppServer);

        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();
        connection = mock(Connection.class, withSettings().lenient());
        doReturn(configuration).when(connection).getConfiguration();
        doReturn(new Certificate[0]).when(connection).getPeerCertificates();

        session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, new BasicStreamIDFactory().createStreamID(), REMOTE_DOMAIN);
    }

    /**
     * Asserts that, when a peer addresses a subsequent domain in a different case from the XMPP domain, the reply that
     * reports success carries the normalised domain in its 'from' attribute, rather than the form the peer wrote.
     */
    @Test
    public void testSubsequentDomainReplyReportsNormalisedDomain()
    {
        // Setup test fixture.
        final Element dbResult = DocumentHelper.createElement("result");
        dbResult.addAttribute("from", REMOTE_DOMAIN);
        dbResult.addAttribute("to", Fixtures.XMPP_DOMAIN.toUpperCase(Locale.ROOT));
        dbResult.setText("key");

        // Execute system under test.
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class);
             final MockedStatic<SASLAuthentication> sasl = mockStatic(SASLAuthentication.class))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            sasl.when(() -> SASLAuthentication.verifyCertificates(any(), anyString(), anyBoolean())).thenReturn(true);

            final boolean result = session.validateSubsequentDomain(dbResult);
            assertTrue(result, "The subsequent domain must be validated successfully.");
        }

        // Verify result.
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("A reply must have been delivered to the peer.")).deliverRawText(captor.capture());
        final String delivered = String.join("", captor.getAllValues());
        assertTrue(delivered.contains("from=\"" + Fixtures.XMPP_DOMAIN + "\""), "The reply must report success from the normalised domain.");
        assertFalse(delivered.contains("from=\"" + Fixtures.XMPP_DOMAIN.toUpperCase(Locale.ROOT) + "\""), "The reply must not report success from the domain in the case the peer wrote.");
    }
}
