/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit test that verify the implementation of {@link ConnectionType}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ConnectionTypeTest
{
    /**
     * Verifies that ConnectionType.SOCKET_S2S is not determined to be 'client orientated'.
     */
    @Test
    public void testSocketS2SIsClientOrientated() throws Exception
    {
        // Setup test fixture.
        final ConnectionType input = ConnectionType.SOCKET_S2S;

        // Execute system under test.
        final boolean result = input.isClientOriented();

        // Verify result.
        assertFalse(result);
    }

    /**
     * Verifies that ConnectionType.SOCKET_C2S is determined to be 'client orientated'.
     */
    @Test
    public void testSocketc2SIsClientOrientated() throws Exception
    {
        // Setup test fixture.
        final ConnectionType input = ConnectionType.SOCKET_C2S;

        // Execute system under test.
        final boolean result = input.isClientOriented();

        // Verify result.
        assertTrue(result);
    }

    /**
     * Verifies that ConnectionType.BOSH_C2S is not determined to be 'client orientated'.
     */
    @Test
    public void testBoshC2SIsClientOrientated() throws Exception
    {
        // Setup test fixture.
        final ConnectionType input = ConnectionType.BOSH_C2S;

        // Execute system under test.
        final boolean result = input.isClientOriented();

        // Verify result.
        assertTrue(result);
    }

    /**
     * Verifies that ConnectionType.WEBADMIN is not determined to be 'client orientated'.
     */
    @Test
    public void testWebadminIsClientOrientated() throws Exception
    {
        // Setup test fixture.
        final ConnectionType input = ConnectionType.WEBADMIN;

        // Execute system under test.
        final boolean result = input.isClientOriented();

        // Verify result.
        assertTrue(result);
    }

    /**
     * Verifies that ConnectionType.COMPONENT is not determined to be 'client orientated'.
     */
    @Test
    public void testComponentIsClientOrientated() throws Exception
    {
        // Setup test fixture.
        final ConnectionType input = ConnectionType.COMPONENT;

        // Execute system under test.
        final boolean result = input.isClientOriented();

        // Verify result.
        assertFalse(result);
    }

    /**
     * Verifies that ConnectionType.CONNECTION_MANAGER is not determined to be 'client orientated'.
     */
    @Test
    public void testConnectionManagerIsClientOrientated() throws Exception
    {
        // Setup test fixture.
        final ConnectionType input = ConnectionType.CONNECTION_MANAGER;

        // Execute system under test.
        final boolean result = input.isClientOriented();

        // Verify result.
        assertFalse(result);
    }
}
