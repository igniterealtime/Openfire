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
import org.eclipse.jetty.util.ssl.SslContextFactory;
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

    private final ConcurrentMap<Purpose, String> identityStoreLocationByPurpose = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IdentityStoreConfig> identityStoresByLocation = new ConcurrentHashMap<>();
    private final ConcurrentMap<Purpose, String> trustStoreLocationByPurpose = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TrustStoreConfig> trustStoresByLocation = new ConcurrentHashMap<>();

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
     * An utility method that is short-hand for getInstance().getIdentityStoreConfig(purpose).getStore();
     * @param purpose The purpose for which to return a store.
     * @return a store (never null).
     */
    public static synchronized KeyStore getIdentityStore( Purpose purpose )
    {
        return getInstance().getIdentityStoreConfig( purpose ).getStore();
    }

    /**
     * An utility method that is short-hand for getInstance().getTrustStoreConfig(purpose).getStore();
     * @param purpose The purpose for which to return a store.
     * @return a store (never null).
     */
    public static synchronized KeyStore getTrustStore( Purpose purpose )
    {
        return getInstance().getTrustStoreConfig( purpose ).getStore();
    }

//    /**
//     * Openfire allows a store to be re-used for multiple purposes. This method will find the store used for the
//     * provided purpose, and based on that will return all <em>other</em> purposes for which the same store is used.
//     *
//     * @param purpose The purpose for which to find a store (cannot be null).
//     * @return all <em>other</em> purposes for which the store is used (never null, but possibly an empty collection).
//     * @throws IOException
//     */
//    public Set<Purpose> getOtherPurposesForSameStore( Type type, boolean isTrustStore ) throws IOException
//    {
//        if ( type == null )
//        {
//            throw new IllegalArgumentException( "Argument 'type' cannot be null." );
//        }
//
//        final Set<Purpose> results = new HashSet<>();
//
//        for ( Map.Entry<Purpose, String> entry : locationByPurpose.entrySet() )
//        {
//            if ( entry.getValue().equalsIgnoreCase( location ) )
//            {
//                results.add( entry.getKey() );
//            }
//        }
//
//        return results;
//    }

//    public static String getNonCanonicalizedLocation(Purpose purpose)
//    {
//        final String path;
//        switch ( purpose )
//        {
//            // Identity store for socket-based IO (this is the default identity store)
//            case SOCKETBASED_IDENTITYSTORE:
//                path = JiveGlobals.getProperty( "xmpp.socket.ssl.keystore", "resources" + File.separator + "security" + File.separator + "keystore" );
//                break;
//
//            // Identity store for BOSH-based IO (falls back to the default identity store when not configured)
//            case BOSHBASED_IDENTITYSTORE:
//                path = JiveGlobals.getProperty( "xmpp.bosh.ssl.keystore", getNonCanonicalizedLocation( Purpose.SOCKETBASED_IDENTITYSTORE ) );
//                break;
//
//            // Identity store for administrative IO (falls back to the default identity store when not configured)
//            case ADMINISTRATIVE_IDENTITYSTORE:
//                path = JiveGlobals.getProperty( "admin.ssl.keystore", getNonCanonicalizedLocation( Purpose.SOCKETBASED_IDENTITYSTORE ) );
//                break;
//
//            // Identity store for admin panel (falls back to the administrative identity store when not configured)
//            case WEBADMIN_IDENTITYSTORE:
//                path = JiveGlobals.getProperty( "admin.web.ssl.keystore", getNonCanonicalizedLocation( Purpose.ADMINISTRATIVE_IDENTITYSTORE ) );
//                break;
//
//            // server-to-server trust store (This is the only / default S2S trust store. S2S over BOSH is unsupported by Openfire).
//            case SOCKETBASED_S2S_TRUSTSTORE:
//                path = JiveGlobals.getProperty( "xmpp.socket.ssl.truststore", "resources" + File.separator + "security" + File.separator + "truststore" );
//                break;
//
//            // client-to-server trust store for socket-based IO (This is the default C2S trust store).
//            case SOCKETBASED_C2S_TRUSTSTORE:
//                path = JiveGlobals.getProperty( "xmpp.socket.ssl.client.truststore", "resources" + File.separator + "security" + File.separator + "client.truststore" );
//                break;
//
//            // client-to-server trust store for BOSH-based IO (falls back to the default C2S trust store when not configured).
//            case BOSHBASED_C2S_TRUSTSTORE:
//                path = JiveGlobals.getProperty( "xmpp.bosh.ssl.client.truststore", getNonCanonicalizedLocation( Purpose.SOCKETBASED_C2S_TRUSTSTORE ) );
//                break;
//
//            // Administrative trust store (falls back to the default trust store when not configured)
//            case ADMINISTRATIVE_TRUSTSTORE:
//                path = JiveGlobals.getProperty( "admin.ssl.truststore", getNonCanonicalizedLocation( Purpose.SOCKETBASED_S2S_TRUSTSTORE ) );
//                break;
//
//            // Trust store for admin panel (falls back to the administrative trust store when not configured)
//            case WEBADMIN_TRUSTSTORE:
//                path = JiveGlobals.getProperty( "admin.web.ssl.truststore", getNonCanonicalizedLocation( Purpose.ADMINISTRATIVE_TRUSTSTORE ) );
//                break;
//
//            default:
//                throw new IllegalStateException( "Unrecognized purpose: " + purpose );
//        }
//        return path;
//    }

