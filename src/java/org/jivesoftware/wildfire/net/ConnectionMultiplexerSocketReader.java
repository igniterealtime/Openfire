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
import org.jivesoftware.wildfire.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.multiplex.ConnectionMultiplexerSession;
import org.jivesoftware.wildfire.multiplex.MultiplexerPacketHandler;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.*;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    private SessionManager sessionManager;
    /**
     * Handler of IQ packets sent from the Connection Manager to the server.
     */
    private MultiplexerPacketHandler packetHandler;

    public ConnectionMultiplexerSocketReader(PacketRouter router, String serverName, Socket socket,
            SocketConnection connection) {
        super(router, serverName, socket, connection);
        // Create a pool of threads that will process received packets. If more threads are
        // required then the command will be executed on the SocketReader process
        int coreThreads = JiveGlobals.getIntProperty("xmpp.multiplex.processing.core.threads", 10);
        int maxThreads = JiveGlobals.getIntProperty("xmpp.multiplex.processing.max.threads", 100);
        int queueSize = JiveGlobals.getIntProperty("xmpp.multiplex.processing.queue", 50);
        threadPool =
                new ThreadPoolExecutor(coreThreads, maxThreads, 60, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(queueSize),
                        new ThreadPoolExecutor.CallerRunsPolicy());
        sessionManager = XMPPServer.getInstance().getSessionManager();
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
            // Session is not authenticated and IQ packet is not being sent to the
            // connection manager
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
                try {
                    JID from = packet.getFrom();
                    if (from != null && from.equals(session.getAddress())) {
                        // IQ packets sent from the connection manager itself have a special
                        // processing logic. No route is created to the Connection Managers
                        // so we need to catch IQ packets here and process them
                        packetHandler.handle(packet);
                    }
                    else {
                        // Increment packet counter of the client session
                        incrementClientPacketCount(from);
                    }
                    // Process and route the packet
                    ConnectionMultiplexerSocketReader.super.processIQ(packet);
                }
                catch (UnauthorizedException e) {
                    Log.error("Error processing packet", e);
                }
            }
        });
    }

    protected void processMessage(final Message packet) throws UnauthorizedException {
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            Message reply = new Message();
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
                try {
                    // Increment packet counter of the client session
                    incrementClientPacketCount(packet.getFrom());
                    // Process and route the packet
                    ConnectionMultiplexerSocketReader.super.processMessage(packet);
                }
                catch (UnauthorizedException e) {
                    Log.error("Error processing packet", e);
                }
            }
        });
    }

    protected void processPresence(final Presence packet) throws UnauthorizedException {
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            Presence reply = new Presence();
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
                try {
                    // Increment packet counter of the client session
                    incrementClientPacketCount(packet.getFrom());
                    // Process and route the packet
                    ConnectionMultiplexerSocketReader.super.processPresence(packet);
                }
                catch (UnauthorizedException e) {
                    Log.error("Error processing packet", e);
                }
            }
        });
    }

    boolean processUnknowPacket(Element doc) {
        if ("handshake".equals(doc.getName())) {
            open = ((ConnectionMultiplexerSession)session).authenticate(doc.getStringValue());
            return true;
        }
        return false;
    }

    private void incrementClientPacketCount(JID from) {
        if (from != null) {
            ClientSession originatingSession = sessionManager.getSession(from);
            if (originatingSession != null) {
                originatingSession.incrementClientPacketCount();
            }
        }
    }

    boolean validateHost() {
        return false;
    }
}
