/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.clearspace;

import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;

import javax.net.ssl.X509TrustManager;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.Log;

/**
 * Trust manager that validates Clearspace certificates. Using system properties
 * it is possible to disable or enabled certain validations. By default all validations
 * are enabled and self-signed certificated are not accepted.
 *
 * @author Gaston Dombiak
 */
public class ClearspaceX509TrustManager implements X509TrustManager {

    /**
     * KeyStore that holds the trusted CA
     */
    private KeyStore trustStore;
    private String server;
    private Map<String, String> properties;

    public ClearspaceX509TrustManager(String server, Map<String, String> properties, KeyStore keystore) throws NoSuchAlgorithmException, KeyStoreException {
        super();
        this.server = server;
        this.trustStore = keystore;
        this.properties = properties;
    }


    /**
     * @see javax.net.ssl.X509TrustManager#checkClientTrusted(X509Certificate[],String authType)
     */
    public void checkClientTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
        // Do nothing. We are the client so we are not testing certificates from clients
    }

    /**
     * @see javax.net.ssl.X509TrustManager#checkServerTrusted(X509Certificate[],String authType)
     */
    public void checkServerTrusted(X509Certificate[] x509Certificates, String authType) throws CertificateException {
        // Flag that indicates if certificates of the remote server should be validated. Disabling
        // certificate validation is not recommended for production environments.
        boolean verify = getBooleanProperty("clearspace.certificate.verify", true);
        if (verify) {
            int nSize = x509Certificates.length;

            List<String> peerIdentities = CertificateManager.getPeerIdentities(x509Certificates[0]);

            if (getBooleanProperty("clearspace.certificate.verify.chain", true)) {
                // Working down the chain, for every certificate in the chain,
                // verify that the subject of the certificate is the issuer of the
                // next certificate in the chain.
                Principal principalLast = null;
                for (int i = nSize -1; i >= 0 ; i--) {
                    X509Certificate x509certificate = x509Certificates[i];
                    Principal principalIssuer = x509certificate.getIssuerDN();
                    Principal principalSubject = x509certificate.getSubjectDN();
                    if (principalLast != null) {
                        if (principalIssuer.equals(principalLast)) {
                            try {
                                PublicKey publickey =
                                        x509Certificates[i + 1].getPublicKey();
                                x509Certificates[i].verify(publickey);
                            }
                            catch (GeneralSecurityException generalsecurityexception) {
                                throw new CertificateException(
                                        "signature verification failed of " + peerIdentities);
                            }
                        }
                        else {
                            throw new CertificateException(
                                    "subject/issuer verification failed of " + peerIdentities);
                        }
                    }
                    principalLast = principalSubject;
                }
            }

            if (getBooleanProperty("clearspace.certificate.verify.root", true)) {
                // Verify that the the last certificate in the chain was issued
                // by a third-party that the client trusts.
                boolean trusted = false;
                try {
                    trusted = trustStore.getCertificateAlias(x509Certificates[nSize - 1]) != null;
                    if (!trusted && nSize == 1 && JiveGlobals
                            .getBooleanProperty("clearspace.certificate.accept-selfsigned", false))
                    {
                        Log.warn("Accepting self-signed certificate of remote server: " +
                                peerIdentities);
                        trusted = true;
                    }
                }
                catch (KeyStoreException e) {
                    Log.error(e);
                }
                if (!trusted) {
                    throw new CertificateException("root certificate not trusted of " + peerIdentities);
                }
            }

            if (getBooleanProperty("clearspace.certificate.verify.identity", false)) {
                // Verify that the first certificate in the chain corresponds to
                // the server we desire to authenticate.
                // Check if the certificate uses a wildcard indicating that subdomains are valid
                if (peerIdentities.size() == 1 && peerIdentities.get(0).startsWith("*.")) {
                    // Remove the wildcard
                    String peerIdentity = peerIdentities.get(0).replace("*.", "");
                    // Check if the requested subdomain matches the certified domain
                    if (!server.endsWith(peerIdentity)) {
                        throw new CertificateException("target verification failed of " + peerIdentities);
                    }
                }
                else if (!peerIdentities.contains(server)) {
                    throw new CertificateException("target verification failed of " + peerIdentities);
                }
            }

            if (getBooleanProperty("clearspace.certificate.verify.validity", true)) {
                // For every certificate in the chain, verify that the certificate
                // is valid at the current time.
                Date date = new Date();
                for (int i = 0; i < nSize; i++) {
                    try {
                        x509Certificates[i].checkValidity(date);
                    }
                    catch (GeneralSecurityException generalsecurityexception) {
                        throw new CertificateException("invalid date of " + peerIdentities);
                    }
                }
            }
        }
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.get(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    /**
     * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
     */
    public X509Certificate[] getAcceptedIssuers() {
        if (getBooleanProperty("clearspace.certificate.accept-selfsigned", false)) {
            // Answer an empty list since we accept any issuer
            return new X509Certificate[0];
        }
        else {
            X509Certificate[] X509Certs = null;
            try {
                // See how many certificates are in the keystore.
                int numberOfEntry = trustStore.size();
                // If there are any certificates in the keystore.
                if (numberOfEntry > 0) {
                    // Create an array of X509Certificates
                    X509Certs = new X509Certificate[numberOfEntry];

                    // Get all of the certificate alias out of the keystore.
                    Enumeration aliases = trustStore.aliases();

                    // Retrieve all of the certificates out of the keystore
                    // via the alias name.
                    int i = 0;
                    while (aliases.hasMoreElements()) {
                        X509Certs[i] =
                                (X509Certificate) trustStore.
                                        getCertificate((String) aliases.nextElement());
                        i++;
                    }

                }
            }
            catch (Exception e) {
                Log.error(e);
                X509Certs = null;
            }
            return X509Certs;
        }
    }
}
