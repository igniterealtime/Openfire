/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.http;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.TimerTask;

/**
 * A task that, periodically, updates the 'last modified' date of all files in the Jetty 'tmp' directories. This
 * prevents operating systems from removing files that it thinks are unused.
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1534">OF-1534</a>
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class TempFileToucherTask extends TimerTask
{
    private final static Logger Log = LoggerFactory.getLogger( TempFileToucherTask.class );

    private final Server server;

    public TempFileToucherTask( final Server server )
    {
        this.server = server;
    }

    @Override
    public void run()
    {
        final FileTime now = FileTime.fromMillis( System.currentTimeMillis() );
        for ( final Handler handler : this.server.getChildHandlersByClass( WebAppContext.class ) )
        {
            final File tempDirectory = ((WebAppContext) handler).getTempDirectory();
            try
            {
                Log.debug( "Updating the last modified timestamp of content in Jetty's temporary storage in: {}", tempDirectory);
                Files.walk( tempDirectory.toPath() )
                    .forEach( f -> {
                        try
                        {
                            Log.trace( "Setting the last modified timestamp of file '{}' in Jetty's temporary storage to: {}", f, now);
                            Files.setLastModifiedTime( f, now );
                        }
                        catch ( IOException e )
                        {
                            Log.warn( "An exception occurred while trying to update the last modified timestamp of content in Jetty's temporary storage in: {}", f, e );
                        }
                    } );
            }
            catch ( IOException e )
            {
                Log.warn( "An exception occurred while trying to update the last modified timestamp of content in Jetty's temporary storage in: {}", tempDirectory, e );
            }
        }
    }
}
