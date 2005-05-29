package org.jivesoftware.messenger.net;

import org.jivesoftware.util.XMLWriter;

import java.net.Socket;
import java.io.Writer;
import java.io.IOException;

/**
 * XMLWriter whose writer is actually sending data on a socket connection. Since sending data over
 * a socket may have particular type of errors this class tries to deal with those errors.
 */
public class XMLSocketWriter extends XMLWriter {

    private Socket socket;

    public XMLSocketWriter(Writer writer, Socket socket) {
        super( writer, DEFAULT_FORMAT );
        this.socket = socket;
    }

    /**
     * Flushes the underlying writer making sure that if the connection is dead then the thread
     * that is flushing does not end up in an endless wait.
     *
     * @throws IOException if an I/O error occurs while flushing the writer.
     */
    public void flush() throws IOException {
        // Register that we have started sending data
        SocketSendingTracker.getInstance().socketStartedSending(socket);
        try {
            super.flush();
        }
        finally {
            // Register that we have finished sending data
            SocketSendingTracker.getInstance().socketFinishedSending(socket);
        }
    }
}
