/**
 * $RCSfile: ServerDialback.java,v $
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.server;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XPPPacketReader;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.AuthFactory;
import org.jivesoftware.messenger.net.DNSUtil;
import org.jivesoftware.messenger.net.SocketConnection;
import org.jivesoftware.messenger.spi.BasicStreamIDFactory;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the Server Dialback method as defined by the RFC3920.
 *
 * The dialback method follows the following logic to validate the remote server:
 * <ol>
 *  <li>The Originating Server establishes a connection to the Receiving Server.</li>
 *  <li>The Originating Server sends a 'key' value over the connection to the Receiving
 *  Server.</li>
 *  <li>The Receiving Server establishes a connection to the Authoritative Server.</li>
 *  <li>The Receiving Server sends the same 'key' value to the Authoritative Server.</li>
 *  <li>The Authoritative Server replies that key is valid or invalid.</li>
 *  <li>The Receiving Server informs the Originating Server whether it is authenticated or
 *  not.</li>
 * </ol>
 *
 * By default a timeout of 20 seconds will be used for reading packets from remote servers. Use
 * the property <b>xmpp.server.read.timeout</b> to change that value. The value should be in
 * milliseconds.
 *
 * @author Gaston Dombiak
 */
class ServerDialback {
    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    protected static String CHARSET = "UTF-8";
    /**
     * Secret key to be used for encoding and decoding keys used for authentication.
     */
    private static final String secretKey = StringUtils.randomString(10);

    private static XmlPullParserFactory FACTORY = null;

