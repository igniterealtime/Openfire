/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.OutgoingServerSession;
import org.jivesoftware.openfire.spi.RoutingTableImpl;
import org.jivesoftware.util.NamedThreadFactory;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

/**
 * An OutgoingSessionPromise provides an asynchronous way for sending packets to remote servers.
 * When looking for a route to a remote server that does not have an existing connection, a session
 * promise is returned.<p>
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
        .setDefaultValue(2000)
        .setMinValue(0)
        .build();

    public static final SystemProperty<Duration> QUEUE_THREAD_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.server.outgoing.threads-timeout")
        .setDynamic(false)
        .setDefaultValue(Duration.ofSeconds(60))
        .setChronoUnit(ChronoUnit.MILLIS)
        .setMinValue(Duration.ZERO)
        .build();

    private static final OutgoingSessionPromise instance = new OutgoingSessionPromise();

    private final Interner<DomainPair> interner = Interners.newWeakInterner();

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
                        new SynchronousQueue<>(),
                        new NamedThreadFactory("S2SOutgoingPromise-", Executors.defaultThreadFactory(), false, Thread.NORM_PRIORITY),
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

    /**
     * Start a new process to establish a new outgoing connection, queuing the stanza that's addressed to the remote
     * domain for delivery after that has occurred.
     *
     * Only one process can exist for any given domainPair. This method will throw an exception when it is invoked for
     * a DomainPair for which a process already exists.
     *
     * The returned value of {@link #hasProcess(DomainPair)} can be used to determine if a process exists. The
     * invocations of that method and this method should be guarded by the mutex returned by {@link #getMutex(DomainPair)}
     * to ensure thread safety, as holding that mutex will prevent other threads from starting a new process.
     *
     * @param domainPair The domainPair that describes the local and remote domain between which the stanza is to be sent
     * @param packet the stanza to send to the remote domain (addressing must correspond with the domainPair argument)
     * @throws IllegalStateException when a process already exists for this domainPair
     */
    public void createProcess(@Nonnull final DomainPair domainPair, @Nonnull final Packet packet)
    {
        if (!packet.getFrom().getDomain().equals(domainPair.getLocal())) {
            throw new IllegalArgumentException("Packet's 'from' domain ("+packet.getFrom().getDomain()+") does not match domainPair's local ("+domainPair.getLocal()+")");
        }
        if (!packet.getTo().getDomain().equals(domainPair.getRemote())) {
            throw new IllegalArgumentException("Packet's 'to' domain ("+packet.getTo().getDomain()+") does not match domainPair's remote ("+domainPair.getRemote()+")");
        }

        final PacketsProcessor packetsProcessor = new PacketsProcessor(domainPair);
        if (packetsProcessors.putIfAbsent(domainPair, packetsProcessor) != null) {
            throw new IllegalStateException("Attempted to create a new PacketProcessor for " + domainPair + " but one already exists.");
        } else {
            Log.debug("Created new PacketProcessor for {}", domainPair);
            packetsProcessor.addPacket(packet);
            threadPool.execute(packetsProcessor);
        }
    }

    /**
     * Queues a stanza for delivery to a remote domain after an ongoing process to establish that connection has finished.
     *
     * A process for the target DomainPair must exist. This method will throw an exception when it does not.
     *
     * The returned value of {@link #hasProcess(DomainPair)} can be used to determine if a process exists. The
     * invocations of that method and this method should be guarded by the mutex returned by {@link #getMutex(DomainPair)}
     * to ensure thread safety, as holding that mutex will prevent other threads to finish the running process.
     *
     * @param domainPair The domainPair that describes the local and remote domain between which the stanza is to be sent
     * @param packet the stanza to send to the remote domain (addressing must correspond with the domainPair argument)
     * @throws IllegalStateException when a process does not exist for the domainPair
     */
    public void queue(@Nonnull final DomainPair domainPair, @Nonnull final Packet packet)
    {
        if (!packet.getFrom().getDomain().equals(domainPair.getLocal())) {
            throw new IllegalArgumentException("Packet's 'from' domain ("+packet.getFrom().getDomain()+") does not match domainPair's local ("+domainPair.getLocal()+")");
        }
        if (!packet.getTo().getDomain().equals(domainPair.getRemote())) {
            throw new IllegalArgumentException("Packet's 'to' domain ("+packet.getTo().getDomain()+") does not match domainPair's remote ("+domainPair.getRemote()+")");
        }
        final PacketsProcessor processor = packetsProcessors.get(domainPair);
        if (processor == null) {
            throw new IllegalStateException("Attempt to queue stanza for " + domainPair + " while no processor exists for that domain pair.");
        }
        Log.trace("Queuing stanza for {}", domainPair);
        processor.addPacket(packet);
    }

    /**
     * Generates an object that is suitable as a mutex for operations that involve the provided DomainPair instance.
     *
     * @param domainPair The DomainPair for which the mutex is issued
     * @return An object suitable to be a mutex.
     */
    public Object getMutex(@Nonnull final DomainPair domainPair) {
        return interner.intern(domainPair);
    }

    /**
     * Checks if an outgoing session is in process of being created, which includes both establishment of the (possibly
     * authenticated) connection as well as delivery of all queued stanzas.
     *
     * @param domainPair The connection to check.
     * @return true if an outgoing session is currently being created, otherwise false.
     */
    public boolean hasProcess(@Nonnull final DomainPair domainPair) {
        final PacketsProcessor processor = packetsProcessors.get(domainPair);
        return processor != null && !processor.isDone();
    }

    private class PacketsProcessor implements Runnable
    {
        private final Logger Log = LoggerFactory.getLogger( PacketsProcessor.class );

        @Nonnull
        private final DomainPair domainPair;

        @Nonnull
        private final Queue<Packet> packetQueue = new ArrayBlockingQueue<>( QUEUE_SIZE.getValue() );

        public PacketsProcessor(@Nonnull final DomainPair domainPair) {
            this.domainPair = domainPair;
        }

        @Override
        public void run() {
            Log.debug("Start for {}", domainPair);
            RoutableChannelHandler channel;
            try {
                channel = establishConnection();
            } catch (Exception e) {
                Log.warn("An exception occurred while trying to establish a connection for {}", domainPair, e);
                channel = null;
            }

            // After the connection has been established (or failed), process all queued stanzas. Ensure that no more
            // stanzas are queued while we process the queue, by first synchronizing on the same mutex that should be
            // used to guards #queue(). That will cause to-be-queued stanzas to be sent directly over the now
            // established connection after we've finished processing all queued stanzas below.
            synchronized (getMutex(domainPair)) {
                Log.trace("Purging queue for {}", domainPair);
                Packet packet;
                while ((packet = packetQueue.poll()) != null) {
                    if (channel != null) {
                        // A connection to the remote server was created so get the route and purge the packet queue.
                        try {
                            Log.trace("Routing queued stanza: {}", packet);
                            channel.process(packet);
                        } catch (Exception e) {
                            Log.debug("Error sending packet to domain '{}': {}", domainPair.getRemote(), packet, e);
                            returnErrorToSender(packet);
                        }
                    } else {
                        // A connection to the remote server failed. Return an error for all queued stanzas.
                        Log.trace("Bouncing queued stanza: {}", packet);
                        returnErrorToSender(packet);
                    }
                }

                // Remove the processor to ensure that it cannot accept new stanzas to be queued.
                packetsProcessors.remove(domainPair);
            }
            Log.trace("Finished processing {}", domainPair);
        }

        private RoutableChannelHandler establishConnection() throws Exception {
            Log.debug("Start establishing a connection for {}", domainPair);
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
                final OutgoingServerSession serverRoute = routingTable.getServerRoute(domainPair);
                if (serverRoute == null || !(serverRoute instanceof LocalOutgoingServerSession)) {
                    throw new Exception("Route created but not found!!!");
                } else {
                    return serverRoute;
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
            }
            catch (Exception e)
            {
                Log.warn( "An exception occurred while trying to generate a remote-server-not-found error (for domain '{}') to the original sender. Original packet: {}", domainPair.getRemote(), packet, e );
            }

            // Send all replies.
            for ( final Packet reply : replies )
            {
                TaskEngine.getInstance().submit(() -> {
                    try
                    {
                        XMPPServer.getInstance().getPacketRouter().route( reply );
                    }
                    catch (Exception e)
                    {
                        Log.warn( "An exception occurred while trying to returning a remote-server-not-found error (for domain '{}') to the original sender. Original packet: {}", domainPair.getRemote(), packet, e );
                    }
                });
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
            Log.trace("Queuing stanza to intended recipient '{}' in the outgoing session promise to domain '{}': {}", packet.getTo(), domainPair, packet.toXML());

            // When queuing for async processing, ensure that the queued stanza is not modified by reference, by queuing
            // a defensive copy rather than the original. Modifications of the original can be expected in broadcast-like
            // scenarios (eg: MUC) where the same stanza is re-addressed for each intended recipient. See OF-2344.
            if (!packetQueue.offer(packet.createCopy()))
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
