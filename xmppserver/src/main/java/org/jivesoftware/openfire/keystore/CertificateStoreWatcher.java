package org.jivesoftware.openfire.keystore;

import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Detects file-system based changes to (Java) keystores that back Openfire Certificate Stores, reloading them when
 * needed.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CertificateStoreWatcher
{
    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey( "cert.storewatcher.enabled" )
        .setDefaultValue( true )
        .setDynamic( false )
        .build();

    private static final Logger Log = LoggerFactory.getLogger( CertificateStoreWatcher.class );

    private final Map<CertificateStore, Path> watchedStores = new HashMap<>();

    private final Map<Path, WatchKey> watchedPaths = new HashMap<>();

    private WatchService storeWatcher;

    private final ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public CertificateStoreWatcher()
    {
        try
        {
            if ( !ENABLED.getValue() ) {
                Log.info( "Certificate update detection disabled by configuration." );
                storeWatcher = null;
                return;
            }

            storeWatcher = FileSystems.getDefault().newWatchService();

            executorService.submit( new Runnable()
            {
                @Override
                public void run()
                {
                    while ( !executorService.isShutdown() )
                    {
                        final WatchKey key;
                        try
                        {
                            key = storeWatcher.poll( 5, TimeUnit.SECONDS );
                        }
                        catch ( InterruptedException e )
                        {
                            // Interrupted. Stop waiting
                            continue;
                        }

                        if ( key == null )
                        {
                            continue;
                        }

                        for ( final WatchEvent<?> event : key.pollEvents() )
                        {
                            final WatchEvent.Kind<?> kind = event.kind();

                            // An OVERFLOW event can occur regardless of what kind of events the watcher was configured for.
                            if ( kind == StandardWatchEventKinds.OVERFLOW )
                            {
                                continue;
                            }

                            synchronized ( watchedStores )
                            {
                                // The filename is the context of the event.
                                final WatchEvent<Path> ev = (WatchEvent<Path>) event;
                                final Path changedFile = ((Path) key.watchable()).resolve( ev.context() );

                                // Can't use the value from the 'watchedStores' map, as that's the parent dir, not the keystore file!
                                for ( final CertificateStore store : watchedStores.keySet() )
                                {
                                    final Path storeFile = store.getConfiguration().getFile().toPath().normalize();
                                    if ( storeFile.equals( changedFile ) )
                                    {
                                        // Check if the modified file is usable.
                                        try ( final FileInputStream is = new FileInputStream( changedFile.toFile() ) )
                                        {
                                            final KeyStore tmpStore = KeyStore.getInstance( store.getConfiguration().getType() );
                                            tmpStore.load( is, store.getConfiguration().getPassword() );
                                        }
                                        catch ( EOFException e )
                                        {
                                            Log.debug( "The keystore is still being modified. Ignore for now. A new event should be thrown later.", e );
                                            break;
                                        }
                                        catch ( Exception e )
                                        {
                                            Log.debug( "Can't read the modified keystore with this config. Continue iterating over configs.", e );
                                            continue;
                                        }

                                        Log.info( "A file system change was detected. A(nother) certificate store that is backed by file '{}' will be reloaded.", storeFile );
                                        try
                                        {
                                            store.reload();
                                        }
                                        catch ( CertificateStoreConfigException e )
                                        {
                                            Log.warn( "An unexpected exception occurred while trying to reload a certificate store that is backed by file '{}'!", storeFile, e );
                                        }
                                    }
                                }
                            }
                        }

                        // Reset the key to receive further events.
                        key.reset();
                    }
                }
            });
        }

        catch ( UnsupportedOperationException e )
        {
            storeWatcher = null;
            Log.info( "This file system does not support watching file system objects for changes and events. Changes to Openfire certificate stores made outside of Openfire might not be detected. A restart of Openfire might be required for these to be applied." );
        }
        catch ( IOException e )
        {
            storeWatcher = null;
            Log.warn( "An exception occured while trying to create a service that monitors the Openfire certificate stores for changes. Changes to Openfire certificate stores made outside of Openfire might not be detected. A restart of Openfire might be required for these to be applied.", e );
        }
    }

    /**
     * Shuts down this watcher, releasing all resources.
     */
    public void destroy()
    {
        if ( executorService != null )
        {
            executorService.shutdown();
        }

        synchronized ( watchedStores )
        {
            if ( storeWatcher != null )
            {
                try
                {
                    storeWatcher.close();
                }
                catch ( IOException e )
                {
                    Log.warn( "Unable to close the watcherservice that is watching for file system changes to certificate stores.", e );
                }
            }
        }
    }

    /**
     * Start watching the file that backs a Certificate Store for changes, reloading the Certificate Store when
     * appropriate.
     *
     * This method does nothing when the file watching functionality is not supported by the file system.
     *
     * @param store The certificate store (cannot be null).
     */
    public void watch( CertificateStore store )
    {
        if ( store == null )
        {
            throw new IllegalArgumentException( "Argument 'store' cannot be null." );
        }

        if ( storeWatcher == null )
        {
            return;
        }

        final Path dir = store.getConfiguration().getFile().toPath().normalize().getParent();

        synchronized ( watchedStores )
        {
            watchedStores.put( store, dir );

            // Watch the directory that contains the keystore, if we're not already watching it.
            if ( !watchedPaths.containsKey( dir ) )
            {
                try
                {
                    // Ignoring deletion events, as those changes should be applied via property value changes.
                    final WatchKey watchKey = dir.register( storeWatcher, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE );
                    watchedPaths.put( dir, watchKey );
                }
                catch ( Throwable t )
                {
                    Log.warn( "Unable to add a watcher for a path that contains files that provides the backend storage for certificate stores. Changes to those files are unlikely to be picked up automatically. Path: {}", dir, t );
                    watchedStores.remove( store );
                }
            }
        }
    }

    /**
     * Stop watching the file that backs a Certificate Store for changes
     *
     * @param store The certificate store (cannot be null).
     */
    public synchronized void unwatch( CertificateStore store )
    {
        if ( store == null )
        {
            throw new IllegalArgumentException( "Argument 'store' cannot be null." );
        }

        synchronized ( watchedStores )
        {
            watchedStores.remove( store );
            final Path dir = store.getConfiguration().getFile().toPath().normalize().getParent();

            // Check if there are any other stores being watched in the same directory.
            if ( watchedStores.containsValue( dir ) )
            {
                return;
            }

            final WatchKey key = watchedPaths.remove( dir );
            if ( key != null )
            {
                key.cancel();
            }
        }
    }
}
