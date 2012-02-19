/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSLSocketFactory that accepts any certificate chain and also accepts expired
 * certificates.
 *
 * @author Matt Tucker
 */
public class SimpleSSLSocketFactory extends SSLSocketFactory {

	private static final Logger Log = LoggerFactory.getLogger(SimpleSSLSocketFactory.class);

    private SSLSocketFactory factory;

    public SimpleSSLSocketFactory() {

        try {
            String algorithm = JiveGlobals.getProperty("xmpp.socket.ssl.algorithm", "TLS");
            SSLContext sslcontent = SSLContext.getInstance(algorithm);
            sslcontent.init(null, // KeyManager not required
                            new TrustManager[] { new DummyTrustManager() },
                            new java.security.SecureRandom());
            factory = sslcontent.getSocketFactory();
        }
        catch (NoSuchAlgorithmException e) {
            Log.error(e.getMessage(), e);
        }
        catch (KeyManagementException e) {
            Log.error(e.getMessage(), e);
        }
    }

    public static SocketFactory getDefault() {
        return new SimpleSSLSocketFactory();
    }

    @Override
	public Socket createSocket() throws IOException {
        return factory.createSocket();
    }

    @Override
	public Socket createSocket(Socket socket, String s, int i, boolean flag)
            throws IOException
    {
        return factory.createSocket(socket, s, i, flag);
    }

    @Override
	public Socket createSocket(InetAddress inaddr, int i, InetAddress inaddr2, int j)
            throws IOException
    {
        return factory.createSocket(inaddr, i, inaddr2, j);
    }

    @Override
	public Socket createSocket(InetAddress inaddr, int i)
            throws IOException
    {
        return factory.createSocket(inaddr, i);
    }

    @Override
	public Socket createSocket(String s, int i, InetAddress inaddr, int j)
            throws IOException
    {
        return factory.createSocket(s, i, inaddr, j);
    }

    @Override
	public Socket createSocket(String s, int i)
            throws IOException
    {
        return factory.createSocket(s, i);
    }

    @Override
	public String[] getDefaultCipherSuites() {
        return factory.getSupportedCipherSuites();
    }

    @Override
	public String[] getSupportedCipherSuites() {
        return factory.getSupportedCipherSuites();
    }

    private static class DummyTrustManager implements X509TrustManager {

        public boolean isClientTrusted(X509Certificate[] cert) {
            return true;
        }

        public boolean isServerTrusted(X509Certificate[] cert) {
            try {
                cert[0].checkValidity();
                return true;
            }
            catch (CertificateExpiredException e) {
                return false;
            }
            catch (CertificateNotYetValidException e) {
                return false;
            }
        }

        public void checkClientTrusted(java.security.cert.X509Certificate[] x509Certificates,
                String s) throws CertificateException
        {
        }

        public void checkServerTrusted(java.security.cert.X509Certificate[] x509Certificates,
                String s) throws CertificateException
        {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}