//    public static String getLocation(Purpose purpose, boolean isTrustStore) throws IOException
//    {
//        final String location;
//        if ( isTrustStore ) {
//            location = purpose.getTrustStoreLocationNonCanonicalized();
//        } else {
//            location = purpose.getIdentityStoreLocationNonCanonicalized();
//        }
//        return canonicalize( location );
//    }

//    protected String getPassword( Type type, boolean isTrustStore )
//    {
//        switch ( purpose ) {
//            case SOCKETBASED_IDENTITYSTORE:
//                return JiveGlobals.getProperty( "xmpp.socket.ssl.keypass", "changeit" ).trim();
//
//            case BOSHBASED_IDENTITYSTORE:
//                return JiveGlobals.getProperty( "xmpp.bosh.ssl.keypass", getPassword( Purpose.SOCKETBASED_IDENTITYSTORE ) ).trim();
//
//            case ADMINISTRATIVE_IDENTITYSTORE:
//                return JiveGlobals.getProperty( "admin.ssl.keypass",  getPassword( Purpose.SOCKETBASED_IDENTITYSTORE ) ).trim();
//
//            case WEBADMIN_IDENTITYSTORE:
//                return JiveGlobals.getProperty( "admin.web.ssl.keypass", getPassword( Purpose.ADMINISTRATIVE_IDENTITYSTORE ) ).trim();
//
//            case SOCKETBASED_S2S_TRUSTSTORE:
//                return JiveGlobals.getProperty( "xmpp.socket.ssl.trustpass", "changeit" ).trim();
//
//            case SOCKETBASED_C2S_TRUSTSTORE:
//                return JiveGlobals.getProperty( "xmpp.socket.ssl.client.trustpass", "changeit" ).trim();
//
//            case BOSHBASED_C2S_TRUSTSTORE:
//                return JiveGlobals.getProperty( "xmpp.bosh.ssl.client.trustpass", getPassword( Purpose.SOCKETBASED_C2S_TRUSTSTORE ) ).trim();
//
//            case ADMINISTRATIVE_TRUSTSTORE:
//                return JiveGlobals.getProperty( "admin.ssl.trustpass", getPassword( Purpose.SOCKETBASED_S2S_TRUSTSTORE ) ).trim();
//
//            case WEBADMIN_TRUSTSTORE:
//                return JiveGlobals.getProperty( "admin..web.ssl.trustpass", getPassword( Purpose.ADMINISTRATIVE_TRUSTSTORE ) ).trim();
//
//            default:
//                throw new IllegalStateException( "Unrecognized purpose: " + purpose );
//        }
//    }

