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
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Verifies the OF-3319 fail-closed contract of resource binding in {@link SessionManager}: a bind is refused with
 * {@link SessionManager.BindResult#CONFLICT} (never silently admitted) when the conflict policy forbids kicking
 * ({@link SessionManager#NEVER_KICK} or the conflict limit not yet exceeded), the bare-JID route lock cannot be
 * acquired in time, the acquiring thread is interrupted, or the dedicated executor is saturated. Positive controls
 * confirm the admit paths (no conflict, and conflict over the limit) still bind and install the route.
 *
 * These tests cover the bind <em>decision</em> logic. Cluster-wide uniqueness and deadlock avoidance are argued
 * structurally in OF-3319 (the bind path takes the same bare-JID lock used by all client-route mutation) and are not
 * covered here; the lock is a mock.
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3319">OF-3319: IQBindHandler busy-wait replacement</a>
 */
@ExtendWith(MockitoExtension.class)
public class SessionManagerBindResourceFailClosedTest
{
    private static final String USERNAME = "juliet";
    private static final String RESOURCE = "balcony";
    private static final JID DESIRED_JID = new JID(USERNAME, Fixtures.XMPP_DOMAIN, RESOURCE, true);

    private SessionManager sessionManager;
    private XMPPServer server;
    private RoutingTable routingTable;
    private PacketRouter packetRouter;
    private Lock routeLock;
    private AuthToken authToken;
    private LocalClientSession bindingSession;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() throws Exception
    {
        routingTable = mock(RoutingTable.class, withSettings().strictness(Strictness.LENIENT));
        packetRouter = mock(PacketRouter.class, withSettings().strictness(Strictness.LENIENT));

        server = Fixtures.mockXMPPServer();
        doReturn(routingTable).when(server).getRoutingTable();
        doReturn(packetRouter).when(server).getPacketRouter();

        //noinspection deprecation
        XMPPServer.setInstance(server);

        sessionManager = new SessionManager();
        sessionManager.initialize(server);

        // The bare-JID route lock that the bind path acquires. Default: acquired immediately; individual tests override.
        routeLock = mock(Lock.class, withSettings().strictness(Strictness.LENIENT));
        when(routeLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(routingTable.getClientRouteLock(any(JID.class))).thenReturn(routeLock);

        authToken = mock(AuthToken.class, withSettings().strictness(Strictness.LENIENT));
        when(authToken.getUsername()).thenReturn(USERNAME);
        when(authToken.isAnonymous()).thenReturn(false);

        bindingSession = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(bindingSession.getAddress()).thenReturn(DESIRED_JID);
    }

    @AfterEach
    public void tearDown()
    {
        if (sessionManager != null) {
            sessionManager.stop();
        }
        Fixtures.clearExistingProperties();
    }

    /**
     * Verify policy refusal: NEVER_KICK causes the session attempting to perform a conflicting resource-bind to be rejected.
     */
    @Test
    public void neverKickRejectsBindWhenConflictingRouteExists()
    {
        // Setup test fixture: a live conflicting session owns the route; policy is NEVER_KICK.
        final ClientSession oldSession = mock(ClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(oldSession.isClosed()).thenReturn(false);
        when(routingTable.getClientRoute(DESIRED_JID)).thenReturn(oldSession);
        sessionManager.setConflictKickLimit(SessionManager.NEVER_KICK);

        // Execute system under test.
        final SessionManager.BindResult result = sessionManager.resolveConflictAndBind(bindingSession, authToken, RESOURCE, DESIRED_JID);

        // Verify result: refused; nothing kicked or installed; conflict count not increased; lock released.
        assertEquals(SessionManager.BindResult.CONFLICT, result, "NEVER_KICK must refuse the bind when a live conflict exists.");
        verify(oldSession, never()).close(any(StreamError.class));
        verify(oldSession, never()).incrementConflictCount();
        verify(bindingSession, never()).setAuthToken(any(AuthToken.class), anyString());
        verify(routeLock).unlock();
    }

    /**
     * Verify policy refusal: A 'conflict count' that has _not_ been reached causes the session attempting to perform a conflicting resource-bind to be rejected.
     */
    @Test
    public void conflictCountAtOrBelowLimitRejectsBind()
    {
        // Setup test fixture: limit 2, this is the 1st conflict (<= limit), so kicking is not yet allowed.
        final ClientSession oldSession = mock(ClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(oldSession.isClosed()).thenReturn(false);
        when(oldSession.incrementConflictCount()).thenReturn(1);
        when(routingTable.getClientRoute(DESIRED_JID)).thenReturn(oldSession);
        sessionManager.setConflictKickLimit(2);

        // Execute system under test.
        final SessionManager.BindResult result = sessionManager.resolveConflictAndBind(bindingSession, authToken, RESOURCE, DESIRED_JID);

        // Verify result: refused; nothing kicked or installed; conflict count increased; lock released.
        assertEquals(SessionManager.BindResult.CONFLICT, result, "A conflict count at or below the limit must refuse the bind.");
        verify(oldSession, never()).close(any(StreamError.class));
        verify(oldSession).incrementConflictCount();
        verify(bindingSession, never()).setAuthToken(any(AuthToken.class), anyString());
        verify(routeLock).unlock();
    }

    /**
     * Verify lock acquisition timeout handling: a lock timeout causes the session attempting to perform a conflicting resource-bind to be rejected.
     */
    @Test
    public void lockTimeoutRejectsBind() throws Exception
    {
        // Setup test fixture: the route lock cannot be acquired within the budget. A live conflict exists too, to be
        // sure refusal is due to the lock and not the no-conflict path.
        when(routeLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);
        final ClientSession oldSession = mock(ClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(routingTable.getClientRoute(DESIRED_JID)).thenReturn(oldSession);

        // Execute system under test.
        final SessionManager.BindResult result = sessionManager.resolveConflictAndBind(bindingSession, authToken, RESOURCE, DESIRED_JID);

        // Verify result: refused (NOT bound anyway); never installed; never unlocked (never acquired).
        assertEquals(SessionManager.BindResult.CONFLICT, result, "A lock-acquisition timeout must refuse the bind.");
        verify(bindingSession, never()).setAuthToken(any(AuthToken.class), anyString());
        verify(routeLock, never()).unlock();
    }

    /**
     * Verify lock acquisition interrupt handling: an interrupt causes the session attempting to perform a conflicting resource-bind to be rejected.
     */
    @Test
    public void interruptWhileAcquiringLockRejectsBindAndRestoresInterruptFlag() throws Exception
    {
        // Setup test fixture: tryLock throws InterruptedException (interrupted while waiting for the lock).
        when(routeLock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("simulated"));

        // Execute system under test on a throwaway thread, and capture whether the interrupt flag is set when the
        // method returns (the method must restore it after swallowing the InterruptedException).
        final boolean[] interruptFlagAfter = { false };
        final SessionManager.BindResult[] resultHolder = new SessionManager.BindResult[1];
        final Thread t = new Thread(() -> {
            resultHolder[0] = sessionManager.resolveConflictAndBind(bindingSession, authToken, RESOURCE, DESIRED_JID);
            interruptFlagAfter[0] = Thread.currentThread().isInterrupted();
        });
        t.start();
        t.join(5_000);

        // Verify result: refused; interrupt flag restored; never installed; never unlocked.
        assertFalse(t.isAlive(), "Test thread failed to complete.");
        assertEquals(SessionManager.BindResult.CONFLICT, resultHolder[0], "Interruption while acquiring the lock must refuse the bind.");
        assertTrue(interruptFlagAfter[0], "The interrupt flag must be restored after an InterruptedException from tryLock.");
        verify(bindingSession, never()).setAuthToken(any(AuthToken.class), anyString());
        verify(routeLock, never()).unlock();
    }

    /**
     * Verify executor saturation: a rejected submission causes the session attempting to perform a conflicting resource-bind to be rejected (submission-rejection does not escape, does not block).
     */
    @Test
    public void executorSaturationRejectsBind() throws Exception
    {
        // Setup test fixture: a SessionManager whose bind-task submission always rejects, simulating a saturated pool.
        // Overriding submitBindTask(...) is the supported seam for this (see SessionManager#submitBindTask); it avoids
        // driving the real executor to saturation.
        final SessionManager saturated = new SessionManager() {
            @Override
            protected CompletableFuture<SessionManager.BindResult> submitBindTask(@Nonnull final Supplier<SessionManager.BindResult> task) {
                throw new RejectedExecutionException("simulated saturation");
            }
        };
        try {
            saturated.initialize(server);

            // Execute system under test.
            final CompletableFuture<SessionManager.BindResult> future = saturated.bindResource(bindingSession, authToken, RESOURCE);
            final SessionManager.BindResult result = future.get(5, TimeUnit.SECONDS);

            // Verify result: load-shed to CONFLICT, completed normally (not exceptionally); the route is never installed.
            assertEquals(SessionManager.BindResult.CONFLICT, result, "A saturated executor must refuse the bind, not throw.");
            assertFalse(future.isCompletedExceptionally(), "Saturation must complete the future normally with CONFLICT.");
            verify(bindingSession, never()).setAuthToken(any(AuthToken.class), anyString());
        } finally {
            // Teardown fixture.
            saturated.stop();
        }
    }

    /**
     * Verify fail-closed behavior: unexpected runtime exceptions during binding are converted into CONFLICT and do not escape to the caller.
     */
    @Test
    public void runtimeExceptionDuringBindRejectsBind()
    {
        // Setup test fixture: no conflicting route, but the actual bind operation fails unexpectedly.
        when(routingTable.getClientRoute(DESIRED_JID)).thenReturn(null);

        doThrow(new RuntimeException("simulated"))
            .when(bindingSession)
            .setAuthToken(authToken, RESOURCE);

        // Execute system under test.
        final SessionManager.BindResult result = sessionManager.resolveConflictAndBind(bindingSession, authToken, RESOURCE, DESIRED_JID);

        // Verify result: fail closed, exception swallowed, lock released.
        assertEquals(SessionManager.BindResult.CONFLICT, result, "Unexpected runtime exceptions must fail closed as CONFLICT.");
        verify(bindingSession).setAuthToken(eq(authToken), eq(RESOURCE));
        verify(routeLock).unlock();
    }

    /**
     * Happy-flow: no conflicting route, and the route is installed.
     */
    @Test
    public void noConflictingRouteBindsAndInstalls()
    {
        // Setup test fixture: no conflicting route.
        when(routingTable.getClientRoute(DESIRED_JID)).thenReturn(null);

        // Execute system under test.
        final SessionManager.BindResult result = sessionManager.resolveConflictAndBind(bindingSession, authToken, RESOURCE, DESIRED_JID);

        // Verify result: bound; route installed; lock released.
        assertEquals(SessionManager.BindResult.BOUND, result, "With no conflicting route, the bind must succeed.");
        verify(bindingSession).setAuthToken(eq(authToken), eq(RESOURCE));
        verify(routeLock).unlock();
    }

    /**
     * Happy-flow: conflict count above the configured limit: the old session should be kicked, the new route installed.
     */
    @Test
    public void conflictCountAboveLimitKicksAndBinds()
    {
        // Setup test fixture: limit 2, this is the 3rd conflict (> limit), so the existing session is kicked.
        final ClientSession oldSession = mock(ClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(oldSession.isClosed()).thenReturn(false);
        when(oldSession.incrementConflictCount()).thenReturn(3);
        when(routingTable.getClientRoute(DESIRED_JID)).thenReturn(oldSession);
        sessionManager.setConflictKickLimit(2);

        // Execute system under test.
        final SessionManager.BindResult result = sessionManager.resolveConflictAndBind(bindingSession, authToken, RESOURCE, DESIRED_JID);

        // Verify result: existing session kicked; new route installed; bound; conflict count incremented; lock released.
        assertEquals(SessionManager.BindResult.BOUND, result, "When the conflict count exceeds the limit, the bind must succeed after kicking.");
        verify(oldSession).close(any(StreamError.class));
        verify(oldSession).incrementConflictCount();
        verify(bindingSession).setAuthToken(eq(authToken), eq(RESOURCE));
        verify(routeLock).unlock();
    }
}
