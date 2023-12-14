/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue( result.isEmpty() );
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
        assertTrue( result.isEmpty() );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains one valid certificate returns an collection that contains that certificate.
     */
    @Test
    public void testFilterValidWithOneValidCert() throws Exception
    {
        // Setup fixture.
        final X509Certificate valid = KeystoreTestUtils.generateValidCertificate().getCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( valid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        assertEquals( 1, result.size() );
        assertTrue( result.contains( valid ) );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains one invalid certificate returns an collection that is empty.
     */
    @Test
    public void testFilterValidWithOneInvalidCert() throws Exception
    {
        // Setup fixture.
        final X509Certificate invalid = KeystoreTestUtils.generateExpiredCertificate().getCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( invalid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        assertTrue( result.isEmpty() );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains two duplicate, valid certificates returns an collection that contains that certificate once.
     */
    @Test
    public void testFilterValidWithTwoDuplicateValidCerts() throws Exception
    {
        // Setup fixture.
        final X509Certificate valid = KeystoreTestUtils.generateValidCertificate().getCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( valid );
        input.add( valid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        assertEquals( 1, result.size() );
        assertTrue( result.contains( valid ) );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains two distinct, valid certificates returns an collection that contains both certificates.
     */
    @Test
    public void testFilterValidWithTwoDistinctValidCerts() throws Exception
    {
        // Setup fixture.
        final X509Certificate validA = KeystoreTestUtils.generateValidCertificate().getCertificate();
        final X509Certificate validB = KeystoreTestUtils.generateValidCertificate().getCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( validA );
        input.add( validB );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        assertEquals( 2, result.size() );
        assertTrue( result.contains( validA ) );
        assertTrue( result.contains( validB ) );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains two duplicate, invalid certificate returns an collection that is empty.
     */
    @Test
    public void testFilterValidWithTwoDuplicateInvalidCerts() throws Exception
    {
        // Setup fixture.
        final X509Certificate invalid = KeystoreTestUtils.generateExpiredCertificate().getCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( invalid );
        input.add( invalid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        assertTrue( result.isEmpty() );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains two distinct, invalid certificate returns an collection that is empty.
     */
    @Test
    public void testFilterValidWithTwoDistinctInvalidCerts() throws Exception
    {
        // Setup fixture.
        final X509Certificate invalidA = KeystoreTestUtils.generateExpiredCertificate().getCertificate();
        final X509Certificate invalidB = KeystoreTestUtils.generateExpiredCertificate().getCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( invalidA );
        input.add( invalidB );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        assertTrue( result.isEmpty() );
    }

    /**
     * Test for {@link CertificateUtils#filterValid(Collection)}. Verifies that an input argument that
     * contains one valid and one invalid valid certificatereturns an collection that contains one valid certificate.
     */
    @Test
    public void testFilterValidWithValidAndInvalidCerts() throws Exception
    {
        // Setup fixture.
        final X509Certificate valid = KeystoreTestUtils.generateValidCertificate().getCertificate();
        final X509Certificate invalid = KeystoreTestUtils.generateExpiredCertificate().getCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( valid );
        input.add( invalid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        assertEquals( 1, result.size() );
        assertTrue( result.contains( valid ) );
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
        final X509Certificate validA = KeystoreTestUtils.generateValidCertificate().getCertificate();
        final X509Certificate validB = KeystoreTestUtils.generateValidCertificate().getCertificate();
        final X509Certificate invalid = KeystoreTestUtils.generateExpiredCertificate().getCertificate();
        final Collection<X509Certificate> input = new ArrayList<>();
        input.add( validA );
        input.add( validA );
        input.add( validB );
        input.add( invalid );

        // Execute system under test.
        final Collection<X509Certificate> result = CertificateUtils.filterValid( input );

        // Verify results.
        assertEquals( 2, result.size() );
        assertTrue( result.contains( validA ) );
        assertTrue( result.contains( validB ) );
    }
}
