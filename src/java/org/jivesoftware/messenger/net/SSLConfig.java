/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.net;

import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.JiveGlobals;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyStore;

/**
 * Configuration of Messenger's SSL settings.
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

    private SSLConfig() {
    }

    static {
        String algorithm = JiveGlobals.getProperty("xmpp.socket.ssl.algorithm", "TLS");
        String storeType = JiveGlobals.getProperty("xmpp.socket.ssl.storeType", "jks");
        // Get the keystore location. The default location is security/keystore
        keyStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.keystore",
                JiveGlobals.getMessengerHome() + File.separator + "security" +
                File.separator + "keystore");
        // Get the keystore password. The default password is "changeit".
        keypass = JiveGlobals.getProperty("xmpp.socket.ssl.keypass", "changeit");
        keypass = keypass.trim();
        // Get the truststore location; default at security/truststore
        trustStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.truststore",
                JiveGlobals.getMessengerHome() + File.separator + "security" +
                File.separator + "truststore");
        // Get the truststore passwprd; default is "changeit".
        trustpass = JiveGlobals.getProperty("xmpp.socket.ssl.trustpass", "changeit");
        trustpass = trustpass.trim();

        try {
            keyStore = KeyStore.getInstance(storeType);
            keyStore.load(new FileInputStream(keyStoreLocation), keypass.toCharArray());

            trustStore = KeyStore.getInstance(storeType);
            trustStore.load(new FileInputStream(trustStoreLocation), trustpass.toCharArray());

            sslFactory = (SSLJiveServerSocketFactory)
                    SSLJiveServerSocketFactory.getInstance(algorithm,
                    keyStore, trustStore);
        }
        catch (Exception e) {
            Log.error(e);
            keyStore = null;
            trustStore = null;
            sslFactory = null;
        }
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
}