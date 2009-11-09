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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchProviderException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.ocsp.BasicOCSPResp;
import org.bouncycastle.ocsp.CertificateID;
import org.bouncycastle.ocsp.CertificateStatus;
import org.bouncycastle.ocsp.OCSPReq;
import org.bouncycastle.ocsp.OCSPReqGenerator;
import org.bouncycastle.ocsp.OCSPResp;
import org.bouncycastle.ocsp.SingleResp;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <code>PKIXCertPathChecker</code> that uses 
 * Online Certificate Status Protocol (OCSP) 
 * 
 * See <a href="http://www.ietf.org/rfc/rfc2560.txt">RFC 2560</a>.
 *
 * @author Jay Kline
 */
public class OCSPChecker extends PKIXCertPathChecker {

	private static final Logger Log = LoggerFactory.getLogger(OCSPChecker.class);

    private static String ocspServerUrl = JiveGlobals.getProperty("ocsp.responderURL");
    private static String ocspServerSubject = JiveGlobals.getProperty("ocsp.responderCertSubjectName");
    private static final boolean dump = true;
    private int certIndex;
    private X509Certificate[] certs;
    private CertPath cp;
    private PKIXParameters pkixParams;

    OCSPChecker(CertPath certPath, PKIXParameters pkixParams)
            throws CertPathValidatorException {

        this.cp = certPath;
        this.pkixParams = pkixParams;
        List<? extends Certificate> tmp = cp.getCertificates();
        certs =
                (X509Certificate[]) tmp.toArray(new X509Certificate[tmp.size()]);
        init(false);
    }

    public void init(boolean forward) throws CertPathValidatorException {
        if (!forward) {
            certIndex = certs.length - 1;
        } else {
            throw new CertPathValidatorException(
                    "Forward checking not supported");
        }
    }

    public boolean isForwardCheckingSupported() {
        return false;
    }

    public Set<String> getSupportedExtensions() {
        return Collections.<String>emptySet();
    }

