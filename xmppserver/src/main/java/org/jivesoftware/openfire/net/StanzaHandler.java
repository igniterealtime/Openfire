/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.StreamIDFactory;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.jivesoftware.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A StanzaHandler is the main responsible for handling incoming stanzas. Some stanzas like startTLS
 * are totally managed by this class. The rest of the stanzas are just forwarded to the router.
 *
 * @author Gaston Dombiak
 */
public abstract class StanzaHandler {

    private static final Logger Log = LoggerFactory.getLogger(StanzaHandler.class);

    public static final SystemProperty<Boolean> PROPERTY_OVERWRITE_EMPTY_TO = SystemProperty.Builder.ofType( Boolean.class )
        .setKey( "xmpp.server.rewrite.replace-missing-to" )
        .setDefaultValue( true )
        .setDynamic( true )
        .build();

    /**
     * A factory that generates random stream IDs
     */
    private static final StreamIDFactory STREAM_ID_FACTORY = new BasicStreamIDFactory();

    protected Connection connection;

    // DANIELE: Indicate if a session is already created
    protected boolean sessionCreated = false;

    // Flag that indicates that the client requested to use TLS and TLS has been negotiated. Once the
    // client sent a new initial stream header the value will return to false.
    protected boolean startedTLS = false;
    // Flag that indicates that the client requested to be authenticated. Once the
    // authentication process is over the value will return to false.
    protected boolean startedSASL = false;
    /**
     * SASL status based on the last SASL interaction
     */
    protected SASLAuthentication.Status saslStatus;

    // DANIELE: Indicate if a stream:stream is arrived to complete compression
    protected boolean waitingCompressionACK = false;

    /**
     * Session associated with the socket reader.
     */
    protected LocalSession session;

    /**
     * Router used to route incoming packets to the correct channels.
     */
    protected PacketRouter router;

    /**
     * Creates a dedicated reader for a socket.
     *
     * @param router     the router for sending packets that were read.
     * @param connection the connection being read.
     */
    public StanzaHandler(PacketRouter router, Connection connection) {
        this.router = router;
        this.connection = connection;
    }

    public void setSession(LocalSession session) {
        this.session = session;
    }

    public void process(String stanza, XMPPPacketReader reader) throws Exception {
        if (isStartOfStream(stanza) || !sessionCreated) {
            initiateSession(stanza, reader);
        } else {
            processStanza(stanza, reader);
        }
    }

    protected void initiateSession(String stanza, XMPPPacketReader reader) throws Exception
    {
        boolean initialStream = isStartOfStream(stanza);
        if (!initialStream) {
            // Ignore <?xml version="1.0"?>
            return;
        }
        // Found a stream:stream tag...
        if (!sessionCreated) {
            sessionCreated = true;
            MXParser parser = reader.getXPPParser();
            parser.setInput(new StringReader(stanza));
            createSession(parser);
        }
        else if (startedTLS) {
            MXParser parser = reader.getXPPParser();
            parser.setInput(new StringReader(stanza));
            tlsNegotiated(parser);
            startedTLS = false;
        }
        else if (startedSASL && saslStatus == SASLAuthentication.Status.authenticated) {
            startedSASL = false;
            saslSuccessful();
        }
        else if (waitingCompressionACK) {
            waitingCompressionACK = false;
            compressionSuccessful();
        }
    }

