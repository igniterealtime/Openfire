/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.spi;

import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
        assertFalse( result.isEmpty() );
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
        assertFalse( result.isEmpty() );
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
        assertFalse( result.isEmpty() );
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
        assertFalse( result.isEmpty() );
    }
}
