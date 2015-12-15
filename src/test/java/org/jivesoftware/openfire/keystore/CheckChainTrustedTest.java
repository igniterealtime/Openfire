package org.jivesoftware.openfire.keystore;

import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.security.KeyStore;
import java.security.cert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests that verify the functionality of {@link OpenfireX509TrustManager#checkChainTrusted(CertSelector, X509Certificate...)}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@RunWith( Parameterized.class )
public class CheckChainTrustedTest
{
    /**
     * All tests in this class are executed repeatedly. A new instance of this class is created for each element in the
     * iterable returned here (its values are passed to the constructor of this class). This allows us to execute the
     * same set of tests against a different configuration of the system under test.
     */
    @Parameterized.Parameters(name = "acceptSelfSignedCertificates={0},checkValidity={1}" )
    public static Iterable<Object[]> constructorArguments()
    {
        final List<Object[]> constructorArguments = new ArrayList<>();
        constructorArguments.add( new Object[] { false, true } );  // acceptSelfSignedCertificates = false, check validity = true
        constructorArguments.add( new Object[] { false, false } ); // acceptSelfSignedCertificates = false, check validity = false
        constructorArguments.add( new Object[] { true, true } );   // acceptSelfSignedCertificates = true, check validity = true
        constructorArguments.add( new Object[] { true, false } );  // acceptSelfSignedCertificates = true, check validity = false
        return constructorArguments;
    }

    /**
     * Configuration for the system under test: does or does not accept self-signed certificates.
     */
    private final boolean acceptSelfSigned;

    /**
     * Configuration for the system under test: does or does not check current validity (notBefore/notAfter).
     */
    private final boolean checkValidity;

    /**
     * The keystore that contains the certificates used by the system under test (refreshed before every test invocation).
     */
    private KeyStore trustStore;

    /**
     * A valid chain of certificates, where the first certificate represents the end-entity certificate and the last
     * certificate represents the trust anchor (the 'root certificate'). This root certificate is present
     * in {@link #trustStore} (refreshed before every test invocation).
     */
    private X509Certificate[] validChain;

    /**
     * A chain of certificates where the first certificate represents the end-entity certificate and the last
     * certificate represents the trust anchor (the 'root certificate'). This root certificate is present
     * in {@link #trustStore}. One of the intermediate certificates in this chain is expired. The chain is otherwise
     * valid. (refreshed before every test invocation).
     */
    private X509Certificate[] expiredIntChain;

    /**
     * A chain of certificates where the first certificate represents the end-entity certificate and the last
     * certificate represents the trust anchor (the 'root certificate'). This root certificate is present
     * in {@link #trustStore}. The root certificate in this chain is expired. The chain is otherwise
     * valid. (refreshed before every test invocation).
     */
    private X509Certificate[] expiredRootChain;

    /**
     * The system under test (refreshed before every test invocation).
     */
    private OpenfireX509TrustManager trustManager;

    public CheckChainTrustedTest( boolean acceptSelfSigned, boolean checkValidity )
    {
        this.acceptSelfSigned = acceptSelfSigned;
        this.checkValidity = checkValidity;
    }

    @Before
    public void createFixture() throws Exception
    {
        trustStore = KeyStore.getInstance( KeyStore.getDefaultType() );
        trustStore.load( null, null );

        // Generate a valid chain and add its root certificate to the trust store.
        validChain = KeystoreTestUtils.generateValidCertificateChain();
        trustStore.setCertificateEntry( getLast( validChain ).getSubjectDN().getName(), getLast( validChain ) );

        // Generate a chain with an expired intermediate certificate and add its root certificate to the trust store.
        expiredIntChain  = KeystoreTestUtils.generateCertificateChainWithExpiredIntermediateCert();
        trustStore.setCertificateEntry( getLast( expiredIntChain ).getSubjectDN().getName(), getLast( expiredIntChain ) );

        // Generate a chain with an expired root certificate and add its root certificate to the trust store.
        expiredRootChain = KeystoreTestUtils.generateCertificateChainWithExpiredRootCert();
        trustStore.setCertificateEntry( getLast( expiredRootChain ).getSubjectDN().getName(), getLast( expiredRootChain ) );

        // Reset the system under test before each test.
        trustManager = new OpenfireX509TrustManager( trustStore, acceptSelfSigned, checkValidity );
    }

    /**
     * Returns the last element from the provided array.
     *
     * @param chain An array (cannot be null).
     * @return The last element of the provided array.
     */
    private static <X> X getLast( X[] chain ) {
        return chain[ chain.length - 1 ];
    }

    @After
    public void tearDown() throws Exception
    {
        if ( trustStore != null)
        {
            trustStore = null;
        }
    }

    /**
     * Verifies that providing a null value for the first argument causes a runtime exception to be thrown.
     */
    @Test( expected = RuntimeException.class )
    public void testNullSelectorArgument() throws Exception
    {
        // Setup fixture.
        final CertSelector selector = null;
        final X509Certificate[] chain = validChain;

        // Execute system under test.
        trustManager.checkChainTrusted( selector, chain );

        // Verify results
        // (verified by 'expected' parameter of @Test annotation).
    }

    /**
     * Verifies that providing a null value for the first argument causes a runtime exception to be thrown.
     */
    @Test( expected = RuntimeException.class )
    public void testNullChainArgument() throws Exception
    {
        // Setup fixture.
        final CertSelector selector = new X509CertSelector();
        final X509Certificate[] chain = null;

        // Execute system under test.
        trustManager.checkChainTrusted( selector, chain );

        // Verify results
        // (verified by 'expected' parameter of @Test annotation).
    }

    /**
     * Verifies that providing an empty array for the first argument causes a runtime exception to be thrown.
     */
    @Test( expected = RuntimeException.class )
    public void testEmptyChainArgument() throws Exception
    {
        // Setup fixture.
        final CertSelector selector = new X509CertSelector();
        final X509Certificate[] chain = new X509Certificate[0];

        // Execute system under test.
        trustManager.checkChainTrusted( selector, chain );

        // Verify results
        // (verified by 'expected' parameter of @Test annotation).
    }

    /**
     * Verifies that null values in the provided chain are silently ignored.
     */
    @Test
    public void testIgnoreEmptyArraySlots() throws Exception
    {
        // Setup fixture.
        final CertSelector selector = new X509CertSelector();

        // Inject a null value, moving but not removing all other certificates.
        final List<X509Certificate> copy = new ArrayList<>( Arrays.asList( validChain ) ); // wrapping needed to support remove function.
        copy.add( 1, null );
        final X509Certificate[] chain = copy.toArray( new X509Certificate[ copy.size() ] );

        // Execute system under test.
        final CertPath result = trustManager.checkChainTrusted( selector, chain );

        // Verify results
        Assert.assertNotNull( result );
    }

    /**
     * Verifies that providing a complete and valid chain does not throw an exception (selection criteria during this
     * test: match on subject). This is a 'happy flow' test.
     */
    @Test
    public void testFullChain() throws Exception
    {
        // Setup fixture.
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( validChain[0].getSubjectX500Principal() );
        final X509Certificate[] chain = validChain;

        // Execute system under test.
        final CertPath result = trustManager.checkChainTrusted( selector, chain );

        // Verify results
        Assert.assertNotNull( result );
    }

    /**
     * Verifies that providing a complete, valid but unordered chain does not throw an exception (selection criteria
     * during this test: match on subject).
     */
    @Test
    public void testFullChainUnordered() throws Exception
    {
        // Setup fixture.
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( validChain[0].getSubjectX500Principal() );
        final List<X509Certificate> input = new ArrayList<>( Arrays.asList( validChain ));
        final List<X509Certificate> shuffled = new ArrayList<>( input );
        while ( input.equals( shuffled ) ) {
            Collections.shuffle( shuffled );
        }
        final X509Certificate[] chain = shuffled.toArray( new X509Certificate[ shuffled.size() ] );

        // Execute system under test.
        final CertPath result = trustManager.checkChainTrusted( selector, chain );

        // Verify results
        Assert.assertNotNull( result );
    }

    /**
     * Verifies that providing a valid chain that does not contain the root certificate does not throw an exception
     * (selection criteria during this test: match on subject). This is a 'happy flow' test.
     */
    @Test
    public void testPartialChain() throws Exception
    {
        // Setup fixture.
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( validChain[0].getSubjectX500Principal() );
        final X509Certificate[] chain = Arrays.copyOf( validChain, validChain.length - 1);

        // Execute system under test.
        final CertPath result = trustManager.checkChainTrusted( selector, chain );

        // Verify results
        Assert.assertNotNull( result );
    }

    /**
     * Verifies that providing a valid chain that does not contain the root certificate and is not ordered does not
     * throw an exception (selection criteria during this test: match on subject).
     */
    @Test
    public void testPartialChainUnordered() throws Exception
    {
        // Setup fixture.
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( validChain[0].getSubjectX500Principal() );
        final List<X509Certificate> input = new ArrayList<>( Arrays.asList( validChain ));
        input.remove( input.size() - 1 );
        final List<X509Certificate> shuffled = new ArrayList<>( input );
        while ( input.equals( shuffled ) ) {
            Collections.shuffle( shuffled );
        }
        final X509Certificate[] chain = shuffled.toArray( new X509Certificate[ shuffled.size() ] );


        // Execute system under test.
        final CertPath result = trustManager.checkChainTrusted( selector, chain );

        // Verify results
        Assert.assertNotNull( result );
    }

    /**
     * Verifies that providing a chain that does not contain an intermediate certificate throws an exception
     * (selection criteria during this test: match on subject).
     */
    @Test( expected = CertPathBuilderException.class )
    public void testIncompleteChain() throws Exception
    {
        // Setup fixture.
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( validChain[0].getSubjectX500Principal() );

        // Copy all but the second certificate of a valid chain to a chain that's used for testing.
        final List<X509Certificate> copy = new ArrayList<>( Arrays.asList( validChain ) ); // wrapping needed to support remove function.
        copy.remove( 1 );
        final X509Certificate[] chain = copy.toArray( new X509Certificate[ copy.size() ] );

        // Execute system under test.
        trustManager.checkChainTrusted( selector, chain );

        // Verify results
        // (verified by 'expected' parameter of @Test annotation).
    }

    /**
     * Verifies that providing chain for which the root certificate is not in the store (but is otherwise valid)
     * throw a CertPathBuilderException.
     */
    @Test( expected = CertPathBuilderException.class )
    public void testFullChainUnrecognizedRoot() throws Exception
    {
        // Setup fixture.
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( validChain[0].getSubjectX500Principal() );
        final X509Certificate[] chain = KeystoreTestUtils.generateValidCertificateChain();

        // Execute system under test.
        trustManager.checkChainTrusted( selector, chain );

        // Verify results
        // (verified by 'expected' parameter of @Test annotation).
    }

    /**
     * Verifies that providing chain for which an intermediate certificate is expired (but is otherwise valid)
     * throws a CertPathBuilderException only when the system under test is configured to enforce date validity.
     */
    @Test
    public void testExpiredIntermediateChain() throws Exception
    {
        // Setup fixture.
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( expiredIntChain[0].getSubjectX500Principal() );
        final X509Certificate[] chain = expiredIntChain;

        // Execute system under test.
        CertPath result = null;
        CertPathBuilderException exception = null;

        try
        {
            result = trustManager.checkChainTrusted( selector, chain );
        }
        catch ( CertPathBuilderException ex)
        {
            exception = ex;
        }

        // Verify results
        if ( checkValidity )
        {
            Assert.assertNotNull( "Certificate validity is enforced. Validation should have thrown an exception.", exception );
        }
        else
        {
            Assert.assertNotNull( "Certificate validity is not checked. Validation should have succeeded.", result );
        }
    }

    /**
     * Verifies that providing chain for which the root certificate is expired (but is otherwise valid)
     * throws a CertPathBuilderException only when the system under test is configured to enforce date validity.
     */
    @Test
    public void testExpiredRootChain() throws Exception
    {
        // Setup fixture.
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( expiredRootChain[0].getSubjectX500Principal() );
        final X509Certificate[] chain = expiredRootChain;

        // Execute system under test.
        CertPath result = null;
        CertPathBuilderException exception = null;

        try
        {
            result = trustManager.checkChainTrusted( selector, chain );
        }
        catch ( CertPathBuilderException ex)
        {
            exception = ex;
        }

        // Verify results
        if ( checkValidity )
        {
            Assert.assertNotNull( "Certificate validity is enforced. Validation should have thrown an exception.", exception );
        }
        else
        {
            Assert.assertNotNull( "Certificate validity is not checked. Validation should have succeeded.", result );
        }
    }

    /**
     * Identical to {@link #testExpiredRootChain()} but uses a partial chain (which does not include the root
     * certificate).
     */
    @Test
    public void testExpiredRootChainPartial() throws Exception
    {
        // Setup fixture.
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( expiredRootChain[0].getSubjectX500Principal() );
        final X509Certificate[] chain = Arrays.copyOf( expiredRootChain, expiredRootChain.length - 1);

        // Execute system under test.
        CertPath result = null;
        CertPathBuilderException exception = null;

        try
        {
            result = trustManager.checkChainTrusted( selector, chain );
        }
        catch ( CertPathBuilderException ex)
        {
            exception = ex;
        }

        // Verify results
        if ( checkValidity )
        {
            Assert.assertNotNull( "Certificate validity is enforced. Validation should have thrown an exception.", exception );
        }
        else
        {
            Assert.assertNotNull( "Certificate validity is not checked. Validation should have succeeded.", result );
        }
    }

    /**
     * Verifies that self-signed certificates are accepted, but only when explicitly configured to do so.
     */
    @Test
    public void testSelfSigned() throws Exception
    {
        // Setup fixture.
        final X509Certificate[] chain = new X509Certificate[] { KeystoreTestUtils.generateSelfSignedCertificate() };
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( chain[ 0 ].getSubjectX500Principal() );

        // Execute system under test.
        CertPath result = null;
        CertPathBuilderException exception = null;

        try
        {
            result = trustManager.checkChainTrusted( selector, chain );
        }
        catch ( CertPathBuilderException ex)
        {
            exception = ex;
        }

        // Verify results
        if ( acceptSelfSigned )
        {
            Assert.assertNotNull( "Self-signed certificates are accepted. Validation should have succeeded.", result );
        }
        else
        {
            Assert.assertNotNull( "Self-signed certificates are not accepted. Validation should have thrown an exception.", exception );
        }
    }

    /**
     * Verifies that self-signed certificates that expired are accepted only when both self-signed certificates are
     * explicitly accepted, as well as validation is explictly skipped.
     */
    @Test
    public void testSelfSignedExpired() throws Exception
    {
        // Setup fixture.
        final X509Certificate[] chain = new X509Certificate[] { KeystoreTestUtils.generateExpiredSelfSignedCertificate() };
        final X509CertSelector selector = new X509CertSelector();
        selector.setSubject( chain[ 0 ].getSubjectX500Principal() );

        // Execute system under test.
        CertPath result = null;
        CertPathBuilderException exception = null;

        try
        {
            result = trustManager.checkChainTrusted( selector, chain );
        }
        catch ( CertPathBuilderException ex)
        {
            exception = ex;
        }

        // Verify results
        if ( acceptSelfSigned && !checkValidity)
        {
            Assert.assertNotNull( "Certificate validity is not checked, and self-signed certificates are accepted. Validation should have succeeded.", result );
        }
        else
        {
            final StringBuilder sb = new StringBuilder();
            if ( checkValidity )
            {
                sb.append( "Certificate validity is checked. " );
            }

            if ( !acceptSelfSigned )
            {
                sb.append( "Self-signed certificates are not accepted. " );
            }
            Assert.assertNotNull( sb.toString() + "Validation should have thrown an exception.", exception );
        }
    }
}
