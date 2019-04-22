package org.jivesoftware.openfire.spi;

import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.integration.jmx.IoServiceMBean;
import org.apache.mina.integration.jmx.IoSessionMBean;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.net.StalledSessionsFilter;
import org.jivesoftware.openfire.nio.*;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for accepting new (socket) connections, using Java NIO implementation provided by the
 * Apache MINA framework.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
class MINAConnectionAcceptor extends ConnectionAcceptor
{
    private final Logger Log;
    private final String name;
    private final ConnectionHandler connectionHandler;

    private final EncryptionArtifactFactory encryptionArtifactFactory;

    private NioSocketAcceptor socketAcceptor;

    /**
     * Instantiates, but not starts, a new instance.
     */
    public MINAConnectionAcceptor( ConnectionConfiguration configuration )
    {
        super( configuration );

        this.name = configuration.getType().toString().toLowerCase() + ( configuration.getTlsPolicy() == Connection.TLSPolicy.legacyMode ? "_ssl" : "" );
        Log = LoggerFactory.getLogger( MINAConnectionAcceptor.class.getName() + "[" + name + "]" );

        switch ( configuration.getType() )
        {
             case SOCKET_S2S:
                connectionHandler = new ServerConnectionHandler( configuration );
                break;
            case SOCKET_C2S:
                connectionHandler = new ClientConnectionHandler( configuration );
                break;
            case COMPONENT:
                connectionHandler = new ComponentConnectionHandler( configuration );
                break;
            case CONNECTION_MANAGER:
                connectionHandler = new MultiplexerConnectionHandler( configuration );
                break;
            default:
                throw new IllegalStateException( "This implementation does not support the connection type as defined in the provided configuration: " + configuration.getType() );
        }

        this.encryptionArtifactFactory = new EncryptionArtifactFactory( configuration );
    }

    /**
     * Starts this acceptor by binding the socket acceptor. When the acceptor is already started, a warning will be
     * logged and the method invocation is otherwise ignored.
     */
    @Override
    public synchronized void start()
    {
        if ( socketAcceptor != null )
        {
            Log.warn( "Unable to start acceptor (it is already started!)" );
            return;
        }

        try
        {
            // Configure the thread pool that is to be used.
            final int initialSize = ( configuration.getMaxThreadPoolSize() / 4 ) + 1;
            final ExecutorFilter executorFilter = new ExecutorFilter( initialSize, configuration.getMaxThreadPoolSize(), 60, TimeUnit.SECONDS );
            final ThreadPoolExecutor eventExecutor = (ThreadPoolExecutor) executorFilter.getExecutor();
            final ThreadFactory threadFactory = new NamedThreadFactory( name + "-thread-", eventExecutor.getThreadFactory(), true, null );
            eventExecutor.setThreadFactory( threadFactory );

            // Construct a new socket acceptor, and configure it.
            socketAcceptor = buildSocketAcceptor();

            if ( JMXManager.isEnabled() )
            {
                configureJMX( socketAcceptor, name );
            }

            final DefaultIoFilterChainBuilder filterChain = socketAcceptor.getFilterChain();
            filterChain.addFirst( ConnectionManagerImpl.EXECUTOR_FILTER_NAME, executorFilter );

            // Add the XMPP codec filter
            filterChain.addAfter( ConnectionManagerImpl.EXECUTOR_FILTER_NAME, ConnectionManagerImpl.XMPP_CODEC_FILTER_NAME, new ProtocolCodecFilter( new XMPPCodecFactory() ) );

            // Kill sessions whose outgoing queues keep growing and fail to send traffic
            filterChain.addAfter( ConnectionManagerImpl.XMPP_CODEC_FILTER_NAME, ConnectionManagerImpl.CAPACITY_FILTER_NAME, new StalledSessionsFilter() );

            // Ports can be configured to start connections in SSL (as opposed to upgrade a non-encrypted socket to an encrypted one, typically using StartTLS)
            if ( configuration.getTlsPolicy() == Connection.TLSPolicy.legacyMode )
            {
                final SslFilter sslFilter = encryptionArtifactFactory.createServerModeSslFilter();
                filterChain.addAfter( ConnectionManagerImpl.EXECUTOR_FILTER_NAME, ConnectionManagerImpl.TLS_FILTER_NAME, sslFilter );
            }

            // Throttle sessions who send data too fast
            if ( configuration.getMaxBufferSize() > 0 )
            {
                socketAcceptor.getSessionConfig().setMaxReadBufferSize( configuration.getMaxBufferSize() );
                Log.debug( "Throttling read buffer for connections to max={} bytes", configuration.getMaxBufferSize() );
            }

            // Start accepting connections
            socketAcceptor.setHandler( connectionHandler );
            socketAcceptor.bind( new InetSocketAddress( configuration.getBindAddress(), configuration.getPort() ) );
        }
        catch ( Exception e )
        {
            System.err.println( "Error starting " + configuration.getPort() + ": " + e.getMessage() );
            Log.error( "Error starting: " + configuration.getPort(), e );
            // Reset for future use.
            if (socketAcceptor != null) {
                try {
                    socketAcceptor.unbind();
                } finally {
                    socketAcceptor = null;
                }
            }
        }
    }

