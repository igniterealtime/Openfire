/**
 * $RCSfile$
 * $Revision: 1217 $
 * $Date: 2005-04-11 18:11:06 -0300 (Mon, 11 Apr 2005) $
 * <p/>
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.openfire.keystore.*;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Configuration of Openfire's SSL settings.
 *
 * Openfire distinguishes up to three distinct sets of certificate stores.
 *
 * <ul>
 *     <li>"Socket" - TCP-based XMPP communication (examples: desktop XMPP clients, server-to-server federation);</li>
 *     <li>"BOSH" - HTTP-based XMPP communication (examples: most mobile clients, web-based clients);</li>
 *     <li>"Administrative" - non-XMPP based communication (example: the web-based admin panel)</li>
 * </ul>
 *
 * By default, the same set of stores is reused for all three purposes.
 *
 * A set consists of three stores: one key store and two trust stores.
 *
 * <em>key store</em>
 * Contains certificates that identify this instance of Openfire. On request, these certificates are transmitted to
 * other parties which use these certificates to identify your server,
 *
 * <em>server-to-server trust store</em>
 * Contains certificates that identify remote servers that you choose to trust (applies to server-to-server federation).
 *
 * <em>client-to-server trust store</em>
 * Contains certificates that identify clients that you choose to trust (applies to mutual authentication). By default,
 * the client-to-server trust store that ships with Openfire is empty.
 *
 * @author Iain Shigeoka
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class SSLConfig
{
    private static final Logger Log = LoggerFactory.getLogger( SSLConfig.class );

    private static SSLConfig INSTANCE;

    public static synchronized SSLConfig getInstance()
    {
        if (INSTANCE == null) {
            try
            {
                INSTANCE = new SSLConfig();
            }
            catch ( CertificateStoreConfigException | NoSuchAlgorithmException | IOException ex )
            {
                Log.error( "Unable to instantiate SSL Configuration!", ex );
            }
        }

        return INSTANCE;
    }

    /**
     * An utility method that is short-hand for getInstance().getStoreConfig(purpose).getStore();
     * @param purpose The purpose for which to return a store.
     * @return a store (never null).
     */
    public static synchronized KeyStore getStore( Purpose purpose )
    {
        return getInstance().getStoreConfig( purpose ).getStore();
    }

    /**
     * Openfire allows a store to be re-used for multiple purposes. This method will find the store used for the
     * provided purpose, and based on that will return all <em>other</em> purposes for which the same store is used.
     *
     * @param purpose The purpose for which to find a store (cannot be null).
     * @return all <em>other</em> purposes for which the store is used (never null, but possibly an empty collection).
     * @throws IOException
     */
    public Set<Purpose> getOtherPurposesForSameStore( Purpose purpose ) throws IOException
    {
        if ( purpose == null )
        {
            throw new IllegalArgumentException( "Argument 'purpose' cannot be null." );
        }

        final Set<Purpose> results = new HashSet<>();

        final String location = getLocation( purpose );
        for ( Map.Entry<Purpose, String> entry : locationByPurpose.entrySet() )
        {
            if ( entry.getValue().equalsIgnoreCase( location ) ) {
                results.add( entry.getKey() );
            }
        }

        return results;
    }

    private final ConcurrentMap<Purpose, String> locationByPurpose = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CertificateStoreConfig> storesByLocation = new ConcurrentHashMap<>();

    public static String getNonCanonicalizedLocation(Purpose purpose)
    {
        final String path;
        switch ( purpose )
        {
            // Identity store for socket-based IO (this is the default identity store)
            case SOCKETBASED_IDENTITYSTORE:
                path = JiveGlobals.getProperty( "xmpp.socket.ssl.keystore", "resources" + File.separator + "security" + File.separator + "keystore" );
                break;

            // Identity store for BOSH-based IO (falls back to the default identity store when not configured)
            case BOSHBASED_IDENTITYSTORE:
                path = JiveGlobals.getProperty( "xmpp.bosh.ssl.keystore", getNonCanonicalizedLocation( Purpose.SOCKETBASED_IDENTITYSTORE ) );
                break;

            // Identity store for administrative IO (falls back to the default identity store when not configured)
            case ADMINISTRATIVE_IDENTITYSTORE:
                path = JiveGlobals.getProperty( "admin.ssl.keystore", getNonCanonicalizedLocation( Purpose.SOCKETBASED_IDENTITYSTORE ) );
                break;

            // Identity store for admin panel (falls back to the administrative identity store when not configured)
            case WEBADMIN_IDENTITYSTORE:
                path = JiveGlobals.getProperty( "admin.web.ssl.keystore", getNonCanonicalizedLocation( Purpose.ADMINISTRATIVE_IDENTITYSTORE ) );
                break;

            // server-to-server trust store (This is the only / default S2S trust store. S2S over BOSH is unsupported by Openfire).
            case SOCKETBASED_S2S_TRUSTSTORE:
                path = JiveGlobals.getProperty( "xmpp.socket.ssl.truststore", "resources" + File.separator + "security" + File.separator + "truststore" );
                break;

            // client-to-server trust store for socket-based IO (This is the default C2S trust store).
            case SOCKETBASED_C2S_TRUSTSTORE:
                path = JiveGlobals.getProperty( "xmpp.socket.ssl.client.truststore", "resources" + File.separator + "security" + File.separator + "client.truststore" );
                break;

            // client-to-server trust store for BOSH-based IO (falls back to the default C2S trust store when not configured).
            case BOSHBASED_C2S_TRUSTSTORE:
                path = JiveGlobals.getProperty( "xmpp.bosh.ssl.client.truststore", getNonCanonicalizedLocation( Purpose.SOCKETBASED_C2S_TRUSTSTORE ) );
                break;

            // Administrative trust store (falls back to the default trust store when not configured)
            case ADMINISTRATIVE_TRUSTSTORE:
                path = JiveGlobals.getProperty( "admin.ssl.truststore", getNonCanonicalizedLocation( Purpose.SOCKETBASED_S2S_TRUSTSTORE ) );
                break;

            // Trust store for admin panel (falls back to the administrative trust store when not configured)
            case WEBADMIN_TRUSTSTORE:
                path = JiveGlobals.getProperty( "admin.web.ssl.truststore", getNonCanonicalizedLocation( Purpose.ADMINISTRATIVE_TRUSTSTORE ) );
                break;

            default:
                throw new IllegalStateException( "Unrecognized purpose: " + purpose );
        }
        return path;
    }

    public static String getLocation(Purpose purpose) throws IOException
    {
        return canonicalize( getNonCanonicalizedLocation( purpose ) );
    }

    protected String getPassword(Purpose purpose){
        switch ( purpose ) {
            case SOCKETBASED_IDENTITYSTORE:
                return JiveGlobals.getProperty( "xmpp.socket.ssl.keypass", "changeit" ).trim();

            case BOSHBASED_IDENTITYSTORE:
                return JiveGlobals.getProperty( "xmpp.bosh.ssl.keypass", getPassword( Purpose.SOCKETBASED_IDENTITYSTORE ) ).trim();

            case ADMINISTRATIVE_IDENTITYSTORE:
                return JiveGlobals.getProperty( "admin.ssl.keypass",  getPassword( Purpose.SOCKETBASED_IDENTITYSTORE ) ).trim();

            case WEBADMIN_IDENTITYSTORE:
                return JiveGlobals.getProperty( "admin.web.ssl.keypass", getPassword( Purpose.ADMINISTRATIVE_IDENTITYSTORE ) ).trim();

            case SOCKETBASED_S2S_TRUSTSTORE:
                return JiveGlobals.getProperty( "xmpp.socket.ssl.trustpass", "changeit" ).trim();

            case SOCKETBASED_C2S_TRUSTSTORE:
                return JiveGlobals.getProperty( "xmpp.socket.ssl.client.trustpass", "changeit" ).trim();

            case BOSHBASED_C2S_TRUSTSTORE:
                return JiveGlobals.getProperty( "xmpp.bosh.ssl.client.trustpass", getPassword( Purpose.SOCKETBASED_C2S_TRUSTSTORE ) ).trim();

            case ADMINISTRATIVE_TRUSTSTORE:
                return JiveGlobals.getProperty( "admin.ssl.trustpass", getPassword( Purpose.SOCKETBASED_S2S_TRUSTSTORE ) ).trim();

            case WEBADMIN_TRUSTSTORE:
                return JiveGlobals.getProperty( "admin..web.ssl.trustpass", getPassword( Purpose.ADMINISTRATIVE_TRUSTSTORE ) ).trim();

            default:
                throw new IllegalStateException( "Unrecognized purpose: " + purpose );
        }
    }

    protected String getStoreType(Purpose purpose) {

        // FIXME All properties should be unique, instead of being re-used by different stores.
        switch ( purpose )
        {
            case SOCKETBASED_IDENTITYSTORE:
                return JiveGlobals.getProperty( "xmpp.socket.ssl.storeType", "jks" ).trim();

            case BOSHBASED_IDENTITYSTORE:
                return JiveGlobals.getProperty( "xmpp.bosh.ssl.storeType", getStoreType( Purpose.SOCKETBASED_IDENTITYSTORE ) ).trim();

            case ADMINISTRATIVE_IDENTITYSTORE:
                return JiveGlobals.getProperty( "admin.ssl.storeType", getStoreType( Purpose.SOCKETBASED_IDENTITYSTORE ) ).trim();

            case WEBADMIN_IDENTITYSTORE:
                return JiveGlobals.getProperty( "admin.web.ssl.storeType", getStoreType( Purpose.ADMINISTRATIVE_IDENTITYSTORE ) ).trim();

            case SOCKETBASED_S2S_TRUSTSTORE:
                return JiveGlobals.getProperty( "xmpp.socket.ssl.storeType", "jks" ).trim();

            case SOCKETBASED_C2S_TRUSTSTORE:
                return JiveGlobals.getProperty( "xmpp.socket.ssl.client.storeType", "jks" ).trim();

            case BOSHBASED_C2S_TRUSTSTORE:
                return JiveGlobals.getProperty( "xmpp.bosh.ssl.client.storeType", getStoreType( Purpose.SOCKETBASED_C2S_TRUSTSTORE ) ).trim();

            case ADMINISTRATIVE_TRUSTSTORE:
                return JiveGlobals.getProperty( "admin.ssl.storeType", getStoreType( Purpose.SOCKETBASED_S2S_TRUSTSTORE ) ).trim();

            case WEBADMIN_TRUSTSTORE:
                return JiveGlobals.getProperty( "admin.web.ssl.storeType", getStoreType( Purpose.ADMINISTRATIVE_TRUSTSTORE ) ).trim();

            default:
                throw new IllegalStateException( "Unrecognized purpose: " + purpose );
        }
    }

    private SSLConfig() throws CertificateStoreConfigException, IOException, NoSuchAlgorithmException
    {
        for (Purpose purpose : Purpose.values()) {
            final String location = getLocation( purpose );
            if ( !storesByLocation.containsKey( location )) {
                final CertificateStoreConfig storeConfig;
                if (purpose.isTrustStore()) {
                    storeConfig = new TrustStoreConfig( getLocation( purpose ), getPassword( purpose ), getStoreType( purpose ), false );
                } else {
                    storeConfig = new IdentityStoreConfig( getLocation( purpose ), getPassword( purpose ), getStoreType( purpose ), false );
                }

                storesByLocation.put( location, storeConfig );
            }

            locationByPurpose.put(purpose, location);
        }
    }

    public CertificateStoreConfig getStoreConfig( Purpose purpose ) {
        if ( purpose == null ) {
            throw new IllegalArgumentException( "Argument 'purpose' cannot be null.");
        }
        return storesByLocation.get( locationByPurpose.get( purpose ) );
    }


    public void useStoreForPurpose( Purpose purpose, String location, String password, String storeType, boolean createIfAbsent ) throws IOException, CertificateStoreConfigException
    {
        final String newPath = canonicalize( location );
        final String oldPath = locationByPurpose.get( purpose );
        final CertificateStoreConfig oldConfig = storesByLocation.get( oldPath );

        // When this invocation does not change the current state, only trigger a reload.
        if (oldPath.equalsIgnoreCase( newPath ) && oldConfig.getPassword().equals( password ) && oldConfig.getType().equals( storeType ))
        {
            oldConfig.reload();
            return;
        }

        // Has a store already been loaded from this location?
        final boolean isKnown = storesByLocation.containsKey( newPath );

        final CertificateStoreConfig newConfig;
        if ( isKnown )
        {
            newConfig = storesByLocation.get( newPath );
        }
        else
        {
            if (purpose.isTrustStore()) {
                newConfig = new TrustStoreConfig( newPath, password, storeType, createIfAbsent );
            } else {
                newConfig = new IdentityStoreConfig( newPath, password, storeType, createIfAbsent );
            }
        }

        locationByPurpose.replace( purpose, newConfig.getCanonicalPath() );
        storesByLocation.replace( newConfig.getCanonicalPath(), newConfig );

        // Persist changes by modifying the Openfire properties.
        final Path locationToStore = Paths.get( newConfig.getPath() );

        switch ( purpose )
        {
            case SOCKETBASED_IDENTITYSTORE:
                JiveGlobals.setProperty( "xmpp.socket.ssl.keystore", locationToStore.toString() );
                JiveGlobals.setProperty( "xmpp.socket.ssl.keypass", password );
                JiveGlobals.setProperty( "xmpp.socket.ssl.storeType", storeType ); // FIXME also in use by SOCKETBASED_S2S_TRUSTSTORE
                break;

            case BOSHBASED_IDENTITYSTORE:
                JiveGlobals.setProperty( "xmpp.bosh.ssl.keystore", locationToStore.toString() );
                JiveGlobals.setProperty( "xmpp.bosh.ssl.keypass", password );
                JiveGlobals.setProperty( "xmpp.bosh.ssl.storeType", storeType );
                break;

            case ADMINISTRATIVE_IDENTITYSTORE:
                JiveGlobals.setProperty( "admin.ssl.keystore", locationToStore.toString() );
                JiveGlobals.setProperty( "admin.ssl.keypass", password );
                JiveGlobals.setProperty( "admin.ssl.storeType", storeType ); // FIXME also in use by ADMINISTRATIVE_TRUSTSTORE
                break;

            case WEBADMIN_IDENTITYSTORE:
                JiveGlobals.setProperty( "admin.web.ssl.keystore", locationToStore.toString() );
                JiveGlobals.setProperty( "admin.web.ssl.keypass", password );
                JiveGlobals.setProperty( "admin.web.ssl.storeType", storeType ); // FIXME also in use by WEBADMIN_TRUSTSTORE
                break;

            case SOCKETBASED_S2S_TRUSTSTORE:
                JiveGlobals.setProperty( "xmpp.socket.ssl.truststore", locationToStore.toString() );
                JiveGlobals.setProperty( "xmpp.socket.ssl.trustpass", password );
                JiveGlobals.setProperty( "xmpp.socket.ssl.storeType", storeType ); // FIXME also in use by SOCKETBASED_IDENTITYSTORE
                break;

            case SOCKETBASED_C2S_TRUSTSTORE:
                JiveGlobals.setProperty( "xmpp.socket.ssl.client.truststore", locationToStore.toString() );
                JiveGlobals.setProperty( "xmpp.socket.ssl.client.trustpass", password );
                JiveGlobals.setProperty( "xmpp.socket.ssl.client.storeType", storeType );
                break;

            case BOSHBASED_C2S_TRUSTSTORE:
                JiveGlobals.setProperty( "xmpp.bosh.ssl.client.truststore", locationToStore.toString() );
                JiveGlobals.setProperty( "xmpp.bosh.ssl.client.trustpass", password );
                JiveGlobals.setProperty( "xmpp.bosh.ssl.storeType", storeType );
                break;

            case ADMINISTRATIVE_TRUSTSTORE:
                JiveGlobals.setProperty( "admin.ssl.truststore", locationToStore.toString() );
                JiveGlobals.setProperty( "admin.ssl.trustpass", password );
                JiveGlobals.setProperty( "admin.ssl.storeType", storeType ); // FIXME also in use by ADMINISTRATIVE_IDENTITYSTORE

            case WEBADMIN_TRUSTSTORE:
                JiveGlobals.setProperty( "admin.web.ssl.truststore", locationToStore.toString() );
                JiveGlobals.setProperty( "admin.web.ssl.trustpass", password );
                JiveGlobals.setProperty( "admin.web.ssl.storeType", storeType ); // FIXME also in use by WEBADMIN_IDENTITYSTORE

            default:
                throw new IllegalStateException( "Unrecognized purpose: " + purpose );
        }

        // TODO notify listeners
    }

    public static String canonicalize( String path ) throws IOException
    {
        File file = new File( path );
        if (!file.isAbsolute()) {
            file = new File( JiveGlobals.getHomeDirectory() + File.separator + path );
        }

        return file.getCanonicalPath();
    }

}
