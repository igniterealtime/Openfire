/**
 * $RCSfile$
 * $Revision: 1217 $
 * $Date: 2005-04-11 18:11:06 -0300 (Mon, 11 Apr 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.CertificateEventListener;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import javax.net.ssl.*;
import javax.net.ServerSocketFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Configuration of Openfire's SSL settings.
 *
 * @author Iain Shigeoka
 */
public class SSLConfig {

    private static SSLServerSocketFactory s2sFactory;
    private static SSLServerSocketFactory c2sFactory;

    private static String storeType;
    private static SSLContext s2sContext;
    private static SSLContext c2sContext;

    private static KeyStore keyStore;
    private static String keyStoreLocation;
    private static String keypass;

    private static KeyStore s2sTrustStore;
    private static String s2sTrustStoreLocation;
    private static String s2sTrustpass;

    private static KeyStore c2sTrustStore;
    private static String c2sTrustStoreLocation;
    private static String c2sTrustpass;


    private SSLConfig() {
    }

    static {
        String algorithm = JiveGlobals.getProperty("xmpp.socket.ssl.algorithm", "TLS");
        storeType = JiveGlobals.getProperty("xmpp.socket.ssl.storeType", "jks");

        // Get the keystore location. The default location is security/keystore
        keyStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.keystore",
                "resources" + File.separator + "security" + File.separator + "keystore");
        keyStoreLocation = JiveGlobals.getHomeDirectory() + File.separator + keyStoreLocation;

        // Get the keystore password. The default password is "changeit".
        keypass = JiveGlobals.getProperty("xmpp.socket.ssl.keypass", "changeit");
        keypass = keypass.trim();

