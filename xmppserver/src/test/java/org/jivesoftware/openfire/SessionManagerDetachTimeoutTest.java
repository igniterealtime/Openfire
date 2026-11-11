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
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that a detached session (XEP-0198 § 5) is kept for xmpp.session.detach.timeout from the moment at which it
 * was detached, rather than from its last activity.
 *
 * These tests run the task that ends detached sessions directly, with a session manager that is spied on so that the
 * tests can tell which sessions it ends, without ending them.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class SessionManagerDetachTimeoutTest
{
    private static final Duration DETACH_TIMEOUT = Duration.ofMinutes(5);

    private SessionManager sessionManager;

    private SessionManager spiedSessionManager;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        Fixtures.clearExistingProperties();

        final XMPPServer server = Fixtures.mockXMPPServer();
        doReturn(mock(RoutingTable.class, withSettings().strictness(Strictness.LENIENT))).when(server).getRoutingTable();
        doReturn(mock(PacketRouter.class, withSettings().strictness(Strictness.LENIENT))).when(server).getPacketRouter();
        //noinspection deprecation
        XMPPServer.setInstance(server);

        sessionManager = new SessionManager();
        sessionManager.initialize(server);
        doReturn(sessionManager).when(server).getSessionManager();
        JiveGlobals.setProperty("xmpp.session.detach.timeout", String.valueOf(DETACH_TIMEOUT.toMillis()));

        // The spy shares the detached sessions of the session manager; it records which sessions are ended, without ending them.
        spiedSessionManager = spy(sessionManager);
        doNothing().when(spiedSessionManager).terminateDetached(any());
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that a session that had no activity for longer than the detach timeout before its connection broke is
     * kept after it is detached, so that the client can still resume it.
     */
    @Test
    void sessionInactiveBeforeDetachingIsKeptWithinDetachTimeout() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = clientSessionInactiveFor(DETACH_TIMEOUT.plusMinutes(1));
        session.setDetached();

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        verify(spiedSessionManager, never().description("A session that was just detached must not be ended, however long it had no activity before.")).terminateDetached(any());
        assertTrue(sessionManager.isDetached(session), "A session that was just detached must still be detached, however long it had no activity before.");
    }

    /**
     * Verifies that a session that has been detached for longer than the detach timeout is ended.
     */
    @Test
    void sessionDetachedForLongerThanDetachTimeoutIsEnded() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = clientSessionInactiveFor(DETACH_TIMEOUT.plusMinutes(2));
        session.setDetached();
        setLastDetachedDate(session, Instant.now().minus(DETACH_TIMEOUT.plusMinutes(1)));

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        verify(spiedSessionManager, times(1).description("A session that has been detached for longer than the detach timeout must be ended.")).terminateDetached(session);
    }

    /**
     * Verifies that a session that is detached records the moment at which that happened.
     */
    @Test
    void detachingRecordsMomentOfDetaching()
    {
        // Setup test fixture.
        final LocalClientSession session = clientSessionInactiveFor(DETACH_TIMEOUT.plusMinutes(1));
        final Instant before = Instant.now();

        // Execute system under test.
        session.setDetached();

        // Verify result.
        final Instant after = Instant.now();
        final Instant result = session.getLastDetachedDate();
        assertNotNull(result, "A session that is detached must record the moment at which it was detached.");
        assertFalse(result.isBefore(before) || result.isAfter(after), "The recorded moment must be the moment at which the session was detached, but was " + result + " for a detach between " + before + " and " + after + ".");
    }

    /**
     * Verifies that a session that is detached, resumed and detached again is measured from the second detach: a
     * session of which the first detach is longer ago than the detach timeout is kept. The resumption is represented by
     * taking the session from the detached sessions, as a resumption does before it moves the connection.
     */
    @Test
    void detachingAgainAfterResumptionStartsNewDetachedPeriod() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = clientSessionInactiveFor(DETACH_TIMEOUT.plusMinutes(2));
        session.setDetached();
        final Instant firstDetach = Instant.now().minus(DETACH_TIMEOUT.plusMinutes(1));
        setLastDetachedDate(session, firstDetach);
        assertTrue(sessionManager.removeDetached(session), "Test fixture expects the session to be taken from the detached sessions when it is resumed.");
        session.setDetached();

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        assertTrue(session.getLastDetachedDate().isAfter(firstDetach), "A session that is detached again must record the moment of its second detach.");
        verify(spiedSessionManager, never().description("A session that was detached again must not be ended because its first detach is longer ago than the detach timeout.")).terminateDetached(any());
        assertTrue(sessionManager.isDetached(session), "A session that was detached again must still be detached while its second detach is not longer ago than the detach timeout.");
    }

    /**
     * Verifies that a session that is registered as detached without being detached, and therefore has no recorded
     * detach moment, is ended once its last activity is longer ago than the detach timeout.
     */
    @Test
    void detachedSessionWithoutDetachMomentIsMeasuredFromLastActivity() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = clientSessionInactiveFor(DETACH_TIMEOUT.plusMinutes(1));
        sessionManager.addDetached(session);
        assertNull(session.getLastDetachedDate(), "Test fixture expects a session that was registered as detached without being detached to have no detach moment.");

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        verify(spiedSessionManager, times(1).description("A detached session without a recorded detach moment must be ended once its last activity is longer ago than the detach timeout.")).terminateDetached(session);
    }

    /**
     * Verifies that a session that is registered as detached without being detached, and therefore has no recorded
     * detach moment, is kept while its last activity is not longer ago than the detach timeout.
     */
    @Test
    void detachedSessionWithoutDetachMomentIsKeptWhileRecentlyActive() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = clientSessionInactiveFor(DETACH_TIMEOUT.minusMinutes(1));
        sessionManager.addDetached(session);
        assertNull(session.getLastDetachedDate(), "Test fixture expects a session that was registered as detached without being detached to have no detach moment.");

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        verify(spiedSessionManager, never().description("A detached session without a recorded detach moment must not be ended while its last activity is not longer ago than the detach timeout.")).terminateDetached(any());
        assertTrue(sessionManager.isDetached(session), "A detached session without a recorded detach moment must still be detached while its last activity is not longer ago than the detach timeout.");
    }

    /**
     * Verifies that a session that was detached and resumed, and that is later registered as detached without being
     * detached again, is measured from its last activity after the resumption, not from its earlier detach.
     */
    @Test
    void detachedSessionWithEarlierDetachMomentIsMeasuredFromLaterActivity() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = clientSessionInactiveFor(DETACH_TIMEOUT.minusMinutes(1));
        session.setDetached();
        setLastDetachedDate(session, Instant.now().minus(DETACH_TIMEOUT.plusMinutes(1)));
        assertTrue(sessionManager.removeDetached(session), "Test fixture expects the session to be taken from the detached sessions when it is resumed.");
        sessionManager.addDetached(session);

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        verify(spiedSessionManager, never().description("A session registered as detached without being detached again must not be ended because an earlier detach is longer ago than the detach timeout.")).terminateDetached(any());
        assertTrue(sessionManager.isDetached(session), "A session registered as detached without being detached again must still be detached while its last activity is not longer ago than the detach timeout.");
    }

    /**
     * Verifies that a session of which the last activity is later than its recorded detach moment is ended once that
     * last activity is longer ago than the detach timeout.
     */
    @Test
    void detachedSessionWithEarlierDetachMomentIsEndedOnceLaterActivityExpires() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = clientSessionInactiveFor(DETACH_TIMEOUT.plusMinutes(1));
        session.setDetached();
        setLastDetachedDate(session, Instant.now().minus(DETACH_TIMEOUT.plusMinutes(3)));
        assertTrue(sessionManager.removeDetached(session), "Test fixture expects the session to be taken from the detached sessions when it is resumed.");
        sessionManager.addDetached(session);

        // Execute system under test.
        runDetachedCleanupTask();

        // Verify result.
        verify(spiedSessionManager, times(1).description("A session of which the last activity is later than its detach moment must be ended once that last activity is longer ago than the detach timeout.")).terminateDetached(session);
    }

    /**
     * A client session, of which the last activity is reported to be the provided duration ago, that uses the session
     * manager under test. Detaching it records the moment of detaching, as for any session.
     */
    private static LocalClientSession clientSessionInactiveFor(final Duration inactiveFor)
    {
        final Connection connection = mock(Connection.class, withSettings().strictness(Strictness.LENIENT));
        final LocalClientSession session = spy(new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH));
        doReturn(Date.from(Instant.now().minus(inactiveFor))).when(session).getLastActiveDate();
        return session;
    }

    /**
     * Sets the moment at which the provided session was most recently detached, so that a test can have a session that
     * was detached in the past.
     */
    private static void setLastDetachedDate(final LocalSession session, final Instant moment) throws Exception
    {
        final Field field = LocalSession.class.getDeclaredField("lastDetachedDate");
        field.setAccessible(true);
        field.set(session, moment);
    }

    /**
     * Runs the private task that ends detached sessions once, for the spied-on session manager.
     */
    private void runDetachedCleanupTask() throws Exception
    {
        final Class<?> taskClass = Class.forName(SessionManager.class.getName() + "$DetachedCleanupTask");
        final Constructor<?> constructor = taskClass.getDeclaredConstructor(SessionManager.class);
        constructor.setAccessible(true);
        ((Runnable) constructor.newInstance(spiedSessionManager)).run();
    }
}
