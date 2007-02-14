/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.LinkedList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class that provides similar functionality to the keytool tool. Generated certificates
 * conform to the XMPP spec where domains are kept in the subject alternative names extension.
 *
 * @author Gaston Dombiak
 */
public class CertificateManager {

    private static Pattern cnPattern = Pattern.compile("(?i)(cn=)([^,]*)");

    private static Provider provider = new BouncyCastleProvider();

    /**
     * The maximum length of lines in certification requests
     */
    private static final int CERT_REQ_LINE_LENGTH = 76;

    private static List<CertificateEventListener> listeners = new CopyOnWriteArrayList<CertificateEventListener>();
    
    static {
        // Add the BC provider to the list of security providers
        Security.addProvider(provider);
    }

    /**
     * Creates a new X509 certificate using the DSA algorithm. The new certificate together with its private
     * key are stored in the specified key store. However, the key store is not saved to the disk. This means
     * that it is up to the "caller" to save the key store to disk after new certificates have been added
     * to the store.
     *
     * @param ksKeys    key store where the new certificate and private key are going to be stored.
     * @param keyPassword password of the keystore.
     * @param alias     name to use when storing the certificate in the key store.
     * @param issuerDN  Issuer string e.g "O=Grid,OU=OGSA,CN=ACME"
     * @param subjectDN Subject string e.g "O=Grid,OU=OGSA,CN=John Doe"
     * @param domain    domain of the server to store in the subject alternative name extension.
     * @return the new X509 V3 Certificate.
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static X509Certificate createDSACert(KeyStore ksKeys, String keyPassword, String alias, String issuerDN,
                                                String subjectDN, String domain)
            throws GeneralSecurityException, IOException {
        // Generate public and private keys
        KeyPair keyPair = generateKeyPair("DSA", 1024);
        // Create X509 certificate with keys and specified domain
        X509Certificate cert = createX509V3Certificate(keyPair, 60, issuerDN, subjectDN, domain, "SHA1withDSA");
        // Store new certificate and private key in the keystore
        ksKeys.setKeyEntry(alias, keyPair.getPrivate(), keyPassword.toCharArray(), new X509Certificate[]{cert});
        // Notify listeners that a new certificate has been created
        for (CertificateEventListener listener : listeners) {
            try {
                listener.certificateCreated(ksKeys, alias, cert);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        // Return new certificate
        return cert;
    }

    /**
     * Creates a new X509 certificate using the RSA algorithm. The new certificate together with its private
     * key are stored in the specified key store. However, the key store is not saved to the disk. This means
     * that it is up to the "caller" to save the key store to disk after new certificates have been added
     * to the store.
     *
     * @param ksKeys    key store where the new certificate and private key are going to be stored.
     * @param keyPassword password of the keystore.
     * @param alias     name to use when storing the certificate in the key store.
     * @param issuerDN  Issuer string e.g "O=Grid,OU=OGSA,CN=ACME"
     * @param subjectDN Subject string e.g "O=Grid,OU=OGSA,CN=John Doe"
     * @param domain    domain of the server to store in the subject alternative name extension.
     * @return the new X509 V3 Certificate.
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static X509Certificate createRSACert(KeyStore ksKeys, String keyPassword, String alias, String issuerDN,
                                                String subjectDN, String domain)
            throws GeneralSecurityException, IOException {
        // Generate public and private keys
        KeyPair keyPair = generateKeyPair("RSA", 1024);
        // Create X509 certificate with keys and specified domain
        X509Certificate cert = createX509V3Certificate(keyPair, 60, issuerDN, subjectDN, domain, "MD5withRSA");
        // Store new certificate and private key in the keystore
        ksKeys.setKeyEntry(alias, keyPair.getPrivate(), keyPassword.toCharArray(), new X509Certificate[]{cert});
        // Notify listeners that a new certificate has been created
        for (CertificateEventListener listener : listeners) {
            try {
                listener.certificateCreated(ksKeys, alias, cert);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        // Return new certificate
        return cert;
    }

    /**
     * Deletes the specified certificate from the
     *
     * @param ksKeys    key store where the certificate is stored.
     * @param alias     alias of the certificate to delete.
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static void deleteCertificate(KeyStore ksKeys, String alias) throws GeneralSecurityException, IOException {
        ksKeys.deleteEntry(alias);
        // Notify listeners that a new certificate has been created
        for (CertificateEventListener listener : listeners) {
            try {
                listener.certificateDeleted(ksKeys, alias);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    /**
     * Returns the identities of the remote server as defined in the specified certificate. The
     * identities are defined in the subjectDN of the certificate and it can also be defined in
     * the subjectAltName extensions of type "xmpp". When the extension is being used then the
     * identities defined in the extension are going to be returned. Otherwise, the value stored in
     * the subjectDN is returned.
     *
     * @param x509Certificate the certificate the holds the identities of the remote server.
     * @return the identities of the remote server as defined in the specified certificate.
     */
    public static List<String> getPeerIdentities(X509Certificate x509Certificate) {
        // Look the identity in the subjectAltName extension if available
        List<String> names = getSubjectAlternativeNames(x509Certificate);
        if (names.isEmpty()) {
            String name = x509Certificate.getSubjectDN().getName();
            Matcher matcher = cnPattern.matcher(name);
            if (matcher.find()) {
                name = matcher.group(2);
            }
            // Create an array with the unique identity
            names = new ArrayList<String>();
            names.add(name);
        }
        return names;
    }

