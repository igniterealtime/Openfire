/**
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.XMLWriter;

import java.io.IOException;
import java.io.Writer;

/**
 * XMLWriter whose writer is actually sending data on a socket connection. Since sending data over
 * a socket may have particular type of errors this class tries to deal with those errors.
 */
public class XMLSocketWriter extends XMLWriter {

    private SocketConnection connection;

    public XMLSocketWriter(Writer writer, SocketConnection connection) {
        super( writer, DEFAULT_FORMAT );
        this.connection = connection;
    }

    /**
     * Flushes the underlying writer making sure that if the connection is dead then the thread
     * that is flushing does not end up in an endless wait.
     *
     * @throws IOException if an I/O error occurs while flushing the writer.
     */
    @Override
	public void flush() throws IOException {
        // Register that we have started sending data
        connection.writeStarted();
        try {
            super.flush();
        }
        finally {
            // Register that we have finished sending data
            connection.writeFinished();
        }
    }
}
