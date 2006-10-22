/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.PacketRouter;
import org.jivesoftware.wildfire.RoutingTable;
import org.jivesoftware.wildfire.Session;
import org.jivesoftware.wildfire.SessionManager;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.multiplex.ConnectionMultiplexerSession;
import org.jivesoftware.wildfire.multiplex.MultiplexerPacketHandler;
import org.jivesoftware.wildfire.multiplex.Route;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.*;

/**
 * A SocketReader specialized for connection manager connections. Connection managers may have
 * one or more connections to the server. Each connection will have its own instance of this
 * class. Each connection will send packets, sent from clients connected to the connection
 * manager, to the server. Moreover, the server will use any of the available connections
 * to the connection manager to send packets to connected clients through the connection manager.<p>
 *
 * Each socket reader has its own thread pool to process many packets in parallel. The thread pool
 * by default will use 10 core threads, a queue of 50 elements and a max number of 100 threads.
 * The pool will use the 10 core threads in parallel and queue packets. When the queue is full
 * then more threads will be created until the max number is reached. Any created thread that
 * exceeds the core number of threads will be killed when idle for 1 minute. The thread pool
 * configuration can be modified by setting the system properties:
 * <ul>
 *  <li>xmpp.multiplex.processing.core.threads
 *  <li>xmpp.multiplex.processing.max.threads
 *  <li>xmpp.multiplex.processing.queue
 * </ul>
 *
 * Each Connection Manager has its own domain. Each connection from the same connection manager
 * uses a different resource. Unlike any other session, connection manager sessions are not
 * present in the routing table. This means that connection managers are not reachable entities.
 * In other words, entities cannot send packets to connection managers but clients being hosted
 * by them. The main reason behind this design decision is that connection managers are private
 * components of the server so they can only be contacted by the server. Connection Manager
 * sessions are present in {@link SessionManager} but not in {@link RoutingTable}.
 *
 * @author Gaston Dombiak
 */
public class ConnectionMultiplexerSocketReader extends SocketReader {

    /**
     * Pool of threads that are available for processing the requests.
     */
    private ThreadPoolExecutor threadPool;

    /**
     * Queue used when thread pool is exhausted (i.e. core threads, queue and max threads are all busy). Once
     * the thread pool is exhausted, incoming packets will be placed into this queue. Once the queue is emptied
     * incoming packets will go to the thread pool.<p>
     * 
     * Note that the queue is unbound so we may potentially consume all Java memory. A future version may make
     * Connection Managers smarter and throttle traffic to the server to avoid this problem.
     */
    private BlockingQueue<Runnable> overflowBuffer = new LinkedBlockingQueue<Runnable>();

    /**
     * Handler of IQ packets sent from the Connection Manager to the server.
     */
    private MultiplexerPacketHandler packetHandler;

