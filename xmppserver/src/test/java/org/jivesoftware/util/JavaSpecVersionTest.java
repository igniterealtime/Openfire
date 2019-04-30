package org.jivesoftware.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertFalse( false );
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

}
