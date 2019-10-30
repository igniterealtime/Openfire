/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.container;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.InputStream;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Tests that verify the functionality of {@link PluginManager}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PluginManagerTest
{
    @Test
    public void testJARMagicBytes() throws Exception
    {
        // Setup test fixture.
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream( "hello.jar" );
        final BufferedInputStream in = new BufferedInputStream( inputStream );

        // Execute system under test
        final boolean result = PluginManager.validMagicNumbers( in );

        // Verify results.
        assertTrue( result );
    }

    @Test
    public void testTxtMagicBytes() throws Exception
    {
        // Setup test fixture.
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream( "fullchain.pem" );
        final BufferedInputStream in = new BufferedInputStream( inputStream );

        // Execute system under test
        final boolean result = PluginManager.validMagicNumbers( in );

        // Verify results.
        assertFalse( result );
    }
}
