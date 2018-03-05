package org.jivesoftware.openfire.keystore;

import org.bouncycastle.bcpg.ElGamalSecretBCPGKey;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A manager of certificate stores.
 *
 */
// TODO Code duplication should be reduced.
// TODO Allow changing the store type.
public class CertificateStoreManager extends BasicModule
{
    private final static Logger Log = LoggerFactory.getLogger( CertificateStoreManager.class );

    private final ConcurrentMap<ConnectionType, CertificateStoreConfiguration> typeToTrustStore    = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConnectionType, CertificateStoreConfiguration> typeToIdentityStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<CertificateStoreConfiguration, IdentityStore>  identityStores      = new ConcurrentHashMap<>();
    private final ConcurrentMap<CertificateStoreConfiguration, TrustStore>     trustStores         = new ConcurrentHashMap<>();

    private CertificateStoreWatcher storeWatcher;

    public CertificateStoreManager( )
    {
        super( "Certificate Store Manager" );
    }

    @Override
    public synchronized void initialize( XMPPServer server )
    {
        super.initialize( server );

        storeWatcher = new CertificateStoreWatcher();

        for ( final ConnectionType type : ConnectionType.values() )
        {
            try
            {
                Log.debug( "(identity store for connection type '{}') Initializing store...", type );
                final CertificateStoreConfiguration identityStoreConfiguration = getIdentityStoreConfiguration( type );
                if ( !identityStores.containsKey( identityStoreConfiguration ) )
                {
                    final IdentityStore store = new IdentityStore( identityStoreConfiguration, false );
                    identityStores.put( identityStoreConfiguration, store );
                    storeWatcher.watch( store );
                }
                typeToIdentityStore.put( type, identityStoreConfiguration );
            }
            catch ( CertificateStoreConfigException | IOException e )
            {
                Log.warn( "(identity store for connection type '{}') Unable to instantiate store ", type, e );
            }

            try
            {
                Log.debug( "(trust store for connection type '{}') Initializing store...", type );
                final CertificateStoreConfiguration trustStoreConfiguration = getTrustStoreConfiguration( type );
                if ( !trustStores.containsKey( trustStoreConfiguration ) )
                {
                    final TrustStore store = new TrustStore( trustStoreConfiguration, false );
                    trustStores.put( trustStoreConfiguration, store );
                    storeWatcher.watch( store );
                }
                typeToTrustStore.put( type, trustStoreConfiguration );
            }
            catch ( CertificateStoreConfigException | IOException e )
            {
                Log.warn( "(trust store for connection type '{}') Unable to instantiate store ", type, e );
            }
        }
    }

    @Override
    public synchronized void destroy()
    {
        storeWatcher.destroy();
        typeToIdentityStore.clear();
        typeToTrustStore.clear();
        identityStores.clear();
        trustStores.clear();
        super.destroy();
    }

    public IdentityStore getIdentityStore( ConnectionType type )
    {
        final CertificateStoreConfiguration configuration = typeToIdentityStore.get( type );
        if (configuration == null) {
            return null;
        }
        return identityStores.get( configuration );
    }

    public TrustStore getTrustStore( ConnectionType type )
    {
        final CertificateStoreConfiguration configuration = typeToTrustStore.get( type );
        if (configuration == null) {
            return null;
        }
        return trustStores.get( configuration );
    }