    static {
        try {
            FACTORY = XmlPullParserFactory.newInstance();
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

    private Connection connection;
    private String serverName;
    private SessionManager sessionManager = SessionManager.getInstance();
    private RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();

    /**
     * Creates a new instance that will be used for creating {@link IncomingServerSession},
     * validating subsequent domains or authenticatig new domains. Use
     * {@link #createIncomingSession(org.dom4j.io.XPPPacketReader)} for creating a new server
     * session used for receiving packets from the remote server. Use
     * {@link #validateRemoteDomain(org.dom4j.Element, org.jivesoftware.messenger.StreamID)} for
     * validating subsequent domains and use
     * {@link #authenticateDomain(OutgoingServerSocketReader, String, String, String)} for
     * registering new domains that are allowed to send packets to the remote server.<p>
     *
     * For validating domains a new TCP connection will be established to the Authoritative Server.
     * The Authoritative Server may be the same Originating Server or some other machine in the
     * Originating Server's network. Once the remote domain gets validated the Originating Server
     * will be allowed for sending packets to this server. However, this server will need to
     * validate its domain/s with the Originating Server if this server needs to send packets to
     * the Originating Server. Another TCP connection will be established for validation this
     * server domain/s and for sending packets to the Originating Server.
     *
     * @param connection the connection created by the remote server.
     * @param serverName the name of the local server.
     */
    ServerDialback(Connection connection, String serverName) {
        this.connection = connection;
        this.serverName = serverName;
    }

    ServerDialback() {
    }

    /**
     * Creates a new connection from the Originating Server to the Receiving Server for
     * authenticating the specified domain.
     *
     * @param domain domain of the Originating Server to authenticate with the Receiving Server.
     * @param hostname IP address or hostname of the Receiving Server.
     * @param port port of the Receiving Server.
     * @return an OutgoingServerSession if the domain was authenticated or <tt>null</tt> if none.
     */
    public OutgoingServerSession createOutgoingSession(String domain, String hostname, int port) {
        String realHostname = null;
        int realPort = port;
        try {
            // Establish a TCP connection to the Receiving Server
            Log.debug("OS - Trying to connect to " + hostname + ":" + port);
            // Get the real hostname to connect to using DNS lookup of the specified hostname
            DNSUtil.HostAddress address = DNSUtil.resolveXMPPServerDomain(hostname, port);
            realHostname = address.getHost();
            realPort = address.getPort();
            // Connect to the remote server
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(realHostname, realPort),
                    RemoteServerManager.getSocketTimeout());
            Log.debug("OS - Connection to " + hostname + ":" + port + " successful");
            connection =
                    new SocketConnection(XMPPServer.getInstance().getPacketDeliverer(), socket,
                            false);
            // Get a writer for sending the open stream tag
            // Send to the Receiving Server a stream header
            StringBuilder stream = new StringBuilder();
            stream.append("<stream:stream");
            stream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            stream.append(" xmlns=\"jabber:server\"");
            stream.append(" xmlns:db=\"jabber:server:dialback\">");
            connection.deliverRawText(stream.toString());
            stream = null;

            XPPPacketReader reader = new XPPPacketReader();
            reader.setXPPFactory(XmlPullParserFactory.newInstance());
            reader.getXPPParser().setInput(new InputStreamReader(socket.getInputStream(),
                    CHARSET));
            // Get the answer from the Receiving Server
            XmlPullParser xpp = reader.getXPPParser();
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }
            if ("jabber:server:dialback".equals(xpp.getNamespace("db"))) {
                String id = xpp.getAttributeValue("", "id");
                OutgoingServerSocketReader socketReader = new OutgoingServerSocketReader(reader);
                if (authenticateDomain(socketReader, domain, hostname, id)) {
                    // Domain was validated so create a new OutgoingServerSession
                    StreamID streamID = new BasicStreamIDFactory().createStreamID(id);
                    OutgoingServerSession session = new OutgoingServerSession(domain, connection,
                            socketReader, streamID);
                    connection.init(session);
                    // Set the hostname as the address of the session
                    session.setAddress(new JID(null, hostname, null));
                    return session;
                }
                else {
                    // Close the connection
                    connection.close();
                }
            }
            else {
                Log.debug("OS - Invalid namespace in packet: " + xpp.getText());
                // Send an invalid-namespace stream error condition in the response
                StreamError error = new StreamError(StreamError.Condition.invalid_namespace);
                StringBuilder sb = new StringBuilder();
                sb.append(error.toXML());
                connection.deliverRawText(sb.toString());
                // Close the connection
                connection.close();
            }
        }
        catch (IOException e) {
            Log.debug("Error connecting to the remote server: " + hostname + "(DNS lookup: " +
                    realHostname + ":" + realPort + ")", e);
            // Close the connection
            if (connection != null) {
                connection.close();
            }
        }
        catch (Exception e) {
            Log.error("Error creating outgoing session to remote server: " + hostname +
                    "(DNS lookup: " +
                    realHostname +
                    ")",
                    e);
            // Close the connection
            if (connection != null) {
                connection.close();
            }
        }
        return null;
    }

    /**
     * Authenticates the Originating Server domain with the Receiving Server. Once the domain has
     * been authenticated the Receiving Server will start accepting packets from the Originating
     * Server.<p>
     *
     * The Receiving Server will connect to the Authoritative Server to verify the dialback key.
     * Most probably the Originating Server machine will be the Authoritative Server too.
     *
     * @param socketReader the reader to use for reading the answer from the Receiving Server.
     * @param domain the domain to authenticate.
     * @param hostname the hostname of the remote server (i.e. Receiving Server).
     * @param id the stream id to be used for creating the dialback key.
     * @return true if the Receiving Server authenticated the domain with the Authoritative Server.
     */
    public boolean authenticateDomain(OutgoingServerSocketReader socketReader, String domain,
            String hostname, String id) {
        String key = AuthFactory.createDigest(id, secretKey);
        Log.debug("OS - Sent dialback key to host: " + hostname + " id: " + id + " from domain: " +
                domain);

        synchronized (socketReader) {
            // Send a dialback key to the Receiving Server
            StringBuilder sb = new StringBuilder();
            sb.append("<db:result");
            sb.append(" from=\"").append(domain).append("\"");
            sb.append(" to=\"").append(hostname).append("\">");
            sb.append(key);
            sb.append("</db:result>");
            connection.deliverRawText(sb.toString());
            sb = null;

            // Process the answer from the Receiving Server
            try {
                Element doc = socketReader.getElement(RemoteServerManager.getSocketTimeout(),
                        TimeUnit.MILLISECONDS);
                if (doc == null) {
                    Log.debug("OS - Time out waiting for answer in validation from: " + hostname +
                            " id: " +
                            id +
                            " for domain: " +
                            domain);
                    return false;
                }
                else if ("db".equals(doc.getNamespacePrefix()) && "result".equals(doc.getName())) {
                    boolean success = "valid".equals(doc.attributeValue("type"));
                    Log.debug("OS - Validation " + (success ? "GRANTED" : "FAILED") + " from: " +
                            hostname +
                            " id: " +
                            id +
                            " for domain: " +
                            domain);
                    return success;
                }
                else {
                    Log.debug("OS - Unexpected answer in validation from: " + hostname + " id: " +
                            id +
                            " for domain: " +
                            domain +
                            " answer:" +
                            doc.asXML());
                    return false;
                }
            }
            catch (InterruptedException e) {
                Log.debug("OS - Validation FAILED from: " + hostname +
                        " id: " +
                        id +
                        " for domain: " +
                        domain, e);
                return false;
            }
        }
    }

