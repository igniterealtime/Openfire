/**
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import javax.net.ssl.X509TrustManager;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;

/**
 * ServerTrustManager is a Trust Manager that is only used for s2s connections. This TrustManager
 * is used both when the server connects to another server as well as when receiving a connection
 * from another server. In both cases, it is possible to indicate if self-signed certificates
 * are going to be accepted. In case of accepting a self-signed certificate a warning is logged.
 * Future version of the server might include a small workflow so admins can review self-signed
 * certificates or certificates of unknown issuers and manually accept them.
 *
 * @author Gaston Dombiak
 */
class ServerTrustManager implements X509TrustManager {

    /**
     * KeyStore that holds the trusted CA
     */
    private KeyStore trustStore;
    /**
     * Holds the domain of the remote server we are trying to connect
     */
    private String server;

    public ServerTrustManager(String server, KeyStore trustTrust) {
        super();
        this.server = server;
        this.trustStore = trustTrust;
    }

    public void checkClientTrusted(X509Certificate[] x509Certificates,
            String string) {
        // Do not validate the certificate at this point. The certificate is going to be used
        // when the remote server requests to do EXTERNAL SASL
    }

    public void checkServerTrusted(X509Certificate[] x509Certificates, String string)
            throws CertificateException {

        // Flag that indicates if certificates of the remote server should be validated. Disabling
        // certificate validation is not recommended for production environments.
        boolean verify = JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify", true);
        if (verify) {
            int nSize = x509Certificates.length;

            String peerIdentity = getPeerIdentity(x509Certificates[nSize - 1]);

            // Working down the chain, for every certificate in the chain,
            // verify that the subject of the certificate is the issuer of the
            // next certificate in the chain.
            Principal principalLast = null;
            for (int i = 0; i < nSize; i++) {
                X509Certificate x509certificate = x509Certificates[i];
                Principal principalIssuer = x509certificate.getIssuerDN();
                Principal principalSubject = x509certificate.getSubjectDN();
                if (principalLast != null) {
                    if (principalIssuer.equals(principalLast)) {
                        try {
                            PublicKey publickey =
                                    x509Certificates[i - 1].getPublicKey();
                            x509Certificates[i].verify(publickey);
                        }
                        catch (GeneralSecurityException generalsecurityexception) {
                            throw new CertificateException(
                                    "signature verification failed of " + peerIdentity);
                        }
                    }
                    else {
                        throw new CertificateException(
                                "subject/issuer verification failed of " + peerIdentity);
                    }
                }
                principalLast = principalSubject;
            }

            // Verify that the the first certificate in the chain was issued
            // by a third-party that the client trusts.
            boolean trusted = false;
            try {
                trusted = trustStore.getCertificateAlias(x509Certificates[0]) != null;
                if (!trusted && nSize == 1 && JiveGlobals
                        .getBooleanProperty("xmpp.server.certificate.accept-selfsigned", false)) {
                    Log.warn("Accepting self-signed certificate of remote server: " + peerIdentity);
                    trusted = true;
                }
            }
            catch (KeyStoreException e) {
                Log.error(e);
            }
            if (!trusted) {
                throw new CertificateException("root certificate not trusted of " + peerIdentity);
            }

            // Verify that the last certificate in the chain corresponds to
            // the server we desire to authenticate.
            if (!server.equals(peerIdentity)) {
                throw new CertificateException("target verification failed of " + peerIdentity);
            }

            // For every certificate in the chain, verify that the certificate
            // is valid at the current time.
            Date date = new Date();
            for (int i = 0; i < nSize; i++) {
                try {
                    x509Certificates[i].checkValidity(date);
                }
                catch (GeneralSecurityException generalsecurityexception) {
                    throw new CertificateException("invalid date of " + peerIdentity);
                }
            }
        }
    }

    /**
     * Returns the identity of the remote server as defined in the specified certificate. The
     * identity is defined in the subjectDN of the certificate and it can also be defined in
     * the subjectAltName extension of type "xmpp". When the extension is being used then the
     * identity defined in the extension in going to be returned. Otherwise, the value stored in
     * the subjectDN is returned.
     *
     * @param x509Certificate the certificate the holds the identity of the remote server.
     * @return the identity of the remote server as defined in the specified certificate.
     */
    static String getPeerIdentity(X509Certificate x509Certificate) {
        Principal principalSubject = x509Certificate.getSubjectDN();
        // TODO Look the identity in the subjectAltName extension if available
        String name = principalSubject.getName();
        name = name.replace("CN=", "");
        return name;
    }

    private boolean isChainTrusted(X509Certificate[] chain) {
        boolean trusted = false;
        try {
            // Start with the root and see if it is in the Keystore.
            // The root is at the end of the chain.
            for (int i = chain.length - 1; i >= 0; i--) {
                if (trustStore.getCertificateAlias(chain[i]) != null) {
                    trusted = true;
                    break;
                }
            }
        }
        catch (Exception e) {
            Log.error(e);
            trusted = false;
        }
        return trusted;
    }

    public X509Certificate[] getAcceptedIssuers() {
        if (JiveGlobals.getBooleanProperty("xmpp.server.certificate.accept-selfsigned", false)) {
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
