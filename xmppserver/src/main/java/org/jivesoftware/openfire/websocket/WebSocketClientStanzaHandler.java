/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.websocket;

import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ClientStanzaHandler;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.StreamErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.StreamError;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * A {@link org.jivesoftware.openfire.net.StanzaHandler} that is able to process the specific framing that is used by
 * clients connecting via websockets.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7395.html">RFC 7395:  An Extensible Messaging and Presence Protocol (XMPP) Subprotocol for WebSocket</a>
 */
public class WebSocketClientStanzaHandler extends ClientStanzaHandler
{
    private static final Logger Log = LoggerFactory.getLogger(WebSocketClientStanzaHandler.class);

    public static final String STREAM_HEADER = "open";

    public static final String STREAM_FOOTER = "close";

    public static final String FRAMING_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-framing";

    public WebSocketClientStanzaHandler(final PacketRouter router, final WebSocketConnection connection)
    {
        super(router, connection);
    }

    @Override
    protected void initiateSession(String stanza, XMPPPacketReader reader) throws Exception
    {
        // Found a stream:stream tag...
        if (!sessionCreated) {
            sessionCreated = true;
            MXParser parser = reader.getXPPParser();
            parser.setInput(new StringReader(stanza));
            createSession(parser);
        }
        else if (startedSASL && saslStatus == SASLAuthentication.Status.authenticated) {
            startedSASL = false;
            saslSuccessful();
        }
    }

    @Override
    protected void createSession(XmlPullParser xpp) throws XmlPullParserException, IOException {
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }

        final String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        String host = xpp.getAttributeValue("", "to");

        try {
            // Check that the TO attribute of the stream header matches the server name or a valid
            // subdomain. If the value of the 'to' attribute is not valid then return a host-unknown
            // error and close the underlying connection.
            if (validateHost() && isHostUnknown(host)) {
                throw new StreamErrorException(StreamError.Condition.host_unknown, "Incorrect hostname in stream header: " + host);
            }

            if (!STREAM_HEADER.equals(xpp.getName())) {
                throw new StreamErrorException(StreamError.Condition.unsupported_stanza_type, "Incorrect stream header: " + xpp.getName());
            }

            // Validate the stream namespace (https://tools.ietf.org/html/rfc7395#section-3.3.2)
            if (!FRAMING_NAMESPACE.equals(xpp.getNamespace())) {
                throw new StreamErrorException(StreamError.Condition.invalid_namespace, "Invalid namespace in stream header: " + xpp.getNamespace());
            }

            // Create the correct session based on the sent namespace. At this point the server
            // may offer the client to encrypt the connection. If the client decides to encrypt
            // the connection then a <starttls> stanza should be received
            createSession(serverName, xpp, connection);
        }
        catch (final StreamErrorException ex) {
            Log.warn("Failed to create a session. Closing connection: {}", connection, ex);
            connection.deliverRawText(ex.getStreamError().toXML());
            connection.deliverRawText("<close xmlns='" + FRAMING_NAMESPACE + "'/>");
            connection.close();
        }
    }

    @Override
    protected void createSession(String serverName, XmlPullParser xpp, Connection connection) throws XmlPullParserException
    {
        // This largely copies LocalClientSession#createSession, with modifications that are specific to websockets.
        if (!LocalClientSession.isAllowed(connection))
        {
            // Client cannot connect from this IP address so end the stream and TCP connection.
            String hostAddress = "Unknown";
            try {
                hostAddress = connection.getHostAddress();
            } catch (UnknownHostException e) {
                // Do nothing
            }

            Log.debug("Closed connection to client attempting to connect from: {}", hostAddress);
            // Include the not-authorized error in the response and close the underlying connection.
            connection.close(new StreamError(StreamError.Condition.not_authorized));
            return;
        }

        // Retrieve list of namespaces declared in current element (OF-2556)
        connection.setAdditionalNamespaces(XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(xpp));

        final Locale language = Session.detectLanguage(xpp);

        connection.setXMPPVersion(1, 0);

        // Create a ClientSession for this user.
        session = SessionManager.getInstance().createClientSession(connection, language);
        session.setSessionData("ws", Boolean.TRUE);

        openStream();
        sendStreamFeatures();
    }

    private void openStream() {
        session.incrementClientPacketCount();
        connection.deliverRawText(withoutDeclaration(getStreamHeader()));
    }

    private void sendStreamFeatures() {
        final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
        if (saslStatus != SASLAuthentication.Status.authenticated) {
            // Include available SASL Mechanisms
            final Element saslMechanisms = SASLAuthentication.getSASLMechanisms(session);
            if (saslMechanisms != null) {
                features.add(saslMechanisms);
            }
        }
        // Include Stream features
        final List<Element> specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            for (final Element feature : specificFeatures) {
                features.add(feature);
            }
        }
        connection.deliverRawText(features.asXML());
    }

    @Override
    protected Document getStreamHeader() {
        final Element open = DocumentHelper.createElement(QName.get("open", FRAMING_NAMESPACE));
        final Document document = DocumentHelper.createDocument(open);
        document.setXMLEncoding(StandardCharsets.UTF_8.toString());
        open.addAttribute("from", XMPPServer.getInstance().getServerInfo().getXMPPDomain());
        open.addAttribute("id", session.getStreamID().toString());
        open.addAttribute(QName.get("lang", Namespace.XML_NAMESPACE), session.getLanguage().toLanguageTag());
        open.addAttribute("version", "1.0");

        return document;
    }

    /**
     * After SASL authentication was successful we should open a new stream and offer
     * new stream features such as resource binding and session establishment. Notice that
     * resource binding and session establishment should only be offered to clients (i.e. not
     * to servers or external components)
     */
    @Override
    protected void saslSuccessful() {
        // When using websockets, send the stream header in a separate websocket frame!
        connection.deliverRawText(withoutDeclaration(getStreamHeader()));
        sendStreamFeatures();
    }

    protected boolean isStartOfStream(final String xml) {
        return xml.startsWith("<" + STREAM_HEADER);
    }

    protected boolean isEndOfStream(final String xml) {
        return xml.startsWith("<" + STREAM_FOOTER);
    }

    public static String withoutDeclaration(final Document document) {
        try {
            StringWriter out = new StringWriter();
            OutputFormat format = new OutputFormat();
            format.setSuppressDeclaration(true);
            format.setExpandEmptyElements(false);
            XMLWriter writer = new XMLWriter(out, format);

            writer.write(document);
            writer.flush();

            return out.toString();
        } catch (IOException e) {
            throw new RuntimeException("IOException while generating "
                + "textual representation: " + e.getMessage());
        }
    }
}