    /**
     * Returns a new {@link IncomingServerSession} with a domain validated by the Authoritative
     * Server. New domains may be added to the returned IncomingServerSession after they have
     * been validated. See
     * {@link IncomingServerSession#validateSubsequentDomain(org.dom4j.Element)}. The remote
     * server will be able to send packets through this session whose domains were previously
     * validated.<p>
     *
     * When acting as an Authoritative Server this method will verify the requested key
     * and will return null since the underlying TCP connection will be closed after sending the
     * response to the Receiving Server.<p>
     *
     * @param reader reader of DOM documents on the connection to the remote server.
     * @return an IncomingServerSession that was previously validated against the remote server.
     * @throws IOException if an I/O error occurs while communicating with the remote server.
     * @throws XmlPullParserException if an error occurs while parsing XML packets.
     */
    public IncomingServerSession createIncomingSession(XPPPacketReader reader) throws IOException,
            XmlPullParserException {
        XmlPullParser xpp = reader.getXPPParser();
        StringBuilder sb;
        StreamError error;
        if ("jabber:server:dialback".equals(xpp.getNamespace("db"))) {

            StreamID streamID = sessionManager.nextStreamID();

            sb = new StringBuilder();
            sb.append("<stream:stream");
            sb.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            sb.append(" xmlns=\"jabber:server\" xmlns:db=\"jabber:server:dialback\"");
            sb.append(" id=\"");
            sb.append(streamID.toString());
            sb.append("\">");
            connection.deliverRawText(sb.toString());

            try {
                Element doc = reader.parseDocument().getRootElement();
                if ("db".equals(doc.getNamespacePrefix()) && "result".equals(doc.getName())) {
                    if (validateRemoteDomain(doc, streamID)) {
                        String hostname = doc.attributeValue("from");
                        String recipient = doc.attributeValue("to");
                        // Create a server Session for the remote server
                        IncomingServerSession session = sessionManager.
                                createIncomingServerSession(connection, streamID);
                        // Set the first validated domain as the address of the session
                        session.setAddress(new JID(null, hostname, null));
                        // Add the validated domain as a valid domain
                        session.addValidatedDomain(hostname);
                        // Set the domain or subdomain of the local server used when
                        // validating the session
                        session.setLocalDomain(recipient);
                        return session;
                    }
                }
                else if ("db".equals(doc.getNamespacePrefix()) && "verify".equals(doc.getName())) {
                    // When acting as an Authoritative Server the Receiving Server will send a
                    // db:verify packet for verifying a key that was previously sent by this
                    // server when acting as the Originating Server
                    verifyReceivedKey(doc, connection);
                    // Close the underlying connection
                    connection.close();
                    String verifyFROM = doc.attributeValue("from");
                    String id = doc.attributeValue("id");
                    Log.debug("AS - Connection closed for host: " + verifyFROM + " id: " + id);
                    sb = null;
                    return null;
                }
                else {
                    // The remote server sent an invalid/unknown packet
                    error = new StreamError(StreamError.Condition.invalid_xml);
                    sb = new StringBuilder();
                    sb.append(error.toXML());
                    connection.deliverRawText(sb.toString());
                    // Close the underlying connection
                    connection.close();
                    return null;
                }
            }
            catch (Exception e) {
                Log.error("An error occured while creating a server session", e);
                // Close the underlying connection
                connection.close();
                return null;
            }

        }
        else {
            // Include the invalid-namespace stream error condition in the response
            error = new StreamError(StreamError.Condition.invalid_namespace);
            sb = new StringBuilder();
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            return null;
        }
        return null;
    }