    public void replaceIdentityStore( ConnectionType type, CertificateStoreConfiguration configuration, boolean createIfAbsent ) throws CertificateStoreConfigException
    {
        if ( type == null)
        {
            throw new IllegalArgumentException( "Argument 'type' cannot be null." );
        }
        if ( configuration == null)
        {
            throw new IllegalArgumentException( "Argument 'configuration' cannot be null." );
        }

        final CertificateStoreConfiguration oldConfig = typeToIdentityStore.get( type ); // can be null if persisted properties are invalid

        if ( oldConfig == null || !oldConfig.equals( configuration ) )
        {
            // If the new store is not already being used by any other type, it'll need to be registered.
            if ( !identityStores.containsKey( configuration ) )
            {
                // This constructor can throw an exception. If it does, the state of the manager should not have already changed.
                final IdentityStore store = new IdentityStore( configuration, createIfAbsent );
                identityStores.put( configuration, store );
                storeWatcher.watch( store );
            }

            typeToIdentityStore.put( type, configuration );


            // If the old store is not used by any other type, it can be shut down.
            if ( oldConfig != null && !typeToIdentityStore.containsValue( oldConfig ) )
            {
                final IdentityStore store = identityStores.remove( oldConfig );
                if ( store != null )
                {
                    storeWatcher.unwatch( store );
                }
            }

            // Update all connection listeners that were using the old configuration.
            final ConnectionManagerImpl connectionManager = ((ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager());
            for ( ConnectionListener connectionListener : connectionManager.getListeners( type ) ) {
                try {
                    connectionListener.setIdentityStoreConfiguration( configuration );
                } catch ( RuntimeException e ) {
                    Log.warn( "An exception occurred while trying to update the identity store configuration for connection type '" + type + "'", e );
                }
            }
        }

        // Always store the new configuration in properties, to make sure that we override a potential fallback.
        JiveGlobals.setProperty( type.getPrefix() + "keystore", configuration.getFile().getPath() ); // FIXME ensure that this is relative to Openfire home!
        JiveGlobals.setProperty( type.getPrefix() + "keypass", new String( configuration.getPassword() ) );
    }

    public void replaceTrustStore( ConnectionType type, CertificateStoreConfiguration configuration, boolean createIfAbsent ) throws CertificateStoreConfigException
    {
        if ( type == null)
        {
            throw new IllegalArgumentException( "Argument 'type' cannot be null." );
        }
        if ( configuration == null)
        {
            throw new IllegalArgumentException( "Argument 'configuration' cannot be null." );
        }

        final CertificateStoreConfiguration oldConfig = typeToTrustStore.get( type ); // can be null if persisted properties are invalid

        if ( oldConfig == null || !oldConfig.equals( configuration ) )
        {
            // If the new store is not already being used by any other type, it'll need to be registered.
            if ( !trustStores.containsKey( configuration ) )
            {
                // This constructor can throw an exception. If it does, the state of the manager should not have already changed.
                final TrustStore store = new TrustStore( configuration, createIfAbsent );
                trustStores.put( configuration, store );
                storeWatcher.watch( store );
            }

            typeToTrustStore.put( type, configuration );


            // If the old store is not used by any other type, it can be shut down.
            if ( oldConfig != null && !typeToTrustStore.containsValue( oldConfig ) )
            {
                final TrustStore store = trustStores.remove( oldConfig );
                if ( store != null )
                {
                    storeWatcher.unwatch( store );
                }
            }

            // Update all connection listeners that were using the old configuration.
            final ConnectionManagerImpl connectionManager = ((ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager());
            for ( ConnectionListener connectionListener : connectionManager.getListeners( type ) ) {
                try {
                    connectionListener.setTrustStoreConfiguration( configuration );
                } catch ( RuntimeException e ) {
                    Log.warn( "An exception occurred while trying to update the trust store configuration for connection type '" + type + "'", e );
                }
            }

        }

        // Always store the new configuration in properties, to make sure that we override a potential fallback.
        JiveGlobals.setProperty( type.getPrefix() + "truststore", configuration.getFile().getPath() ); // FIXME ensure that this is relative to Openfire home!
        JiveGlobals.setProperty( type.getPrefix() + "trustpass", new String( configuration.getPassword() )  );
    }

    public CertificateStoreConfiguration getIdentityStoreConfiguration( ConnectionType type ) throws IOException
    {
        // Getting individual properties might use fallbacks. It is assumed (but not asserted) that each property value
        // is obtained from the same connectionType (which is either the argument to this method, or one of its
        // fallbacks.
        final String keyStoreType = getKeyStoreType( type );
        final String password = getIdentityStorePassword( type );
        final String location = getIdentityStoreLocation( type );
        final File file = canonicalize( location );

        return new CertificateStoreConfiguration( keyStoreType, file, password.toCharArray() );
    }

    public CertificateStoreConfiguration getTrustStoreConfiguration( ConnectionType type ) throws IOException
    {
        // Getting individual properties might use fallbacks. It is assumed (but not asserted) that each property value
        // is obtained from the same connectionType (which is either the argument to this method, or one of its
        // fallbacks.
        final String keyStoreType = getKeyStoreType( type );
        final String password = getTrustStorePassword( type );
        final String location = getTrustStoreLocation( type );
        final File file = canonicalize( location );

        return new CertificateStoreConfiguration( keyStoreType, file, password.toCharArray() );
    }

    /**
     * The KeyStore type (jks, jceks, pkcs12, etc) for the identity and trust store for connections created by this
     * listener.
     *
     * @return a store type (never null).
     * @see <a href="https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyStore">Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     */
    static String getKeyStoreType( ConnectionType type )
    {
        final String propertyName = type.getPrefix() + "storeType";
        final String defaultValue = "jks";

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getKeyStoreType( type.getFallback() ) ).trim();
        }
    }

