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

import org.apache.mina.filter.ssl.SslFilter;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.keystore.*;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Utility functions for TLS / SSL.
 *
 * @author Iain Shigeoka
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class SSLConfig
{
    private static final Logger Log = LoggerFactory.getLogger( SSLConfig.class );

    private final ConcurrentMap<Purpose, String> locationByPurpose = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CertificateStoreConfig> storesByLocation = new ConcurrentHashMap<>();

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
                ex.printStackTrace();
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
                    final boolean acceptSelfSigned = false; // TODO make configurable
                    final boolean checkValidity = true; ; // TODO make configurable
                    storeConfig = new TrustStoreConfig( getLocation( purpose ), getPassword( purpose ), getStoreType( purpose ), false, acceptSelfSigned, checkValidity );
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

//    public void useStoreForPurpose( Purpose purpose, String location, String password, String storeType, boolean createIfAbsent ) throws IOException, CertificateStoreConfigException
//    {
//        final String newPath = canonicalize( location );
//        final String oldPath = locationByPurpose.get( purpose );
//        final CertificateStoreConfig oldConfig = storesByLocation.get( oldPath );
//
//        // When this invocation does not change the current state, only trigger a reload.
//        if (oldPath.equalsIgnoreCase( newPath ) && oldConfig.getPassword().equals( password ) && oldConfig.getType().equals( storeType ))
//        {
//            oldConfig.reload();
//            return;
//        }
//
//        // Has a store already been loaded from this location?
//        final boolean isKnown = storesByLocation.containsKey( newPath );
//
//        final CertificateStoreConfig newConfig;
//        if ( isKnown )
//        {
//            newConfig = storesByLocation.get( newPath );
//        }
//        else
//        {
//            if (purpose.isTrustStore()) {
//                final boolean acceptSelfSigned = false; // TODO make configurable
//                final boolean checkValidity = true; ; // TODO make configurable
//                newConfig = new TrustStoreConfig( newPath, password, storeType, createIfAbsent, acceptSelfSigned, checkValidity );
//            } else {
//                newConfig = new IdentityStoreConfig( newPath, password, storeType, createIfAbsent );
//            }
//        }
//
//        locationByPurpose.replace( purpose, newConfig.getCanonicalPath() );
//        storesByLocation.replace( newConfig.getCanonicalPath(), newConfig );
//
//        // Persist changes by modifying the Openfire properties.
//        final Path locationToStore = Paths.get( newConfig.getPath() );
//
//        switch ( purpose )
//        {
//            case SOCKETBASED_IDENTITYSTORE:
//                JiveGlobals.setProperty( "xmpp.socket.ssl.keystore", locationToStore.toString() );
//                JiveGlobals.setProperty( "xmpp.socket.ssl.keypass", password );
//                JiveGlobals.setProperty( "xmpp.socket.ssl.storeType", storeType ); // FIXME also in use by SOCKETBASED_S2S_TRUSTSTORE
//                break;
//
//            case BOSHBASED_IDENTITYSTORE:
//                JiveGlobals.setProperty( "xmpp.bosh.ssl.keystore", locationToStore.toString() );
//                JiveGlobals.setProperty( "xmpp.bosh.ssl.keypass", password );
//                JiveGlobals.setProperty( "xmpp.bosh.ssl.storeType", storeType );
//                break;
//
//            case ADMINISTRATIVE_IDENTITYSTORE:
//                JiveGlobals.setProperty( "admin.ssl.keystore", locationToStore.toString() );
//                JiveGlobals.setProperty( "admin.ssl.keypass", password );
//                JiveGlobals.setProperty( "admin.ssl.storeType", storeType ); // FIXME also in use by ADMINISTRATIVE_TRUSTSTORE
//                break;
//
//            case WEBADMIN_IDENTITYSTORE:
//                JiveGlobals.setProperty( "admin.web.ssl.keystore", locationToStore.toString() );
//                JiveGlobals.setProperty( "admin.web.ssl.keypass", password );
//                JiveGlobals.setProperty( "admin.web.ssl.storeType", storeType ); // FIXME also in use by WEBADMIN_TRUSTSTORE
//                break;
//
//            case SOCKETBASED_S2S_TRUSTSTORE:
//                JiveGlobals.setProperty( "xmpp.socket.ssl.truststore", locationToStore.toString() );
//                JiveGlobals.setProperty( "xmpp.socket.ssl.trustpass", password );
//                JiveGlobals.setProperty( "xmpp.socket.ssl.storeType", storeType ); // FIXME also in use by SOCKETBASED_IDENTITYSTORE
//                break;
//
//            case SOCKETBASED_C2S_TRUSTSTORE:
//                JiveGlobals.setProperty( "xmpp.socket.ssl.client.truststore", locationToStore.toString() );
//                JiveGlobals.setProperty( "xmpp.socket.ssl.client.trustpass", password );
//                JiveGlobals.setProperty( "xmpp.socket.ssl.client.storeType", storeType );
//                break;
//
//            case BOSHBASED_C2S_TRUSTSTORE:
//                JiveGlobals.setProperty( "xmpp.bosh.ssl.client.truststore", locationToStore.toString() );
//                JiveGlobals.setProperty( "xmpp.bosh.ssl.client.trustpass", password );
//                JiveGlobals.setProperty( "xmpp.bosh.ssl.storeType", storeType );
//                break;
//
//            case ADMINISTRATIVE_TRUSTSTORE:
//                JiveGlobals.setProperty( "admin.ssl.truststore", locationToStore.toString() );
//                JiveGlobals.setProperty( "admin.ssl.trustpass", password );
//                JiveGlobals.setProperty( "admin.ssl.storeType", storeType ); // FIXME also in use by ADMINISTRATIVE_IDENTITYSTORE
//
//            case WEBADMIN_TRUSTSTORE:
//                JiveGlobals.setProperty( "admin.web.ssl.truststore", locationToStore.toString() );
//                JiveGlobals.setProperty( "admin.web.ssl.trustpass", password );
//                JiveGlobals.setProperty( "admin.web.ssl.storeType", storeType ); // FIXME also in use by WEBADMIN_IDENTITYSTORE
//
//            default:
//                throw new IllegalStateException( "Unrecognized purpose: " + purpose );
//        }
//
//        // TODO notify listeners
//    }

    public static String canonicalize( String path ) throws IOException
    {
        File file = new File( path );
        if (!file.isAbsolute()) {
            file = new File( JiveGlobals.getHomeDirectory() + File.separator + path );
        }

        return file.getCanonicalPath();
    }


    // TODO merge this with Purpose!
    public enum Type {
        SOCKET_S2S( "xmpp.socket.ssl.", null ),
        SOCKET_C2S( "xmpp.socket.ssl.client.", null ),
        BOSH_C2S( "xmpp.bosh.ssl.client.", SOCKET_C2S),
        ADMIN( "admin.ssl.", SOCKET_S2S),
        WEBADMIN( "admin.web.ssl.", ADMIN);

        String prefix;
        Type fallback;
        Type( String prefix, Type fallback) {
            this.prefix = prefix;
            this.fallback = fallback;
        }

        public String getPrefix()
        {
            return prefix;
        }

        public Type getFallback()
        {
            return fallback;
        }
    }

    public static SSLContext getSSLContext( final Type type ) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException
    {
        // TODO: allow different algorithms for different connection types (eg client/server/bosh etc)
        final String algorithm = JiveGlobals.getProperty( ConnectionSettings.Client.TLS_ALGORITHM, "TLS" );
        final Purpose idPurpose;
        final Purpose trustPurpose;
        switch ( type ) {
            case SOCKET_S2S:
                idPurpose = Purpose.SOCKETBASED_IDENTITYSTORE;
                trustPurpose = Purpose.SOCKETBASED_S2S_TRUSTSTORE;
                break;

            case SOCKET_C2S:
                idPurpose = Purpose.SOCKETBASED_IDENTITYSTORE;
                trustPurpose = Purpose.SOCKETBASED_C2S_TRUSTSTORE;
                break;

            case BOSH_C2S:
                idPurpose = Purpose.BOSHBASED_IDENTITYSTORE;
                trustPurpose = Purpose.BOSHBASED_C2S_TRUSTSTORE;
                break;

            case ADMIN:
                idPurpose = Purpose.ADMINISTRATIVE_IDENTITYSTORE;
                trustPurpose = Purpose.ADMINISTRATIVE_TRUSTSTORE;
                break;

            case WEBADMIN:
                idPurpose = Purpose.WEBADMIN_IDENTITYSTORE;
                trustPurpose = Purpose.WEBADMIN_TRUSTSTORE;
                break;

            default:
                throw new IllegalStateException( "Unsupported type: " + type );
        }

        final KeyManager[] keyManagers = ((IdentityStoreConfig) getInstance().getStoreConfig( idPurpose )).getKeyManagers();
        final TrustManager[] trustManagers = ((TrustStoreConfig) getInstance().getStoreConfig( trustPurpose )).getTrustManagers();

        final SSLContext sslContext = SSLContext.getInstance( algorithm );
        sslContext.init( keyManagers, trustManagers, new SecureRandom() );

        return sslContext;
    }

    /**
     * Creates an SSL Engine that is configured to use server mode when handshaking.
     *
     * For Openfire, an engine is of this mode used for most purposes (as Openfire is a server by nature).
     *
     * @param type The type of connectivity for which to configure a new SSLEngine instance. Cannot be null.
     * @param clientAuth indication of the desired level of client-sided authentication (mutual authentication). Cannot be null.
     * @return An initialized SSLEngine instance (never null).
     */
    public static SSLEngine getServerModeSSLEngine( Type type, Connection.ClientAuth clientAuth ) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException
    {
        final SSLEngine sslEngine = getSSLEngine( type );
        sslEngine.setUseClientMode( false );

        switch ( clientAuth )
        {
            case needed:
                sslEngine.setNeedClientAuth( true );
                break;

            case wanted:
                sslEngine.setWantClientAuth( true );
                break;

            case disabled:
                sslEngine.setWantClientAuth( false );
                break;
        }

        return sslEngine;
    }

    /**
     * Creates an SSL Engine that is configured to use client mode when handshaking.
     *
     * For Openfire, an engine of this mode is typically used when the server tries to connect to another server.
     *
     * @param type The type of connectivity for which to configure a new SSLEngine instance. Cannot be null.
     * @return An initialized SSLEngine instance (never null).
     */
    public static SSLEngine getClientModeSSLEngine( Type type ) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException
    {
        final SSLEngine sslEngine = getSSLEngine( type );
        sslEngine.setUseClientMode( true );

        return sslEngine;
    }

    /**
     * A utility method that implements the shared functionality of getClientModeSSLEngine and getServerModeSSLEngine.
     *
     * This method is used to initialize and pre-configure an instance of SSLEngine for a particular connection type.
     * The returned value lacks further configuration. In most cases, developers will want to use getClientModeSSLEngine
     * or getServerModeSSLEngine instead of this method.
     *
     * @param type The type of connectivity for which to pre-configure a new SSLEngine instance. Cannot be null.
     * @return A pre-configured SSLEngine (never null).
     */
    private static SSLEngine getSSLEngine( final Type type ) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException
    {
        final SSLContext sslContext = getSSLContext( type );

        final SSLEngine sslEngine = sslContext.createSSLEngine();
        configureProtocols( sslEngine, type );
        configureCipherSuites( sslEngine, type );

        return sslEngine;
    }

    /**
     * Enables a specific set of protocols in an SSLEngine instance.
     *
     * To determine what protocols to enable, this implementation first looks at a type-specific property. This property
     * can contain a comma-separated list of protocols that are to be enabled.
     *
     * When the property is not set (or when the property value is empty), the protocols that will be enabled are all
     * protocols supported by the SSLEngine for which the protocol name starts with "TLS".
     *
     * Note that the selection strategy is a different strategy than with cipher suites in configureCipherSuites(),
     * where the SSLEngine default gets filtered but not replaced.
     *
     * @param sslEngine The instance to configure. Cannot be null.
     * @param type The type of configuration to use (used to select the relevent property). Cannot be null.
     */
    private static void configureProtocols( SSLEngine sslEngine, Type type )
    {
        // Find configuration, using fallback where applicable.
        String enabledProtocols = JiveGlobals.getProperty( type.getPrefix() + "enabled.protocols" );
        while (enabledProtocols == null && type.getFallback() != null)
        {
            type = type.getFallback();
            enabledProtocols = JiveGlobals.getProperty( type.getPrefix() + "enabled.protocols" );
        }

        if (enabledProtocols != null )
        {
            final String[] protocols = enabledProtocols.split( "," );
            if (protocols != null && protocols.length > 0)
            {
                sslEngine.setEnabledProtocols( protocols );
            }
        }
        else
        {
            // When no user-based configuration is available, the SSL Engine will use a default. Instead of this default,
            // we want all of the TLS protocols that are supported (which will exclude all of the older, insecure SSL
            // protocols).
            final ArrayList<String> defaultEnabled = new ArrayList<>();
            for ( String supported : sslEngine.getSupportedProtocols() )
            {
                // Include only TLS protocols.
                if ( supported.toUpperCase().startsWith( "TLS" ) )
                {
                    defaultEnabled.add( supported );
                }
            }

            sslEngine.setEnabledProtocols( defaultEnabled.toArray( new String[ defaultEnabled.size()] ) );
        }
    }

    /**
     * Enables a specific set of cipher suites in an SSLEngine instance.
     *
     * To determine what suites to enable, this implementation first looks at a type-specific property. This property
     * can contain a comma-separated list of suites that are to be enabled.
     *
     * When the property is not set (or when the property value is empty), the suites that will be enabled are all
     * suites that are enabled by default in the SSLEngine, with the exclusion of a number of known weak suites.
     *
     * Note that the selection strategy is a different strategy than with protocols in configureProtocols(), where the
     * entire SSLEngine default gets replaced.
     *
     * @param sslEngine The instance to configure. Cannot be null.
     * @param type The type of configuration to use (used to select the relevent property). Cannot be null.
     */
    private static void configureCipherSuites( SSLEngine sslEngine, Type type )
    {
        String enabledCipherSuites = JiveGlobals.getProperty( type.getPrefix() + "enabled.ciphersuites" );
        while (enabledCipherSuites == null && type.getFallback() != null)
        {
            type = type.getFallback();
            enabledCipherSuites = JiveGlobals.getProperty( type.getPrefix() + "enabled.ciphersuites" );
        }

        if (enabledCipherSuites != null )
        {
            final String[] suites = enabledCipherSuites.split( "," );
            if (suites != null && suites.length > 0)
            {
                sslEngine.setEnabledCipherSuites( suites );
            }
        }
        else
        {
            // When no user-based configuration is available, the SSL Engine will use a default. From this default, we
            // want to filter out a couple of insecure ciphers.
            final ArrayList<String> defaultEnabled = new ArrayList<>();
            for ( String supported : sslEngine.getSupportedCipherSuites() )
            {
                // A number of weaknesses in SHA-1 are known. It is no longer recommended to be used.
                if ( supported.toUpperCase().endsWith( "SHA" ) )
                {
                    continue;
                }

                // Due to problems with collision resistance MD5 is no longer safe to use.
                if ( supported.toUpperCase().endsWith( "MD5" ) )
                {
                    continue;
                }

                defaultEnabled.add( supported );
            }

            sslEngine.setEnabledCipherSuites( defaultEnabled.toArray( new String[ defaultEnabled.size()] ) );
        }
    }

    /**
     * Creates an Apache MINA SslFilter that is configured to use server mode when handshaking.
     *
     * For Openfire, an engine is of this mode used for most purposes (as Openfire is a server by nature).
     *
     * Instead of an SSLContext or SSLEngine, Apache MINA uses an SslFilter instance. It is generally not needed to
     * create both SSLContext/SSLEngine as well as SslFilter instances.
     *
     * @param type Communication type (used to select the relevant property). Cannot be null.
     * @param clientAuth indication of the desired level of client-sided authentication (mutual authentication). Cannot be null.
     * @return An initialized SslFilter instance (never null)
     */
    public static SslFilter getServerModeSslFilter( SSLConfig.Type type, Connection.ClientAuth clientAuth ) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException
    {
        final SSLContext sslContext = SSLConfig.getSSLContext( type );
        final SSLEngine sslEngine = SSLConfig.getServerModeSSLEngine( type, clientAuth );

        return getSslFilter( sslContext, sslEngine );
    }

    /**
     * Creates an Apache MINA SslFilter that is configured to use client mode when handshaking.
     *
     * For Openfire, a filter of this mode is typically used when the server tries to connect to another server.
     *
     * Instead of an SSLContext or SSLEngine, Apache MINA uses an SslFilter instance. It is generally not needed to
     * create both SSLContext/SSLEngine as well as SslFilter instances.
     *
     * @param type Communication type (used to select the relevant property). Cannot be null.
     * @return An initialized SslFilter instance (never null)
     */
    public static SslFilter getClientModeSslFilter( SSLConfig.Type type ) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException
    {
        final SSLContext sslContext = SSLConfig.getSSLContext( type );
        final SSLEngine sslEngine = SSLConfig.getClientModeSSLEngine( type );

        return getSslFilter( sslContext, sslEngine );
    }

    /**
     * A utility method that implements the shared functionality of getServerModeSslFilter and getClientModeSslFilter.
     *
     * This method is used to initialize and configure an instance of SslFilter for a particular pre-configured
     * SSLContext and SSLEngine. In most cases, developers will want to use getServerModeSslFilter or
     * getClientModeSslFilter instead of this method.
     *
     * @param sslContext a pre-configured SSL Context instance (cannot be null).
     * @param sslEngine a pre-configured SSL Engine instance (cannot be null).
     * @return A SslFilter instance (never null).
     */
    private static SslFilter getSslFilter( SSLContext sslContext, SSLEngine sslEngine ) {
        final SslFilter filter = new SslFilter( sslContext );

        // Copy configuration from the SSL Engine into the filter.
        filter.setUseClientMode( sslEngine.getUseClientMode() );
        filter.setEnabledProtocols( sslEngine.getEnabledProtocols() );
        filter.setEnabledCipherSuites( sslEngine.getEnabledCipherSuites() );

        // Note that the setters for 'need' and 'want' influence each-other. Invoke only one of them!
        if ( sslEngine.getNeedClientAuth() ) {
            filter.setNeedClientAuth( true );
        } else if ( sslEngine.getWantClientAuth() ) {
            filter.setWantClientAuth( true );
        }
        return filter;
    }
}
