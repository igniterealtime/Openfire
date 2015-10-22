package org.jivesoftware.openfire.net;

import org.jivesoftware.openfire.keystore.IdentityStoreConfig;
import org.jivesoftware.openfire.keystore.Purpose;
import org.jivesoftware.openfire.keystore.TrustStoreConfig;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.CertificateEventListener;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * A factory object for creating server sockets based on Openfire's SSL configuration.
 *
 * This implementation distinguishes between server sockets created for server-to-server ('s2s') and for
 * client-to-server ('c2s') communication. The primary difference between the two is the set of key and trust stores
 * that are used.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
// TODO: This code was split off from SSLConfig, but does not appear to be used! Remove?
public class SSLConfigSocketFactory
{
    private static final Logger Log = LoggerFactory.getLogger( SSLConfigSocketFactory.class );

    /**
     * The factory used for server-to-server connections.
     */
    private static SSLServerSocketFactory s2sFactory;

    /**
     * The factory used for client-to-server connections.
     */
    private static SSLServerSocketFactory c2sFactory;

    static
    {
        // Initial instantiation
        resetFactory();

        // Reset SSL factory when certificates are modified
        CertificateManager.addListener( new CertificateEventListener()
        {
            // Reset SSL factory since key stores have changed
            @Override
            public void certificateCreated( KeyStore keyStore, String alias, X509Certificate cert )
            {
                resetFactory();
            }

            @Override
            public void certificateDeleted( KeyStore keyStore, String alias )
            {
                resetFactory();
            }

            @Override
            public void certificateSigned( KeyStore keyStore, String alias, List<X509Certificate> certificates )
            {
                resetFactory();
            }
        } );
    }

    private static void resetFactory()
    {
        Log.debug( "(Re)setting the SSL-based socket factories." );

        try
        {
            final KeyStore s2sTrustStore = SSLConfig.getStore( Purpose.SOCKETBASED_S2S_TRUSTSTORE );
            final KeyStore c2sTrustStore = SSLConfig.getStore( Purpose.SOCKETBASED_C2S_TRUSTSTORE );

            s2sFactory = createS2SServerSocketFactory();

            if ( s2sTrustStore == c2sTrustStore )
            {
                c2sFactory = s2sFactory;
            }
            else
            {
                c2sFactory = createC2SServerSocketFactory();
            }

        }
        catch ( Exception e )
        {
            Log.error( "An exception occurred while (re)setting the SSL-based socket factories. Factories will be unavailable.", e );
            s2sFactory = null;
            c2sFactory = null;
        }
    }

    static SSLServerSocketFactory createC2SServerSocketFactory() throws NoSuchAlgorithmException, KeyManagementException
    {
        final IdentityStoreConfig identityStoreConfig = (IdentityStoreConfig) SSLConfig.getInstance().getStoreConfig( Purpose.SOCKETBASED_IDENTITYSTORE );
        final TrustStoreConfig trustStoreConfig = (TrustStoreConfig) SSLConfig.getInstance().getStoreConfig( Purpose.SOCKETBASED_C2S_TRUSTSTORE );

        final SSLContext context = SSLConfig.getSSLContext();
        context.init( identityStoreConfig.getKeyManagers(), trustStoreConfig.getTrustManagers(), new java.security.SecureRandom() );

        return context.getServerSocketFactory();
    }

    static SSLServerSocketFactory createS2SServerSocketFactory() throws NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, KeyStoreException, IOException
    {
        final IdentityStoreConfig identityStoreConfig = (IdentityStoreConfig) SSLConfig.getInstance().getStoreConfig( Purpose.SOCKETBASED_IDENTITYSTORE );
        final TrustStoreConfig trustStoreConfig = (TrustStoreConfig) SSLConfig.getInstance().getStoreConfig( Purpose.SOCKETBASED_S2S_TRUSTSTORE );

        final SSLContext context = SSLConfig.getSSLContext();
        context.init( identityStoreConfig.getKeyManagers(), trustStoreConfig.getTrustManagers(), new java.security.SecureRandom() );

        return context.getServerSocketFactory();
    }