    static void setKeyStoreType( ConnectionType type, String keyStoreType )
    {
        // Always set the property explicitly even if it appears the equal to the old value (the old value might be a fallback value).
        JiveGlobals.setProperty( type.getPrefix() + "storeType", keyStoreType );

        final String oldKeyStoreType = getKeyStoreType( type );
        if ( oldKeyStoreType.equals( keyStoreType ) )
        {
            Log.debug( "Ignoring KeyStore type change request (to '{}'): listener already in this state.", keyStoreType );
            return;
        }

        Log.debug( "Changing KeyStore type from '{}' to '{}'.", oldKeyStoreType, keyStoreType );
    }

    /**
     * The password of the identity store for connection created by this listener.
     *
     * @return a password (never null).
     */
    static String getIdentityStorePassword( ConnectionType type )
    {
        final String propertyName = type.getPrefix() + "keypass";
        final String defaultValue = "changeit";

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getIdentityStorePassword( type.getFallback() ) ).trim();
        }
    }

    /**
     * The password of the trust store for connections created by this listener.
     *
     * @return a password (never null).
     */
    static String getTrustStorePassword( ConnectionType type )
    {
        final String propertyName = type.getPrefix() + "trustpass";
        final String defaultValue = "changeit";

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getTrustStorePassword( type.getFallback() ) ).trim();
        }
    }

    /**
     * The location (relative to OPENFIRE_HOME) of the identity store for connections created by this listener.
     *
     * @return a path (never null).
     */
    static String getIdentityStoreLocation( ConnectionType type )
    {
        final String propertyName = type.getPrefix()  + "keystore";
        final String defaultValue = "resources" + File.separator + "security" + File.separator + "keystore";

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getIdentityStoreLocation( type.getFallback() ) ).trim();
        }
    }

    /**
     * The location (relative to OPENFIRE_HOME) of the trust store for connections created by this listener.
     *
     * @return a path (never null).
     */
    static String getTrustStoreLocation( ConnectionType type )
    {
        final String propertyName = type.getPrefix()  + "truststore";
        final String defaultValue;

        // OF-1191: For client-oriented connection types, Openfire traditionally uses a different truststore.
        if ( Arrays.asList( ConnectionType.SOCKET_C2S, ConnectionType.BOSH_C2S, ConnectionType.WEBADMIN ).contains( type ) )
        {
            defaultValue = "resources" + File.separator + "security" + File.separator + "client.truststore";
        }
        else
        {
            defaultValue = "resources" + File.separator + "security" + File.separator + "truststore";
        }

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getTrustStoreLocation( type.getFallback() ) ).trim();
        }
    }

    /**
     * Canonicalizes a path. When the provided path is a relative path, it is interpreted as to be relative to the home
     * directory of Openfire.
     *
     * @param path A path (cannot be null)
     * @return A canonical representation of the path.
     */
    static File canonicalize( String path ) throws IOException
    {
        File file = new File( path );
        if (!file.isAbsolute()) {
            file = new File( JiveGlobals.getHomeDirectory() + File.separator + path );
        }

        return file;
    }
}
