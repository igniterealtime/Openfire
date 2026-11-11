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

import org.dom4j.DocumentHelper;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RemotePacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.cluster.RemotePacketExecution;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.jivesoftware.openfire.streammanagement.UnacknowledgedStanzas;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies that stanzas for the domain pairs of an outgoing server session that is detached, while its stream can be
 * resumed, reach that session from every cluster node, and do not cause a second outgoing connection to be started
 * (decision 7 of OF-2425).
 *
 * The cluster is simulated by two routing tables that share their caches, as cluster nodes do, but not the sessions that
 * they hold. The routing table of the other cluster node passes a stanza for a route on this node to this node by running
 * the cluster task that does so on this node, as a cluster would.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RoutingTableImplDetachedServerRouteTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final DomainPair DOMAIN_PAIR = new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN);

    private static final JID SENDER = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource");

    private static final JID RECIPIENT = new JID("janedoe", REMOTE_DOMAIN, "remote-resource");

    private static final NodeID THIS_NODE = NodeID.getInstance("this-node".getBytes(StandardCharsets.UTF_8));

    private static final NodeID OTHER_NODE = NodeID.getInstance("other-node".getBytes(StandardCharsets.UTF_8));

    private RoutingTableImpl routingTable;

    private RoutingTableImpl otherNodeRoutingTable;

    /**
     * The cluster nodes to which the other cluster node passed a stanza, in order.
     */
    private final List<NodeID> passedToNodes = new CopyOnWriteArrayList<>();

    private LocalOutgoingServerSession session;

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
        StreamManager.SERVER_ACTIVE.setValue(true);
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);

        final XMPPServer thisServer = Fixtures.mockXMPPServer();
        doReturn(THIS_NODE).when(thisServer).getNodeID();
        doReturn(mock(PacketRouter.class, withSettings().lenient())).when(thisServer).getPacketRouter();
        //noinspection deprecation
        XMPPServer.setInstance(thisServer);
        routingTable = new RoutingTableImpl();
        doReturn(routingTable).when(thisServer).getRoutingTable();
        routingTable.initialize(thisServer);
        final SessionManager sessionManager = new SessionManager();
        sessionManager.initialize(thisServer);
        doReturn(sessionManager).when(thisServer).getSessionManager();

        final XMPPServer otherServer = Fixtures.mockXMPPServer();
        doReturn(OTHER_NODE).when(otherServer).getNodeID();
        otherNodeRoutingTable = new RoutingTableImpl();
        otherNodeRoutingTable.initialize(otherServer);
        otherNodeRoutingTable.setRemotePacketRouter(new RemotePacketRouter() {
            @Override
            public boolean routePacket(final byte[] nodeID, final JID recipient, final Packet packet) {
                passedToNodes.add(NodeID.getInstance(nodeID));
                // The cluster task runs on the node that holds the route, with a copy of the stanza that it received.
                new RemotePacketExecution(recipient, packet.createCopy()).run();
                return true;
            }

            @Override
            public void broadcastPacket(final Message packet) {}
        });

        session = detachedSession();
    }

    @AfterEach
    public void tearDown()
    {
        CacheFactory.createCache(RoutingTableImpl.S2S_CACHE_NAME).clear();
        ClusterManager.removeListener(routingTable);
        ClusterManager.removeListener(otherNodeRoutingTable);
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that a stanza that is routed on the cluster node that holds a detached session for its domain pair is
     * queued on that session, and does not start a new outgoing connection.
     */
    @Test
    public void stanzaRoutedOnNodeThatHoldsDetachedSessionIsQueuedOnIt()
    {
        // Setup test fixture.
        final Message message = message("on-this-node");

        // Execute system under test.
        route(routingTable, message);

        // Verify result.
        assertEquals(List.of("on-this-node"), UnacknowledgedStanzas.queuedIds(session.getStreamManager()), "The stanza must be queued on the detached session that holds the route for its domain pair.");
        assertFalse(OutgoingSessionPromise.getInstance().hasProcess(DOMAIN_PAIR), "No new outgoing connection must be started for the domain pair of a detached session.");
        assertSame(session, routingTable.getServerRoute(DOMAIN_PAIR), "The detached session must stay the route for its domain pair.");
    }

    /**
     * Verifies that a stanza that is routed on another cluster node is passed to the node that holds a detached session
     * for its domain pair, where it is queued on that session, and that no new outgoing connection is started.
     */
    @Test
    public void stanzaRoutedOnOtherNodeIsQueuedOnDetachedSessionOnNodeThatHoldsIt()
    {
        // Setup test fixture.
        final Message message = message("on-other-node");

        // Execute system under test.
        route(otherNodeRoutingTable, message);

        // Verify result.
        assertEquals(List.of(THIS_NODE), passedToNodes, "The other cluster node must pass the stanza to the node that holds the detached session, once.");
        assertEquals(List.of("on-other-node"), UnacknowledgedStanzas.queuedIds(session.getStreamManager()), "The stanza must be queued on the detached session that holds the route for its domain pair.");
        assertFalse(OutgoingSessionPromise.getInstance().hasProcess(DOMAIN_PAIR), "No new outgoing connection must be started for the domain pair of a detached session.");
    }

    /**
     * Verifies that stanzas that are routed on different cluster nodes are queued on the detached session in the order
     * in which they were routed.
     */
    @Test
    public void stanzasRoutedOnBothNodesAreQueuedInOrder()
    {
        // Setup test fixture.
        final Message first = message("first");
        final Message second = message("second");
        final Message third = message("third");

        // Execute system under test.
        route(routingTable, first);
        route(otherNodeRoutingTable, second);
        route(routingTable, third);

        // Verify result.
        assertEquals(List.of("first", "second", "third"), UnacknowledgedStanzas.queuedIds(session.getStreamManager()), "Stanzas routed on either cluster node must be queued on the detached session in the order in which they were routed.");
        assertFalse(OutgoingSessionPromise.getInstance().hasProcess(DOMAIN_PAIR), "No new outgoing connection must be started for the domain pair of a detached session.");
    }

    /**
     * Routes a stanza to its recipient through the provided routing table, with access to the remote domain allowed.
     */
    private static void route(final RoutingTableImpl table, final Packet packet)
    {
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class)) {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            table.routePacket(packet.getTo(), packet);
        }
    }

    /**
     * Creates an authenticated outgoing session on this cluster node that is the route for {@link #DOMAIN_PAIR}, on
     * which the remote server enabled Stream Management with a stream that can be resumed, and detaches it, as when its
     * connection broke.
     */
    private LocalOutgoingServerSession detachedSession()
    {
        final Connection connection = mock(Connection.class, withSettings().lenient());
        doReturn(new CompletableFuture<Void>()).when(connection).getCloseFuture();
        final LocalOutgoingServerSession result = new LocalOutgoingServerSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID("stream-detached"));
        result.setAddress(new JID(null, REMOTE_DOMAIN, null));
        result.setAuthenticationMethod(ServerSession.AuthenticationMethod.SASL_EXTERNAL);
        result.addOutgoingDomainPair(DOMAIN_PAIR);
        result.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);
        result.getStreamManager().process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)).addAttribute("resume", "true").addAttribute("id", "sm-id-1"));
        assertTrue(result.getStreamManager().getResume(), "Test fixture expects the stream to be resumable.");

        doReturn(true).when(connection).isClosed();
        result.setDetached();
        assertTrue(result.isDetached(), "Test fixture expects the session to be detached.");
        assertTrue(otherNodeRoutingTable.getServerRoutes().contains(DOMAIN_PAIR), "Test fixture expects the route of the detached session to be visible to the other cluster node.");
        return result;
    }

    private static Message message(final String id)
    {
        final Message message = new Message();
        message.setID(id);
        message.setType(Message.Type.chat);
        message.setFrom(SENDER);
        message.setTo(RECIPIENT);
        return message;
    }
}
