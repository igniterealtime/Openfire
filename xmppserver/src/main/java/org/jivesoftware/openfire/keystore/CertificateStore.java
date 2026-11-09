/*
 * Copyright (C) 2017-2026 Ignite Realtime Foundation. All rights reserved.
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

    /**
     * Loads a store from its file, creating the file first if requested and it does not exist.
     *
     * When Openfire is configured not to modify its certificate stores ({@link CertificateStoreManager#READ_ONLY}), a
     * missing file is not created: a {@link CertificateStoreReadOnlyException} is thrown instead.
     *
     * @param configuration the configuration of the store (cannot be null).
     * @param createIfAbsent true to create the file when it does not exist.
     * @throws CertificateStoreConfigException if the store could not be loaded or created
     * @throws CertificateStoreReadOnlyException if the file would have to be created while the stores are read-only
     */
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
                checkWritable();

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
     *
     * When Openfire is configured not to modify its certificate stores ({@link CertificateStoreManager#READ_ONLY}),
     * nothing is saved: the store is reloaded from disk, discarding any change made in memory, and a
     * {@link CertificateStoreReadOnlyException} is thrown. The methods of this class that change the store refuse before
     * changing anything; this covers changes made directly to the {@link KeyStore} from {@link #getStore()}.
     *
     * @throws CertificateStoreConfigException of the configuration could not be persisted
     * @throws CertificateStoreReadOnlyException if the stores are read-only
     */
    public void persist() throws CertificateStoreConfigException
    {
        if ( isReadOnly() )
        {
            final CertificateStoreReadOnlyException refusal = readOnlyRefusal();
            try
            {
                reload(); // discard changes made in memory, so that memory matches the file.
            }
            catch ( CertificateStoreConfigException e )
            {
                // The store in memory may now be empty, so say so: the refusal alone would hide it.
                Log.warn( "Unable to reload {} after refusing to persist it (the certificate stores are read-only).", configuration.getFile(), e );
                refusal.addSuppressed( e );
            }
            throw refusal;
        }

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
     * When the store does not contain an entry that matches the provided alias, this method does nothing, unless the
     * stores are read-only, in which case it is refused like any other change.
     *
     * @param alias The alias for which to delete an entry (cannot be null or empty).
     * @throws CertificateStoreConfigException if the entry could not be deleted
     * @throws CertificateStoreReadOnlyException if the stores are read-only
     */
    public void delete( String alias ) throws CertificateStoreConfigException
    {
        // Input validation
        if ( alias == null || alias.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'alias' cannot be null or an empty String." );
        }
        checkWritable();

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
        catch ( CertificateStoreReadOnlyException e )
        {
            throw e; // persist() has already reloaded the store; report the refusal as such.
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

    /**
     * Refuses a change to this store when Openfire is configured not to modify its certificate stores
     * ({@link CertificateStoreManager#READ_ONLY}). Methods that change the store call this before changing anything, in
     * memory or on disk. The setting is read on every call, so a change of the setting takes effect at once.
     *
     * @throws CertificateStoreReadOnlyException if the stores are read-only
     */
    protected final void checkWritable() throws CertificateStoreReadOnlyException
    {
        if ( isReadOnly() )
        {
            throw readOnlyRefusal();
        }
    }

    /**
     * Returns whether Openfire is configured not to modify its certificate stores
     * ({@link CertificateStoreManager#READ_ONLY}). The setting is read on every call.
     *
     * @return true if the certificate stores are read-only
     */
    protected static boolean isReadOnly()
    {
        return CertificateStoreManager.READ_ONLY.getValue();
    }

    private CertificateStoreReadOnlyException readOnlyRefusal()
    {
        return new CertificateStoreReadOnlyException( "The store in '" + configuration.getFile() + "' is read-only: Openfire is configured not to modify the certificate stores it uses (" + CertificateStoreManager.READ_ONLY.getKey() + ")." );
    }
}
