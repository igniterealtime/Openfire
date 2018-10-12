package org.jivesoftware.openfire.keystore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.*;
import java.util.*;

/**
 * Unit tests that verify the functionality of {@link OpenfireX509TrustManager}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class OpenfireX509TrustManagerTest
{
    /**
     * An instance that is freshly recreated before each test.
     */
    private OpenfireX509TrustManager systemUnderTest;

    /**
     * The keystore that contains the certificates used by the system under test (refreshed before every test invocation).
     */
    private KeyStore trustStore;
    private String location; // location on disc for the keystore. Used to clean-up after each test.

    private X509Certificate[] validChain;
    private X509Certificate[] expiredIntChain;
    private X509Certificate[] expiredRootChain;
    private X509Certificate[] untrustedCAChain;

    @Before
    public void createFixture() throws Exception
    {
        // Create a fresh store in a location that holds only temporary files.
        final String tempDir = System.getProperty("java.io.tmpdir");
        location = tempDir + ( tempDir.endsWith( File.separator ) ? "" : File.separator ) + UUID.randomUUID();

        trustStore = KeyStore.getInstance( KeyStore.getDefaultType());
        final String password = "TS%WV@# aSG 4";
        trustStore.load( null, password.toCharArray() );

        // Populate the store with a valid CA certificate.
        validChain       = KeystoreTestUtils.generateValidCertificateChain();
        expiredIntChain  = KeystoreTestUtils.generateCertificateChainWithExpiredIntermediateCert();
        expiredRootChain = KeystoreTestUtils.generateCertificateChainWithExpiredRootCert();
        untrustedCAChain = KeystoreTestUtils.generateValidCertificateChain();

        trustStore.setCertificateEntry( getLast( validChain       ).getSubjectDN().getName(), getLast( validChain       ) );
        trustStore.setCertificateEntry( getLast( expiredIntChain  ).getSubjectDN().getName(), getLast( expiredIntChain  ) );
        trustStore.setCertificateEntry( getLast( expiredRootChain ).getSubjectDN().getName(), getLast( expiredRootChain ) );

        // Persist the key store file
        try ( FileOutputStream fos = new FileOutputStream( location ) ) {
            trustStore.store( fos, password.toCharArray() );
        }

        // Create the Trust Manager that is subject of these tests.
        systemUnderTest = new OpenfireX509TrustManager( trustStore, false, true );
    }

    /**
     * Returns the last element from the provided array.
     * @param chain An array (cannot be null).
     * @return The last element of the provided array.
     */
    private static <X extends Object> X getLast(X[]chain) {
        return chain[ chain.length - 1 ];
    }

    @After
    public void tearDown() throws Exception
    {
        // Attempt to delete any left-overs from the test.
        validChain = null;

        if ( trustStore != null)
        {
            trustStore = null;
            Files.deleteIfExists( Paths.get( location ) );
        }

        systemUnderTest = null;
    }

    /**
     * This test verifies that {@link OpenfireX509TrustManager#getAcceptedIssuers()} does not return expired
     * certificates.
     */
    @Test
    public void testAcceptedIssuersAreAllValid() throws Exception
    {
        // Setup fixture.

        // Execute system under test.
        final X509Certificate[] result = systemUnderTest.getAcceptedIssuers();

        // Verify results.
        Assert.assertEquals( 2, result.length );
        Assert.assertTrue( Arrays.asList( result ).contains( getLast( validChain ) ) );
        Assert.assertTrue( Arrays.asList( result ).contains( getLast( expiredIntChain ) ) );
        Assert.assertFalse( Arrays.asList( result ).contains( getLast( expiredRootChain ) ) );
    }

    /**
     * Verifies that a valid chain is not rejected.
     */
    @Test
    public void testValidChain() throws Exception
    {
        // Setup fixture.

        // Execute system under test.
        systemUnderTest.checkClientTrusted( validChain, "RSA" );

        // Verify result
        // (getting here without an exception being thrown is enough).
    }

    /**
     * Verifies that a chain that has an intermediate certificate that is expired is rejected.
     */
    @Test(expected = CertificateException.class)
    public void testInvalidChainExpiredIntermediate() throws Exception
    {
        // Setup fixture.

        // Execute system under test.
        systemUnderTest.checkClientTrusted( expiredIntChain, "RSA" );
    }

    /**
     * Verifies that a chain that has an root certificate (trust anchor) that is expired is rejected.
     */
    @Test(expected = CertificateException.class)
    public void testInvalidChainExpiredTrustAnchor() throws Exception
    {
        // Setup fixture.

        // Execute system under test.
        systemUnderTest.checkClientTrusted( expiredRootChain, "RSA" );
    }

    /**
     * Verifies that a chain that is missing an intermediate certificate is rejected.
     */
    @Test(expected = CertificateException.class)
    public void testInvalidChainMissingIntermediate() throws Exception
    {
        // Setup fixture.
        assert validChain.length == 4;
        final X509Certificate[] input = new X509Certificate[ 3 ];
        input[ 0 ] = validChain[ 0 ];
        input[ 1 ] = validChain[ 2 ];
        input[ 2 ] = validChain[ 3 ];

        // Execute system under test.
        systemUnderTest.checkClientTrusted( input, "RSA" );
    }

    /**
     * Verifies that a chain that is valid, but does not have its root CA certificate in the trust store, is rejected.
     */
    @Test(expected = CertificateException.class)
    public void testInvalidChainCAnotTrusted() throws Exception
    {
        // Setup fixture.

        // Execute system under test.
        systemUnderTest.checkClientTrusted( untrustedCAChain, "RSA" );
    }
}
