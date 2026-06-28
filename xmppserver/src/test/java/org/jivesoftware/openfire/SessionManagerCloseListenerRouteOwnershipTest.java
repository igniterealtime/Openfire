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
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

/**
 * Characterization test for the client {@code ConnectionCloseListener} path in {@link SessionManager}.
 *
 * This complements {@code SessionManagerRouteOwnershipTest} (which drives {@code removeSession} directly). The close
 * listener emits an unavailable presence <em>before</em> {@code removeSession} is reached, gated only on
 * {@code routingTable.hasClientRoute(...)} (a route-existence check, not an ownership check). When a stale session
 * closes after a reconnect while a live session owns the route for the same full JID, that emission could incorrectly
 * mark the live session offline. This test exercises exactly that emission.
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3318">OF-3318: SessionManager teardown ownership</a>
 * @see SessionManagerRouteOwnershipTest
 */
@ExtendWith(MockitoExtension.class)
public class SessionManagerCloseListenerRouteOwnershipTest
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
     * A mock {@link LocalClientSession} configured so that the close-listener reaches the unavailable-presence
     * emission branch: it is not detached, has no stream-management resume, reports an available presence, and
     * carries the shared full JID.
     */
    private LocalClientSession session(final String streamId)
    {
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        doReturn(BasicStreamIDFactory.createStreamID(streamId)).when(session).getStreamID();
        doReturn(FULL_JID).when(session).getAddress();
        doReturn(new Presence()).when(session).getPresence(); // default type == available
        doReturn(false).when(session).isDetached();

        // Stream management must report "no resume" so the listener proceeds to teardown rather than detaching.
        final StreamManager streamManager = mock(StreamManager.class, withSettings().strictness(Strictness.LENIENT));
        doReturn(false).when(streamManager).getResume();
        doReturn(streamManager).when(session).getStreamManager();
        return session;
    }

    /**
     * Reflectively invoke the private client close listener for the given session. Any returned future is joined so that
     * assertions observe a settled state.
     */
    private void fireConnectionClose(final LocalClientSession session) throws Exception
    {
        final Field listenerField = SessionManager.class.getDeclaredField("clientSessionListener");
        listenerField.setAccessible(true);
        final Object listener = listenerField.get(sessionManager);

        final Method method = listener.getClass().getDeclaredMethod("onConnectionClosing", Object.class);
        method.setAccessible(true);

        final Object result = method.invoke(listener, session);
        if (result instanceof CompletableFuture) {
            ((CompletableFuture<?>) result).join(); // wait for async routing to complete before asserting
        }
    }

    /**
     * The core presence bug. A stale session closes while the live session owns the route for the same full JID. The
     * close listener must not route an unavailable presence for that full JID (which would mark the live session
     * offline).
     */
    @Test
    public void staleSessionClose_doesNotRouteUnavailableForLiveSession() throws Exception
    {
        // Setup test fixture: a stale session closes; a route exists (owned by the live session).
        final LocalClientSession stale = session("stalestreamid");
        final LocalClientSession live = session("livestreamid");
        when(routingTable.hasClientRoute(FULL_JID)).thenReturn(true);
        when(routingTable.getClientRoute(FULL_JID)).thenReturn(live);

        // Execute system under test: the stale session's connection closes.
        fireConnectionClose(stale);

        // Verify result: no unavailable presence whose 'from' is the shared full JID was routed.
        verify(packetRouter, never()).route((Presence) argThat(p ->
            p instanceof Presence
                && Presence.Type.unavailable.equals(((Presence) p).getType())
                && FULL_JID.equals(((Presence) p).getFrom())));
    }

    /**
     * Guard against a no-op regression: when the session that closes is the one that owns the route, the listener
     * SHOULD route an unavailable presence for the full JID. This passes both before and after the fix; it ensures a
     * fix does not make the listener stop emitting unavailable presence for genuine closes.
     */
    @Test
    public void owningSessionClose_routesUnavailableForJid() throws Exception
    {
        // Setup test fixture: the closing session owns the route.
        final LocalClientSession owner = session("ownerstreamid");
        when(routingTable.hasClientRoute(FULL_JID)).thenReturn(true);
        when(routingTable.getClientRoute(FULL_JID)).thenReturn(owner);

        // Execute system under test.
        fireConnectionClose(owner);

        // Verify result: at least one unavailable presence whose 'from' is the full JID IS routed.
        verify(packetRouter, atLeastOnce()).route((Presence) argThat(p ->
            p instanceof Presence
                && Presence.Type.unavailable.equals(((Presence) p).getType())
                && FULL_JID.equals(((Presence) p).getFrom())));
    }
}