    protected void processStanza(String stanza, XMPPPacketReader reader) throws Exception {
        // Verify if end of stream was requested
        if (isEndOfStream(stanza)) {
            if (session != null) {
                session.getStreamManager().formalClose();
                Log.debug( "Closing session as an end-of-stream was received: {}", session );
                session.close();
            }
            return;
        }
        // Ignore <?xml version="1.0"?> stanzas sent by clients
        if (stanza.startsWith("<?xml")) {
            return;
        }

        Element doc;
        final Set<Namespace> namespaces = connection.getAdditionalNamespaces();
        if (namespaces.isEmpty()) {
            doc = reader.read(new StringReader(stanza)).getRootElement();
        } else {
            // When the peer defined namespace prefixes on the 'stream' element, other than the default namespaces, then
            // these need to be 'put back' for the parser to be able to parse any data that is prefixed with those. The
            // only known case occurring 'in the wild' for this is Server Dialback, but it's valid XML / XMPP regardless.
            // Reestablishing those prefixes is achieved by wrapping the data-to-be-parsed in a dummy root element on which
            // the prefixes are defined. After the data has been parsed, the dummy root element is discarded. See OF-2556.
            Log.trace("Connection '{}' defined namespace prefixes on its original 'stream' element: {}", connection.getAddress(), namespaces.stream().map(Namespace::asXML).collect(Collectors.joining(", ")));
            final StringBuilder sb = new StringBuilder();
            sb.append("<stream:stream");
            namespaces.forEach(namespace -> sb.append(" ").append(namespace.asXML()));
            if (namespaces.stream().noneMatch(namespace -> namespace.getPrefix().equals("stream"))) {
                sb.append(" ").append(Namespace.get("stream", "http://etherx.jabber.org/streams").asXML());
            }
            sb.append(">").append(stanza).append("</stream:stream>");

            doc = reader.read(new StringReader(sb.toString())).getRootElement().elementIterator().next();
            doc.detach();
        }

        if (doc == null) {
            // No document found.
            return;
        }
        String tag = doc.getName();
        if ("starttls".equals(tag)) {
            // Negotiate TLS
            if (negotiateTLS()) {
                startedTLS = true;
            }
            else {
                connection.close();
                session = null;
            }
        }
        else if ("auth".equals(tag)) {
            // User is trying to authenticate using SASL
            startedSASL = true;
            // Process authentication stanza
            saslStatus = SASLAuthentication.handle(session, doc);
        } else if (startedSASL && "response".equals(tag) || "abort".equals(tag)) {
            // User is responding to SASL challenge. Process response
            saslStatus = SASLAuthentication.handle(session, doc);
        }
        else if ("compress".equals(tag)) {
            // Client is trying to initiate compression
            if (compressClient(doc)) {
                // Compression was successful so open a new stream and offer
                // resource binding and session establishment (to client sessions only)
                waitingCompressionACK = true;
            }
        } else if (isStreamManagementStanza(doc)) {
            Log.trace("Client '{}' is sending stream management stanza.", session.getAddress());
            session.getStreamManager().process( doc );
        }
        else {
            process(doc);
        }
    }

    private void process(Element doc) throws UnauthorizedException {
        if (doc == null) {
            return;
        }

        // Ensure that connection was encrypted if TLS was required
        if (connection.isInitialized() && connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required && !connection.isEncrypted()) {
            closeNeverEncryptedConnection();
            return;
        }

        String tag = doc.getName();
        if ("error".equals(tag)) {
            Log.info("The stream is being closed by the peer ('{}'), which sent this stream error: {}", session.getAddress(), doc.asXML());
            session.close();
        }
        else if ("message".equals(tag)) {
            Message packet;
            try {
                packet = new Message(doc, !validateJIDs());
            }
            catch (IllegalArgumentException e) {
                Log.debug("Rejecting stanza from '{}'. JID malformed", session.getAddress(), e);
                // The original packet contains a malformed JID so answer with an error.
                Message reply = new Message();
                reply.setID(doc.attributeValue("id"));
                reply.setTo(session.getAddress());
                reply.getElement().addAttribute("from", doc.attributeValue("to"));
                reply.setError(PacketError.Condition.jid_malformed);
                session.process(reply);
                return;
            }
            processMessage(packet);
        }
        else if ("presence".equals(tag)) {
            Presence packet;
            try {
                packet = new Presence(doc, !validateJIDs());
            }
            catch (IllegalArgumentException e) {
                Log.debug("Rejecting stanza from '{}'. JID malformed", session.getAddress(), e);
                // The original packet contains a malformed JID so answer an error
                Presence reply = new Presence();
                reply.setID(doc.attributeValue("id"));
                reply.setTo(session.getAddress());
                reply.getElement().addAttribute("from", doc.attributeValue("to"));
                reply.setError(PacketError.Condition.jid_malformed);
                session.process(reply);
                return;
            }
            // Check that the presence type is valid. If not then assume available type
            try {
                packet.getType();
            }
            catch (IllegalArgumentException e) {
                Log.warn("Invalid presence type '{}' received from '{}'", packet.getElement().attributeValue("type"), session.getAddress(), e);
                // The presence packet contains an invalid presence type so replace it with
                // an available presence type
                packet.setType(null);
            }
            // Check that the presence show is valid. If not then assume available show value
            try {
                packet.getShow();
            }
            catch (IllegalArgumentException e) {
                Log.debug("Invalid presence show '{}' received from '{}'", packet.getElement().elementText("show"), e);
                // The presence packet contains an invalid presence show so replace it with
                // an available presence show
                packet.setShow(null);
            }
            if (session.getStatus() == Session.Status.CLOSED && packet.isAvailable()) {
                // Ignore available presence packets sent from a closed session. A closed
                // session may have buffered data pending to be processes so we want to ignore
                // just Presences of type available
                Log.warn("Ignoring available presence packet received on closed session '{}': {} ", session.getAddress(), packet);
                return;
            }
            processPresence(packet);
        }
        else if ("iq".equals(tag)) {
            IQ packet;
            try {
                packet = getIQ(doc);
            }
            catch (IllegalArgumentException e) {
                Log.debug("Rejecting packet from '{}'. JID malformed", session.getAddress(), e);
                // The original packet contains a malformed JID so answer an error
                IQ reply = new IQ();
                if (!doc.elements().isEmpty()) {
                    reply.setChildElement(((Element)doc.elements().get(0)).createCopy());
                }
                reply.setID(doc.attributeValue("id"));
                reply.setTo(session.getAddress());
                if (doc.attributeValue("to") != null) {
                    reply.getElement().addAttribute("from", doc.attributeValue("to"));
                }
                reply.setError(PacketError.Condition.jid_malformed);
                session.process(reply);
                return;
            }
            if (packet.getID() == null && JiveGlobals.getBooleanProperty("xmpp.server.validation.enabled", false)) {
                // IQ packets MUST have an 'id' attribute so close the connection
                Log.debug( "Closing session, as it sent us an IQ packet that has no ID attribute: {}. Affected session: {}", packet.toXML(), session );
                StreamError error = new StreamError(StreamError.Condition.invalid_xml, "Stanza is missing 'id' attribute.");
                session.deliverRawText(error.toXML());
                session.close();
                return;
            }
            processIQ(packet);
        }
        else {
            if (!processUnknowPacket(doc)) {
                Log.warn(LocaleUtils.getLocalizedString("admin.error.packet.tag") + "{}. Closing session: {}", doc.asXML(), session);
                session.close();
            }
        }
    }

