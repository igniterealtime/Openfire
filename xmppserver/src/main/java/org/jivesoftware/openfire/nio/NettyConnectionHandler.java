/*
 * Copyright (C) 2005-2008 Jive Software, 2022 Ignite Realtime Foundation. All rights reserved.
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

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import org.apache.mina.core.session.IoSession;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.StreamError;

import java.nio.charset.StandardCharsets;

/**
 * A NettyConnectionHandler is responsible for creating new sessions, destroying sessions and delivering
 * received XML stanzas to the proper StanzaHandler.
 *
 * @author Matthew Vivian
 * @author Alex Gidman
 */
@Sharable
public abstract class NettyConnectionHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger Log = LoggerFactory.getLogger(NettyConnectionHandler.class);
    static final AttributeKey<XMLLightweightParser> XML_PARSER = AttributeKey.valueOf("XML-PARSER");
    public static final AttributeKey<NettyConnection> CONNECTION = AttributeKey.valueOf("CONNECTION");
    static final AttributeKey<StanzaHandler> HANDLER = AttributeKey.valueOf("HANDLER");


    protected static final ThreadLocal<XMPPPacketReader> PARSER_CACHE = new ThreadLocal<XMPPPacketReader>()
            {
               @Override
               protected XMPPPacketReader initialValue()
               {
                  final XMPPPacketReader parser = new XMPPPacketReader();
                  parser.setXPPFactory( factory );
                  return parser;
               }
            };
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

    /**
     * The configuration for new connections.
     */
    protected final ConnectionConfiguration configuration;

    protected NettyConnectionHandler(ConnectionConfiguration configuration ) {
        this.configuration = configuration;
    }

    abstract NettyConnection createNettyConnection(ChannelHandlerContext ctx);

    abstract StanzaHandler createStanzaHandler(NettyConnection connection);

    /**
     * Returns the max number of seconds a connection can be idle (both ways) before
     * being closed.<p>
     *
     * @return the max number of seconds a connection can be idle.
     */
    abstract int getMaxIdleTime();

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        System.out.println("Netty XMPP handler added: " + ctx.channel().localAddress());
        //        ConnectionHandler.sessionOpened()

        // TODO - do we want a separate parser per-channel?
        // Create a new XML parser for the new connection. The parser will be used by the XMPPDecoder filter.
        ctx.channel().attr(XML_PARSER).set(new XMLLightweightParser());

        // Create a new Connection for the new session
        final NettyConnection nettyConnection = createNettyConnection(ctx);
        ctx.channel().attr(CONNECTION).set(nettyConnection);

        ctx.channel().attr(HANDLER).set(createStanzaHandler(nettyConnection));

        // Set the max time a connection can be idle before closing it. This amount of seconds
        // is divided in two, as Openfire will ping idle clients first (at 50% of the max idle time)
        // before disconnecting them (at 100% of the max idle time). This prevents Openfire from
        // removing connections without warning.
// TODO idle handler see: https://netty.io/4.0/api/io/netty/handler/timeout/IdleStateHandler.html
//        final int idleTime = getMaxIdleTime() / 2;
//        if (idleTime > 0) {
//            session.getConfig().setIdleTime(IdleStatus.READER_IDLE, idleTime);
//        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        System.out.println("Netty XMPP handler removed: " + ctx.channel().localAddress());
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, String message) {
        // Get the parser to use to process stanza. For optimization there is going
        // to be a parser for each running thread. Each Filter will be executed
        // by the Executor placed as the first Filter. So we can have a parser associated
        // to each Thread
        final XMPPPacketReader parser = PARSER_CACHE.get();

        // Update counter of read bytes
        // updateReadBytesCounter(session); TODO maybe replace with https://netty.io/4.0/api/io/netty/handler/traffic/TrafficCounter.html#currentReadBytes--


        System.out.println("Received: " + message);
        // Let the stanza handler process the received stanza
        try {
            ctx.channel().attr(HANDLER).get().process(message, parser);
        } catch (Throwable e) { // Make sure to catch Throwable, not (only) Exception! See OF-2367
            Log.error("Closing connection due to error while processing message: {}", message, e);
            final Connection connection = ctx.channel().attr(CONNECTION).get();
            if ( connection != null ) {
                connection.close(new StreamError(StreamError.Condition.internal_server_error, "An error occurred while processing data raw inbound data."));
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        Log.error(cause.getMessage(), cause);
        ctx.close();
    }




    /**
     * Updates the system counter of read bytes. This information is used by the incoming
     * bytes statistic.
     *
     * @param ctx the session that read more bytes from the socket.
     */
    private void updateReadBytesCounter(ChannelHandlerContext ctx) {
// TODO maybe replace with https://netty.io/4.0/api/io/netty/handler/traffic/TrafficCounter.html#currentReadBytes--
//        long currentBytes = session.getReadBytes();
//        Long prevBytes = (Long) session.getAttribute("_read_bytes");
//        long delta;
//        if (prevBytes == null) {
//            delta = currentBytes;
//        }
//        else {
//            delta = currentBytes - prevBytes;
//        }
//        session.setAttribute("_read_bytes", currentBytes);
//        ServerTrafficCounter.incrementIncomingCounter(delta);
    }

    /**
     * Updates the system counter of written bytes. This information is used by the outgoing
     * bytes statistic.
     *
     * @param session the session that wrote more bytes to the socket.
     */
    private void updateWrittenBytesCounter(IoSession session) {
// TODO maybe replace with https://netty.io/4.0/api/io/netty/handler/traffic/TrafficCounter.html#currentReadBytes--
//        long currentBytes = session.getWrittenBytes();
//        Long prevBytes = (Long) session.getAttribute("_written_bytes");
//        long delta;
//        if (prevBytes == null) {
//            delta = currentBytes;
//        }
//        else {
//            delta = currentBytes - prevBytes;
//        }
//        session.setAttribute("_written_bytes", currentBytes);
//        ServerTrafficCounter.incrementOutgoingCounter(delta);
    }
}
