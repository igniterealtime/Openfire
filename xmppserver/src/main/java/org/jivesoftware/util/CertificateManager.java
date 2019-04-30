/*
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

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.CertException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.*;
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.disco.DiscoItem;
import org.jivesoftware.openfire.keystore.CertificateStore;
import org.jivesoftware.openfire.keystore.CertificateUtils;
import org.jivesoftware.util.cert.CNCertificateIdentityMapping;
import org.jivesoftware.util.cert.CertificateIdentityMapping;
import org.jivesoftware.util.cert.SANCertificateIdentityMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;
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

    private static final Logger Log = LoggerFactory.getLogger( CertificateManager.class );

    private static Pattern valuesPattern = Pattern.compile("(?i)(=)([^,]*)");

    private static List<CertificateEventListener> listeners = new CopyOnWriteArrayList<>();

    private static List<CertificateIdentityMapping> serverCertMapping = new ArrayList<>();
    
    private static List<CertificateIdentityMapping> clientCertMapping = new ArrayList<>();
    
    static {

        if ( Security.getProvider( BouncyCastleProvider.PROVIDER_NAME ) == null )
        {
            java.security.Security.addProvider( new BouncyCastleProvider() );
        }

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
     * Returns true if the specified certificate is a self-signed certificate.
     * @param certificate  the certificate to check
     * @return true if the specified certificate is a self-signed certificate.
     */
    public static boolean isSelfSignedCertificate(X509Certificate certificate) {
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
     * @param certificate the certificate to check
     * @return true if the specified certificate is ready to be signed by a Certificate Authority.
     */
    public static boolean isSigningRequestPending(X509Certificate certificate) {
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
     * certificate is going to be returned.
     *
     * @param cert the certificate to create a signing request.
     * @param privKey the private key of the certificate.
     * @return the content of a new singing request for the specified certificate.
     * @throws OperatorCreationException if there was a problem creating the CSR
     * @throws IOException if there was a problem creating the CSR
     * @throws CertificateParsingException if there was a problem creating the CSR
     */
    public static String createSigningRequest(X509Certificate cert, PrivateKey privKey) throws OperatorCreationException, IOException, CertificateParsingException
    {
        JcaPKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder( //
                cert.getSubjectX500Principal(), //
                cert.getPublicKey() //
                );

        // Add SubjectAlternativeNames (SANs)
        final ASN1EncodableVector subjectAlternativeNames = new ASN1EncodableVector();

        final Collection<List<?>> certSans = cert.getSubjectAlternativeNames();
        if ( certSans != null )
        {
            for ( final List<?> certSan : certSans )
            {
                final int nameType = (Integer) certSan.get( 0 );
                final Object value = certSan.get( 1 ); // this is either a string, or a byte-array that represents the ASN.1 DER encoded form.
                switch ( nameType )
                {
                    case 0:
                        // OtherName: search for "id-on-xmppAddr" or 'sRVName' or 'userPrincipalName'
                        try ( final ASN1InputStream decoder = new ASN1InputStream( (byte[]) value ) )
                        {
                            // By specification, OtherName instances must always be an ASN.1 Sequence.
                            final ASN1Primitive object = decoder.readObject();
                            final ASN1Sequence otherNameSeq = (ASN1Sequence) object;

                            // By specification, an OtherName instance consists of:
                            // - the type-id (which is an Object Identifier), followed by:
                            // - a tagged value, of which the tag number is 0 (zero) and the value is defined by the type-id.
                            final ASN1ObjectIdentifier typeId = (ASN1ObjectIdentifier) otherNameSeq.getObjectAt( 0 );
                            final ASN1TaggedObject taggedValue = (ASN1TaggedObject) otherNameSeq.getObjectAt( 1 );

                            final int tagNo = taggedValue.getTagNo();
                            if ( tagNo != 0 )
                            {
                                throw new IllegalArgumentException( "subjectAltName 'otherName' sequence's second object is expected to be a tagged value of which the tag number is 0. The tag number that was detected: " + tagNo );
                            }
                            subjectAlternativeNames.add(
                                new DERTaggedObject( false,
                                                     GeneralName.otherName,
                                                     new DERSequence(
                                                         new ASN1Encodable[] {
                                                             typeId,
                                                             taggedValue
                                                         }
                                                     )
                                )
                            );
                        }
                        catch ( Exception e )
                        {
                            Log.warn( "Unable to parse certificate SAN 'otherName' value", e );
                        }
                        break;
                    case 2:
                        // DNS
                        subjectAlternativeNames.add( new GeneralName( GeneralName.dNSName, (String) value ) );
                        break;
                    case 6:
                        // URI
                        subjectAlternativeNames.add( new GeneralName( GeneralName.uniformResourceIdentifier, (String) value ) );
                        break;
                    default:
                        // Not applicable to XMPP, so silently ignore them
                        break;
                }

            }
        }

        final GeneralNames subjectAltNames = GeneralNames.getInstance(
            new DERSequence( subjectAlternativeNames )
        );

        final ExtensionsGenerator extGen = new ExtensionsGenerator();
        extGen.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
        csrBuilder.addAttribute( PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());

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
     * Generates a PEM representation of the input argument.
     *
     * @param object the input argument (cannot be null).
     * @return PEM representation of the input argument.
     * @throws IOException When a PEM representation of the input could not be created.
     */
    public static String toPemRepresentation( Object object ) throws IOException
    {
        final StringWriter result = new StringWriter();
        try ( final PemWriter pemWriter = new PemWriter(result) )
        {
            final PemObjectGenerator objGen = new JcaMiscPEMGenerator ( object );
            pemWriter.writeObject( objGen );
        }
        return result.toString();
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
     * @throws IOException if there was a problem parsing the key
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
     * @throws IOException never
     * @throws CertificateException if there was a problem parsing certificates
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

    /**
     * Notify listeners that a certificate store has been changed.
     * @param store the store that has changed
     */
    public static void fireCertificateStoreChanged( CertificateStore store )
    {
        for ( CertificateEventListener listener : listeners )
        {
            try
            {
                listener.storeContentChanged( store );
            }
            catch ( Exception e )
            {
                Log.error( "A listener threw an exception while processing a 'store changed' event.", e );
            }
        }
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
     * @throws CertificateException if there was a problem accessing the certificates
     */
    @Deprecated
    public static List<X509Certificate> order( Collection<X509Certificate> certificates ) throws CertificateException
    {
        return CertificateUtils.order( certificates );
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
     * @throws GeneralSecurityException if there was a problem creating the certificate
     * @throws IOException if there was a problem creating the certificate
     */
    public static synchronized X509Certificate createX509V3Certificate(KeyPair kp, int days, String issuerCommonName,
                                                                        String subjectCommonName, String domain,
                                                                        String signAlgoritm)
            throws GeneralSecurityException, IOException {
        return createX509V3Certificate( kp, days, issuerCommonName, subjectCommonName, domain, signAlgoritm, null );
    }

    public static synchronized X509Certificate createX509V3Certificate(KeyPair kp, int days, String issuerCommonName,
                                                                        String subjectCommonName, String domain,
                                                                        String signAlgoritm, Set<String> sanDnsNames)
            throws GeneralSecurityException, IOException {

        // subjectDN
        X500NameBuilder subjectBuilder = new X500NameBuilder();
        subjectBuilder.addRDN(BCStyle.CN, subjectCommonName);

        // issuerDN
        X500NameBuilder issuerBuilder = new X500NameBuilder();
        issuerBuilder.addRDN(BCStyle.CN, issuerCommonName);

        return createX509V3Certificate(kp, days, issuerBuilder, subjectBuilder, domain, signAlgoritm, sanDnsNames);
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
     * @throws GeneralSecurityException if there was a problem creating the certificate
     * @throws IOException if there was a problem creating the certificate
     */
    public static synchronized X509Certificate createX509V3Certificate(KeyPair kp, int days, X500NameBuilder issuerBuilder,
            X500NameBuilder subjectBuilder, String domain, String signAlgoritm ) throws GeneralSecurityException, IOException
    {
        return createX509V3Certificate( kp, days, issuerBuilder, subjectBuilder, domain, signAlgoritm, null );
    }

    public static synchronized X509Certificate createX509V3Certificate(KeyPair kp, int days, X500NameBuilder issuerBuilder,
            X500NameBuilder subjectBuilder, String domain, String signAlgoritm, Set<String> sanDnsNames ) throws GeneralSecurityException, IOException {
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

        // add subjectAlternativeName extension that includes all relevant names.
        final GeneralNames subjectAlternativeNames = getSubjectAlternativeNames( sanDnsNames );

        final boolean critical = subjectDN.getRDNs().length == 0;
        certBuilder.addExtension(Extension.subjectAlternativeName, critical, subjectAlternativeNames);

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

    protected static GeneralNames getSubjectAlternativeNames( Set<String> sanDnsNames )
    {
        final ASN1EncodableVector subjectAlternativeNames = new ASN1EncodableVector();
        if ( sanDnsNames != null )
        {
            for ( final String dnsNameValue : sanDnsNames )
            {
                subjectAlternativeNames.add(
                    new GeneralName( GeneralName.dNSName, dnsNameValue )
                );
            }
        }

        return GeneralNames.getInstance(
            new DERSequence( subjectAlternativeNames )
        );
    }

    /**
     * Finds all values that aught to be added as a Subject Alternate Name of the dnsName type to a certificate that
     * identifies this XMPP server.
     *
     * @return A set of names, possibly empty, never null.
     */
    public static Set<String> determineSubjectAlternateNameDnsNameValues()
    {
        final HashSet<String> result = new HashSet<>();

        // Add the XMPP domain name itself.
        result.add( XMPPServer.getInstance().getServerInfo().getXMPPDomain() );

        // The fully qualified domain name of the server
        result.add( XMPPServer.getInstance().getServerInfo().getHostname() );

        if ( XMPPServer.getInstance().getIQDiscoItemsHandler() != null ) // When we're not in setup any longer...
        {
            // Add the name of each of the domain level item nodes as reported by service discovery.
            for ( final DiscoItem item : XMPPServer.getInstance().getIQDiscoItemsHandler().getServerItems() )
            {
                result.add( item.getJID().toBareJID() );
            }
        }

        return result;
    }
}
