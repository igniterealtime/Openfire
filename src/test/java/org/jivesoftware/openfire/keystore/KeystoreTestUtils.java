package org.jivesoftware.openfire.keystore;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jivesoftware.util.Base64;

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
        final StringBuilder sb = new StringBuilder();
        sb.append( BEGIN_CERT ).append( '\n' );
        sb.append( Base64.encodeBytes( certificate.getEncoded() ) ).append( '\n' );
        sb.append( END_CERT).append( '\n' );
        return sb.toString();
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
    public static X509Certificate[] generateValidCertificateChain() throws Exception
    {
        int length = 4;
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance( "RSA" );
        keyPairGenerator.initialize( 512 );

        // Root certificate (representing the CA) is self-signed.
        KeyPair subjectKeyPair = keyPairGenerator.generateKeyPair();
        KeyPair issuerKeyPair = subjectKeyPair;

        final X509Certificate[] result = new X509Certificate[ length ];
        for ( int i = length - 1 ; i >= 0; i-- )
        {
            result[ i ] = generateTestCertificate( true, issuerKeyPair, subjectKeyPair, i );

            // Further away from the root CA, each certificate is issued by the previous subject.
            issuerKeyPair = subjectKeyPair;
            subjectKeyPair = keyPairGenerator.generateKeyPair();
        }

        return result;
    }

    /**
     * Generates a chain of certificates, identical to {@link #generateValidCertificateChain()}, with one exception:
     * the second certificate (the first intermediate) is expired.
     *
     * @return an array of certificates. Never null, never an empty array.
     */
    public static X509Certificate[] generateCertificateChainWithExpiredIntermediateCert() throws Exception
    {
        int length = 4;
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance( "RSA" );
        keyPairGenerator.initialize( 512 );

        // Root certificate (representing the CA) is self-signed.
        KeyPair subjectKeyPair = keyPairGenerator.generateKeyPair();
        KeyPair issuerKeyPair = subjectKeyPair;

        final X509Certificate[] result = new X509Certificate[ length ];
        for ( int i = length - 1 ; i >= 0; i-- )
        {
            boolean isValid = ( i != 1 ); // second certificate needs to be expired!
            result[ i ] = generateTestCertificate( isValid, issuerKeyPair, subjectKeyPair, i );

            // Further away from the root CA, each certificate is issued by the previous subject.
            issuerKeyPair = subjectKeyPair;
            subjectKeyPair = keyPairGenerator.generateKeyPair();
        }

        return result;
    }

    /**
     * Generates a chain of certificates, identical to {@link #generateValidCertificateChain()}, with one exception:
     * the last certificate (the root CA) is expired.
     *
     * @return an array of certificates. Never null, never an empty array.
     */
    public static X509Certificate[] generateCertificateChainWithExpiredRootCert() throws Exception
    {
        int length = 4;
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance( "RSA" );
        keyPairGenerator.initialize( 512 );

        // Root certificate (representing the CA) is self-signed.
        KeyPair subjectKeyPair = keyPairGenerator.generateKeyPair();
        KeyPair issuerKeyPair = subjectKeyPair;

        final X509Certificate[] result = new X509Certificate[ length ];
        for ( int i = length - 1 ; i >= 0; i-- )
        {
            boolean isValid = ( i != length - 1 ); // root certificate needs to be expired!
            result[ i ] = generateTestCertificate( isValid, issuerKeyPair, subjectKeyPair, i );

            // Further away from the root CA, each certificate is issued by the previous subject.
            issuerKeyPair = subjectKeyPair;
            subjectKeyPair = keyPairGenerator.generateKeyPair();
        }

        return result;
    }

    private static X509Certificate generateTestCertificate( final boolean isValid, final KeyPair issuerKeyPair, final KeyPair subjectKeyPair, int indexAwayFromEndEntity) throws Exception
    {
        // Issuer and Subject.
        final X500Name subject = new X500Name( "CN=" + Base64.encodeBytes( subjectKeyPair.getPublic().getEncoded(), Base64.URL_SAFE ) );
        final X500Name issuer  = new X500Name( "CN=" + Base64.encodeBytes( issuerKeyPair.getPublic().getEncoded(), Base64.URL_SAFE ) );

        // Validity
        final Date notBefore;
        final Date notAfter;
        if ( isValid )
        {
            notBefore = new Date( System.currentTimeMillis() - ( 1000L * 60 * 60 * 24 * 30 ) ); // 30 days ago
            notAfter  = new Date( System.currentTimeMillis() + ( 1000L * 60 * 60 * 24 * 99 ) ); // 99 days from now.
        }
        else
        {
            // Generate a certificate for which the validate period has expired.
            notBefore = new Date( System.currentTimeMillis() - ( 1000L * 60 * 60 * 24 * 40 ) ); // 40 days ago
            notAfter  = new Date( System.currentTimeMillis() - ( 1000L * 60 * 60 * 24 * 10 ) ); // 10 days ago
        }

        // The new certificate should get a unique serial number.
        final BigInteger serial = BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) );

        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                subject,
                subjectKeyPair.getPublic()
        );

        // When this certificate is used to sign another certificate, basic constraints need to be set.
        if ( indexAwayFromEndEntity > 0 )
        {
            builder.addExtension( Extension.basicConstraints, true, new BasicConstraints( indexAwayFromEndEntity - 1 ) );
        }

        final ContentSigner contentSigner = new JcaContentSignerBuilder( "SHA1withRSA" ).build( issuerKeyPair.getPrivate() );
        final X509CertificateHolder certificateHolder = builder.build( contentSigner );


        return new JcaX509CertificateConverter().setProvider( "BC" ).getCertificate( certificateHolder );
    }

    /**
     * This method will validate a chain of certificates. It is provided as an alternative to the certificate chain
     * validation mechanisms that are under test. This method is intended to be used as a comparative benchmark against
     * other validation methods.
     *
     * The first certificate in the chain is expected to be the end-entity certificate.
     *
     * The last certificate in the chain is expected to be the root CA certificate.
     *
     * @param chain A certificate chain (cannot be null or empty).
     * @return CertPathBuilderResult result of validation.
     * @throws Exception When the chain is not valid.
     */
    public CertPathBuilderResult testChain( X509Certificate[] chain ) throws Exception
    {
        // Create the selector that specifies the starting certificate
        X509CertSelector selector = new X509CertSelector();
        selector.setCertificate( chain[0] );

        // Create the trust anchors (set of root CA certificates)
        Set<TrustAnchor> trustAnchors = new HashSet<TrustAnchor>();
        trustAnchors.add(new TrustAnchor(chain[ chain.length - 1], null));

        // Configure the PKIX certificate builder algorithm parameters
        PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(
                trustAnchors, selector);

        // Disable CRL checks (this is done manually as additional step)
        pkixParams.setRevocationEnabled(false);

        // Specify a list of intermediate certificates
        Set<java.security.cert.Certificate> intermediateCerts = new HashSet<>();
        for (int i=1; i<chain.length -1; i++)
        {
            intermediateCerts.add( chain[ i ] );
        }

        CertStore intermediateCertStore = CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(intermediateCerts));
        pkixParams.addCertStore(intermediateCertStore);

        // Build and verify the certification chain
        CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
        PKIXCertPathBuilderResult result = (PKIXCertPathBuilderResult) builder
                .build(pkixParams);

        return result;
    }

    /**
     * Instantiates a new certificate of which the notAfter value is a point in time that is in the past (as compared
     * to the point in time of the invocation of this method).
     *
     * @return A certificate that is invalid (never null).
     */
    public static X509Certificate generateExpiredCertificate() throws Exception
    {
        return generateTestCertificate( false, false, 0 );
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
    public static X509Certificate generateValidCertificate() throws Exception
    {
        return generateTestCertificate( true, false, 0 );
    }

    /**
     * Instantiates a new certificate that is self-signed, meaning that the issuer and subject values are identical. The
     * returned certificate is valid in the same manner as described in the documentation of
     * {@link #generateSelfSignedCertificate()}.
     *
     * @return A certificate that is self-signed (never null).
     * @see #generateValidCertificate()
     */
    public static X509Certificate generateSelfSignedCertificate() throws Exception
    {
        return generateTestCertificate( true, true, 0 );
    }

    /**
     * Instantiates a new certificate that is self-signed, of which the notAfter value is a point in time that is in the
     * past (as compared to the point in time of the invocation of this method).
     *
     * @return A certificate that is self-signed and expired (never null).
     * @see #generateSelfSignedCertificate()
     * @see #generateExpiredCertificate()
     */
    public static X509Certificate generateExpiredSelfSignedCertificate() throws Exception
    {
        return generateTestCertificate( false, true, 0 );
    }

    private static X509Certificate generateTestCertificate( final boolean isValid, final boolean isSelfSigned, int indexAwayFromEndEntity ) throws Exception
    {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance( "RSA" );
        keyPairGenerator.initialize( 512 );

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

        return generateTestCertificate( isValid, issuerKeyPair, subjectKeyPair, indexAwayFromEndEntity );
    }
}
