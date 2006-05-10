/**
 * $RCSfile$
 * $Revision: 3187 $
 * $Date: 2005-12-11 13:34:34 -0300 (Sun, 11 Dec 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZInputStream;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.wildfire.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.interceptor.InterceptorManager;
import org.jivesoftware.wildfire.interceptor.PacketRejectedException;
import org.jivesoftware.wildfire.server.OutgoingSessionPromise;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.AsynchronousCloseException;

/**
 * A SocketReader creates the appropriate {@link Session} based on the defined namespace in the
 * stream element and will then keep reading and routing the received packets.
 *
 * @author Gaston Dombiak
 */
public abstract class SocketReader implements Runnable {

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    private static String CHARSET = "UTF-8";
    /**
     * Reuse the same factory for all the connections.
     */
    private static XmlPullParserFactory factory = null;

    private Socket socket;
    protected Session session;
    protected SocketConnection connection;
    protected String serverName;

    /**
     * Router used to route incoming packets to the correct channels.
     */
    private PacketRouter router;
    XMPPPacketReader reader = null;
    protected boolean open;
    private RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

    /**
     * Creates a dedicated reader for a socket.
     *
     * @param router the router for sending packets that were read.
     * @param serverName the name of the server this socket is working for.
     * @param socket the socket to read from.
     * @param connection the connection being read.
     */
    public SocketReader(PacketRouter router, String serverName, Socket socket,
            SocketConnection connection) {
        this.serverName = serverName;
        this.router = router;
        this.connection = connection;
        this.socket = socket;

        connection.setSocketReader(this);
    }

    /**
     * A dedicated thread loop for reading the stream and sending incoming
     * packets to the appropriate router.
     */
    public void run() {
        try {
            reader = new XMPPPacketReader();
            reader.setXPPFactory(factory);

            reader.getXPPParser().setInput(new InputStreamReader(socket.getInputStream(),
                    CHARSET));

            // Read in the opening tag and prepare for packet stream
            try {
                createSession();
            }
            catch (IOException e) {
                Log.debug("Error creating session", e);
                throw e;
            }

            // Read the packet stream until it ends
            if (session != null) {
                readStream();
            }

        }
        catch (EOFException eof) {
            // Normal disconnect
        }
        catch (SocketException se) {
            // The socket was closed. The server may close the connection for several
            // reasons (e.g. user requested to remove his account). Do nothing here.
        }
        catch (AsynchronousCloseException ace) {
            // The socket was closed.
        }
        catch (XmlPullParserException ie) {
            // It is normal for clients to abruptly cut a connection
            // rather than closing the stream document. Since this is
            // normal behavior, we won't log it as an error.
            // Log.error(LocaleUtils.getLocalizedString("admin.disconnect"),ie);
        }
        catch (Exception e) {
            if (session != null) {
                Log.warn(LocaleUtils.getLocalizedString("admin.error.stream") + ". Session: " +
                        session, e);
            }
        }
        finally {
            if (session != null) {
                if (Log.isDebugEnabled()) {
                    Log.debug("Logging off " + session.getAddress() + " on " + connection);
                }
                try {
                    session.getConnection().close();
                }
                catch (Exception e) {
                    Log.warn(LocaleUtils.getLocalizedString("admin.error.connection")
                            + "\n" + socket.toString());
                }
            }
            else {
                // Close and release the created connection
                connection.close();
                Log.error(LocaleUtils.getLocalizedString("admin.error.connection")
                        + "\n" + socket.toString());
            }
            shutdown();
        }
    }