    public ConnectionMultiplexerSocketReader(PacketRouter router, RoutingTable routingTable,
            String serverName, Socket socket, SocketConnection connection,
            boolean useBlockingMode) {
        super(router, routingTable, serverName, socket, connection, useBlockingMode);
        // Create a pool of threads that will process received packets. If more threads are
        // required then the command will be executed on the SocketReader process
        int coreThreads = JiveGlobals.getIntProperty("xmpp.multiplex.processing.core.threads", 10);
        int maxThreads = JiveGlobals.getIntProperty("xmpp.multiplex.processing.max.threads", 100);
        int queueSize = JiveGlobals.getIntProperty("xmpp.multiplex.processing.queue", 50);
        threadPool =
                new ThreadPoolExecutor(coreThreads, maxThreads, 60, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(queueSize),
                        new RejectedExecutionHandler() {
                            /**
                             * Stores rejected tasks in the overflow queue.
                             * @param r the rejected task.
                             * @param executor thread pool executor.
                             */
                            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                                if (!executor.isShutdown()) {
                                    overflowBuffer.add(r);
                                }
                            }
                        });
        // Thread that will consume packets present in the overflow queue. The thread will monitor the threadPool
        // and when a thread in the pool is available it will remove the oldest packet in the overflow queue and
        // process it with the idle threads.
        Thread overflowThread = new Thread() {

            public void run() {
                while (!threadPool.isShutdown() && !threadPool.isTerminated()) {
                    try {
                        // Get the next task that has been rejected when the threadPool was exhausted
                        Runnable runnable = overflowBuffer.take();
                        // Wait until the pool has available threads
                        while (threadPool.getActiveCount() >= threadPool.getMaximumPoolSize()) {
                            Thread.sleep(100);
                        }
                        // Process the rejected task
                        threadPool.execute(runnable);

                    } catch (InterruptedException e) {
                        // Do nothing
                    }
                    catch(Exception e) {
                        Log.error("Error consuming overflow buffer", e);
                    }
                }
            }
        };
        overflowThread.start();
    }

    boolean createSession(String namespace)
            throws UnauthorizedException, XmlPullParserException, IOException {
        if (getNamespace().equals(namespace)) {
            // The connected client is a connection manager so create a ConnectionMultiplexerSession
            session = ConnectionMultiplexerSession.createSession(serverName, reader, connection);
            packetHandler = new MultiplexerPacketHandler(session.getAddress().getDomain());
            return true;
        }
        return false;
    }

    String getNamespace() {
        return "jabber:connectionmanager";
    }

    protected void processIQ(final IQ packet) throws UnauthorizedException {
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            // Session is not authenticated so return error
            IQ reply = new IQ();
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setID(packet.getID());
            reply.setTo(packet.getFrom());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_authorized);
            session.process(reply);
            return;
        }
        // Process the packet in another thread
        threadPool.execute(new Runnable() {
            public void run() {
                packetHandler.handle(packet);
            }
        });
    }

    /**
     * Process stanza sent by a client that is connected to a connection manager. The
     * original stanza is wrapped in the route element. Only a single stanza must be
     * wrapped in the route element.
     *
     * @param packet the route element.
     */
    private void processRoute(final Route packet) throws UnauthorizedException {
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            // Session is not authenticated so return error
            Route reply = new Route(packet.getStreamID());
            reply.setID(packet.getID());
            reply.setTo(packet.getFrom());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_authorized);
            session.process(reply);
            return;
        }
        // Process the packet in another thread
        Runnable runnable = new Runnable() {
            public void run() {
                packetHandler.route(packet);
            }
        };
        if (!overflowBuffer.isEmpty()) {
            // Thread pool is exhausted or we are still recoving from a recent exhausted state.
            // Keep placing tasks in this queue until the queue is empty. The queue will help us
            // keep the cronological order of incoming packets. Note that if we don't care about
            // being cronologically correct then we should just add the task to the threadPool.
            overflowBuffer.add(runnable);
        }
        else {
            // Thread pool is not exhausted and we are not recovering from an exhausted state so just
            // run the task using the thread pool
            threadPool.execute(runnable);
        }
    }

    protected void processMessage(final Message packet) throws UnauthorizedException {
        throw new UnauthorizedException("Message packets are not supported. Original packets " +
                "should be wrapped by IQ packets.");
    }

    protected void processPresence(final Presence packet) throws UnauthorizedException {
        throw new UnauthorizedException("Message packets are not supported. Original packets " +
                "should be wrapped by IQ packets.");
    }

    boolean processUnknowPacket(Element doc) {
        String tag = doc.getName();
        if ("route".equals(tag)) {
            // Process stanza wrapped by the route packet
            try {
                processRoute(new Route(doc));
                return true;
            }
            catch (UnauthorizedException e) {
                // Should never happen
            }
        }
        else if ("handshake".equals(tag)) {
            open = ((ConnectionMultiplexerSession)session).authenticate(doc.getStringValue());
            return true;
        }
        else if ("error".equals(tag) && "stream".equals(doc.getNamespacePrefix())) {
            session.getConnection().close();
            open = false;
            return true;
        }
        return false;
    }

    protected void shutdown() {
        super.shutdown();
        // Shutdown the pool of threads that are processing packets sent by
        // the remote server
        threadPool.shutdown();
    }

    String getName() {
        return "ConnectionMultiplexer SR - " + hashCode();
    }

    boolean validateHost() {
        return false;
    }
}
