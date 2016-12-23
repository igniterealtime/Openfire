/**
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

package org.jivesoftware.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.CertException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.MiscPEMGenerator;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObjectGenerator;
import org.bouncycastle.util.io.pem.PemWriter;
import org.jivesoftware.openfire.keystore.CertificateStore;
import org.jivesoftware.openfire.keystore.CertificateUtils;
import org.jivesoftware.util.cert.CNCertificateIdentityMapping;
import org.jivesoftware.util.cert.CertificateIdentityMapping;
import org.jivesoftware.util.cert.SANCertificateIdentityMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that provides similar functionality to the keytool tool. Generated certificates
 * conform to the XMPP spec where domains are kept in the subject alternative names extension.
 *
 * @author Gaston Dombiak
 */
public class CertificateManager {

	private static final Logger Log = LoggerFactory.getLogger( CertificateManager.class );

    private static Pattern valuesPattern = Pattern.compile("(?i)(=)([^,]*)");


    private static List<CertificateEventListener> listeners = new CopyOnWriteArrayList<>();

    private static List<CertificateIdentityMapping> serverCertMapping = new ArrayList<>();
    
    private static List<CertificateIdentityMapping> clientCertMapping = new ArrayList<>();
    
    static {

        String serverCertIdentityMapList = JiveGlobals.getProperty("provider.serverCertIdentityMap.classList");
        if (serverCertIdentityMapList != null) {
        	StringTokenizer st = new StringTokenizer(serverCertIdentityMapList, " ,\t\n\r\f");
            while (st.hasMoreTokens()) {
                String s_provider = st.nextToken();
                try {
                    Class<?> c_provider = ClassUtils.forName(s_provider);
                    CertificateIdentityMapping provider =
                            (CertificateIdentityMapping)(c_provider.newInstance());
                    Log.debug("CertificateManager: Loaded server identity mapping " + s_provider);
                    serverCertMapping.add(provider);
                }
                catch (Exception e) {
                    Log.error("CertificateManager: Error loading CertificateIdentityMapping: " + s_provider + "\n" + e);
                }
            }
        }
        
        if (serverCertMapping.isEmpty()) {
        	Log.debug("CertificateManager: No server CertificateIdentityMapping's found. Loading default mappings");
        	serverCertMapping.add(new SANCertificateIdentityMapping());
        	serverCertMapping.add(new CNCertificateIdentityMapping());   	
        }
                
        String clientCertMapList = JiveGlobals.getProperty("provider.clientCertIdentityMap.classList");
        if (clientCertMapList != null) {
        	StringTokenizer st = new StringTokenizer(clientCertMapList, " ,\t\n\r\f");
            while (st.hasMoreTokens()) {
                String s_provider = st.nextToken();
                try {
                    Class<?> c_provider = ClassUtils.forName(s_provider);
                    CertificateIdentityMapping provider =
                            (CertificateIdentityMapping)(c_provider.newInstance());
                    Log.debug("CertificateManager: Loaded client identity mapping " + s_provider);
                    clientCertMapping.add(provider);
                }
                catch (Exception e) {
                    Log.error("CertificateManager: Error loading CertificateIdentityMapping: " + s_provider + "\n" + e);
                }
            }
        }
        
        if (clientCertMapping.isEmpty()) {
        	Log.debug("CertificateManager: No client CertificateIdentityMapping's found. Loading default mappings");
        	clientCertMapping.add(new CNCertificateIdentityMapping());
        }
    }

