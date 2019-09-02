package org.jivesoftware.openfire.net;

import org.jivesoftware.openfire.server.RemoteServerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class to generate Socket instances.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class SocketUtil
{
    private final static Logger Log = LoggerFactory.getLogger( SocketUtil.class );

    /**
     * Creates a socket connection to an XMPP domain.
     *
     * This implementation uses DNS SRV records to find a list of remote hosts for the XMPP domain (as implemented by
     * {@link DNSUtil#resolveXMPPDomain(String, int)}. It then iteratively tries to create a socket connection to each
     * of them, until one socket connection succeeds.
     *
     * Either the connected Socket instance is returned, or null if no connection could be established.
     *
     * Note that this method blocks while performing network IO. The timeout as defined by
     * {@link RemoteServerManager#getSocketTimeout()} is observed.
     *
     * @param xmppDomain The XMPP domain to connect to.
     * @param port The port to connect to when DNS resolution fails.
     * @return a Socket instance that is connected, or null.
     * @see DNSUtil#resolveXMPPDomain(String, int)
     */
    public static Map.Entry<Socket, Boolean> createSocketToXmppDomain( String xmppDomain, int port )
    {
        Log.debug( "Creating a socket connection to XMPP domain '{}' ...", xmppDomain );

        Log.debug( "Use DNS to resolve remote hosts for the provided XMPP domain '{}' (default port: {}) ...", xmppDomain, port );
        final List<DNSUtil.HostAddress> remoteHosts = DNSUtil.resolveXMPPDomain( xmppDomain, port );
        Log.debug( "Found {} host(s) for XMPP domain '{}'.", remoteHosts.size(), xmppDomain );
        remoteHosts.forEach( remoteHost -> Log.debug( "- {} ({})", remoteHost.toString(), (remoteHost.isDirectTLS() ? "direct TLS" : "no direct TLS" ) ) );

        Socket socket = null;
        final int socketTimeout = RemoteServerManager.getSocketTimeout();
        for ( DNSUtil.HostAddress remoteHost : remoteHosts )
        {
            final String realHostname = remoteHost.getHost();
            final int realPort = remoteHost.getPort();
            final boolean directTLS = remoteHost.isDirectTLS();

            try
            {
                // (re)initialize the socket.
                socket = new Socket();

                Log.debug( "Trying to create socket connection to XMPP domain '{}' using remote host: {}:{} (blocks up to {} ms) ...", xmppDomain, realHostname, realPort, socketTimeout );
                socket.connect( new InetSocketAddress( realHostname, realPort ), socketTimeout );
                Log.debug( "Successfully created socket connection to XMPP domain '{}' using remote host: {}:{}!", xmppDomain, realHostname, realPort );
                return new AbstractMap.SimpleEntry<>(socket, directTLS);
            }
            catch ( Exception e )
            {
                Log.debug( "An exception occurred while trying to create a socket connection to XMPP domain '{}' using remote host {}:{}", xmppDomain, realHostname, realPort, e );
                Log.warn( "Unable to create a socket connection to XMPP domain '{}' using remote host: {}:{}. Cause: {} (a full stacktrace is logged on debug level)", xmppDomain, realHostname, realPort, e.getMessage() );
                try
                {
                    if ( socket != null )
                    {
                        socket.close();
                        socket = null;
                    }
                }
                catch ( IOException ex )
                {
                    Log.debug( "An additional exception occurred while trying to close a socket when creating a connection to {}:{} failed.", realHostname, realPort, ex );
                }
            }
        }

        Log.warn( "Unable to create a socket connection to XMPP domain '{}': Unable to connect to any of its remote hosts.", xmppDomain );
        return null;
    }
}
