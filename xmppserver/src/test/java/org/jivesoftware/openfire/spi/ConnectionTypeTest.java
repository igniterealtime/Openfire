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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Unit test that verify the implementation of {@link ConnectionType}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@RunWith(Parameterized.class)
public class ConnectionTypeTest
{

    /**
     * Define the expected outputs of ConnectionType.isClientOriented() for the given ConnectionType
     */
    @Parameterized.Parameters(name = "Verify that when ConnnectionType is \"{0}\" then isClientOriented returns \"{1}\"")
	public static Iterable<Object[]> data() {
		return Arrays.asList(new Object[][] {
            {ConnectionType.SOCKET_S2S, false},
            {ConnectionType.SOCKET_C2S, true},
            {ConnectionType.BOSH_C2S, true},
            {ConnectionType.WEBADMIN, true},
            {ConnectionType.COMPONENT, false},
            {ConnectionType.CONNECTION_MANAGER, false}
		});
	}

	private final ConnectionType connType;
    private final boolean expected;

	public ConnectionTypeTest(ConnectionType connType, boolean expected){
        this.connType = connType;
        this.expected = expected;
    }

    /**
     * Verifies that a given ConnectionType returns the appropriate 'client orientated' value.
     */
    @Test
    public void testConnectionTypeHasCorrectIsClientOriented() throws Exception
    {
        // Execute system under test.
        final boolean result = connType.isClientOriented();

        // Verify result.
        assertEquals(result, expected);
    }
}
