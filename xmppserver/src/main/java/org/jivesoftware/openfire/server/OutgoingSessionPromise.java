/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.server;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.spi.RoutingTableImpl;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * An OutgoingSessionPromise provides an asynchronic way for sending packets to remote servers.
 * When looking for a route to a remote server that does not have an existing connection, a session
 * promise is returned.
 *
 * This class will queue packets and process them in another thread. The processing thread will
 * use a pool of thread that will actually do the hard work. The threads in the pool will try
 * to connect to remote servers and deliver the packets. If an error occurred while establishing
 * the connection or sending the packet an error will be returned to the sender of the packet.
 *
 * @author Gaston Dombiak
 */
public class OutgoingSessionPromise implements RoutableChannelHandler {

    private static final Logger Log = LoggerFactory.getLogger(OutgoingSessionPromise.class);

    private static final Interner<String> domainBasedMutex = Interners.newWeakInterner();

    private static OutgoingSessionPromise instance = new OutgoingSessionPromise();

    /**
     * Queue that holds the packets pending to be sent to remote servers.
     */
    private BlockingQueue<Packet> packets = new LinkedBlockingQueue<>(10000);

    /**
     * Pool of threads that will create outgoing sessions to remote servers and send
     * the queued packets.
     */
    private ThreadPoolExecutor threadPool;

    private Map<String, PacketsProcessor> packetsProcessors = new HashMap<>();

    /**
     * Cache (unlimited, never expire) that holds outgoing sessions to remote servers from this server.
     * Key: server domain, Value: nodeID
     */
    private Cache<String, NodeID> serversCache;
    /**
     * Flag that indicates if the process that consumed the queued packets should stop.
     */
    private boolean shutdown = false;
    private RoutingTable routingTable;

    private OutgoingSessionPromise() {
        super();
        init();
    }