    private IQ getIQ(Element doc) {
        Element query = doc.element("query");
        if (query != null && "jabber:iq:roster".equals(query.getNamespaceURI())) {
            return new Roster(doc);
        }else if (query != null && "jabber:iq:version".equals(query.getNamespaceURI())) {
            try {
                List<Element> elements = query.elements();
                if (!elements.isEmpty()){
                    for (Element element : elements){
                        session.setSoftwareVersionData(element.getName(), element.getStringValue());
                    }
                }    
            } catch (Exception e) {
                Log.error("Unexpected exception while processing IQ Version stanza from '{}'", session.getAddress(), e);
            }
            return new IQ(doc, !validateJIDs());
        }else if(query != null && "http://jabber.org/protocol/disco#info".equals(query.getNamespaceURI())){
            //XEP-0232 if responses service discovery can include detailed information about the software application
            IQDiscoInfoHandler.setSoftwareVersionDataFormFromDiscoInfo(query, session);
            return new IQ(doc, !validateJIDs());
        }
        else {
            return new IQ(doc, !validateJIDs());
        }
    }

    /**
     * Process the received IQ packet. Registered
     * {@link org.jivesoftware.openfire.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.
     * <p>
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.</p>
     *
     * @param packet the received packet.
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException
     *          if service is not available to sender.
     */
    protected void processIQ(IQ packet) throws UnauthorizedException
    {
        // If the 'to' attribute is null, treat the IQ on behalf of the entity from which the IQ stanza originated
        // in accordance with RFC 6120 § 10.3.3. See https://tools.ietf.org/html/rfc6120#section-10.3.3:
        // > […] responding as if the server were the bare JID of the sending entity.
        if ( packet.getTo() == null && PROPERTY_OVERWRITE_EMPTY_TO.getValue() ) {
            packet.setTo( packet.getFrom().asBareJID() );
        }

        router.route(packet);
        session.incrementClientPacketCount();
    }

    /**
     * Process the received Presence packet. Registered
     * {@link org.jivesoftware.openfire.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.
     * <p>
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.</p>
     *
     * @param packet the received packet.
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException
     *          if service is not available to sender.
     */
    protected void processPresence(Presence packet) throws UnauthorizedException {
        router.route(packet);
        session.incrementClientPacketCount();
    }

