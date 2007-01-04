/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.nio;

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.Connection;
import org.jivesoftware.wildfire.net.StanzaHandler;

import java.io.IOException;

/**
 * A ConnectionHandler is responsible for creating new sessions, destroying sessions and delivering
 * received XML stanzas to the proper StanzaHandler.
 *
 * @author Gaston Dombiak
 */
public abstract class ConnectionHandler extends IoHandlerAdapter {

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    static final String CHARSET = "UTF-8";
    static final String XML_PARSER = "XML-PARSER";
    private static final String HANDLER = "HANDLER";
    private static final String CONNECTION = "CONNECTION";

    protected String serverName;


    protected ConnectionHandler(String serverName) {
        this.serverName = serverName;
    }

    public void sessionOpened(IoSession session) throws Exception {
        // Create a new XML parser for the new connection. The parser will be used by the XMPPDecoder filter.
        XMLLightweightParser parser = new XMLLightweightParser(CHARSET);
        session.setAttribute(XML_PARSER, parser);
        // Create a new NIOConnection for the new session
        NIOConnection connection = createNIOConnection(session);
        session.setAttribute(CONNECTION, connection);
        session.setAttribute(HANDLER, createStanzaHandler(connection));
        // Set the max time a connection can be idle before closing it
        int idleTime = getMaxIdleTime();
        if (idleTime > 0) {
            session.setIdleTime(IdleStatus.BOTH_IDLE, idleTime);
        }
    }

    public void sessionClosed(IoSession session) throws Exception {
        // Get the connection for this session
        Connection connection = (Connection) session.getAttribute(CONNECTION);
        // Inform the connection that it was closed
        connection.close();
    }

    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        // Get the connection for this session
        Connection connection = (Connection) session.getAttribute(CONNECTION);
        // Close idle connection
        if (Log.isDebugEnabled()) {
            Log.debug("Closing connection that has been idle: " + connection);
        }
        connection.close();
    }

    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            // TODO Verify if there were packets pending to be sent and decide what to do with them
            Log.debug(cause);
        }
        else {
            Log.error(cause);
        }
    }

    public void messageReceived(IoSession session, Object message) throws Exception {
        //System.out.println("RCVD: " + message);
        // Get the stanza handler for this session
        StanzaHandler handler = (StanzaHandler) session.getAttribute(HANDLER);
        // Let the stanza handler process the received stanza
        try {
            handler.process( (String) message);
        } catch (Exception e) {
            Log.error("Closing connection due to error while processing message: " + message, e);
            Connection connection = (Connection) session.getAttribute(CONNECTION);
            connection.close();
        }
    }

    abstract NIOConnection createNIOConnection(IoSession session);

    abstract StanzaHandler createStanzaHandler(NIOConnection connection);

    /**
     * Returns the max number of seconds a connection can be idle (both ways) before
     * being closed.<p>
     *
     * @return the max number of seconds a connection can be idle.
     */
    abstract int getMaxIdleTime();
}