    /**
     * Returns the JID representation of an XMPP entity contained as a SubjectAltName extension
     * in the certificate. If none was found then return <tt>null</tt>.
     *
     * @param certificate the certificate presented by the remote entity.
     * @return the JID representation of an XMPP entity contained as a SubjectAltName extension
     *         in the certificate. If none was found then return <tt>null</tt>.
     */
    private static List<String> getSubjectAlternativeNames(X509Certificate certificate) {
        List<String> identities = new ArrayList<String>();
        try {
            Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
            // Check that the certificate includes the SubjectAltName extension
            if (altNames == null) {
                return Collections.emptyList();
            }
            // Use the type OtherName to search for the certified server name
            for (List item : altNames) {
                Integer type = (Integer) item.get(0);
                if (type == 0) {
                    // Type OtherName found so return the associated value
                    try {
                        // Value is encoded using ASN.1 so decode it to get the server's identity
                        ASN1InputStream decoder = new ASN1InputStream((byte[]) item.toArray()[1]);
                        DEREncodable encoded = decoder.readObject();
                        encoded = ((DERSequence) encoded).getObjectAt(1);
                        encoded = ((DERTaggedObject) encoded).getObject();
                        encoded = ((DERTaggedObject) encoded).getObject();
                        String identity = ((DERUTF8String) encoded).getString();
                        if (!"".equals(identity)) {
                            // Add the decoded server name to the list of identities
                            identities.add(identity);
                        }
                    }
                    catch (UnsupportedEncodingException e) {
                        // Ignore
                    }
                    catch (IOException e) {
                        // Ignore
                    }
                    catch (Exception e) {
                        Log.error("Error decoding subjectAltName", e);
                    }
                }
                // Other types are not good for XMPP so ignore them
                else if (Log.isDebugEnabled()) {
                    Log.debug("SubjectAltName of invalid type found: " + certificate);
                }
            }
        }
        catch (CertificateParsingException e) {
            Log.error("Error parsing SubjectAltName in certificate: " + certificate, e);
        }
        return identities;
    }

    /**
     * Returns true if an RSA certificate was found in the specified keystore for the specified domain.
     *
     * @param ksKeys the keystore that contains the certificates.
     * @param domain domain of the server signed by the certificate.
     * @return true if an RSA certificate was found in the specified keystore for the specified domain.
     * @throws KeyStoreException
     */
    public static boolean isRSACertificate(KeyStore ksKeys, String domain) throws KeyStoreException {
        return isCertificate(ksKeys, domain, "RSA");
    }

