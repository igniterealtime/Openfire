package org.jivesoftware.util;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit test to validate the functionality of @{link CertificateManager}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class CertificateManagerTest
{
    public static final ASN1ObjectIdentifier XMPP_ADDR_OID = new ASN1ObjectIdentifier( "1.3.6.1.5.5.7.8.5" );
    public static final ASN1ObjectIdentifier DNS_SRV_OID = new ASN1ObjectIdentifier( "1.3.6.1.5.5.7.8.7" );

    private static KeyPairGenerator keyPairGenerator;
    private static KeyPair subjectKeyPair;
    private static KeyPair issuerKeyPair;
    private static ContentSigner contentSigner;

    @BeforeClass
    public static void initialize() throws Exception
    {
        keyPairGenerator = KeyPairGenerator.getInstance( "RSA" );
        keyPairGenerator.initialize( 512 );

        subjectKeyPair = keyPairGenerator.generateKeyPair();
        issuerKeyPair = keyPairGenerator.generateKeyPair();
        contentSigner = new JcaContentSignerBuilder( "SHA1withRSA" ).build( issuerKeyPair.getPrivate() );
    }

    /**
     * {@link CertificateManager#getServerIdentities(X509Certificate)} should return:
     * <ul>
     *     <li>the Common Name</li>
     * </ul>
     *
     * when a certificate contains:
     * <ul>
     *     <li>no other identifiers than its CommonName</li>
     * </ul>
     */
    @Test
    public void testServerIdentitiesCommonNameOnly() throws Exception
    {
        // Setup fixture.
        final String subjectCommonName = "MySubjectCommonName";

        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name( "CN=MyIssuer" ),                                          // Issuer
                BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ),         // Random serial number
                new Date( System.currentTimeMillis() - ( 1000L * 60 * 60 * 24 * 30 ) ), // Not before 30 days ago
                new Date( System.currentTimeMillis() + ( 1000L * 60 * 60 * 24 * 99 ) ), // Not after 99 days from now
                new X500Name( "CN=" + subjectCommonName ),                              // Subject
                subjectKeyPair.getPublic()
        );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        final X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // Execute system under test
        final List<String> serverIdentities = CertificateManager.getServerIdentities( cert );

        // Verify result
        assertEquals( 1, serverIdentities.size() );
        assertEquals( subjectCommonName, serverIdentities.get( 0 ) );
    }

    /**
     * {@link CertificateManager#getServerIdentities(X509Certificate)} should return:
     * <ul>
     *     <li>the 'xmppAddr' subjectAltName value</li>
     *     <li>explicitly not the Common Name</li>
     * </ul>
     *
     * when a certificate contains:
     * <ul>
     *     <li>a subjectAltName entry of type otherName with an ASN.1 Object Identifier of "id-on-xmppAddr"</li>
     * </ul>
     */
    @Test
    public void testServerIdentitiesXmppAddr() throws Exception
    {
        // Setup fixture.
        final String subjectCommonName = "MySubjectCommonName";
        final String subjectAltNameXmppAddr = "MySubjectAltNameXmppAddr";

        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name( "CN=MyIssuer" ),                                          // Issuer
                BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ),         // Random serial number
                new Date( System.currentTimeMillis() - ( 1000L * 60 * 60 * 24 * 30 ) ), // Not before 30 days ago
                new Date( System.currentTimeMillis() + ( 1000L * 60 * 60 * 24 * 99 ) ), // Not after 99 days from now
                new X500Name( "CN=" + subjectCommonName ),                              // Subject
                subjectKeyPair.getPublic()
        );

        final DERSequence otherName = new DERSequence( new ASN1Encodable[] { XMPP_ADDR_OID, new DERUTF8String( subjectAltNameXmppAddr ) });
        final GeneralNames subjectAltNames = new GeneralNames( new GeneralName(GeneralName.otherName, otherName ) );
        builder.addExtension( Extension.subjectAlternativeName, true, subjectAltNames );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        final X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // Execute system under test
        final List<String> serverIdentities = CertificateManager.getServerIdentities( cert );

        // Verify result
        assertEquals( 1, serverIdentities.size() );
        assertTrue( serverIdentities.contains( subjectAltNameXmppAddr ));
        assertFalse( serverIdentities.contains( subjectCommonName ) );
    }


    /**
     * {@link CertificateManager#getServerIdentities(X509Certificate)} should return:
     * <ul>
     *     <li>the 'DNS SRV' subjectAltName value</li>
     *     <li>explicitly not the Common Name</li>
     * </ul>
     *
     * when a certificate contains:
     * <ul>
     *     <li>a subjectAltName entry of type otherName with an ASN.1 Object Identifier of "id-on-dnsSRV"</li>
     * </ul>
     */
    @Test
    public void testServerIdentitiesDnsSrv() throws Exception
    {
        // Setup fixture.
        final String subjectCommonName = "MySubjectCommonName";
        final String subjectAltNameDnsSrv = "MySubjectAltNameXmppAddr";

        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name( "CN=MyIssuer" ),                                          // Issuer
                BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ),         // Random serial number
                new Date( System.currentTimeMillis() - ( 1000L * 60 * 60 * 24 * 30 ) ), // Not before 30 days ago
                new Date( System.currentTimeMillis() + ( 1000L * 60 * 60 * 24 * 99 ) ), // Not after 99 days from now
                new X500Name( "CN=" + subjectCommonName ),                              // Subject
                subjectKeyPair.getPublic()
        );

        final DERSequence otherName = new DERSequence( new ASN1Encodable[] {DNS_SRV_OID, new DERUTF8String( "_xmpp-server."+subjectAltNameDnsSrv ) });
        final GeneralNames subjectAltNames = new GeneralNames( new GeneralName(GeneralName.otherName, otherName ) );
        builder.addExtension( Extension.subjectAlternativeName, true, subjectAltNames );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        final X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // Execute system under test
        final List<String> serverIdentities = CertificateManager.getServerIdentities( cert );

        // Verify result
        assertEquals( 1, serverIdentities.size() );
        assertTrue( serverIdentities.contains( subjectAltNameDnsSrv ));
        assertFalse( serverIdentities.contains( subjectCommonName ) );
    }

    /**
     * {@link CertificateManager#getServerIdentities(X509Certificate)} should return:
     * <ul>
     *     <li>the DNS subjectAltName value</li>
     *     <li>explicitly not the Common Name</li>
     * </ul>
     *
     * when a certificate contains:
     * <ul>
     *     <li>a subjectAltName entry of type DNS </li>
     * </ul>
     */
    @Test
    public void testServerIdentitiesDNS() throws Exception
    {
        // Setup fixture.
        final String subjectCommonName = "MySubjectCommonName";
        final String subjectAltNameDNS = "MySubjectAltNameDNS";

        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name( "CN=MyIssuer" ),                                          // Issuer
                BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ),         // Random serial number
                new Date( System.currentTimeMillis() - ( 1000L * 60 * 60 * 24 * 30 ) ), // Not before 30 days ago
                new Date( System.currentTimeMillis() + ( 1000L * 60 * 60 * 24 * 99 ) ), // Not after 99 days from now
                new X500Name( "CN=" + subjectCommonName ),                              // Subject
                subjectKeyPair.getPublic()
        );

        final GeneralNames generalNames = new GeneralNames(new GeneralName(GeneralName.dNSName, subjectAltNameDNS));

        builder.addExtension( Extension.subjectAlternativeName, false, generalNames );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        final X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // Execute system under test
        final List<String> serverIdentities = CertificateManager.getServerIdentities( cert );

        // Verify result
        assertEquals( 1, serverIdentities.size() );
        assertTrue( serverIdentities.contains( subjectAltNameDNS ) );
        assertFalse( serverIdentities.contains( subjectCommonName ) );
    }

    /**
     * {@link CertificateManager#getServerIdentities(X509Certificate)} should return:
     * <ul>
     *     <li>the DNS subjectAltName value</li>
     *     <li>the 'xmppAddr' subjectAltName value</li>
     *     <li>explicitly not the Common Name</li>
     * </ul>
     *
     * when a certificate contains:
     * <ul>
     *     <li>a subjectAltName entry of type DNS </li>
     *     <li>a subjectAltName entry of type otherName with an ASN.1 Object Identifier of "id-on-xmppAddr"</li>
     * </ul>
     */
    @Test
    public void testServerIdentitiesXmppAddrAndDNS() throws Exception
    {
        // Setup fixture.
        final String subjectCommonName = "MySubjectCommonName";
        final String subjectAltNameXmppAddr = "MySubjectAltNameXmppAddr";
        final String subjectAltNameDNS = "MySubjectAltNameDNS";

        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name( "CN=MyIssuer" ),                                          // Issuer
                BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ),         // Random serial number
                new Date( System.currentTimeMillis() - ( 1000L * 60 * 60 * 24 * 30 ) ), // Not before 30 days ago
                new Date( System.currentTimeMillis() + ( 1000L * 60 * 60 * 24 * 99 ) ), // Not after 99 days from now
                new X500Name( "CN=" + subjectCommonName ),                              // Subject
                subjectKeyPair.getPublic()
        );

        final DERSequence otherName = new DERSequence( new ASN1Encodable[] { XMPP_ADDR_OID, new DERUTF8String( subjectAltNameXmppAddr ) });
        final GeneralNames subjectAltNames = new GeneralNames( new GeneralName[] {
                new GeneralName( GeneralName.otherName, otherName ),
                new GeneralName( GeneralName.dNSName, subjectAltNameDNS )
        });
        builder.addExtension( Extension.subjectAlternativeName, true, subjectAltNames );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        final X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // Execute system under test
        final List<String> serverIdentities = CertificateManager.getServerIdentities( cert );

        // Verify result
        assertEquals( 2, serverIdentities.size() );
        assertTrue( serverIdentities.contains( subjectAltNameXmppAddr ));
        assertFalse( serverIdentities.contains( subjectCommonName ) );
    }

    /**
     * Asserts that {@link CertificateManager#parsePrivateKey(InputStream, String)} can parse a password-less private
     * key PEM file.
     */
    @Test
    public void testParsePrivateKey() throws Exception
    {
        // Setup fixture.
        try ( final InputStream stream = getClass().getResourceAsStream( "/privatekey.pem" ) )
        {
            // Execute system under test.
            final PrivateKey result = CertificateManager.parsePrivateKey( stream, "" );

            // Verify result.
            assertNotNull( result );
        }
    }

    /**
     * Asserts that {@link CertificateManager#parseCertificates(InputStream)} can parse a PEM file that contains a
     * certificate chain
     */
    @Test
    public void testParseFullChain() throws Exception
    {
        // Setup fixture.
        try ( final InputStream stream = getClass().getResourceAsStream( "/fullchain.pem" ) )
        {
            // Execute system under test.
            final Collection<X509Certificate> result = CertificateManager.parseCertificates( stream );

            // Verify result.
            assertNotNull( result );
            assertEquals( 2, result.size() );
        }
    }
}
