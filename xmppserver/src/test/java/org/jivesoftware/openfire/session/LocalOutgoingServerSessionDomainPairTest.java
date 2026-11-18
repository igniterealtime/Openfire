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

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies that an outgoing server session is not used to authenticate a domain pair other than those it was
 * authenticated for (dialback multiplexing, XEP-0220 section 2.6), and that a stanza for such a domain pair is returned
 * to its sender rather than sent on the session (OF-3470).
 */
public class LocalOutgoingServerSessionDomainPairTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final String OTHER_REMOTE_DOMAIN = "other.example.org";

    private Connection connection;

    private PacketRouter packetRouter;

    private SessionManager sessionManager;

    private LocalOutgoingServerSession session;

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
        packetRouter = mock(PacketRouter.class);
        doReturn(packetRouter).when(xmppServer).getPacketRouter();
        sessionManager = xmppServer.getSessionManager();
        XMPPServer.setInstance(xmppServer);

        connection = mock(Connection.class, withSettings().lenient());
        session = new LocalOutgoingServerSession(Fixtures.XMPP_DOMAIN, connection, new BasicStreamIDFactory().createStreamID());
        session.setAuthenticationMethod(ServerSession.AuthenticationMethod.DIALBACK);
        session.addOutgoingDomainPair(new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN));
    }

    /**
     * Asserts that a new connection is established for a domain pair when an outgoing session exists for another domain
     * of the same remote server, rather than that session being used to authenticate the domain pair.
     */
    @SuppressWarnings("removal")
    @Test
    public void testAuthenticateDomainDoesNotUseSessionOfOtherDomain()
    {
        // Setup test fixture.
        final DomainPair requested = new DomainPair(Fixtures.XMPP_DOMAIN, OTHER_REMOTE_DOMAIN);
        final IncomingServerSession incoming = mock(IncomingServerSession.class, withSettings().lenient());
        doReturn(List.of(REMOTE_DOMAIN, OTHER_REMOTE_DOMAIN)).when(incoming).getValidatedDomains();
        doReturn(List.of(incoming)).when(sessionManager).getIncomingServerSessions(OTHER_REMOTE_DOMAIN);
        final OutgoingServerSession sibling = mock(OutgoingServerSession.class, withSettings().lenient());
        doReturn(true).when(sibling).isUsingServerDialback();
        doReturn(sibling).when(sessionManager).getOutgoingServerSession(new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN));
        final DomainAuthResult result;

        // Execute system under test.
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            // Stops the attempt to establish a new connection before it reaches the network.
            remoteServers.when(() -> RemoteServerManager.getPortForServer(OTHER_REMOTE_DOMAIN)).thenThrow(new IllegalStateException("No network in this test."));

            result = LocalOutgoingServerSession.authenticateDomain(requested);

            // Verify result.
            remoteServers.verify(() -> RemoteServerManager.getPortForServer(OTHER_REMOTE_DOMAIN), times(1).description("A new connection must be established for the domain pair."));
        }
        assertFalse(result.isSuccess(), "Authentication must fail when no new connection can be established.");
        verify(sibling, never().description("The session of the other domain must not be used to authenticate the domain pair.")).authenticateSubdomain(any());
    }

    /**
     * Asserts that a stanza for a domain pair that the session is authenticated for can be delivered.
     */
    @Test
    public void testCanDeliverStanzaForAuthenticatedDomainPair()
    {
        // Setup test fixture.
        final Message stanza = new Message();
        stanza.setFrom("user@" + Fixtures.XMPP_DOMAIN);
        stanza.setTo("user@" + REMOTE_DOMAIN);

        // Execute system under test.
        final boolean result = session.canDeliver(stanza);

        // Verify result.
        assertTrue(result, "A stanza for an authenticated domain pair must be deliverable.");
    }

    /**
     * Asserts that a stanza for a domain pair that the session is not authenticated for is not delivered on the
     * session, and that the session does not try to authenticate that domain pair.
     */
    @Test
    public void testCannotDeliverStanzaForOtherDomainPair() throws Exception
    {
        // Setup test fixture.
        final Message stanza = new Message();
        stanza.setID("test-stanza");
        stanza.setFrom("user@" + Fixtures.XMPP_DOMAIN);
        stanza.setTo("user@" + OTHER_REMOTE_DOMAIN);

        // Execute system under test.
        final boolean result = session.canDeliver(stanza);

        // Verify result.
        assertFalse(result, "A stanza for a domain pair that the session is not authenticated for must not be deliverable.");
        assertFalse(session.checkOutgoingDomainPair(new DomainPair(Fixtures.XMPP_DOMAIN, OTHER_REMOTE_DOMAIN)), "The domain pair of the stanza must not be authenticated on the session.");
        verify(connection, never().description("No dialback request may be sent for the domain pair of the stanza.")).deliverRawText(anyString());
        final ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(packetRouter, timeout(5000).description("An error must be returned to the sender of the stanza.")).route(captor.capture());
        final Message error = captor.getValue();
        assertEquals("test-stanza", error.getID(), "The error must refer to the stanza that was not delivered.");
        assertEquals(stanza.getFrom(), error.getTo(), "The error must be addressed to the sender of the stanza.");
        assertEquals(PacketError.Condition.remote_server_not_found, error.getError().getCondition(), "The error must carry the 'remote-server-not-found' condition.");
    }
}
