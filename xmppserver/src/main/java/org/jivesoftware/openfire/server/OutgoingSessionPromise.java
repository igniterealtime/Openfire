/*
 * Copyright (C) 2005-2008 Jive Software, 2015-2021 Ignite Realtime Foundation. All rights reserved.
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

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
import org.jivesoftware.util.SystemProperty;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
 * @author Gaston Dombiak, Dave Cridland, Guus der Kinderen
 */
public class OutgoingSessionPromise {

    private static final Logger Log = LoggerFactory.getLogger(OutgoingSessionPromise.class);

    public static final SystemProperty<Integer> QUEUE_MAX_THREADS = SystemProperty.Builder.ofType(Integer.class)
        .setKey(ConnectionSettings.Server.QUEUE_MAX_THREADS)
        .setDynamic(false)
        .setDefaultValue(20)
        .setMinValue(0) // RejectedExecutionHandler is CallerRunsPolicy, meaning that the calling thread would execute the task.
        .build();

    public static final SystemProperty<Integer> QUEUE_MIN_THREADS = SystemProperty.Builder.ofType(Integer.class)
        .setKey(ConnectionSettings.Server.QUEUE_MIN_THREADS)
        .setDynamic(false)
        .setDefaultValue(0)
        .setMinValue(0)
        .build();

    public static final SystemProperty<Integer> QUEUE_SIZE = SystemProperty.Builder.ofType(Integer.class)
        .setKey(ConnectionSettings.Server.QUEUE_SIZE)
        .setDynamic(false)
        .setDefaultValue(50)
        .setMinValue(0)
        .build();

    public static final SystemProperty<Duration> QUEUE_THREAD_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.server.outgoing.threads-timeout")
        .setDynamic(false)
        .setDefaultValue(Duration.ofSeconds(60))
        .setChronoUnit(ChronoUnit.MILLIS)
        .setMinValue(Duration.ZERO)
        .build();

    public static final SystemProperty<Duration> FAST_DISCARD_DURATION = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.server.outgoing.fastdiscard.duration")
        .setDynamic(true)
        .setDefaultValue(Duration.ofSeconds(5))
        .setChronoUnit(ChronoUnit.MILLIS)
        .setMinValue(Duration.ZERO)
        .build();

    private static final OutgoingSessionPromise instance = new OutgoingSessionPromise();

    /**
     * Pool of threads that will create outgoing sessions to remote servers and send
     * the queued packets.
     */
    private ThreadPoolExecutor threadPool;

    private final ConcurrentMap<DomainPair, PacketsProcessor> packetsProcessors = new ConcurrentHashMap<>();

    /**
     * Cache (unlimited, never expire) that holds outgoing sessions to remote servers from this server.
     * Key: server domain, Value: nodeID
     */
    private Cache<DomainPair, NodeID> serversCache;

    private RoutingTable routingTable;

    private OutgoingSessionPromise() {
        super();
        init();
    }

    private void init() {
        serversCache = CacheFactory.createCache(RoutingTableImpl.S2S_CACHE_NAME);
        routingTable = XMPPServer.getInstance().getRoutingTable();

        // Create a pool of threads that will process queued packets.
        threadPool = new ThreadPoolExecutor(QUEUE_MIN_THREADS.getValue(), QUEUE_MAX_THREADS.getValue(),
                        QUEUE_THREAD_TIMEOUT.getValue().toMillis(), TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(QUEUE_SIZE.getValue()),
                        new ThreadPoolExecutor.CallerRunsPolicy());
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
    }

    public void process(@Nonnull final Packet packet) {
        final DomainPair domainPair = new DomainPair(packet.getFrom().getDomain(), packet.getTo().getDomain());
        final PacketsProcessor newProc = new PacketsProcessor(OutgoingSessionPromise.this, domainPair);
        final PacketsProcessor oldProc = packetsProcessors.putIfAbsent(domainPair, newProc);
        if (oldProc == null) {
            // There's not a processor for this packet yet.
            newProc.addPacket(packet);
            threadPool.execute(newProc);
        } else {
            // There's already a processor for this packet. Add the packet to that one.
            oldProc.addPacket(packet);
        }
    }

    private void processorDone(@Nonnull final DomainPair domainPair)
    {
        final PacketsProcessor processor = packetsProcessors.computeIfPresent(
            domainPair,
            (pair, preExistingProcessor) -> preExistingProcessor.isDone() ? null : preExistingProcessor
        );
        if (processor != null) {
            // Not done yet. Re-schedule
            threadPool.execute(processor);
        }
    }

    /**
     * Checks if an outgoing session is in process of being created, which includes both establishment of the (possibly
     * authenticated) connection as well as delivery of all queued stanzas.
     *
     * @param domainPair The connection to check.
     * @return true if an outgoing session is currently being created, otherwise false.
     */
    public boolean isPending(@Nonnull final DomainPair domainPair) {
        final PacketsProcessor processor = packetsProcessors.get(domainPair);
        return processor != null && !processor.isDone();
    }