    /**
     * Process the received Message packet. Registered
     * {@link org.jivesoftware.openfire.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.
     * <p>
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.</p>
     *
     * @param packet the received packet.
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException
     *          if service is not available to sender.
     */
    protected void processMessage(Message packet) throws UnauthorizedException {
        // If the 'to' attribute is null, treat the IQ on behalf of the entity from which the IQ stanza originated
        // in accordance with RFC 6120 § 10.3.1. See https://tools.ietf.org/html/rfc6120#section-10.3.1:
        // > […] treat the message as if the 'to' address were the bare JID <localpart@domainpart> of the sending entity.
        if ( packet.getTo() == null && PROPERTY_OVERWRITE_EMPTY_TO.getValue() ) {
            packet.setTo( packet.getFrom().asBareJID() );
        }
        router.route(packet);
        session.incrementClientPacketCount();
    }

    /**
     * Returns true if a received packet of an unkown type (i.e. not a Message, Presence
     * or IQ) has been processed. If the packet was not processed then an exception will
     * be thrown which will make the thread to stop processing further packets.
     *
     * @param doc the DOM element of an unkown type.
     * @return true if a received packet has been processed.
     * @throws UnauthorizedException if stanza failed to be processed. Connection will be closed.
     */
    abstract boolean processUnknowPacket(Element doc) throws UnauthorizedException;

    /**
     * Tries to encrypt the connection using TLS. If the connection is encrypted then reset
     * the parser to use the new encrypted reader. But if the connection failed to be encrypted
     * then send a <failure> stanza and close the connection.
     *
     * @return true if the connection was encrypted.
     */
    protected boolean negotiateTLS() {
        if (connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.disabled) {
            // Send a not_authorized error and close the underlying connection
            connection.close(new StreamError(StreamError.Condition.not_authorized, "A request to negotiate TLS is denied, as TLS has been disabled by configuration."));
            // Log a warning so that admins can track this case from the server side
            Log.warn("TLS requested by initiator when TLS was never offered by server. Closing connection: {}", connection);
            return false;
        }
        // Client requested to encrypt the connection using TLS. Negotiate TLS.
        try {
            startTLS();
        }
        catch (Exception e) {
            Log.error("Error while negotiating TLS with connection {}", connection, e);
            connection.deliverRawText("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");
            connection.close();
            return false;
        }
        return true;
    }

    abstract void startTLS() throws Exception;

    /**
     * TLS negotiation was successful so open a new stream and offer the new stream features.
     * The new stream features will include available SASL mechanisms and specific features
     * depending on the session type such as auth for Non-SASL authentication and register
     * for in-band registration.
     */
    protected void tlsNegotiated(XmlPullParser xpp) throws XmlPullParserException, IOException {
        final Document document = getStreamHeader();

        // Offer stream features including SASL Mechanisms
        final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
        document.getRootElement().add(features);

        // Include available SASL Mechanisms
        features.add(SASLAuthentication.getSASLMechanisms(session));
        // Include specific features such as auth and register for client sessions
        final List<Element> specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            for (final Element feature : specificFeatures) {
                features.add(feature);
            }
        }

