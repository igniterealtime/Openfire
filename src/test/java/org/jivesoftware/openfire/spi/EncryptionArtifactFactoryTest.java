package org.jivesoftware.openfire.spi;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;

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
        final Collection<String> result = EncryptionArtifactFactory.getSupportedProtocols();

        // Verify results.
        Assert.assertFalse( result.isEmpty() );
    }

    /**
     * Verifies that the collection of default encryption protocols is not empty.
     */
    @Test
    public void testHasDefaultProtocols() throws Exception
    {
        // Setup fixture.
        // (not needed)

        // Execute system under test.
        final Collection<String> result = EncryptionArtifactFactory.getDefaultProtocols();

        // Verify results.
        Assert.assertFalse( result.isEmpty() );
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
        final Collection<String> result = EncryptionArtifactFactory.getSupportedCipherSuites();

        // Verify results.
        Assert.assertFalse( result.isEmpty() );
    }

    /**
     * Verifies that the collection of default cipher suites is not empty.
     */
    @Test
    public void testHasDefaultCipherSuites() throws Exception
    {
        // Setup fixture.
        // (not needed)

        // Execute system under test.
        final Collection<String> result = EncryptionArtifactFactory.getDefaultCipherSuites();

        // Verify results.
        Assert.assertFalse( result.isEmpty() );
    }
}
