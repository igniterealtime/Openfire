/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.streammanagement;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify what {@link StreamManager} implements for both ends of a stream, for a session that is
 * resumed, or that is detached and ended. Each test runs against both {@link AnsweringStreamManager} and
 * {@link RequestingStreamManager}, which each complete a resumption in their own way (see {@link End#resume}).
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class StreamManagerDetachedSessionTest extends AbstractStreamManagerTest
{
    /**
     * Verifies that stanzas that are sent to a detached session after exceeding the maximum number of unacknowledged
     * stanzas are still tracked. A detached session has no connection to hand them to, so they would otherwise be lost.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void stanzasSentToDetachedSessionAfterExceedingMaximumAreTracked(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        final CountDownLatch terminated = new CountDownLatch(1);
        final CountDownLatch terminatedAgain = new CountDownLatch(2);
        doAnswer(invocation -> { terminated.countDown(); terminatedAgain.countDown(); return null; }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "Test fixture expects the detached session to be ended.");

        // Execute system under test.
        sm.sentStanza(message("3"));

        // Verify result.
        assertEquals(List.of("1", "2", "3"), unacknowledgedMessageIds(sm), "Stanzas that are sent to a detached session must be tracked, so that they are routed when the session closes.");
        // OF-3434: Wait with a latch, as verify() with a timeout holds the lock of the synchronized terminateDetached() on the mock.
        assertFalse(terminatedAgain.await(500, TimeUnit.MILLISECONDS), "The detached session must be ended once, not again for every stanza that is sent after exceeding the maximum.");
    }

    /**
     * Verifies that a session is ended when its close listener detached it while it was closed for exceeding the
     * maximum number of unacknowledged stanzas. That happens when the connection was already closing, and its close
     * listener found the session to be resumable before resumption was disabled.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void sessionDetachedWhileClosingForExceedingMaximumIsEnded(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalSession session = end.newSession();
        final Connection connection = session.getConnection();
        final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        doReturn(closeFuture).when(connection).getCloseFuture();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> { terminated.countDown(); return null; }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        doReturn(true).when(session).isDetached();
        closeFuture.complete(null);

        // Verify result.
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "A session that was detached while it was closed for exceeding the maximum must be ended.");
    }

    /**
     * Verifies that a session that is not detached when its connection finished closing, after it was closed for
     * exceeding the maximum number of unacknowledged stanzas, is not ended as a detached session, as it has been closed.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void sessionNotDetachedWhenClosedForExceedingMaximumIsNotEndedAgain(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalSession session = end.newSession();
        final Connection connection = session.getConnection();
        final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        doReturn(closeFuture).when(connection).getCloseFuture();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> { terminated.countDown(); return null; }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        closeFuture.complete(null);

        // Verify result.
        assertFalse(terminated.await(500, TimeUnit.MILLISECONDS), "A session that is not detached must not be ended as a detached session.");
    }

    /**
     * Verifies that a detached session, which has no stream to close, is ended when the maximum number of unacknowledged
     * stanzas is exceeded, rather than after its detach timeout.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void exceedingMaximumOfUnacknowledgedStanzasOnDetachedSessionEndsSession(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        sm.sentStanza(message("1"));

        // OF-3434: Wait with a latch, as verify() with a timeout holds the lock of the synchronized terminateDetached() on the mock.
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> { terminated.countDown(); return null; }).when(sessionManager).terminateDetached(session);

        // Execute system under test.
        sm.sentStanza(message("2"));

        // Verify result.
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "A detached session must be ended when the maximum number of unacknowledged stanzas is exceeded.");
        verify(sessionManager, times(1).description("A detached session must be ended once.")).terminateDetached(session);
    }

    /**
     * Verifies that a stanza is still tracked when it is delivered on a connection that is not closed, after the maximum
     * number of unacknowledged stanzas was exceeded. That happens when a detached session that exceeded the maximum is
     * resumed: its new connection delivers the stanza before the resumed stream is closed, so no fallback handles it.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void stanzaDeliveredOnResumedConnectionAfterExceedingMaximumIsTracked(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> { terminated.countDown(); return null; }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "Test fixture expects the detached session to be ended.");
        doReturn(false).when(session).isDetached();
        final Connection resumedConnection = closableConnection();
        doReturn(resumedConnection).when(session).getConnection();

        // Execute system under test.
        sm.sentStanza(message("3"));

        // Verify result.
        assertEquals(List.of("1", "2", "3"), unacknowledgedMessageIds(sm), "A stanza that is delivered on a connection that is not closed must be tracked, as no fallback handles it.");
        assertFalse(resumedConnection.isClosed(), "A stanza that is sent after the maximum was exceeded must not close the stream again; closing a resumed stream is up to its resumption.");
    }

    /**
     * Verifies that a detached session that exceeded the maximum number of unacknowledged stanzas is closed when it is
     * resumed before it could be ended, rather than continuing on the new connection without a limit.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void resumptionAfterExceedingMaximumOfUnacknowledgedStanzasClosesStream(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        // Hold the task that ends the detached session until the session was resumed, so that the resumption runs first.
        final CountDownLatch resumed = new CountDownLatch(1);
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> {
            resumed.await(5, TimeUnit.SECONDS);
            terminated.countDown();
            return null;
        }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        final Connection newConnection = closableConnection();
        doReturn(false).when(session).isDetached();
        doReturn(newConnection).when(session).getConnection();

        // Execute system under test.
        end.resume(sm, 0);
        resumed.countDown();

        // Verify result.
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "Test fixture expects the task that ends the detached session to complete.");
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(newConnection, times(1).description("The resumed stream must be closed, as the maximum number of unacknowledged stanzas was exceeded.")).close(captor.capture());
        assertEquals(StreamError.Condition.policy_violation, captor.getValue().getCondition(), "The stream error must use the 'policy-violation' condition, as a configured limit was exceeded.");
        verify(newConnection, never().description("Stanzas must not be sent again on a stream that is being closed.")).deliver(any());
        assertEquals(List.of("1", "2"), unacknowledgedMessageIds(sm), "Stanzas that were not acknowledged must still be routed when the session closes.");
    }

    /**
     * Verifies that a resumed stream that is closed for exceeding the maximum number of unacknowledged stanzas is closed
     * while the lock of the stream manager is held. The session delivers and tracks stanzas under that lock. Closing the
     * stream between those two steps would leave a stanza that was delivered on the open stream untracked.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void resumedStreamIsClosedForExceedingMaximumWhileHoldingLock(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> { terminated.countDown(); return null; }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "Test fixture expects the detached session to be ended.");
        final Connection newConnection = mock(Connection.class);
        final AtomicBoolean closedWhileHoldingLock = new AtomicBoolean(false);
        doAnswer(invocation -> { closedWhileHoldingLock.set(Thread.holdsLock(sm)); return null; }).when(newConnection).close(any());
        doReturn(false).when(session).isDetached();
        doReturn(newConnection).when(session).getConnection();

        // Execute system under test.
        end.resume(sm, 0);

        // Verify result.
        verify(newConnection, times(1).description("The resumed stream must be closed, as the maximum number of unacknowledged stanzas was exceeded.")).close(any());
        assertTrue(closedWhileHoldingLock.get(), "The resumed stream must be closed while the lock of the stream manager is held, so that no stanza is delivered on it without being tracked.");
    }

    /**
     * Verifies that a stanza that is sent while a session is resumed, and that exceeds the maximum number of
     * unacknowledged stanzas, cannot close the resumed stream before the unacknowledged stanzas are re-delivered on it.
     * The closed stream would hand the re-delivered stanzas to its fallback, while they are also routed again when the
     * session closes.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void stanzaExceedingMaximumDuringResumptionDoesNotCloseStreamBeforeRedelivery(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "2");
        final LocalSession session = end.newSession();
        final Connection connection = session.getConnection();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        final List<String> deliveredWhileClosed = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            if (connection.isClosed()) {
                deliveredWhileClosed.add(((Packet) invocation.getArgument(0)).getID());
            }
            return null;
        }).when(connection).deliver(any());
        // Send a stanza from another thread, as LocalClientSession#pushPackets() does, when the connection is obtained for the re-delivery.
        final AtomicBoolean sending = new AtomicBoolean(false);
        final Thread[] sender = new Thread[1];
        doAnswer(invocation -> {
            if (sending.compareAndSet(false, true)) {
                sender[0] = new Thread(() -> { synchronized (sm) { sm.sentStanza(message("3")); } });
                sender[0].start();
                sender[0].join(500);
            }
            return connection;
        }).when(session).getConnection();

        // Execute system under test.
        end.resume(sm, 0);

        // Verify result.
        sender[0].join(5000);
        assertTrue(connection.isClosed(), "Test fixture expects the stream to be closed for exceeding the maximum.");
        assertEquals(List.of(), deliveredWhileClosed, "Unacknowledged stanzas must be re-delivered before the stream is closed, as the stanzas on a closed stream go to its fallback, and are also routed again when the session closes.");
    }
}