    private class PacketsProcessor implements Runnable
    {
        private final Logger Log = LoggerFactory.getLogger( PacketsProcessor.class );

        @Nonnull
        private final OutgoingSessionPromise promise;

        @Nonnull
        private final DomainPair domainPair;

        @Nonnull
        private final Queue<Packet> packetQueue = new ArrayBlockingQueue<>( JiveGlobals.getIntProperty(ConnectionSettings.Server.QUEUE_SIZE, 50) );

        /**
         * Keep track of the last time s2s failed. Once a packet failed to be sent to a
         * remote server this stamp will be used so that for the next few moments future packets
         * for the same domain will automatically fail. After this timeout, a new attempt to
         * establish a s2s connection and deliver pending packets will be performed.
         * This optimization is good when the server is receiving many packets per second for the
         * same domain. This will help reduce high CPU consumption.
         */
        @Nullable
        private Instant failureTimestamp = null;

        public PacketsProcessor(@Nonnull final OutgoingSessionPromise promise, @Nonnull final DomainPair domainPair) {
            this.promise = promise;
            this.domainPair = domainPair;
        }

        @Override
        public void run() {
            while (!isDone()) {
                Packet packet = packetQueue.poll();
                if (packet != null) {
                    // Check if s2s already failed
                    if (failureTimestamp != null) {
                        // Check if enough time has passed to attempt a new s2s
                        if (Duration.between(failureTimestamp, Instant.now()).compareTo(FAST_DISCARD_DURATION.getValue()) < 0) {
                            Log.debug( "Error sending packet to domain '{}' (fast discard): {}", domainPair.getRemote(), packet );
                            returnErrorToSender(packet);
                            continue;
                        }
                        else {
                            // Reset timestamp of last failure since we are ready to try again doing a s2s
                            failureTimestamp = null;
                        }
                    }
                    try {
                        establishConnection();
                        do {
                            // A connection to the remote server was created so get the route and purge the packet queue
                            routingTable.routePacket(packet.getTo(), packet, false);
                        } while ((packet = packetQueue.poll()) != null);
                    }
                    catch (Exception e) {
                        // Mark the time when s2s failed
                        failureTimestamp = Instant.now();
                        Log.debug( "Error sending packet to domain '{}': {}", domainPair.getRemote(), packet, e );
                        returnErrorToSender(packet);
                    }
                }
            }
            promise.processorDone(domainPair);
        }

        private void establishConnection() throws Exception {
            // Create a connection to the remote server from the domain where the packet has been sent
            boolean created;
            // Make sure that only one cluster node is creating the outgoing connection
            final Lock lock = serversCache.getLock(domainPair);
            lock.lock();
            try {
                created = LocalOutgoingServerSession.authenticateDomain(domainPair);
            } finally {
                lock.unlock();
            }
            if (created) {
                if (!routingTable.hasServerRoute(domainPair)) {
                    throw new Exception("Route created but not found!!!");
                }
            }
            else {
                throw new Exception("Failed to create connection to remote server");
            }
        }

        /**
         * Processes stanzas that could not be delivered to a remote domain, by generating error responses where
         * appropriate.
         *
         * @param packet The stanza that could not be delivered.
         */
        private void returnErrorToSender(@Nonnull final Packet packet) {
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
                    if (((IQ) packet).isRequest()) {
                        IQ reply = new IQ();
                        reply.setID(packet.getID());
                        reply.setTo(from);
                        reply.setFrom(to);
                        reply.setChildElement(((IQ) packet).getChildElement().createCopy());
                        reply.setError(PacketError.Condition.remote_server_not_found);
                        replies.add( reply );
                    }
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
                Log.warn( "An exception occurred while trying to returning a remote-server-not-found error (for domain '{}') to the original sender. Original packet: {}", domainPair.getRemote(), packet, e );
            }
        }

        void addPacket( @Nonnull final Packet packet )
        {
            if (!packet.getFrom().getDomain().equals(domainPair.getLocal())) {
                throw new IllegalArgumentException("Cannot queue packet from sender '" + packet.getFrom() + "' in the outgoing session promise for " + domainPair + ". Local domain does not match!");
            }
            if (!packet.getTo().getDomain().equals(domainPair.getRemote())) {
                throw new IllegalArgumentException("Cannot queue packet to intended recipient '" + packet.getTo() + "' in the outgoing session promise to domain " + domainPair + ". Remote domain does not match!");
            }
            if (!packetQueue.offer(packet))
            {
                Log.debug("Error sending packet in the outgoing session promise for {}. (outbound queue full): {}", domainPair, packet);
                returnErrorToSender(packet);
            }
        }

        @Nonnull
        public DomainPair getDomainPair() {
            return domainPair;
        }

        public boolean isDone() {
            return packetQueue.isEmpty();
        }
    }
}
