/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.nio;

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderException;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.ServerTrafficCounter;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private static Map<Integer, XMPPPacketReader> parsers = new ConcurrentHashMap<Integer, XMPPPacketReader>();
    /**
     * Reuse the same factory for all the connections.
     */
    private static XmlPullParserFactory factory = null;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

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
            session.setIdleTime(IdleStatus.READER_IDLE, idleTime);
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
            Log.debug("ConnectionHandler: Closing connection that has been idle: " + connection);
        }
        connection.close();
    }

    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            // TODO Verify if there were packets pending to be sent and decide what to do with them
            Log.debug("ConnectionHandler: ",cause);
        }
        else if (cause instanceof ProtocolDecoderException) {
            Log.warn("Closing session due to exception: " + session, cause);
            session.close();
        }
        else {
            Log.error(cause);
        }
    }

    public void messageReceived(IoSession session, Object message) throws Exception {
        // Get the stanza handler for this session
        StanzaHandler handler = (StanzaHandler) session.getAttribute(HANDLER);
        // Get the parser to use to process stanza. For optimization there is going
        // to be a parser for each running thread. Each Filter will be executed
        // by the Executor placed as the first Filter. So we can have a parser associated
        // to each Thread
        int hashCode = Thread.currentThread().hashCode();
        XMPPPacketReader parser = parsers.get(hashCode);
        if (parser == null) {
            parser = new XMPPPacketReader();
            parser.setXPPFactory(factory);
            parsers.put(hashCode, parser);
        }
        // Update counter of read btyes
        updateReadBytesCounter(session);
        //System.out.println("RCVD: " + message);
        // Let the stanza handler process the received stanza
        try {
            handler.process((String) message, parser);
        } catch (Exception e) {
            Log.error("Closing connection due to error while processing message: " + message, e);
            Connection connection = (Connection) session.getAttribute(CONNECTION);
            connection.close();
        }
    }

    public void messageSent(IoSession session, Object message) throws Exception {
        super.messageSent(session, message);
        // Update counter of written btyes
        updateWrittenBytesCounter(session);
        //System.out.println("SENT: " + Charset.forName("UTF-8").decode(((ByteBuffer)message).buf()));
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

    /**
     * Updates the system counter of read bytes. This information is used by the incoming
     * bytes statistic.
     *
     * @param session the session that read more bytes from the socket.
     */
    private void updateReadBytesCounter(IoSession session) {
        long currentBytes = session.getReadBytes();
        Long prevBytes = (Long) session.getAttribute("_read_bytes");
        long delta;
        if (prevBytes == null) {
            delta = currentBytes;
        }
        else {
            delta = currentBytes - prevBytes;
        }
        session.setAttribute("_read_bytes", currentBytes);
        ServerTrafficCounter.incrementIncomingCounter(delta);
    }

    /**
     * Updates the system counter of written bytes. This information is used by the outgoing
     * bytes statistic.
     *
     * @param session the session that wrote more bytes to the socket.
     */
    private void updateWrittenBytesCounter(IoSession session) {
        long currentBytes = session.getWrittenBytes();
        Long prevBytes = (Long) session.getAttribute("_written_bytes");
        long delta;
        if (prevBytes == null) {
            delta = currentBytes;
        }
        else {
            delta = currentBytes - prevBytes;
        }
        session.setAttribute("_written_bytes", currentBytes);
        ServerTrafficCounter.incrementOutgoingCounter(delta);
    }
}
