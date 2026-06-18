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
package org.jivesoftware.openfire;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Characterization tests for {@link SessionManager#removeSession(ClientSession, JID, boolean, boolean)} in the presence
 * of two sessions for the same full JID (as happens after a reconnect, or under a conflict-limit that tolerates
 * concurrent sessions).
 *
 * This complements {@code SessionManagerCloseListenerRouteOwnershipTest} (which covers the close-listener path, which
 * also drives removeSession, but not in isolation).
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3318">OF-3318: SessionManager teardown ownership</a>
 * @see SessionManagerCloseListenerRouteOwnershipTest
 */
@ExtendWith(MockitoExtension.class)
public class SessionManagerRouteOwnershipTest
{
    private static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "resourcepart-used-for-testing", true);

    private SessionManager sessionManager;
    private RoutingTable routingTable;
    private PacketRouter packetRouter;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        routingTable = mock(RoutingTable.class, withSettings().strictness(Strictness.LENIENT));
        packetRouter = mock(PacketRouter.class, withSettings().strictness(Strictness.LENIENT));

        // Wire the two collaborators SessionManager.initialize(...) pulls from the server, on top of the standard mock
        // server provided by the test fixtures.
        final XMPPServer server = Fixtures.mockXMPPServer();
        doReturn(routingTable).when(server).getRoutingTable();
        doReturn(packetRouter).when(server).getPacketRouter();

        //noinspection deprecation
        XMPPServer.setInstance(server);

        sessionManager = new SessionManager();
        sessionManager.initialize(server);
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
    }

    /**
     * A lenient mock client session that reports the given stream ID, the shared full JID, and an available presence.
     * <p>
     * Note: this is a plain {@link ClientSession} rather than a {@code LocalClientSession}. That is deliberate and
     * harmless here. {@code removeSession} only dereferences {@code localSessionManager} inside a
     * {@code session instanceof LocalClientSession} branch, so a plain {@code ClientSession} mock avoids needing to
     * stub that collaborator while leaving the route- and presence-handling paths under test fully exercised.
     */
    private ClientSession session(final String streamId)
    {
        final ClientSession session = mock(ClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(session.getStreamID()).thenReturn(BasicStreamIDFactory.createStreamID(streamId));
        when(session.getAddress()).thenReturn(FULL_JID);
        when(session.getPresence()).thenReturn(new Presence()); // default type == available
        return session;
    }

    /**
     * The core bug. Two sessions exist for the same full JID; the live session owns the route. When the stale session
     * is torn down, the live session's route must NOT be removed.
     */
    @Test
    public void tearingDownStaleSession_doesNotRemoveLiveRoute()
    {
        // Setup test fixture: live session owns the route; a stale session also exists for the same full JID.
        final ClientSession live = session("livestreamid");
        final ClientSession stale = session("stalestreamid");
        when(routingTable.getClientRoute(FULL_JID)).thenReturn(live);

        // Execute system under test: the stale session's connection closes and it is removed.
        sessionManager.removeSession(stale, FULL_JID, false, false);

        // Verify result: the live session's route must never be removed by the stale teardown.
        verify(routingTable, never()).removeClientRoute(FULL_JID);
    }

    /**
     * Companion assertion on presence: tearing down the stale session must not route an unavailable presence from the
     * full JID (which would mark the live session offline to its subscribers and server-side).
     */
    @Test
    public void tearingDownStaleSession_doesNotRouteUnavailableForLiveSession()
    {
        // Setup test fixture.
        final ClientSession live = session("livestreamid");
        final ClientSession stale = session("stalestreamid");
        when(routingTable.getClientRoute(FULL_JID)).thenReturn(live);

        // Execute system under test.
        sessionManager.removeSession(stale, FULL_JID, false, false);

        // Verify result: no unavailable presence for the shared full JID was routed.
        final ArgumentCaptor<Packet> routed = ArgumentCaptor.forClass(Packet.class);
        verify(packetRouter, atLeast(0)).route(routed.capture());

        final boolean routedUnavailableForJid = routed.getAllValues().stream()
            .filter(p -> p instanceof Presence)
            .map(p -> (Presence) p)
            .anyMatch(p -> Presence.Type.unavailable.equals(p.getType()) && FULL_JID.equals(p.getFrom()));
        assertFalse(routedUnavailableForJid, "Teardown of the stale session must not route an unavailable presence for the live session's full JID.");
    }

    /**
     * Guard against a no-op regression: tearing down the session that ACTUALLY owns the route must still remove that
     * route.
     */
    @Test
    public void tearingDownOwningSession_stillRemovesRoute()
    {
        // Setup test fixture: a single session that owns the route.
        final ClientSession owner = session("ownerstreamid");
        when(routingTable.getClientRoute(FULL_JID)).thenReturn(owner);
        when(routingTable.removeClientRoute(FULL_JID)).thenReturn(true);

        // Execute system under test
        sessionManager.removeSession(owner, FULL_JID, false, false);

        // Verify result: the owner's own route IS removed.
        verify(routingTable).removeClientRoute(FULL_JID);
    }

    /**
     * Sanity: the two sessions are genuinely distinct instances with distinct stream IDs, so the scenario under test is
     * the real two-session overlap and not an artefact of mock reuse.
     */
    @Test
    public void preconditions_sessionsAreDistinct()
    {
        final ClientSession live = session("livestreamid");
        final ClientSession stale = session("stalestreamid");
        assertNotEquals(live, stale, "Sanity check failed: the sessions used for testing are expected to be non-equal instances (but they are equal). This will cause the outcome of other tests in this suite to be unreliable.");
        assertNotEquals(live.getStreamID(), stale.getStreamID(), "Sanity check failed: the stream IDs of the sessions used for testing are expected to be non-equal instances (but they are equal). This will cause the outcome of other tests in this suite to be unreliable.");
    }
}
