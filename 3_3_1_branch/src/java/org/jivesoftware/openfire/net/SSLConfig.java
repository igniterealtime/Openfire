/**
 * $RCSfile$
 * $Revision: 1217 $
 * $Date: 2005-04-11 18:11:06 -0300 (Mon, 11 Apr 2005) $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.CertificateEventListener;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Configuration of Openfire's SSL settings.
 *
 * @author Iain Shigeoka
 */
public class SSLConfig {

    private static SSLJiveServerSocketFactory sslFactory;
    private static KeyStore keyStore;
    private static String keypass;
    private static KeyStore trustStore;
    private static String trustpass;
    private static String keyStoreLocation;
    private static String trustStoreLocation;
    private static String storeType;

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

        // Get the truststore location; default at security/truststore
        trustStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.truststore",
                "resources" + File.separator + "security" + File.separator + "truststore");
        trustStoreLocation = JiveGlobals.getHomeDirectory() + File.separator + trustStoreLocation;

        // Get the truststore passwprd; default is "changeit".
        trustpass = JiveGlobals.getProperty("xmpp.socket.ssl.trustpass", "changeit");
        trustpass = trustpass.trim();

        try {
            keyStore = KeyStore.getInstance(storeType);
            keyStore.load(new FileInputStream(keyStoreLocation), keypass.toCharArray());

            trustStore = KeyStore.getInstance(storeType);
            trustStore.load(new FileInputStream(trustStoreLocation), trustpass.toCharArray());

            sslFactory = (SSLJiveServerSocketFactory)SSLJiveServerSocketFactory.getInstance(
                    algorithm, keyStore, trustStore);
        }
        catch (Exception e) {
            Log.error("SSLConfig startup problem.\n" +
                    "  storeType: [" + storeType + "]\n" +
                    "  keyStoreLocation: [" + keyStoreLocation + "]\n" +
                    "  keypass: [" + keypass + "]\n" +
                    "  trustStoreLocation: [" + trustStoreLocation+ "]\n" +
                    "  trustpass: [" + trustpass + "]", e);
            keyStore = null;
            trustStore = null;
            sslFactory = null;
        }
        // Reset ssl factoty when certificates are modified
        CertificateManager.addListener(new CertificateEventListener() {
            public void certificateCreated(KeyStore keyStore, String alias, X509Certificate cert) {
                // Reset ssl factory since keystores have changed
                resetFactory(keyStore);
            }

            public void certificateDeleted(KeyStore keyStore, String alias) {
                // Reset ssl factory since keystores have changed
                resetFactory(keyStore);
            }

            public void certificateSigned(KeyStore keyStore, String alias,
                    List<X509Certificate> certificates) {
                // Reset ssl factory since keystores have changed
                resetFactory(keyStore);
            }

            private void resetFactory(KeyStore keyStore) {
                try {
                    String algorithm = JiveGlobals.getProperty("xmpp.socket.ssl.algorithm", "TLS");
                    sslFactory = (SSLJiveServerSocketFactory)SSLJiveServerSocketFactory.getInstance(
                            algorithm, keyStore, trustStore);
                }
                catch (IOException e) {
                    Log.error("Error while resetting ssl factory", e);
                    sslFactory = null;
                }
            }
        });
    }

    public static String getKeyPassword() {
        return keypass;
    }

    public static String getTrustPassword() {
        return trustpass;
    }

    public static String[] getDefaultCipherSuites() {
        String[] suites;
        if (sslFactory == null) {
            suites = new String[]{};
        }
        else {
            suites = sslFactory.getDefaultCipherSuites();
        }
        return suites;
    }

    public static String[] getSpportedCipherSuites() {
        String[] suites;
        if (sslFactory == null) {
            suites = new String[]{};
        }
        else {
            suites = sslFactory.getSupportedCipherSuites();
        }
        return suites;
    }

    public static KeyStore getKeyStore() throws IOException {
        if (keyStore == null) {
            throw new IOException();
        }
        return keyStore;
    }

    public static KeyStore getTrustStore() throws IOException {
        if (trustStore == null) {
            throw new IOException();
        }
        return trustStore;
    }

    public static void saveStores() throws IOException {
        try {
            keyStore.store(new FileOutputStream(keyStoreLocation), keypass.toCharArray());
            trustStore.store(new FileOutputStream(trustStoreLocation), trustpass.toCharArray());
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public static ServerSocket createServerSocket(int port, InetAddress ifAddress) throws
            IOException {
        if (sslFactory == null) {
            throw new IOException();
        }
        else {
            return sslFactory.createServerSocket(port, -1, ifAddress);
        }
    }

    public static String getKeystoreLocation() {
        return keyStoreLocation;
    }

    public static String getTruststoreLocation() {
        return trustStoreLocation;
    }

    public static String getStoreType() {
        return storeType;
    }

    public static SSLJiveServerSocketFactory getServerSocketFactory() {
        return sslFactory;
    }
}