    /**
     * Returns true if the domain requested by the remote server was validated by the Authoritative
     * Server. To validate the domain a new TCP connection will be established to the
     * Authoritative Server. The Authoritative Server may be the same Originating Server or
     * some other machine in the Originating Server's network.<p>
     *
     * If the domain was not valid or some error occured while validating the domain then the
     * underlying TCP connection will be closed.
     *
     * @param doc the request for validating the new domain.
     * @param streamID the stream id generated by this server for the Originating Server.
     * @return true if the requested domain is valid.
     */
    public boolean validateRemoteDomain(Element doc, StreamID streamID) {
        StreamError error;
        StringBuilder sb;
        String recipient = doc.attributeValue("to");
        String hostname = doc.attributeValue("from");
        Log.debug("RS - Received dialback key from host: " + hostname + " to: " + recipient);
        if (!RemoteServerManager.canAccess(hostname)) {
            // Remote server is not allowed to establish a connection to this server
            error = new StreamError(StreamError.Condition.host_unknown);
            sb = new StringBuilder();
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            Log.debug("RS - Error, hostname is not allowed to establish a connection to " +
                    "this server: " +
                    recipient);
            return false;
        }
        else if (isHostUnknown(recipient)) {
            // address does not match a recognized hostname
            error = new StreamError(StreamError.Condition.host_unknown);
            sb = new StringBuilder();
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            Log.debug("RS - Error, hostname not recognized: " + recipient);
            return false;
        }
        else {
            // Check if the remote server already has a connection to the target domain/subdomain
            boolean alreadyExists = false;
            for (IncomingServerSession session : sessionManager
                    .getIncomingServerSessions(hostname)) {
                if (recipient.equals(session.getLocalDomain())) {
                    alreadyExists = true;
                }
            }
            if (alreadyExists) {
                // Remote server already has a IncomingServerSession created
                error = new StreamError(StreamError.Condition.not_authorized);
                sb = new StringBuilder();
                sb.append(error.toXML());
                connection.deliverRawText(sb.toString());
                // Close the underlying connection
                connection.close();
                Log.debug("RS - Error, incoming connection already exists from: " + hostname);
                return false;
            }
            else {
                String key = doc.getTextTrim();

                DNSUtil.HostAddress address = DNSUtil.resolveXMPPServerDomain(hostname,
                        RemoteServerManager.getPortForServer(hostname));

                try {
                    boolean valid = verifyKey(key, streamID.toString(), recipient, hostname,
                            address.getHost(), address.getPort());

                    Log.debug("RS - Sending key verification result to OS: " + hostname);
                    sb = new StringBuilder();
                    sb.append("<db:result");
                    sb.append(" from=\"").append(recipient).append("\"");
                    sb.append(" to=\"").append(hostname).append("\"");
                    sb.append(" type=\"");
                    sb.append(valid ? "valid" : "invalid");
                    sb.append("\"/>");
                    connection.deliverRawText(sb.toString());

                    if (!valid) {
                        // Close the underlying connection
                        connection.close();
                    }
                    return valid;
                }
                catch (Exception e) {
                    Log.warn("Error verifying key", e);
                    // Send a <remote-connection-failed/> stream error condition
                    // and terminate both the XML stream and the underlying
                    // TCP connection
                    error =
                            new StreamError(StreamError.Condition.remote_connection_failed);
                    sb = new StringBuilder();
                    sb.append(error.toXML());
                    connection.deliverRawText(sb.toString());
                    // Close the underlying connection
                    connection.close();
                    return false;
                }
            }
        }
    }

