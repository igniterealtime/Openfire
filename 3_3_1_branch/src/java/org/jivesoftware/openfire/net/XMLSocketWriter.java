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
