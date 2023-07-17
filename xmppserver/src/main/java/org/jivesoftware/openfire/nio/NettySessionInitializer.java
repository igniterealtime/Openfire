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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.RespondingServerStanzaHandler;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.CONNECTION;
import static org.jivesoftware.openfire.session.Session.Log;


/**
 * Initialises an outgoing netty channel for outbound S2S
 */
public class NettySessionInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(NettySessionInitializer.class);
    private final DomainPair domainPair;
    private final int port;
    private  boolean directTLS = false;
    private EventLoopGroup workerGroup;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Channel channel;

    public NettySessionInitializer(DomainPair domainPair, int port, boolean directTLS) {
        this(domainPair, port);
        this.directTLS = directTLS;
    }

    public NettySessionInitializer(DomainPair domainPair, int port) {
        this.domainPair = domainPair;
        this.port = port;
    }

    public Future<LocalSession> init() {
        workerGroup = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.handler(new ChannelInitializer<SocketChannel>() {
                          @Override
                          public void initChannel(SocketChannel ch) throws Exception {
                              final ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
                              ConnectionConfiguration listenerConfiguration = connectionManager.getListener(ConnectionType.SOCKET_S2S, false).generateConnectionConfiguration();

                              ch.pipeline().addLast(new NettyXMPPDecoder());
                              ch.pipeline().addLast(new StringEncoder());
                              ch.pipeline().addLast(new NettyOutboundConnectionHandler(listenerConfiguration, domainPair));
                              // Should have a connection
                              if (directTLS) {
                                  ch.attr(CONNECTION).get().startTLS(true, true);
                              }
                          }

                          @Override
                          public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                              super.exceptionCaught(ctx, cause);
                              if (exceptionOccurredForDirectTLS(cause)) {
                                  if ( directTLS &&
                                      JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ON_PLAIN_DETECTION_ALLOW_NONDIRECTTLS_FALLBACK, true) &&
                                      cause.getMessage().contains( "plaintext connection?")
                                  ) {
                                      Log.warn( "Plaintext detected on a new connection that is was started in DirectTLS mode (socket address: {}). Attempting to restart the connection in non-DirectTLS mode.", domainPair.getRemote() );
                                      directTLS = false;
                                      Log.info( "Re-establishing connection to {}. Proceeding without directTLS.", domainPair.getRemote() );
                                      init();
                                  }
                              }
                          }

                          public boolean exceptionOccurredForDirectTLS(Throwable cause) {
                              return cause instanceof SSLException;
                          }
            });

            this.channel = b.connect(domainPair.getRemote(), port).sync().channel();

            // Start the session negotiation
            sendOpeningStreamHeader(channel);

            return waitForSession(channel);
        } catch (InterruptedException e) {
            stop();
            throw new RuntimeException(e); // TODO: Better to throw all exceptions and catch outside?
        }
    }

    public void stop() {
        channel.close();
        workerGroup.shutdownGracefully();
    }

    private Future<LocalSession> waitForSession(Channel channel) {
        RespondingServerStanzaHandler stanzaHandler = (RespondingServerStanzaHandler) channel.attr(NettyConnectionHandler.HANDLER).get();

        return executor.submit(() -> {
            while (!stanzaHandler.isSessionAuthenticated()) {
                Thread.sleep(100);
            }
            return stanzaHandler.getSession();
        });
    }

    private void sendOpeningStreamHeader(Channel channel) {
        LOG.debug("Send the stream header and wait for response...");
        StringBuilder sb = new StringBuilder();
        sb.append("<stream:stream");
        sb.append(" xmlns:db=\"jabber:server:dialback\"");
        sb.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
        sb.append(" xmlns=\"jabber:server\"");
        sb.append(" from=\"").append(domainPair.getLocal()).append("\""); // OF-673
        sb.append(" to=\"").append(domainPair.getRemote()).append("\"");
        sb.append(" version=\"1.0\">");
        channel.writeAndFlush(sb.toString());
        System.out.println("Sending: " + sb.toString());
    }

}
