/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.CertificateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests that verify the functionality of {@link IdentityStore#updateIssuerOfSelfSignedCertificates(String, String, String, String, String)}.
 *
 * @author Matthew Vivian
 */
public class IdentityStoreIssuerUpdateTest
{
    private static final String STORE_TYPE = "jks";
    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final String SELF_SIGNED_ALIAS = "selfsigned";
    private static final String CA_SIGNED_ALIAS = "casigned";

    private static final String OU = "Engineering";
    private static final String O = "Example Organisation";
    private static final String CITY = "Exeter";
    private static final String STATE = "Devon";
    private static final String COUNTRY = "GB";

    @TempDir
    Path tempDir;

    private File storeFile;
    private KeystoreTestUtils.ResultHolder selfSigned;
    private KeystoreTestUtils.ResultHolder caSigned;
    private IdentityStore identityStore;

    @BeforeEach
    public void setUp() throws Exception
    {
        XMPPServer.setInstance( Fixtures.mockXMPPServer() );

        // A store with one self-signed and one CA-signed private key entry, written to disk.
        selfSigned = KeystoreTestUtils.generateSelfSignedCertificate( Fixtures.XMPP_DOMAIN );
        caSigned = KeystoreTestUtils.generateValidCertificateChain( Fixtures.XMPP_DOMAIN );

        final KeyStore keyStore = KeyStore.getInstance( STORE_TYPE );
        keyStore.load( null, PASSWORD );
        keyStore.setKeyEntry( SELF_SIGNED_ALIAS, selfSigned.getKeyPair().getPrivate(), PASSWORD, selfSigned.getCertificateChain() );
        keyStore.setKeyEntry( CA_SIGNED_ALIAS, caSigned.getKeyPair().getPrivate(), PASSWORD, caSigned.getCertificateChain() );

        storeFile = tempDir.resolve( "keystore.jks" ).toFile();
        try ( final FileOutputStream os = new FileOutputStream( storeFile ) )
        {
            keyStore.store( os, PASSWORD );
        }

        identityStore = new IdentityStore( new CertificateStoreConfiguration( STORE_TYPE, storeFile, PASSWORD, tempDir.toFile() ), false );
    }

    @AfterEach
    public void tearDown()
    {
        if ( storeFile != null )
        {
            storeFile.setWritable( true );
        }
        XMPPServer.setInstance( null );
    }

    /**
     * Verifies that a self-signed certificate is replaced by one that has the provided issuer details, with the XMPP
     * domain as its common name, as both its issuer and its subject.
     */
    @Test
    public void testSelfSignedCertificateGetsNewIssuerAndSubject() throws Exception
    {
        // Execute system under test.
        identityStore.updateIssuerOfSelfSignedCertificates( OU, O, CITY, STATE, COUNTRY );

        // Verify result.
        final X509Certificate result = (X509Certificate) identityStore.getStore().getCertificate( SELF_SIGNED_ALIAS );
        final X500Name issuer = X500Name.getInstance( result.getIssuerX500Principal().getEncoded() );
        final X500Name subject = X500Name.getInstance( result.getSubjectX500Principal().getEncoded() );

        assertEquals( Fixtures.XMPP_DOMAIN, rdnValue( issuer, BCStyle.CN ), "the issuer common name must be the XMPP domain" );
        assertEquals( OU, rdnValue( issuer, BCStyle.OU ), "the issuer must carry the provided organisational unit" );
        assertEquals( O, rdnValue( issuer, BCStyle.O ), "the issuer must carry the provided organisation" );
        assertEquals( CITY, rdnValue( issuer, BCStyle.L ), "the issuer must carry the provided city" );
        assertEquals( STATE, rdnValue( issuer, BCStyle.ST ), "the issuer must carry the provided state" );
        assertEquals( COUNTRY, rdnValue( issuer, BCStyle.C ), "the issuer must carry the provided country code" );
        assertEquals( issuer, subject, "the re-signed certificate must have the same subject as issuer" );
    }

    /**
     * Verifies that the re-signed certificate keeps the key pair of the original entry, and is itself self-signed (so
     * that a signing request can be generated from it).
     */
    @Test
    public void testSelfSignedCertificateKeepsItsKeyPair() throws Exception
    {
        // Execute system under test.
        identityStore.updateIssuerOfSelfSignedCertificates( OU, O, CITY, STATE, COUNTRY );

        // Verify result.
        final KeyStore store = identityStore.getStore();
        final X509Certificate result = (X509Certificate) store.getCertificate( SELF_SIGNED_ALIAS );
        final Key key = store.getKey( SELF_SIGNED_ALIAS, PASSWORD );

        assertNotEquals( selfSigned.getCertificate(), result, "the self-signed certificate must have been replaced" );
        assertEquals( selfSigned.getKeyPair().getPublic(), result.getPublicKey(), "the re-signed certificate must keep the original public key" );
        assertEquals( selfSigned.getKeyPair().getPrivate(), key, "the entry must keep the original private key" );
        assertTrue( CertificateManager.isSelfSignedCertificate( result ), "the re-signed certificate must be self-signed" );
        assertEquals( 1, store.getCertificateChain( SELF_SIGNED_ALIAS ).length, "the entry must hold only the re-signed certificate" );
    }

    /**
     * Verifies that an entry with a certificate that is not self-signed is left unchanged.
     */
    @Test
    public void testCertificateThatIsNotSelfSignedIsUntouched() throws Exception
    {
        // Execute system under test.
        identityStore.updateIssuerOfSelfSignedCertificates( OU, O, CITY, STATE, COUNTRY );

        // Verify result.
        final KeyStore store = identityStore.getStore();
        assertArrayEquals( caSigned.getCertificateChain(), store.getCertificateChain( CA_SIGNED_ALIAS ), "a CA-signed certificate chain must not be changed" );
        assertEquals( caSigned.getKeyPair().getPrivate(), store.getKey( CA_SIGNED_ALIAS, PASSWORD ), "the private key of a CA-signed entry must not be changed" );
    }

    /**
     * Verifies that the change is written to the file that backs the store.
     */
    @Test
    public void testChangeIsPersisted() throws Exception
    {
        // Execute system under test.
        identityStore.updateIssuerOfSelfSignedCertificates( OU, O, CITY, STATE, COUNTRY );

        // Verify result.
        final X509Certificate inMemory = (X509Certificate) identityStore.getStore().getCertificate( SELF_SIGNED_ALIAS );
        final IdentityStore fromDisk = new IdentityStore( new CertificateStoreConfiguration( STORE_TYPE, storeFile, PASSWORD, tempDir.toFile() ), false );
        assertEquals( inMemory, fromDisk.getStore().getCertificate( SELF_SIGNED_ALIAS ), "the re-signed certificate must have been saved to disk" );
    }

    /**
     * Verifies that when the change cannot be saved, an exception is thrown and the in-memory store is reloaded from
     * disk, so that it does not hold changes that are not in the file.
     */
    @Test
    public void testStoreIsReloadedWhenChangeCannotBeSaved() throws Exception
    {
        // Setup test fixture: make the file that backs the store impossible to write to.
        final byte[] fileBefore = Files.readAllBytes( storeFile.toPath() );
        assumeTrue( storeFile.setWritable( false ) && !Files.isWritable( storeFile.toPath() ), "The store file cannot be made read-only in this environment (for example, when running as root)." );

        // Execute system under test.
        assertThrows( CertificateStoreConfigException.class, () -> identityStore.updateIssuerOfSelfSignedCertificates( OU, O, CITY, STATE, COUNTRY ),
            "a failure to save the change must be reported" );

        // Verify result.
        assertArrayEquals( fileBefore, Files.readAllBytes( storeFile.toPath() ), "the store file must be unchanged" );
        assertEquals( selfSigned.getCertificate(), identityStore.getStore().getCertificate( SELF_SIGNED_ALIAS ), "the in-memory store must be reloaded from disk after a failure" );
    }

    /**
     * Verifies that the re-signed certificate is valid for 60 days, the period the admin console has always used for
     * it, regardless of the validity of the certificate that it replaces.
     */
    @Test
    public void testReissuedCertificateIsValidForSixtyDays() throws Exception
    {
        // Execute system under test.
        identityStore.updateIssuerOfSelfSignedCertificates( OU, O, CITY, STATE, COUNTRY );

        // Verify result.
        final X509Certificate result = (X509Certificate) identityStore.getStore().getCertificate( SELF_SIGNED_ALIAS );
        final Duration validity = Duration.between( result.getNotBefore().toInstant(), result.getNotAfter().toInstant() );

        // The two ends of the validity period are taken from separate clock reads and encoded with a precision of one
        // second, so allow for one second of difference.
        assertTrue( validity.minus( Duration.ofDays( 60 ) ).abs().compareTo( Duration.ofSeconds( 1 ) ) <= 0,
            "the re-signed certificate must be valid for 60 days, but is valid for " + validity );
    }

    /**
     * Verifies that the subject alternative names of the re-signed certificate are the DNS names that Openfire
     * determines for this server.
     */
    @Test
    public void testReissuedCertificateHasServerDnsNamesAsSubjectAlternativeNames() throws Exception
    {
        // Setup test fixture.
        final Set<String> expected = CertificateManager.determineSubjectAlternateNameDnsNameValues();

        // Execute system under test.
        identityStore.updateIssuerOfSelfSignedCertificates( OU, O, CITY, STATE, COUNTRY );

        // Verify result.
        final X509Certificate result = (X509Certificate) identityStore.getStore().getCertificate( SELF_SIGNED_ALIAS );
        final Set<String> dnsNames = result.getSubjectAlternativeNames().stream()
            .filter( san -> Integer.valueOf( 2 ).equals( san.get( 0 ) ) ) // 2 is the GeneralName type for dNSName.
            .map( san -> (String) san.get( 1 ) )
            .collect( Collectors.toSet() );
        assertEquals( expected, dnsNames, "the subject alternative names must be the DNS names determined for this server" );
    }

    /**
     * Verifies that when re-signing fails part-way (here because a self-signed trusted certificate entry has no private
     * key to sign with), an exception is thrown and no self-signed key entry is left changed in memory.
     *
     * The order in which a store lists its aliases is unspecified. For the failure to happen after the key entry has
     * been re-signed in memory (the case the reload exists for), the fixture picks an alias for the trusted entry that
     * the loaded store lists after the key entry, and checks that it did so.
     */
    @Test
    public void testStoreIsReloadedWhenReissuingFails() throws Exception
    {
        // Setup test fixture: add a self-signed trusted certificate entry (without a private key) to the store file,
        // under an alias that the loaded store lists after the self-signed key entry.
        final X509Certificate trustedCertificate = KeystoreTestUtils.generateSelfSignedCertificate( Fixtures.XMPP_DOMAIN ).getCertificate();
        final byte[] originalFile = Files.readAllBytes( storeFile.toPath() );
        List<String> aliases = List.of();
        String trustedAlias = null;
        for ( int i = 0; i < 50; i++ )
        {
            trustedAlias = "trusted" + i;
            final KeyStore keyStore = KeyStore.getInstance( STORE_TYPE );
            try ( final ByteArrayInputStream is = new ByteArrayInputStream( originalFile ) )
            {
                keyStore.load( is, PASSWORD );
            }
            keyStore.setCertificateEntry( trustedAlias, trustedCertificate );
            try ( final FileOutputStream os = new FileOutputStream( storeFile ) )
            {
                keyStore.store( os, PASSWORD );
            }
            identityStore = new IdentityStore( new CertificateStoreConfiguration( STORE_TYPE, storeFile, PASSWORD, tempDir.toFile() ), false );
            aliases = Collections.list( identityStore.getStore().aliases() );
            if ( aliases.indexOf( SELF_SIGNED_ALIAS ) < aliases.indexOf( trustedAlias ) )
            {
                break;
            }
        }
        assertTrue( aliases.indexOf( SELF_SIGNED_ALIAS ) < aliases.indexOf( trustedAlias ),
            "precondition: the store must list the self-signed key entry before the trusted entry, but lists " + aliases );
        final byte[] fileBefore = Files.readAllBytes( storeFile.toPath() );

        // Execute system under test.
        assertThrows( CertificateStoreConfigException.class, () -> identityStore.updateIssuerOfSelfSignedCertificates( OU, O, CITY, STATE, COUNTRY ),
            "a failure to re-sign a certificate must be reported as a CertificateStoreConfigException" );

        // Verify result.
        assertArrayEquals( fileBefore, Files.readAllBytes( storeFile.toPath() ), "the store file must be unchanged" );
        assertEquals( selfSigned.getCertificate(), identityStore.getStore().getCertificate( SELF_SIGNED_ALIAS ), "the self-signed key entry must hold its original certificate after a failure" );
    }

    /**
     * Verifies that a null argument is rejected before the store is changed.
     */
    @Test
    public void testNullArgumentIsRejected() throws Exception
    {
        // Setup test fixture.
        final byte[] fileBefore = Files.readAllBytes( storeFile.toPath() );

        // Execute system under test.
        assertThrows( NullPointerException.class, () -> identityStore.updateIssuerOfSelfSignedCertificates( OU, O, CITY, STATE, null ),
            "a null country code must be rejected" );

        // Verify result.
        assertArrayEquals( fileBefore, Files.readAllBytes( storeFile.toPath() ), "the store file must be unchanged" );
        assertEquals( selfSigned.getCertificate(), identityStore.getStore().getCertificate( SELF_SIGNED_ALIAS ), "the self-signed key entry must be unchanged" );
    }

    private static String rdnValue( final X500Name name, final org.bouncycastle.asn1.ASN1ObjectIdentifier type )
    {
        return IETFUtils.valueToString( name.getRDNs( type )[0].getFirst().getValue() );
    }
}