    /**
     * Decide whether or not to trust the given supplied certificate chain, returning the
     * End Entity Certificate in this case where it can, and null otherwise.
     * A self-signed certificate will, for example, return null.
     * For certain failures, we SHOULD generate an exception - revocations and the like,
     * but we currently do not.
     *
     * @param chain an array of X509Certificate where the first one is the endEntityCertificate.
     * @param certStore a keystore containing untrusted certificates (including ICAs, etc).
     * @param trustStore a keystore containing Trust Anchors (most-trusted CA certificates).
     * @return trusted end-entity certificate, or null.
     */
    public static X509Certificate getEndEntityCertificate(Certificate chain[],
            KeyStore certStore, KeyStore trustStore) {
        if (chain.length == 0) {
            return null;
        }
        X509Certificate first = (X509Certificate) chain[0];
        try {
            first.checkValidity();
        } catch(CertificateException e) {
            Log.warn("EE Certificate not valid: " + e.getMessage());
            return null;
        }
        if (chain.length == 1
                && first.getSubjectX500Principal().equals(first.getIssuerX500Principal())) {
            // Chain is single cert, and self-signed.
            try {
                if (trustStore.getCertificateAlias(first) != null) {
                    // Interesting case: trusted self-signed cert.
                    return first;
                }
            } catch (KeyStoreException e) {
                Log.warn("Keystore error while looking for self-signed cert; assuming untrusted.");
            }
            return null;
        }
        final List<Certificate> all_certs = new ArrayList<>();
        try {
            // First, load up certStore contents into a CertStore.
            // It's a mystery why these objects are different.
            for (Enumeration<String> aliases = certStore.aliases(); aliases
                    .hasMoreElements();) {
                String alias = aliases.nextElement();
                if (certStore.isCertificateEntry(alias)) {
                    X509Certificate cert = (X509Certificate) certStore
                            .getCertificate(alias);
                    all_certs.add(cert);
                }
            }
            // Now add the trusted certs.
            for (Enumeration<String> aliases = trustStore.aliases(); aliases
                    .hasMoreElements();) {
                String alias = aliases.nextElement();
                if (trustStore.isCertificateEntry(alias)) {
                    X509Certificate cert = (X509Certificate) trustStore
                            .getCertificate(alias);
                    all_certs.add(cert);
                }
            }
            // Finally, add all the certs in the chain:
            for (int i = 0; i < chain.length; ++i) {
                all_certs.add(chain[i]);
            }
            CertStore cs = CertStore.getInstance("Collection",
                    new CollectionCertStoreParameters(all_certs));
            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(first);
            // / selector.setSubject(first.getSubjectX500Principal());
            PKIXBuilderParameters params = new PKIXBuilderParameters(
                    trustStore, selector);
            params.addCertStore(cs);
            params.setDate(new Date());
            params.setRevocationEnabled(false);
            /* Code here is the right way to do things. */
            CertPathBuilder pathBuilder = CertPathBuilder
                    .getInstance(CertPathBuilder.getDefaultType());
            CertPath cp = pathBuilder.build(params).getCertPath();
            /**
             * This section is an alternative to using CertPathBuilder which is
             * not as complete (or safe), but will emit much better errors. If
             * things break, swap around the code.
             *
             **** COMMENTED OUT. ****
            ArrayList<X509Certificate> ls = new ArrayList<X509Certificate>();
            for (int i = 0; i < chain.length; ++i) {
                ls.add((X509Certificate) chain[i]);
            }
            for (X509Certificate last = ls.get(ls.size() - 1); !last
                    .getIssuerX500Principal().equals(last.getSubjectX500Principal()); last = ls
                    .get(ls.size() - 1)) {
                X509CertSelector sel = new X509CertSelector();
                sel.setSubject(last.getIssuerX500Principal());
                ls.add((X509Certificate) cs.getCertificates(sel).toArray()[0]);
            }
            CertPath cp = CertificateFactory.getInstance("X.509").generateCertPath(ls);
             ****** END ALTERNATIVE. ****
             */
            // Not entirely sure if I need to do this with CertPathBuilder.
            // Can't hurt.
            CertPathValidator pathValidator = CertPathValidator
                    .getInstance("PKIX");
            pathValidator.validate(cp, params);
            return (X509Certificate) cp.getCertificates().get(0);
        } catch (CertPathBuilderException e) {
            Log.warn("Path builder: " + e.getMessage());
        } catch (CertPathValidatorException e) {
            Log.warn("Path validator: " + e.getMessage());
        } catch (Exception e) {
            Log.warn("Unkown exception while validating certificate chain: " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns the identities of the remote client as defined in the specified certificate. The
     * identities are mapped by the classes in the "provider.clientCertIdentityMap.classList" property. 
     * By default, the subjectDN of the certificate is used.
     *
     * @param x509Certificate the certificate the holds the identities of the remote server.
     * @return the identities of the remote client as defined in the specified certificate.
     */
    public static List<String> getClientIdentities(X509Certificate x509Certificate) {
    	
    	List<String> names = new ArrayList<>();
    	for (CertificateIdentityMapping mapping : clientCertMapping) {
    		List<String> identities = mapping.mapIdentity(x509Certificate);
    		Log.debug("CertificateManager: " + mapping.name() + " returned " + identities.toString());
    		if (!identities.isEmpty()) {
    			names.addAll(identities);
    			break;
    		}
    	}

        return names;
    }
    
    /**
     * Returns the identities of the remote server as defined in the specified certificate. The
     * identities are mapped by the classes in the "provider.serverCertIdentityMap.classList" property.
     * By default, the identities are defined in the subjectDN of the certificate and it can also be 
     * defined in the subjectAltName extensions of type "xmpp". When the extension is being used then the
     * identities defined in the extension are going to be returned. Otherwise, the value stored in
     * the subjectDN is returned.
     *
     * @param x509Certificate the certificate the holds the identities of the remote server.
     * @return the identities of the remote server as defined in the specified certificate.
     */
    public static List<String> getServerIdentities(X509Certificate x509Certificate) {
    	
    	List<String> names = new ArrayList<>();
    	for (CertificateIdentityMapping mapping : serverCertMapping) {
    		List<String> identities = mapping.mapIdentity(x509Certificate);
    		Log.debug("CertificateManager: " + mapping.name() + " returned " + identities.toString());
    		if (!identities.isEmpty()) {
    			names.addAll(identities);
    			break;
    		}
    	}

        return names;
    }

    /**
     * Returns true if an RSA certificate was found in the specified keystore for the specified domain.
     *
     * @param storeConfig the store to use for searching the certificate.
     * @param domain domain of the server signed by the certificate.
     * @return true if an RSA certificate was found in the specified keystore for the specified domain.
     * @throws KeyStoreException
     */
    public static boolean isRSACertificate(CertificateStore storeConfig, String domain) throws KeyStoreException {
        return isCertificate(storeConfig, domain, "RSA");
    }

    /**
     * Returns true if an DSA certificate was found in the specified keystore for the specified domain.
     *
     * @param storeConfig the store to use for searching the certificate.
     * @param domain domain of the server signed by the certificate.
     * @return true if an DSA certificate was found in the specified keystore for the specified domain.
     * @throws KeyStoreException
     */
    public static boolean isDSACertificate(CertificateStore storeConfig, String domain) throws KeyStoreException {
        return isCertificate( storeConfig, domain, "DSA" );
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
        return certificate.getPublicKey().getAlgorithm().equals( "DSA" );
    }

    /**
     * Returns true if a certificate with the specified configuration was found in a certificate store.
     *
     * @param storeConfig the store to use for searching the certificate.
     * @param domain the domain present in the subjectAltName or "*" if anything is accepted.
     * @param algorithm the DSA or RSA algorithm used by the certificate.
     * @return true if a certificate with the specified configuration was found in the key store.
     * @throws KeyStoreException
     */
    private static boolean isCertificate(CertificateStore storeConfig, String domain, String algorithm) throws KeyStoreException {
    	for (Enumeration<String> aliases = storeConfig.getStore().aliases(); aliases.hasMoreElements();) {
            X509Certificate certificate = (X509Certificate) storeConfig.getStore().getCertificate(aliases.nextElement());

            if ( !certificate.getPublicKey().getAlgorithm().equalsIgnoreCase( algorithm ) ) {
                continue;
            }

            if ("*".equals(domain)) {
                // Any domain certified by the certificate is accepted
                return true;
            }
            else {
                // Only accept certified domains that match the specified domain
                // TODO check that domain=foo.bar does not match identitiy "a.longerfoo.bar"
                for (String identity : getServerIdentities( certificate ) ) {
                    if (identity.endsWith(domain) ) {
                        return true;
                    }
                }
            }
        }

    	return false;
    }

    /**
     * Returns true if the specified certificate is a self-signed certificate.
     *
     * @return true if the specified certificate is a self-signed certificate.
     * @throws KeyStoreException if an error happens while usign the keystore
     */
    public static boolean isSelfSignedCertificate(X509Certificate certificate) throws KeyStoreException {
        try {
            certificate.verify(certificate.getPublicKey());
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /**
     * Returns true if the specified certificate is ready to be signed by a Certificate Authority. Self-signed
     * certificates need to get their issuer information entered to be able to generate a Certificate
     * Signing Request (CSR).
     *
     * @return true if the specified certificate is ready to be signed by a Certificate Authority.
     * @throws KeyStoreException if an error happens while usign the keystore
     */
    public static boolean isSigningRequestPending(X509Certificate certificate) throws KeyStoreException {
        // Verify that this is a self-signed certificate
        if (!isSelfSignedCertificate(certificate)) {
            return false;
        }
        // Verify that the issuer information has been entered
        Matcher matcher = valuesPattern.matcher(certificate.getIssuerDN().toString());
        return matcher.find() && matcher.find();
    }

    /**
     * Creates and returns the content of a new singing request for the specified certificate. Signing
     * requests are required by Certificate Authorities as part of their signing process. The signing request
     * contains information about the certificate issuer, subject DN, subject alternative names and public key.
     * Private keys are not included. After the Certificate Authority verified and signed the certificate a new
     * certificate is going to be returned. Use {@link #installReply(java.security.KeyStore, java.security.KeyStore, char[], String, java.io.InputStream)}
     * to import the CA reply.
     *
     * @param cert the certificate to create a signing request.
     * @param privKey the private key of the certificate.
     * @return the content of a new singing request for the specified certificate.
     */
    public static String createSigningRequest(X509Certificate cert, PrivateKey privKey) throws OperatorCreationException, IOException {

        JcaPKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder( //
                cert.getSubjectX500Principal(), //
                cert.getPublicKey() //
                );

        String signatureAlgorithm = "SHA256WITH" + cert.getPublicKey().getAlgorithm();

        ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm).build(privKey);
        PKCS10CertificationRequest csr = csrBuilder.build(signer);

        StringWriter string = new StringWriter();
        PemWriter pemWriter = new PemWriter(string);

        PemObjectGenerator objGen = new MiscPEMGenerator(csr);
        pemWriter.writeObject(objGen);
        pemWriter.close();

        return string.toString();
    }

    /**
     * Installs the Certificate Authority reply returned as part of the signing request. The certificate
     * being signed will get its certificate chain updated with the imported certificate(s). An exception
     * will be thrown if the replied certificate does not match a local certificate or if the signing
     * authority is not known by the server (i.e. keystore and truststore files)
     *
     * The identity of the entity that has signed the reply is verified against the provided trust store.
     *
     * The
     *
     * @param keyStore    key store where the certificate is stored.
     * @param trustStore  key store where ca certificates are stored.
     * @param keyPassword password of the keystore.
     * @param alias the alias of the existing certificate being signed.
     * @param inputStream the stream containing the CA reply.
     * @return true if the CA reply was successfully processed.
     * @throws Exception
     */
    public static boolean installReply(KeyStore keyStore, KeyStore trustStore, char[] keyPassword, String alias, InputStream inputStream) throws Exception {

        // Check that there is a certificate for the specified alias
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate( alias );
        if (certificate == null) {
            Log.warn("Certificate not found for alias: " + alias);
            return false;
        }
        // Retrieve the private key of the stored certificate
        PrivateKey privKey = (PrivateKey) keyStore.getKey(alias, keyPassword);
        // Load certificates found in the PEM input stream
        Collection<X509Certificate> certs = parseCertificates( inputStream );
        if (certs.isEmpty()) {
            throw new Exception("Reply has no certificates");
        }
        List<X509Certificate> newCerts;
        if (certs.size() == 1) {
            // Reply has only one certificate
            newCerts = establishCertChain(keyStore, trustStore, null, certs.iterator().next());
        } else {
            // Reply has a chain of certificates
            newCerts = validateReply(keyStore, trustStore, alias, null, certs);
        }
        if (newCerts == null)
        {
            return false;
        }
        keyStore.setKeyEntry(alias, privKey, keyPassword, newCerts.toArray(new X509Certificate[newCerts.size()]));

        // Notify listeners that a new certificate has been created
        for (CertificateEventListener listener : listeners) {
            try {
                listener.certificateSigned( keyStore, alias, newCerts );
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }

        return true;
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
     * @param passPhrase is the password phrased used when creating the private key.
     * @param inputStream the stream containing the signed certificate.
     * @return true if the certificate was successfully imported.
     * @throws Exception if no certificates were found in the inputStream.
     */
    public static boolean installCert(KeyStore keyStore, KeyStore trustStore, String keyPassword, String alias,
                                      InputStream pkInputStream, final String passPhrase, InputStream inputStream) throws Exception {
        // Check that there is a certificate for the specified alias
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
        if (certificate != null) {
            Log.warn("Certificate already exists for alias: " + alias);
            return false;
        }

        PrivateKey privKey = parsePrivateKey( pkInputStream, passPhrase );
        Collection<X509Certificate> certs = parseCertificates( inputStream );
        if (certs.isEmpty()) {
            throw new Exception("No certificates were found");
        }
        List<X509Certificate> newCerts;
        if (certs.size() == 1)
        {
            // Reply has only one certificate
            newCerts = establishCertChain(keyStore, trustStore, certificate, certs.iterator().next() );
        }
        else
        {
            // Reply has a chain of certificates
            newCerts = validateReply(keyStore, trustStore, alias, certificate, certs);
        }

        if (newCerts == null)
        {
            return false;
        }
        keyStore.setKeyEntry( alias, privKey, keyPassword.toCharArray(), newCerts.toArray( new X509Certificate[ newCerts.size() ] ) );

        // Notify listeners that a new certificate has been created (and signed)
        for (CertificateEventListener listener : listeners) {
            try {
                listener.certificateCreated( keyStore, alias, newCerts.get( 0 ) );
                if (newCerts.size() > 1) {
                    listener.certificateSigned(keyStore, alias, newCerts);
                }
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }

        return true;
    }


    public static PrivateKey parsePrivateKey(String pemRepresentation, String passPhrase) throws IOException {

        if (pemRepresentation == null || pemRepresentation.trim().isEmpty()) {
            throw new IllegalArgumentException("Argument 'pemRepresentation' cannot be null or an empty String.");
        }

        ByteArrayInputStream input = new ByteArrayInputStream(pemRepresentation.getBytes(StandardCharsets.UTF_8));
        return parsePrivateKey(input, passPhrase);
    }

    /**
     * Parses a PrivateKey instance from a PEM representation.
     *
     * When the provided key is encrypted, the provided pass phrase is applied.
     *
     * @param pemRepresentation a PEM representation of a private key (cannot be null or empty)
     * @param passPhrase optional pass phrase (must be present if the private key is encrypted).
     * @return a PrivateKey instance (never null)
     */
    public static PrivateKey parsePrivateKey(InputStream pemRepresentation, String passPhrase) throws IOException {

        if ( passPhrase == null ) {
            passPhrase = "";
        }
        try (Reader reader = new InputStreamReader(pemRepresentation); //
                PEMParser pemParser = new PEMParser(reader)) {

            final Object object = pemParser.readObject();
            final JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider( "BC" );

            final KeyPair kp;

            if ( object instanceof PEMEncryptedKeyPair )
            {
                // Encrypted key - we will use provided password
                final PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build( passPhrase.toCharArray() );
                kp = converter.getKeyPair( ( (PEMEncryptedKeyPair) object ).decryptKeyPair( decProv ) );
            }
            else if ( object instanceof PKCS8EncryptedPrivateKeyInfo )
            {
                // Encrypted key - we will use provided password
                try
                {
                    final PKCS8EncryptedPrivateKeyInfo encryptedInfo = (PKCS8EncryptedPrivateKeyInfo) object;
                    final InputDecryptorProvider provider = new JceOpenSSLPKCS8DecryptorProviderBuilder().build( passPhrase.toCharArray() );
                    final PrivateKeyInfo privateKeyInfo = encryptedInfo.decryptPrivateKeyInfo( provider );
                    return converter.getPrivateKey( privateKeyInfo );
                }
                catch ( PKCSException | OperatorCreationException e )
                {
                    throw new IOException( "Unable to decrypt private key.", e );
                }
            }
            else if ( object instanceof PrivateKeyInfo )
            {
                return converter.getPrivateKey( (PrivateKeyInfo) object );
            }
            else
            {
                // Unencrypted key - no password needed
                kp = converter.getKeyPair( (PEMKeyPair) object );
            }
            return kp.getPrivate();
        }
    }

    public static Collection<X509Certificate> parseCertificates(String pemRepresentation) throws IOException,
            CertificateException {

        // The parser is very picky. We should trim each line of the input string.
        final String pem = pemRepresentation //
                .replaceAll("(?m) +$", "") // remove trailing whitespace
                .replaceAll("(?m)^ +", ""); // remove leading whitespace

        ByteArrayInputStream input = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
        return parseCertificates(input);
    }

    /**
     * Parses a certificate chain from a PEM representation.
     *
     * @param pemRepresentation a PEM representation of a certificate or certificate chain (cannot be null or empty)
     * @return A collection of certificates (possibly empty, but never null).
     */
    @SuppressWarnings("unchecked")
    public static Collection<X509Certificate> parseCertificates(InputStream pemRepresentation) throws IOException,
            CertificateException {

        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X509", "BC");
        } catch (NoSuchProviderException e) {
            certificateFactory = CertificateFactory.getInstance("X509");
        }
        return (Collection<X509Certificate>) certificateFactory.generateCertificates(pemRepresentation);
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
        listeners.add( listener );
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(CertificateEventListener listener) {
        listeners.remove( listener );
    }

    private static List<X509Certificate> establishCertChain(KeyStore keyStore, KeyStore trustStore,
                                                                  X509Certificate certificate,
                                                                  X509Certificate certReply)
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
        Map<String, List<X509Certificate>> knownCerts = new Hashtable<>();

        // TODO Figure out why we add keystore issuers. This implies that we always trust the issuer of our identitity (which probably is right, but shouldn't be required)
        if (keyStore.size() > 0) {
            knownCerts.putAll(getCertsByIssuer(keyStore));
        }
        if (trustStore.size() > 0) {
            knownCerts.putAll(getCertsByIssuer(trustStore));
        }
        LinkedList<X509Certificate> answer = new LinkedList<>();
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
                                      Map<String, List<X509Certificate>> knownCerts) {
        Principal subject = certificate.getSubjectDN();
        Principal issuer = certificate.getIssuerDN();
        // Check if the certificate is a root certificate (i.e. was issued by the same Principal that
        // is present in the subject)
        if (subject.equals(issuer)) {
            answer.addFirst(certificate);
            return true;
        }
        // Get the list of known certificates of the certificate's issuer
        List<X509Certificate> issuerCerts = knownCerts.get(issuer.getName());
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
        answer.addFirst( certificate );
        return true;
    }

    /**
     * Returns a Map where the key holds the certificate issuers and values the certificates of each issuer.
     *
     * @param ks the keystore to get its certs per issuer.
     * @return a map with the certificates per issuer.
     * @throws Exception
     */
    private static Map<String, List<X509Certificate>> getCertsByIssuer(KeyStore ks)
            throws Exception {
        Map<String, List<X509Certificate>> answer = new HashMap<>();
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (cert != null) {
                Principal subjectDN = cert.getSubjectDN();
                List<X509Certificate> vec = answer.get(subjectDN);
                if (vec == null) {
                    vec = new ArrayList<>();
                    vec.add(cert);
                }
                else {
                    if (!vec.contains(cert)) {
                        vec.add(cert);
                    }
                }
                answer.put(subjectDN.getName(), vec);
            }
        }
        return answer;
    }

    /**
     * Orders certificates, starting from the entity to be validated and progressing back toward the CA root.
     *
     * This implementation matches "issuers" to "subjects" of certificates in such a way that "issuer" value of a
     * certificate matches the "subject" value of the next certificate.
     *
     * When certificates are provided that do not belong to the same chain, a CertificateException is thrown.
     *
     * @param certificates an unordered collection of certificates (cannot be null).
     * @return An ordered list of certificates (possibly empty, but never null).
     * @deprecated Moved to CertificateUtils
     */
    @Deprecated
    public static List<X509Certificate> order( Collection<X509Certificate> certificates ) throws CertificateException
    {
        return CertificateUtils.order( certificates );
    }

    /**
     * Validates chain in certification reply, and returns the ordered
     * elements of the chain (with user certificate first, and root
     * certificate last in the array).
     *
     * @param alias the alias name
     * @param userCert the user certificate of the alias
     * @param certs the chain provided in the reply
     */
    private static List<X509Certificate> validateReply(KeyStore keyStore, KeyStore trustStore, String alias,
                                                             X509Certificate userCert, Collection<X509Certificate> certs)
            throws Exception {
        List<X509Certificate> replyCerts = new ArrayList<>(certs);
        // order the certs in the reply (bottom-up).
        int i;
        X509Certificate tmpCert;
        if (userCert != null) {
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

            tmpCert = replyCerts.get(0);
            replyCerts.set(0, replyCerts.get(i));
            replyCerts.set(i, tmpCert);
        }

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

        // do we trust the (root) cert at the top?
        X509Certificate topCert = replyCerts.get(replyCerts.size() - 1);
        boolean foundInKeyStore = keyStore.getCertificateAlias(topCert) != null;
        boolean foundInCAStore =  trustStore.getCertificateAlias(topCert) != null;
        if (!foundInKeyStore && !foundInCAStore) {
            boolean verified = false;
            X509Certificate rootCert = null;
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
     * @param days       time to live
     * @param issuerCommonName     Issuer CN string
     * @param subjectCommonName    Subject CN string
     * @param domain       Domain of the server.
     * @param signAlgoritm Signature algorithm. This can be either a name or an OID.
     * @return X509 V3 Certificate
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static synchronized X509Certificate createX509V3Certificate(KeyPair kp, int days, String issuerCommonName,
                                                                        String subjectCommonName, String domain,
                                                                        String signAlgoritm)
            throws GeneralSecurityException, IOException {

        // subjectDN
        X500NameBuilder subjectBuilder = new X500NameBuilder();
        subjectBuilder.addRDN(BCStyle.CN, subjectCommonName);

        // issuerDN
        X500NameBuilder issuerBuilder = new X500NameBuilder();
        issuerBuilder.addRDN(BCStyle.CN, issuerCommonName);

        return createX509V3Certificate(kp, days, issuerBuilder, subjectBuilder, domain, signAlgoritm);
    }

    /**
     * Creates an X509 version3 certificate.
     *
     * @param kp           KeyPair that keeps the public and private keys for the new certificate.
     * @param days       time to live
     * @param issuerBuilder     IssuerDN builder
     * @param subjectBuilder    SubjectDN builder
     * @param domain       Domain of the server.
     * @param signAlgoritm Signature algorithm. This can be either a name or an OID.
     * @return X509 V3 Certificate
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static synchronized X509Certificate createX509V3Certificate(KeyPair kp, int days, X500NameBuilder issuerBuilder,
            X500NameBuilder subjectBuilder, String domain, String signAlgoritm) throws GeneralSecurityException, IOException {
        PublicKey pubKey = kp.getPublic();
        PrivateKey privKey = kp.getPrivate();

        byte[] serno = new byte[8];
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed((new Date().getTime()));
        random.nextBytes(serno);
        BigInteger serial = (new java.math.BigInteger(serno)).abs();

        X500Name issuerDN = issuerBuilder.build();
        X500Name subjectDN = subjectBuilder.build();

        // builder
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder( //
                issuerDN, //
                serial, //
                new Date(), //
                new Date(System.currentTimeMillis() + days * (1000L * 60 * 60 * 24)), //
                subjectDN, //
                pubKey //
                );

        // add subjectAlternativeName extension
        boolean critical = subjectDN.getRDNs().length == 0;
        ASN1Sequence othernameSequence = new DERSequence(new ASN1Encodable[]{
                new ASN1ObjectIdentifier("1.3.6.1.5.5.7.8.5"), new DERUTF8String( domain )});
        GeneralName othernameGN = new GeneralName(GeneralName.otherName, othernameSequence);
        GeneralNames subjectAltNames = new GeneralNames(new GeneralName[]{othernameGN});
        certBuilder.addExtension(Extension.subjectAlternativeName, critical, subjectAltNames);

        // add keyIdentifiers extensions
        JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, utils.createSubjectKeyIdentifier(pubKey));
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, utils.createAuthorityKeyIdentifier(pubKey));

        try {
            // build the certificate
            ContentSigner signer = new JcaContentSignerBuilder(signAlgoritm).build(privKey);
            X509CertificateHolder cert = certBuilder.build(signer);

            // verify the validity
            if (!cert.isValidOn(new Date())) {
                throw new GeneralSecurityException("Certificate validity not valid");
            }

            // verify the signature (self-signed)
            ContentVerifierProvider verifierProvider = new JcaContentVerifierProviderBuilder().build(pubKey);
            if (!cert.isSignatureValid(verifierProvider)) {
                throw new GeneralSecurityException("Certificate signature not valid");
            }

            return new JcaX509CertificateConverter().getCertificate(cert);

        } catch (OperatorCreationException | CertException e) {
            throw new GeneralSecurityException(e);
        }
    }
}
