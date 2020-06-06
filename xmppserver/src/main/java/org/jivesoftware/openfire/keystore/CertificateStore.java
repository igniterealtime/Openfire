package org.jivesoftware.openfire.keystore;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jivesoftware.util.CertificateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * A wrapper class for a Java store of certificates, its metadata (password, location) and related functionality.
 *
 * A subclass of this class exists for each of the two distinct types of key store.
 * <ul>
 *     <li>one that is used to provide credentials, an <em>identity store</em>, in {@link IdentityStore}</li>
 *     <li>one that is used to verify credentials, a <em>trust store</em>, in {@link TrustStore}</li>
 * </ul>
 *
 * Note that in Java terminology, an identity store is commonly referred to as a 'key store', while the same name is
 * also used to identify the generic certificate store. To have clear distinction between common denominator and each of
 * the specific types, this implementation uses the terms "certificate store", "identity store" and "trust store".
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public abstract class CertificateStore
{
    private static final Logger Log = LoggerFactory.getLogger( CertificateStore.class );

    protected static final Provider PROVIDER = new BouncyCastleProvider();

    static
    {
        // Add the BC provider to the list of security providers
        Security.addProvider( PROVIDER );
    }

    protected final KeyStore store;
    protected final CertificateStoreConfiguration configuration;

    public CertificateStore( CertificateStoreConfiguration configuration, boolean createIfAbsent ) throws CertificateStoreConfigException
    {
        if (configuration == null)
        {
            throw new IllegalArgumentException( "Argument 'configuration' cannot be null." );
        }

        this.configuration = configuration;
        try
        {
            final File file = configuration.getFile();

            if ( createIfAbsent && !file.exists() )
            {
                try ( final FileOutputStream os = new FileOutputStream( file.getPath() ) )
                {
                    store = KeyStore.getInstance( configuration.getType() );
                    store.load( null, configuration.getPassword() );
                    store.store( os, configuration.getPassword() );
                }
            }
            else
            {
                try ( final FileInputStream is = new FileInputStream( file ) )
                {
                    store = KeyStore.getInstance( configuration.getType() );
                    store.load( is, configuration.getPassword() );
                }
            }
        }
        catch ( IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException ex )
        {
            throw new CertificateStoreConfigException( "Unable to load store of type '" + configuration.getType() + "' from file '" + configuration.getFile() + "'", ex );
        }
    }

    /**
     * Reloads the content of the store from disk. Useful when the store content has been modified outside of the
     * Openfire process, or when changes that have not been persisted need to be undone.
     * @throws CertificateStoreConfigException if the store could not be reloaded
     */
    public void reload() throws CertificateStoreConfigException
    {
        try ( final FileInputStream is = new FileInputStream( configuration.getFile() ) )
        {
            store.load( is, configuration.getPassword() );
            CertificateManager.fireCertificateStoreChanged( this );
        }
        catch ( IOException | NoSuchAlgorithmException | CertificateException ex )
        {
            throw new CertificateStoreConfigException( "Unable to reload store in '" + configuration.getFile() + "'", ex );
        }
    }

    /**
     * Saves the current state of the store to disk. Useful when certificates have been added or removed from the
     * store.
     * @throws CertificateStoreConfigException of the configuration could not be persisted
     */
    public void persist() throws CertificateStoreConfigException
    {
        try ( final FileOutputStream os = new FileOutputStream( configuration.getFile() ) )
        {
            store.store( os, configuration.getPassword() );
        }
        catch ( NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException ex )
        {
            throw new CertificateStoreConfigException( "Unable to save changes to store in '" + configuration.getFile() + "'", ex );
        }
    }

    /**
     * Copies the file that is the persistent storage for this store to a new file in the backup location.
     *
     * @return The path in which the backup was created, or null if the creation of the backup failed.
     */
    public Path backup()
    {
        final String postfix = "." + new Date().getTime();
        final Path original = configuration.getFile().toPath();
        final Path backup = configuration.getBackupDirectory().toPath().resolve( original.getFileName() + postfix );

        Log.info( "Creating a backup of {} in {}.", original, backup );
        try
        {
            Files.copy( original, backup );
            return backup;
        }
        catch ( IOException e )
        {
            Log.error( "An error occurred creating a backup of {} in {}!", original, backup, e );
        }
        return null;
    }

    /**
     * Returns a collection of all x.509 certificates in this store. Certificates returned by this method can be of any
     * state (eg: invalid, on a revocation list, etc).
     *
     * @return A collection (possibly empty, never null) of all certificates in this store, mapped by their alias.
     * @throws KeyStoreException if a keystore has not been initialized
     */
    public Map<String, X509Certificate> getAllCertificates() throws KeyStoreException
    {
        final Map<String, X509Certificate> results = new HashMap<>();

        for ( final String alias : Collections.list( store.aliases() ) )
        {
            final Certificate certificate = store.getCertificate( alias );
            if ( !( certificate instanceof X509Certificate ) )
            {
                continue;
            }

            results.put( alias, (X509Certificate) certificate );
        }

        return results;
    }

    /**
     * Deletes an entry (by entry) in this store. All information related to this entry will be removed, including
     * certificates and keys.
     *
     * When the store does not contain an entry that matches the provided alias, this method does nothing.
     *
     * @param alias The alias for which to delete an entry (cannot be null or empty).
     * @throws CertificateStoreConfigException if the entry could not be deleted
     */
    public void delete( String alias ) throws CertificateStoreConfigException
    {
        // Input validation
        if ( alias == null || alias.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'alias' cannot be null or an empty String." );
        }

        try
        {
            if ( !store.containsAlias( alias ) )
            {
                Log.info( "Unable to delete certificate for alias '" + alias + "' from store, as the store does not contain a certificate for that alias." );
                return;
            }

            store.deleteEntry( alias );
            persist();
        }
        catch ( CertificateStoreConfigException | KeyStoreException e )
        {
            reload(); // reset state of the store.
            throw new CertificateStoreConfigException( "Unable to delete the certificate from the identity store.", e );

        }
    }

    public KeyStore getStore()
    {
        return store;
    }

    public CertificateStoreConfiguration getConfiguration()
    {
        return configuration;
    }
}
