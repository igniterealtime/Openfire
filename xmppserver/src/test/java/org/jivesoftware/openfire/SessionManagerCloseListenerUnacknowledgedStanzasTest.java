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
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;
import org.xmpp.packet.StreamError;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

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
     * For each IQ that was routed, by ID: whether a session was registered for its sender at that time. The IQ router
     * does not accept stanzas from a registered session that is no longer authenticated, such as one that is closing.
     */
    private final Map<String, Boolean> senderSessionRegisteredWhenRouted = new HashMap<>();

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
        senderSessionRegisteredWhenRouted.clear();
        recipientRouteExistedWhenRouted.clear();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof Message message) {
                recipientRouteExistedWhenRouted.put(message.getID(), routingTable.getClientRoute(message.getTo()) != null);
            }
            return null;
        }).when(packetRouter).route(any(Packet.class));
        doAnswer(invocation -> {
            final IQ iq = invocation.getArgument(0);
            senderSessionRegisteredWhenRouted.put(iq.getID(), sessionManager.getSession(iq.getFrom()) != null);
            return null;
        }).when(packetRouter).route(any(IQ.class));

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
        //noinspection deprecation
        XMPPServer.setInstance(null);
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
     * Verifies that the sender of an unacknowledged IQ request of type 'get' receives a 'service-unavailable' error when
     * the session closes. RFC 6120 § 8.2.3 requires a response to every IQ request, and RFC 6121 § 8.5.3.2.3 defines
     * this error for an IQ stanza for a full JID that matches no resource.
     */
    @Test
    public void unacknowledgedIqGetIsAnsweredWithError() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        session.getStreamManager().sentStanza(iqRequest(IQ.Type.get, "iq-get-1"));

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        assertErrorReturnedToSender("iq-get-1");
    }

    /**
     * Verifies that the sender of an unacknowledged IQ request of type 'set' receives a 'service-unavailable' error when
     * the session closes. RFC 6120 § 8.2.3 requires a response to every IQ request, and RFC 6121 § 8.5.3.2.3 defines
     * this error for an IQ stanza for a full JID that matches no resource.
     */
    @Test
    public void unacknowledgedIqSetIsAnsweredWithError() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        session.getStreamManager().sentStanza(iqRequest(IQ.Type.set, "iq-set-1"));

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        assertErrorReturnedToSender("iq-set-1");
    }

    /**
     * Verifies that an unacknowledged IQ request that has no 'from' address, which is one that the server sent itself, is
     * answered with an error that has no 'to' address, so that it reaches the server.
     */
    @Test
    public void unacknowledgedIqRequestFromServerIsAnsweredWithErrorToServer() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        final IQ request = iqRequest(IQ.Type.get, "iq-from-server-1");
        request.setFrom((JID) null);
        session.getStreamManager().sentStanza(request);

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        final List<Packet> routed = routedPackets();
        assertTrue(routed.stream().anyMatch(packet -> packet instanceof IQ iq && iq.getType() == IQ.Type.error && "iq-from-server-1".equals(iq.getID()) && iq.getTo() == null),
            "The server must receive an error for an unacknowledged IQ request that it sent, but the stanzas routed were: " + routed);
        assertNoSessionForSenderWhenRouted("iq-from-server-1");
    }

    /**
     * Verifies that a failure to handle one unacknowledged stanza does not prevent the stanzas after it from being
     * handled.
     */
    @Test
    public void failureToHandleOneUnacknowledgedStanzaDoesNotPreventOthers() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        session.getStreamManager().sentStanza(iqRequest(IQ.Type.get, "iq-get-failing-1"));
        final Message message = new Message();
        message.setID("message-after-failure-1");
        message.setFrom(SENDER);
        message.setTo(FULL_JID);
        session.getStreamManager().sentStanza(message);
        doThrow(new IllegalStateException("Failure used for testing")).when(packetRouter).route(any(IQ.class));

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        assertEquals(Boolean.FALSE, recipientRouteExistedWhenRouted.get("message-after-failure-1"), "The message must be routed again, even though routing the error for the IQ request before it failed.");
    }

    /**
     * Verifies that the error for an unacknowledged IQ request without a 'to' address, which was addressed to the session,
     * has the address of the session as its sender.
     */
    @Test
    public void errorForUnacknowledgedIqRequestWithoutRecipientHasSessionAsSender() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        final IQ request = iqRequest(IQ.Type.get, "iq-without-to-1");
        request.setTo((JID) null);
        session.getStreamManager().sentStanza(request);

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        final IQ error = (IQ) routedPackets().stream().filter(packet -> packet instanceof IQ && "iq-without-to-1".equals(packet.getID())).findFirst()
            .orElseThrow(() -> new AssertionError("Expected an error to be routed for the unacknowledged IQ request."));
        assertEquals(FULL_JID, error.getFrom(), "The error must have the address of the session as its sender, as the request was addressed to that session.");
        assertEquals(SENDER, error.getTo(), "The error must be addressed to the sender of the request.");
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
        session.getStreamManager().sentStanza(iqRequest(IQ.Type.get, "iq-get-failure-1"));
        sessionManager.addDetached(session);

        // Routing an unavailable presence fails, which also happens while removing the session, after its route was removed.
        doThrow(new IllegalStateException("Failure used for testing")).when(packetRouter).route(any(Presence.class));

        // Execute system under test.
        assertThrows(IllegalStateException.class, () -> sessionManager.terminateDetached(session), "The failure used for testing is expected to be thrown by terminateDetached().");

        // Verify result.
        assertEquals(Boolean.FALSE, recipientRouteExistedWhenRouted.get("message-failure-1"), "The message must be routed again, after the route of the detached session was removed.");
        assertErrorReturnedToSender("iq-get-failure-1");
    }

    /**
     * Verifies that the unacknowledged stanzas of a detached session are still handled when removing its route fails,
     * rather than being kept for another attempt to terminate the session, which would fail in the same way. As the
     * session still owns the route, its messages are routed back to it, from where they go to offline storage, and the
     * IQ router drops its errors.
     */
    @Test
    public void failureToRemoveRouteOfDetachedSessionStillHandlesUnacknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = detachedSessionWithUnacknowledgedStanzas("message-route-failure-1", "iq-get-route-failure-1");
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
        final LocalClientSession session = detachedSessionWithUnacknowledgedStanzas("message-replaced-1", "iq-get-replaced-1");
        final LocalClientSession replacement = mock(LocalClientSession.class, withSettings().lenient());
        doReturn(BasicStreamIDFactory.createStreamID("replacement-stream-id")).when(replacement).getStreamID();
        doReturn(FULL_JID).when(replacement).getAddress();
        doReturn(true).when(replacement).isAuthenticated();
        doReturn(replacement).when(routingTable).getClientRoute(FULL_JID);

        // Execute system under test.
        sessionManager.terminateDetached(session);

        // Verify result.
        assertTrue(recipientRouteExistedWhenRouted.containsKey("message-replaced-1"), "The message must be routed again.");
        final List<Packet> routed = routedPackets();
        assertTrue(routed.stream().anyMatch(packet -> packet instanceof IQ iq && iq.getType() == IQ.Type.error && "iq-get-replaced-1".equals(iq.getID()) && SENDER.equals(iq.getTo())),
            "The sender of the IQ request must receive an error, but the stanzas routed were: " + routed);
    }

    /**
     * Verifies that the unacknowledged stanzas of a detached session are handled when a new session binds the same
     * resource, which kicks the detached session instead of resuming it. They are handled only once the client has the
     * result of the bind, and its messages are routed while the new session owns the route of the resource.
     */
    @Test
    public void unacknowledgedStanzasOfDetachedSessionKickedByBindAreHandledAfterBindResult() throws Exception
    {
        // Setup test fixture.
        kickableDetachedSession("message-kicked-1", "iq-get-kicked-1");
        final LocalClientSession binding = bindingSession();
        final CountDownLatch anyRouted = new CountDownLatch(1);
        doAnswer(invocation -> { anyRouted.countDown(); return null; }).when(packetRouter).route(any(Packet.class));
        final CountDownLatch errorRouted = new CountDownLatch(1);
        doAnswer(invocation -> { anyRouted.countDown(); errorRouted.countDown(); return null; }).when(packetRouter).route(any(IQ.class));
        final SessionManager.BindResult result = sessionManager.resolveConflictAndBind(binding, mock(AuthToken.class), FULL_JID.getResource(), FULL_JID);
        assertEquals(SessionManager.BindResult.BOUND, result, "Test fixture expects the new session to be bound.");
        assertFalse(anyRouted.await(500, TimeUnit.MILLISECONDS), "No stanza of the kicked session may be routed before the client has the result of the bind, but these were: " + routedPackets());

        // Execute system under test.
        sessionManager.handleStanzasOfReplacedDetachedSession(binding);

        // Verify result.
        assertTrue(errorRouted.await(5, TimeUnit.SECONDS), "The sender of the unacknowledged IQ request must receive an error.");
        final List<Packet> routed = routedPackets();
        assertTrue(routed.stream().anyMatch(packet -> packet instanceof Message && "message-kicked-1".equals(packet.getID())),
            "The unacknowledged message must be routed again, to the full JID that the new session now owns, but the stanzas routed were: " + routed);
        assertTrue(routed.stream().anyMatch(packet -> packet instanceof IQ iq && iq.getType() == IQ.Type.error && "iq-get-kicked-1".equals(iq.getID()) && SENDER.equals(iq.getTo())
                && iq.getError() != null && iq.getError().getCondition() == PacketError.Condition.service_unavailable),
            "The sender of the IQ request must receive a 'service-unavailable' error, but the stanzas routed were: " + routed);
    }

    /**
     * Verifies that a bind that fails after kicking a detached session does not record that session on the session that
     * failed to bind, as that session did not get the resource, and the route of the resource still belongs to the
     * kicked session.
     */
    @Test
    public void detachedSessionKickedByFailedBindIsNotRecordedOnBindingSession() throws Exception
    {
        // Setup test fixture.
        kickableDetachedSession("message-failed-bind-1", "iq-get-failed-bind-1");
        final LocalClientSession binding = bindingSession();
        doThrow(new IllegalStateException("Simulated failure to install the route.")).when(binding).setAuthToken(any(), anyString());

        // Execute system under test.
        final SessionManager.BindResult result = sessionManager.resolveConflictAndBind(binding, mock(AuthToken.class), FULL_JID.getResource(), FULL_JID);

        // Verify result.
        assertEquals(SessionManager.BindResult.CONFLICT, result, "Test fixture expects the bind to fail.");
        verify(binding, never().description("A bind that failed must not record the session that it kicked on the session that failed to bind.")).setSessionData(anyString(), any());
    }

    /**
     * Verifies that the stanzas of a kicked detached session are not handled when a resumption that raced the bind
     * reattached that session before they could be handled, as the resumed session then delivers them itself.
     */
    @Test
    public void unacknowledgedStanzasOfKickedSessionThatWasResumedAreNotHandled() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession detached = kickableDetachedSession("message-resumed-1", "iq-get-resumed-1");
        final LocalClientSession binding = bindingSession();
        final CountDownLatch routed = new CountDownLatch(1);
        doAnswer(invocation -> { routed.countDown(); return null; }).when(packetRouter).route(any(Packet.class));
        doAnswer(invocation -> { routed.countDown(); return null; }).when(packetRouter).route(any(IQ.class));
        final SessionManager.BindResult result = sessionManager.resolveConflictAndBind(binding, mock(AuthToken.class), FULL_JID.getResource(), FULL_JID);
        assertEquals(SessionManager.BindResult.BOUND, result, "Test fixture expects the new session to be bound.");
        doReturn(mock(Connection.class)).when(detached).getConnection();

        // Execute system under test.
        sessionManager.handleStanzasOfReplacedDetachedSession(binding);

        // Verify result.
        assertFalse(routed.await(500, TimeUnit.MILLISECONDS), "The stanzas of a kicked session that was resumed must be left to the resumed session, but these were routed: " + routedPackets());
    }

    /**
     * Verifies that a bind that kicks a session that is not detached leaves its unacknowledged stanzas to the close
     * listener of its connection, as handling them in both places would route them twice.
     */
    @Test
    public void unacknowledgedStanzasOfConnectedSessionKickedByBindAreLeftToCloseListener() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession connected = sessionWithStreamManagement();
        doReturn(Session.Status.AUTHENTICATED).when(connected).getStatus();
        doReturn(1).when(connected).incrementConflictCount();
        final Message message = new Message();
        message.setID("message-connected-1");
        message.setFrom(SENDER);
        message.setTo(FULL_JID);
        connected.getStreamManager().sentStanza(message);
        doReturn(new ReentrantLock()).when(routingTable).getClientRouteLock(FULL_JID);
        sessionManager.setConflictKickLimit(0);
        final LocalClientSession binding = bindingSession();
        final CountDownLatch routed = new CountDownLatch(1);
        doAnswer(invocation -> { routed.countDown(); return null; }).when(packetRouter).route(any(Packet.class));
        final SessionManager.BindResult result = sessionManager.resolveConflictAndBind(binding, mock(AuthToken.class), FULL_JID.getResource(), FULL_JID);
        assertEquals(SessionManager.BindResult.BOUND, result, "Test fixture expects the new session to be bound.");
        verify(connected, times(1).description("Test fixture expects the connected session to be kicked.")).close(any(StreamError.class));

        // Execute system under test.
        sessionManager.handleStanzasOfReplacedDetachedSession(binding);

        // Verify result.
        assertFalse(routed.await(500, TimeUnit.MILLISECONDS), "The unacknowledged stanzas of a kicked session that is not detached must be left to its close listener, but these were routed: " + routedPackets());
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
     * Verifies that the sender of an unacknowledged IQ request receives a 'service-unavailable' error when a detached
     * session is ended without being resumed.
     */
    @Test
    public void unacknowledgedIqRequestIsAnsweredWithErrorWhenDetachedSessionIsEnded() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        doReturn(true).when(session).isDetached();
        session.getStreamManager().sentStanza(iqRequest(IQ.Type.get, "iq-get-detached-1"));
        sessionManager.addDetached(session);

        // Execute system under test.
        sessionManager.terminateDetached(session);

        // Verify result.
        assertErrorReturnedToSender("iq-get-detached-1");
    }

    /**
     * Verifies that the error for an unacknowledged IQ request does not include the payload of the request. RFC 6120
     * § 8.3.1 allows for it to be left out, and an error without a payload cannot be mistaken for a request by a handler
     * for the namespace of that payload.
     */
    @Test
    public void errorForUnacknowledgedIqRequestHasNoPayload() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        session.getStreamManager().sentStanza(iqRequest(IQ.Type.get, "iq-get-2"));

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        final IQ error = (IQ) routedPackets().stream().filter(packet -> packet instanceof IQ && "iq-get-2".equals(packet.getID())).findFirst()
            .orElseThrow(() -> new AssertionError("Expected an error to be routed for the unacknowledged IQ request."));
        assertNull(error.getChildElement(), "The error must not include the payload of the request, but was: " + error.toXML());
    }

    /**
     * Verifies that an unacknowledged IQ response is not answered when the session closes, as RFC 6120 § 8.2.3 forbids
     * responding to an IQ of type 'result' or 'error'.
     */
    @Test
    public void unacknowledgedIqResultIsNotAnswered() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = sessionWithStreamManagement();
        final IQ result = new IQ(IQ.Type.result, "iq-result-1");
        result.setFrom(SENDER);
        result.setTo(FULL_JID);
        session.getStreamManager().sentStanza(result);

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        assertTrue(routedPackets().stream().noneMatch(packet -> "iq-result-1".equals(packet.getID())), "An unacknowledged IQ response must not be answered, but something was routed for it: " + routedPackets());
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

        final StreamManager streamManager = StreamManager.forSession(session);
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
     * message and IQ request.
     */
    private LocalClientSession detachedSessionWithUnacknowledgedStanzas(final String messageId, final String iqId)
    {
        final LocalClientSession session = sessionWithStreamManagement();
        doReturn(true).when(session).isDetached();
        final Message message = new Message();
        message.setID(messageId);
        message.setFrom(SENDER);
        message.setTo(FULL_JID);
        session.getStreamManager().sentStanza(message);
        session.getStreamManager().sentStanza(iqRequest(IQ.Type.get, iqId));
        sessionManager.addDetached(session);
        return session;
    }

    /**
     * A detached session of the test fixture with an unacknowledged message and IQ request, which a bind of its resource
     * kicks: the conflict kick limit is zero, and the route lock is available. A session that can be resumed keeps its
     * status when its connection closes, so it is not closed.
     */
    private LocalClientSession kickableDetachedSession(final String messageId, final String iqId)
    {
        final LocalClientSession session = detachedSessionWithUnacknowledgedStanzas(messageId, iqId);
        doReturn(Session.Status.AUTHENTICATED).when(session).getStatus();
        doReturn(false).when(session).isClosed();
        doReturn(1).when(session).incrementConflictCount();
        doReturn(new ReentrantLock()).when(routingTable).getClientRouteLock(FULL_JID);
        sessionManager.setConflictKickLimit(0);
        return session;
    }

    /**
     * A mock of a session that binds the resource of the test fixture, and that keeps its session data.
     */
    private static LocalClientSession bindingSession()
    {
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().lenient());
        doReturn(BasicStreamIDFactory.createStreamID("binding-stream-id")).when(session).getStreamID();
        doReturn(true).when(session).isAuthenticated();
        final Map<String, Object> data = new ConcurrentHashMap<>();
        doAnswer(invocation -> data.put(invocation.getArgument(0), invocation.getArgument(1))).when(session).setSessionData(anyString(), any());
        doAnswer(invocation -> data.remove(invocation.getArgument(0))).when(session).removeSessionData(anyString());
        return session;
    }

    private static IQ iqRequest(final IQ.Type type, final String id)
    {
        final IQ iq = new IQ(type, id);
        iq.setFrom(SENDER);
        iq.setTo(FULL_JID);
        iq.setChildElement("query", "jabber:iq:version");
        return iq;
    }

    private void assertErrorReturnedToSender(final String id)
    {
        final List<Packet> routed = routedPackets();
        assertTrue(routed.stream().anyMatch(packet -> packet instanceof IQ iq && iq.getType() == IQ.Type.error && id.equals(iq.getID()) && SENDER.equals(iq.getTo())
                && iq.getError() != null && iq.getError().getCondition() == PacketError.Condition.service_unavailable),
            "The sender of an unacknowledged IQ request must receive a 'service-unavailable' error with the same ID when the session closes, but the stanzas routed were: " + routed);
        assertNoSessionForSenderWhenRouted(id);
    }

    private void assertNoSessionForSenderWhenRouted(final String id)
    {
        assertEquals(Boolean.FALSE, senderSessionRegisteredWhenRouted.get(id), "The error must be routed after the closing session is removed, as the IQ router does not accept it while that session is registered as its sender.");
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