    /**
     * Returns true if an DSA certificate was found in the specified keystore for the specified domain.
     *
     * @param ksKeys the keystore that contains the certificates.
     * @param domain domain of the server signed by the certificate.
     * @return true if an DSA certificate was found in the specified keystore for the specified domain.
     * @throws KeyStoreException
     */
    public static boolean isDSACertificate(KeyStore ksKeys, String domain) throws KeyStoreException {
        return isCertificate(ksKeys, domain, "DSA");
    }

    /**
     * Returns true if the specified certificate is using the DSA algorithm. The DSA algorithm is not
     * good for encryption but only for authentication. On the other hand, the RSA algorithm is good
     * for encryption and authentication.
     *
     * @param certificate the certificate to analyze.
     * @return true if the specified certificate is using the DSA algorithm.
     * @throws KeyStoreException
     */
    public static boolean isDSACertificate(X509Certificate certificate) throws KeyStoreException {
        return certificate.getPublicKey().getAlgorithm().equals("DSA");
    }

    /**
     * Returns true if a certificate with the specifed configuration was found in the key store.
     *
     * @param ksKeys the keystore to use for searching the certificate.
     * @param domain the domain present in the subjectAltName or "*" if anything is accepted.
     * @param algorithm the DSA or RSA algorithm used by the certificate.
     * @return true if a certificate with the specifed configuration was found in the key store.
     * @throws KeyStoreException
     */
    private static boolean isCertificate(KeyStore ksKeys, String domain, String algorithm) throws KeyStoreException {
        for (Enumeration<String> aliases = ksKeys.aliases(); aliases.hasMoreElements();) {
            X509Certificate certificate = (X509Certificate) ksKeys.getCertificate(aliases.nextElement());
            if ("*".equals(domain)) {
                // Any domain certified by the certificate is accepted
                if (certificate.getPublicKey().getAlgorithm().equals(algorithm)) {
                    return true;
                }
            }
            else {
                // Only accept certified domains that match the specified domain
                for (String identity : getPeerIdentities(certificate)) {
                    if (identity.endsWith(domain) && certificate.getPublicKey().getAlgorithm().equals(algorithm)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Creates and returns the content of a new singing request for the specified certificate. Signing
     * requests are required by Certificate Authorities as part of their signing process. The signing request
     * contains information about the certificate issuer, subject DN, subject alternative names and public key.
     * Private keys are not included. After the Certificate Authority verified and signed the certificate a new
     * certificate is going to be returned. Use {@link #installReply(java.security.KeyStore, java.security.KeyStore, String, String, java.io.InputStream, boolean, boolean)}
     * to import the CA reply.
     *
     * @param cert the certificate to create a signing request.
     * @param privKey the private key of the certificate.
     * @return the content of a new singing request for the specified certificate.
     * @throws Exception
     */
    public static String createSigningRequest(X509Certificate cert, PrivateKey privKey) throws Exception {
        StringBuilder sb = new StringBuilder();

        String subject = cert.getSubjectDN().getName();
        X509Name xname = new X509Name(subject);

        PublicKey pubKey = cert.getPublicKey();

        String signatureAlgorithm = "DSA".equals(pubKey.getAlgorithm()) ? "SHA1withDSA" : "MD5withRSA";

        PKCS10CertificationRequest csr =
                new PKCS10CertificationRequest(signatureAlgorithm, xname, pubKey, null, privKey);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DEROutputStream deros = new DEROutputStream(baos);
        deros.writeObject(csr.getDERObject());
        String sTmp = new String(org.bouncycastle.util.encoders.Base64.encode(baos.toByteArray()));

        // Header
        sb.append("-----BEGIN NEW CERTIFICATE REQUEST-----\n");

        // Add signing request content (base 64 encoded)
        for (int iCnt = 0; iCnt < sTmp.length(); iCnt += CERT_REQ_LINE_LENGTH) {
            int iLineLength;

            if ((iCnt + CERT_REQ_LINE_LENGTH) > sTmp.length()) {
                iLineLength = sTmp.length() - iCnt;
            } else {
                iLineLength = CERT_REQ_LINE_LENGTH;
            }

            sb.append(sTmp.substring(iCnt, iCnt + iLineLength)).append("\n");
        }

        // Footer
        sb.append("-----END NEW CERTIFICATE REQUEST-----\n");
        return sb.toString();
    }

    /**
     * Installs the Certificate Authority reply returned as part of the signing request. The certificate
     * being signed will get its certificate chain updated with the imported certificate(s). An exception
     * will be thrown if the replied certificate does not match a local certificate or if the signing
     * authority is not known by the server (i.e. keystore and truststore files). When <tt>trustCACerts</tt>
     * is set to <tt>true</tt> then certificates present in the truststore file will be used to verify the
     * identity of the entity signing the certificate. In case the reply is composed of more than one
     * certificate then you can also specify if you want to verify that the root certificate in the chain
     * can be trusted.
     *
     * @param keyStore    key store where the certificate is stored.
     * @param trustStore  key store where ca certificates are stored.
     * @param keyPassword password of the keystore.
     * @param alias the alias of the existing certificate being signed.
     * @param inputStream the stream containing the CA reply.
     * @param trustCACerts true if certificates present in the truststore file will be used to verify the
     *        identity of the entity signing the certificate.
     * @param validateRoot true if you want to verify that the root certificate in the chain can be trusted
     *        based on the truststore.
     * @return true if the CA reply was successfully processed.
     * @throws Exception
     */
    public static boolean installReply(KeyStore keyStore, KeyStore trustStore, String keyPassword, String alias, InputStream inputStream, boolean trustCACerts,
                                       boolean validateRoot) throws Exception {

        // Check that there is a certificate for the specified alias
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
        if (certificate == null) {
            Log.warn("Certificate not found for alias: " + alias);
            return false;
        }
        // Retrieve the private key of the stored certificate
        PrivateKey privKey = (PrivateKey) keyStore.getKey(alias, keyPassword.toCharArray());
        // Load certificates found in the PEM input stream
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        for (Certificate cert : CertificateFactory.getInstance("X509").generateCertificates(inputStream)) {
            certs.add((X509Certificate) cert);
        }
        if (certs.isEmpty()) {
            throw new Exception("Reply has no certificates");
        }
        List<X509Certificate> newCerts;
        if (certs.size() == 1) {
            // Reply has only one certificate
            newCerts = establishCertChain(keyStore, trustStore, certificate, certs.get(0), trustCACerts);
        } else {
            // Reply has a chain of certificates
            newCerts = validateReply(keyStore, trustStore, alias, certificate, certs, trustCACerts, validateRoot);
        }
        if (newCerts != null) {
            keyStore.setKeyEntry(alias, privKey, keyPassword.toCharArray(),
                    newCerts.toArray(new X509Certificate[newCerts.size()]));

            // Notify listeners that a new certificate has been created
            for (CertificateEventListener listener : listeners) {
                try {
                    listener.certificateSigned(keyStore, alias, newCerts);
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * Imports a new signed certificate and its private key into the keystore. The certificate input
     * stream may contain the signed certificate as well as its CA chain.
     *
     * @param keyStore    key store where the certificate will be stored.
     * @param trustStore  key store where ca certificates are stored.
     * @param keyPassword password of the keystore.
     * @param alias the alias of the the new signed certificate.
     * @param pkInputStream the stream containing the private key.
     * @param inputStream the stream containing the signed certificate.
     * @param trustCACerts true if certificates present in the truststore file will be used to verify the
     *        identity of the entity signing the certificate.
     * @param validateRoot true if you want to verify that the root certificate in the chain can be trusted
     *        based on the truststore.
     * @return true if the certificate was successfully imported.
     * @throws Exception if no certificates were found in the inputStream.
     */
    public static boolean installCert(KeyStore keyStore, KeyStore trustStore, String keyPassword, String alias,
                                      InputStream pkInputStream, InputStream inputStream, boolean trustCACerts,
                                      boolean validateRoot) throws Exception {
        // Check that there is a certificate for the specified alias
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
        if (certificate != null) {
            Log.warn("Certificate already exists for alias: " + alias);
            return false;
        }
        // Retrieve the private key of the stored certificate
        PEMReader pemReader = new PEMReader(new InputStreamReader(pkInputStream));
        KeyPair kp = (KeyPair) pemReader.readObject();
        PrivateKey privKey = kp.getPrivate();

        // Load certificates found in the PEM input stream
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        for (Certificate cert : CertificateFactory.getInstance("X509").generateCertificates(inputStream)) {
            certs.add((X509Certificate) cert);
        }
        if (certs.isEmpty()) {
            throw new Exception("No certificates were found");
        }
        List<X509Certificate> newCerts;
        if (certs.size() == 1) {
            // Reply has only one certificate
            newCerts = establishCertChain(keyStore, trustStore, certificate, certs.get(0), trustCACerts);
        } else {
            // Reply has a chain of certificates
            newCerts = validateReply(keyStore, trustStore, alias, certificate, certs, trustCACerts, validateRoot);
        }
        if (newCerts != null) {
            keyStore.setKeyEntry(alias, privKey, keyPassword.toCharArray(),
                    newCerts.toArray(new X509Certificate[newCerts.size()]));

            // Notify listeners that a new certificate has been created (and signed)
            for (CertificateEventListener listener : listeners) {
                try {
                    listener.certificateCreated(keyStore, alias, certs.get(0));
                    if (newCerts.size() > 1) {
                        listener.certificateSigned(keyStore, alias, newCerts);
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(CertificateEventListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(CertificateEventListener listener) {
        listeners.remove(listener);
    }

    private static List<X509Certificate> establishCertChain(KeyStore keyStore, KeyStore trustStore,
                                                                  X509Certificate certificate,
                                                                  X509Certificate certReply, boolean trustCACerts)
            throws Exception {
        if (certificate != null) {
            PublicKey publickey = certificate.getPublicKey();
            PublicKey publickey1 = certReply.getPublicKey();
            if (!publickey.equals(publickey1)) {
                throw new Exception("Public keys in reply and keystore don't match");
            }
            if (certReply.equals(certificate)) {
                throw new Exception("Certificate reply and certificate in keystore are identical");
            }
        }
        Map<Principal, List<X509Certificate>> knownCerts = new Hashtable<Principal, List<X509Certificate>>();
        if (keyStore.size() > 0) {
            knownCerts.putAll(getCertsByIssuer(keyStore));
        }
        if (trustCACerts && trustStore.size() > 0) {
            knownCerts.putAll(getCertsByIssuer(trustStore));
        }
        LinkedList<X509Certificate> answer = new LinkedList<X509Certificate>();
        if (buildChain(certReply, answer, knownCerts)) {
            return answer;
        } else {
            throw new Exception("Failed to establish chain from reply");
        }
    }


    /**
     * Builds the certificate chain of the specified certificate based on the known list of certificates
     * that were issued by their respective Principals. Returns true if the entire chain of all certificates
     * was successfully built.
     *
     * @param certificate certificate to build its chain.
     * @param answer      the certificate chain for the corresponding certificate.
     * @param knownCerts  list of known certificates grouped by their issues (i.e. Principals).
     * @return true if the entire chain of all certificates was successfully built.
     */
    private static boolean buildChain(X509Certificate certificate, LinkedList<X509Certificate> answer,
                                      Map<Principal, List<X509Certificate>> knownCerts) {
        Principal subject = certificate.getSubjectDN();
        Principal issuer = certificate.getIssuerDN();
        // Check if the certificate is a root certificate (i.e. was issued by the same Principal that
        // is present in the subject)
        if (subject.equals(issuer)) {
            answer.addFirst(certificate);
            return true;
        }
        // Get the list of known certificates of the certificate's issuer
        List<X509Certificate> issuerCerts = knownCerts.get(issuer);
        if (issuerCerts == null || issuerCerts.isEmpty()) {
            // No certificates were found so building of chain failed
            return false;
        }
        for (X509Certificate issuerCert : issuerCerts) {
            PublicKey publickey = issuerCert.getPublicKey();
            try {
                // Verify the certificate with the specified public key
                certificate.verify(publickey);
                // Certificate was verified successfully so build chain of issuer's certificate
                if (!buildChain(issuerCert, answer, knownCerts)) {
                    return false;
                }
            }
            catch (Exception exception) {
                // Failed to verify certificate
                return false;
            }
        }
        answer.addFirst(certificate);
        return true;
    }

    /**
     * Returns a Map where the key holds the certificate issuers and values the certificates of each issuer.
     *
     * @param ks the keystore to get its certs per issuer.
     * @return a map with the certificates per issuer.
     * @throws Exception
     */
    private static Map<Principal, List<X509Certificate>> getCertsByIssuer(KeyStore ks)
            throws Exception {
        Map<Principal, List<X509Certificate>> answer = new HashMap<Principal, List<X509Certificate>>();
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (cert != null) {
                Principal subjectDN = cert.getSubjectDN();
                List<X509Certificate> vec = answer.get(subjectDN);
                if (vec == null) {
                    vec = new ArrayList<X509Certificate>();
                    vec.add(cert);
                }
                else {
                    if (!vec.contains(cert)) {
                        vec.add(cert);
                    }
                }
                answer.put(subjectDN, vec);
            }
        }
        return answer;
    }

    /**
     * Validates chain in certification reply, and returns the ordered
     * elements of the chain (with user certificate first, and root
     * certificate last in the array).
     *
     * @param alias the alias name
     * @param userCert the user certificate of the alias
     * @param replyCerts the chain provided in the reply
     */
    private static List<X509Certificate> validateReply(KeyStore keyStore, KeyStore trustStore, String alias,
                                                             X509Certificate userCert, List<X509Certificate> replyCerts,
                                                             boolean trustCACerts, boolean verifyRoot)
            throws Exception {
        // order the certs in the reply (bottom-up).
        int i;
        PublicKey userPubKey = userCert.getPublicKey();
        for (i = 0; i < replyCerts.size(); i++) {
            if (userPubKey.equals(replyCerts.get(i).getPublicKey())) {
                break;
            }
        }
        if (i == replyCerts.size()) {
            throw new Exception(
                    "Certificate reply does not contain public key for <alias>: " + alias);
        }

        X509Certificate tmpCert = replyCerts.get(0);
        replyCerts.set(0, replyCerts.get(i));
        replyCerts.set(i, tmpCert);
        Principal issuer = replyCerts.get(0).getIssuerDN();

        for (i = 1; i < replyCerts.size() - 1; i++) {
            // find a cert in the reply whose "subject" is the same as the
            // given "issuer"
            int j;
            for (j = i; j < replyCerts.size(); j++) {
                Principal subject = replyCerts.get(j).getSubjectDN();
                if (subject.equals(issuer)) {
                    tmpCert = replyCerts.get(i);
                    replyCerts.set(i, replyCerts.get(j));
                    replyCerts.set(j, tmpCert);
                    issuer = replyCerts.get(i).getIssuerDN();
                    break;
                }
            }
            if (j == replyCerts.size()) {
                throw new Exception("Incomplete certificate chain in reply");
            }
        }

        // now verify each cert in the ordered chain
        for (i = 0; i < replyCerts.size() - 1; i++) {
            PublicKey pubKey = replyCerts.get(i + 1).getPublicKey();
            try {
                replyCerts.get(i).verify(pubKey);
            }
            catch (Exception e) {
                throw new Exception(
                        "Certificate chain in reply does not verify: " + e.getMessage());
            }
        }

        if (!verifyRoot) {
            return replyCerts;
        }

        // do we trust the (root) cert at the top?
        X509Certificate topCert = replyCerts.get(replyCerts.size() - 1);
        boolean foundInKeyStore = keyStore.getCertificateAlias(topCert) != null;
        boolean foundInCAStore = trustCACerts && (trustStore.getCertificateAlias(topCert) != null);
        if (!foundInKeyStore && !foundInCAStore) {
            boolean verified = false;
            X509Certificate rootCert = null;
            if (trustCACerts) {
                for (Enumeration<String> aliases = trustStore.aliases(); aliases.hasMoreElements();) {
                    String name = aliases.nextElement();
                    rootCert = (X509Certificate) trustStore.getCertificate(name);
                    if (rootCert != null) {
                        try {
                            topCert.verify(rootCert.getPublicKey());
                            verified = true;
                            break;
                        }
                        catch (Exception e) {
                            // Ignore
                        }
                    }
                }
            }
            if (!verified) {
                return null;
            }
            else {
                // Check if the cert is a self-signed cert
                if (!topCert.getSubjectDN().equals(topCert.getIssuerDN())) {
                    // append the (self-signed) root CA cert to the chain
                    replyCerts.add(rootCert);
                }
            }
        }

        return replyCerts;
    }

    /**
     * Creates an X509 version3 certificate.
     *
     * @param kp           KeyPair that keeps the public and private keys for the new certificate.
     * @param months       time to live
     * @param issuerDN     Issuer string e.g "O=Grid,OU=OGSA,CN=ACME"
     * @param subjectDN    Subject string e.g "O=Grid,OU=OGSA,CN=John Doe"
     * @param domain       Domain of the server.
     * @param signAlgoritm Signature algorithm. This can be either a name or an OID.
     * @return X509 V3 Certificate
     * @throws GeneralSecurityException
     * @throws IOException
     */
    private static synchronized X509Certificate createX509V3Certificate(KeyPair kp, int months, String issuerDN,
                                                                        String subjectDN, String domain,
                                                                        String signAlgoritm)
            throws GeneralSecurityException, IOException {
        PublicKey pubKey = kp.getPublic();
        PrivateKey privKey = kp.getPrivate();

        byte[] serno = new byte[8];
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed((new Date().getTime()));
        random.nextBytes(serno);
        BigInteger serial = (new java.math.BigInteger(serno)).abs();

        X509V3CertificateGenerator certGenerator = new X509V3CertificateGenerator();
        certGenerator.reset();

        certGenerator.setSerialNumber(serial);
        certGenerator.setIssuerDN(new X509Name(issuerDN));
        certGenerator.setNotBefore(new Date(System.currentTimeMillis()));
        certGenerator.setNotAfter(
                new Date(System.currentTimeMillis() + months * (1000L * 60 * 60 * 24 * 30)));
        certGenerator.setSubjectDN(new X509Name(subjectDN));
        certGenerator.setPublicKey(pubKey);
        certGenerator.setSignatureAlgorithm(signAlgoritm);

        // Generate the subject alternative name
        boolean critical = subjectDN == null || "".equals(subjectDN.trim());
        DERSequence othernameSequence = new DERSequence(new ASN1Encodable[]{
                new DERObjectIdentifier("1.3.6.1.5.5.7.8.5"), new DERTaggedObject(true, 0, new DERUTF8String(domain))});
        GeneralName othernameGN = new GeneralName(GeneralName.otherName, othernameSequence);
        GeneralNames subjectAltNames = new GeneralNames(new DERSequence(new ASN1Encodable[]{othernameGN}));
        // Add subject alternative name extension
        certGenerator.addExtension(X509Extensions.SubjectAlternativeName, critical, subjectAltNames);

        X509Certificate cert =
                certGenerator.generateX509Certificate(privKey, "BC", new SecureRandom());
        cert.checkValidity(new Date());
        cert.verify(pubKey);

        return cert;
    }

    /**
     * Returns a new public & private key with the specified algorithm (e.g. DSA, RSA, etc.).
     *
     * @param algorithm DSA, RSA, etc.
     * @param keysize   the keysize. This is an algorithm-specific metric, such as modulus
     *                  length, specified in number of bits.
     * @return a new public & private key with the specified algorithm (e.g. DSA, RSA, etc.).
     * @throws GeneralSecurityException
     */
    private static KeyPair generateKeyPair(String algorithm, int keysize) throws GeneralSecurityException {
        KeyPairGenerator generator;
        if (provider == null) {
            generator = KeyPairGenerator.getInstance(algorithm);
        } else {
            generator = KeyPairGenerator.getInstance(algorithm, provider);
        }
        generator.initialize(keysize, new SecureRandom());
        return generator.generateKeyPair();
    }
}
