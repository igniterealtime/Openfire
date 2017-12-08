/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
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
package org.igniterealtime.openfire.plugins.certificatemanager;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.IdentityStore;
import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Automatically installs a private key with corresponding certificate chain in Openfire's identity store, by looking
 * for new files in a directory.
 *
 * This implementation will act when both a PEM-encoded private key file and a PEM-encoding certificate chain file are
 * added to the directory that's being watched.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DirectoryWatcher
{
    private static final Logger Log = LoggerFactory.getLogger( DirectoryWatcher.class );

    public static final String PROPERTY_WATCHED_PATH = "certificate-manager.directory-watcher.watched-path";
    public static final String PROPERTY_WATCHED_PATH_DEFAULT = JiveGlobals.getHomeDirectory() + File.separator + "resources" + File.separator + "security" + File.separator + "hotdeploy" + File.separator;
    public static final String PROPERTY_ENABLED = "certificate-manager.directory-watcher.enabled";
    public static final boolean PROPERTY_ENABLED_DEFAULT = true;
    public static final String PROPERTY_REPLACE = "certificate-manager.directory-watcher.replace";
    public static final boolean PROPERTY_REPLACE_DEFAULT = true;
    public static final String PROPERTY_DELETE = "certificate-manager.directory-watcher.delete";
    public static final boolean PROPERTY_DELETE_DEFAULT = false;

    private WatchService watchService;
    private ExecutorService executorService;

    public synchronized void start()
    {
        if ( watchService != null )
        {
            throw new IllegalStateException( "Cannot start - already started." );
        }

        if ( !JiveGlobals.getBooleanProperty( PROPERTY_ENABLED, PROPERTY_ENABLED_DEFAULT ) )
        {
            Log.info( "The TLS hot-deploy service is disabled by configuration." );
            return;
        }

        try
        {
            watchService = FileSystems.getDefault().newWatchService();

            final Path watchedPath = Paths.get( JiveGlobals.getProperty( PROPERTY_WATCHED_PATH, PROPERTY_WATCHED_PATH_DEFAULT ) );

            if ( !watchedPath.toFile().exists() && !watchedPath.toFile().mkdirs() )
            {
                Log.warn( "Unable to create path used for hot-deployment of certificates: {}", watchedPath );
            }
            watchedPath.register( watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE );

            Log.info( "Watching '{}' for updates for installed certificate chains and private keys.", watchedPath );
        }
        catch ( IOException e )
        {
            Log.error( "Unable to start watching path for certificate changes.", e );
        }

        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.submit( new Runnable()
        {
            @Override
            public void run()
            {
                while ( !executorService.isShutdown() )
                {
                    Path lastChangedCertificateChain = null;
                    long lastChangeCertificateChain = 0;

                    Path lastChangedPrivateKey = null;
                    long lastChangePrivateKey = 0;

                    final WatchKey key;
                    try
                    {
                        key = watchService.poll( 5, TimeUnit.SECONDS );
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

                        // The filename is the context of the event.
                        final WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        final Path changedFile = ((Path) key.watchable()).resolve( ev.context() );

                        final File file = changedFile.toFile();
                        if ( isCertificateChain( file ) )
                        {
                            Log.info( "Found a certificate chain file in the hot-deploy directory." );
                            lastChangeCertificateChain = System.currentTimeMillis();
                            lastChangedCertificateChain = changedFile;
                        }

                        if ( isPrivateKey( file ) )
                        {
                            Log.info( "Found a private key file in the hot-deploy directory." );
                            lastChangePrivateKey = System.currentTimeMillis();
                            lastChangedPrivateKey = changedFile;
                        }

                        // If both the private key and certificate chain files were updated, reload them.
                        if ( lastChangeCertificateChain > 0 && Math.abs( lastChangeCertificateChain - lastChangePrivateKey ) < 60000 )
                        {
                            Log.info( "Files containing both a private key as well as a certificate chain were recently added to the hot-deploy directory. Attempting to install them..." );
                            final IdentityStore identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( ConnectionType.SOCKET_C2S );

                            try
                            {
                                final String certsChain = new String( Files.readAllBytes( lastChangedCertificateChain ) );
                                final String privateKey = new String( Files.readAllBytes( lastChangedPrivateKey ) );

//                                if ( JiveGlobals.getBooleanProperty( PROPERTY_REPLACE, PROPERTY_REPLACE_DEFAULT ) )
//                                {
//                                    identityStore.replaceCertificate( certsChain, privateKey, null );
//                                }
//                                else
//                                {
                                    identityStore.installCertificate( certsChain, privateKey, null );
//                                }
                                SecurityAuditManager.getInstance().logEvent( "", "hot-deployed private key and certificate chain.", "A private key and coresponding certificate chain were automatically installed." );

                                Log.info( "Hot-deployment of certificate and private key was successful." );

                                if ( JiveGlobals.getBooleanProperty( PROPERTY_DELETE, PROPERTY_DELETE_DEFAULT ))
                                {
                                    if ( !lastChangedCertificateChain.toFile().delete() )
                                    {
                                        Log.info( "Unable to delete the hot-deployed certificate chain file." );
                                    }

                                    if ( !lastChangedPrivateKey.toFile().delete() )
                                    {
                                        Log.info( "Unable to delete the hot-deployed private key file." );
                                    }
                                }

                                key.reset(); // prevent re-loading the same files based on the same events.
                                break;
                            }
                            catch ( Exception e )
                            {
                                Log.warn( "Unable to hot-deploy certificate and private key.", e );
                                // Prevent another attempt
                                lastChangeCertificateChain = 0;
                                lastChangePrivateKey = 0;
                            }
                        }
                    }

                    // Reset the key to receive further events.
                    key.reset();
                }
            }
        });
    }

    public static boolean isPrivateKey( File file )
    {
        if ( file.isFile() && file.canRead() )
        {
            try ( final InputStream is = new FileInputStream( file ) )
            {
                CertificateManager.parsePrivateKey( is, "" );
                return true;
            }
            catch ( Exception e )
            {
                // This also catches events triggered by file modifications that are still underway.
                return false;
            }
        }
        return false;
    }

    public static boolean isCertificateChain( File file )
    {
        if ( file.isFile() && file.canRead() )
        {
            try ( final InputStream is = new FileInputStream( file ) )
            {
                final Collection<X509Certificate> x509Certificates = CertificateManager.parseCertificates( is );
                return !x509Certificates.isEmpty();
            }
            catch ( Exception e )
            {
                return false;
            }
        }
        return false;
    }

    public synchronized void stop()
    {
        if ( executorService != null )
        {
            executorService.shutdown();
            executorService = null;
        }

        if ( watchService != null )
        {
            try
            {
                watchService.close();
                watchService = null;
            }
            catch ( IOException e )
            {
                Log.warn( "Unable to close the watcherservice that is watching for file system changes to certificate stores.", e );
            }
        }
        Log.info( "Stopped watching for updates for installed certificate chains and private keys." );
    }
}