    /**
     * Stops this acceptor by unbinding the socket acceptor. Does nothing when the instance is not started.
     */
    @Override
    public synchronized void stop()
    {
        if ( socketAcceptor != null )
        {
            socketAcceptor.unbind();
            socketAcceptor = null;
        }
    }

    /**
     * Determines if this instance is currently in a state where it is actively serving connections.
     *
     * @return false when this instance is started and is currently being used to serve connections (otherwise true)
     */
    @Override
    public synchronized boolean isIdle()
    {
        return this.socketAcceptor != null && this.socketAcceptor.getManagedSessionCount() == 0;
    }

    @Override
    public synchronized void reconfigure( ConnectionConfiguration configuration )
    {
        this.configuration = configuration;

        if ( socketAcceptor == null )
        {
            return; // reconfig will occur when acceptor is started.
        }

        final DefaultIoFilterChainBuilder filterChain = socketAcceptor.getFilterChain();

        if ( filterChain.contains( ConnectionManagerImpl.EXECUTOR_FILTER_NAME ) )
        {
            final ExecutorFilter executorFilter = (ExecutorFilter) filterChain.get( ConnectionManagerImpl.EXECUTOR_FILTER_NAME );
            ( (ThreadPoolExecutor) executorFilter.getExecutor()).setCorePoolSize( ( configuration.getMaxThreadPoolSize() / 4 ) + 1 );
            ( (ThreadPoolExecutor) executorFilter.getExecutor()).setMaximumPoolSize( ( configuration.getMaxThreadPoolSize() ) );
        }

        if ( configuration.getTlsPolicy() == Connection.TLSPolicy.legacyMode )
        {
            // add or replace TLS filter (that's used only for 'direct-TLS')
            try
            {
                final SslFilter sslFilter = encryptionArtifactFactory.createServerModeSslFilter();
                if ( filterChain.contains( ConnectionManagerImpl.TLS_FILTER_NAME ) )
                {
                    filterChain.replace( ConnectionManagerImpl.TLS_FILTER_NAME, sslFilter );
                }
                else
                {
                    filterChain.addAfter( ConnectionManagerImpl.EXECUTOR_FILTER_NAME, ConnectionManagerImpl.TLS_FILTER_NAME, sslFilter );
                }
            }
            catch ( KeyManagementException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e )
            {
                Log.error( "An exception occurred while reloading the TLS configuration.", e );
            }
        }
        else
        {
            // The acceptor is in 'startTLS' mode. Remove TLS filter (that's used only for 'direct-TLS')
            if ( filterChain.contains( ConnectionManagerImpl.TLS_FILTER_NAME ) )
            {
                filterChain.remove( ConnectionManagerImpl.TLS_FILTER_NAME );
            }
        }

        if ( configuration.getMaxBufferSize() > 0 )
        {
            socketAcceptor.getSessionConfig().setMaxReadBufferSize( configuration.getMaxBufferSize() );
            Log.debug( "Throttling read buffer for connections to max={} bytes", configuration.getMaxBufferSize() );
        }
    }

    public synchronized int getPort()
    {
        return configuration.getPort();
    }

