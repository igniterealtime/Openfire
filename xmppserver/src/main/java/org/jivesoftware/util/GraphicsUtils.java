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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utilities for working with graphics-related data.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class GraphicsUtils
{
    private static final Logger Log = LoggerFactory.getLogger( GraphicsUtils.class );

    /**
     * Checks if the provided input stream represents an image.
     *
     * @param stream The data to be parsed. Cannot be null.
     * @return true if the provided data is successfully identified as an image, otherwise false.
     */
    public static boolean isImage( final InputStream stream )
    {
        try
        {
            // This attempts to read the bytes as an image, returning null if it cannot parse the bytes as an image.
            return null != ImageIO.read( stream );
        }
        catch ( IOException e )
        {
            Log.debug( "An exception occurred while determining if data represents an image.", e );
            return false;
        }
    }

    /**
     * Checks if the provided byte array represents an image.
     *
     * @param bytes The data to be parsed. Cannot be null.
     * @return true if the provided data is successfully identified as an image, otherwise false.
     */
    public static boolean isImage( final byte[] bytes )
    {
        if ( bytes == null )
        {
            throw new IllegalArgumentException( "Argument 'bytes' cannot be null." );
        }
        return isImage( new ByteArrayInputStream( bytes ) );
    }
}