    /**
     * Create a ServerSocket for server-to-server connections. This method will throw an IOException if it fails to
     * create a socket.
     *
     * @return the ServerSocket for a server-to-server connection (never null).
     * @throws IOException Failed to create a socket.
     */
    public static ServerSocket createS2SServerSocket( int port, InetAddress ifAddress ) throws IOException
    {
        if ( s2sFactory == null )
        {
            throw new IOException( "S2S server socket factory has not been initialized successfully." );
        }
        return s2sFactory.createServerSocket( port, -1, ifAddress );
    }

    /**
     * Create a ServerSocket for client-to-server connections. This method will throw an IOException if it fails to
     * create a socket.
     *
     * @return the ServerSocket for a client-to-server connection (never null).
     * @throws IOException Failed to create a socket.
     */
    public static ServerSocket createC2SServerSocket( int port, InetAddress ifAddress ) throws IOException
    {
        if ( c2sFactory == null )
        {
            throw new IOException( "C2S server socket factory has not been initialized successfully." );
        }
        return c2sFactory.createServerSocket( port, -1, ifAddress );
    }

    /**
     * Get the SSLServerSocketFactory for server-to-server connections. When the factory has not been initialized
     * successfully, this method returns null.
     *
     * @return the SSLServerSocketFactory for server-to-server connections (possibly null).
     */
    public static SSLServerSocketFactory getS2SServerSocketFactory()
    {
        return s2sFactory;
    }

    /**
     * Get the SSLServerSocketFactory for client-to-server connections. When the factory has not been initialized
     * successfully, this method returns null.
     *
     * @return the SSLServerSocketFactory for client-to-server connections (possibly null).
     */
    public static SSLServerSocketFactory getC2SServerSocketFactory()
    {
        return c2sFactory;
    }

    /**
     * Returns an array of cipher suite names that are enabled by default for server-to-server communication. When no
     * cipher suite names cannot be determined (ie: when the socket factory has not been initialized) this method
     * returns an empty array.
     *
     * @return array of cipher suites names, possibly empty, but never null.
     * @see SSLServerSocketFactory#getDefaultCipherSuites()
     */
    public static String[] getS2SDefaultCipherSuites()
    {
        if ( s2sFactory == null )
        {
            return new String[ 0 ];
        }
        return s2sFactory.getDefaultCipherSuites();
    }

    /**
     * Returns an array of cipher suite names that are available (but not necessarily enabled) for server-to-server
     * communication. When cipher suite names cannot be determined (ie: when the socket factory has not been
     * initialized) this method returns an empty array.
     *
     * @return array of cipher suites names, possibly empty, but never null.
     * @see SSLServerSocketFactory#getSupportedCipherSuites()
     */
    public static String[] getS2SSupportedCipherSuites()
    {
        if ( s2sFactory == null )
        {
            return new String[ 0 ];
        }
        return s2sFactory.getSupportedCipherSuites();
    }

    /**
     * Returns an array of cipher suite names that are enabled by default for client-to-server communication. When
     * cipher suite names cannot be determined (ie: when the socket factory has not been initialized) this method
     * returns an empty array.
     *
     * @return array of cipher suites names, possibly empty, but never null.
     * @see SSLServerSocketFactory#getDefaultCipherSuites()
     */
    public static String[] getC2SDefaultCipherSuites()
    {
        if ( c2sFactory == null )
        {
            return new String[ 0 ];
        }
        return c2sFactory.getDefaultCipherSuites();
    }

    /**
     * Returns an array of cipher suite names that are available (but not necessarily enabled) for client-to-server
     * communication. When cipher suite names cannot be determined (ie: when the socket factory has not been
     * initialized) this method returns an empty array.
     *
     * @return array of cipher suites names, possibly empty, but never null.
     * @see SSLServerSocketFactory#getSupportedCipherSuites()
     */
    public static String[] getC2SSupportedCipherSuites()
    {
        if ( c2sFactory == null )
        {
            return new String[ 0 ];
        }
        return c2sFactory.getSupportedCipherSuites();
    }
}
