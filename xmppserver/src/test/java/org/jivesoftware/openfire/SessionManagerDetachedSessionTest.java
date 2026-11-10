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
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.AnsweringStreamManager;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.jivesoftware.openfire.streammanagement.TerminationDelegate;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify how {@link SessionManager} keeps a client session that uses Stream Management when its
 * connection breaks, so that the session can be resumed, and how it ends that session when it is not resumed in time
 * (XEP-0198 § 4 and § 7). Incoming server sessions are kept in the same way when resumption on server connections is
 * enabled, and detached server sessions are ended after a timeout of their own.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class SessionManagerDetachedSessionTest
{
    private static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource", true);
    private static final JID REMOTE_DOMAIN = new JID("remote.example.org");
    private static final Duration DETACH_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration SERVER_DETACH_TIMEOUT = Duration.ofMinutes(1);

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
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(SERVER_DETACH_TIMEOUT);
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
     * Verifies that a detached server session is ended once it has been detached for longer than
     * xmpp.server.session.detach.timeout, even when it has not been detached for longer than the timeout for client
     * sessions.
     */
    @Test
    void detachedServerSessionIsEndedAfterServerDetachTimeout() throws Exception
    {
        // Setup test fixture.
        final SessionManager spiedSessionManager = spy(sessionManager);
        final LocalIncomingServerSession session = detachedServerSession(SERVER_DETACH_TIMEOUT.plusSeconds(30));

        // Execute system under test.
        runDetachedCleanupTask(spiedSessionManager);

        // Verify result.
        verify(spiedSessionManager, times(1).description("A server session that was detached for longer than the server detach timeout must be ended.")).terminateDetached(session);
    }

    /**
     * Verifies that a detached server session is kept while it has been detached for less than
     * xmpp.server.session.detach.timeout.
     */
    @Test
    void detachedServerSessionIsKeptWithinServerDetachTimeout() throws Exception
    {
        // Setup test fixture.
        final SessionManager spiedSessionManager = spy(sessionManager);
        final LocalIncomingServerSession session = detachedServerSession(SERVER_DETACH_TIMEOUT.minusSeconds(30));

        // Execute system under test.
        runDetachedCleanupTask(spiedSessionManager);

        // Verify result.
        verify(spiedSessionManager, never().description("A server session that was detached for less than the server detach timeout must be kept.")).terminateDetached(any());
        assertTrue(sessionManager.isDetached(session), "A server session that was detached for less than the server detach timeout must still be detached.");
    }

    /**
     * Verifies that a detached client session is kept while it has been detached for less than
     * xmpp.session.detach.timeout, even when it has been detached for longer than the shorter timeout for server
     * sessions.
     */
    @Test
    void detachedClientSessionIsKeptAfterServerDetachTimeout() throws Exception
    {
        // Setup test fixture.
        final SessionManager spiedSessionManager = spy(sessionManager);
        final LocalClientSession session = detachedSession(SERVER_DETACH_TIMEOUT.plusSeconds(30));

        // Execute system under test.
        runDetachedCleanupTask(spiedSessionManager);

        // Verify result.
        verify(spiedSessionManager, never().description("A client session must not be ended after the timeout for server sessions.")).terminateDetached(any());
        assertTrue(sessionManager.isDetached(session), "A client session that was detached for less than the client detach timeout must still be detached.");
    }

    /**
     * Verifies that a detached server session is kept, however long it has been detached, when
     * xmpp.server.session.detach.timeout is set to -1, while detached client sessions are still ended after their own
     * timeout.
     */
    @Test
    void detachedServerSessionIsKeptWhenServerDetachTimeoutIsDisabled() throws Exception
    {
        // Setup test fixture.
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofMillis(-1));
        final SessionManager spiedSessionManager = spy(sessionManager);
        final LocalIncomingServerSession serverSession = detachedServerSession(Duration.ofDays(1));
        final LocalClientSession clientSession = detachedSession(DETACH_TIMEOUT.plusMinutes(1));

        // Execute system under test.
        runDetachedCleanupTask(spiedSessionManager);

        // Verify result.
        verify(spiedSessionManager, never().description("A detached server session must be kept when the server detach timeout is disabled.")).terminateDetached(serverSession);
        verify(spiedSessionManager, times(1).description("A detached client session must still be ended after the client detach timeout when the server detach timeout is disabled.")).terminateDetached(clientSession);
    }

    /**
     * Verifies that the termination delegates of a detached server session are given the timeout for server sessions,
     * rather than the one for client sessions.
     */
    @Test
    void terminationDelegateOfServerSessionIsGivenServerDetachTimeout() throws Exception
    {
        // Setup test fixture.
        final LocalIncomingServerSession session = detachedServerSession(Duration.ZERO);
        final TerminationDelegate delegate = mock(TerminationDelegate.class);
        doReturn(false).when(delegate).shouldTerminate(any());
        final StreamManager streamManager = session.getStreamManager();
        doReturn(Set.of(delegate)).when(streamManager).getTerminationDelegates();

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        verify(delegate, times(1).description("The termination delegate of a server session must be given the server detach timeout.")).shouldTerminate(SERVER_DETACH_TIMEOUT);
    }

    /**
     * Verifies that an incoming server session of which the stream can be resumed is detached when its connection
     * closes, while resumption on server connections is enabled, and that its validated domain stays registered.
     */
    @Test
    void resumableIncomingServerSessionIsDetachedWhenConnectionCloses() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalIncomingServerSession session = incomingServerSession(true);

        // Execute system under test.
        fireIncomingServerConnectionClose(session);

        // Verify result.
        assertTrue(sessionManager.isDetached(session), "An incoming server session of which the stream can be resumed must be detached when its connection closes.");
        assertSame(session, sessionManager.getIncomingServerSession(session.getStreamID()), "A detached incoming server session must stay registered.");
        assertEquals(List.of(session), sessionManager.getIncomingServerSessions(REMOTE_DOMAIN.getDomain()), "The validated domain of a detached incoming server session must stay registered for that session.");
    }

    /**
     * Verifies that an incoming server session of which the stream can be resumed is not detached when its connection
     * closes while resumption on server connections is disabled, but has its validated domain unregistered, as any
     * other incoming server session.
     */
    @Test
    void resumableIncomingServerSessionIsNotDetachedWhenServerResumptionIsDisabled() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(false);
        final LocalIncomingServerSession session = incomingServerSession(true);

        // Execute system under test.
        fireIncomingServerConnectionClose(session);

        // Verify result.
        assertFalse(sessionManager.isDetached(session), "An incoming server session must not be detached while resumption on server connections is disabled.");
        assertNull(sessionManager.getIncomingServerSession(session.getStreamID()), "An incoming server session that is not detached must be unregistered when its connection closes.");
        assertTrue(sessionManager.getIncomingServerSessions(REMOTE_DOMAIN.getDomain()).isEmpty(), "The validated domain of an incoming server session that is not detached must be unregistered when its connection closes.");
    }

    /**
     * Verifies that an incoming server session of which the stream cannot be resumed is not detached when its
     * connection closes, also while resumption on server connections is enabled, and has its validated domain
     * unregistered.
     */
    @Test
    void nonResumableIncomingServerSessionIsNotDetachedWhenConnectionCloses() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalIncomingServerSession session = incomingServerSession(false);

        // Execute system under test.
        fireIncomingServerConnectionClose(session);

        // Verify result.
        assertFalse(sessionManager.isDetached(session), "An incoming server session of which the stream cannot be resumed must not be detached.");
        assertNull(sessionManager.getIncomingServerSession(session.getStreamID()), "An incoming server session of which the stream cannot be resumed must be unregistered when its connection closes.");
        assertTrue(sessionManager.getIncomingServerSessions(REMOTE_DOMAIN.getDomain()).isEmpty(), "The validated domain of an incoming server session of which the stream cannot be resumed must be unregistered when its connection closes.");
    }

    /**
     * Verifies that a detached incoming server session is ended once it has been detached for longer than
     * xmpp.server.session.detach.timeout: it is no longer detached, and it and its validated domain are unregistered,
     * as when its connection had closed without it being detached.
     */
    @Test
    void detachedIncomingServerSessionIsUnregisteredAfterServerDetachTimeout() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalIncomingServerSession session = incomingServerSession(true);
        fireIncomingServerConnectionClose(session);
        assertTrue(sessionManager.isDetached(session), "Test fixture expects the incoming server session to be detached.");
        stubDetachedSince(session, Instant.now().minus(SERVER_DETACH_TIMEOUT.plusSeconds(30)));

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        assertFalse(sessionManager.isDetached(session), "An incoming server session that was detached for longer than the server detach timeout must no longer be detached.");
        assertNull(sessionManager.getIncomingServerSession(session.getStreamID()), "An incoming server session that is ended must be unregistered.");
        assertTrue(sessionManager.getIncomingServerSessions(REMOTE_DOMAIN.getDomain()).isEmpty(), "The validated domain of an incoming server session that is ended must be unregistered.");
    }

    /**
     * Verifies that a detached incoming server session with several validated domains is unregistered completely once
     * it is ended: neither the session nor any of its validated domains stays registered.
     */
    @Test
    void detachedIncomingServerSessionWithSeveralDomainsIsUnregisteredCompletely() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final String otherDomain = "other.example.org";
        final LocalIncomingServerSession session = incomingServerSession(true);
        doReturn(List.of(REMOTE_DOMAIN.getDomain(), otherDomain)).when(session).getValidatedDomains();
        sessionManager.registerIncomingServerSession(otherDomain, session);
        fireIncomingServerConnectionClose(session);
        assertTrue(sessionManager.isDetached(session), "Test fixture expects the incoming server session to be detached.");

        // Execute system under test.
        sessionManager.terminateDetached(session);

        // Verify result.
        assertFalse(sessionManager.isDetached(session), "An incoming server session that is ended must no longer be detached.");
        assertNull(sessionManager.getIncomingServerSession(session.getStreamID()), "An incoming server session with several validated domains that is ended must be unregistered.");
        assertTrue(sessionManager.getIncomingServerSessions(REMOTE_DOMAIN.getDomain()).isEmpty(), "The first validated domain of an incoming server session that is ended must be unregistered.");
        assertTrue(sessionManager.getIncomingServerSessions(otherDomain).isEmpty(), "The second validated domain of an incoming server session that is ended must be unregistered.");
    }

    /**
     * Verifies that a detached incoming server session, and its validated domain, stay registered while it has been
     * detached for less than xmpp.server.session.detach.timeout.
     */
    @Test
    void detachedIncomingServerSessionStaysRegisteredWithinServerDetachTimeout() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalIncomingServerSession session = incomingServerSession(true);
        fireIncomingServerConnectionClose(session);
        assertTrue(sessionManager.isDetached(session), "Test fixture expects the incoming server session to be detached.");
        stubDetachedSince(session, Instant.now().minus(SERVER_DETACH_TIMEOUT.minusSeconds(30)));

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        assertTrue(sessionManager.isDetached(session), "An incoming server session that was detached for less than the server detach timeout must still be detached.");
        assertEquals(List.of(session), sessionManager.getIncomingServerSessions(REMOTE_DOMAIN.getDomain()), "The validated domain of an incoming server session that can still be resumed must stay registered.");
    }

    /**
     * Verifies that a detached incoming server session is unregistered only once, when the session is ended more than
     * once, for example by the cleanup task and by another cluster node at the same time.
     */
    @Test
    void detachedIncomingServerSessionIsUnregisteredOnlyOnce() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalIncomingServerSession session = incomingServerSession(true);
        fireIncomingServerConnectionClose(session);
        assertTrue(sessionManager.isDetached(session), "Test fixture expects the incoming server session to be detached.");
        final SessionManager spiedSessionManager = spy(sessionManager);

        // Execute system under test.
        spiedSessionManager.terminateDetached(session);
        spiedSessionManager.terminateDetached(session);

        // Verify result.
        verify(spiedSessionManager, times(1).description("A detached incoming server session must be unregistered only by the first request to end the session.")).unregisterIncomingServerSession(session.getStreamID());
    }

    /**
     * Verifies that a detached incoming server session that is ended is unregistered, with its validated domains,
     * without holding the monitor of the session manager, so that no lock is taken while that monitor is held.
     */
    @Test
    void detachedIncomingServerSessionIsUnregisteredWithoutHoldingSessionManagerMonitor() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalIncomingServerSession session = incomingServerSession(true);
        fireIncomingServerConnectionClose(session);
        assertTrue(sessionManager.isDetached(session), "Test fixture expects the incoming server session to be detached.");
        final SessionManager spiedSessionManager = spy(sessionManager);
        final AtomicBoolean unregistered = new AtomicBoolean(false);
        final AtomicBoolean heldMonitor = new AtomicBoolean(false);
        doAnswer(invocation -> {
            unregistered.set(true);
            heldMonitor.set(Thread.holdsLock(spiedSessionManager));
            return invocation.callRealMethod();
        }).when(spiedSessionManager).unregisterIncomingServerSession(any(StreamID.class));

        // Execute system under test.
        spiedSessionManager.terminateDetached(session);

        // Verify result.
        assertTrue(unregistered.get(), "A detached incoming server session that is ended must be unregistered.");
        assertFalse(heldMonitor.get(), "A detached incoming server session must be unregistered without holding the monitor of the session manager.");
    }

    /**
     * Verifies that the task that ends detached sessions runs at a tenth of the shortest timeout, so that a session
     * is ended at most a tenth of its timeout late.
     */
    @Test
    void cleanupPeriodIsATenthOfShortestTimeout()
    {
        // Setup test fixture.
        final Duration clientTimeout = Duration.ofMinutes(10);
        final Duration serverTimeout = Duration.ofMinutes(2);

        // Execute system under test.
        final Duration result = SessionManager.getDetachedCleanupPeriod(clientTimeout, serverTimeout);

        // Verify result.
        assertEquals(Duration.ofSeconds(12), result, "The cleanup task must run at a tenth of the shortest detach timeout.");
    }

    /**
     * Verifies that a disabled (negative) timeout is not used to determine how often the task that ends detached
     * sessions runs.
     */
    @Test
    void cleanupPeriodIgnoresDisabledTimeout()
    {
        // Setup test fixture.
        final Duration clientTimeout = Duration.ofMinutes(10);
        final Duration serverTimeout = Duration.ofMillis(-1);

        // Execute system under test.
        final Duration result = SessionManager.getDetachedCleanupPeriod(clientTimeout, serverTimeout);

        // Verify result.
        assertEquals(Duration.ofMinutes(1), result, "A disabled detach timeout must not be used to determine how often the cleanup task runs.");
    }

    /**
     * Verifies that the task that ends detached sessions runs every three minutes when all timeouts are disabled.
     */
    @Test
    void cleanupPeriodIsThreeMinutesWhenAllTimeoutsAreDisabled()
    {
        // Setup test fixture.
        final Duration disabled = Duration.ofMillis(-1);

        // Execute system under test.
        final Duration result = SessionManager.getDetachedCleanupPeriod(disabled, disabled);

        // Verify result.
        assertEquals(Duration.ofMinutes(3), result, "The cleanup task must run every three minutes when all detach timeouts are disabled.");
    }

    /**
     * Verifies that the detach timeout of server sessions does not determine how often the task that ends detached
     * sessions runs when resumption on server connections is disabled, as server sessions are not detached then.
     */
    @Test
    void cleanupPeriodIgnoresServerTimeoutWhenServerResumptionIsDisabled()
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(false);

        // Execute system under test.
        final Duration result = sessionManager.getDetachedCleanupPeriodForSettings();

        // Verify result.
        assertEquals(DETACH_TIMEOUT.dividedBy(10), result, "When resumption on server connections is disabled, the cleanup task must run at a tenth of the detach timeout of client sessions, even when that of server sessions is shorter.");
    }

    /**
     * Verifies that the detach timeout of server sessions determines how often the task that ends detached sessions
     * runs when resumption on server connections is enabled and that timeout is the shortest.
     */
    @Test
    void cleanupPeriodUsesServerTimeoutWhenServerResumptionIsEnabled()
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);

        // Execute system under test.
        final Duration result = sessionManager.getDetachedCleanupPeriodForSettings();

        // Verify result.
        assertEquals(SERVER_DETACH_TIMEOUT.dividedBy(10), result, "When resumption on server connections is enabled, the cleanup task must run at a tenth of the shortest detach timeout, which is that of server sessions.");
    }

    /**
     * Verifies that the task that ends detached sessions is rescheduled to run at a tenth of the detach timeout of server
     * sessions when resumption on server connections is enabled while the task runs, as server sessions are then kept
     * for that timeout.
     */
    @Test
    void cleanupTaskIsRescheduledWhenServerResumptionIsEnabled()
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(false);
        sessionManager.startDetachedCleanup();
        try {
            assertEquals(DETACH_TIMEOUT.dividedBy(10), sessionManager.getScheduledDetachedCleanupPeriod(), "Test fixture expects the task to run at a tenth of the detach timeout of client sessions.");

            // Execute system under test.
            StreamManager.SERVER_RESUME_ENABLED.setValue(true);

            // Verify result.
            assertEquals(SERVER_DETACH_TIMEOUT.dividedBy(10), sessionManager.getScheduledDetachedCleanupPeriod(), "When resumption on server connections is enabled, the task must be rescheduled to run at a tenth of the detach timeout of server sessions.");
        } finally {
            sessionManager.stopDetachedCleanup();
        }
    }

    /**
     * Verifies that the task that ends detached sessions is rescheduled when the detach timeout of server sessions
     * changes while resumption on server connections is enabled.
     */
    @Test
    void cleanupTaskIsRescheduledWhenServerDetachTimeoutChanges()
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        sessionManager.startDetachedCleanup();
        try {
            // Execute system under test.
            SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofSeconds(30));

            // Verify result.
            assertEquals(Duration.ofSeconds(3), sessionManager.getScheduledDetachedCleanupPeriod(), "When the detach timeout of server sessions changes, the task must be rescheduled to run at a tenth of the new timeout.");
        } finally {
            sessionManager.stopDetachedCleanup();
        }
    }

    /**
     * Verifies that the task that ends detached sessions is not scheduled again when a setting changes after it was
     * stopped.
     */
    @Test
    void stoppedCleanupTaskIsNotRescheduled()
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(false);
        sessionManager.startDetachedCleanup();
        sessionManager.stopDetachedCleanup();

        // Execute system under test.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);

        // Verify result.
        assertNull(sessionManager.getScheduledDetachedCleanupPeriod(), "A task that was stopped must not be scheduled again when a setting changes.");
    }

    /**
     * Verifies that the time between two runs of the task that ends detached sessions is limited to between one second
     * and three minutes.
     */
    @Test
    void cleanupPeriodIsLimited()
    {
        // Setup test fixture.
        final Duration veryShort = Duration.ZERO;
        final Duration veryLong = Duration.ofHours(2);

        // Execute system under test.
        final Duration shortResult = SessionManager.getDetachedCleanupPeriod(veryShort);
        final Duration longResult = SessionManager.getDetachedCleanupPeriod(veryLong);

        // Verify result.
        assertEquals(Duration.ofSeconds(1), shortResult, "The cleanup task must not run more often than once a second.");
        assertEquals(Duration.ofMinutes(3), longResult, "The cleanup task must run at least every three minutes.");
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

        final StreamManager streamManager = mock(AnsweringStreamManager.class, withSettings().strictness(Strictness.LENIENT));
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
        stubDetachedSince(session, Instant.now().minus(detachedFor));
        sessionManager.addDetached(session);
        assertTrue(sessionManager.isDetached(session), "Test fixture expects the session to be registered as detached.");
        return session;
    }

    /**
     * A mock of an incoming server session from a remote domain, registered as detached for the provided duration.
     */
    private LocalIncomingServerSession detachedServerSession(final Duration detachedFor)
    {
        final LocalIncomingServerSession session = mock(LocalIncomingServerSession.class, withSettings().strictness(Strictness.LENIENT));
        doReturn(BasicStreamIDFactory.createStreamID("test-server-stream-id")).when(session).getStreamID();
        doReturn(REMOTE_DOMAIN).when(session).getAddress();
        doReturn(true).when(session).isDetached();
        stubDetachedSince(session, Instant.now().minus(detachedFor));

        final StreamManager streamManager = mock(AnsweringStreamManager.class, withSettings().strictness(Strictness.LENIENT));
        doReturn(true).when(streamManager).getResume();
        doReturn(streamManager).when(session).getStreamManager();

        sessionManager.addDetached(session);
        assertTrue(sessionManager.isDetached(session), "Test fixture expects the server session to be registered as detached.");
        return session;
    }

    /**
     * An incoming server session from a remote domain, of which the last activity is reported to be the provided
     * duration ago, that uses the session manager under test. Detaching it records the moment of detaching, as for any
     * session.
     */
    private LocalIncomingServerSession realIncomingServerSessionInactiveFor(final Duration inactiveFor)
    {
        doReturn(sessionManager).when(XMPPServer.getInstance()).getSessionManager();
        final Connection connection = mock(Connection.class, withSettings().strictness(Strictness.LENIENT));
        final LocalIncomingServerSession session = spy(new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID("test-real-server-stream-id"), REMOTE_DOMAIN.getDomain()));
        doReturn(Date.from(Instant.now().minus(inactiveFor))).when(session).getLastActiveDate();
        return session;
    }

    /**
     * Stubs the moment at which the provided mocked session was most recently detached, with its last activity at that
     * same moment, as for a session that has had no activity since it was detached.
     */
    private static void stubDetachedSince(final LocalSession session, final Instant moment)
    {
        doReturn(moment).when(session).getLastDetachedDate();
        doReturn(Date.from(moment)).when(session).getLastActiveDate();
    }

    /**
     * Sets the moment at which the provided session was most recently detached, so that a test can have a session
     * that was detached in the past.
     */
    private static void setLastDetachedDate(final LocalSession session, final Instant moment) throws Exception
    {
        final Field field = LocalSession.class.getDeclaredField("lastDetachedDate");
        field.setAccessible(true);
        field.set(session, moment);
    }

    /**
     * A mock of an incoming server session with one validated domain, registered with the session manager, of which
     * the stream can or cannot be resumed. Whether it is detached is kept by the session manager.
     */
    private LocalIncomingServerSession incomingServerSession(final boolean resumable)
    {
        final LocalIncomingServerSession session = mock(LocalIncomingServerSession.class, withSettings().strictness(Strictness.LENIENT));
        doReturn(new BasicStreamIDFactory().createStreamID()).when(session).getStreamID();
        doReturn(REMOTE_DOMAIN).when(session).getAddress();
        doReturn(List.of(REMOTE_DOMAIN.getDomain())).when(session).getValidatedDomains();
        doAnswer(invocation -> sessionManager.isDetached(session)).when(session).isDetached();
        doAnswer(invocation -> { sessionManager.addDetached(session); return null; }).when(session).setDetached();

        final AnsweringStreamManager streamManager = mock(AnsweringStreamManager.class, withSettings().strictness(Strictness.LENIENT));
        doReturn(resumable).when(streamManager).getResume();
        doReturn(streamManager).when(session).getStreamManager();

        sessionManager.registerIncomingServerSession(REMOTE_DOMAIN.getDomain(), session);
        assertEquals(List.of(session), sessionManager.getIncomingServerSessions(REMOTE_DOMAIN.getDomain()), "Test fixture expects the validated domain of the incoming server session to be registered.");
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
        fireConnectionClose("clientSessionListener", session);
    }

    /**
     * Invokes the private close listener for incoming server sessions for the provided session, and waits for it to
     * complete.
     */
    private void fireIncomingServerConnectionClose(final LocalIncomingServerSession session) throws Exception
    {
        fireConnectionClose("incomingServerListener", session);
    }

    /**
     * Invokes the private close listener in the provided field of the session manager for the provided session, and
     * waits for it to complete.
     */
    private void fireConnectionClose(final String listenerFieldName, final Object session) throws Exception
    {
        final Field listenerField = SessionManager.class.getDeclaredField(listenerFieldName);
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
        runDetachedCleanupTask(sessionManager);
    }

    /**
     * Runs the private task that ends detached sessions once for the provided session manager, which can be a spy that
     * records which sessions the task ends.
     */
    private void runDetachedCleanupTask(final SessionManager manager) throws Exception
    {
        final Class<?> taskClass = Class.forName(SessionManager.class.getName() + "$DetachedCleanupTask");
        final Constructor<?> constructor = taskClass.getDeclaredConstructor(SessionManager.class);
        constructor.setAccessible(true);
        ((Runnable) constructor.newInstance(manager)).run();
    }
}
