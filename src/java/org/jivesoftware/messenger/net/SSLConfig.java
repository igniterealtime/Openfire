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
import java.security.Security;

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
    private static String keystore;
    private static String truststore;

    private SSLConfig() {
    }

    static {
        String algorithm = JiveGlobals.getProperty("xmpp.socket.ssl.algorithm");
        if ("".equals(algorithm) || algorithm == null) {
            algorithm = "TLS";
        }
        String storeType = JiveGlobals.getProperty("xmpp.socket.ssl.storeType");
        if ("".equals(storeType)) {
            storeType = null;
        }
        keystore = JiveGlobals.getProperty("xmpp.socket.ssl.keystore");
        if ("".equals(keystore) || keystore == null) {
            keystore = null;
        }
        else {
            keystore = JiveGlobals.getMessengerHome() + File.separator + keystore;
        }
        keypass = JiveGlobals.getProperty("xmpp.socket.ssl.keypass");
        if (keypass == null) {
            keypass = "";
        }
        else {
            keypass = keypass.trim();
        }
        truststore = JiveGlobals.getProperty("xmpp.socket.ssl.truststore");
        if ("".equals(truststore) || truststore == null) {
            truststore = null;
        }
        else {
            truststore = JiveGlobals.getMessengerHome() + File.separator + truststore;
        }
        trustpass = JiveGlobals.getProperty("xmpp.socket.ssl.trustpass");
        if (trustpass == null) {
            trustpass = "";
        }
        else {
            trustpass = trustpass.trim();
        }


        try {
            keyStore = KeyStore.getInstance(storeType);
            if (keystore == null) {
                keyStore.load(null, keypass.toCharArray());
            }
            else {
                keyStore.load(new FileInputStream(keystore), keypass.toCharArray());
            }

            trustStore = KeyStore.getInstance(storeType);
            if (truststore == null) {
                trustStore.load(null, trustpass.toCharArray());
            }
            else {
                trustStore.load(new FileInputStream(truststore), trustpass.toCharArray());
            }

            // Install the jsse provider for jdk 1.3.x and the external jsse
            // Not needed on jdk1.4.x but this implementation must support both platforms
            Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
            sslFactory = (SSLJiveServerSocketFactory)
                    SSLJiveServerSocketFactory.getInstance(algorithm,
                            keyStore,
                            trustStore);
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
            if (keystore != null) {
                keyStore.store(new FileOutputStream(keystore), keypass.toCharArray());
            }

            if (truststore != null) {
                trustStore.store(new FileOutputStream(truststore), trustpass.toCharArray());
            }
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
