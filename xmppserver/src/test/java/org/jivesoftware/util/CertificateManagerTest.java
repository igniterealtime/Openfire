/*
 * Copyright (C) 2017-2024 Ignite Realtime Foundation. All rights reserved.
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
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.OtherName;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jivesoftware.util.cert.SANCertificateIdentityMapping;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test to validate the functionality of @{link CertificateManager}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class CertificateManagerTest
{
    public static final ASN1ObjectIdentifier XMPP_ADDR_OID = new ASN1ObjectIdentifier( "1.3.6.1.5.5.7.8.5" );
    public static final ASN1ObjectIdentifier DNS_SRV_OID = new ASN1ObjectIdentifier( "1.3.6.1.5.5.7.8.7" );

    public static final int KEY_SIZE = 2048;
    public static final String KEY_ALGORITHM = "RSA";
    public static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private static KeyPair subjectKeyPair;
    private static KeyPair issuerKeyPair;
    private static ContentSigner contentSigner;

    @BeforeAll
    public static void initialize() throws Exception
    {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        keyPairGenerator.initialize( KEY_SIZE );

        subjectKeyPair = keyPairGenerator.generateKeyPair();
        issuerKeyPair = keyPairGenerator.generateKeyPair();
        contentSigner = new JcaContentSignerBuilder( SIGNATURE_ALGORITHM ).build( issuerKeyPair.getPrivate() );
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
                new X500Name( "CN=MyIssuer" ),                          // Issuer
                BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ), // Random serial number
                Date.from( Instant.now().plus(Duration.ofDays(30)) ),           // Not before 30 days ago
                Date.from( Instant.now().minus(Duration.ofDays(99)) ),          // Not after 99 days from now
                new X500Name( "CN=" + subjectCommonName ),              // Subject
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
                new X500Name( "CN=MyIssuer" ),                          // Issuer
                BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ), // Random serial number
                Date.from( Instant.now().plus(Duration.ofDays(30)) ),           // Not before 30 days ago
                Date.from( Instant.now().minus(Duration.ofDays(99)) ),          // Not after 99 days from now
                new X500Name( "CN=" + subjectCommonName ),              // Subject
                subjectKeyPair.getPublic()
        );

        final OtherName otherName = new OtherName(XMPP_ADDR_OID, new DERUTF8String( subjectAltNameXmppAddr ) );
        final GeneralNames subjectAltNames = new GeneralNames( new GeneralName(GeneralName.otherName, otherName ) );
        builder.addExtension( Extension.subjectAlternativeName, true, subjectAltNames );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // FIXME: Unsure why, but without this back-and-forth, tests will fail on Java 17.
        final String value = CertificateManager.toPemRepresentation(cert);
        final Collection<X509Certificate> chain = CertificateManager.parseCertificates(value);
        cert = chain.iterator().next();

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
                new X500Name( "CN=MyIssuer" ),                          // Issuer
                BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ), // Random serial number
                Date.from( Instant.now().plus(Duration.ofDays(30)) ),           // Not before 30 days ago
                Date.from( Instant.now().minus(Duration.ofDays(99)) ),          // Not after 99 days from now
                new X500Name( "CN=" + subjectCommonName ),              // Subject
                subjectKeyPair.getPublic()
        );

        final OtherName otherName = new OtherName(DNS_SRV_OID, new DERIA5String( "_xmpp-server."+subjectAltNameDnsSrv ) );
        final GeneralNames subjectAltNames = new GeneralNames( new GeneralName(GeneralName.otherName, otherName ) );
        builder.addExtension( Extension.subjectAlternativeName, true, subjectAltNames );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // FIXME: Unsure why, but without this back-and-forth, tests will fail on Java 17.
        final String value = CertificateManager.toPemRepresentation(cert);
        final Collection<X509Certificate> chain = CertificateManager.parseCertificates(value);
        cert = chain.iterator().next();

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
                new X500Name( "CN=MyIssuer" ),                          // Issuer
                BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ), // Random serial number
                Date.from( Instant.now().plus(Duration.ofDays(30)) ),           // Not before 30 days ago
                Date.from( Instant.now().minus(Duration.ofDays(99)) ),          // Not after 99 days from now
                new X500Name( "CN=" + subjectCommonName ),              // Subject
                subjectKeyPair.getPublic()
        );

        final GeneralNames generalNames = new GeneralNames(new GeneralName(GeneralName.dNSName, subjectAltNameDNS));

        builder.addExtension( Extension.subjectAlternativeName, false, generalNames );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // FIXME: Unsure why, but without this back-and-forth, tests will fail on Java 17.
        final String value = CertificateManager.toPemRepresentation(cert);
        final Collection<X509Certificate> chain = CertificateManager.parseCertificates(value);
        cert = chain.iterator().next();

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
                new X500Name( "CN=MyIssuer" ),                          // Issuer
                BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ), // Random serial number
                Date.from( Instant.now().plus(Duration.ofDays(30)) ),           // Not before 30 days ago
                Date.from( Instant.now().minus(Duration.ofDays(99)) ),          // Not after 99 days from now
                new X500Name( "CN=" + subjectCommonName ),              // Subject
                subjectKeyPair.getPublic()
        );

        final OtherName otherName = new OtherName(XMPP_ADDR_OID, new DERUTF8String( subjectAltNameXmppAddr ) );
        final GeneralNames subjectAltNames = new GeneralNames( new GeneralName[] {
                new GeneralName( GeneralName.otherName, otherName ),
                new GeneralName( GeneralName.dNSName, subjectAltNameDNS )
        });
        builder.addExtension( Extension.subjectAlternativeName, true, subjectAltNames );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // FIXME: Unsure why, but without this back-and-forth, tests will fail on Java 17.
        final String value = CertificateManager.toPemRepresentation(cert);
        final Collection<X509Certificate> chain = CertificateManager.parseCertificates(value);
        cert = chain.iterator().next();

        // Execute system under test
        final List<String> serverIdentities = CertificateManager.getServerIdentities( cert );

        // Verify result
        assertEquals( 2, serverIdentities.size() );
        assertTrue( serverIdentities.contains( subjectAltNameXmppAddr ));
        assertFalse( serverIdentities.contains( subjectCommonName ) );
    }

    /**
     * Tests a PEM generated by OpenSSL using this config file:
     *
     * <code>
     * [ req ]
     * default_bits       = 2048
     * distinguished_name = req_distinguished_name
     * req_extensions     = req_ext
     * x509_extensions    = v3_ca # The main difference
     * prompt = no
     *
     * [ req_distinguished_name ]
     * C = US
     * ST = YourState
     * L = YourCity
     * O = YourOrganization
     * OU = YourUnit
     * CN = yourdomain.com
     *
     * [ req_ext ]
     * subjectAltName = @alt_names
     *
     * [ v3_ca ]
     * subjectAltName = @alt_names
     * basicConstraints = CA:TRUE
     * keyUsage = digitalSignature, keyEncipherment
     * extendedKeyUsage = serverAuth, clientAuth
     *
     * [ alt_names ]
     * otherName.0 = 1.3.6.1.5.5.7.8.7;IA5:_xmpp-server.service.example.com
     * otherName.1 = 1.3.6.1.5.5.7.8.7;IA5:_dns.service.example.net
     * otherName.2 = 1.3.6.1.5.5.7.8.5;UTF8:user@example.com
     * otherName.3 = 1.3.6.1.5.5.7.8.5;UTF8:not-a-user.example.com
     * URI.1 = xmpp:third-one.net
     * DNS.1 = yourdomain.com
     * DNS.2 = anotherdomain.com
     * IP.1 = 192.168.1.1
     * </code>
     *
     * Using:
     * <code>
     * $ openssl req -new -key mykey.key -out mycsr.csr -config san.cnf
     * $ openssl x509 -req -days 365 -in mycsr.csr -signkey mykey.key -out mycert.crt -extfile san.cnf -extensions v3_ca
     * Signature ok
     * subject=C = US, ST = YourState, L = YourCity, O = YourOrganization, OU = YourUnit, CN = yourdomain.com
     * Getting Private key
     * </code>
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2904">OF-2904</a>
     */
    @Test
    public void testPrebuiltPEM() throws Exception
    {
        // Setup test fixture.
        final Collection<X509Certificate> chain = CertificateManager.parseCertificates(
            "-----BEGIN CERTIFICATE-----\n" +
                "MIIErTCCA5WgAwIBAgIULIC8uiTUXMHADnhPH6YH2BoFcOIwDQYJKoZIhvcNAQEL\n" +
                "BQAwezELMAkGA1UEBhMCVVMxEjAQBgNVBAgMCVlvdXJTdGF0ZTERMA8GA1UEBwwI\n" +
                "WW91ckNpdHkxGTAXBgNVBAoMEFlvdXJPcmdhbml6YXRpb24xETAPBgNVBAsMCFlv\n" +
                "dXJVbml0MRcwFQYDVQQDDA55b3VyZG9tYWluLmNvbTAeFw0yNDExMTAxNjI4MzZa\n" +
                "Fw0yNTExMTAxNjI4MzZaMHsxCzAJBgNVBAYTAlVTMRIwEAYDVQQIDAlZb3VyU3Rh\n" +
                "dGUxETAPBgNVBAcMCFlvdXJDaXR5MRkwFwYDVQQKDBBZb3VyT3JnYW5pemF0aW9u\n" +
                "MREwDwYDVQQLDAhZb3VyVW5pdDEXMBUGA1UEAwwOeW91cmRvbWFpbi5jb20wggEi\n" +
                "MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDMDg2nLepMRS6o3F5oSiP/U4yh\n" +
                "5lOWSE24VQE4R0EMbTiQ1lATIA0AbYU0MbVfu2EU+6rcyml7wSwekVBdRq/KLcvH\n" +
                "5mJjmQ25qHzJIFzxqNtUygY790job51zpOsIaFfg+MZkCdCWQK5G4qUr5bkfCKCN\n" +
                "VCiFcTi1nJo/PIP5Cx+/NCq3iFUL//Dt4+UxADUhD9mdXODIFUYGAP0IDD5hL58g\n" +
                "0IPNAAECky1fx4oSP1G0I8IYEnZ7V3RXvO82WZOlthJTtyysVTlIt6vy2cyG6WIg\n" +
                "iuBYOyl3Uf1S//TAMQwDF6oBO43EkqJqEODe4HTdMODd+72LY/4HSbikyBvRAgMB\n" +
                "AAGjggEnMIIBIzCB5gYDVR0RBIHeMIHboC4GCCsGAQUFBwgHoCIWIF94bXBwLXNl\n" +
                "cnZlci5zZXJ2aWNlLmV4YW1wbGUuY29toCYGCCsGAQUFBwgHoBoWGF9kbnMuc2Vy\n" +
                "dmljZS5leGFtcGxlLm5ldKAeBggrBgEFBQcIBaASDBB1c2VyQGV4YW1wbGUuY29t\n" +
                "oCQGCCsGAQUFBwgFoBgMFm5vdC1hLXVzZXIuZXhhbXBsZS5jb22GEnhtcHA6dGhp\n" +
                "cmQtb25lLm5ldIIOeW91cmRvbWFpbi5jb22CEWFub3RoZXJkb21haW4uY29thwTA\n" +
                "qAEBMAwGA1UdEwQFMAMBAf8wCwYDVR0PBAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUF\n" +
                "BwMBBggrBgEFBQcDAjANBgkqhkiG9w0BAQsFAAOCAQEADrekbzSNviLTvI8DXqBD\n" +
                "JnNPPS98nzWgABscB5Xups+G7Jrj4aibNHonePXW8B6rOqEYeBBbIzCYRRRPbuGl\n" +
                "kqksCmGa0/CWYX0uf4RoLaGy5BzZndJWYNPe/Hj5GbyLbFCFNyBOMDz0NyrwfVoH\n" +
                "Yq0W2rkve2SWKp7iiiUc80qKj4tcTX25x5h8oLgv7Lh4OAGKXFr6TYk23wdDPjiC\n" +
                "zZlXLN8TFw+RT7LQQc/Xi8XC/1ULbLalTEwh/xIaKju5P5CBTZO9xnVDc9LJ3hww\n" +
                "TN04BDlf3U02OCoSr0SxiLmmDRJOLbzGJK2AEQPpHUM5URcd98Tf2GzyUvxfhHUc\n" +
                "7A==\n" +
                "-----END CERTIFICATE-----");
        final SANCertificateIdentityMapping mapper = new SANCertificateIdentityMapping();

        // Execute system under test.
        final List<String> result = mapper.mapIdentity(chain.iterator().next());

        // Verify results
        assertTrue(result.contains("service.example.com"), "Expected the to contain 'service.example.com', as the certificate contains an id-on-dnsSRV OtherName entry with value '_xmpp-server.service.example.com'");
        assertFalse(result.contains("service.example.net"), "Didn't expect the result to contain 'service.example.net. Although the certificate contains an id-on-dnsSRV OtherName entry with value '_dns.service.example.net', it service is not of an XMPP-type (but rather, DNS).");
        assertTrue(result.contains("user@example.com"), "Expected the result to contain 'user@example.com', as the certificate contains that value in an id-on-xmppAddr OtherName entry.");
        assertTrue(result.contains("not-a-user.example.com"), "Expected the result to contain 'not-a-user.example.com', as the certificate contains that value in an id-on-xmppAddr OtherName entry.");
        assertFalse(result.contains("third-one.net"), "Didn't expect the result to contain 'third-one.net, which is provided in an URI entry in the certificate. URI entries are not defined as a valid source for JIDs in a certificate by RFC6120.");
        assertTrue(result.contains("yourdomain.com"), "Expected the result to contain 'yourdomain.com', as the certificate contains that value in DNS entry.");
        assertTrue(result.contains("anotherdomain.com"), "Expected the result to contain 'anotherdomain.com', as the certificate contains that value in DNS entry.");
        assertFalse(result.contains("192.168.1.1"), "Didn't expect the result to contain '192.168.1.1, which is provided in an IP entry in the certificate. IP entries are not defined as a valid source for JIDs in a certificate by RFC6120.");
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

    /**
     * Asserts the date-based validity constraints in a certificate that is generated by {@link org.jivesoftware.util.CertificateManager#createX509V3Certificate(KeyPair, int, X500NameBuilder, X500NameBuilder, String, String, Set)}
     */
    @Test
    public void testGenerateCertificateDateValidity() throws Exception
    {
        // Setup fixture.
        final KeyPair keyPair = subjectKeyPair;
        final int days = 2;
        final String issuerCommonName = "issuer common name";
        final String subjectCommonName = "subject common name";
        final String domain = "domain.example.org";
        final Set<String> sanDnsNames = Stream.of( "alternative-a.example.org", "alternative-b.example.org" ).collect( Collectors.toSet() );

        // Execute system under test.
        final X509Certificate result = CertificateManager.createX509V3Certificate( keyPair, days, issuerCommonName, subjectCommonName, domain, SIGNATURE_ALGORITHM, sanDnsNames );

        // Verify results.
        assertNotNull( result );

        assertCertificateDateValid( "The generated certificate is expected to be valid immediately (but is not).", result, new Date() );
        assertCertificateDateValid( "The generated certificate is expected to be valid half was during its maximum validity period (but is not).", result, addDays( days / 2 ) );
        assertCertificateDateNotValid( "The generated certificate is not expected to be valid on a date before it was created (but is).", result, addDays( -1 ) );
        assertCertificateDateNotValid( "The generated certificate is not expected to be valid after its maximum validity period has ended (but is).", result, addDays( days * 2 ) );
    }

    /**
     * Asserts that the provided common name of the issuer is returned as part of the issuer distinguished name in a certificate that is generated by {@link org.jivesoftware.util.CertificateManager#createX509V3Certificate(KeyPair, int, X500NameBuilder, X500NameBuilder, String, String, Set)}
     */
    @Test
    public void testGenerateCertificateIssuer() throws Exception
    {
        // Setup fixture.
        final KeyPair keyPair = subjectKeyPair;
        final int days = 2;
        final String issuerCommonName = "issuer common name";
        final String subjectCommonName = "subject common name";
        final String domain = "domain.example.org";
        final Set<String> sanDnsNames = Stream.of( "alternative-a.example.org", "alternative-b.example.org" ).collect( Collectors.toSet() );

        // Execute system under test.
        final X509Certificate result = CertificateManager.createX509V3Certificate( keyPair, days, issuerCommonName, subjectCommonName, domain, SIGNATURE_ALGORITHM, sanDnsNames );

        // Verify results.
        assertNotNull( result );

        final Set<String> foundIssuerCNs = parse( result.getIssuerX500Principal().getName(), "CN" );
        assertEquals( 1, foundIssuerCNs.size() );
        assertEquals( issuerCommonName, foundIssuerCNs.iterator().next() );
    }

    /**
     * Asserts that the provided common name of the subject is returned as part of the subject distinguished name in a certificate that is generated by {@link org.jivesoftware.util.CertificateManager#createX509V3Certificate(KeyPair, int, X500NameBuilder, X500NameBuilder, String, String, Set)}
     */
    @Test
    public void testGenerateCertificateSubject() throws Exception
    {
        // Setup fixture.
        final KeyPair keyPair = subjectKeyPair;
        final int days = 2;
        final String issuerCommonName = "issuer common name";
        final String subjectCommonName = "subject common name";
        final String domain = "domain.example.org";
        final Set<String> sanDnsNames = Stream.of( "alternative-a.example.org", "alternative-b.example.org" ).collect( Collectors.toSet() );

        // Execute system under test.
        final X509Certificate result = CertificateManager.createX509V3Certificate( keyPair, days, issuerCommonName, subjectCommonName, domain, SIGNATURE_ALGORITHM, sanDnsNames );

        // Verify results.
        assertNotNull( result );

        final Set<String> foundSubjectCNs = parse( result.getSubjectX500Principal().getName(), "CN" );
        assertEquals( 1, foundSubjectCNs.size() );
        assertEquals( subjectCommonName, foundSubjectCNs.iterator().next() );
    }

    /**
     * Asserts that the provided subject alternative DNS names are returned as part of the subject alternative names in a certificate that is generated by {@link org.jivesoftware.util.CertificateManager#createX509V3Certificate(KeyPair, int, X500NameBuilder, X500NameBuilder, String, String, Set)}
     */
    @Test
    public void testGenerateCertificateSubjectAlternativeNames() throws Exception
    {
        // Setup fixture.
        final KeyPair keyPair = subjectKeyPair;
        final int days = 2;
        final String issuerCommonName = "issuer common name";
        final String subjectCommonName = "subject common name";
        final String domain = "domain.example.org";
        final Set<String> sanDnsNames = Stream.of( "alternative-a.example.org", "alternative-b.example.org" ).collect( Collectors.toSet() );

        // Execute system under test.
        final X509Certificate result = CertificateManager.createX509V3Certificate( keyPair, days, issuerCommonName, subjectCommonName, domain, SIGNATURE_ALGORITHM, sanDnsNames );

        // Verify results.
        assertNotNull( result );

        final ArrayList<List<?>> expectedNames = new ArrayList<>();
        for ( final String sanDnsName : sanDnsNames ) {
            expectedNames.add( Arrays.asList( 2 , sanDnsName ) );
        }

        assertThat( "Expected to find all 'alternative names' as DNS entries in the subject alternative names (but does not).",
                    result.getSubjectAlternativeNames(),
                    both( hasSize( sanDnsNames.size() ) )
                        .and( containsInAnyOrder( expectedNames.toArray() ) )
        );
    }

    public static Date addDays( int amount )
    {
        final Calendar instance = Calendar.getInstance();
        instance.add( Calendar.DATE, amount );
        return instance.getTime();
    }

    /**
     *
     * @see <a href="https://stackoverflow.com/questions/2914521/how-to-extract-cn-from-x509certificate-in-java>https://stackoverflow.com/questions/2914521/how-to-extract-cn-from-x509certificate-in-java</a>
     */
    public static Set<String> parse( String distinguishedName, String identifier ) throws Exception
    {
        final Set<String> result = new HashSet<>();

        final LdapName ln = new LdapName( distinguishedName);
        for( final Rdn rdn : ln.getRdns() )
        {
            if( rdn.getType().equalsIgnoreCase( identifier ) )
            {
                result.add( rdn.getValue().toString() );
            }
        }
        return result;
    }

    public static void assertCertificateDateValid( String message, X509Certificate certificate, Date date )
    {
        try
        {
            certificate.checkValidity( date );
        }
        catch ( CertificateExpiredException | CertificateNotYetValidException e )
        {
            fail( message );
        }
    }

    public static void assertCertificateDateNotValid( String message, X509Certificate certificate, Date date )
    {
        try
        {
            certificate.checkValidity( date );
            fail( message );
        }
        catch ( CertificateExpiredException | CertificateNotYetValidException e )
        {
            // This is expected to be thrown.
        }
    }
}
