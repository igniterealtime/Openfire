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
import io.netty.handler.timeout.IdleStateHandler;
import org.jivesoftware.openfire.net.RespondingServerStanzaHandler;
import org.jivesoftware.openfire.net.SocketUtil;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    private final EventLoopGroup workerGroup;
    private Channel channel;

    public NettySessionInitializer(DomainPair domainPair, int port) {
        this.domainPair = domainPair;
        this.port = port;
        this.workerGroup = new NioEventLoopGroup();
    }

    public Future<LocalSession> init(ConnectionConfiguration listenerConfiguration) {
        // Connect to remote server using XMPP 1.0 (TLS + SASL EXTERNAL or TLS + server dialback or server dialback)
        LOG.debug( "Creating plain socket connection to a host that belongs to the remote XMPP domain." );
        final Map.Entry<Socket, Boolean> socketToXmppDomain = SocketUtil.createSocketToXmppDomain(domainPair.getRemote(), port );

        if ( socketToXmppDomain == null ) {
            throw new RuntimeException("Unable to create new session: Cannot create a plain socket connection with any applicable remote host.");
        }
        Socket socket = socketToXmppDomain.getKey();
        this.directTLS = socketToXmppDomain.getValue();

        final SocketAddress socketAddress = socket.getRemoteSocketAddress();
        LOG.debug( "Opening a new connection to {} {}.", socketAddress, directTLS ? "using directTLS" : "that is initially not encrypted" );

        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    NettyConnectionHandler businessLogicHandler = new NettyOutboundConnectionHandler(listenerConfiguration, domainPair, port);
                    int maxIdleTimeBeforeClosing = businessLogicHandler.getMaxIdleTime() > -1 ? businessLogicHandler.getMaxIdleTime() : 0;
                    int maxIdleTimeBeforePinging = maxIdleTimeBeforeClosing / 2;

                    ch.pipeline().addLast(new NettyXMPPDecoder());
                    ch.pipeline().addLast(new StringEncoder());
                    ch.pipeline().addLast("idleStateHandler", new IdleStateHandler(maxIdleTimeBeforeClosing, maxIdleTimeBeforePinging, 0));
                    ch.pipeline().addLast("keepAliveHandler", new NettyIdleStateKeepAliveHandler(false));
                    ch.pipeline().addLast(businessLogicHandler);
                    // Should have a connection
                    if (directTLS) {
                        ch.attr(CONNECTION).get().startTLS(true, true);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    super.exceptionCaught(ctx, cause);
                    if (exceptionOccurredForDirectTLS(cause)) {
                        if (directTLS &&
                            JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ON_PLAIN_DETECTION_ALLOW_NONDIRECTTLS_FALLBACK, true) &&
                            cause.getMessage().contains("plaintext connection?")
                        ) {
                            Log.warn("Plaintext detected on a new connection that is was started in DirectTLS mode (socket address: {}). Attempting to restart the connection in non-DirectTLS mode.", domainPair.getRemote());
                            directTLS = false;
                            Log.info("Re-establishing connection to {}. Proceeding without directTLS.", domainPair.getRemote());
                            init(listenerConfiguration);
                        }
                    }
                }

                public boolean exceptionOccurredForDirectTLS(Throwable cause) {
                    return cause instanceof SSLException;
                }
            });

            this.channel = b.connect(socketAddress).sync().channel();

            // Make sure we free up resources (worker group NioEventLoopGroup) when the channel is closed
            this.channel.closeFuture().addListener(future -> stop());

            // Start the session negotiation
            sendOpeningStreamHeader(channel);

            return waitForSession(channel);
        } catch (InterruptedException e) {
            Log.error("Error establishing Netty client session", e);
            stop();
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        if (channel != null) {
            channel.close();
        }
        workerGroup.shutdownGracefully();
    }

    private Future<LocalSession> waitForSession(Channel channel) {
        RespondingServerStanzaHandler stanzaHandler = (RespondingServerStanzaHandler) channel.attr(NettyConnectionHandler.HANDLER).get();
        return CompletableFuture.anyOf(stanzaHandler.isSessionAuthenticated(), stanzaHandler.haveAttemptedAllAuthenticationMethods())
            .thenApply(o -> stanzaHandler.getSession());
    }

    private void sendOpeningStreamHeader(Channel channel) {
        LOG.debug("Send the stream header and wait for response...");
        StringBuilder sb = new StringBuilder();
        sb.append("<stream:stream");
        if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
            sb.append(" xmlns:db=\"jabber:server:dialback\"");
        }
        sb.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
        sb.append(" xmlns=\"jabber:server\"");
        sb.append(" from=\"").append(domainPair.getLocal()).append("\""); // OF-673
        sb.append(" to=\"").append(domainPair.getRemote()).append("\"");
        sb.append(" version=\"1.0\">");
        channel.writeAndFlush(sb.toString());
        Log.trace("Sending: " + sb);
    }

}
