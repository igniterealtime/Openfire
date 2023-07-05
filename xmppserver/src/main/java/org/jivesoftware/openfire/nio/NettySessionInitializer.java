package org.jivesoftware.openfire.nio;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.RespondingServerStanzaHandler;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class NettySessionInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(NettySessionInitializer.class);
    
    private final DomainPair domainPair;
    private final int port;
    private EventLoopGroup workerGroup;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public NettySessionInitializer(DomainPair domainPair, int port) {
        this.domainPair = domainPair;
        this.port = port;
    }


// TODO handle direct TLS
//            if (directTLS) {
//                try {
//                    connection.startTLS( true, true );
//                } catch ( SSLException ex ) {
//                    if ( JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ON_PLAIN_DETECTION_ALLOW_NONDIRECTTLS_FALLBACK, true) && ex.getMessage().contains( "plaintext connection?" ) ) {
//                        Log.warn( "Plaintext detected on a new connection that is was started in DirectTLS mode (socket address: {}). Attempting to restart the connection in non-DirectTLS mode.", socketAddress );
//                        try {
//                            // Close old socket
//                            socket.close();
//                        } catch ( Exception e ) {
//                            Log.debug( "An exception occurred (and is ignored) while trying to close a socket that was already in an error state.", e );
//                        }
//                        socket = new Socket();
//                        socket.connect( socketAddress, RemoteServerManager.getSocketTimeout() );
//                        connection = new SocketConnection(XMPPServer.getInstance().getPacketDeliverer(), socket, false);
//                        directTLS = false;
//                        Log.info( "Re-established connection to {}. Proceeding without directTLS.", socketAddress );
//                    } else {
//                        // Do not retry as non-DirectTLS, rethrow the exception.
//                        throw ex;
//                    }
//                }
//            }



    public Future<LocalSession> init() {
        workerGroup = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    final ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
                    ConnectionConfiguration listenerConfiguration = connectionManager.getListener(ConnectionType.SOCKET_S2S, false).generateConnectionConfiguration();

                    ch.pipeline().addLast(new NettyXMPPDecoder());
                    ch.pipeline().addLast(new StringEncoder());
                    ch.pipeline().addLast(new NettyOutboundConnectionHandler(listenerConfiguration, domainPair));
                }
            });

            Channel channel = b.connect(domainPair.getRemote(), port).sync().channel();

            // Start the session negotiation
            sendOpeningStreamHeader(channel);

            return waitForSession(channel);


            // TODO - do something here (block?) until we get a LocalOutgoingServerSession
            // How do we report back that the session is open?
            //      This happens in RespondingServerStanzaHandler.createSession()
            //   Pass something in for ^ to update?
            //
            // How do Futures work?
            //
            //
            // can we add a handler to the pipeline to handle this?
            //      Does this even make sense? Handlers deal with network in/out

//            return channel;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // TODO how do we shut down the server?
//                workerGroup.shutdownGracefully();
        }

    }

    public void stop() {
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