        // Get the truststore location for c2s connections
        c2sTrustStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.client.truststore",
                "resources" + File.separator + "security" + File.separator + "client.truststore");
        c2sTrustStoreLocation = JiveGlobals.getHomeDirectory() + File.separator + c2sTrustStoreLocation;

        c2sTrustpass = JiveGlobals.getProperty("xmpp.socket.ssl.client.trustpass", "changeit");
        c2sTrustpass = c2sTrustpass.trim();

        // Get the truststore location for s2s connections
        s2sTrustStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.truststore",
                "resources" + File.separator + "security" + File.separator + "truststore");
        s2sTrustStoreLocation = JiveGlobals.getHomeDirectory() + File.separator + s2sTrustStoreLocation;

        // Get the truststore passwprd; default is "changeit".
        s2sTrustpass = JiveGlobals.getProperty("xmpp.socket.ssl.trustpass", "changeit");
        s2sTrustpass = s2sTrustpass.trim();

	    // Load s2s keystore and trusstore
        try {
            keyStore = KeyStore.getInstance(storeType);
            keyStore.load(new FileInputStream(keyStoreLocation), keypass.toCharArray());

            s2sTrustStore = KeyStore.getInstance(storeType);
            s2sTrustStore.load(new FileInputStream(s2sTrustStoreLocation), s2sTrustpass.toCharArray());


        }
        catch (Exception e) {
            Log.error("SSLConfig startup problem.\n" +
                    "  storeType: [" + storeType + "]\n" +
                    "  keyStoreLocation: [" + keyStoreLocation + "]\n" +
                    "  keypass: [" + keypass + "]\n" +
                    "  s2sTrustStoreLocation: [" + s2sTrustStoreLocation + "]\n" +
                    "  s2sTrustpass: [" + s2sTrustpass + "]\n"); 
            keyStore = null;
            s2sTrustStore = null;
            s2sFactory = null;
        }
        // Load c2s trusstore
        try {
            if (s2sTrustStoreLocation.equals(c2sTrustStoreLocation)) {
                c2sTrustStore = s2sTrustStore;
                c2sTrustpass = s2sTrustpass;
            }
            else {
                c2sTrustStore = KeyStore.getInstance(storeType);
                c2sTrustStore.load(new FileInputStream(c2sTrustStoreLocation), c2sTrustpass.toCharArray());
            }
        }
        catch (Exception e) {
            try {
                c2sTrustStore = KeyStore.getInstance(storeType);
                c2sTrustStore.load(null, c2sTrustpass.toCharArray());
            }
            catch (Exception ex) {
                Log.error("SSLConfig startup problem.\n" +
                        "  storeType: [" + storeType + "]\n" +
                        "  c2sTrustStoreLocation: [" + c2sTrustStoreLocation + "]\n" +
                        "  c2sTrustPass: [" + c2sTrustpass + "]", e);
                c2sTrustStore = null;
                c2sFactory = null;
            }
        }
        resetFactory();

        // Reset ssl factoty when certificates are modified
        CertificateManager.addListener(new CertificateEventListener() {
            // Reset ssl factory since keystores have changed
            public void certificateCreated(KeyStore keyStore, String alias, X509Certificate cert) {
                resetFactory();
            }

            public void certificateDeleted(KeyStore keyStore, String alias) {
                resetFactory();
            }

            public void certificateSigned(KeyStore keyStore, String alias, List<X509Certificate> certificates) {
                resetFactory();
            }
        });
    }

    private static void resetFactory() {
        try {
            String algorithm = JiveGlobals.getProperty("xmpp.socket.ssl.algorithm", "TLS");

            s2sContext = SSLContext.getInstance(algorithm);
            c2sContext = SSLContext.getInstance(algorithm);

            KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyFactory.init(keyStore, SSLConfig.getKeyPassword().toCharArray());

            TrustManagerFactory s2sTrustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            s2sTrustFactory.init(s2sTrustStore);

            s2sContext.init(keyFactory.getKeyManagers(),
                    s2sTrustFactory.getTrustManagers(),
                    new java.security.SecureRandom());

            s2sFactory = s2sContext.getServerSocketFactory();

            if (s2sTrustStore == c2sTrustStore) {
                c2sContext = s2sContext;
                c2sFactory = s2sFactory;
            }
            else {
                TrustManagerFactory c2sTrustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                c2sTrustFactory.init(c2sTrustStore);

                c2sContext.init(keyFactory.getKeyManagers(),
                    s2sTrustFactory.getTrustManagers(),
                    new java.security.SecureRandom());

                c2sFactory = c2sContext.getServerSocketFactory();
            }

        }
        catch (Exception e) {
            Log.error("SSLConfig factory setup problem.\n" +
                    "  storeType: [" + storeType + "]\n" +
                    "  keyStoreLocation: [" + keyStoreLocation + "]\n" +
                    "  keypass: [" + keypass + "]\n" +
                    "  s2sTrustStoreLocation: [" + s2sTrustStoreLocation+ "]\n" +
                    "  s2sTrustpass: [" + s2sTrustpass + "]" +
                    "  c2sTrustStoreLocation: [" + c2sTrustStoreLocation + "]\n" +
                    "  c2sTrustpass: [" + c2sTrustpass + "]", e);
            keyStore = null;
            s2sTrustStore = null;
            c2sTrustStore = null;
            s2sFactory = null;
            c2sFactory = null;
        }
    }

    /**
     * Get the Key Store password
     *
     * @return the key store password
     */
    public static String getKeyPassword() {
        return keypass;
    }

    /**
     * Return the Trust Store password for s2s connections.
     *
     * @return the s2s trust store password.
     */
    public static String gets2sTrustPassword() {
        return s2sTrustpass;
    }


    /**
     * Return the Trust Store password for c2s connections.
     *
     * @return the c2s trust store password.
     */
    public static String getc2sTrustPassword() {
        return c2sTrustpass;
    }

    public static String[] getDefaultCipherSuites() {
        String[] suites;
        if (s2sFactory == null) {
            suites = new String[]{};
        }
        else {
            suites = s2sFactory.getDefaultCipherSuites();
        }
        return suites;
    }

    public static String[] getSupportedCipherSuites() {
        String[] suites;
        if (s2sFactory == null) {
            suites = new String[]{};
        }
        else {
            suites = s2sFactory.getSupportedCipherSuites();
        }
        return suites;
    }

    /**
     * Get the Key Store
     *
     * @return the Key Store
     */
    public static KeyStore getKeyStore() throws IOException {
        if (keyStore == null) {
            throw new IOException();
        }
        return keyStore;
    }

    /**
     * Get the Trust Store for s2s connections
     *
     * @return the s2s Trust Store
     */
    public static KeyStore gets2sTrustStore() throws IOException {
        if (s2sTrustStore == null) {
            throw new IOException();
        }
        return s2sTrustStore;
    }

    /** 
     * Get the Trust Store for c2s connections
     *
     * @return the c2s Trust Store
     */ 
    public static KeyStore getc2sTrustStore() throws IOException {
        if (c2sTrustStore == null) {
            throw new IOException();
        }
        return c2sTrustStore;
    }

    /**
     * Initializes (wipes and recreates) the keystore, and returns the new keystore.
     *
     * @return Newly initialized keystore.
     */
    public static KeyStore initializeKeyStore() {
        try {
            keyStore = KeyStore.getInstance(storeType);
            keyStore.load(null, keypass.toCharArray());
        }
        catch (Exception e) {
            Log.error("Unable to initialize keystore: ", e);
        }
        return keyStore;
    }

    /**
     * Save all key and trust stores.
     */
    public static void saveStores() throws IOException {
        try {
            keyStore.store(new FileOutputStream(keyStoreLocation), keypass.toCharArray());
            s2sTrustStore.store(new FileOutputStream(s2sTrustStoreLocation), s2sTrustpass.toCharArray());
            if (c2sTrustStore != s2sTrustStore) {
                c2sTrustStore.store(new FileOutputStream(c2sTrustStoreLocation), c2sTrustpass.toCharArray());
            }
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Create a ServerSocket for s2s connections
     *
     * @return the ServerSocket for an s2s connection
     */
    public static ServerSocket createServerSocket(int port, InetAddress ifAddress) throws
            IOException {
        if (s2sFactory == null) {
            throw new IOException();
        }
        else {
            return s2sFactory.createServerSocket(port, -1, ifAddress);
        }
    }

    /**
     * Create a ServerSocket for c2s connections
     *
     * @return the ServerSocket for an c2s connection
     */
    public static ServerSocket createc2sServerSocket(int port, InetAddress ifAddress) throws
            IOException {
        if (c2sFactory == null) {
            throw new IOException();
        }
        else {
            return c2sFactory.createServerSocket(port, -1, ifAddress);
        }
    }

    /**
     * Get the Key Store location
     *
     * @return the keystore location
     */
    public static String getKeystoreLocation() {
        return keyStoreLocation;
    }

    /**
     * Get the s2s Trust Store location
     *
     * @return the s2s Trust Store location
     */
    public static String gets2sTruststoreLocation() {
        return s2sTrustStoreLocation;
    }

    /**
     * Get the c2s Trust Store location
     *
     * @return the c2s Trust Store location
     */
    public static String getc2sTruststoreLocation() {
        return c2sTrustStoreLocation;
    }

    public static String getStoreType() {
        return storeType;
    }

    /**
     * Get the SSLContext for s2s connections
     *
     * @return the SSLContext for s2s connections
     */
    public static SSLContext getSSLContext() {
        return s2sContext;
    }

    /**
     * Get the SSLContext for c2s connections
     *
     * @return the SSLContext for c2s connections
     */
    public static SSLContext getc2sSSLContext() {
        return c2sContext;
    }

    /**
     * Get the SSLServerSocketFactory for s2s connections
     *
     * @return the SSLServerSocketFactory for s2s connections
     */
    public static SSLServerSocketFactory getServerSocketFactory() {
        return s2sFactory;
    }
    /**
     * Get the SSLServerSocketFactory for c2s connections
     *
     * @return the SSLServerSocketFactory for c2s connections
     */
    public static SSLServerSocketFactory getc2sServerSocketFactory() {
        return c2sFactory;
    }
}
