/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.net;

import org.jivesoftware.util.Log;
import com.sun.net.ssl.KeyManagerFactory;
import com.sun.net.ssl.SSLContext;
import com.sun.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyStore;
import javax.net.ssl.SSLServerSocketFactory;

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
