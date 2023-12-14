/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.keystore;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.jivesoftware.util.Base64;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.zip.Adler32;

/**
 * Utility functions that are intended to be used by unit tests.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class KeystoreTestUtils
{
    private static final Provider PROVIDER = new BouncyCastleProvider();
    private static final Object BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    private static final Object END_CERT = "-----END CERTIFICATE-----";

    public static final int CHAIN_LENGTH = 4;
    public static final int KEY_SIZE = 2048;
    public static final String KEY_ALGORITHM = "RSA";
    public static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    static
    {
        // Add the BC provider to the list of security providers
        Security.addProvider( PROVIDER );
    }

    /**
     * Returns the Privacy Enhanced Mail (PEM) format of a X509 certificate.
     *
     * @param certificate An X509 certificate (cannot be null).
     * @return a PEM representation of the certificate (never null, never an empty string).
     */
    public static String toPemFormat( X509Certificate certificate ) throws Exception {
        return String.valueOf(BEGIN_CERT) + '\n' +
            Base64.encodeBytes(certificate.getEncoded()) + '\n' +
            END_CERT + '\n';
    }

    /**
     * Generates a chain of certificates, where the first certificate represents the end-entity certificate and the last
     * certificate represents the trust anchor (the 'root certificate').
     *
     * Exactly four certificates are returned:
     * <ol>
     *     <li>The end-entity certificate</li>
     *     <li>an intermediate CA certificate</li>
     *     <li>a different intermediate CA certificate</li>
     *     <li>a root CA certificate</li>
     * </ol>
     *
     * Each certificate is issued by the certificate that's in the next position of the chain. The last certificate is
     * self-signed.
     *
     * @return an array of certificates. Never null, never an empty array.
     */
    public static ResultHolder generateValidCertificateChain() throws Exception
    {
        return generateValidCertificateChain(null);
    }

    /**
     * Generates a chain of certificates, where the first certificate represents the end-entity certificate and the last
     * certificate represents the trust anchor (the 'root certificate').
     *
     * Exactly four certificates are returned:
     * <ol>
     *     <li>The end-entity certificate</li>
     *     <li>an intermediate CA certificate</li>
     *     <li>a different intermediate CA certificate</li>
     *     <li>a root CA certificate</li>
     * </ol>
     *
     * Each certificate is issued by the certificate that's in the next position of the chain. The last certificate is
     * self-signed.
     *
     * @param subjectCommonName The CN value to use in the end-entity certificate.
     * @return an array of certificates. Never null, never an empty array.
     */
    public static ResultHolder generateValidCertificateChain(@Nullable final String subjectCommonName) throws Exception
    {
        return generateCertificateChainWithExpiredCertOnPosition(subjectCommonName, -1);
    }

    /**
     * Generates a chain of certificates, identical to {@link #generateValidCertificateChain()}, with one exception:
     * the first certificate (the end-entity) is expired.
     *
     * @return an array of certificates. Never null, never an empty array.
     */
    public static ResultHolder generateCertificateChainWithExpiredEndEntityCert() throws Exception
    {
        return generateCertificateChainWithExpiredEndEntityCert(null);
    }

    /**
     * Generates a chain of certificates, identical to {@link #generateValidCertificateChain()}, with one exception:
     * the first certificate (the end-entity) is expired.
     *
     * @param subjectCommonName The CN value to use in the end-entity certificate.
     * @return an array of certificates. Never null, never an empty array.
     */
    public static ResultHolder generateCertificateChainWithExpiredEndEntityCert(@Nullable final String subjectCommonName) throws Exception
    {
        return generateCertificateChainWithExpiredCertOnPosition(subjectCommonName, 0);
    }

    /**
     * Generates a chain of certificates, identical to {@link #generateValidCertificateChain()}, with one exception:
     * the second certificate (the first intermediate) is expired.
     *
     * @return an array of certificates. Never null, never an empty array.
     */
    public static ResultHolder generateCertificateChainWithExpiredIntermediateCert() throws Exception
    {
        return generateCertificateChainWithExpiredIntermediateCert(null);
    }

    /**
     * Generates a chain of certificates, identical to {@link #generateValidCertificateChain()}, with one exception:
     * the second certificate (the first intermediate) is expired.
     *
     * @param subjectCommonName The CN value to use in the end-entity certificate.
     * @return an array of certificates. Never null, never an empty array.
     */
    public static ResultHolder generateCertificateChainWithExpiredIntermediateCert(@Nullable final String subjectCommonName) throws Exception
    {
        return generateCertificateChainWithExpiredCertOnPosition(subjectCommonName, 1);
    }

    /**
     * Generates a chain of certificates, identical to {@link #generateValidCertificateChain()}, with one exception:
     * the last certificate (the root CA) is expired.
     *
     * @return an array of certificates. Never null, never an empty array.
     */
    public static ResultHolder generateCertificateChainWithExpiredRootCert() throws Exception {
        return generateCertificateChainWithExpiredRootCert(null);
    }

    /**
     * Generates a chain of certificates, identical to {@link #generateValidCertificateChain()}, with one exception:
     * the last certificate (the root CA) is expired.
     *
     * @param subjectCommonName The CN value to use in the end-entity certificate.
     * @return an array of certificates. Never null, never an empty array.
     */
    public static ResultHolder generateCertificateChainWithExpiredRootCert(@Nullable final String subjectCommonName) throws Exception
    {
        return generateCertificateChainWithExpiredCertOnPosition(subjectCommonName, CHAIN_LENGTH - 1);
    }

    /**
     * Generates a chain of certificates, identical to {@link #generateValidCertificateChain()}, with one exception:
     * one certificate in the chain (identified by its number in the chain, provided by the second argument) is expired.
     *
     * @param subjectCommonName The CN value to use in the end-entity certificate.
     * @param certificateInChainThatIsExpired certificate position in chain that is expired (zero-based).
     * @return an array of certificates. Never null, never an empty array.
     */
    private static ResultHolder generateCertificateChainWithExpiredCertOnPosition(@Nullable final String subjectCommonName, final int certificateInChainThatIsExpired) throws Exception
    {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance( KEY_ALGORITHM );
        keyPairGenerator.initialize( KEY_SIZE );

        // Root certificate (representing the CA) is self-signed.
        KeyPair subjectKeyPair = keyPairGenerator.generateKeyPair();
        KeyPair issuerKeyPair = subjectKeyPair;

        final X509Certificate[] result = new X509Certificate[ CHAIN_LENGTH ];
        for ( int i = CHAIN_LENGTH - 1 ; i >= 0; i-- )
        {
            boolean isValid = ( i != certificateInChainThatIsExpired ); // one certificate needs to be expired!
            result[ i ] = generateTestCertificate( i == 0 ? subjectCommonName : null, isValid, issuerKeyPair, subjectKeyPair, i );

            // Further away from the root CA, each certificate is issued by the previous subject.
            issuerKeyPair = subjectKeyPair;
            subjectKeyPair = keyPairGenerator.generateKeyPair();
        }

        // Note that the issuerKeyPair now holds the subjectKeyPair that was used last! SubjectKeyPair now holds an unused value.
        return new ResultHolder(issuerKeyPair, result);
    }

    private static X509Certificate generateTestCertificate(@Nullable final String subjectCommonName, final boolean isValid, final KeyPair issuerKeyPair, final KeyPair subjectKeyPair, int indexAwayFromEndEntity) throws Exception
    {
        // Issuer and Subject.
        final String subjectName = (subjectCommonName != null ? subjectCommonName : asSemiUniqueName(subjectKeyPair.getPublic()));
        final String issuerName = issuerKeyPair == subjectKeyPair ? subjectName : asSemiUniqueName(issuerKeyPair.getPublic());
        final X500NameBuilder subjectBuilder = new X500NameBuilder();
        subjectBuilder.addRDN(BCStyle.CN, subjectName);
        final X500Name subject = subjectBuilder.build();
        final X500NameBuilder issuerBuilder = new X500NameBuilder();
        issuerBuilder.addRDN(BCStyle.CN, issuerName);
        final X500Name issuer = issuerBuilder.build();

        // Validity
        final Instant notBefore;
        final Instant notAfter;
        if ( isValid )
        {
            notBefore = Instant.now().minus(30, ChronoUnit.DAYS); // 30 days ago
            notAfter  = Instant.now().plus(99, ChronoUnit.DAYS); // 99 days from now.
        }
        else
        {
            // Generate a certificate for which the validate period has expired.
            notBefore = Instant.now().minus(40, ChronoUnit.DAYS); // 40 days ago
            notAfter  = Instant.now().minus(10, ChronoUnit.DAYS); // 10 days ago
        }

        // The new certificate should get a unique serial number.
        final BigInteger serial = BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) );

        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                Date.from(notBefore),
                Date.from(notAfter),
                subject,
                subjectKeyPair.getPublic()
        );

        // When this certificate is used to sign another certificate, basic constraints need to be set.
        if ( indexAwayFromEndEntity > 0 )
        {
            builder.addExtension( Extension.basicConstraints, true, new BasicConstraints( indexAwayFromEndEntity - 1 ) );
        }

        // add subjectAlternativeName extension that includes all relevant names.
        builder.addExtension(Extension.subjectAlternativeName, false,
            new GeneralNames(new GeneralName(GeneralName.dNSName, subjectName)));

        // Add keyIdentifiers extensions
        final JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false, utils.createSubjectKeyIdentifier(subjectKeyPair.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false, utils.createAuthorityKeyIdentifier(issuerKeyPair.getPublic()));

        // Build the certificate.
        final ContentSigner contentSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build( issuerKeyPair.getPrivate() );
        final X509CertificateHolder certificateHolder = builder.build( contentSigner );

        // Verify the signature.
        final ContentVerifierProvider verifierProvider = new JcaContentVerifierProviderBuilder().build(issuerKeyPair.getPublic());
        if (!certificateHolder.isSignatureValid(verifierProvider)) {
            throw new GeneralSecurityException("Certificate signature not valid");
        }

        return new JcaX509CertificateConverter().setProvider( "BC" ).getCertificate( certificateHolder );
    }

    /**
     * Instantiates a new certificate of which the notAfter value is a point in time that is in the past (as compared
     * to the point in time of the invocation of this method).
     *
     * @return A certificate that is invalid (never null).
     */
    public static ResultHolder generateExpiredCertificate() throws Exception
    {
        return generateTestCertificate( null, false, false, 0 );
    }

    /**
     * Instantiates a new certificate of which the notAfter value is a point in time that is in the past (as compared
     * to the point in time of the invocation of this method).
     *
     * @param subjectCommonName The CN value to use in the certificate.
     * @return A certificate that is invalid (never null).
     */
    public static ResultHolder generateExpiredCertificate(@Nullable final String subjectCommonName)  throws Exception
    {
        return generateTestCertificate( subjectCommonName, false, false, 0 );
    }

    /**
     * Instantiates a new certificate of which the notBefore value is a point in the past, and the notAfter value is a
     * point in the future (as compared to the point in time of the invocation of this method).
     *
     * The notAfter value can be expected to be a value that is far enough in the future for unit testing purposes, but
     * should not be assumed to be a value that is in the distant future. It is safe to assume that the generated
     * certificate will remain to be valid for the duration of a generic unit test (which is measured in seconds or
     * fractions thereof).
     *
     * @return A certificate that is valid (never null).
     */
    public static ResultHolder generateValidCertificate() throws Exception
    {
        return generateTestCertificate( null, true, false, 0 );
    }

    /**
     * Instantiates a new certificate of which the notBefore value is a point in the past, and the notAfter value is a
     * point in the future (as compared to the point in time of the invocation of this method).
     *
     * The notAfter value can be expected to be a value that is far enough in the future for unit testing purposes, but
     * should not be assumed to be a value that is in the distant future. It is safe to assume that the generated
     * certificate will remain to be valid for the duration of a generic unit test (which is measured in seconds or
     * fractions thereof).
     *
     * @param subjectCommonName The CN value to use in the certificate.
     * @return A certificate that is valid (never null).
     */
    public static ResultHolder generateValidCertificate(@Nullable final String subjectCommonName ) throws Exception
    {
        return generateTestCertificate( subjectCommonName, true, false, 0 );
    }

    /**
     * Instantiates a new certificate that is self-signed, meaning that the issuer and subject values are identical. The
     * returned certificate is valid in the same manner as described in the documentation of
     * {@link #generateValidCertificate()}.
     *
     * @return A certificate that is self-signed (never null).
     * @see #generateValidCertificate()
     */
    public static ResultHolder generateSelfSignedCertificate() throws Exception
    {
        return generateTestCertificate( null, true, true, 0 );
    }

    /**
     * Instantiates a new certificate that is self-signed, meaning that the issuer and subject values are identical. The
     * returned certificate is valid in the same manner as described in the documentation of
     * {@link #generateValidCertificate()}.
     *
     * @param subjectCommonName The CN value to use in the certificate.
     * @return A certificate that is self-signed (never null).
     * @see #generateValidCertificate()
     */
    public static ResultHolder generateSelfSignedCertificate(@Nullable final String subjectCommonName ) throws Exception
    {
        return generateTestCertificate( subjectCommonName, true, true, 0 );
    }

    /**
     * Instantiates a new certificate that is self-signed, of which the notAfter value is a point in time that is in the
     * past (as compared to the point in time of the invocation of this method).
     *
     * @return A certificate that is self-signed and expired (never null).
     * @see #generateSelfSignedCertificate()
     * @see #generateExpiredCertificate()
     */
    public static ResultHolder generateExpiredSelfSignedCertificate() throws Exception
    {
        return generateTestCertificate( null, false, true, 0 );
    }

    /**
     * Instantiates a new certificate that is self-signed, of which the notAfter value is a point in time that is in the
     * past (as compared to the point in time of the invocation of this method).
     *
     * @param subjectCommonName The CN value to use in the certificate.
     * @return A certificate that is self-signed and expired (never null).
     * @see #generateSelfSignedCertificate()
     * @see #generateExpiredCertificate()
     */
    public static ResultHolder generateExpiredSelfSignedCertificate(@Nullable final String subjectCommonName ) throws Exception
    {
        return generateTestCertificate( subjectCommonName, false, true, 0 );
    }

    private static ResultHolder generateTestCertificate(@Nullable final String subjectCommonName, final boolean isValid, final boolean isSelfSigned, int indexAwayFromEndEntity ) throws Exception
    {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance( KEY_ALGORITHM );
        keyPairGenerator.initialize( KEY_SIZE );

        final KeyPair subjectKeyPair;
        final KeyPair issuerKeyPair;

        if ( isSelfSigned )
        {
            // Self signed: subject and issuer are the same entity.
            subjectKeyPair = keyPairGenerator.generateKeyPair();
            issuerKeyPair = subjectKeyPair;
        }
        else
        {
            subjectKeyPair = keyPairGenerator.generateKeyPair();
            issuerKeyPair = keyPairGenerator.generateKeyPair();
        }

        final X509Certificate subjectCertificate = generateTestCertificate(subjectCommonName, isValid, issuerKeyPair, subjectKeyPair, indexAwayFromEndEntity);
        return new ResultHolder(subjectKeyPair, subjectCertificate);
    }

    /**
     * Generate a short semi-unique string to identify a public key.
     *
     * This method uses a very short checksum algorithm. The benefit of this is that the generated values are
     * human-readable. The downside is that the generated values are not truly unique to the public key. As this method
     * is part of unit testing, that should not be to much of a problem.
     *
     * @param publicKey For which to generate an identifier
     * @return an identifier
     */
    public static String asSemiUniqueName(final PublicKey publicKey) {
        final byte[] bytes = publicKey.getEncoded();
        final Adler32 checksum = new Adler32();
        checksum.update(bytes,0,bytes.length);
        return Long.toHexString(checksum.getValue());
    }

    /**
     * A data structure that holds a generated key pair and the associated certificate chain.
     */
    public static class ResultHolder
    {
        private final KeyPair keyPair;
        private final X509Certificate[] certificateChain;

        public ResultHolder(final KeyPair keyPair, final X509Certificate certificate)
        {
            this.keyPair = keyPair;
            this.certificateChain = new X509Certificate[] { certificate };
        }

        public ResultHolder(final KeyPair keyPair, final X509Certificate[] certificateChain)
        {
            this.keyPair = keyPair;
            this.certificateChain = certificateChain;
        }

        public KeyPair getKeyPair()
        {
            return keyPair;
        }

        public X509Certificate getCertificate()
        {
            return certificateChain[0];
        }

        public X509Certificate[] getCertificateChain()
        {
            return certificateChain;
        }
    }
}