    // TODO see if we can avoid exposing MINA internals.
    public synchronized NioSocketAcceptor getSocketAcceptor()
    {
        return socketAcceptor;
    }

    private static NioSocketAcceptor buildSocketAcceptor()
    {
        // Create SocketAcceptor with correct number of processors
        final int processorCount = JiveGlobals.getIntProperty( "xmpp.processor.count", Runtime.getRuntime().availableProcessors() );

        final NioSocketAcceptor socketAcceptor = new NioSocketAcceptor( processorCount );

        // Set that it will be possible to bind a socket if there is a connection in the timeout state.
        socketAcceptor.setReuseAddress( true );

        // Set the listen backlog (queue) length. Default is 50.
        socketAcceptor.setBacklog( JiveGlobals.getIntProperty( "xmpp.socket.backlog", 50 ) );

        // Set default (low level) settings for new socket connections
        final SocketSessionConfig socketSessionConfig = socketAcceptor.getSessionConfig();

        //socketSessionConfig.setKeepAlive();
        final int receiveBuffer = JiveGlobals.getIntProperty( "xmpp.socket.buffer.receive", -1 );
        if ( receiveBuffer > 0 )
        {
            socketSessionConfig.setReceiveBufferSize( receiveBuffer );
        }

        final int sendBuffer = JiveGlobals.getIntProperty( "xmpp.socket.buffer.send", -1 );
        if ( sendBuffer > 0 )
        {
            socketSessionConfig.setSendBufferSize( sendBuffer );
        }

        final int linger = JiveGlobals.getIntProperty( "xmpp.socket.linger", -1 );
        if ( linger > 0 )
        {
            socketSessionConfig.setSoLinger( linger );
        }

        socketSessionConfig.setTcpNoDelay( JiveGlobals.getBooleanProperty( "xmpp.socket.tcp-nodelay", socketSessionConfig.isTcpNoDelay() ) );

        return socketAcceptor;
    }

    private void configureJMX( NioSocketAcceptor acceptor, String suffix )
    {
        final String prefix = IoServiceMBean.class.getPackage().getName();

        // monitor the IoService
        try
        {
            final IoServiceMBean mbean = new IoServiceMBean( acceptor );
            final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            final ObjectName name = new ObjectName( prefix + ":type=SocketAcceptor,name=" + suffix );
            mbs.registerMBean( mbean, name );
            // mbean.startCollectingStats(JiveGlobals.getIntProperty("xmpp.socket.jmx.interval", 60000));
        }
        catch ( JMException ex )
        {
            Log.warn( "Failed to register MINA acceptor mbean (JMX): " + ex );
        }

        // optionally register IoSession mbeans (one per session)
        if ( JiveGlobals.getBooleanProperty( "xmpp.socket.jmx.sessions", false ) )
        {
            acceptor.addListener( new IoServiceListener()
            {
                private ObjectName getObjectNameForSession( IoSession session ) throws MalformedObjectNameException
                {
                    return new ObjectName( prefix + ":type=IoSession,name=" + session.getRemoteAddress().toString().replace( ':', '/' ) );
                }

                @Override
                public void sessionCreated(final IoSession session)
                {
                    try
                    {
                        ManagementFactory.getPlatformMBeanServer().registerMBean(
                                new IoSessionMBean( session ),
                                getObjectNameForSession( session )
                        );
                    }
                    catch ( JMException ex )
                    {
                        Log.warn( "Failed to register MINA session mbean (JMX): " + ex );
                    }
                }

                @Override
                public void sessionDestroyed(final IoSession session)
                {
                    try
                    {
                        ManagementFactory.getPlatformMBeanServer().unregisterMBean(
                                getObjectNameForSession( session )
                        );
                    }
                    catch ( JMException ex )
                    {
                        Log.warn( "Failed to unregister MINA session mbean (JMX): " + ex );
                    }
                }

                @Override
                public void serviceActivated(final IoService service) {}

                @Override
                public void serviceDeactivated(final IoService service ) {}

                @Override
                public void serviceIdle(final IoService service, final IdleStatus idleStatus) {}

                @Override
                public void sessionClosed(final IoSession ioSession) {}
            } );
        }
    }
}
