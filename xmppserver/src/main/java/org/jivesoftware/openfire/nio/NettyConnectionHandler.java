/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.AttributeKey;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.ServerTrafficCounter;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.StreamError;

import java.time.Duration;

import static org.jivesoftware.openfire.spi.NettyServerInitializer.TRAFFIC_HANDLER_NAME;

/**
 * A NettyConnectionHandler is responsible for creating new sessions, destroying sessions and delivering
 * received XML stanzas to the proper StanzaHandler.<p>
 *
 * Subclasses of this will supply a specific {@link StanzaHandler} implementation depending on the
 * type of connection to be handled, e.g. C2S, S2S.
 *
 * @author Matthew Vivian
 * @author Alex Gidman
 */
public abstract class NettyConnectionHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger Log = LoggerFactory.getLogger(NettyConnectionHandler.class);
    static final AttributeKey<XMLLightweightParser> XML_PARSER = AttributeKey.valueOf("XML-PARSER");
    public static final AttributeKey<NettyConnection> CONNECTION = AttributeKey.valueOf("CONNECTION");
    public static final AttributeKey<Long> READ_BYTES = AttributeKey.valueOf("READ_BYTES");
    public static final AttributeKey<Long> WRITTEN_BYTES = AttributeKey.valueOf("WRITTEN_BYTES");
    static final AttributeKey<StanzaHandler> HANDLER = AttributeKey.valueOf("HANDLER");
    public static final AttributeKey<Boolean> IDLE_FLAG = AttributeKey.valueOf("IDLE_FLAG");


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

    protected boolean sslInitDone;

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
     * Returns the time that a connection can be idle before being closed.
     *
     * @return the time a connection can be idle.
     */
    public abstract Duration getMaxIdleTime();

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Log.trace("Netty XMPP handler added: {}", ctx.channel().remoteAddress() == null ? ctx.channel().localAddress() : ctx.channel().localAddress() + "--" + ctx.channel().remoteAddress());

        // Create a new XML parser for the new connection. The parser will be used by the XMPPDecoder filter.
        ctx.channel().attr(XML_PARSER).set(new XMLLightweightParser());

        // Create a new Connection for the new session
        final NettyConnection nettyConnection = createNettyConnection(ctx);
        ctx.channel().attr(CONNECTION).set(nettyConnection);
        ctx.channel().attr(READ_BYTES).set(0L);

        ctx.channel().attr(HANDLER).set(createStanzaHandler(nettyConnection));
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        Log.trace("Netty XMPP handler removed: {}", ctx.channel().remoteAddress() == null ? ctx.channel().localAddress() : ctx.channel().localAddress() + "--" + ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, String message) {
        // Get the parser to use to process stanza. For optimization there is going
        // to be a parser for each running thread. Each Filter will be executed
        // by the Executor placed as the first Filter. So we can have a parser associated
        // to each Thread
        final XMPPPacketReader parser = PARSER_CACHE.get();

        // Update counter of read bytes
        updateReadBytesCounter(ctx);

        Log.trace("Handler on {} received: {}", ctx.channel().remoteAddress() == null ? ctx.channel().localAddress() : ctx.channel().localAddress() + "--" + ctx.channel().remoteAddress(), message);
        // Let the stanza handler process the received stanza
        try {
            ctx.channel().attr(HANDLER).get().process(message, parser);
        } catch (Throwable e) { // Make sure to catch Throwable, not (only) Exception! See OF-2367
            Log.error("Closing connection on {} due to error while processing message: {}", ctx.channel().remoteAddress() == null ? ctx.channel().localAddress() : ctx.channel().localAddress() + "--" + ctx.channel().remoteAddress(), message, e);
            final Connection connection = ctx.channel().attr(CONNECTION).get();
            if ( connection != null ) {
                connection.close(new StreamError(StreamError.Condition.internal_server_error, "An error occurred while processing data raw inbound data."));
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        Log.error("Exception caught on channel {}", ctx.channel().remoteAddress() == null ? ctx.channel().localAddress() : ctx.channel().localAddress() + "--" + ctx.channel().remoteAddress(), cause);
        ctx.channel().close();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        Connection connection = ctx.channel().attr(CONNECTION).get();
        if (connection != null) {
            connection.close(); // clean up resources (connection and session) when channel is unregistered.
        }
        super.channelUnregistered(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!sslInitDone && evt instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent e = (SslHandshakeCompletionEvent) evt;

            if (e.isSuccess()) {
                sslInitDone = true;

                NettyConnection connection = ctx.channel().attr(NettyConnectionHandler.CONNECTION).get();
                connection.setEncrypted(true);
                Log.debug("TLS negotiation was successful on channel {}", ctx.channel().remoteAddress() == null ? ctx.channel().localAddress() : ctx.channel().localAddress() + "--" + ctx.channel().remoteAddress());
                ctx.fireChannelActive();
            }
        }

        super.userEventTriggered(ctx, evt);
    }

    /**
     * Updates the system counter of read bytes. This information is used by the incoming
     * bytes statistic.
     *
     * @param ctx the context for the channel reading bytes
     */
    private void updateReadBytesCounter(ChannelHandlerContext ctx) {
        ChannelTrafficShapingHandler handler = (ChannelTrafficShapingHandler) ctx.channel().pipeline().get(TRAFFIC_HANDLER_NAME);
        if (handler != null) {
            long currentBytes = handler.trafficCounter().currentReadBytes();
            Long prevBytes = ctx.channel().attr(READ_BYTES).get();
            long delta;
            if (prevBytes == null) {
                delta = currentBytes;
            }
            else {
                delta = currentBytes - prevBytes;
            }
            ctx.channel().attr(READ_BYTES).set(currentBytes);
            ctx.channel().attr(IDLE_FLAG).set(null);
            ServerTrafficCounter.incrementIncomingCounter(delta);
        }
    }

    @Override
    public String toString()
    {
        return "NettyConnectionHandler{" +
            "sslInitDone=" + sslInitDone +
            ", configuration=" + configuration +
            '}';
    }
}
