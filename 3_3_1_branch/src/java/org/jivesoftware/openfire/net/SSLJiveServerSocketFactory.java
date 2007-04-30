/**
 * $RCSfile$
 * $Revision: 1217 $
 * $Date: 2005-04-11 18:11:06 -0300 (Mon, 11 Apr 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.Log;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyStore;

/**
 * Securue socket factory wrapper allowing simple setup of all security
 * SSL related parameters.
 *
 * @author Iain Shigeoka
 */
public class SSLJiveServerSocketFactory extends SSLServerSocketFactory {

    public static SSLServerSocketFactory getInstance(String algorithm,
                                                     KeyStore keystore,
                                                     KeyStore truststore) throws
            IOException {

        try {
            SSLContext sslcontext = SSLContext.getInstance(algorithm);
            SSLServerSocketFactory factory;
            KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyFactory.init(keystore, SSLConfig.getKeyPassword().toCharArray());
            TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustFactory.init(truststore);

            sslcontext.init(keyFactory.getKeyManagers(),
                    trustFactory.getTrustManagers(),
                    new java.security.SecureRandom());
            factory = sslcontext.getServerSocketFactory();
            return new SSLJiveServerSocketFactory(factory);
        }
        catch (Exception e) {
            Log.error(e);
            throw new IOException(e.getMessage());
        }
    }

    private SSLServerSocketFactory factory;

    private SSLJiveServerSocketFactory(SSLServerSocketFactory factory) {
        this.factory = factory;
    }

    public ServerSocket createServerSocket(int i) throws IOException {
        return factory.createServerSocket(i);
    }

    public ServerSocket createServerSocket(int i, int i1) throws IOException {
        return factory.createServerSocket(i, i1);
    }

    public ServerSocket createServerSocket(int i, int i1, InetAddress inetAddress) throws IOException {
        return factory.createServerSocket(i, i1, inetAddress);
    }

    public String[] getDefaultCipherSuites() {
        return factory.getDefaultCipherSuites();
    }

    public String[] getSupportedCipherSuites() {
        return factory.getSupportedCipherSuites();
    }
}
