/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

import java.io.IOException;
import java.io.StringReader;

import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.http.FlashCrossDomainServlet;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;
import org.xmpp.packet.Roster;
import org.xmpp.packet.StreamError;

/**
 * A StanzaHandler is the main responsible for handling incoming stanzas. Some stanzas like startTLS
 * are totally managed by this class. The rest of the stanzas are just forwarded to the router.
 *
 * @author Gaston Dombiak
 */
public abstract class StanzaHandler {
	
	private static final Logger Log = LoggerFactory.getLogger(StanzaHandler.class);

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    protected static String CHARSET = "UTF-8";
    protected Connection connection;

    // DANIELE: Indicate if a session is already created
    private boolean sessionCreated = false;

    // Flag that indicates that the client requested to use TLS and TLS has been negotiated. Once the
    // client sent a new initial stream header the value will return to false.
    private boolean startedTLS = false;
    // Flag that indicates that the client requested to be authenticated. Once the
    // authentication process is over the value will return to false.
    private boolean startedSASL = false;
    /**
     * SASL status based on the last SASL interaction
     */
    private SASLAuthentication.Status saslStatus;

    // DANIELE: Indicate if a stream:stream is arrived to complete compression
    private boolean waitingCompressionACK = false;

    /**
     * Session associated with the socket reader.
     */
    protected LocalSession session;
    /**
     * Server name for which we are attending clients.
     */
    protected String serverName;

    /**
     * Router used to route incoming packets to the correct channels.
     */
    private PacketRouter router;

    /**
     * Creates a dedicated reader for a socket.
     *
     * @param router     the router for sending packets that were read.
     * @param serverName the name of the server this socket is working for.
     * @param connection the connection being read.
     */
    public StanzaHandler(PacketRouter router, String serverName, Connection connection) {
        this.serverName = serverName;
        this.router = router;
        this.connection = connection;
    }