        connection.deliverRawText(StringUtils.asUnclosedStream(document));
    }

    /**
     * After SASL authentication was successful we should open a new stream and offer
     * new stream features such as resource binding and session establishment. Notice that
     * resource binding and session establishment should only be offered to clients (i.e. not
     * to servers or external components)
     */
    protected void saslSuccessful() {
        final Document document = getStreamHeader();
        final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
        document.getRootElement().add(features);

        // Include specific features such as resource binding and session establishment for client sessions
        final List<Element> specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            for (final Element feature : specificFeatures) {
                features.add(feature);
            }
        }

        connection.deliverRawText(StringUtils.asUnclosedStream(document));
    }

    /**
     * Start using compression but first check if the connection can and should use compression.
     * The connection will be closed if the requested method is not supported, if the connection
     * is already using compression or if client requested to use compression but this feature
     * is disabled.
     *
     * @param doc the element sent by the client requesting compression. Compression method is
     *            included.
     * @return true if it was possible to use compression.
     */
    protected boolean compressClient(Element doc) {
        String error = null;
        if (connection.getConfiguration().getCompressionPolicy() == Connection.CompressionPolicy.disabled) {
            // Client requested compression but this feature is disabled
            error = "<failure xmlns='http://jabber.org/protocol/compress'><setup-failed/></failure>";
            // Log a warning so that admins can track this case from the server side
            Log.warn("Client requested compression while compression is disabled. Closing connection: {}", connection);
        }
        else if (connection.isCompressed()) {
            // Client requested compression but connection is already compressed
            error = "<failure xmlns='http://jabber.org/protocol/compress'><setup-failed/></failure>";
            // Log a warning so that admins can track this case from the server side
            Log.warn("Client requested compression and connection is already compressed. Closing connection: {}", connection);
        }
        else {
            // Check that the requested method is supported
            String method = doc.elementText("method");
            if (!"zlib".equals(method)) {
                error = "<failure xmlns='http://jabber.org/protocol/compress'><unsupported-method/></failure>";
                // Log a warning so that admins can track this case from the server side
                Log.warn("Requested compression method is not supported: {}. Closing connection: {}", method, connection);
            }
        }

        if (error != null) {
            // Deliver stanza
            connection.deliverRawText(error);
            return false;
        }
        else {
            // Start using compression for incoming traffic
            connection.addCompression();

            // Indicate client that he can proceed and compress the socket
            connection.deliverRawText("<compressed xmlns='http://jabber.org/protocol/compress'/>");

            // Start using compression for outgoing traffic
            connection.startCompression();
            return true;
        }
    }

    /**
     * After compression was successful we should open a new stream and offer
     * new stream features such as resource binding and session establishment. Notice that
     * resource binding and session establishment should only be offered to clients (i.e. not
     * to servers or external components)
     */
    protected void compressionSuccessful() {
        final Document document = getStreamHeader();
        final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
        document.getRootElement().add(features);

        // Include SASL mechanisms only if client has not been authenticated
        if (!session.isAuthenticated()) {
            final Element saslMechanisms = SASLAuthentication.getSASLMechanisms(session);
            if (saslMechanisms != null) {
                features.add(saslMechanisms);
            }
        }
        // Include specific features such as resource binding and session establishment for client sessions
        final List<Element> specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            for (final Element feature : specificFeatures) {
                features.add(feature);
            }
        }

        connection.deliverRawText(StringUtils.asUnclosedStream(document));
    }

    /**
     * Determines whether stanza's namespace matches XEP-0198 namespace
     * @param stanza Stanza to be checked
     * @return whether stanza's namespace matches XEP-0198 namespace
     */
    protected boolean isStreamManagementStanza(Element stanza) {
        return StreamManager.NAMESPACE_V2.equals(stanza.getNamespace().getStringValue()) ||
                StreamManager.NAMESPACE_V3.equals(stanza.getNamespace().getStringValue());
    }

    protected Document getStreamHeader()
    {
        final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
        final Document document = DocumentHelper.createDocument(stream);
        document.setXMLEncoding(StandardCharsets.UTF_8.toString());
        stream.add(getNamespace());
        stream.addAttribute("from", XMPPServer.getInstance().getServerInfo().getXMPPDomain());
        stream.addAttribute("id", session.getStreamID().getID());
        stream.addAttribute(QName.get("lang", Namespace.XML_NAMESPACE), session.getLanguage().toLanguageTag());
        stream.addAttribute("version", Session.MAJOR_VERSION + "." + Session.MINOR_VERSION);

        return document;
    }

    /**
     * Close the connection since TLS was mandatory and the entity never negotiated TLS. Before
     * closing the connection a stream error will be sent to the entity.
     *
     * @deprecated Renamed. Use {@link #closeNeverEncryptedConnection()}
     */
    @Deprecated // remove in Openfire 4.9 or later.
    protected void closeNeverSecuredConnection() {
        closeNeverEncryptedConnection();
    }

    /**
     * Close the connection since TLS was mandatory and the entity never negotiated TLS. Before
     * closing the connection a stream error will be sent to the entity.
     */
    protected void closeNeverEncryptedConnection() {
        // Send a stream error and close the underlying connection.
        connection.close(new StreamError(StreamError.Condition.not_authorized, "TLS is mandatory, but was established."));
        // Log a warning so that admins can track this case from the server side
        Log.warn("TLS was required by the server and connection was never encrypted. Closing connection: {}", connection);
    }

    /**
     * Uses the XPP to grab the opening stream tag and create an active session
     * object. The session to create will depend on the sent namespace. In all
     * cases, the method obtains the opening stream tag, checks for errors, and
     * either creates a session or returns an error and kills the connection.
     * If the connection remains open, the XPP will be set to be ready for the
     * first packet. A call to next() should result in an START_TAG state with
     * the first packet in the stream.
     * @param xpp the pull parser
     * @throws XmlPullParserException if an exception occurs reading from the pull parser
     * @throws IOException if an IO exception occurs reading from the pull parser
     */
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

            // Validate the stream namespace
            if (!"http://etherx.jabber.org/streams".equals(xpp.getNamespace())) {
                throw new StreamErrorException(StreamError.Condition.invalid_namespace, "Invalid namespace in stream header: " + xpp.getNamespace());
            }

            // http://xmpp.org/rfcs/rfc6120.html#streams-error-conditions-invalid-namespace
            // "or the content namespace declared as the default namespace is not supported (e.g., something other than "jabber:client" or "jabber:server")."
            if (!getNamespace().getURI().equals(xpp.getNamespace(null))) {
                throw new StreamErrorException(StreamError.Condition.invalid_namespace, "Invalid namespace in stream header. Expected: '" + getNamespace().getURI() + "'. Received: '" + xpp.getNamespace(null) + "'.");
            }

            // Create the correct session based on the sent namespace. At this point the server
            // may offer the client to encrypt the connection. If the client decides to encrypt
            // the connection then a <starttls> stanza should be received
            createSession(serverName, xpp, connection);

            if (session == null) {
                throw new StreamErrorException(StreamError.Condition.internal_server_error, "Unable to create a session.");
            }
        }
        catch (final StreamErrorException ex) {
            Log.warn("Failed to create a session. Closing connection: {}", connection, ex);
            final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
            final Document document = DocumentHelper.createDocument(stream);
            document.setXMLEncoding(StandardCharsets.UTF_8.toString());
            stream.add(Namespace.get(xpp.getNamespace(null)));
            stream.addAttribute("from", host);
            stream.addAttribute("id", STREAM_ID_FACTORY.createStreamID().getID());
            stream.addAttribute("version", "1.0");
            stream.add(ex.getStreamError().getElement());

            // Deliver stanza
            connection.deliverRawText(StringUtils.asUnclosedStream(document));

            // Close the underlying connection
            connection.close();
        }
    }

    protected boolean isHostUnknown(String host) {
        if (host == null) {
            // Answer false since when using server dialback the stream header will not
            // have a TO attribute
            return false;
        }
        if (XMPPServer.getInstance().getServerInfo().getXMPPDomain().equals( host )) {
            // requested host matched the server name
            return false;
        }
        return true;
    }

    /**
     * Obtain the address of the XMPP entity for which this StanzaHandler
     * handles stanzas.
     *
     * Note that the value that is returned for this method can
     * change over time. For example, if no session has been established yet,
     * this method will return {@code null}, or, if resource binding occurs,
     * the returned value might change. Values obtained from this method are
     * therefore best <em>not</em> cached.
     *
     * @return The address of the XMPP entity for.
     */
    public JID getAddress() {
        if (session == null) {
            return null;
        }

        return session.getAddress();
    }

    /**
     * Returns the stream namespace. (E.g. jabber:client, jabber:server, etc.).
     *
     * @return the stream namespace.
     */
    abstract Namespace getNamespace();

    /**
     * Returns true if the value of the 'to' attribute in the stream header should be
     * validated. If the value of the 'to' attribute is not valid then a host-unknown error
     * will be returned and the underlying connection will be closed.
     *
     * @return true if the value of the 'to' attribute in the initial stream header should be
     *         validated.
     */
    abstract boolean validateHost();

    /**
     * Returns true if the value of the 'to' attribute of {@link IQ}, {@link Presence} and
     * {@link Message} must be validated. Connection Managers perform their own
     * JID validation so there is no need to validate JIDs again but when clients are
     * directly connected to the server then we need to validate JIDs.
     *
     * @return rue if the value of the 'to' attribute of IQ, Presence and Messagemust be validated.
     */
    abstract boolean validateJIDs();

    /**
     * Creates the appropriate {@link Session} subclass.
     *
     * @throws org.xmlpull.v1.XmlPullParserException when XML parsing causes an error.
     */
    abstract void createSession(String serverName, XmlPullParser xpp, Connection connection) throws XmlPullParserException;

    /**
     * Checks if the provided XML data represents the beginning of a new XMPP stream.
     *
     * @param xml The XML to verify
     * @return 'true' if the provided data represents the beginning of an XMPP stream.
     */
    protected boolean isStartOfStream(final String xml) {
        return xml.startsWith("<stream:stream") || (xml.startsWith("<?xml ") && xml.contains("<stream:stream"));
    }

    /**
     * Checks if the provided XML data represents the end / closing of an XMPP stream.
     *
     * @param xml The XML to verify
     * @return 'true' if the provided data represents the end of an XMPP stream.
     */
    protected boolean isEndOfStream(final String xml) {
        return xml.equals("</stream:stream>");
    }
}
