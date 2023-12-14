/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.*;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.StreamError;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * Abstract class for {@link BlockingReadingMode}.
 *
 * @author Gaston Dombiak
 * @deprecated Old, pre NIO / MINA code. Should not be used as Netty offers better performance. Currently only in use for server dialback.
 */
abstract class SocketReadingMode {

    private static final Logger Log = LoggerFactory.getLogger(SocketReadingMode.class);

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
     * Tries to encrypt the connection using TLS. If the connection is encrypted then reset
     * the parser to use the new encrypted reader. But if the connection failed to be encrypted
     * then send a <failure> stanza and close the connection.
     *
     * @return true if the connection was encryped.
     */
    protected boolean negotiateTLS() {
        if (socketReader.connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.disabled) {
            // Send a not_authorized error and close the underlying connection
            socketReader.connection.close(new StreamError(StreamError.Condition.not_authorized, "A request to negotiate TLS is denied, as TLS has been disabled by configuration."));
            // Log a warning so that admins can track this case from the server side
            Log.warn("TLS requested by initiator when TLS was never offered by server. Closing connection: {}", socketReader.connection);
            return false;
        }
        // Client requested to encrypt the connection using TLS. Negotiate TLS.
        try {
            // This code is only used for s2s
            socketReader.connection.startTLS(false, false);
        }
        catch (SSLHandshakeException e) {
            // RFC6120, section 5.4.3.2 "STARTTLS Failure" - close the socket *without* sending any more data (<failure/> nor </stream>).
            Log.info( "STARTTLS negotiation (with: {}) failed.", socketReader.connection, e );
            socketReader.connection.forceClose();
            return false;
        }
        catch (IOException | RuntimeException e) {
            // RFC6120, section 5.4.2.2 "Failure case" - Send a <failure/> element, then close the socket.
            Log.warn( "An exception occurred while performing STARTTLS negotiation (with: {})", socketReader.connection, e);
            socketReader.connection.deliverRawText("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");
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
    protected void tlsNegotiated() throws XmlPullParserException, IOException
    {
        final Document document = getStreamHeader();

        // Offer stream features including SASL Mechanisms
        final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
        final Element mechanisms = SASLAuthentication.getSASLMechanisms(socketReader.session);
        if (mechanisms != null) {
            features.add(mechanisms);
        }
        final List<Element> specificFeatures = socketReader.session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            for (final Element feature : specificFeatures) {
                features.add(feature);
            }
        }
        document.getRootElement().add(features);

        socketReader.connection.deliverRawText(StringUtils.asUnclosedStream(document));
    }

    protected boolean authenticateClient(Element doc) throws DocumentException, IOException,
            XmlPullParserException {
        // Ensure that connection was encrypted if TLS was required
        if (socketReader.connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required &&
                !socketReader.connection.isEncrypted()) {
            socketReader.closeNeverEncryptedConnection();
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
    protected void saslSuccessful() throws XmlPullParserException, IOException
    {
        final Document document = getStreamHeader();
        final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));

        // Include specific features such as resource binding and session establishment for client sessions
        final List<Element> specificFeatures = socketReader.session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            for (final Element feature : specificFeatures) {
                features.add(feature);
            }
        }
        document.getRootElement().add(features);

        socketReader.connection.deliverRawText(StringUtils.asUnclosedStream(document));
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
        Element error = null;
        if (socketReader.connection.getConfiguration().getCompressionPolicy() == Connection.CompressionPolicy.disabled)
        {
            // Client requested compression but this feature is disabled
            error = DocumentHelper.createElement(QName.get("failure", "http://jabber.org/protocol/compress"));
            error.addElement("setup-failed");

            // Log a warning so that admins can track this case from the server side
            Log.warn("Client requested compression while compression is disabled. Closing connection : {}", socketReader.connection);
        }
        else if (socketReader.connection.isCompressed())
        {
            // Client requested compression but connection is already compressed
            error = DocumentHelper.createElement(QName.get("failure", "http://jabber.org/protocol/compress"));
            error.addElement("setup-failed");

            // Log a warning so that admins can track this case from the server side
            Log.warn("Client requested compression and connection is already compressed. Closing connection : {}", socketReader.connection);
        }
        else
        {
            // Check that the requested method is supported
            String method = doc.elementText("method");
            if (!"zlib".equals(method))
            {
                error = DocumentHelper.createElement(QName.get("failure", "http://jabber.org/protocol/compress"));
                error.addElement("unsupported-method");

                // Log a warning so that admins can track this case from the server side
                Log.warn("Requested compression method is not supported: {}. Closing connection : {}", method, socketReader.connection);
            }
        }

        if (error != null) {
            // Deliver stanza
            socketReader.connection.deliverRawText(error.asXML());
            return false;
        }
        else {
            // Start using compression for incoming traffic
            socketReader.connection.addCompression();

            // Indicate client that he can proceed and compress the socket
            socketReader.connection.deliverRawText(DocumentHelper.createElement(QName.get("compressed", "http://jabber.org/protocol/compress")).asXML());

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
        final Document document = getStreamHeader();
        final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
        document.getRootElement().add(features);

        // Include SASL mechanisms only if client has not been authenticated
        if (!socketReader.session.isAuthenticated()) {
            // Include available SASL Mechanisms
            final Element saslMechanisms = SASLAuthentication.getSASLMechanisms(socketReader.session);
            if (saslMechanisms != null) {
                features.add(saslMechanisms);
            }
        }
        // Include specific features such as resource binding and session establishment for client sessions.
        final List<Element> specificFeatures = socketReader.session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            for (final Element feature : specificFeatures) {
                features.add(feature);
            }
        }

        socketReader.connection.deliverRawText(StringUtils.asUnclosedStream(document));
    }

    private Document getStreamHeader()
    {
        final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
        final Document document = DocumentHelper.createDocument(stream);
        document.setXMLEncoding(CHARSET);
        stream.add(socketReader.getNamespace());
        stream.addAttribute("from", socketReader.session.getServerName());
        stream.addAttribute("id", socketReader.session.getStreamID().toString());
        stream.addAttribute(QName.get("lang", Namespace.XML_NAMESPACE), socketReader.session.getLanguage().toLanguageTag());
        stream.addAttribute("version", Session.MAJOR_VERSION + "." + Session.MINOR_VERSION);

        return document;
    }
}
