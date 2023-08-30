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

package org.jivesoftware.openfire.spi;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.jivesoftware.openfire.nio.NettyConnection.SSL_HANDLER_NAME;
import static org.jivesoftware.openfire.nio.NettyConnectionHandler.CONNECTION;
import static org.jivesoftware.openfire.nio.NettySessionInitializer.GRACEFUL_SHUTDOWN_QUIET_PERIOD;
import static org.jivesoftware.openfire.nio.NettySessionInitializer.GRACEFUL_SHUTDOWN_TIMEOUT;

/**
 * Responsible for accepting new (socket) connections, using Java NIO implementation provided by the Netty framework.
 *
 * @author Matthew Vivian
 * @author Alex Gidman
 */
class NettyConnectionAcceptor extends ConnectionAcceptor {
    // NioEventLoopGroup is a multithreaded event loop that handles I/O operation.
    // The first one, often called 'boss', accepts an incoming connection.
    // The second one, often called 'worker', handles the traffic of the accepted connection once the boss
    // accepts the connection and registers the accepted connection to the worker. How many Threads are
    // used and how they are mapped to the created Channels depends on the EventLoopGroup implementation
    // and may be even configurable via a constructor.

    /**
     * A multithreaded event loop that handles I/O operation
     * <p>
     * The parent 'boss' accepts an incoming connection.
     */
    private static final EventLoopGroup PARENT_GROUP = new NioEventLoopGroup();

    /**
     * A multithreaded event loop that handles I/O operation
     * <p>
     * The child 'worker', handles the traffic of the accepted connection once the parent accepts the connection
     * and registers the accepted connection to the worker.
     */
    private static final EventLoopGroup CHILD_GROUP = new NioEventLoopGroup();

    /**
     * A thread-safe Set containing all open Channels associated with this ConnectionAcceptor
     *
     * Allows various bulk operations to be made on them such as pipeline modification & broadcast messages.
     * Closed Channels are automatically removed.
     */
    private final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private final Logger Log;
    private Channel mainChannel;

    /**
     * Instantiates, but does not start, a new instance.
     */
    public NettyConnectionAcceptor(ConnectionConfiguration configuration) {
        super(configuration);

        String name = configuration.getType().toString().toLowerCase() + (isDirectTLSConfigured() ? "_ssl" : "");
        Log = LoggerFactory.getLogger( NettyConnectionAcceptor.class.getName() + "[" + name + "]" );
    }

    /**
     * Starts this acceptor by binding the socket acceptor. When the acceptor is already started, a warning will be
     * logged and the method invocation is otherwise ignored.
     */
    @Override
    public synchronized void start() {
        Log.debug("Running Netty on port: " + getPort());

        try {
            // ServerBootstrap is a helper class that sets up a server
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(PARENT_GROUP, CHILD_GROUP)
                // Instantiate a new Channel to accept incoming connections.
                .channel(NioServerSocketChannel.class)
                // The handler specified here will always be evaluated by a newly accepted Channel.
                .childHandler(new NettyServerInitializer(configuration, allChannels))
                // Set the listen backlog (queue) length.
                .option(ChannelOption.SO_BACKLOG, JiveGlobals.getIntProperty("xmpp.socket.backlog", 50))
                // option() is for the NioServerSocketChannel that accepts incoming connections.
                // childOption() is for the Channels accepted by the parent ServerChannel,
                // which is NioSocketChannel in this case.
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                // Setting TCP_NODELAY to false enables the Nagle algorithm, which delays sending small successive packets
                .childOption(ChannelOption.TCP_NODELAY, JiveGlobals.getBooleanProperty( "xmpp.socket.tcp-nodelay", true))
                // Set that it will be possible to bind a socket if there is a connection in the timeout state.
                .childOption(ChannelOption.SO_REUSEADDR, true);

            final int sendBuffer = JiveGlobals.getIntProperty( "xmpp.socket.buffer.send", -1 );
            if ( sendBuffer > 0 ) {
                // SO_SNDBUF = Socket Option - Send Buffer Size in Bytes
                serverBootstrap.childOption(ChannelOption.SO_SNDBUF, sendBuffer);
            }
            final int receiveBuffer = JiveGlobals.getIntProperty( "xmpp.socket.buffer.receive", -1 );
            if ( receiveBuffer > 0 ) {
                // SO_RCVBUF = Socket Option - Receive Buffer Size in Bytes
                serverBootstrap.childOption(ChannelOption.SO_RCVBUF, receiveBuffer);
            }
            final int linger = JiveGlobals.getIntProperty( "xmpp.socket.linger", -1 );
            if ( linger > 0 ) {
                serverBootstrap.childOption(ChannelOption.SO_LINGER, linger);
            }

            // Bind to the port and start the server to accept incoming connections.
            this.mainChannel = serverBootstrap.bind(
                    new InetSocketAddress(
                        configuration.getBindAddress(),
                        configuration.getPort())
                )
                .sync()
                .channel();

        } catch (InterruptedException e) {
            Log.error("Error starting: " + configuration.getPort(), e);
            closeMainChannel();
        }
    }


