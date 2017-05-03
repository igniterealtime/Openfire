/*
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CRLException;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.X509CRL;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.X509TrustManager;

import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClientTrustManager is a Trust Manager that is only used for c2s connections. This TrustManager
 * is used both when a client connects to this server. It is possible to indicate if self-signed
 * certificates are going to be accepted. In case of accepting a self-signed certificate a warning
 * is logged. Future version of the server might include a small workflow so admins can review 
 * self-signed certificates or certificates of unknown issuers and manually accept them.
 *
 * @author Gaston Dombiak
 * @author Jay Kline
 */
public class ClientTrustManager implements X509TrustManager {

	private static final Logger Log = LoggerFactory.getLogger(ClientTrustManager.class);

    /**
     * KeyStore that holds the trusted CA
     */
    private KeyStore trustStore;

    /**
     * Holds the CRL's to validate certs
     */
    private CertStore crlStore = null;
    
    /**
     * Holds the actual CRL's
     */
    private Collection<X509CRL> crls = null;

    /**
     * Last time the CRL file was updated 
     */
    private long crlLastUpdated = 0;
    
    /**
     * Should CRL checking be done
     */
    private boolean useCRLs = false;
    
    
    public ClientTrustManager(KeyStore trustTrust) {
        super();
        this.trustStore = trustTrust;

        //Note: A reference of the Collection is used in the CertStore, so we can add CRL's 
        // after creating the CertStore.
        crls = new ArrayList<>();
        CollectionCertStoreParameters params = new CollectionCertStoreParameters(crls);
        
        try {
            crlStore = CertStore.getInstance("Collection", params);
        }
        catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException ex) {
            Log.warn("ClientTrustManager: ",ex);
        }

