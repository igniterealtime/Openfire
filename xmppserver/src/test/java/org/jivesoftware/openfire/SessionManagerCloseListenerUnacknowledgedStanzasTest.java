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

import org.dom4j.DocumentHelper;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies how the client {@code ConnectionCloseListener} and {@link SessionManager#terminateDetached(org.jivesoftware.openfire.session.LocalSession)}
 * handle stanzas that the client did not acknowledge, when a session that uses Stream Management closes without being
 * resumed.
 *
 * XEP-0198 § 4 states that a server should treat unacknowledged stanzas in the same way that it would treat a stanza sent
 * to an unavailable resource, by returning an error to the sender, delivering it to an alternate resource, or committing
 * it to offline storage. RFC 6121 § 8.5.3.2 defines how a server treats a stanza for a full JID that matches no resource.
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3442">OF-3442: Handle all unacknowledged stanzas when a Stream Management session closes without being resumed</a>
 */
public class SessionManagerCloseListenerUnacknowledgedStanzasTest
{
    private static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "resourcepart-used-for-testing", true);
    private static final JID SENDER = new JID("janedoe", Fixtures.XMPP_DOMAIN, "sender-resource", true);

    private SessionManager sessionManager;
    private RoutingTable routingTable;
    private PacketRouter packetRouter;

    /**
     * For each message that was routed, by ID: whether a route existed for its recipient at that time. While the closing
     * session owns the route for its address, a message for that address is routed back to it.
     */
    private final Map<String, Boolean> recipientRouteExistedWhenRouted = new HashMap<>();

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        routingTable = mock(RoutingTable.class, withSettings().lenient());
        packetRouter = mock(PacketRouter.class, withSettings().lenient());
        recipientRouteExistedWhenRouted.clear();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof Message message) {
                recipientRouteExistedWhenRouted.put(message.getID(), routingTable.getClientRoute(message.getTo()) != null);
            }
            return null;
        }).when(packetRouter).route(any(Packet.class));

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
     * Verifies that an unacknowledged message is routed again when the session closes, after the session is removed, so
     * that it is treated as a message for an unavailable resource (XEP-0198 § 4), rather than routed back to the closing
     * session.
     */
    @Test
    public void unacknowledgedMessageIsRoutedAgain() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        final Message message = new Message();
        message.setID("message-1");
        message.setFrom(SENDER);
        message.setTo(FULL_JID);
        session.getStreamManager().sentStanza(message);

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        assertTrue(routedPackets().stream().anyMatch(packet -> packet instanceof Message && "message-1".equals(packet.getID())), "An unacknowledged message must be routed again when the session closes.");
        assertEquals(Boolean.FALSE, recipientRouteExistedWhenRouted.get("message-1"), "The message must be routed after the closing session is removed, so that it is not routed back to that session.");
    }

    /**
     * Verifies that unacknowledged stanzas are still handled when removing a detached session fails after its route was
     * removed. The registration of the session as detached is already cleared at that point, so there is no later
     * attempt to handle them.
     */
    @Test
    public void unacknowledgedStanzasAreHandledWhenRemovingDetachedSessionFails() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        doReturn(true).when(session).isDetached();
        final Message message = new Message();
        message.setID("message-failure-1");
        message.setFrom(SENDER);
        message.setTo(FULL_JID);
        session.getStreamManager().sentStanza(message);
        sessionManager.addDetached(session);

        // Routing an unavailable presence fails, which also happens while removing the session, after its route was removed.
        doThrow(new IllegalStateException("Failure used for testing")).when(packetRouter).route(any(Presence.class));

        // Execute system under test.
        assertThrows(IllegalStateException.class, () -> sessionManager.terminateDetached(session), "The failure used for testing is expected to be thrown by terminateDetached().");

        // Verify result.
        assertEquals(Boolean.FALSE, recipientRouteExistedWhenRouted.get("message-failure-1"), "The message must be routed again, after the route of the detached session was removed.");
    }

    /**
     * Verifies that the unacknowledged stanzas of a detached session are still handled when removing its route fails,
     * rather than being kept for another attempt to terminate the session, which would fail in the same way. As the
     * session still owns the route, its messages are routed back to it, from where they go to offline storage.
     */
    @Test
    public void failureToRemoveRouteOfDetachedSessionStillHandlesUnacknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = detachedSessionWithUnacknowledgedStanzas("message-route-failure-1");
        doThrow(new IllegalStateException("Failure used for testing")).when(routingTable).removeClientRoute(FULL_JID);

        // Execute system under test.
        assertThrows(IllegalStateException.class, () -> sessionManager.terminateDetached(session), "The failure used for testing is expected to be thrown by terminateDetached().");

        // Verify result.
        assertFalse(sessionManager.isDetached(session), "The session must not be registered as detached again.");
        assertTrue(recipientRouteExistedWhenRouted.containsKey("message-route-failure-1"), "The message must still be routed again.");
    }

    /**
     * Verifies that the unacknowledged stanzas of a detached session that was replaced by another session for the same
     * full JID are handled when the detached session is terminated. The route is owned by the other session, so the
     * stanzas can be routed right away.
     */
    @Test
    public void unacknowledgedStanzasOfReplacedDetachedSessionAreHandled() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = detachedSessionWithUnacknowledgedStanzas("message-replaced-1");
        final LocalClientSession replacement = mock(LocalClientSession.class, withSettings().lenient());
        doReturn(BasicStreamIDFactory.createStreamID("replacement-stream-id")).when(replacement).getStreamID();
        doReturn(FULL_JID).when(replacement).getAddress();
        doReturn(true).when(replacement).isAuthenticated();
        doReturn(replacement).when(routingTable).getClientRoute(FULL_JID);

        // Execute system under test.
        sessionManager.terminateDetached(session);

        // Verify result.
        assertTrue(recipientRouteExistedWhenRouted.containsKey("message-replaced-1"), "The message must be routed again.");
    }

    /**
     * Verifies that an unacknowledged message is routed again after a detached session is removed, when it is ended
     * without being resumed.
     */
    @Test
    public void unacknowledgedMessageIsRoutedAgainWhenDetachedSessionIsEnded() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        doReturn(true).when(session).isDetached();
        final Message message = new Message();
        message.setID("message-detached-1");
        message.setFrom(SENDER);
        message.setTo(FULL_JID);
        session.getStreamManager().sentStanza(message);
        sessionManager.addDetached(session);

        // Execute system under test.
        sessionManager.terminateDetached(session);

        // Verify result.
        assertEquals(Boolean.FALSE, recipientRouteExistedWhenRouted.get("message-detached-1"), "The message must be routed again, after the detached session is removed.");
    }

    /**
     * Verifies that an unacknowledged presence without a type is silently ignored when the session closes, as RFC 6121
     * § 8.5.3.2.2 requires for such a presence for a full JID that matches no resource.
     */
    @Test
    public void unacknowledgedPresenceIsIgnored() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        final Presence presence = new Presence();
        presence.setID("presence-1");
        presence.setFrom(SENDER);
        presence.setTo(FULL_JID);
        session.getStreamManager().sentStanza(presence);

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        assertTrue(routedPackets().stream().noneMatch(packet -> "presence-1".equals(packet.getID())), "An unacknowledged presence without a type must be silently ignored, but something was routed for it: " + routedPackets());
    }

    /**
     * A mock of a client session that owns the route for its full JID and has a real stream manager with Stream
     * Management enabled, without resumption. Its connection is already gone and its status is 'closed', as when the
     * close listener runs. Its route is gone once the session manager removes it.
     */
    private LocalClientSession sessionWithStreamManagement()
    {
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().lenient());
        doReturn(BasicStreamIDFactory.createStreamID("test-stream-id")).when(session).getStreamID();
        doReturn(FULL_JID).when(session).getAddress();
        doReturn(new Presence()).when(session).getPresence();
        doReturn(true).when(session).isAuthenticated();

        final StreamManager streamManager = new StreamManager(session);
        streamManager.process(DocumentHelper.createElement(QName.get("enable", StreamManager.NAMESPACE_V3)));
        assertTrue(streamManager.isEnabled(), "Test fixture expects Stream Management to be enabled.");
        doReturn(streamManager).when(session).getStreamManager();
        doReturn(false).when(session).isAuthenticated();
        doReturn(Session.Status.CLOSED).when(session).getStatus();

        final AtomicBoolean routeRemoved = new AtomicBoolean(false);
        when(routingTable.hasClientRoute(FULL_JID)).thenAnswer(invocation -> !routeRemoved.get());
        when(routingTable.getClientRoute(FULL_JID)).thenAnswer(invocation -> routeRemoved.get() ? null : session);
        when(routingTable.removeClientRoute(FULL_JID)).thenAnswer(invocation -> !routeRemoved.getAndSet(true));
        return session;
    }

    /**
     * A detached session of the test fixture, registered as such with the session manager, with an unacknowledged
     * message.
     */
    private LocalClientSession detachedSessionWithUnacknowledgedStanzas(final String messageId)
    {
        final LocalClientSession session = sessionWithStreamManagement();
        doReturn(true).when(session).isDetached();
        final Message message = new Message();
        message.setID(messageId);
        message.setFrom(SENDER);
        message.setTo(FULL_JID);
        session.getStreamManager().sentStanza(message);
        sessionManager.addDetached(session);
        return session;
    }

    /**
     * Returns all stanzas that were routed, through any of the methods of the packet router.
     */
    private List<Packet> routedPackets()
    {
        return mockingDetails(packetRouter).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("route"))
            .map(invocation -> (Packet) invocation.getArgument(0))
            .toList();
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
            ((CompletableFuture<?>) result).join();
        }
    }
}
