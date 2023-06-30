package org.jivesoftware.openfire.spi;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.nio.NettyClientConnectionHandler;
import org.jivesoftware.openfire.nio.NettyConnectionHandler;
import org.jivesoftware.openfire.nio.NettyServerConnectionHandler;
import org.jivesoftware.openfire.nio.NettyXMPPDecoder;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * This class is responsible for accepting new (socket) connections, using Java NIO implementation provided by the
 * Netty framework.
 *
 * @author Matthew Vivian
 * @author Alex Gidman
 */
class NettyConnectionAcceptor extends ConnectionAcceptor {
    // NioEventLoopGroup is a multithreaded event loop that handles I/O operation.
    // Netty provides various EventLoopGroup implementations for different kind of transports.
    // We are implementing a server-side application in this example, and therefore two
    // NioEventLoopGroup will be used. The first one, often called 'boss', accepts an incoming connection.
    // The second one, often called 'worker', handles the traffic of the accepted connection once the boss
    // accepts the connection and registers the accepted connection to the worker. How many Threads are
    // used and how they are mapped to the created Channels depends on the EventLoopGroup implementation
    // and may be even configurable via a constructor.
    private static final EventLoopGroup BOSS_GROUP = new NioEventLoopGroup();
    private static final EventLoopGroup WORKER_GROUP = new NioEventLoopGroup();
    private final Logger Log;
    private final NettyConnectionHandler connectionHandler;
    private Channel mainChannel;

    /**
     * Instantiates, but not starts, a new instance.
     */
    public NettyConnectionAcceptor(ConnectionConfiguration configuration) {
        super(configuration);

        String name = configuration.getType().toString().toLowerCase() + (configuration.getTlsPolicy() == Connection.TLSPolicy.legacyMode ? "_ssl" : "");
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
//        connectionHandler = new NettyServerConnectionHandler( configuration );

//        switch ( configuration.getType() )
//        {
//             case SOCKET_S2S:
//                connectionHandler = new ServerConnectionHandler( configuration );
//                break;
//            case SOCKET_C2S:
//                connectionHandler = new ClientConnectionHandler( configuration );
//                break;
//            case COMPONENT:
//                connectionHandler = new ComponentConnectionHandler( configuration );
//                break;
//            case CONNECTION_MANAGER:
//                connectionHandler = new MultiplexerConnectionHandler( configuration );
//                break;
//            default:
//                throw new IllegalStateException( "This implementation does not support the connection type as defined in the provided configuration: " + configuration.getType() );
//        }
    }

    private static NioSocketAcceptor buildSocketAcceptor() {

        // TODO consider configuring netty with the settings below (i.e. find the netty way of doing this)

        // Create SocketAcceptor with correct number of processors
        final int processorCount = JiveGlobals.getIntProperty("xmpp.processor.count", Runtime.getRuntime().availableProcessors());

        final NioSocketAcceptor socketAcceptor = new NioSocketAcceptor(processorCount);

        // Set that it will be possible to bind a socket if there is a connection in the timeout state.
        socketAcceptor.setReuseAddress(true);

        // Set the listen backlog (queue) length. Default is 50.
        socketAcceptor.setBacklog(JiveGlobals.getIntProperty("xmpp.socket.backlog", 50));

        // Set default (low level) settings for new socket connections
        final SocketSessionConfig socketSessionConfig = socketAcceptor.getSessionConfig();

        //socketSessionConfig.setKeepAlive();
        final int receiveBuffer = JiveGlobals.getIntProperty("xmpp.socket.buffer.receive", -1);
        if (receiveBuffer > 0) {
            socketSessionConfig.setReceiveBufferSize(receiveBuffer);
        }

        final int sendBuffer = JiveGlobals.getIntProperty("xmpp.socket.buffer.send", -1);
        if (sendBuffer > 0) {
            socketSessionConfig.setSendBufferSize(sendBuffer);
        }

        final int linger = JiveGlobals.getIntProperty("xmpp.socket.linger", -1);
        if (linger > 0) {
            socketSessionConfig.setSoLinger(linger);
        }

        socketSessionConfig.setTcpNoDelay(JiveGlobals.getBooleanProperty("xmpp.socket.tcp-nodelay", socketSessionConfig.isTcpNoDelay()));

        return socketAcceptor;
    }