    private boolean isHostUnknown(String recipient) {
        boolean host_unknown = !serverName.equals(recipient);
        // If the recipient does not match the serverName then check if it matches a subdomain. This
        // trick is useful when subdomains of this server are registered in the DNS so remote
        // servers may establish connections directly to a subdomain of this server
        if (host_unknown && recipient.contains(serverName)) {
            try {
                RoutableChannelHandler route = routingTable.getRoute(new JID(recipient));
                if (route instanceof OutgoingSessionPromise) {
                    host_unknown = true;
                }
                else {
                    host_unknown = false;
                }
            }
            catch (NoSuchRouteException e) {
                host_unknown = true;
            }
        }
        return host_unknown;
    }

    /**
     * Verifies the key with the Authoritative Server.
     */
    private boolean verifyKey(String key, String streamID, String recipient, String hostname,
            String host, int port) throws IOException, XmlPullParserException,
            RemoteConnectionFailedException {
        XPPPacketReader reader = null;
        Writer writer = null;
        StreamError error;
        // Establish a TCP connection back to the domain name asserted by the Originating Server
        Log.debug("RS - Trying to connect to Authoritative Server: " + hostname + ":" + port);
        // Connect to the Authoritative server
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), RemoteServerManager.getSocketTimeout());
        // Set a read timeout
        socket.setSoTimeout(RemoteServerManager.getSocketTimeout());
        Log.debug("RS - Connection to AS: " + hostname + ":" + port + " successful");
        try {
            reader = new XPPPacketReader();
            reader.setXPPFactory(FACTORY);

            reader.getXPPParser().setInput(new InputStreamReader(socket.getInputStream(),
                    CHARSET));
            // Get a writer for sending the open stream tag
            writer =
                    new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                            CHARSET));
            // Send the Authoritative Server a stream header
            StringBuilder stream = new StringBuilder();
            stream.append("<stream:stream");
            stream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            stream.append(" xmlns=\"jabber:server\"");
            stream.append(" xmlns:db=\"jabber:server:dialback\">");
            writer.write(stream.toString());
            writer.flush();
            stream = null;

            // Get the answer from the Authoritative Server
            XmlPullParser xpp = reader.getXPPParser();
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }
            if ("jabber:server:dialback".equals(xpp.getNamespace("db"))) {
                Log.debug("RS - Asking AS to verify dialback key for id" + streamID);
                // Request for verification of the key
                StringBuilder sb = new StringBuilder();
                sb.append("<db:verify");
                sb.append(" from=\"").append(recipient).append("\"");
                sb.append(" to=\"").append(hostname).append("\"");
                sb.append(" id=\"").append(streamID).append("\">");
                sb.append(key);
                sb.append("</db:verify>");
                writer.write(sb.toString());
                writer.flush();
                sb = null;

                try {
                    Element doc = reader.parseDocument().getRootElement();
                    if ("db".equals(doc.getNamespacePrefix()) && "verify".equals(doc.getName())) {
                        if (!streamID.equals(doc.attributeValue("id"))) {
                            // Include the invalid-id stream error condition in the response
                            error = new StreamError(StreamError.Condition.invalid_id);
                            sb = new StringBuilder();
                            sb.append(error.toXML());
                            writer.write(sb.toString());
                            writer.flush();
                            // Thrown an error so <remote-connection-failed/> stream error
                            // condition is sent to the Originating Server
                            throw new RemoteConnectionFailedException("Invalid ID");
                        }
                        else if (isHostUnknown(doc.attributeValue("to"))) {
                            // Include the host-unknown stream error condition in the response
                            error = new StreamError(StreamError.Condition.host_unknown);
                            sb = new StringBuilder();
                            sb.append(error.toXML());
                            writer.write(sb.toString());
                            writer.flush();
                            // Thrown an error so <remote-connection-failed/> stream error
                            // condition is sent to the Originating Server
                            throw new RemoteConnectionFailedException("Host unknown");
                        }
                        else if (!hostname.equals(doc.attributeValue("from"))) {
                            // Include the invalid-from stream error condition in the response
                            error = new StreamError(StreamError.Condition.invalid_from);
                            sb = new StringBuilder();
                            sb.append(error.toXML());
                            writer.write(sb.toString());
                            writer.flush();
                            // Thrown an error so <remote-connection-failed/> stream error
                            // condition is sent to the Originating Server
                            throw new RemoteConnectionFailedException("Invalid From");
                        }
                        else {
                            boolean valid = "valid".equals(doc.attributeValue("type"));
                            Log.debug("RS - Key was " + (valid ? "" : "NOT ") +
                                    "VERIFIED by the Authoritative Server for: " +
                                    hostname);
                            return valid;
                        }
                    }
                    else {
                        Log.debug("db:verify answer was: " + doc.asXML());
                    }
                }
                catch (DocumentException e) {
                    Log.error("An error occured connecting to the Authoritative Server", e);
                    // Thrown an error so <remote-connection-failed/> stream error condition is
                    // sent to the Originating Server
                    throw new RemoteConnectionFailedException("Error connecting to the Authoritative Server");
                }

            }
            else {
                // Include the invalid-namespace stream error condition in the response
                error = new StreamError(StreamError.Condition.invalid_namespace);
                StringBuilder sb = new StringBuilder();
                sb.append(error.toXML());
                writer.write(sb.toString());
                writer.flush();
                // Thrown an error so <remote-connection-failed/> stream error condition is
                // sent to the Originating Server
                throw new RemoteConnectionFailedException("Invalid namespace");
            }
        }
        finally {
            try {
                Log.debug("RS - Closing connection to Authoritative Server: " + hostname);
                // Close the stream
                StringBuilder sb = new StringBuilder();
                sb.append("</stream:stream>");
                writer.write(sb.toString());
                writer.flush();
                // Close the TCP connection
                socket.close();
            }
            catch (IOException ioe) {
            }
        }
        return false;
    }

    /**
     * Verifies the key sent by a Receiving Server. This server will be acting as the
     * Authoritative Server when executing this method. The remote server may have established
     * a new connection to the Authoritative Server (i.e. this server) for verifying the key
     * or it may be reusing an existing incoming connection.
     *
     * @param doc the Element that contains the key to verify.
     * @param connection the connection to use for sending the verification result
     * @return true if the key was verified.
     */
    public static boolean verifyReceivedKey(Element doc, Connection connection) {
        String verifyFROM = doc.attributeValue("from");
        String verifyTO = doc.attributeValue("to");
        String key = doc.getTextTrim();
        String id = doc.attributeValue("id");
        Log.debug("AS - Verifying key for host: " + verifyFROM + " id: " + id);

        // TODO If the value of the 'to' address does not match a recognized hostname,
        // then generate a <host-unknown/> stream error condition
        // TODO If the value of the 'from' address does not match the hostname
        // represented by the Receiving Server when opening the TCP connection, then
        // generate an <invalid-from/> stream error condition

        // Verify the received key
        // Created the expected key based on the received ID value and the shared secret
        String expectedKey = AuthFactory.createDigest(id, secretKey);
        boolean verified = expectedKey.equals(key);

        // Send the result of the key verification
        StringBuilder sb = new StringBuilder();
        sb.append("<db:verify");
        sb.append(" from=\"").append(verifyTO).append("\"");
        sb.append(" to=\"").append(verifyFROM).append("\"");
        sb.append(" type=\"");
        sb.append(verified ? "valid" : "invalid");
        sb.append("\" id=\"").append(id).append("\"/>");
        connection.deliverRawText(sb.toString());
        Log.debug("AS - Key was: " + (verified ? "VALID" : "INVALID") + " for host: " +
                verifyFROM +
                " id: " +
                id);
        return verified;
    }
}

