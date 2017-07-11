package org.jivesoftware.openfire.keystore;

import org.junit.Assert;
import org.junit.Test;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Unit tests that verify the functionality of {@link CertificateUtils}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CertificateUtilsTest
{
    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * is null returns an empty collection.
     */
    @Test
    public void testFilterValidNull() throws Exception
    {
        // Setup fixture.
        final Collection<X509Certificate> input = null;

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        Assert.assertTrue( result.isEmpty() );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * is an empty (not-null) collection returns an empty collection.
     */
    @Test
    public void testFilterValidEmpty() throws Exception
    {
        // Setup fixture.
        final Collection<X509Certificate> input = new ArrayList<>();

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        Assert.assertTrue( result.isEmpty() );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains one valid certificate returns an collection that contains that certificate.
     */
    @Test
    public void testFilterValidWithOneValidCert() throws Exception
    {
        // Setup fixture.
        final X509Certificate valid = KeystoreTestUtils.generateValidCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( valid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        Assert.assertEquals( 1, result.size() );
        Assert.assertTrue( result.contains( valid ) );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains one invalid certificate returns an collection that is empty.
     */
    @Test
    public void testFilterValidWithOneInvalidCert() throws Exception
    {
        // Setup fixture.
        final X509Certificate invalid = KeystoreTestUtils.generateExpiredCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( invalid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        Assert.assertTrue( result.isEmpty() );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains two duplicate, valid certificates returns an collection that contains that certificate once.
     */
    @Test
    public void testFilterValidWithTwoDuplicateValidCerts() throws Exception
    {
        // Setup fixture.
        final X509Certificate valid = KeystoreTestUtils.generateValidCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( valid );
        input.add( valid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        Assert.assertEquals( 1, result.size() );
        Assert.assertTrue( result.contains( valid ) );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains two distinct, valid certificates returns an collection that contains both certificates.
     */
    @Test
    public void testFilterValidWithTwoDistinctValidCerts() throws Exception
    {
        // Setup fixture.
        final X509Certificate validA = KeystoreTestUtils.generateValidCertificate();
        final X509Certificate validB = KeystoreTestUtils.generateValidCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( validA );
        input.add( validB );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        Assert.assertEquals( 2, result.size() );
        Assert.assertTrue( result.contains( validA ) );
        Assert.assertTrue( result.contains( validB ) );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains two duplicate, invalid certificate returns an collection that is empty.
     */
    @Test
    public void testFilterValidWithTwoDuplicateInvalidCerts() throws Exception
    {
        // Setup fixture.
        final X509Certificate invalid = KeystoreTestUtils.generateExpiredCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( invalid );
        input.add( invalid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        Assert.assertTrue( result.isEmpty() );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains two distinct, invalid certificate returns an collection that is empty.
     */
    @Test
    public void testFilterValidWithTwoDistinctInvalidCerts() throws Exception
    {
        // Setup fixture.
        final X509Certificate invalidA = KeystoreTestUtils.generateExpiredCertificate();
        final X509Certificate invalidB = KeystoreTestUtils.generateExpiredCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( invalidA );
        input.add( invalidB );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        Assert.assertTrue( result.isEmpty() );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains one valid and one invalid valid certificatereturns an collection that contains one valid certificate.
     */
    @Test
    public void testFilterValidWithValidAndInvalidCerts() throws Exception
    {
        // Setup fixture.
        final X509Certificate valid = KeystoreTestUtils.generateValidCertificate();
        final X509Certificate invalid = KeystoreTestUtils.generateExpiredCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( valid );
        input.add( invalid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        Assert.assertEquals( 1, result.size() );
        Assert.assertTrue( result.contains( valid ) );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains:
     * - one valid
     * - another valid (duplicated from the first)
     * - a third valid (no duplicate)
     * - and one invalid valid certificate
     * returns an collection that contains the two distinc valid certificates.
     */
    @Test
    public void testFilterValidWithMixOfValidityAndDuplicates() throws Exception
    {
        // Setup fixture.
        final X509Certificate validA = KeystoreTestUtils.generateValidCertificate();
        final X509Certificate validB = KeystoreTestUtils.generateValidCertificate();
        final X509Certificate invalid = KeystoreTestUtils.generateExpiredCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( validA );
        input.add( validA );
        input.add( validB );
        input.add( invalid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        Assert.assertEquals( 2, result.size() );
        Assert.assertTrue( result.contains( validA ) );
        Assert.assertTrue( result.contains( validB ) );
    }
}