    public void check(Certificate cert, Collection<String> unresolvedCritExts)
            throws CertPathValidatorException {
        Log.debug("OCSPChecker: check called");
        InputStream in = null;
        OutputStream out = null;
        try {
            // Examine OCSP properties
            X509Certificate responderCert = null;
            boolean haveResponderCert = true; //defaults to issuers cert
            X500Principal responderSubjectName = null;
            boolean haveIssuerCert = false;

            // If we set the subject name, we need to find the certificate
            if (ocspServerSubject != null) {
                haveResponderCert = false;
                responderSubjectName = new X500Principal(ocspServerSubject);
            }


            X509Certificate issuerCert = null;
            X509Certificate currCert = (X509Certificate) cert;

            // Set the issuer certificate if we were passed a chain
            if (certIndex != 0) {
                issuerCert = (X509Certificate) (certs[certIndex]);
                haveIssuerCert = true;

                if (haveResponderCert) {
                    responderCert = certs[certIndex];
                }
            }


            if (!haveIssuerCert || !haveResponderCert) {

                if (!haveResponderCert) {
                    Log.debug("OCSPChecker: Looking for responder's certificate");
                }
                if (!haveIssuerCert) {
                    Log.debug("OCSPChecker: Looking for issuer's certificate");
                }

                // Extract the anchor certs
                Iterator anchors = pkixParams.getTrustAnchors().iterator();
                if (!anchors.hasNext()) {
                    throw new CertPathValidatorException(
                            "Must specify at least one trust anchor");
                }

                X500Principal certIssuerName =
                        currCert.getIssuerX500Principal();
                while (anchors.hasNext() &&
                        (!haveIssuerCert || !haveResponderCert)) {

                    TrustAnchor anchor = (TrustAnchor) anchors.next();
                    X509Certificate anchorCert = anchor.getTrustedCert();
                    X500Principal anchorSubjectName =
                            anchorCert.getSubjectX500Principal();

                    // Check if this anchor cert is the issuer cert
                    if (!haveIssuerCert && certIssuerName.equals(anchorSubjectName)) {

                        issuerCert = anchorCert;
                        haveIssuerCert = true;

                        //If we have not set the responderCert at this point, set it to the issuer
                        if (haveResponderCert && responderCert == null) {
                            responderCert = anchorCert;
                            Log.debug("OCSPChecker: Responder's certificate = issuer certificate");
                        }
                    }

                    // Check if this anchor cert is the responder cert
                    if (!haveResponderCert) {
                        if (responderSubjectName != null &&
                                responderSubjectName.equals(anchorSubjectName)) {

                            responderCert = anchorCert;
                            haveResponderCert = true;
                        }
                    }
                }
                
                if (issuerCert == null) {
                    //No trust anchor was found matching the issuer
                    throw new CertPathValidatorException("No trusted certificate for " + currCert.getIssuerDN());
                }

                // Check cert stores if responder cert has not yet been found
                if (!haveResponderCert) {
                    Log.debug("OCSPChecker: Searching cert stores for responder's certificate");
                    
                    if (responderSubjectName != null) {
                        X509CertSelector filter = new X509CertSelector();
                        filter.setSubject(responderSubjectName.getName());
                    
                        List<CertStore> certStores = pkixParams.getCertStores();
                        for (CertStore certStore : certStores) {
                            Iterator i = certStore.getCertificates(filter).iterator();
                            if (i.hasNext()) {
                                responderCert = (X509Certificate) i.next();
                                haveResponderCert = true;
                                break;
                            }
                        }
                    }
                }
            }

            // Could not find the responder cert
            if (!haveResponderCert) {
                throw new CertPathValidatorException("Cannot find the responder's certificate.");
            }

            // Construct an OCSP Request
            OCSPReqGenerator gen = new OCSPReqGenerator();

            CertificateID certID = new CertificateID(CertificateID.HASH_SHA1, issuerCert, currCert.getSerialNumber());
            gen.addRequest(certID);
            OCSPReq ocspRequest = gen.generate();


            URL url;
            if (ocspServerUrl != null) {
                try {
                    url = new URL(ocspServerUrl);
                } catch (MalformedURLException e) {
                    throw new CertPathValidatorException(e);
                }
            } else {
                throw new CertPathValidatorException("Must set OCSP Server URL");
            }
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            Log.debug("OCSPChecker: connecting to OCSP service at: " + url);

            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-type", "application/ocsp-request");
            con.setRequestProperty("Accept","application/ocsp-response");
            byte[] bytes = ocspRequest.getEncoded();


            con.setRequestProperty("Content-length", String.valueOf(bytes.length));
            out = con.getOutputStream();
            out.write(bytes);
            out.flush();

            // Check the response
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.debug("OCSPChecker: Received HTTP error: " + con.getResponseCode() +
                        " - " + con.getResponseMessage());
            }
            in = con.getInputStream();
            OCSPResp ocspResponse = new OCSPResp(in);
            BigInteger serialNumber = currCert.getSerialNumber();
            BasicOCSPResp brep = (BasicOCSPResp) ocspResponse.getResponseObject();
            try {
                if( ! brep.verify(responderCert.getPublicKey(),"BC")) {
                    throw new CertPathValidatorException("OCSP response is not verified");
                }
            } catch (NoSuchProviderException e) {
                throw new CertPathValidatorException("OCSP response could not be verified ("+e.getMessage()+")" ,null, cp, certIndex);
            }
            SingleResp[] singleResp = brep.getResponses();
            boolean foundResponse = false;
            for (SingleResp resp : singleResp) {
                CertificateID respCertID = resp.getCertID();
                if (respCertID.equals(certID)) {
                    Object status = resp.getCertStatus();
                    if (status == CertificateStatus.GOOD) {
                        Log.debug("OCSPChecker: Status of certificate (with serial number " +
                                serialNumber.toString() + ") is: good");
                        foundResponse = true;
                        break;
                    } else if (status instanceof org.bouncycastle.ocsp.RevokedStatus) {
                        Log.debug("OCSPChecker: Status of certificate (with serial number " +
                                serialNumber.toString() + ") is: revoked");
                        throw new CertPathValidatorException("Certificate has been revoked", null, cp, certIndex);
                    } else if (status instanceof org.bouncycastle.ocsp.UnknownStatus) {
                        Log.debug("OCSPChecker: Status of certificate (with serial number " +
                                serialNumber.toString() + ") is: unknown");
                        throw new CertPathValidatorException("Certificate's revocation status is unknown", null, cp, certIndex);
                    } else {
                        Log.debug("Status of certificate (with serial number " +
                                serialNumber.toString() + ") is: not recognized");
                        throw new CertPathValidatorException("Unknown OCSP response for certificate", null, cp, certIndex);
                    }
                }
            }

            // Check that response applies to the cert that was supplied
            if (!foundResponse) {
                throw new CertPathValidatorException(
                        "No certificates in the OCSP response match the " +
                        "certificate supplied in the OCSP request.");
            }
        } catch (CertPathValidatorException cpve) {
            throw cpve;
        } catch (Exception e) {
            throw new CertPathValidatorException(e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ioe) {
                    throw new CertPathValidatorException(ioe);
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ioe) {
                    throw new CertPathValidatorException(ioe);
                }
            }
        }
    }
}
