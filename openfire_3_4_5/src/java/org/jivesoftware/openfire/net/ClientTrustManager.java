/**
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import javax.net.ssl.X509TrustManager;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.cert.*;
import java.util.*;

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

    /**
     * KeyStore that holds the trusted CA
     */
    private KeyStore trustStore;
    /**
     * Holds the domain of the remote server we are trying to connect
     */
    private String server;

    /**
     * Holds the CRL's to validate certs
     */
    private CertStore crlStore = null;

    public ClientTrustManager(KeyStore trustTrust) {
        super();
        this.trustStore = trustTrust;


        //Note: A reference of the Collection is used in the CertStore, so we can add CRL's 
        // after creating the CertStore.
        Collection<X509CRL> crls = new ArrayList<X509CRL>();
        CollectionCertStoreParameters params = new CollectionCertStoreParameters(crls);
        X509CRL crl;
        CertificateFactory cf;

        try {
            crlStore = CertStore.getInstance("Collection",params);

            FileInputStream crlFile = new FileInputStream(JiveGlobals.getProperty("xmpp.client.certificate.crl","/tmp/crl.pem"));
            BufferedInputStream crlBuffer = new BufferedInputStream(crlFile);
            cf = CertificateFactory.getInstance("X.509");
            while (crlBuffer.available() > 0) {
                crl = (X509CRL)cf.generateCRL(crlBuffer);
                Log.debug("ClientTrustManager: adding CRL for "+crl.getIssuerDN());
                crls.add(crl);
            }
        }
        catch(FileNotFoundException e) {
            // Its ok if the file wasnt found- maybe we dont have any CRL's
            Log.debug("ClientTrustManager: CRL file not found: "+JiveGlobals.getProperty("xmpp.client.certificate.crl","/tmp/crl.pem"));
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
        catch(InvalidAlgorithmParameterException e) {
            Log.error("ClientTrustManager: ",e);
        }
        catch(NoSuchAlgorithmException e) {
            Log.error("ClientTrustManager: ",e);
        }



    }

    public void checkClientTrusted(X509Certificate[] x509Certificates, String string)
            throws CertificateException {
        Log.debug("ClientTrustManager: checkClientTrusted(x509Certificates,"+string+") called");

        ArrayList<X509Certificate> certs = new ArrayList<X509Certificate>();
        for(int i = 0; i < x509Certificates.length ; i++) {
            certs.add(x509Certificates[i]);
        }


        boolean verify = JiveGlobals.getBooleanProperty("xmpp.client.certificate.verify", true);
        if (verify) {
            int nSize = x509Certificates.length;

            List<String> peerIdentities = CertificateManager.getPeerIdentities(x509Certificates[0]);

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
                    Log.error(e);
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
                certSelector.setSubject(x509Certificates[0].getIssuerX500Principal());
                PKIXBuilderParameters params = new PKIXBuilderParameters(trustStore,new X509CertSelector());
                if(crlStore != null)
                    params.addCertStore(crlStore);

                CertPathBuilderResult cpbr = cpb.build(params);
                CertPath cp = cpbr.getCertPath();
                PKIXCertPathValidatorResult cpvResult = (PKIXCertPathValidatorResult) cpv.validate(cp, params);
                X509Certificate trustedCert = (X509Certificate) cpvResult.getTrustAnchor().getTrustedCert();
                if(trustedCert == null) {
                    throw new CertificateException("certificate path failed: Trusted CA is NULL");
                } else {
                    Log.debug("ClientTrustManager: Trusted CA: "+trustedCert.getSubjectDN());
                }
            }
            catch(CertPathBuilderException e) {
                throw new CertificateException("certificate path failed: "+e.getMessage());
            }
            catch(CertPathValidatorException e) {
                throw new CertificateException("certificate path failed: "+e.getMessage());
            }
            catch(KeyStoreException e) {
                Log.debug("ClientTrustManager: ",e);
            }
            catch(InvalidAlgorithmParameterException e) {
                Log.debug("ClientTrustManager: ",e);
            }
            catch(NoSuchAlgorithmException e) {
                Log.debug("ClientTrustManager: ",e);
            }
            
            //If we did not get any CRL's, we have nothing more to do.
            if(crlStore == null)
                return;

            try {
                X509CRLSelector crlSelector = new X509CRLSelector();
                crlSelector.addIssuerName(x509Certificates[0].getIssuerDN().getName());
                crlSelector.setDateAndTime(new Date()); //right now
                Collection<X509CRL> selectedCrls = (Collection<X509CRL>) crlStore.getCRLs(crlSelector);
                for(X509CRL crl : selectedCrls) {
                    if(crl.isRevoked(x509Certificates[0])) {
                        throw new CertificateException("certificate is revoked: "+peerIdentities);
                    }
                }
            }
            catch(CertStoreException e) {
                Log.error("ClientTrustManager: ",e);
            }
            catch(IOException e) {
                Log.error("ClientTrustManager: ",e);
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
    public void checkServerTrusted(X509Certificate[] x509Certificates, String string)
            throws CertificateException {

        Log.debug("ClientTrustManager: checkServerTrusted(...) called");

    }

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
                Log.error(e);
                X509Certs = null;
            }
            return X509Certs;
        }
    }
}