    /**
     * Stops this acceptor by unbinding the socket acceptor. Does nothing when the instance is not started.
     */
    @Override
    public synchronized void stop() {
        closeMainChannel();
    }

    /**
     * Close the main channel (this is not synchronous and does not verify the channel has closed).
     */
    private void closeMainChannel() {
        if (this.mainChannel != null) {
            Log.info("Closing channel " + mainChannel);
            mainChannel.close();
        }
    }

    /**
     * Shuts down event loop groups if they are not already shutdown - this will close all channels.
     */
    public static void shutdownEventLoopGroups() {
        if (!PARENT_GROUP.isShuttingDown()) {
            PARENT_GROUP.shutdownGracefully(GRACEFUL_SHUTDOWN_QUIET_PERIOD.getValue().toMillis(), GRACEFUL_SHUTDOWN_TIMEOUT.getValue().toMillis(), TimeUnit.MILLISECONDS);
        }
        if (!CHILD_GROUP.isShuttingDown()) {
            CHILD_GROUP.shutdownGracefully(GRACEFUL_SHUTDOWN_QUIET_PERIOD.getValue().toMillis(), GRACEFUL_SHUTDOWN_TIMEOUT.getValue().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Determines if this instance is currently in a state where it is actively serving connections or not.
     * Channel must be open with no connections if it is idle
     *
     * @return false when this instance is started and is currently being used to serve connections (otherwise true)
     */
    @Override
    public synchronized boolean isIdle() {
        return mainChannel.isOpen() && !mainChannel.isActive();
    }

    @Override
    public synchronized void reconfigure(ConnectionConfiguration configuration) {
        this.configuration = configuration;

        if (isDirectTLSConfigured(configuration)) {
            addNewSslHandlerToAllChannels();
        } else {
            // The acceptor is in 'startTLS' mode. Remove TLS filter
            removeSslHandlerFromAllChannels();
        }
    }

    /**
     * Remove TLS filter from all channels of this acceptor
     */
    private void removeSslHandlerFromAllChannels() {
        this.allChannels
            .stream()
            .map(Channel::pipeline)
            .filter(pipeline -> pipeline.toMap().containsKey(SSL_HANDLER_NAME))
            .forEach(pipeline -> pipeline.remove(SSL_HANDLER_NAME));
    }

    /**
     * Add or replace TLS filter in all channels of this acceptor
     */
    private void addNewSslHandlerToAllChannels() {
        this.allChannels.forEach(channel ->
        {
            if (channel.pipeline().toMap().containsKey(SSL_HANDLER_NAME)) {
                channel.pipeline().remove(SSL_HANDLER_NAME);
            }
            try {
                channel.attr(CONNECTION).get().startTLS(false, true);
            } catch (Exception e) {
                Log.error("An exception occurred while reloading the TLS configuration.", e);
            }
        });
    }

    private boolean isDirectTLSConfigured() {
        return isDirectTLSConfigured(this.configuration);
    }

    private static boolean isDirectTLSConfigured(ConnectionConfiguration configuration) {
        return configuration.getTlsPolicy() == Connection.TLSPolicy.legacyMode;
    }

    public synchronized int getPort() {
        return configuration.getPort();
    }
}