    private void init() {
        serversCache = CacheFactory.createCache(RoutingTableImpl.S2S_CACHE_NAME);
        routingTable = XMPPServer.getInstance().getRoutingTable();
        // Create a pool of threads that will process queued packets.
        int maxThreads = JiveGlobals.getIntProperty(ConnectionSettings.Server.QUEUE_MAX_THREADS, 20);
        int queueSize = JiveGlobals.getIntProperty(ConnectionSettings.Server.QUEUE_SIZE, 50);
        if (maxThreads < 10) {
            // Ensure that the max number of threads in the pool is at least 10
            maxThreads = 10;
        }
        threadPool =
                new ThreadPoolExecutor(maxThreads/4, maxThreads, 60, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(queueSize),
                        new ThreadPoolExecutor.CallerRunsPolicy());

        // Start the thread that will consume the queued packets. Each pending packet will
        // be actually processed by a thread of the pool (when available). If an error occurs
        // while creating the remote session or sending the packet then a packet with error 502
        // will be sent to the sender of the packet
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!shutdown) {
                    try {
                        if (threadPool.getActiveCount() < threadPool.getMaximumPoolSize()) {
                            // Wait until a packet is available
                            final Packet packet = packets.take();

                            boolean newProcessor = false;
                            PacketsProcessor packetsProcessor;
                            String domain = packet.getTo().getDomain();
                            synchronized (domainBasedMutex.intern(domain)) {
                                packetsProcessor = packetsProcessors.get(domain);
                                if (packetsProcessor == null) {
                                    packetsProcessor =
                                            new PacketsProcessor(OutgoingSessionPromise.this, domain);
                                    packetsProcessors.put(domain, packetsProcessor);
                                    newProcessor = true;
                                }
                                packetsProcessor.addPacket(packet);
                            }

                            if (newProcessor) {
                                // Process the packet in another thread
                                threadPool.execute(packetsProcessor);
                            }
                        }
                        else {
                            // No threads are available so take a nap :)
                            Thread.sleep(200);
                        }
                    }
                    catch (InterruptedException e) {
                        // Do nothing
                    }
                    catch (Exception e) {
                        Log.error(e.getMessage(), e);
                    }
                }
            }
        }, "Queued Packets Processor");
        thread.setDaemon(true);
        thread.start();

    }

    public static OutgoingSessionPromise getInstance() {
        return instance;
    }

    /**
     * Shuts down the thread that consumes the queued packets and also stops the pool
     * of threads that actually send the packets to the remote servers.
     */
    public void shutdown() {
        threadPool.shutdown();
        shutdown = true;
    }

    @Override
    public JID getAddress() {
        // TODO Will somebody send this message to me????
        return null;
    }

    @Override
    public void process(Packet packet) {
        // Queue the packet. Another process will process the queued packets.
        packets.add(packet.createCopy());
    }

    private void processorDone(PacketsProcessor packetsProcessor) {
        synchronized(domainBasedMutex.intern(packetsProcessor.getDomain())) {
            if (packetsProcessor.isDone()) {
                packetsProcessors.remove(packetsProcessor.getDomain());
            }
            else {
                threadPool.execute(packetsProcessor);
            }
        }
    }

    private class PacketsProcessor implements Runnable
    {
        private final Logger Log = LoggerFactory.getLogger( PacketsProcessor.class );

        private OutgoingSessionPromise promise;
        private String domain;
        private Queue<Packet> packetQueue = new ArrayBlockingQueue<>( JiveGlobals.getIntProperty(ConnectionSettings.Server.QUEUE_SIZE, 50) );

        /**
         * Keep track of the last time s2s failed. Once a packet failed to be sent to a
         * remote server this stamp will be used so that for the next 5 seconds future packets
         * for the same domain will automatically fail. After 5 seconds a new attempt to
         * establish a s2s connection and deliver pendings packets will be performed.
         * This optimization is good when the server is receiving many packets per second for the
         * same domain. This will help reduce high CPU consumption.
         */
        private long failureTimestamp = -1;

        public PacketsProcessor(OutgoingSessionPromise promise, String domain) {
            this.promise = promise;
            this.domain = domain;
        }

        @Override
        public void run() {
            while (!isDone()) {
                Packet packet = packetQueue.poll();
                if (packet != null) {
                    // Check if s2s already failed
                    if (failureTimestamp > 0) {
                        // Check if enough time has passed to attempt a new s2s
                        if (System.currentTimeMillis() - failureTimestamp < 5000) {
                            returnErrorToSender(packet);
                            Log.debug( "Error sending packet to domain '{}' (fast discard): {}", domain, packet );
                            continue;
                        }
                        else {
                            // Reset timestamp of last failure since we are ready to try again doing a s2s
                            failureTimestamp = -1;
                        }
                    }
                    try {
                        sendPacket(packet);
                    }
                    catch (Exception e) {
                        returnErrorToSender(packet);
                        Log.debug( "Error sending packet to domain '{}': {}", domain, packet, e );
                        // Mark the time when s2s failed
                        failureTimestamp = System.currentTimeMillis();
                    }
                }
            }
            promise.processorDone(this);
        }

        private void sendPacket(Packet packet) throws Exception {
            // Create a connection to the remote server from the domain where the packet has been sent
            boolean created;
            // Make sure that only one cluster node is creating the outgoing connection
            // TODO: Evaluate why removing the oss part causes nasty s2s and lockup issues.
            Lock lock = serversCache.getLock(domain+"oss");
            lock.lock();
            try {
                created = LocalOutgoingServerSession
                        .authenticateDomain(packet.getFrom().getDomain(), packet.getTo().getDomain());
            } finally {
                lock.unlock();
            }
            if (created) {
                if (!routingTable.hasServerRoute(new DomainPair(packet.getFrom().getDomain(), packet.getTo().getDomain()))) {
                    throw new Exception("Route created but not found!!!");
                }
                // A connection to the remote server was created so get the route and send the packet
                routingTable.routePacket(packet.getTo(), packet, false);
            }
            else {
                throw new Exception("Failed to create connection to remote server");
            }
        }

        private void returnErrorToSender(Packet packet) {
            XMPPServer server = XMPPServer.getInstance();
            JID from = packet.getFrom();
            JID to = packet.getTo();
            if (!server.isLocal(from) && !XMPPServer.getInstance().matchesComponent(from) &&
                    !server.isLocal(to) && !XMPPServer.getInstance().matchesComponent(to)) {
                // Do nothing since the sender and receiver of the packet that failed to reach a remote
                // server are not local users. This prevents endless loops if the FROM or TO address
                // are non-existent addresses
                return;
            }

            final Set<Packet> replies = new HashSet<>();

            // TODO Send correct error condition: timeout or not_found depending on the real error
            try {
                if (packet instanceof IQ) {
                    IQ reply = new IQ();
                    reply.setID(packet.getID());
                    reply.setTo(from);
                    reply.setFrom(to);
                    reply.setChildElement(((IQ) packet).getChildElement().createCopy());
                    reply.setError(PacketError.Condition.remote_server_not_found);

                    replies.add( reply );
                }
                else if (packet instanceof Presence) {
                    // workaround for OF-23. "undo" the 'setFrom' to a bare JID 
                    // by sending the error to all available resources.
                    final List<JID> routes = new ArrayList<>();
                    if (from.getResource() == null || from.getResource().trim().length() == 0) {
                        routes.addAll(routingTable.getRoutes(from, null));
                    } else {
                        routes.add(from);
                    }
                    
                    for (JID route : routes) {
                        Presence reply = new Presence();
                        reply.setID(packet.getID());
                        reply.setTo(route);
                        reply.setFrom(to);
                        reply.setError(PacketError.Condition.remote_server_not_found);

                        replies.add( reply );
                    }
                }
                else if (packet instanceof Message) {
                    Message reply = new Message();
                    reply.setID(packet.getID());
                    reply.setTo(from);
                    reply.setFrom(to);
                    reply.setType(((Message)packet).getType());
                    reply.setThread(((Message)packet).getThread());
                    reply.setError(PacketError.Condition.remote_server_not_found);

                    replies.add( reply );
                }

                // Send all replies.
                final SessionManager sessionManager = SessionManager.getInstance();
                for ( final Packet reply : replies )
                {
                    try
                    {
                        final ClientSession session = sessionManager.getSession( reply.getTo() );
                        InterceptorManager.getInstance().invokeInterceptors( reply, session, false, false );
                        routingTable.routePacket( reply.getTo(), reply, true );
                        InterceptorManager.getInstance().invokeInterceptors( reply, session, false, true );
                    }
                    catch ( PacketRejectedException ex )
                    {
                        Log.debug( "Reply got rejected by an interceptor: {}", reply, ex );
                    }
                }
            }
            catch (Exception e) {
                Log.warn( "An exception occurred while trying to returning a remote-server-not-found error (for domain '{}') to the original sender. Original packet: {}", domain, packet, e );
            }
        }

        void addPacket( Packet packet )
        {
            if ( !packetQueue.offer( packet ) )
            {
                returnErrorToSender(packet);
                Log.debug( "Error sending packet to domain '{}' (outbound queue full): {}", domain, packet );
            }
        }

        public String getDomain() {
            return domain;
        }

        public boolean isDone() {
            return packetQueue.isEmpty();
        }
    }
}
