package org.jivesoftware.openfire.keystore;

import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.jivesoftware.util.Base64;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import sun.security.provider.X509Factory;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Unit tests that verify the functionality of {@link TrustStoreConfig}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class TrustStoreConfigTest
{
//    /**
//     * An instance that is freshly recreated before each test.
//     */
//    private TrustStoreConfig trustStoreConfig;
//
//    @Before
//    public void createFixture() throws Exception
//    {
//        // Create a fresh store in a location that holds only temporary files.
//        final String tempDir = System.getProperty("java.io.tmpdir");
//        final String location = tempDir + ( tempDir.endsWith( File.separator ) ? "" : File.separator ) + UUID.randomUUID();
//
//        final KeyStore keyStore = KeyStore.getInstance( KeyStore.getDefaultType());
//        final String password = "TS%WV@# aSG 4";
//        keyStore.load( null, password.toCharArray() );
//
//        // Populate the store with a valid CA certificate.
//        final X509Certificate validCertificate = KeystoreTestUtils.generateValidCertificate();
//        keyStore.setCertificateEntry( "valid-ca", validCertificate );
//
//        // Populate the store with an invalid CA certificate. This certificate is invalid as its validity period has expired.
//        final X509Certificate invalidCertificate = KeystoreTestUtils.generateExpiredCertificate();
//        keyStore.setCertificateEntry( "invalid-ca", invalidCertificate );
//
//        // Persist the keystore file
//        try ( FileOutputStream fos = new FileOutputStream( location ) ) {
//            keyStore.store( fos, password.toCharArray() );
//        }
//
//        // Use the new keystore file to create a fresh trust store, which will be used as a fixture by the tests.
//        trustStoreConfig = new TrustStoreConfig( location, password, keyStore.getType(), false );
//    }
//
//    @After
//    public void tearDown() throws Exception
//    {
//        // Attempt to delete any left-overs from the test.
//        if (trustStoreConfig != null)
//        {
//            Files.deleteIfExists( Paths.get( trustStoreConfig.getCanonicalPath() ) );
//            trustStoreConfig = null;
//        }
//    }
//
//    /**
//     * The store in the fixture contains two certificates - one that is valid, and one that is invalid.
//     *
//     * This test verifies that {@link TrustStoreConfig#getAllCertificates()} returns both.
//     */
//    @Test
//    public void testGetAll() throws Exception
//    {
//        // Setup fixture.
//
//        // Execute system under test.
//        final Map<String, X509Certificate> result = trustStoreConfig.getAllCertificates();
//
//        // Verify results.
//        Assert.assertEquals( 2, result.size() );
//    }

//    /**
//     * The store in the fixture contains two certificates - one that is valid, and one that is invalid.
//     *
//     * This test verifies that {@link TrustStoreConfig#getAllValidTrustAnchors()} returns only the valid one.
//     */
//    @Test
//    public void testGetValid() throws Exception
//    {
//        // Setup fixture.
//
//        // Execute system under test.
//        final Set<TrustAnchor> result = trustStoreConfig.getAllValidTrustAnchors();
//
//        // Verify results.
//        Assert.assertEquals( 1, result.size() );
//        Assert.assertTrue( result.iterator().next().getTrustedCert().getIssuerDN().getName().contains( "CN=valid.example.org" ) );
//    }
//
//    /**
//     * A chain that has a trust anchor in the trust store (and is otherwise valid) should be trusted.
//     */
//    @Test
//    public void testTrustCertSignedByCA() throws Exception
//    {
//        // Setup fixture
//        final Collection<X509Certificate> chain = new HashSet<>();
//        chain.add( (X509Certificate) trustStoreConfig.getStore().getCertificate( "valid-ca" ) ); // somewhat of a hack. Should use a distinct cert for the test.
//
//        // Execute System Under Test
//        final boolean result = trustStoreConfig.canTrust( chain );
//
//        // Verify
//        Assert.assertTrue( result );
//    }
//
//    /**
//     * A chain that has no trust anchor in the trust store (but is otherwise valid) should not be trusted.
//     */
//    @Test
//    public void testDontTrustCertNotSignedByCA() throws Exception
//    {
//        // Setup fixture
//        final Collection<X509Certificate> chain = new HashSet<>();
//        chain.add( KeystoreTestUtils.generateValidCertificate() );
//
//        // Execute System Under Test
//        final boolean result = trustStoreConfig.canTrust( chain );
//
//        // Verify
//        Assert.assertFalse( result );
//    }
//
//    /**
//     * This test verifies that when a certificate is installed in the store using
//     * {@link TrustStoreConfig#installCertificate(String, String)} a certificate chain of which the anchor is that same
//     * certificate is successfully verified.
//     */
//    @Test
//    public void verifyWithNewlyInstalledCACert() throws Exception
//    {
//        // Setup fixture
//        final X509Certificate cert = KeystoreTestUtils.generateValidCertificate();
//        final String pemCert = KeystoreTestUtils.toPemFormat( cert );
//
//        final Collection<X509Certificate> chain = new HashSet<>();
//        chain.add( cert ); // somewhat of a hack. Should use a distinct cert for the test.
//
//        // Execute System Under Test
//        trustStoreConfig.installCertificate( "new-cert", pemCert );
//        final boolean result = trustStoreConfig.canTrust( chain );
//
//        // Verify
//        Assert.assertTrue( result );
//    }
}