        loadCRL();
       
    }
    
    private void loadCRL() {
        File crlFile = new File(JiveGlobals.getProperty("xmpp.client.certificate.crl",
                "resources" + File.separator + "security" + File.separator + "crl.pem"));
        
        
        if (!crlFile.isFile()) {
            Log.debug("ClientTrustmanager: crl file not found "+crlFile.toString());
            useCRLs = false;
            return;
        }
        
        
        long modified = crlFile.lastModified();
        if (modified > crlLastUpdated) {
            crlLastUpdated = modified;
            Log.debug("ClientTrustManager: Updating CRLs");
            useCRLs = false;
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");

                X509CRL crl;

                FileInputStream crlStream = new FileInputStream(crlFile);
                BufferedInputStream crlBuffer = new BufferedInputStream(crlStream);
                
                crls.clear(); //remove existing CRLs
                while (crlBuffer.available() > 0) {
                    crl = (X509CRL)cf.generateCRL(crlBuffer);
                    Log.debug("ClientTrustManager: adding CRL for "+crl.getIssuerDN());
                    crls.add(crl);
                }
                useCRLs = true;
            }
            catch(FileNotFoundException e) {
                // Its ok if the file wasnt found- maybe we dont have any CRL's
                Log.debug("ClientTrustManager: CRL file not found: "+crlFile.toString());
            }
            catch(IOException e) {
                //Thrown bot the input streams
                Log.error("ClientTrustManager: IOException while parsing CRLs", e);
            }
            catch(CertificateException e) {
                //Thrown by CertificateFactory.getInstance(...)
                Log.error("ClientTrustManager: ",e);
            }
            catch(CRLException e) {
                Log.error("ClientTrustManager: CRLException while parsing CRLs", e);
            }
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String string)
            throws CertificateException {
        Log.debug("ClientTrustManager: checkClientTrusted(x509Certificates,"+string+") called");

        loadCRL();
        ArrayList<X509Certificate> certs = new ArrayList<>();
        for(int i = 0; i < x509Certificates.length ; i++) {
            certs.add(x509Certificates[i]);
        }


        boolean verify = JiveGlobals.getBooleanProperty("xmpp.client.certificate.verify", true);
        if (verify) {
            int nSize = x509Certificates.length;

            List<String> peerIdentities = CertificateManager.getClientIdentities(x509Certificates[0]);

            if (JiveGlobals.getBooleanProperty("xmpp.client.certificate.verify.chain", true)) {
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

            if (JiveGlobals.getBooleanProperty("xmpp.client.certificate.verify.root", true)) {
                // Verify that the the last certificate in the chain was issued
                // by a third-party that the client trusts, or is trusted itself
                boolean trusted = false;
                try {
                    Enumeration<String> aliases = trustStore.aliases();
                    while(aliases.hasMoreElements()) {
                        String alias = aliases.nextElement();
                        X509Certificate tCert = (X509Certificate) trustStore.getCertificate(alias);
                        if(x509Certificates[nSize - 1].equals(tCert)) {
                            try {
                                PublicKey publickey = tCert.getPublicKey();
                                x509Certificates[nSize -1].verify(publickey);
                            }
                            catch (GeneralSecurityException generalsecurityexception) {
                                throw new CertificateException(
                                        "signature verification failed of " + peerIdentities);
                            }
                            trusted = true;
                            break;
                        } else {
                            if(x509Certificates[nSize - 1].getIssuerDN().equals(tCert.getSubjectDN())) {
                                try {
                                    PublicKey publickey = tCert.getPublicKey();
                                    x509Certificates[nSize -1].verify(publickey);
                                }
                                catch (GeneralSecurityException generalsecurityexception) {
                                    throw new CertificateException(
                                            "signature verification failed of " + peerIdentities);
                                }
                                trusted = true;
                                break;
                            }
                        }
                    }
                }
                catch (KeyStoreException e) {
                    Log.error(e.getMessage(), e);
                }
                if (!trusted) {
                    //Log.debug("certificate not trusted of "+peerIdentities);
                    throw new CertificateException("root certificate not trusted of " + peerIdentities);
                }
            }

            if (JiveGlobals.getBooleanProperty("xmpp.client.certificate.verify.validity", true)) {
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


            //Verify certificate path
            try {
                CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
                CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
                X509CertSelector certSelector = new X509CertSelector();
                certSelector.setCertificate(x509Certificates[0]);
                PKIXBuilderParameters params = new PKIXBuilderParameters(trustStore,certSelector);
                if(useCRLs) {
                    params.addCertStore(crlStore);
                } else {
                    Log.debug("ClientTrustManager: no CRL's found, so setRevocationEnabled(false)");
                    params.setRevocationEnabled(false);
                }

                CertPathBuilderResult cpbr = cpb.build(params);
                CertPath cp = cpbr.getCertPath();
                if(JiveGlobals.getBooleanProperty("ocsp.enable",false)) {
                    Log.debug("ClientTrustManager: OCSP requested");
                    OCSPChecker ocspChecker = new OCSPChecker(cp,params);
                    params.addCertPathChecker(ocspChecker);
                }
                PKIXCertPathValidatorResult cpvResult = (PKIXCertPathValidatorResult) cpv.validate(cp, params);
                X509Certificate trustedCert = cpvResult.getTrustAnchor().getTrustedCert();
                if(trustedCert == null) {
                    throw new CertificateException("certificate path failed: Trusted CA is NULL");
                } else {
                    Log.debug("ClientTrustManager: Trusted CA: "+trustedCert.getSubjectDN());
                }
            }
            catch(CertPathBuilderException | CertPathValidatorException e) {
                Log.debug("ClientTrustManager:",e);
                throw new CertificateException("certificate path failed: "+e.getMessage());
            } catch(Exception e) {
                Log.debug("ClientTrustManager:",e);
                throw new CertificateException("unexpected error: "+e.getMessage());
            }
            
        }
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
    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String string)
            throws CertificateException {

        Log.debug("ClientTrustManager: checkServerTrusted(...) called");

    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        if (JiveGlobals.getBooleanProperty("xmpp.client.certificate.accept-selfsigned", false)) {
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