    /**
     * Read the incoming stream until it ends.
     */
    private void readStream() throws Exception {
        open = true;
        while (open) {
            Element doc = reader.parseDocument().getRootElement();

            if (doc == null) {
                // Stop reading the stream since the client has sent an end of
                // stream element and probably closed the connection.
                return;
            }

            String tag = doc.getName();
            if ("message".equals(tag)) {
                Message packet;
                try {
                    packet = new Message(doc);
                }
                catch(IllegalArgumentException e) {
                    // The original packet contains a malformed JID so answer with an error.
                    Message reply = new Message();
                    reply.setID(doc.attributeValue("id"));
                    reply.setTo(session.getAddress());
                    reply.getElement().addAttribute("from", doc.attributeValue("to"));
                    reply.setError(PacketError.Condition.jid_malformed);
                    session.process(reply);
                    continue;
                }
                processMessage(packet);
            }
            else if ("presence".equals(tag)) {
                Presence packet;
                try {
                    packet = new Presence(doc);
                }
                catch (IllegalArgumentException e) {
                    // The original packet contains a malformed JID so answer an error
                    Presence reply = new Presence();
                    reply.setID(doc.attributeValue("id"));
                    reply.setTo(session.getAddress());
                    reply.getElement().addAttribute("from", doc.attributeValue("to"));
                    reply.setError(PacketError.Condition.jid_malformed);
                    session.process(reply);
                    continue;
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
                    Log.warn("Invalid presence show", e);
                    // The presence packet contains an invalid presence show so replace it with
                    // an available presence show
                    packet.setShow(null);
                }
                if (session.getStatus() == Session.STATUS_CLOSED && packet.isAvailable()) {
                    // Ignore available presence packets sent from a closed session. A closed
                    // session may have buffered data pending to be processes so we want to ignore
                    // just Presences of type available
                    Log.warn("Ignoring available presence packet of closed session: " + packet);
                    continue;
                }
                processPresence(packet);
            }
            else if ("iq".equals(tag)) {
                IQ packet;
                try {
                    packet = getIQ(doc);
                }
                catch(IllegalArgumentException e) {
                    // The original packet contains a malformed JID so answer an error
                    IQ reply = new IQ();
                    if (!doc.elements().isEmpty()) {
                        reply.setChildElement(((Element) doc.elements().get(0)).createCopy());
                    }
                    reply.setID(doc.attributeValue("id"));
                    reply.setTo(session.getAddress());
                    if (doc.attributeValue("to") != null) {
                        reply.getElement().addAttribute("from", doc.attributeValue("to"));
                    }
                    reply.setError(PacketError.Condition.jid_malformed);
                    session.process(reply);
                    continue;
                }
                processIQ(packet);
            }
            else if ("starttls".equals(tag)) {
                // Negotiate TLS
                if (negotiateTLS()) {
                    tlsNegotiated();
                }
                else {
                    open = false;
                    session = null;
                }
            }
            else if ("auth".equals(tag)) {
                // User is trying to authenticate using SASL
                if (authenticateClient(doc)) {
                    // SASL authentication was successful so open a new stream and offer
                    // resource binding and session establishment (to client sessions only)
                    saslSuccessful();
                }
                else if (connection.isClosed()) {
                    open = false;
                    session = null;
                }
            }
            else if ("compress".equals(tag))
            {
                // Client is trying to initiate compression
                if (compressClient(doc)) {
                    // Compression was successful so open a new stream and offer
                    // resource binding and session establishment (to client sessions only)
                    compressionSuccessful();
                }
            }
            else
            {
                if (!processUnknowPacket(doc)) {
                    Log.warn(LocaleUtils.getLocalizedString("admin.error.packet.tag") +
                            doc.asXML());
                    open = false;
                }
            }
        }
    }

