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
package org.jivesoftware.openfire.spi;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;
import org.xmpp.packet.JID;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Verifies that the task of the routing table that closes idle outgoing server sessions closes them so that they cannot
 * be resumed (XEP-0198), as Openfire closes them on purpose, and leaves sessions that are detached to their detach
 * timeout.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RoutingTableImplServerCleanupTaskTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final DomainPair DOMAIN_PAIR = new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN);

    private RoutingTableImpl routingTable;

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
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        final XMPPServer server = Fixtures.mockXMPPServer();
        doReturn(mock(PacketRouter.class, withSettings().strictness(Strictness.LENIENT))).when(server).getPacketRouter();
        //noinspection deprecation
        XMPPServer.setInstance(server);
        routingTable = new RoutingTableImpl();
        doReturn(routingTable).when(server).getRoutingTable();
        routingTable.initialize(server);
        final SessionManager sessionManager = new SessionManager();
        sessionManager.initialize(server);
        doReturn(sessionManager).when(server).getSessionManager();
        ConnectionSettings.Server.IDLE_TIMEOUT_PROPERTY.setValue(Duration.ofMinutes(5));
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that an idle outgoing server session is made non-resumable before it is closed, so that its close
     * listener ends it rather than keeping it to be resumed.
     */
    @Test
    public void idleSessionIsMadeNonResumableBeforeItIsClosed() throws Exception
    {
        // Setup test fixture.
        final LocalOutgoingServerSession session = idleSession(false);
        final List<String> invoked = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { invoked.add("markNonResumable"); return null; }).when(session).markNonResumable();
        doAnswer(invocation -> { invoked.add("close"); return null; }).when(session).close();

        // Execute system under test.
        runServerCleanupTask();

        // Verify result.
        assertEquals(List.of("markNonResumable", "close"), invoked, "An idle session must be made non-resumable, and then closed.");
    }

    /**
     * Verifies that an idle outgoing server session that is detached is left to its detach timeout, rather than closed
     * or ended because it is idle.
     */
    @Test
    public void detachedSessionIsLeftToItsDetachTimeout() throws Exception
    {
        // Setup test fixture.
        final LocalOutgoingServerSession session = idleSession(true);

        // Execute system under test.
        runServerCleanupTask();

        // Verify result.
        verify(session, never().description("A detached session must not be made non-resumable because it is idle.")).markNonResumable();
        verify(session, never().description("A detached session must not be closed because it is idle.")).close();
    }

    /**
     * A mock of an outgoing server session that is the route for {@link #DOMAIN_PAIR}, that was last active longer ago
     * than the idle timeout, and that is or is not detached.
     */
    private LocalOutgoingServerSession idleSession(final boolean detached) throws Exception
    {
        final LocalOutgoingServerSession session = mock(LocalOutgoingServerSession.class, withSettings().strictness(Strictness.LENIENT));
        doReturn(BasicStreamIDFactory.createStreamID("idle-stream")).when(session).getStreamID();
        doReturn(new JID(null, REMOTE_DOMAIN, null)).when(session).getAddress();
        doReturn(Date.from(Instant.now().minus(Duration.ofHours(1)))).when(session).getLastActiveDate();
        doReturn(detached).when(session).isDetached();
        localServerRoutingTable().addRoute(DOMAIN_PAIR, session);
        return session;
    }

    /**
     * Returns the private table of the routes of the outgoing server sessions on this cluster node.
     */
    @SuppressWarnings("unchecked")
    private LocalRoutingTable<LocalOutgoingServerSession> localServerRoutingTable() throws Exception
    {
        final Field field = RoutingTableImpl.class.getDeclaredField("localServerRoutingTable");
        field.setAccessible(true);
        return (LocalRoutingTable<LocalOutgoingServerSession>) field.get(routingTable);
    }

    /**
     * Runs the private task of the routing table that closes idle outgoing server sessions once.
     */
    private void runServerCleanupTask() throws Exception
    {
        final Class<?> taskClass = Class.forName(RoutingTableImpl.class.getName() + "$ServerCleanupTask");
        final Constructor<?> constructor = taskClass.getDeclaredConstructor(RoutingTableImpl.class);
        constructor.setAccessible(true);
        ((Runnable) constructor.newInstance(routingTable)).run();
    }
}
