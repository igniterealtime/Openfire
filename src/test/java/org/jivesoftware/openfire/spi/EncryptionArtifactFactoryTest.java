package org.jivesoftware.openfire.spi;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests that verify the functionality of {@link EncryptionArtifactFactory}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class EncryptionArtifactFactoryTest
{
    /**
     * Verifies that the collection of supported encryption protocols is not empty.
     */
    @Test
    public void testHasSupportedProtocols() throws Exception
    {
        // Setup fixture.
        // (not needed)

        // Execute system under test.
        final String[] result = EncryptionArtifactFactory.getSupportedProtocols();

        // Verify results.
        Assert.assertTrue( result.length > 0 );
    }

    /**
     * Verifies that the collection of supported cipher suites is not empty.
     */
    @Test
    public void testHasSupportedCipherSuites() throws Exception
    {
        // Setup fixture.
        // (not needed)

        // Execute system under test.
        final String[] result = EncryptionArtifactFactory.getSupportedCipherSuites();

        // Verify results.
        Assert.assertTrue( result.length > 0 );
    }
}