    private boolean authenticateClient(Element doc) throws DocumentException, IOException,
            XmlPullParserException {
        // Ensure that connection was secured if TLS was required
        if (connection.getTlsPolicy() == Connection.TLSPolicy.required &&
                !connection.isSecure()) {
            closeNeverSecuredConnection();
            return false;
        }

        boolean isComplete = false;
        boolean success = false;
        while (!isComplete) {
            SASLAuthentication.Status status = SASLAuthentication.handle(session, doc);
            if (status == SASLAuthentication.Status.needResponse) {
                // Get the next answer since we are not done yet
                doc = reader.parseDocument().getRootElement();
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
    private boolean compressClient(Element doc) throws IOException {
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
            // Indicate client that he can proceed and compress the socket
            connection.deliverRawText("<compressed xmlns='http://jabber.org/protocol/compress'/>");

            // Start using compression
            connection.startCompression();
            return true;
        }

    }

    /**
     * Process the received IQ packet. Registered
     * {@link org.jivesoftware.wildfire.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.<p>
     *
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.
     *
     * @param packet the received packet.
     */
    protected void processIQ(IQ packet) throws UnauthorizedException {
        // Ensure that connection was secured if TLS was required
        if (connection.getTlsPolicy() == Connection.TLSPolicy.required &&
                !connection.isSecure()) {
            closeNeverSecuredConnection();
            return;
        }
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    false);
            router.route(packet);
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    true);
            session.incrementClientPacketCount();
        }
        catch (PacketRejectedException e) {
            // An interceptor rejected this packet so answer a not_allowed error
            IQ reply = new IQ();
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setID(packet.getID());
            reply.setTo(session.getAddress());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_allowed);
            session.process(reply);
            // Check if a message notifying the rejection should be sent
            if (e.getRejectionMessage() != null && e.getRejectionMessage().trim().length() > 0) {
                // A message for the rejection will be sent to the sender of the rejected packet
                Message notification = new Message();
                notification.setTo(session.getAddress());
                notification.setFrom(packet.getTo());
                notification.setBody(e.getRejectionMessage());
                session.process(notification);
            }
        }
    }

    /**
     * Process the received Presence packet. Registered
     * {@link org.jivesoftware.wildfire.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.<p>
     *
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.
     *
     * @param packet the received packet.
     */
    protected void processPresence(Presence packet) throws UnauthorizedException {
        // Ensure that connection was secured if TLS was required
        if (connection.getTlsPolicy() == Connection.TLSPolicy.required &&
                !connection.isSecure()) {
            closeNeverSecuredConnection();
            return;
        }
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    false);
            router.route(packet);
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    true);
            session.incrementClientPacketCount();
        }
        catch (PacketRejectedException e) {
            // An interceptor rejected this packet so answer a not_allowed error
            Presence reply = new Presence();
            reply.setID(packet.getID());
            reply.setTo(session.getAddress());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_allowed);
            session.process(reply);
            // Check if a message notifying the rejection should be sent
            if (e.getRejectionMessage() != null && e.getRejectionMessage().trim().length() > 0) {
                // A message for the rejection will be sent to the sender of the rejected packet
                Message notification = new Message();
                notification.setTo(session.getAddress());
                notification.setFrom(packet.getTo());
                notification.setBody(e.getRejectionMessage());
                session.process(notification);
            }
        }
    }

    /**
     * Process the received Message packet. Registered
     * {@link org.jivesoftware.wildfire.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.<p>
     *
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.
     *
     * @param packet the received packet.
     */
    protected void processMessage(Message packet) throws UnauthorizedException {
        // Ensure that connection was secured if TLS was required
        if (connection.getTlsPolicy() == Connection.TLSPolicy.required &&
                !connection.isSecure()) {
            closeNeverSecuredConnection();
            return;
        }
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    false);
            router.route(packet);
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    true);
            session.incrementClientPacketCount();
        }
        catch (PacketRejectedException e) {
            // An interceptor rejected this packet
            if (e.getRejectionMessage() != null && e.getRejectionMessage().trim().length() > 0) {
                // A message for the rejection will be sent to the sender of the rejected packet
                Message reply = new Message();
                reply.setID(packet.getID());
                reply.setTo(session.getAddress());
                reply.setFrom(packet.getTo());
                reply.setType(packet.getType());
                reply.setThread(packet.getThread());
                reply.setBody(e.getRejectionMessage());
                session.process(reply);
            }
        }
    }

    /**
     * Returns true if a received packet of an unkown type (i.e. not a Message, Presence
     * or IQ) has been processed. If the packet was not processed then an exception will
     * be thrown which will make the thread to stop processing further packets.
     *
     * @param doc the DOM element of an unkown type.
     * @return  true if a received packet has been processed.
     */
    abstract boolean processUnknowPacket(Element doc);

    /**
     * Returns the last time a full Document was read or a heartbeat was received. Hearbeats
     * are represented as whitespaces received while a Document is not being parsed.
     *
     * @return the time in milliseconds when the last document or heartbeat was received.
     */
    long getLastActive() {
        return reader.getLastActive();
    }

    /**
     * Close the connection since TLS was mandatory and the entity never negotiated TLS. Before
     * closing the connection a stream error will be sent to the entity.
     */
    private void closeNeverSecuredConnection() {
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

    private IQ getIQ(Element doc) {
        Element query = doc.element("query");
        if (query != null && "jabber:iq:roster".equals(query.getNamespaceURI())) {
            return new Roster(doc);
        }
        else {
            return new IQ(doc);
        }
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
    private void createSession() throws UnauthorizedException, XmlPullParserException, IOException {
        XmlPullParser xpp = reader.getXPPParser();
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }

        // Check that the TO attribute of the stream header matches the server name or a valid
        // subdomain. If the value of the 'to' attribute is not valid then return a host-unknown
        // error and close the underlying connection.
        String host = reader.getXPPParser().getAttributeValue("", "to");
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
        else if (!createSession(xpp.getNamespace(null))) {
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
        // Check if the host matches a subdomain of this host
        RoutableChannelHandler route = routingTable.getRoute(new JID(host));
        if (route == null || route instanceof OutgoingSessionPromise) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Tries to secure the connection using TLS. If the connection is secured then reset
     * the parser to use the new secured reader. But if the connection failed to be secured
     * then send a <failure> stanza and close the connection.
     *
     * @return true if the connection was secured.
     * @throws IOException if an I/O error occures while parsing the input stream.
     * @throws XmlPullParserException if an error occures while parsing.
     */
    private boolean negotiateTLS() throws IOException, XmlPullParserException {
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
            connection.startTLS(false, null);
        }
        catch (IOException e) {
            Log.error("Error while negotiating TLS", e);
            connection.deliverRawText("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\">");
            connection.close();
            return false;
        }
        XmlPullParser xpp = reader.getXPPParser();
        // Reset the parser to use the new reader
        xpp.setInput(new InputStreamReader(connection.getTLSStreamHandler().getInputStream(), CHARSET));
        // Skip new stream element
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }
        return true;
    }

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
    private void saslSuccessful() throws XmlPullParserException, IOException {
        MXParser xpp = reader.getXPPParser();
        // Reset the parser since a new stream header has been sent from the client
        xpp.resetInput();

        // Skip the opening stream sent by the client
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;)
        {
            eventType = xpp.next();
        }

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
     * After compression was successful we should open a new stream and offer
     * new stream features such as resource binding and session establishment. Notice that
     * resource binding and session establishment should only be offered to clients (i.e. not
     * to servers or external components)
     */
    private void compressionSuccessful() throws XmlPullParserException, IOException
    {
        XmlPullParser xpp = reader.getXPPParser();
        // Reset the parser since a new stream header has been sent from the client
        if (connection.getTLSStreamHandler() == null)
        {
            ZInputStream in = new ZInputStream(socket.getInputStream());
            in.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
            xpp.setInput(new InputStreamReader(in, CHARSET));
        }
        else
        {
            ZInputStream in = new ZInputStream(connection.getTLSStreamHandler().getInputStream());
            in.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
            xpp.setInput(new InputStreamReader(in, CHARSET));
        }

        // Skip the opening stream sent by the client
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;)
        {
            eventType = xpp.next();
        }

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
        if (specificFeatures != null)
        {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        connection.deliverRawText(sb.toString());
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

    private String geStreamHeader() {
        StringBuilder sb = new StringBuilder(200);
        sb.append("<?xml version='1.0' encoding='");
        sb.append(CHARSET);
        sb.append("'?>");
        if (connection.isFlashClient()) {
            sb.append("<flash:stream xmlns:flash=\"http://www.jabber.com/streams/flash\" ");
        } else {
            sb.append("<stream:stream ");
        }
        sb.append("xmlns:stream=\"http://etherx.jabber.org/streams\" xmlns=\"");
        sb.append(getNamespace());
        sb.append("\" from=\"");
        sb.append(session.getServerName());
        sb.append("\" id=\"");
        sb.append(session.getStreamID().toString());
        sb.append("\" xml:lang=\"");
        sb.append(connection.getLanguage());
        sb.append("\" version=\"");
        sb.append(Session.MAJOR_VERSION).append(".").append(Session.MINOR_VERSION);
        sb.append("\">");
        return sb.toString();
    }

    /**
     * Notification message indicating that the SocketReader is shutting down. The thread will
     * stop reading and processing new requests. Subclasses may want to redefine this message
     * for releasing any resource they might need.
     */
    protected void shutdown() {
    }

    /**
     * Creates the appropriate {@link Session} subclass based on the specified namespace.
     *
     * @param namespace the namespace sent in the stream element. eg. jabber:client.
     * @return the created session or null.
     * @throws UnauthorizedException
     * @throws XmlPullParserException
     * @throws IOException
     */
    abstract boolean createSession(String namespace) throws UnauthorizedException,
            XmlPullParserException, IOException;
}
