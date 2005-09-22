/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.net;

import org.dom4j.Element;
import org.dom4j.io.XPPPacketReader;
import org.jivesoftware.messenger.PacketRouter;
import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.interceptor.InterceptorManager;
import org.jivesoftware.messenger.interceptor.PacketRejectedException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketException;

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
    XPPPacketReader reader = null;
    protected boolean open;

    static {
        try {
            factory = XmlPullParserFactory.newInstance();
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
    }

    /**
     * A dedicated thread loop for reading the stream and sending incoming
     * packets to the appropriate router.
     */
    public void run() {
        try {
            reader = new XPPPacketReader();
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
        catch (XmlPullParserException ie) {
            // It is normal for clients to abruptly cut a connection
            // rather than closing the stream document. Since this is
            // normal behavior, we won't log it as an error.
            // Log.error(LocaleUtils.getLocalizedString("admin.disconnect"),ie);
        }
        catch (Exception e) {
            if (session != null) {
                Log.warn(LocaleUtils.getLocalizedString("admin.error.stream"), e);
            }
        }
        finally {
            if (session != null) {
                Log.debug("Logging off " + session.getAddress() + " on " + connection);
                try {
                    session.getConnection().close();
                }
                catch (Exception e) {
                    Log.warn(LocaleUtils.getLocalizedString("admin.error.connection")
                            + "\n" + socket.toString());
                }
            }
            else {
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
                Message packet = null;
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
                Presence packet = null;
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
                try {
                    packet.getType();
                }
                catch (IllegalArgumentException e) {
                    Log.warn("Invalid presence type", e);
                    // The presence packet contains an invalid presence type so replace it with
                    // an available presence type
                    packet.setType(null);
                }
                processPresence(packet);
            }
            else if ("iq".equals(tag)) {
                IQ packet = null;
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
                // Client requested to secure the connection using TLS
                connection.deliverRawText("<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");
                // Negotiate TLS
                if (negotiateTLS()) {
                    tlsNegotiated();
                }
                else {
                    open = false;
                    session = null;
                }
                continue;
            }
            else if ("auth".equals(tag)) {
                // User is trying to authenticate using SASL
                SASLAuthentication saslAuth = new SASLAuthentication(session, reader);
                if (saslAuth.doHandshake(doc)) {
                    // SASL authentication was successful so open a new stream and offer
                    // resource binding and session establishment (to client sessions only)
                    saslSuccessful();
                }
                else {
                    open = false;
                    session = null;
                }
                continue;
            }
			else {
                if (!processUnknowPacket(doc)) {
                    Log.warn(LocaleUtils.getLocalizedString("admin.error.packet.tag") +
                            doc.asXML());
                    open = false;
                }
            }
        }
    }

    /**
     * Process the received IQ packet. Registered
     * {@link org.jivesoftware.messenger.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.<p>
     *
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.
     *
     * @param packet the received packet.
     */
    protected void processIQ(IQ packet) throws UnauthorizedException {
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
     * {@link org.jivesoftware.messenger.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.<p>
     *
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.
     *
     * @param packet the received packet.
     */
    protected void processPresence(Presence packet) throws UnauthorizedException {
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
     * {@link org.jivesoftware.messenger.interceptor.PacketInterceptor} will be invoked before
     * and after the packet was routed.<p>
     *
     * Subclasses may redefine this method for different reasons such as modifying the sender
     * of the packet to avoid spoofing, rejecting the packet or even process the packet in
     * another thread.
     *
     * @param packet the received packet.
     */
    protected void processMessage(Message packet) throws UnauthorizedException {
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

        // Create the correct session based on the sent namespace. At this point the server
        // may offer the client to secure the connection. If the client decides to secure
        // the connection then a <starttls> stanza should be received
        if (!createSession(xpp.getNamespace(null))) {
            // No session was created because of an invalid namespace prefix so answer a stream
            // error and close the underlying connection
            Writer writer = connection.getWriter();
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version='1.0' encoding='");
            sb.append(CHARSET);
            sb.append("'?>");
            // Include the bad-namespace-prefix in the response
            StreamError error = new StreamError(StreamError.Condition.bad_namespace_prefix);
            sb.append(error.toXML());
            writer.write(sb.toString());
            writer.flush();
            // Close the underlying connection
            connection.close();
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
        // Negotiate TLS.
        try {
            connection.startTLS(false);
        }
        catch (IOException e) {
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
        StringBuilder sb = new StringBuilder();
        sb.append(geStreamHeader());
        sb.append("<stream:features>");
        // Include available SASL Mechanisms
        sb.append(SASLAuthentication.getSASLMechanisms(session));
        // Include specific features such as auth and register for client sessions
        String specificFeatures = getAvailableStreamFeatures();
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
        StringBuilder sb = new StringBuilder();
        sb.append(geStreamHeader());
        sb.append("<stream:features>");

        // Include specific features such as resource binding and session establishment
        // for client sessions
        String specificFeatures = getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        connection.deliverRawText(sb.toString());


        XmlPullParser xpp = reader.getXPPParser();
        // Reset the parser since a new stream header has been sent from the client
        if (connection.getTLSStreamHandler() == null) {
            xpp.setInput(new InputStreamReader(socket.getInputStream(), CHARSET));
        }
        else {
            xpp.setInput(new InputStreamReader(connection.getTLSStreamHandler().getInputStream(),
                    CHARSET));
        }

        // Skip the opening stream sent by the client
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }
    }

    /**
     * Returns a text with the available stream features. Each subclass may return different
     * values depending whether the session has been authenticated or not.
     *
     * @return a text with the available stream features or <tt>null</tt> to add nothing.
     */
    abstract String getAvailableStreamFeatures();

    /**
     * Returns the stream namespace. (E.g. jabber:client, jabber:server, etc.).
     *
     * @return the stream namespace.
     */
    abstract String getNamespace();

    private String geStreamHeader() {
        StringBuilder sb = new StringBuilder();
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