    public void process(String stanza, XMPPPacketReader reader) throws Exception {

        boolean initialStream = stanza.startsWith("<stream:stream") || stanza.startsWith("<flash:stream");
        if (!sessionCreated || initialStream) {
            if (!initialStream) {
                // Allow requests for flash socket policy files directly on the client listener port
                if (stanza.startsWith("<policy-file-request/>")) {
                    String crossDomainText = FlashCrossDomainServlet.CROSS_DOMAIN_TEXT +
                            XMPPServer.getInstance().getConnectionManager().getClientListenerPort() +
                            FlashCrossDomainServlet.CROSS_DOMAIN_END_TEXT + '\0';
                    connection.deliverRawText(crossDomainText);
                    return;
                }
                else {
                    // Ignore <?xml version="1.0"?>
                    return;
                }
            }
            // Found an stream:stream tag...
            if (!sessionCreated) {
                sessionCreated = true;
                MXParser parser = reader.getXPPParser();
                parser.setInput(new StringReader(stanza));
                createSession(parser);
            }
            else if (startedTLS) {
                startedTLS = false;
                tlsNegotiated();
            }
            else if (startedSASL && saslStatus == SASLAuthentication.Status.authenticated) {
                startedSASL = false;
                saslSuccessful();
            }
            else if (waitingCompressionACK) {
                waitingCompressionACK = false;
                compressionSuccessful();
            }
            return;
        }

        // Verify if end of stream was requested
        if (stanza.equals("</stream:stream>")) {
            session.close();
            return;
        }
        // Ignore <?xml version="1.0"?> stanzas sent by clients
        if (stanza.startsWith("<?xml")) {
            return;
        }
        // Create DOM object from received stanza
        Element doc = reader.read(new StringReader(stanza)).getRootElement();
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
        }
        else if (startedSASL && "response".equals(tag)) {
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
        }
        else {
            process(doc);
        }
    }

    private void process(Element doc) throws UnauthorizedException {
        if (doc == null) {
            return;
        }

        // Ensure that connection was secured if TLS was required
        if (connection.getTlsPolicy() == Connection.TLSPolicy.required &&
                !connection.isSecure()) {
            closeNeverSecuredConnection();
            return;
        }

        String tag = doc.getName();
        if ("message".equals(tag)) {
            Message packet;
            try {
                packet = new Message(doc, !validateJIDs());
            }
            catch (IllegalArgumentException e) {
                Log.debug("Rejecting packet. JID malformed", e);
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
                Log.debug("Rejecting packet. JID malformed", e);
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
                Log.warn("Invalid presence type", e);
                // The presence packet contains an invalid presence type so replace it with
                // an available presence type
                packet.setType(null);
            }
            // Check that the presence show is valid. If not then assume available show value
            try {
                packet.getShow();
            }
            catch (IllegalArgumentException e) {
                Log.warn("Invalid presence show for -" + packet.toXML(), e);
                // The presence packet contains an invalid presence show so replace it with
                // an available presence show
                packet.setShow(null);
            }
            if (session.getStatus() == Session.STATUS_CLOSED && packet.isAvailable()) {
                // Ignore available presence packets sent from a closed session. A closed
                // session may have buffered data pending to be processes so we want to ignore
                // just Presences of type available
                Log.warn("Ignoring available presence packet of closed session: " + packet);
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
                Log.debug("Rejecting packet. JID malformed", e);
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
                StreamError error = new StreamError(StreamError.Condition.invalid_xml);
                session.deliverRawText(error.toXML());
                session.close();
                return;
            }
            processIQ(packet);
        }
        else {
            if (!processUnknowPacket(doc)) {
                Log.warn(LocaleUtils.getLocalizedString("admin.error.packet.tag") +
                        doc.asXML());
                session.close();
            }
        }
    }

    private IQ getIQ(Element doc) {
        Element query = doc.element("query");
        if (query != null && "jabber:iq:roster".equals(query.getNamespaceURI())) {
            return new Roster(doc);
        }
        else {
            return new IQ(doc, !validateJIDs());
        }
    }

    /**
     * Process the received IQ packet. Registered
     * {@link org.jivesoftware.openfire.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.<p>
     * <p/>
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.
     *
     * @param packet the received packet.
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException
     *          if service is not available to sender.
     */
    protected void processIQ(IQ packet) throws UnauthorizedException {
        router.route(packet);
        session.incrementClientPacketCount();
    }

    /**
     * Process the received Presence packet. Registered
     * {@link org.jivesoftware.openfire.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.<p>
     * <p/>
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.
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
     * and after the packet was routed.<p>
     * <p/>
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.
     *
     * @param packet the received packet.
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException
     *          if service is not available to sender.
     */
    protected void processMessage(Message packet) throws UnauthorizedException {
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
     * Tries to secure the connection using TLS. If the connection is secured then reset
     * the parser to use the new secured reader. But if the connection failed to be secured
     * then send a <failure> stanza and close the connection.
     *
     * @return true if the connection was secured.
     */
    private boolean negotiateTLS() {
        if (connection.getTlsPolicy() == Connection.TLSPolicy.disabled) {
            // Set the not_authorized error
            StreamError error = new StreamError(StreamError.Condition.not_authorized);
            // Deliver stanza
            connection.deliverRawText(error.toXML());
            // Close the underlying connection
            connection.close();
            // Log a warning so that admins can track this case from the server side
            Log.warn("TLS requested by initiator when TLS was never offered by server. " +
                    "Closing connection : " + connection);
            return false;
        }
        // Client requested to secure the connection using TLS. Negotiate TLS.
        try {
            startTLS();
        }
        catch (Exception e) {
            Log.error("Error while negotiating TLS", e);
            connection.deliverRawText("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\">");
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
    private void tlsNegotiated() {
        // Offer stream features including SASL Mechanisms
        StringBuilder sb = new StringBuilder(620);
        sb.append(geStreamHeader());
        sb.append("<stream:features>");
        // Include available SASL Mechanisms
        sb.append(SASLAuthentication.getSASLMechanisms(session));
        // Include specific features such as auth and register for client sessions
        String specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        connection.deliverRawText(sb.toString());
    }

    /**
     * After SASL authentication was successful we should open a new stream and offer
     * new stream features such as resource binding and session establishment. Notice that
     * resource binding and session establishment should only be offered to clients (i.e. not
     * to servers or external components)
     */
    private void saslSuccessful() {
        StringBuilder sb = new StringBuilder(420);
        sb.append(geStreamHeader());
        sb.append("<stream:features>");

        // Include specific features such as resource binding and session establishment
        // for client sessions
        String specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        connection.deliverRawText(sb.toString());
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
    private boolean compressClient(Element doc) {
        String error = null;
        if (connection.getCompressionPolicy() == Connection.CompressionPolicy.disabled) {
            // Client requested compression but this feature is disabled
            error = "<failure xmlns='http://jabber.org/protocol/compress'><setup-failed/></failure>";
            // Log a warning so that admins can track this case from the server side
            Log.warn("Client requested compression while compression is disabled. Closing " +
                    "connection : " + connection);
        }
        else if (connection.isCompressed()) {
            // Client requested compression but connection is already compressed
            error = "<failure xmlns='http://jabber.org/protocol/compress'><setup-failed/></failure>";
            // Log a warning so that admins can track this case from the server side
            Log.warn("Client requested compression and connection is already compressed. Closing " +
                    "connection : " + connection);
        }
        else {
            // Check that the requested method is supported
            String method = doc.elementText("method");
            if (!"zlib".equals(method)) {
                error = "<failure xmlns='http://jabber.org/protocol/compress'><unsupported-method/></failure>";
                // Log a warning so that admins can track this case from the server side
                Log.warn("Requested compression method is not supported: " + method +
                        ". Closing connection : " + connection);
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
    private void compressionSuccessful() {
        StringBuilder sb = new StringBuilder(340);
        sb.append(geStreamHeader());
        sb.append("<stream:features>");
        // Include SASL mechanisms only if client has not been authenticated
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            // Include available SASL Mechanisms
            sb.append(SASLAuthentication.getSASLMechanisms(session));
        }
        // Include specific features such as resource binding and session establishment
        // for client sessions
        String specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        connection.deliverRawText(sb.toString());
    }

    private String geStreamHeader() {
        StringBuilder sb = new StringBuilder(200);
        sb.append("<?xml version='1.0' encoding='");
        sb.append(CHARSET);
        sb.append("'?>");
        if (connection.isFlashClient()) {
            sb.append("<flash:stream xmlns:flash=\"http://www.jabber.com/streams/flash\" ");
        }
        else {
            sb.append("<stream:stream ");
        }
        sb.append("xmlns:stream=\"http://etherx.jabber.org/streams\" xmlns=\"");
        sb.append(getNamespace());
        sb.append("\" from=\"");
        sb.append(serverName);
        sb.append("\" id=\"");
        sb.append(session.getStreamID());
        sb.append("\" xml:lang=\"");
        sb.append(connection.getLanguage());
        sb.append("\" version=\"");
        sb.append(Session.MAJOR_VERSION).append(".").append(Session.MINOR_VERSION);
        sb.append("\">");
        return sb.toString();
    }

    /**
     * Close the connection since TLS was mandatory and the entity never negotiated TLS. Before
     * closing the connection a stream error will be sent to the entity.
     */
    void closeNeverSecuredConnection() {
        // Set the not_authorized error
        StreamError error = new StreamError(StreamError.Condition.not_authorized);
        // Deliver stanza
        connection.deliverRawText(error.toXML());
        // Close the underlying connection
        connection.close();
        // Log a warning so that admins can track this case from the server side
        Log.warn("TLS was required by the server and connection was never secured. " +
                "Closing connection : " + connection);
    }

    /**
     * Uses the XPP to grab the opening stream tag and create an active session
     * object. The session to create will depend on the sent namespace. In all
     * cases, the method obtains the opening stream tag, checks for errors, and
     * either creates a session or returns an error and kills the connection.
     * If the connection remains open, the XPP will be set to be ready for the
     * first packet. A call to next() should result in an START_TAG state with
     * the first packet in the stream.
     */
    protected void createSession(XmlPullParser xpp) throws XmlPullParserException, IOException {
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }

        // Check that the TO attribute of the stream header matches the server name or a valid
        // subdomain. If the value of the 'to' attribute is not valid then return a host-unknown
        // error and close the underlying connection.
        String host = xpp.getAttributeValue("", "to");
        if (validateHost() && isHostUnknown(host)) {
            StringBuilder sb = new StringBuilder(250);
            sb.append("<?xml version='1.0' encoding='");
            sb.append(CHARSET);
            sb.append("'?>");
            // Append stream header
            sb.append("<stream:stream ");
            sb.append("from=\"").append(serverName).append("\" ");
            sb.append("id=\"").append(StringUtils.randomString(5)).append("\" ");
            sb.append("xmlns=\"").append(xpp.getNamespace(null)).append("\" ");
            sb.append("xmlns:stream=\"").append(xpp.getNamespace("stream")).append("\" ");
            sb.append("version=\"1.0\">");
            // Set the host_unknown error
            StreamError error = new StreamError(StreamError.Condition.host_unknown);
            sb.append(error.toXML());
            // Deliver stanza
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            // Log a warning so that admins can track this cases from the server side
            Log.warn("Closing session due to incorrect hostname in stream header. Host: " + host +
                    ". Connection: " + connection);
        }

        // Create the correct session based on the sent namespace. At this point the server
        // may offer the client to secure the connection. If the client decides to secure
        // the connection then a <starttls> stanza should be received
        else if (!createSession(xpp.getNamespace(null), serverName, xpp, connection)) {
            // No session was created because of an invalid namespace prefix so answer a stream
            // error and close the underlying connection
            StringBuilder sb = new StringBuilder(250);
            sb.append("<?xml version='1.0' encoding='");
            sb.append(CHARSET);
            sb.append("'?>");
            // Append stream header
            sb.append("<stream:stream ");
            sb.append("from=\"").append(serverName).append("\" ");
            sb.append("id=\"").append(StringUtils.randomString(5)).append("\" ");
            sb.append("xmlns=\"").append(xpp.getNamespace(null)).append("\" ");
            sb.append("xmlns:stream=\"").append(xpp.getNamespace("stream")).append("\" ");
            sb.append("version=\"1.0\">");
            // Include the bad-namespace-prefix in the response
            StreamError error = new StreamError(StreamError.Condition.bad_namespace_prefix);
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            // Log a warning so that admins can track this cases from the server side
            Log.warn("Closing session due to bad_namespace_prefix in stream header. Prefix: " +
                    xpp.getNamespace(null) + ". Connection: " + connection);
        }
    }

    private boolean isHostUnknown(String host) {
        if (host == null) {
            // Answer false since when using server dialback the stream header will not
            // have a TO attribute
            return false;
        }
        if (serverName.equals(host)) {
            // requested host matched the server name
            return false;
        }
        return true;
    }

    /**
     * Returns the stream namespace. (E.g. jabber:client, jabber:server, etc.).
     *
     * @return the stream namespace.
     */
    abstract String getNamespace();

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
     * Creates the appropriate {@link Session} subclass based on the specified namespace.
     *
     * @param namespace the namespace sent in the stream element. eg. jabber:client.
     * @return the created session or null.
     * @throws org.xmlpull.v1.XmlPullParserException
     *
     */
    abstract boolean createSession(String namespace, String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException;
}
