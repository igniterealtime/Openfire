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
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify how {@link SessionManager} keeps a client session that uses Stream Management when its
 * connection breaks, so that the session can be resumed, and how it ends that session when it is not resumed in time
 * (XEP-0198 § 4 and § 7).
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class SessionManagerDetachedSessionTest
{
    private static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource", true);
    private static final Duration DETACH_TIMEOUT = Duration.ofMinutes(5);

    private SessionManager sessionManager;
    private RoutingTable routingTable;

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
        final PacketRouter packetRouter = mock(PacketRouter.class, withSettings().strictness(Strictness.LENIENT));

        final XMPPServer server = Fixtures.mockXMPPServer();
        doReturn(routingTable).when(server).getRoutingTable();
        doReturn(packetRouter).when(server).getPacketRouter();

        //noinspection deprecation
        XMPPServer.setInstance(server);

        sessionManager = new SessionManager();
        sessionManager.initialize(server);
        JiveGlobals.setProperty("xmpp.session.detach.timeout", String.valueOf(DETACH_TIMEOUT.toMillis()));
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that a session of which the stream can be resumed is detached when its connection closes, rather than
     * removed, so that the client can resume it (XEP-0198 § 7).
     */
    @Test
    void resumableSessionIsDetachedWhenConnectionCloses() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = clientSession(true);

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        verify(session, times(1).description("A session of which the stream can be resumed must be detached when its connection closes.")).setDetached();
        verify(routingTable, never().description("The route of a detached session must be kept, so that the session can be resumed.")).removeClientRoute(any());
        verify(session.getStreamManager(), never().description("The unacknowledged stanzas of a detached session must be kept, so that they can be sent again on resumption.")).onClose(any(), any());
    }

    /**
     * Verifies that a session of which the stream cannot be resumed is removed when its connection closes, rather than
     * detached.
     */
    @Test
    void nonResumableSessionIsRemovedWhenConnectionCloses() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = clientSession(false);

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        verify(session, never().description("A session of which the stream cannot be resumed must not be detached.")).setDetached();
        verify(routingTable, times(1).description("The route of a session that cannot be resumed must be removed.")).removeClientRoute(FULL_JID);
        verify(session.getStreamManager(), times(1).description("The unacknowledged stanzas of a session that cannot be resumed must be handled.")).onClose(any(), any());
    }

    /**
     * Verifies that a detached session is ended once it has been detached for longer than xmpp.session.detach.timeout:
     * it is no longer detached, its unacknowledged stanzas are handled, and its route is removed.
     */
    @Test
    void detachedSessionIsEndedAfterDetachTimeout() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = detachedSession(DETACH_TIMEOUT.plusMinutes(1));

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        assertFalse(sessionManager.isDetached(session), "A session that was detached for longer than the detach timeout must no longer be detached.");
        verify(session.getStreamManager(), times(1).description("The unacknowledged stanzas of a session that is ended must be handled.")).onClose(any(), any());
        verify(routingTable, times(1).description("The route of a session that is ended must be removed.")).removeClientRoute(FULL_JID);
    }

    /**
     * Verifies that a detached session is kept while it has been detached for less than xmpp.session.detach.timeout,
     * so that the client can still resume it.
     */
    @Test
    void detachedSessionIsKeptWithinDetachTimeout() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = detachedSession(DETACH_TIMEOUT.minusMinutes(1));

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        assertTrue(sessionManager.isDetached(session), "A session that was detached for less than the detach timeout must still be detached.");
        verify(session.getStreamManager(), never().description("The unacknowledged stanzas of a session that can still be resumed must be kept.")).onClose(any(), any());
        verify(routingTable, never().description("The route of a session that can still be resumed must be kept.")).removeClientRoute(any());
    }

    /**
     * Verifies that a detached session is kept, however long it has been detached, when xmpp.session.detach.timeout is
     * set to -1.
     */
    @Test
    void detachedSessionIsKeptWhenDetachTimeoutIsDisabled() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("xmpp.session.detach.timeout", "-1");
        final LocalClientSession session = detachedSession(Duration.ofDays(1));

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        assertTrue(sessionManager.isDetached(session), "A detached session must be kept when the detach timeout is disabled.");
        verify(session.getStreamManager(), never().description("The unacknowledged stanzas of a session that is kept must be kept.")).onClose(any(), any());
    }

    /**
     * Verifies that the unacknowledged stanzas of a session that is removed when its connection closes are handled after
     * the session is removed. XEP-0198 § 4 has them treated as stanzas for an unavailable resource. While the session is
     * still registered, a message would be routed back to it, rather than to another resource of the user.
     */
    @Test
    void unacknowledgedStanzasAreHandledAfterClosingSessionIsRemoved() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = clientSession(false);
        final AtomicBoolean removedBeforeOnClose = recordWhetherRemovedBeforeOnClose(session);

        // Execute system under test.
        fireConnectionClose(session);

        // Verify result.
        assertTrue(removedBeforeOnClose.get(), "The session must be removed before its unacknowledged stanzas are handled.");
    }

    /**
     * Verifies that the unacknowledged stanzas of a detached session that is ended are handled after the session is
     * removed, for the same reason as for a session that is removed when its connection closes.
     */
    @Test
    void unacknowledgedStanzasAreHandledAfterDetachedSessionIsRemoved() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = detachedSession(DETACH_TIMEOUT.plusMinutes(1));
        final AtomicBoolean removedBeforeOnClose = recordWhetherRemovedBeforeOnClose(session);

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        assertTrue(removedBeforeOnClose.get(), "The session must be removed before its unacknowledged stanzas are handled.");
    }

    /**
     * A mock of a client session that is the route for its full JID, and of which the stream can or cannot be resumed.
     */
    private LocalClientSession clientSession(final boolean resumable)
    {
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        doReturn(BasicStreamIDFactory.createStreamID("test-stream-id")).when(session).getStreamID();
        doReturn(FULL_JID).when(session).getAddress();
        doReturn(new Presence()).when(session).getPresence();
        doReturn(false).when(session).isDetached();
        doReturn(session).when(routingTable).getClientRoute(FULL_JID);

        final StreamManager streamManager = mock(StreamManager.class, withSettings().strictness(Strictness.LENIENT));
        doReturn(resumable).when(streamManager).getResume();
        doReturn(streamManager).when(session).getStreamManager();
        return session;
    }

    /**
     * A mock of a client session of which the stream can be resumed, registered as detached for the provided duration.
     */
    private LocalClientSession detachedSession(final Duration detachedFor)
    {
        final LocalClientSession session = clientSession(true);
        doReturn(true).when(session).isDetached();
        doReturn(Date.from(Instant.now().minus(detachedFor))).when(session).getLastActiveDate();
        sessionManager.addDetached(session);
        assertTrue(sessionManager.isDetached(session), "Test fixture expects the session to be registered as detached.");
        return session;
    }

    /**
     * Records whether the route of a session was removed by the time its unacknowledged stanzas are handled.
     */
    private AtomicBoolean recordWhetherRemovedBeforeOnClose(final LocalClientSession session)
    {
        final AtomicBoolean removed = new AtomicBoolean(false);
        final AtomicBoolean removedBeforeOnClose = new AtomicBoolean(false);
        doAnswer(invocation -> { removed.set(true); return true; }).when(routingTable).removeClientRoute(FULL_JID);
        final StreamManager streamManager = session.getStreamManager();
        doAnswer(invocation -> { removedBeforeOnClose.set(removed.get()); return null; }).when(streamManager).onClose(any(), any());
        return removedBeforeOnClose;
    }

    /**
     * Invokes the private close listener for client sessions for the provided session, and waits for it to complete.
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

    /**
     * Runs the private task that ends detached sessions once, rather than waiting for it to be scheduled.
     */
    private void runDetachedCleanupTask() throws Exception
    {
        final Class<?> taskClass = Class.forName(SessionManager.class.getName() + "$DetachedCleanupTask");
        final Constructor<?> constructor = taskClass.getDeclaredConstructor(SessionManager.class);
        constructor.setAccessible(true);
        ((Runnable) constructor.newInstance(sessionManager)).run();
    }
}
