package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.net.SocketAcceptThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A connection acceptor that employs the legacy, pre-MINA/NIO socket implementation of Openfire.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @deprecated Used only for S2S, which should be be ported to NIO.
 */
@Deprecated
public class LegacyConnectionAcceptor extends ConnectionAcceptor
{
    private final Logger Log = LoggerFactory.getLogger( LegacyConnectionAcceptor.class );

    private SocketAcceptThread socketAcceptThread;

    /**
     * Constructs a new instance which will accept new connections based on the provided configuration.
     * <p/>
     * The provided configuration is expected to be immutable. ConnectionAcceptor instances are not expected to handle
     * changes in configuration. When such changes are to be applied, an instance is expected to be replaced.
     * <p/>
     * Newly instantiated ConnectionAcceptors will not accept any connections before {@link #start()} is invoked.
     *
     * @param configuration The configuration for connections to be accepted (cannot be null).
     */
    public LegacyConnectionAcceptor( ConnectionConfiguration configuration )
    {
        super( configuration );
    }

    /**
     * Starts this acceptor by binding the socket acceptor. When the acceptor is already started, a warning will be
     * logged and the method invocation is otherwise ignored.
     */
    @Override
    public synchronized void start()
    {
        if ( socketAcceptThread != null )
        {
            Log.warn( "Unable to start acceptor (it is already started!)" );
            return;
        }

        if ( configuration.getMaxThreadPoolSize() > 1 ) {
            Log.warn( "Configuration allows for up to " + configuration.getMaxThreadPoolSize() + " threads, although implementation is limited to exactly one." );
        }

        try {
            socketAcceptThread = new SocketAcceptThread(configuration.getPort(), configuration.getBindAddress());
            socketAcceptThread.setDaemon(true);
            socketAcceptThread.setPriority(Thread.MAX_PRIORITY);
            socketAcceptThread.start();

        }
        catch (Exception e) {
            System.err.println( "Error starting " + configuration.getPort() + ": " + e.getMessage() );
            Log.error( "Error starting: " + configuration.getPort(), e );

            // Reset for future use.
            if (socketAcceptThread != null ) {
                try {
                    socketAcceptThread.shutdown();
                } finally {
                    socketAcceptThread = null;
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
        if ( socketAcceptThread != null ) {
            try {
                socketAcceptThread.shutdown();
            } finally {
                socketAcceptThread = null;
            }
        }
    }

    @Override
    public synchronized boolean isIdle()
    {
        return socketAcceptThread != null; // We're not tracking actual sessions. This is a best effort response.
    }

    @Override
    public synchronized void reconfigure( ConnectionConfiguration configuration )
    {
        this.configuration = configuration;

        // nothing can be reloaded in this implementation.
    }
}
