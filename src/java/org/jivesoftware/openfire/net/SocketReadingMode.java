/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.net;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.StreamError;

import java.io.IOException;
import java.net.Socket;

/**
 * Abstract class for {@link BlockingReadingMode}.
 *
 * @author Gaston Dombiak
 */
abstract class SocketReadingMode {

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    protected static String CHARSET = "UTF-8";

    protected SocketReader socketReader;
    protected Socket socket;

    protected SocketReadingMode(Socket socket, SocketReader socketReader) {
        this.socket = socket;
        this.socketReader = socketReader;
    }

    /*
    * This method is invoked when client send data to the channel.
    */
    abstract void run();

    /**
     * Tries to secure the connection using TLS. If the connection is secured then reset
     * the parser to use the new secured reader. But if the connection failed to be secured
     * then send a <failure> stanza and close the connection.
     *
     * @return true if the connection was secured.
     */
    protected boolean negotiateTLS() {
        if (socketReader.connection.getTlsPolicy() == Connection.TLSPolicy.disabled) {
            // Set the not_authorized error
            StreamError error = new StreamError(StreamError.Condition.not_authorized);
            // Deliver stanza
            socketReader.connection.deliverRawText(error.toXML());
            // Close the underlying connection
            socketReader.connection.close();
            // Log a warning so that admins can track this case from the server side
            Log.warn("TLS requested by initiator when TLS was never offered by server. " +
                    "Closing connection : " + socketReader.connection);
            return false;
        }
        // Client requested to secure the connection using TLS. Negotiate TLS.
        try {
            socketReader.connection.startTLS(false, null);
        }
        catch (IOException e) {
            Log.error("Error while negotiating TLS: " + socketReader.connection, e);
            socketReader.connection.deliverRawText("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\">");
            socketReader.connection.close();
            return false;
        }
        return true;
    }

    /**
     * TLS negotiation was successful so open a new stream and offer the new stream features.
     * The new stream features will include available SASL mechanisms and specific features
     * depending on the session type such as auth for Non-SASL authentication and register
     * for in-band registration.
     */
    protected void tlsNegotiated() throws XmlPullParserException, IOException {
        // Offer stream features including SASL Mechanisms
        StringBuilder sb = new StringBuilder(620);
        sb.append(geStreamHeader());
        sb.append("<stream:features>");
        // Include available SASL Mechanisms
        sb.append(SASLAuthentication.getSASLMechanisms(socketReader.session));
        // Include specific features such as auth and register for client sessions
        String specificFeatures = socketReader.session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        socketReader.connection.deliverRawText(sb.toString());
    }

    protected boolean authenticateClient(Element doc) throws DocumentException, IOException,
            XmlPullParserException {
        // Ensure that connection was secured if TLS was required
        if (socketReader.connection.getTlsPolicy() == Connection.TLSPolicy.required &&
                !socketReader.connection.isSecure()) {
            socketReader.closeNeverSecuredConnection();
            return false;
        }

        boolean isComplete = false;
        boolean success = false;
        while (!isComplete) {
            SASLAuthentication.Status status = SASLAuthentication.handle(socketReader.session, doc);
            if (status == SASLAuthentication.Status.needResponse) {
                // Get the next answer since we are not done yet
                doc = socketReader.reader.parseDocument().getRootElement();
                if (doc == null) {
                    // Nothing was read because the connection was closed or dropped
                    isComplete = true;
                }
            }
            else {
                isComplete = true;
                success = status == SASLAuthentication.Status.authenticated;
            }
        }
        return success;
    }

    /**
     * After SASL authentication was successful we should open a new stream and offer
     * new stream features such as resource binding and session establishment. Notice that
     * resource binding and session establishment should only be offered to clients (i.e. not
     * to servers or external components)
     */
    protected void saslSuccessful() throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder(420);
        sb.append(geStreamHeader());
        sb.append("<stream:features>");

