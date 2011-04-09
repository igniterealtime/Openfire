/**
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

package org.jivesoftware.openfire.net;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.X509TrustManager;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class ServerTrustManager implements X509TrustManager {

	private static final Logger Log = LoggerFactory.getLogger(ServerTrustManager.class);

    /**
     * KeyStore that holds the trusted CA
     */
    private KeyStore trustStore;
    /**
     * Holds the domain of the remote server we are trying to connect
     */
    private String server;
    /**
     * Holds the LocalIncomingServerSession that is part of the TLS negotiation.
     */
    private Connection connection;

    public ServerTrustManager(String server, KeyStore trustTrust, Connection connection) {
        super();
        this.server = server;
        this.trustStore = trustTrust;
        this.connection = connection;
    }

    public void checkClientTrusted(X509Certificate[] x509Certificates,
            String string) {
        // Do not validate the certificate at this point. The certificate is going to be used
        // when the remote server requests to do EXTERNAL SASL
    }

    /**
     * Given the partial or complete certificate chain provided by the peer, build a certificate
     * path to a trusted root and return if it can be validated and is trusted for server SSL
     * authentication based on the authentication type. The authentication type is the key
     * exchange algorithm portion of the cipher suites represented as a String, such as "RSA",
     * "DHE_DSS". Note: for some exportable cipher suites, the key exchange algorithm is
     * determined at run time during the handshake. For instance, for
     * TLS_RSA_EXPORT_WITH_RC4_40_MD5, the authType should be RSA_EXPORT when an ephemeral
     * RSA key is used for the key exchange, and RSA when the key from the server certificate
     * is used. Checking is case-sensitive.<p>
     *
     * By default certificates are going to be verified. This includes verifying the certificate
     * chain, the root certificate and the certificates validity. However, it is possible to
     * disable certificates validation as a whole or each specific validation.
     *
     * @param x509Certificates an ordered array of peer X.509 certificates with the peer's own
     *        certificate listed first and followed by any certificate authorities.
     * @param string the key exchange algorithm used.
     * @throws CertificateException if the certificate chain is not trusted by this TrustManager.
     */
    public void checkServerTrusted(X509Certificate[] x509Certificates, String string)
            throws CertificateException {

        // Flag that indicates if certificates of the remote server should be validated. Disabling
        // certificate validation is not recommended for production environments.
        boolean verify = JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify", true);
        if (verify) {
            int nSize = x509Certificates.length;

            List<String> peerIdentities = CertificateManager.getPeerIdentities(x509Certificates[0]);

            if (JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify.chain", true)) {
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

            if (JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify.root", true)) {
                // Verify that the the last certificate in the chain was issued
                // by a third-party that the client trusts.
                boolean trusted = false;
                try {
                    trusted = trustStore.getCertificateAlias(x509Certificates[nSize - 1]) != null;
                    // Keep track if the other peer presented a self-signed certificate
                    connection.setUsingSelfSignedCertificate(!trusted && nSize == 1);
                    if (!trusted && nSize == 1 && JiveGlobals
                            .getBooleanProperty("xmpp.server.certificate.accept-selfsigned", false))
                    {
                        Log.warn("Accepting self-signed certificate of remote server: " +
                                peerIdentities);
                        trusted = true;
                    }
                }
                catch (KeyStoreException e) {
                    Log.error(e.getMessage(), e);
                }
                if (!trusted) {
                    throw new CertificateException("Root certificate (subject: "+x509Certificates[nSize - 1].getSubjectX500Principal()
                    		+ ") of " + peerIdentities + " not trusted.");
                }
            }

            // Verify that the server either matches an identity from the chain, or
            // a wildcard.
            Boolean found = false;
            for (String identity : peerIdentities) {
                // Verify that either the identity is the same as the hostname, or for wildcarded
                // identities that the hostname ends with .domainspecified or -is- domainspecified.
                if ((identity.startsWith("*.")
                        && (server.endsWith(identity.replace("*.", "."))
                        || server.equals(identity.replace("*.", ""))))
                        || server.equals(identity)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new CertificateException("target verification failed of " + peerIdentities);
            }
            
            if (JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify.validity", true)) {
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
                Log.error(e.getMessage(), e);
                X509Certs = null;
            }
            return X509Certs;
        }
    }
}
