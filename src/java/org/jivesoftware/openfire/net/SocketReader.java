/**
 * $RCSfile$
 * $Revision: 3187 $
 * $Date: 2005-12-11 13:34:34 -0300 (Sun, 11 Dec 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.net;

import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.session.Session;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.*;

import java.io.IOException;
import java.net.Socket;

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

    /**
     * Session associated with the socket reader.
     */
    protected Session session;
    /**
     * Reference to the physical connection.
     */
    protected SocketConnection connection;
    /**
     * Server name for which we are attending clients.
     */
    protected String serverName;

    /**
     * Router used to route incoming packets to the correct channels.
     */
    private PacketRouter router;
    /**
     * Routing table used for checking whether a domain is known or not.
     */
    private RoutingTable routingTable;
    /**
     * Specifies whether the socket is using blocking or non-blocking connections.
     */
    private SocketReadingMode readingMode;
    XMPPPacketReader reader = null;
    protected boolean open;

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
     * @param routingTable the table that keeps routes to registered services.
     * @param serverName the name of the server this socket is working for.
     * @param socket the socket to read from.
     * @param connection the connection being read.
     * @param useBlockingMode true means that the server will use a thread per connection.
     */
    public SocketReader(PacketRouter router, RoutingTable routingTable, String serverName,
            Socket socket, SocketConnection connection, boolean useBlockingMode) {
        this.serverName = serverName;
        this.router = router;
        this.routingTable = routingTable;
        this.connection = connection;

        connection.setSocketReader(this);

        // Reader is associated with a new XMPPPacketReader
        reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);

        // Set the blocking reading mode to use
        readingMode = new BlockingReadingMode(socket, this);
    }

    /**
     * A dedicated thread loop for reading the stream and sending incoming
     * packets to the appropriate router.
     */
    public void run() {
        readingMode.run();
    }

    protected void process(Element doc) throws Exception {
        if (doc == null) {
            return;
        }

        String tag = doc.getName();
        if ("message".equals(tag)) {
            Message packet;
            try {
                packet = new Message(doc);
            }
            catch(IllegalArgumentException e) {
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
                packet = new Presence(doc);
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
                return;
            }
            processPresence(packet);
        }
        else if ("iq".equals(tag)) {
            IQ packet;
            try {
                packet = getIQ(doc);
            }
            catch(IllegalArgumentException e) {
                Log.debug("Rejecting packet. JID malformed", e);
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
                return;
            }
            processIQ(packet);
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

    /**
     * Process the received IQ packet. Registered
     * {@link org.jivesoftware.openfire.interceptor.PacketInterceptor} will be invoked before
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
        router.route(packet);
        session.incrementClientPacketCount();
    }

    /**
     * Process the received Presence packet. Registered
     * {@link org.jivesoftware.openfire.interceptor.PacketInterceptor} will be invoked before
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
        router.route(packet);
        session.incrementClientPacketCount();
    }

    /**
     * Process the received Message packet. Registered
     * {@link org.jivesoftware.openfire.interceptor.PacketInterceptor} will be invoked before
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
        router.route(packet);
        session.incrementClientPacketCount();
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
     * Returns a name that identifies the type of reader and the unique instance.
     *
     * @return a name that identifies the type of reader and the unique instance.
     */
    abstract String getName();

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
    protected void createSession()
            throws UnauthorizedException, XmlPullParserException, IOException {
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
     * Notification message indicating that the SocketReader is shutting down. The thread will
     * stop reading and processing new requests. Subclasses may want to redefine this message
     * for releasing any resource they might need.
     */
    protected void shutdown() {
    }

    /**
     * Creates the appropriate {@link org.jivesoftware.openfire.session.Session} subclass based on the specified namespace.
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