    /**
     * Starts this acceptor by binding the socket acceptor. When the acceptor is already started, a warning will be
     * logged and the method invocation is otherwise ignored.
     */
    @Override
    public synchronized void start() {
        System.out.println("Running Netty on port: " + getPort());

        try {
            // ServerBootstrap is a helper class that sets up a server. You can set up the server using
            // a Channel directly. However, please note that this is a tedious process, and you do not
            // need to do that in most cases.
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(BOSS_GROUP, WORKER_GROUP)
                // Here, we specify to use the NioServerSocketChannel class which is used to
                // instantiate a new Channel to accept incoming connections.
                .channel(NioServerSocketChannel.class)
                // The handler specified here will always be evaluated by a newly accepted Channel.
                // The ChannelInitializer is a special handler that is purposed to help a user configure
                // a new Channel. It is most likely that you want to configure the ChannelPipeline of the
                // new Channel by adding some handlers such as DiscardServerHandler to implement your
                // network application. As the application gets complicated, it is likely that you will add
                // more handlers to the pipeline and extract this anonymous class into a top-level
                // class eventually.
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new NettyXMPPDecoder());
                        ch.pipeline().addLast(new StringEncoder());
                        ch.pipeline().addLast(connectionHandler);
                    }
                })
                // You can also set the parameters which are specific to the Channel implementation.
                // We are writing a TCP/IP server, so we are allowed to set the socket options such as
                // tcpNoDelay and keepAlive. Please refer to the apidocs of ChannelOption and the specific
                // ChannelConfig implementations to get an overview about the supported ChannelOptions.

                // Set the listen backlog (queue) length.
                .option(ChannelOption.SO_BACKLOG, 128)
                // option() is for the NioServerSocketChannel that accepts incoming connections.
                // childOption() is for the Channels accepted by the parent ServerChannel,
                // which is NioSocketChannel in this case.
                .childOption(ChannelOption.SO_KEEPALIVE, true);


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
                serverBootstrap.childOption(ChannelOption.SO_LINGER, receiveBuffer);
            }

            serverBootstrap.childOption(ChannelOption.TCP_NODELAY, JiveGlobals.getBooleanProperty( "xmpp.socket.tcp-nodelay", true));

            // Set that it will be possible to bind a socket if there is a connection in the timeout state.
            serverBootstrap.childOption(ChannelOption.SO_REUSEADDR, true);

            // We do not need to throttle sessions for Netty
            if ( configuration.getMaxBufferSize() > 0 ) {
                Log.warn( "Throttling by using max buffer size not implemented for Netty; a maximum of 1 message per read is implemented instead.");
            }

            // Bind to the port and start the server to accept incoming connections.
            // You can now call the bind() method as many times as you want (with different bind addresses.)
            this.mainChannel = serverBootstrap.bind(
                new InetSocketAddress(configuration.getBindAddress(),
                    configuration.getPort()))
                .sync()
                .channel();

        } catch (InterruptedException e) {
            System.err.println("Error starting " + configuration.getPort() + ": " + e.getMessage());
            Log.error("Error starting: " + configuration.getPort(), e);
            closeMainChannel();
        }
    }

    /**
     * Stops this acceptor by unbinding the socket acceptor. Does nothing when the instance is not started.
     */
    @Override
    public synchronized void stop() {
        System.out.println("Stop called for port: " + getPort());
        closeMainChannel();
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

    /**
     * Close the main channel (this is not synchronous and does not verify the channel has closed).
     */
    public void closeMainChannel() {
        if (this.mainChannel != null) {
            Log.info("Closing channel " + mainChannel);
            mainChannel.close();
        }
    }

    public synchronized int getPort() {
        return configuration.getPort();
    }
}