        // Include specific features such as resource binding and session establishment
        // for client sessions
        String specificFeatures = socketReader.session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        socketReader.connection.deliverRawText(sb.toString());
    }

    /**
     * Start using compression but first check if the connection can and should use compression.
     * The connection will be closed if the requested method is not supported, if the connection
     * is already using compression or if client requested to use compression but this feature
     * is disabled.
     *
     * @param doc the element sent by the client requesting compression. Compression method is
     *        included.
     * @return true if it was possible to use compression.
     * @throws IOException if an error occurs while starting using compression.
     */
    protected boolean compressClient(Element doc) throws IOException, XmlPullParserException {
        String error = null;
        if (socketReader.connection.getCompressionPolicy() == Connection.CompressionPolicy.disabled) {
            // Client requested compression but this feature is disabled
            error = "<failure xmlns='http://jabber.org/protocol/compress'><setup-failed/></failure>";
            // Log a warning so that admins can track this case from the server side
            Log.warn("Client requested compression while compression is disabled. Closing " +
                    "connection : " + socketReader.connection);
        }
        else if (socketReader.connection.isCompressed()) {
            // Client requested compression but connection is already compressed
            error = "<failure xmlns='http://jabber.org/protocol/compress'><setup-failed/></failure>";
            // Log a warning so that admins can track this case from the server side
            Log.warn("Client requested compression and connection is already compressed. Closing " +
                    "connection : " + socketReader.connection);
        }
        else {
            // Check that the requested method is supported
            String method = doc.elementText("method");
            if (!"zlib".equals(method)) {
                error = "<failure xmlns='http://jabber.org/protocol/compress'><unsupported-method/></failure>";
                // Log a warning so that admins can track this case from the server side
                Log.warn("Requested compression method is not supported: " + method +
                        ". Closing connection : " + socketReader.connection);
            }
        }

        if (error != null) {
            // Deliver stanza
            socketReader.connection.deliverRawText(error);
            return false;
        }
        else {
            // Start using compression for incoming traffic
            socketReader.connection.addCompression();

            // Indicate client that he can proceed and compress the socket
            socketReader.connection.deliverRawText("<compressed xmlns='http://jabber.org/protocol/compress'/>");

            // Start using compression for outgoing traffic
            socketReader.connection.startCompression();
            return true;
        }
    }

    /**
     * After compression was successful we should open a new stream and offer
     * new stream features such as resource binding and session establishment. Notice that
     * resource binding and session establishment should only be offered to clients (i.e. not
     * to servers or external components)
     */
    protected void compressionSuccessful() throws XmlPullParserException, IOException
    {
        StringBuilder sb = new StringBuilder(340);
        sb.append(geStreamHeader());
        sb.append("<stream:features>");
        // Include SASL mechanisms only if client has not been authenticated
        if (socketReader.session.getStatus() != Session.STATUS_AUTHENTICATED) {
            // Include available SASL Mechanisms
            sb.append(SASLAuthentication.getSASLMechanisms(socketReader.session));
        }
        // Include specific features such as resource binding and session establishment
        // for client sessions
        String specificFeatures = socketReader.session.getAvailableStreamFeatures();
        if (specificFeatures != null)
        {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        socketReader.connection.deliverRawText(sb.toString());
    }

    private String geStreamHeader() {
        StringBuilder sb = new StringBuilder(200);
        sb.append("<?xml version='1.0' encoding='");
        sb.append(CHARSET);
        sb.append("'?>");
        if (socketReader.connection.isFlashClient()) {
            sb.append("<flash:stream xmlns:flash=\"http://www.jabber.com/streams/flash\" ");
        } else {
            sb.append("<stream:stream ");
        }
        sb.append("xmlns:stream=\"http://etherx.jabber.org/streams\" xmlns=\"");
        sb.append(socketReader.getNamespace());
        sb.append("\" from=\"");
        sb.append(socketReader.session.getServerName());
        sb.append("\" id=\"");
        sb.append(socketReader.session.getStreamID().toString());
        sb.append("\" xml:lang=\"");
        sb.append(socketReader.connection.getLanguage());
        sb.append("\" version=\"");
        sb.append(Session.MAJOR_VERSION).append(".").append(Session.MINOR_VERSION);
        sb.append("\">");
        return sb.toString();
    }

}
