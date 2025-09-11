/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util.cert;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link CNCertificateIdentityMapping}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class CNCertificateIdentityMappingTest
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
     * Asserts that a basic common name can be extracted from the subject attribute of a certificate.
     */
    @Test
    public void testSimpleCommonName() throws Exception
    {
        // Setup fixture.
        final String subject = "CN=MySubjectCommonName";

        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            new X500Name( "CN=MyIssuer" ),                          // Issuer
            BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ), // Random serial number
            Date.from( Instant.now().plus(Duration.ofDays(30)) ),           // Not before 30 days ago
            Date.from( Instant.now().minus(Duration.ofDays(99)) ),          // Not after 99 days from now
            new X500Name( subject ),                                        // Subject
            subjectKeyPair.getPublic()
        );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        final X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // Execute system under test
        final List<String> identities = new CNCertificateIdentityMapping().mapIdentity( cert );

        // Verify result
        assertEquals( 1, identities.size());
        assertEquals( "MySubjectCommonName", identities.get( 0 ) );
    }

    /**
     * Asserts that a common name that is specifically crafted to inject an additional, false identity can be extracted
     * from the subject attribute of a certificate.
     */
    @Test
    public void testInjectedCommonName() throws Exception
    {
        // Setup fixture.
        final String subject = "OU=\"infrastructure, CN=admin,\", O=Example Corp, C=GB, CN=attacker";

        final X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            new X500Name( "CN=MyIssuer" ),                          // Issuer
            BigInteger.valueOf( Math.abs( new SecureRandom().nextInt() ) ), // Random serial number
            Date.from( Instant.now().plus(Duration.ofDays(30)) ),           // Not before 30 days ago
            Date.from( Instant.now().minus(Duration.ofDays(99)) ),          // Not after 99 days from now
            new X500Name( subject ),                                        // Subject
            subjectKeyPair.getPublic()
        );

        final X509CertificateHolder certificateHolder = builder.build( contentSigner );
        final X509Certificate cert = new JcaX509CertificateConverter().getCertificate( certificateHolder );

        // Execute system under test
        final List<String> identities = new CNCertificateIdentityMapping().mapIdentity( cert );

        // Verify result
        assertEquals( 1, identities.size(), "Identities found: " + identities );
        assertEquals( "attacker", identities.get( 0 ) );
    }
}