//    protected String getStoreType( Type type, boolean isTrustStore )
//    {
//        // FIXME All properties should be unique, instead of being re-used by different stores.
//        switch ( purpose )
//        {
//            case SOCKETBASED_IDENTITYSTORE:
//                return JiveGlobals.getProperty( "xmpp.socket.ssl.storeType", "jks" ).trim();
//
//            case BOSHBASED_IDENTITYSTORE:
//                return JiveGlobals.getProperty( "xmpp.bosh.ssl.storeType", getStoreType( Purpose.SOCKETBASED_IDENTITYSTORE ) ).trim();
//
//            case ADMINISTRATIVE_IDENTITYSTORE:
//                return JiveGlobals.getProperty( "admin.ssl.storeType", getStoreType( Purpose.SOCKETBASED_IDENTITYSTORE ) ).trim();
//
//            case WEBADMIN_IDENTITYSTORE:
//                return JiveGlobals.getProperty( "admin.web.ssl.storeType", getStoreType( Purpose.ADMINISTRATIVE_IDENTITYSTORE ) ).trim();
//
//            case SOCKETBASED_S2S_TRUSTSTORE:
//                return JiveGlobals.getProperty( "xmpp.socket.ssl.storeType", "jks" ).trim();
//
//            case SOCKETBASED_C2S_TRUSTSTORE:
//                return JiveGlobals.getProperty( "xmpp.socket.ssl.client.storeType", "jks" ).trim();
//
//            case BOSHBASED_C2S_TRUSTSTORE:
//                return JiveGlobals.getProperty( "xmpp.bosh.ssl.client.storeType", getStoreType( Purpose.SOCKETBASED_C2S_TRUSTSTORE ) ).trim();
//
//            case ADMINISTRATIVE_TRUSTSTORE:
//                return JiveGlobals.getProperty( "admin.ssl.storeType", getStoreType( Purpose.SOCKETBASED_S2S_TRUSTSTORE ) ).trim();
//
//            case WEBADMIN_TRUSTSTORE:
//                return JiveGlobals.getProperty( "admin.web.ssl.storeType", getStoreType( Purpose.ADMINISTRATIVE_TRUSTSTORE ) ).trim();
//
//            default:
//                throw new IllegalStateException( "Unrecognized purpose: " + purpose );
//        }
//    }

    private SSLConfig() throws CertificateStoreConfigException, IOException, NoSuchAlgorithmException
    {
        for ( final Purpose purpose : Purpose.values() )
        {
            // Instantiate an identity store.
            final String locationIdent = purpose.getIdentityStoreLocation();
            identityStoreLocationByPurpose.put( purpose, locationIdent );
            if ( !identityStoresByLocation.containsKey( locationIdent ) )
            {
                final IdentityStoreConfig storeConfig = new IdentityStoreConfig( purpose.getIdentityStoreLocation(), purpose.getIdentityStorePassword(), purpose.getIdentityStoreType(), false );
                identityStoresByLocation.put( locationIdent, storeConfig );
            }

            // Instantiate trust store.
            final String locationTrust = purpose.getTrustStoreLocation();
            trustStoreLocationByPurpose.put( purpose, locationTrust );
            if ( !trustStoresByLocation.containsKey( locationTrust ) )
            {
                final TrustStoreConfig storeConfig = new TrustStoreConfig( purpose.getTrustStoreLocation(), purpose.getTrustStorePassword(), purpose.getTrustStoreType(), false, purpose.acceptSelfSigned(), purpose.verifyValidity() );
                trustStoresByLocation.put( locationTrust, storeConfig );
            }
        }
    }

    public IdentityStoreConfig getIdentityStoreConfig( Purpose purpose )
    {
        if ( purpose == null ) {
            throw new IllegalArgumentException( "Argument 'purpose' cannot be null.");
        }
        final IdentityStoreConfig config = identityStoresByLocation.get( identityStoreLocationByPurpose.get( purpose ) );
        if (config == null) {
            throw new IllegalStateException( "Cannot retrieve identity store for " + purpose );
        }
        return config;
    }

    public TrustStoreConfig getTrustStoreConfig( Purpose purpose )
    {
        if ( purpose == null ) {
            throw new IllegalArgumentException( "Argument 'purpose' cannot be null.");
        }
        final TrustStoreConfig config = trustStoresByLocation.get( trustStoreLocationByPurpose.get( purpose ) );
        if (config == null) {
            throw new IllegalStateException( "Cannot retrieve trust store for " + purpose );
        }
        return config;
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

    public static SSLContext getSSLContext( final Purpose purpose ) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException
    {
        final KeyManager[] keyManagers = getInstance().getIdentityStoreConfig( purpose ).getKeyManagers();
        final TrustManager[] trustManagers = getInstance().getTrustStoreConfig( purpose ).getTrustManagers();

        final SSLContext sslContext = SSLContext.getInstance( "TLSv1" );
        sslContext.init( keyManagers, trustManagers, new SecureRandom() );
        return sslContext;
    }

    /**
     * Creates an SSL Engine that is configured to use server mode when handshaking.
     *
     * For Openfire, an engine is of this mode used for most purposes (as Openfire is a server by nature).
     *
     * @param purpose The type of connectivity for which to configure a new SSLEngine instance. Cannot be null.
     * @param clientAuth indication of the desired level of client-sided authentication (mutual authentication). Cannot be null.
     * @return An initialized SSLEngine instance (never null).
     */
    public static SSLEngine getServerModeSSLEngine( Purpose purpose, Connection.ClientAuth clientAuth ) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException
    {
        final SSLEngine sslEngine = getSSLEngine( purpose );
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
     * @param purpose The type of connectivity for which to configure a new SSLEngine instance. Cannot be null.
     * @return An initialized SSLEngine instance (never null).
     */
    public static SSLEngine getClientModeSSLEngine( Purpose purpose ) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException
    {
        final SSLEngine sslEngine = getSSLEngine( purpose );
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
     * @param purpose The type of connectivity for which to pre-configure a new SSLEngine instance. Cannot be null.
     * @return A pre-configured SSLEngine (never null).
     */
    private static SSLEngine getSSLEngine( final Purpose purpose ) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException
    {
        final SSLContext sslContext = getSSLContext( purpose );

        final SSLEngine sslEngine = sslContext.createSSLEngine();

        // Configure protocol support.
        if ( purpose.getProtocolsEnabled() != null && !purpose.getProtocolsEnabled().isEmpty() )
        {
            // When an explicit list of enabled protocols is defined, use only those.
            sslEngine.setEnabledProtocols( purpose.getProtocolsEnabled().split( "," ) );
        }
        else if ( purpose.getProtocolsDisabled() != null && !purpose.getProtocolsDisabled().isEmpty() )
        {
            // Otherwise, use all supported protocols (except for the ones that are explicitly disabled).
            final List<String> disabled = Arrays.asList( purpose.getProtocolsDisabled() );
            final ArrayList<String> supported = new ArrayList<>(  );
            for ( final String candidate : sslEngine.getSupportedProtocols() ) {
                if ( !disabled.contains( candidate ) ) {
                    supported.add( candidate );
                }
            }

            sslEngine.setEnabledProtocols( supported.toArray( new String[ supported.size()] ) );
        }

        // Configure cipher suite support.
        if ( purpose.getCipherSuitesEnabled() != null && !purpose.getCipherSuitesEnabled().isEmpty() )
        {
            // When an explicit list of enabled protocols is defined, use only those.
            sslEngine.setEnabledCipherSuites( purpose.getCipherSuitesEnabled().split( "," ) );
        }
        else if ( purpose.getCipherSuitesDisabled() != null && !purpose.getCipherSuitesDisabled().isEmpty() )
        {
            // Otherwise, use all supported cipher suites (except for the ones that are explicitly disabled).
            final List<String> disabled = Arrays.asList( purpose.getCipherSuitesDisabled() );
            final ArrayList<String> supported = new ArrayList<>(  );
            for ( final String candidate : sslEngine.getSupportedCipherSuites() ) {
                if ( !disabled.contains( candidate ) ) {
                    supported.add( candidate );
                }
            }

            sslEngine.setEnabledCipherSuites( supported.toArray( new String[ supported.size() ] ) );
        }

        // TODO: Set policy for checking client certificates

        return sslEngine;
    }

    public static SslContextFactory getSslContextFactory( final Purpose purpose )
    {
        final SslContextFactory sslContextFactory = new SslContextFactory();

        final TrustStoreConfig trustStoreConfig = SSLConfig.getInstance().getTrustStoreConfig( purpose );
        sslContextFactory.setTrustStore( trustStoreConfig.getStore() );
        sslContextFactory.setTrustStorePassword( trustStoreConfig.getPassword() );

        final IdentityStoreConfig identityStoreConfig = SSLConfig.getInstance().getIdentityStoreConfig( purpose );
        sslContextFactory.setKeyStore( identityStoreConfig.getStore() );
        sslContextFactory.setKeyStorePassword( identityStoreConfig.getPassword() );

        // Configure protocol and cipher suite support.
        if ( purpose.getProtocolsEnabled() != null ) {
            sslContextFactory.setIncludeProtocols( purpose.getProtocolsEnabled().split( "," ) );
        }
        if ( purpose.getProtocolsDisabled() != null ) {
            sslContextFactory.setExcludeProtocols( purpose.getProtocolsDisabled().split( "," ) );
        }
        if ( purpose.getCipherSuitesEnabled() != null) {
            sslContextFactory.setIncludeCipherSuites( purpose.getCipherSuitesEnabled().split( "," ) );
        }
        if ( purpose.getCipherSuitesDisabled() != null ) {
            sslContextFactory.setExcludeCipherSuites( purpose.getCipherSuitesDisabled().split( "," ) );
        }

// TODO: Set policy for checking client certificates
//        String certPol = JiveGlobals.getProperty(HTTP_BIND_AUTH_PER_CLIENTCERT_POLICY, "disabled");
//        if(certPol.equals("needed")) {
//            sslContextFactory.setNeedClientAuth(true);
//            sslContextFactory.setWantClientAuth(true);
//        } else if(certPol.equals("wanted")) {
//            sslContextFactory.setNeedClientAuth(false);
//            sslContextFactory.setWantClientAuth(true);
//        } else {
//            sslContextFactory.setNeedClientAuth(false);
//            sslContextFactory.setWantClientAuth(false);
//        }

        return sslContextFactory;
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
     * @param purpose The type of configuration to use (used to select the relevent property). Cannot be null.

    private static void configureProtocols( SSLEngine sslEngine, Purpose purpose )
    {
        // Find configuration, using fallback where applicable.
        String enabledProtocols = JiveGlobals.getProperty( purpose.getPrefix() + "enabled.protocols" );
        while (enabledProtocols == null && purpose.getFallback() != null)
        {
            purpose = purpose.getFallback();
            enabledProtocols = JiveGlobals.getProperty( purpose.getPrefix() + "enabled.protocols" );
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
                      */

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
     * @param purpose The type of configuration to use (used to select the relevent property). Cannot be null.

    private static void configureCipherSuites( SSLEngine sslEngine, Purpose purpose )
    {
        String enabledCipherSuites = JiveGlobals.getProperty( purpose.getPrefix() + "enabled.ciphersuites" );
        while (enabledCipherSuites == null && purpose.getFallback() != null)
        {
            purpose = purpose.getFallback();
            enabledCipherSuites = JiveGlobals.getProperty( purpose.getPrefix() + "enabled.ciphersuites" );
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
                      */

    /**
     * Creates an Apache MINA SslFilter that is configured to use server mode when handshaking.
     *
     * For Openfire, an engine is of this mode used for most purposes (as Openfire is a server by nature).
     *
     * Instead of an SSLContext or SSLEngine, Apache MINA uses an SslFilter instance. It is generally not needed to
     * create both SSLContext/SSLEngine as well as SslFilter instances.
     *
     * @param purpose Communication type (used to select the relevant property). Cannot be null.
     * @param clientAuth indication of the desired level of client-sided authentication (mutual authentication). Cannot be null.
     * @return An initialized SslFilter instance (never null)
     */
    public static SslFilter getServerModeSslFilter( Purpose purpose, Connection.ClientAuth clientAuth ) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException
    {
        final SSLContext sslContext = SSLConfig.getSSLContext( purpose );
        final SSLEngine sslEngine = SSLConfig.getServerModeSSLEngine( purpose, clientAuth );

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
     * @param purpose Communication type (used to select the relevant property). Cannot be null.
     * @return An initialized SslFilter instance (never null)
     */
    public static SslFilter getClientModeSslFilter( Purpose purpose ) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException
    {
        final SSLContext sslContext = SSLConfig.getSSLContext( purpose );
        final SSLEngine sslEngine = SSLConfig.getClientModeSSLEngine( purpose );

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
