package org.jivesoftware.openfire.keystore;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.CollectionUtils;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

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
        JiveGlobals.setProperty( type.getPrefix() + "keypass", new String( configuration.getPassword() ), true );
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
        JiveGlobals.setProperty( type.getPrefix() + "trustpass", new String( configuration.getPassword() ), true  );
    }

    public CertificateStoreConfiguration getIdentityStoreConfiguration( ConnectionType type ) throws IOException
    {
        // Getting individual properties might use fallbacks. It is assumed (but not asserted) that each property value
        // is obtained from the same connectionType (which is either the argument to this method, or one of its
        // fallbacks.
        final String keyStoreType = getIdentityStoreType( type );
        final String password = getIdentityStorePassword( type );
        final String location = getIdentityStoreLocation( type );
        final String backupDirectory = getIdentityStoreBackupDirectory( type );
        final File file = canonicalize( location );
        final File dir = canonicalize( backupDirectory );

        return new CertificateStoreConfiguration( keyStoreType, file, password.toCharArray(), dir );
    }

    public CertificateStoreConfiguration getTrustStoreConfiguration( ConnectionType type ) throws IOException
    {
        // Getting individual properties might use fallbacks. It is assumed (but not asserted) that each property value
        // is obtained from the same connectionType (which is either the argument to this method, or one of its
        // fallbacks.
        final String keyStoreType = getTrustStoreType( type );
        final String password = getTrustStorePassword( type );
        final String location = getTrustStoreLocation( type );
        final String backupDirectory = getTrustStoreBackupDirectory( type );
        final File file = canonicalize( location );
        final File dir = canonicalize( backupDirectory );

        return new CertificateStoreConfiguration( keyStoreType, file, password.toCharArray(), dir );
    }

    /**
     * Creates a backup of all files that back any of the certificate stores.
     *
     * Each certificate store can be configured to use a distinct file, as well as use a distinct backup location.
     * In practise, there will be a lot of overlap. This implementation creates a backup (by copying the file) for
     * each unique file/backup-location combination in the collection of all certificate stores.
     * @return the paths the store was backed up to
     * @throws IOException if the store could not be backed up
     */
    public Collection<Path> backup() throws IOException
    {
        // Create a collection that holds all of the certificate stores.
        final Collection<CertificateStore> allStores = new ArrayList<>();
        allStores.addAll( identityStores.values() );
        allStores.addAll( trustStores.values() );

        // Extract the set of unique file/backup-directory combinations. This prevents Openfire from creating duplicate backup files.
        final Set<CertificateStore> unique = allStores.stream().filter(
            CollectionUtils.distinctByKey(
                store -> store.getConfiguration().getFile().getAbsolutePath() + "|" + store.configuration.backupDirectory.getAbsolutePath() ) )
            .collect( Collectors.toSet() );

        // Trigger a backup for each of the unique combinations, and record the unique backup locations
        final Collection<Path> backups = unique.stream()
            .map( CertificateStore::backup )
            .filter( Objects::nonNull )
            .distinct()
            .sorted()
            .collect( Collectors.toList() );

        // If the number of backups is smaller than the number of unique file/backup-directory combinations, something went wrong!
        if ( unique.size() != backups.size() ) {
            throw new IOException( "Unable to create (all) backups!" );
        }

        return backups;
    }

    /**
     * The KeyStore type (jks, jceks, pkcs12, etc) for the trust store for connections of a particular type.
     *
     * @param type the connection type
     * @return a store type (never null).
     * @see <a href="https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyStore">Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     */
    public static String getTrustStoreType( ConnectionType type )
    {
        final String propertyName = type.getPrefix() + "trustStoreType";
        final String defaultValue = getKeyStoreType( type );

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getTrustStoreType( type.getFallback() ) ).trim();
        }
    }

    static void setTrustStoreType( ConnectionType type, String keyStoreType )
    {
        // Always set the property explicitly even if it appears the equal to the old value (the old value might be a fallback value).
        JiveGlobals.setProperty( type.getPrefix() + "trustStoreType", keyStoreType );

        final String oldKeyStoreType = getTrustStoreType( type );
        if ( oldKeyStoreType.equals( keyStoreType ) )
        {
            Log.debug( "Ignoring Trust Store type change request (to '{}'): listener already in this state.", keyStoreType );
            return;
        }

        Log.debug( "Changing Trust Store type from '{}' to '{}'.", oldKeyStoreType, keyStoreType );
        // TODO shouldn't this do something?
    }

    /**
     * The KeyStore type (jks, jceks, pkcs12, etc) for the identity store for connections of a particular type.
     *
     * @param type the connection type
     * @return a store type (never null).
     * @see <a href="https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyStore">Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     */
    public static String getIdentityStoreType( ConnectionType type )
    {
        final String propertyName = type.getPrefix() + "identityStoreType";
        final String defaultValue = getKeyStoreType( type );

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getIdentityStoreType( type.getFallback() ) ).trim();
        }
    }

    static void setIdentityStoreType( ConnectionType type, String keyStoreType )
    {
        // Always set the property explicitly even if it appears the equal to the old value (the old value might be a fallback value).
        JiveGlobals.setProperty( type.getPrefix() + "identityStoreType", keyStoreType );

        final String oldKeyStoreType = getIdentityStoreType( type );
        if ( oldKeyStoreType.equals( keyStoreType ) )
        {
            Log.debug( "Ignoring Identity Store type change request (to '{}'): listener already in this state.", keyStoreType );
            return;
        }

        Log.debug( "Changing Identity Store type from '{}' to '{}'.", oldKeyStoreType, keyStoreType );
        // TODO shouldn't this do something?
    }

    /**
     * The KeyStore type (jks, jceks, pkcs12, etc) for the identity and trust store for connections of a particular type.
     *
     * @param type the connection type
     * @return a store type (never null).
     * @see <a href="https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyStore">Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * @deprecated use either {@link #getTrustStoreType(ConnectionType)} or {@link #getIdentityStoreType(ConnectionType)}
     */
    @Deprecated
    public static String getKeyStoreType( ConnectionType type )
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
        // TODO shouldn't this do something?
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
        if ( type.isClientOriented() )
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
     * The location (relative to OPENFIRE_HOME) of the directory that holds backups for identity stores.
     *
     * @param type the connection type
     * @return a path (never null).
     */
    public static String getIdentityStoreBackupDirectory( ConnectionType type )
    {
        final String propertyName = type.getPrefix()  + "backup.keystore.location";
        final String defaultValue = "resources" + File.separator + "security" + File.separator + "archive" + File.separator;

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getIdentityStoreBackupDirectory( type.getFallback() ) ).trim();
        }
    }

    /**
     * The location (relative to OPENFIRE_HOME) of the directory that holds backups for trust stores.
     *
     * @param type the connection type
     * @return a path (never null).
     */
    public static String getTrustStoreBackupDirectory( ConnectionType type )
    {
        final String propertyName = type.getPrefix()  + "backup.truststore.location";
        final String defaultValue = "resources" + File.separator + "security" + File.separator + "archive" + File.separator;

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getTrustStoreBackupDirectory( type.getFallback() ) ).trim();
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

    /**
     * Checks if Openfire is configured to use the same set of three keystore files for all connection types (one
     * identity store, and two trust stores - one for client-based connections, and one for server/component-based
     * connections).
     *
     * This method will return 'false' when running Openfire without changes to its default keystore configuration. If
     * changes are made to use different keystores for at least one connection type, this method returns 'true'.
     *
     * @return true if Openfire is using different keystores based on the type of connection, false when running with the default store configuration.
     * @throws IOException if there was an IO error
     */
    public boolean usesDistinctConfigurationForEachType() throws IOException
    {
        CertificateStoreConfiguration identityStoreConfiguration = null;
        CertificateStoreConfiguration c2sTrustStoreConfiguration = null;
        CertificateStoreConfiguration s2sTrustStoreConfiguration = null;
        for ( ConnectionType connectionType : ConnectionType.values() )
        {
            // Identity stores
            if ( identityStoreConfiguration == null )
            {
                identityStoreConfiguration = getIdentityStoreConfiguration( connectionType );
            }
            if ( !identityStoreConfiguration.equals( getIdentityStoreConfiguration( connectionType ) ) )
            {
                return true;
            }

            // Client-to-Server trust stores
            if ( connectionType.isClientOriented() )
            {
                if ( c2sTrustStoreConfiguration == null )
                {
                    c2sTrustStoreConfiguration = getTrustStoreConfiguration( connectionType );
                }
                if ( !c2sTrustStoreConfiguration.equals( getTrustStoreConfiguration( connectionType ) ) )
                {
                    return true;
                }
            }
            else
            // Server-to-Server trust stores (includes component connections)
            {
                if ( s2sTrustStoreConfiguration == null )
                {
                    s2sTrustStoreConfiguration = getTrustStoreConfiguration( connectionType );
                }
                if ( !s2sTrustStoreConfiguration.equals( getTrustStoreConfiguration( connectionType ) ) )
                {
                    return true;
                }
            }
        }

        return false;
    }
}
