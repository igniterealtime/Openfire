/*
 * Copyright (C) 2020-2023 Ignite Realtime Foundation. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test that verify the implementation of {@link ConnectionType}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ConnectionTypeTest
{

    /**
     * Verifies that the SOCKET_S2S ConnectionType returns the appropriate 'client orientated' value.
     */
    @Test
    public void testSOCKET_S2SConnectionTypeHasCorrectIsClientOriented()
    {
        assertFalse(ConnectionType.SOCKET_S2S.isClientOriented());
    }

    /**
     * Verifies that the SOCKET_C2S ConnectionType returns the appropriate 'client orientated' value.
     */
    @Test
    public void testSOCKET_C2SConnectionTypeHasCorrectIsClientOriented()
    {
        assertTrue(ConnectionType.SOCKET_C2S.isClientOriented());
    }

    /**
     * Verifies that the BOSH_C2S ConnectionType returns the appropriate 'client orientated' value.
     */
    @Test
    public void testBOSH_C2SConnectionTypeHasCorrectIsClientOriented()
    {
        assertTrue(ConnectionType.BOSH_C2S.isClientOriented());
    }
    
    /**
     * Verifies that the WEBADMIN ConnectionType returns the appropriate 'client orientated' value.
     */
    @Test
    public void testWEBADMINConnectionTypeHasCorrectIsClientOriented()
    {
        assertTrue(ConnectionType.WEBADMIN.isClientOriented());
    }

    /**
     * Verifies that the COMPONENT ConnectionType returns the appropriate 'client orientated' value.
     */
    @Test
    public void testCOMPONENTConnectionTypeHasCorrectIsClientOriented()
    {
        assertFalse(ConnectionType.COMPONENT.isClientOriented());
    }

    /**
     * Verifies that the CONNECTION_MANAGER ConnectionType returns the appropriate 'client orientated' value.
     */
    @Test
    public void testCONNECTION_MANAGERConnectionTypeHasCorrectIsClientOriented()
    {
        assertFalse(ConnectionType.CONNECTION_MANAGER.isClientOriented());
    }
}
