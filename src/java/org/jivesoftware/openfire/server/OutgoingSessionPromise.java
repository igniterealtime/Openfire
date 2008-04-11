/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.server;

import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.spi.RoutingTableImpl;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

/**
 * An OutgoingSessionPromise provides an asynchronic way for sending packets to remote servers.
 * When looking for a route to a remote server that does not have an existing connection, a session
 * promise is returned.
 *
 * This class will queue packets and process them in another thread. The processing thread will
 * use a pool of thread that will actually do the hard work. The threads in the pool will try
 * to connect to remote servers and deliver the packets. If an error occured while establishing
 * the connection or sending the packet an error will be returned to the sender of the packet.
 *
 * @author Gaston Dombiak
 */
public class OutgoingSessionPromise implements RoutableChannelHandler {

    private static OutgoingSessionPromise instance = new OutgoingSessionPromise();

    /**
     * Queue that holds the packets pending to be sent to remote servers.
     */
    private BlockingQueue<Packet> packets = new LinkedBlockingQueue<Packet>();

    /**
     * Pool of threads that will create outgoing sessions to remote servers and send
     * the queued packets.
     */
    private ThreadPoolExecutor threadPool;

    private Map<String, PacketsProcessor> packetsProcessors = new HashMap<String, PacketsProcessor>();

    /**
     * Cache (unlimited, never expire) that holds outgoing sessions to remote servers from this server.
     * Key: server domain, Value: nodeID
     */
    private Cache<String, byte[]> serversCache;
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
        int maxThreads = JiveGlobals.getIntProperty("xmpp.server.outgoing.max.threads", 20);
        int queueSize = JiveGlobals.getIntProperty("xmpp.server.outgoing.queue", 50);
        if (maxThreads < 10) {
            // Ensure that the max number of threads in the pool is at least 10
            maxThreads = 10;
        }
        threadPool =
                new ThreadPoolExecutor(Math.round(maxThreads/4), maxThreads, 60, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(queueSize),
                        new ThreadPoolExecutor.CallerRunsPolicy());

        // Start the thread that will consume the queued packets. Each pending packet will
        // be actually processed by a thread of the pool (when available). If an error occurs
        // while creating the remote session or sending the packet then a packet with error 502
        // will be sent to the sender of the packet
        Thread thread = new Thread(new Runnable() {
            public void run() {
                while (!shutdown) {
                    try {
                        if (threadPool.getActiveCount() < threadPool.getMaximumPoolSize()) {
                            // Wait until a packet is available
                            final Packet packet = packets.take();

                            boolean newProcessor = false;
                            PacketsProcessor packetsProcessor;
                            String domain = packet.getTo().getDomain();
                            synchronized (domain.intern()) {
                                packetsProcessor = packetsProcessors.get(domain);
                                if (packetsProcessor == null) {
                                    packetsProcessor =
                                            new PacketsProcessor(OutgoingSessionPromise.this, domain, routingTable);
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
                        Log.error(e);
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

    public JID getAddress() {
        // TODO Will somebody send this message to me????
        return null;
    }

    public void process(Packet packet) {
        // Queue the packet. Another process will process the queued packets.
        packets.add(packet.createCopy());
    }

    private void processorDone(PacketsProcessor packetsProcessor) {
        synchronized(packetsProcessor.getDomain().intern()) {
            if (packetsProcessor.isDone()) {
                packetsProcessors.remove(packetsProcessor.getDomain());
            }
            else {
                threadPool.execute(packetsProcessor);
            }
        }
    }

    private class PacketsProcessor implements Runnable {

        private OutgoingSessionPromise promise;
        private String domain;
        private RoutingTable routingTable;
        private Queue<Packet> packets = new ConcurrentLinkedQueue<Packet>();

        public PacketsProcessor(OutgoingSessionPromise promise, String domain, RoutingTable routingTable) {
            this.promise = promise;
            this.domain = domain;
            this.routingTable = routingTable;
        }

        public void run() {
            while (!isDone()) {
                Packet packet = packets.poll();
                if (packet != null) {
                    try {
                        sendPacket(packet);
                    }
                    catch (Exception e) {
                        returnErrorToSender(packet);
                        Log.debug(
                                "OutgoingSessionPromise: Error sending packet to remote server: " + packet,
                                e);
                    }
                }
            }
            promise.processorDone(this);
        }

        private void sendPacket(Packet packet) throws Exception {
            // Create a connection to the remote server from the domain where the packet has been sent
            boolean created;
            // Make sure that only one cluster node is creating the outgoing connection
            Lock lock = CacheFactory.getLock(domain, serversCache);
            try {
                lock.lock();
                created = LocalOutgoingServerSession
                        .authenticateDomain(packet.getFrom().getDomain(), packet.getTo().getDomain());
            } finally {
                lock.unlock();
            }
            if (created) {
                if (!routingTable.hasServerRoute(packet.getTo())) {
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
                // are non-existen addresses
                return;
            }

            // TODO Send correct error condition: timeout or not_found depending on the real error
            try {
                if (packet instanceof IQ) {
                    IQ reply = new IQ();
                    reply.setID(packet.getID());
                    reply.setTo(from);
                    reply.setFrom(to);
                    reply.setChildElement(((IQ) packet).getChildElement().createCopy());
                    reply.setError(PacketError.Condition.remote_server_not_found);
                    routingTable.routePacket(reply.getTo(), reply, true);
                }
                else if (packet instanceof Presence) {
                    Presence reply = new Presence();
                    reply.setID(packet.getID());
                    reply.setTo(from);
                    reply.setFrom(to);
                    reply.setError(PacketError.Condition.remote_server_not_found);
                    routingTable.routePacket(reply.getTo(), reply, true);
                }
                else if (packet instanceof Message) {
                    Message reply = new Message();
                    reply.setID(packet.getID());
                    reply.setTo(from);
                    reply.setFrom(to);
                    reply.setType(((Message)packet).getType());
                    reply.setThread(((Message)packet).getThread());
                    reply.setError(PacketError.Condition.remote_server_not_found);
                    routingTable.routePacket(reply.getTo(), reply, true);
                }
            }
            catch (Exception e) {
                Log.warn("Error returning error to sender. Original packet: " + packet, e);
            }
        }

        public void addPacket(Packet packet) {
            packets.add(packet);
        }

        public String getDomain() {
            return domain;
        }

        public boolean isDone() {
            return packets.isEmpty();
        }
    }
}
