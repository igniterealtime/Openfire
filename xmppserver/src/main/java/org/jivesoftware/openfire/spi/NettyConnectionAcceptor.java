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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.nio.NettyClientConnectionHandler;
import org.jivesoftware.openfire.nio.NettyConnectionHandler;
import org.jivesoftware.openfire.nio.NettyServerConnectionHandler;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

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
     * The 'boss' accepts an incoming connection.
     */
    private static final EventLoopGroup BOSS_GROUP = new NioEventLoopGroup();

    /**
     * A multithreaded event loop that handles I/O operation
     * <p>
     * The 'worker', handles the traffic of the accepted connection once the boss accepts the connection
     * and registers the accepted connection to the worker.
     */
    private static final EventLoopGroup WORKER_GROUP = new NioEventLoopGroup();
    private final Logger Log;
    private final NettyConnectionHandler connectionHandler;
    private Channel mainChannel;

    /**
     * Instantiates, but not starts, a new instance.
     */
    public NettyConnectionAcceptor(ConnectionConfiguration configuration) {
        super(configuration);

        String name = configuration.getType().toString().toLowerCase() + (isDirectTLS() ? "_ssl" : "");
        Log = LoggerFactory.getLogger( NettyConnectionAcceptor.class.getName() + "[" + name + "]" );

        switch (configuration.getType()) {
            case SOCKET_S2S:
                connectionHandler = new NettyServerConnectionHandler(configuration);
                break;
            case SOCKET_C2S:
                connectionHandler = new NettyClientConnectionHandler(configuration);
                break;
            default:
                throw new IllegalStateException("This implementation does not support the connection type as defined in the provided configuration: " + configuration.getType());
        }


// TODO add support for COMPONENT & Multiplexer
//            case COMPONENT:
//                connectionHandler = new ComponentConnectionHandler( configuration );
//                break;
//            case CONNECTION_MANAGER:
//                connectionHandler = new MultiplexerConnectionHandler( configuration );
//                break;

    }

    /**
     * Starts this acceptor by binding the socket acceptor. When the acceptor is already started, a warning will be
     * logged and the method invocation is otherwise ignored.
     */
    @Override
    public synchronized void start() {
        System.out.println("Running Netty on port: " + getPort());

        try {
            // ServerBootstrap is a helper class that sets up a server
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(BOSS_GROUP, WORKER_GROUP)
                // Instantiate a new Channel to accept incoming connections.
                .channel(NioServerSocketChannel.class)
                // The handler specified here will always be evaluated by a newly accepted Channel.
                .childHandler(new NettyServerInitializer(connectionHandler, isDirectTLS()))
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

    private boolean isDirectTLS() {
        return configuration.getTlsPolicy() == Connection.TLSPolicy.legacyMode;
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
        if (!BOSS_GROUP.isShuttingDown()) {
            BOSS_GROUP.shutdownGracefully();
        }
        if (!WORKER_GROUP.isShuttingDown()) {
            WORKER_GROUP.shutdownGracefully();
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
        // TODO reconfigure the netty connection
    }

    public synchronized int getPort() {
        return configuration.getPort();
    }
}
