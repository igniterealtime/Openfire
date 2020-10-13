/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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

import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link GraphicsUtils}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class GraphicsUtilsTest
{
    /**
     * Verifies that {@link GraphicsUtils#isImage(InputStream)} correctly identifies an image.
     */
    @Test
    public void testIsImageWithImage() throws Exception
    {
        // Setup test fixture.
        final InputStream input = getClass().getResourceAsStream( "/images/ant_logo_large.gif" );

        // Execute system under test.
        final boolean result = GraphicsUtils.isImage( input );

        // Verify result.
        assertTrue( result );
    }

    /**
     * Verifies that {@link GraphicsUtils#isImage(InputStream)} correctly identifies a favicon.
     */
    @Test
    public void testIsImageWithFavicon() throws Exception
    {
        // Setup test fixture.
        final InputStream input = getClass().getResourceAsStream( "/favicon.ico" );

        // Execute system under test.
        final boolean result = GraphicsUtils.isImage( input );

        // Verify result.
        assertTrue( result );
    }

    /**
     * Verifies that {@link GraphicsUtils#isImage(InputStream)} rejects data that's not an image.
     */
    @Test
    public void testIsImageWithNonImage() throws Exception
    {
        // Setup test fixture.
        final InputStream input = getClass().getResourceAsStream( "/privatekey.pem" );

        // Execute system under test.
        final boolean result = GraphicsUtils.isImage( input );

        // Verify result.
        assertFalse( result );
    }
}
