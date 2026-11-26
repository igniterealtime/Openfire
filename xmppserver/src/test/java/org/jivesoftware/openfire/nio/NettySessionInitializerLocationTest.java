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
package org.jivesoftware.openfire.nio;

import com.google.common.net.InetAddresses;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.net.SocketUtil;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.streammanagement.RequestingStreamManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify where {@link NettySessionInitializer#init(ConnectionListener)} connects to: first to the
 * location that the peer named for resuming a stream (XEP-0198 § 3), if any, and to the hosts of the remote domain when
 * no connection to that location can be made.
 */
public class NettySessionInitializerLocationTest
{
    private static final InetAddress PEER_ADDRESS = InetAddresses.forString("203.0.113.5");

    private static final DomainPair DOMAIN_PAIR = new DomainPair(Fixtures.XMPP_DOMAIN, "remote.example.org");
    private static final String LOCATION = "node2.remote.example.org:5270";

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        NettySessionInitializer.initializeSharedResources();
    }

    @AfterAll
    public static void tearDownClass()
    {
        NettySessionInitializer.destroySharedResources();
    }

    @AfterEach
    public void tearDown()
    {
        // Clears an interrupt that a test left on this thread.
        Thread.interrupted();
    }

    /**
     * Verifies that the location that the peer named is connected to first, within the time that the remote server is
     * given to answer a request to resume, and that the hosts of the remote domain are connected to when no connection
     * to the location can be made (XEP-0198 § 3: "if reconnection to that location fails, the standard XMPP connection
     * algorithm specified in RFC 6120 applies").
     */
    @Test
    public void connectsToRemoteDomainWhenLocationCannotBeConnectedTo()
    {
        // Setup test fixture.
        final List<String> connectedTo = new CopyOnWriteArrayList<>();
        final NettySessionInitializer initialiser = new NettySessionInitializer(DOMAIN_PAIR, 5269, resumeTarget(), LOCATION);
        try (final MockedStatic<SocketUtil> socketUtil = mockStatic(SocketUtil.class))
        {
            socketUtil.when(() -> SocketUtil.createSocketToLocation(anyString(), anyString(), anyInt(), any(), any())).thenAnswer(invocation -> { connectedTo.add("location"); return null; });
            socketUtil.when(() -> SocketUtil.createSocketToXmppDomain(anyString(), anyInt(), any(), any())).thenAnswer(invocation -> { connectedTo.add("domain"); return null; });

            // Execute system under test & Verify result.
            assertThrows(NetworkEntityUnreachableException.class, () -> initialiser.init(mock(ConnectionListener.class)), "When neither the location nor the remote domain can be connected to, the initialiser must report that the remote domain is unreachable.");
            assertEquals(List.of("location", "domain"), connectedTo, "The location must be connected to first, and the remote domain when no connection to the location can be made.");
            socketUtil.verify(() -> SocketUtil.createSocketToLocation(eq(DOMAIN_PAIR.getRemote()), eq(LOCATION), eq(5269), eq(LocalOutgoingServerSession.STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT.getValue()), any()), times(1).description("The location must be connected to for at most the time that the remote server is given to answer a request to resume."));
            socketUtil.verify(() -> SocketUtil.createSocketToXmppDomain(eq(DOMAIN_PAIR.getRemote()), eq(5269), eq(SocketUtil.RESOLUTION_TIMEOUT.getValue()), any()), times(1).description("The remote domain must be connected to on the port for that domain."));
            socketUtil.verify(() -> SocketUtil.locationAddressFilter(PEER_ADDRESS), times(1).description("The addresses of the location must be filtered by the address of the original connection to the peer."));
        }
    }

    /**
     * Verifies that only the hosts of the remote domain are connected to when the peer named no location.
     */
    @Test
    public void connectsToRemoteDomainOnlyWithoutLocation()
    {
        // Setup test fixture.
        final NettySessionInitializer initialiser = new NettySessionInitializer(DOMAIN_PAIR, 5269, resumeTarget(), null);
        try (final MockedStatic<SocketUtil> socketUtil = mockStatic(SocketUtil.class))
        {
            // Execute system under test & Verify result.
            assertThrows(NetworkEntityUnreachableException.class, () -> initialiser.init(mock(ConnectionListener.class)), "When the remote domain cannot be connected to, the initialiser must report that it is unreachable.");
            socketUtil.verify(() -> SocketUtil.createSocketToLocation(any(), any(), anyInt(), any(Duration.class), any()), never().description("No location must be connected to when the peer named none."));
            socketUtil.verify(() -> SocketUtil.createSocketToXmppDomain(eq(DOMAIN_PAIR.getRemote()), eq(5269), eq(SocketUtil.RESOLUTION_TIMEOUT.getValue()), any()), times(1).description("The remote domain must be connected to."));
        }
    }

    /**
     * Verifies that the hosts of the remote domain are connected to for at most the time that the detached session of
     * which the stream is to be resumed is still kept, when that is shorter than the usual time to resolve and connect,
     * so that an attempt does not run on after the session is to be ended.
     */
    @Test
    public void connectsToRemoteDomainForAtMostTheTimeThatTheSessionIsStillKept()
    {
        // Setup test fixture.
        final Duration timeLeft = Duration.ofSeconds(30);
        final LocalOutgoingServerSession target = resumeTarget();
        doReturn(timeLeft).when(target).getTimeLeftWhileDetached();
        final NettySessionInitializer initialiser = new NettySessionInitializer(DOMAIN_PAIR, 5269, target, null);
        try (final MockedStatic<SocketUtil> socketUtil = mockStatic(SocketUtil.class))
        {
            // Execute system under test & Verify result.
            assertThrows(NetworkEntityUnreachableException.class, () -> initialiser.init(mock(ConnectionListener.class)), "When the remote domain cannot be connected to, the initialiser must report that it is unreachable.");
            assertTrue(timeLeft.compareTo(SocketUtil.RESOLUTION_TIMEOUT.getValue()) < 0, "Test fixture expects the time left to be shorter than the usual time to resolve and connect.");
            socketUtil.verify(() -> SocketUtil.createSocketToXmppDomain(eq(DOMAIN_PAIR.getRemote()), eq(5269), eq(timeLeft), any()), times(1).description("The remote domain must be connected to for at most the time that the session is still kept."));
        }
    }

    /**
     * Verifies that the hosts of the remote domain are not connected to when the thread is interrupted while it connects
     * to the location, so that an interrupted thread does not block on another attempt to connect.
     */
    @Test
    public void doesNotConnectToRemoteDomainWhenInterruptedWhileConnectingToLocation()
    {
        // Setup test fixture.
        final NettySessionInitializer initialiser = new NettySessionInitializer(DOMAIN_PAIR, 5269, resumeTarget(), LOCATION);
        try (final MockedStatic<SocketUtil> socketUtil = mockStatic(SocketUtil.class))
        {
            socketUtil.when(() -> SocketUtil.createSocketToLocation(anyString(), anyString(), anyInt(), any(), any())).thenAnswer(invocation -> { Thread.currentThread().interrupt(); return null; });

            // Execute system under test & Verify result.
            assertThrows(NetworkEntityUnreachableException.class, () -> initialiser.init(mock(ConnectionListener.class)), "When interrupted while connecting to the location, the initialiser must report that the remote domain is unreachable.");
            assertTrue(Thread.currentThread().isInterrupted(), "The interrupt must be kept.");
            socketUtil.verify(() -> SocketUtil.createSocketToXmppDomain(anyString(), anyInt(), any(), any()), never().description("The remote domain must not be connected to when the thread was interrupted while connecting to the location."));
        }
    }

    /**
     * Creates a detached session of which the stream is to be resumed, of which the original connection was made to
     * {@link #PEER_ADDRESS}, and that has no limit on the time that it is still kept.
     */
    private static LocalOutgoingServerSession resumeTarget()
    {
        final RequestingStreamManager streamManager = mock(RequestingStreamManager.class);
        doReturn(PEER_ADDRESS).when(streamManager).getPeerAddress();
        final LocalOutgoingServerSession target = mock(LocalOutgoingServerSession.class);
        doReturn(streamManager).when(target).getStreamManager();
        // No limit by default; Mockito would otherwise return Duration.ZERO.
        doReturn(null).when(target).getTimeLeftWhileDetached();
        return target;
    }
}
