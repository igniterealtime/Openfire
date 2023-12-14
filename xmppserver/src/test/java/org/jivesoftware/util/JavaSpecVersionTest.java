/*
 * Copyright (C) 2019-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JavaSpecVersionTest
{
    @Test
    public void test8notNewerThan8() throws Exception
    {
        // Setup fixture.
        final JavaSpecVersion eightA = new JavaSpecVersion( "1.8" );
        final JavaSpecVersion eightB = new JavaSpecVersion( "1.8" );

        // Execute system under test.
        final boolean result = eightA.isNewerThan( eightB );

        // Verify results.
        assertFalse( result );
    }

    @Test
    public void test8newerThan7() throws Exception
    {
        // Setup fixture.
        final JavaSpecVersion seven = new JavaSpecVersion( "1.7" );
        final JavaSpecVersion eight = new JavaSpecVersion( "1.8" );

        // Execute system under test.
        final boolean result = eight.isNewerThan( seven );

        // Verify results.
        assertTrue( result );
    }

    @Test
    public void test7notNewerThan8() throws Exception
    {
        // Setup fixture.
        final JavaSpecVersion seven = new JavaSpecVersion( "1.7" );
        final JavaSpecVersion eight = new JavaSpecVersion( "1.8" );

        // Execute system under test.
        final boolean result = seven.isNewerThan( eight );

        // Verify results.
        assertFalse( result );
    }

    @Test
    public void test11newerThan8() throws Exception
    {
        // Setup fixture.
        final JavaSpecVersion eight = new JavaSpecVersion( "1.8" );
        final JavaSpecVersion eleven = new JavaSpecVersion( "11" );

        // Execute system under test.
        final boolean result = eleven.isNewerThan( eight );

        // Verify results.
        assertTrue( result );
    }

    @Test
    public void test8notNewerThan11() throws Exception
    {
        // Setup fixture.
        final JavaSpecVersion eight = new JavaSpecVersion( "1.8" );
        final JavaSpecVersion eleven = new JavaSpecVersion( "11" );

        // Execute system under test.
        final boolean result = eight.isNewerThan( eleven );

        // Verify results.
        assertFalse( result );
    }

    @Test
    public void test17newerThan11() throws Exception
    {
        // Setup fixture.
        final JavaSpecVersion eleven = new JavaSpecVersion( "11" );
        final JavaSpecVersion seventeen = new JavaSpecVersion( "17" );

        // Execute system under test.
        final boolean result = seventeen.isNewerThan( eleven );

        // Verify results.
        assertTrue( result );
    }

    @Test
    public void test11notNewerThan17() throws Exception
    {
        // Setup fixture.
        final JavaSpecVersion eleven = new JavaSpecVersion( "11" );
        final JavaSpecVersion seventeen = new JavaSpecVersion( "17" );

        // Execute system under test.
        final boolean result = eleven.isNewerThan( seventeen );

        // Verify results.
        assertFalse( result );
    }
}
