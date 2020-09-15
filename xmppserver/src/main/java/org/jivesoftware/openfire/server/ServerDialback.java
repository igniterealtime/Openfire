/*
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

package org.jivesoftware.openfire.server;

import java.io.*;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.net.*;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.IncomingServerSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.openfire.event.ServerSessionEventDispatcher;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.StreamError;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

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
public class ServerDialback {
    private enum VerifyResult {
        decline, // For some reason, we declined to do the verify.
        error,   // Remote error from the authoritative server.
        valid,   // Explicitly valid.
        invalid  // Invalid.
    }
    private static final Logger Log = LoggerFactory.getLogger(ServerDialback.class);

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    protected static String CHARSET = "UTF-8";
    /**
     * Cache (unlimited, never expire) that holds the secret key to be used for
     * encoding and decoding keys used for authentication.
     * Key: constant hard coded value, Value: random generated string
     */
    private static Cache<String, String> secretKeyCache;

    private static XmlPullParserFactory FACTORY = null;

    static {
        try {
            FACTORY = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
        secretKeyCache = CacheFactory.createCache("Secret Keys Cache");
    }

    private Connection connection;
    private String serverName;
    private SessionManager sessionManager = SessionManager.getInstance();
    private RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();

    /**
     * Returns true if server dialback is enabled. When enabled remote servers may connect to this
     * server using the server dialback method and this server may try the server dialback method
     * to connect to remote servers.<p>
     *
     * When TLS is enabled between servers and server dialback method is enabled then TLS is going
     * to be tried first, when connecting to a remote server, and if TLS fails then server dialback
     * is going to be used as a last resort. If enabled and the remote server offered server-dialback
     * after TLS and no SASL EXTERNAL then server dialback will be used.
     *
     * @return true if server dialback is enabled.
     */
    public static boolean isEnabled() {
        return JiveGlobals.getBooleanProperty(ConnectionSettings.Server.DIALBACK_ENABLED, true);
    }

    /**
     * Returns true if server dialback can be used when the remote server presented a self-signed
     * certificate. During TLS the remote server can present a self-signed certificate, if this
     * setting is enabled then the self-signed certificate will be accepted and if SASL EXTERNAL
     * is not offered then server dialback will be used for verifying the remote server.<p>
     *
     * If self-signed certificates are accepted then server dialback over TLS is enabled.
     *
     * @return true if server dialback can be used when the remote server presented a self-signed
     * certificate.
     */
    public static boolean isEnabledForSelfSigned() {
        return JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, false);
    }

    /**
     * Sets if server dialback can be used when the remote server presented a self-signed
     * certificate. During TLS the remote server can present a self-signed certificate, if this
     * setting is enabled then the self-signed certificate will be accepted and if SASL EXTERNAL
     * is not offered then server dialback will be used for verifying the remote server.<p>
     *
     * If self-signed certificates are accepted then server dialback over TLS is enabled.
     *
     * @param enabled if server dialback can be used when the remote server presented a self-signed
     * certificate.
     */
    public static void setEnabledForSelfSigned(boolean enabled) {
        JiveGlobals.setProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, Boolean.toString(enabled));
    }

    /**
     * Creates a new instance that will be used for creating {@link IncomingServerSession},
     * validating subsequent domains or authenticatig new domains. Use
     * {@link #createIncomingSession(org.dom4j.io.XMPPPacketReader)} for creating a new server
     * session used for receiving packets from the remote server. Use
     * {@link #validateRemoteDomain(org.dom4j.Element, org.jivesoftware.openfire.StreamID)} for
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
    public ServerDialback(Connection connection, String serverName) {
        this.connection = connection;
        this.serverName = serverName;
    }

    public ServerDialback() {
    }

    /**
     * Creates a new connection from the Originating Server to the Receiving Server for
     * authenticating the specified domain.
     *
     * @param localDomain domain of the Originating Server to authenticate with the Receiving Server.
     * @param remoteDomain IP address or hostname of the Receiving Server.
     * @param port port of the Receiving Server.
     * @return an OutgoingServerSession if the domain was authenticated or {@code null} if none.
     */
    public LocalOutgoingServerSession createOutgoingSession(String localDomain, String remoteDomain, int port) {
        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Acting as Originating Server: Create Outgoing Session from: " + localDomain + " to RS at: " + remoteDomain + " (port: " + port+ ")]" );

        log.debug( "Creating new outgoing session..." );

        String hostname = null;
        int realPort = port;
        try {
            // Establish a TCP connection to the Receiving Server
            final Map.Entry<Socket, Boolean> socketToXmppDomain = SocketUtil.createSocketToXmppDomain( remoteDomain, port );
            if ( socketToXmppDomain  == null ) {
                log.info( "Unable to create new outgoing session: Cannot create a plain socket connection with any applicable remote host." );
                return null;
            }

            final Socket socket = socketToXmppDomain.getKey();
            final boolean directTLS = socketToXmppDomain.getValue();
            connection = new SocketConnection(XMPPServer.getInstance().getPacketDeliverer(), socket, false);
            if (directTLS) {
                connection.startTLS( false, directTLS );
            }

            log.debug( "Send the stream header and wait for response..." );
            StringBuilder stream = new StringBuilder();
            stream.append("<stream:stream");
            stream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            stream.append(" xmlns=\"jabber:server\"");
            stream.append(" to=\"").append(remoteDomain).append("\"");
            stream.append(" from=\"").append(localDomain).append("\"");
            stream.append(" xmlns:db=\"jabber:server:dialback\"");
            stream.append(">");
            connection.deliverRawText(stream.toString());

            // Set a read timeout (of 5 seconds) so we don't keep waiting forever
            int soTimeout = socket.getSoTimeout();
            socket.setSoTimeout(RemoteServerManager.getSocketTimeout());

            XMPPPacketReader reader = new XMPPPacketReader();
            reader.setXPPFactory(FACTORY);

            final InputStream input;
            if (directTLS) {
                input = ((SocketConnection)connection).getTLSStreamHandler().getInputStream();
            } else {
                input = socket.getInputStream();
            }
            reader.getXPPParser().setInput(new InputStreamReader(
                    ServerTrafficCounter.wrapInputStream(input), CHARSET));
            // Get the answer from the Receiving Server
            XmlPullParser xpp = reader.getXPPParser();
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }
            log.debug( "Got a response. Check if the remote server supports dialback..." );

            if ("jabber:server:dialback".equals(xpp.getNamespace("db"))) {
                log.debug( "Dialback seems to be supported by the remote server." );

                // Restore default timeout
                socket.setSoTimeout(soTimeout);
                String id = xpp.getAttributeValue("", "id");
                OutgoingServerSocketReader socketReader = new OutgoingServerSocketReader(reader);
                if (authenticateDomain(socketReader, localDomain, remoteDomain, id)) {
                    log.debug( "Successfully authenticated the connection with dialback." );
                    // Domain was validated so create a new OutgoingServerSession
                    StreamID streamID = BasicStreamIDFactory.createStreamID(id);
                    LocalOutgoingServerSession session = new LocalOutgoingServerSession(localDomain, connection, socketReader, streamID);
                    connection.init(session);
                    // Set the hostname as the address of the session
                    session.setAddress(new JID(null, remoteDomain, null));
                    log.debug( "Successfully created new outgoing session!" );
                    return session;
                }
                else {
                    log.debug( "Failed to authenticate the connection with dialback." );
                    // Close the connection
                    connection.close();
                }
            }
            else {
                log.debug("Error! Invalid namespace in packet: '{}'. Closing connection.", xpp.getText() );
                // Send an invalid-namespace stream error condition in the response
                connection.deliverRawText(
                        new StreamError(StreamError.Condition.invalid_namespace).toXML());
                // Close the connection
                connection.close();
            }
        }
        catch (Exception e) {
            log.error( "An exception occurred while creating outgoing session to remote server:", e );
            // Close the connection
            if (connection != null) {
                connection.close();
            }
        }
        log.warn( "Unable to create a new outgoing session" );
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
     * @param localDomain the domain to authenticate.
     * @param remoteDomain the domain of the remote server (i.e. Receiving Server).
     * @param id the stream id to be used for creating the dialback key.
     * @return true if the Receiving Server authenticated the domain with the Authoritative Server.
     */
    public boolean authenticateDomain(OutgoingServerSocketReader socketReader, String localDomain, String remoteDomain, String id) {

        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Acting as Originating Server: Authenticate domain: " + localDomain + " with RS: " + remoteDomain + " (id: " + id + ")]" );

        log.debug( "Authenticating domain ..." );

        String key = AuthFactory.createDigest( id, getSecretkey() );

        synchronized (socketReader) {
            log.debug( "Sending dialback key and wait for the validation response..." );
            StringBuilder sb = new StringBuilder();
            sb.append("<db:result");
            sb.append(" from=\"").append(localDomain).append("\"");
            sb.append(" to=\"").append(remoteDomain).append("\">");
            sb.append(key);
            sb.append("</db:result>");
            connection.deliverRawText(sb.toString());

            // Process the answer from the Receiving Server
            try {
                while (true) {
                    Element doc = socketReader.getElement(RemoteServerManager.getSocketTimeout(), TimeUnit.MILLISECONDS);
                    if (doc == null) {
                        log.debug( "Failed to authenticate domain: Time out waiting for validation response." );
                        return false;
                    }
                    else if ("db".equals(doc.getNamespacePrefix()) && "result".equals(doc.getName())) {
                        if ( "valid".equals(doc.attributeValue("type")) ) {
                            log.debug( "Authenticated succeeded!" );
                            return true;
                        } else {
                            log.debug( "Failed to authenticate domain: the validation response was received, but did not grant authentication." );
                            return false;
                        }
                    }
                    else {
                        log.warn( "Ignoring unexpected answer while waiting for dialback validation: " + doc.asXML() );
                    }
                }
            }
            catch (InterruptedException e) {
                log.debug( "Failed to authenticate domain: An interrupt was received while waiting for validation response (is Openfire shutting down?)" );
                return false;
            }
        }
    }

    /**
     * Returns a new {@link IncomingServerSession} with a domain validated by the Authoritative
     * Server. New domains may be added to the returned IncomingServerSession after they have
     * been validated. See
     * {@link LocalIncomingServerSession#validateSubsequentDomain(org.dom4j.Element)}. The remote
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
    public LocalIncomingServerSession createIncomingSession(XMPPPacketReader reader) throws IOException,
            XmlPullParserException {
        XmlPullParser xpp = reader.getXPPParser();
        StringBuilder sb;
        if ("jabber:server:dialback".equals(xpp.getNamespace("db"))) {
            Log.debug("ServerDialback: Processing incoming session.");

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
                    String hostname = doc.attributeValue("from");
                    String recipient = doc.attributeValue("to");
                    Log.debug("ServerDialback: RS - Validating remote domain for incoming session from {} to {}", hostname, recipient);
                    if (validateRemoteDomain(doc, streamID)) {
                        Log.debug("ServerDialback: RS - Validation of remote domain for incoming session from {} to {} was successful.", hostname, recipient);
                        // Create a server Session for the remote server
                        LocalIncomingServerSession session = sessionManager.
                                createIncomingServerSession(connection, streamID, hostname);
                        // Add the validated domain as a valid domain
                        session.addValidatedDomain(hostname);
                        // Set the domain or subdomain of the local server used when
                        // validating the session
                        session.setLocalDomain(recipient);
                        // After the session has been created, inform all listeners as well.
                        ServerSessionEventDispatcher.dispatchEvent(session, ServerSessionEventDispatcher.EventType.session_created);
                        return session;
                    } else {
                        Log.debug("ServerDialback: RS - Validation of remote domain for incoming session from {} to {} was not successful.", hostname, recipient);
                        return null;
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
                    Log.debug("ServerDialback: AS - Connection closed for host: " + verifyFROM + " id: " + id);
                    return null;
                }
                else {
                    Log.debug("ServerDialback: Received an invalid/unknown packet while trying to process an incoming session: {}", doc.asXML());
                    // The remote server sent an invalid/unknown packet
                    connection.deliverRawText(
                            new StreamError(StreamError.Condition.invalid_xml).toXML());
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
            Log.debug("ServerDialback: Received a stanza in an invalid namespace while trying to process an incoming session: {}", xpp.getNamespace("db"));
            connection.deliverRawText(
                    new StreamError(StreamError.Condition.invalid_namespace).toXML());
            // Close the underlying connection
            connection.close();
            return null;
        }
    }
    
    /**
     * Send a dialback error.
     * 
     * @param from From
     * @param to To
     * @param err Error type.
     */
    protected void dialbackError(String from, String to, PacketError err) {
        connection.deliverRawText("<db:result type=\"error\" from=\"" + from + "\" to=\"" + to + "\">" + err.toXML() + "</db:result>");
    }

    /**
     * Returns true if the domain requested by the remote server was validated by the Authoritative
     * Server. To validate the domain a new TCP connection will be established to the
     * Authoritative Server. The Authoritative Server may be the same Originating Server or
     * some other machine in the Originating Server's network.<p>
     *
     * If the domain was not valid or some error occurred while validating the domain then the
     * underlying TCP connection may be closed.
     *
     * @param doc the request for validating the new domain.
     * @param streamID the stream id generated by this server for the Originating Server.
     * @return true if the requested domain is valid.
     */
    public boolean validateRemoteDomain(Element doc, StreamID streamID) {
        StringBuilder sb;
        String recipient = doc.attributeValue("to");
        String remoteDomain = doc.attributeValue("from");

        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Acting as Receiving Server: Validate domain:" + recipient + "(id " + streamID + ") for OS: " + remoteDomain + "]" );

        log.debug( "Validating domain...");
        if (connection.getTlsPolicy() == Connection.TLSPolicy.required &&
                !connection.isSecure()) {
            connection.deliverRawText(new StreamError(StreamError.Condition.policy_violation).toXML());
            // Close the underlying connection
            connection.close();
            return false;
        }

        if (!RemoteServerManager.canAccess(remoteDomain)) {
            connection.deliverRawText(new StreamError(StreamError.Condition.policy_violation).toXML());
            // Close the underlying connection
            connection.close();
            log.debug( "Unable to validate domain: Remote domain is not allowed to establish a connection to this server." );
            return false;
        }
        else if (isHostUnknown(recipient)) {
            dialbackError(recipient, remoteDomain, new PacketError(PacketError.Condition.item_not_found, PacketError.Type.cancel, "Service not hosted here"));
            log.debug( "Unable to validate domain: recipient not recognized as a local domain." );
            return false;
        }
        else {
            log.debug( "Check if the remote domain already has a connection to the target domain/subdomain" );
            boolean alreadyExists = false;
            for (IncomingServerSession session : sessionManager.getIncomingServerSessions(remoteDomain)) {
                if (recipient.equals(session.getLocalDomain())) {
                    alreadyExists = true;
                }
            }
            if (alreadyExists && !sessionManager.isMultipleServerConnectionsAllowed()) {
                dialbackError(recipient, remoteDomain, new PacketError(PacketError.Condition.resource_constraint, PacketError.Type.cancel, "Incoming session already exists"));
                log.debug( "Unable to validate domain: An incoming connection already exists from this remote domain, and multiple connections are not allowed." );
                return false;
            }
            else {
                log.debug( "Checking to see if the remote server provides stronger authentication based on SASL. If that's the case, dialback-based authentication can be skipped." );
                if (SASLAuthentication.verifyCertificates(connection.getPeerCertificates(), remoteDomain, true)) {
                    log.debug( "Host authenticated based on SASL. Weaker dialback-based authentication is skipped." );
                    sb = new StringBuilder();
                    sb.append("<db:result");
                    sb.append(" from=\"").append(recipient).append("\"");
                    sb.append(" to=\"").append(remoteDomain).append("\"");
                    sb.append(" type=\"valid\"");
                    sb.append("/>");
                    connection.deliverRawText(sb.toString());

                    log.debug( "Domain validated successfully!" );
                    return true;
                }

                log.debug( "Unable to authenticate host based on stronger SASL. Proceeding with dialback..." );

                String key = doc.getTextTrim();

                final Map.Entry<Socket, Boolean> socketToXmppDomain = SocketUtil.createSocketToXmppDomain( remoteDomain, RemoteServerManager.getPortForServer(remoteDomain) );

                if ( socketToXmppDomain == null )
                {
                    log.debug( "Unable to validate domain: No server available for verifying key of remote server." );
                    dialbackError(recipient, remoteDomain, new PacketError(PacketError.Condition.remote_server_not_found, PacketError.Type.cancel, "Unable to connect to authoritative server"));
                    return false;
                }

                Socket socket = socketToXmppDomain.getKey();
                boolean directTLS = socketToXmppDomain.getValue();

                VerifyResult result;
                try {
                    log.debug( "Verifying dialback key..." );
                    final SocketAddress socketAddress = socket.getRemoteSocketAddress();
                    log.debug( "Opening a new connection to {} {}.", socketAddress, directTLS ? "using directTLS" : "that is initially not encrypted" );
                    try {
                        result = verifyKey( key, streamID, recipient, remoteDomain, socket, directTLS, directTLS );
                    }
                    catch (SSLHandshakeException e)
                    {
                        result = VerifyResult.error;
                        log.debug( "Verification of dialback key failed due to TLS failure.", e );

                        // The receiving entity is expected to close the socket *without* sending any more data (<failure/> nor </stream>).
                        // It is probably (see OF-794) best if we, as the initiating entity, therefor don't send any data either.
                        final SocketAddress oldAddress = socket.getRemoteSocketAddress();
                        socket.close();

                        if ( !directTLS )
                        {
                            log.debug( "Retry without StartTLS... Re-opening socket (with the same remote peer)..." );

                            // Retry, without TLS.
                            socket = new Socket();
                            socket.connect( oldAddress, RemoteServerManager.getSocketTimeout() );
                            log.debug( "Successfully re-opened socket! Try to validate dialback key again (without TLS this time)..." );

                            result = verifyKey( key, streamID, recipient, remoteDomain, socket, true, directTLS );
                        }
                    } catch ( SSLException ex ) {
                        if ( JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ON_PLAIN_DETECTION_ALLOW_NONDIRECTTLS_FALLBACK, true) && ex.getMessage().contains( "plaintext connection?" ) ) {
                            Log.warn( "Plaintext detected on a new connection that is was started in DirectTLS mode (socket address: {}). Attempting to restart the connection in non-DirectTLS mode.", socketAddress );
                            try {
                                // Close old socket
                                socket.close();
                            } catch ( Exception e ) {
                                Log.debug( "An exception occurred (and is ignored) while trying to close a socket that was already in an error state.", e );
                            }
                            socket = new Socket();
                            socket.connect( socketAddress, RemoteServerManager.getSocketTimeout() );
                            result = verifyKey( key, streamID, recipient, remoteDomain, socket, false, false );
                            directTLS = false; // No error this time? directTLS apparently is 'false'. Change it's value for future usage (if any)
                            Log.info( "Re-established connection to {}. Proceeding without directTLS.", socketAddress );
                        } else {
                            // Do not retry as non-DirectTLS, rethrow the exception.
                            throw ex;
                        }
                    }

                    switch(result) {
                    case valid:
                    case invalid:
                        boolean valid = (result == VerifyResult.valid);
                        log.debug( "Dialback key is " + (valid? "valid":"invalid") + ". Sending verification result to remote domain." );
                        sb = new StringBuilder();
                        sb.append("<db:result");
                        sb.append(" from=\"").append(recipient).append("\"");
                        sb.append(" to=\"").append(remoteDomain).append("\"");
                        sb.append(" type=\"");
                        sb.append(valid ? "valid" : "invalid");
                        sb.append("\"/>");
                        connection.deliverRawText(sb.toString());
    
                        if (!valid) {
                            log.debug( "Close the underlying connection as key verification failed." );
                            connection.close();
                            log.debug( "Unable to validate domain: dialback key is invalid." );
                            return false;
                        } else {
                            log.debug( "Successfully validated domain!" );
                            return true;
                        }
                    default:
                        break;
                    }
                    log.debug( "Unable to validate domain: key verification did not complete (the AS likely returned an error or a time out occurred)." );
                    dialbackError( recipient, remoteDomain, new PacketError( PacketError.Condition.remote_server_timeout, PacketError.Type.cancel, "Authoritative server returned error" ) );
                    return false;
                }
                catch (Exception e) {
                    dialbackError(recipient, remoteDomain, new PacketError(PacketError.Condition.remote_server_timeout, PacketError.Type.cancel, "Authoritative server failed"));
                    log.warn( "Unable to validate domain: An exception occurred while verifying the dialback key.", e );
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
            host_unknown = !routingTable.hasComponentRoute(new JID(recipient));
        }
        return host_unknown;
    }

    private VerifyResult sendVerifyKey(String key, StreamID streamID, String recipient, String remoteDomain, Writer writer, XMPPPacketReader reader, Socket socket, boolean skipTLS, boolean directTLS) throws IOException, XmlPullParserException, RemoteConnectionFailedException {
        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Acting as Receiving Server: Verify key with AS: " + remoteDomain + " for OS: " + recipient + " (id " + streamID + ")]" );

        VerifyResult result = VerifyResult.error;

        final ConnectionManagerImpl connectionManager = ( (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager() );
        final TLSStreamHandler tlsStreamHandler = new TLSStreamHandler( socket, connectionManager.getListener( ConnectionType.SOCKET_S2S, directTLS ).generateConnectionConfiguration(), true );

        if ( directTLS ) {
            // Start handshake
            log.debug( "Starting Direct TLS handshake." );
            tlsStreamHandler.start();

            // Use new wrapped writers
            writer = new BufferedWriter( new OutputStreamWriter( tlsStreamHandler.getOutputStream(), StandardCharsets.UTF_8 ) );
            reader.getXPPParser().setInput( new InputStreamReader( tlsStreamHandler.getInputStream(), StandardCharsets.UTF_8 ) );
        }

        log.debug( "Send the Authoritative Server a stream header and wait for answer." );
        StringBuilder stream = new StringBuilder();
        stream.append("<stream:stream");
        stream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
        stream.append(" xmlns=\"jabber:server\"");
        stream.append(" xmlns:db=\"jabber:server:dialback\"");
        stream.append(" to=\"");
        stream.append(remoteDomain);
        stream.append("\"");
        stream.append(" from=\"");
        stream.append(recipient);
        stream.append("\"");
        stream.append(" version=\"1.0\">");
        writer.write(stream.toString());
        writer.flush();

        // Get the answer from the Authoritative Server
        XmlPullParser xpp = reader.getXPPParser();
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }

        log.debug( "Got a response." ); // TODO there's code duplication here with LocalOutgoingServerSession.
        if ((xpp.getAttributeValue("", "version") != null) && (xpp.getAttributeValue("", "version").equals("1.0"))) {
            log.debug( "The remote server is XMPP 1.0 compliant (or at least reports to be).");
            Document doc;
            try {
                doc = reader.parseDocument();
            } catch (DocumentException e) {
                log.warn("Unable to verify key: XML Error!", e);
                // Close the stream
                writer.write("</stream:stream>");
                writer.flush();
                return VerifyResult.error;
            }
            Element features = doc.getRootElement();
            Element starttls = features.element("starttls");
            if (!directTLS && !skipTLS && starttls != null) {
                writer.write("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");
                writer.flush();
                try {
                    doc = reader.parseDocument();
                } catch (DocumentException e) {
                    log.warn("Unable to verify key: XML Error!", e);
                    // Close the stream
                    writer.write("</stream:stream>");
                    writer.flush();
                    return VerifyResult.error;
                }
                if (!doc.getRootElement().getName().equals("proceed")) {
                    log.warn("Unable to verify key: Got {} instead of proceed for starttls", doc.getRootElement().getName());
                    log.debug("Like this: {}", doc.asXML());
                    // Close the stream
                    writer.write("</stream:stream>");
                    writer.flush();
                    return VerifyResult.error;
                }

                log.debug( "Negotiating StartTLS with AS... " );
                // Start handshake
                tlsStreamHandler.start();
                // Use new wrapped writers
                writer = new BufferedWriter( new OutputStreamWriter( tlsStreamHandler.getOutputStream(), StandardCharsets.UTF_8 ) );
                reader.getXPPParser().setInput( new InputStreamReader( tlsStreamHandler.getInputStream(), StandardCharsets.UTF_8 ) );
                log.debug( "Successfully negotiated StartTLS with AS... " );
                /// Recurses!
                return sendVerifyKey( key, streamID, recipient, remoteDomain, writer, reader, socket, skipTLS, directTLS );
            }
        }
        if ("jabber:server:dialback".equals(xpp.getNamespace("db"))) {
            log.debug("Request for verification of the key and wait for response");
            StringBuilder sb = new StringBuilder();
            sb.append("<db:verify");
            sb.append(" from=\"").append(recipient).append("\"");
            sb.append(" to=\"").append(remoteDomain).append("\"");
            sb.append(" id=\"").append(streamID.getID()).append("\">");
            sb.append(key);
            sb.append("</db:verify>");
            writer.write(sb.toString());
            writer.flush();

            try {
                Element doc = reader.parseDocument().getRootElement();
                if ("db".equals(doc.getNamespacePrefix()) && "verify".equals(doc.getName())) {
                    if (doc.attributeValue("id") == null || !streamID.equals(BasicStreamIDFactory.createStreamID( doc.attributeValue("id") ))) {
                        // Include the invalid-id stream error condition in the response
                        writer.write(new StreamError(StreamError.Condition.invalid_id).toXML());
                        writer.write("</stream:stream>");
                        writer.flush();
                        // Thrown an error so <remote-connection-failed/> stream error
                        // condition is sent to the Originating Server
                        throw new RemoteConnectionFailedException("Invalid ID");
                    }
                    else if (isHostUnknown( doc.attributeValue( "to" ) )) {
                        // Include the host-unknown stream error condition in the response
                        writer.write(new StreamError(StreamError.Condition.host_unknown).toXML());
                        writer.write("</stream:stream>");
                        writer.flush();
                        // Thrown an error so <remote-connection-failed/> stream error
                        // condition is sent to the Originating Server
                        throw new RemoteConnectionFailedException("Host unknown");
                    }
                    else if (!remoteDomain.equals(doc.attributeValue("from"))) {
                        // Include the invalid-from stream error condition in the response
                        writer.write(new StreamError(StreamError.Condition.invalid_from).toXML());
                        writer.write("</stream:stream>");
                        writer.flush();
                        // Thrown an error so <remote-connection-failed/> stream error
                        // condition is sent to the Originating Server
                        throw new RemoteConnectionFailedException("Invalid From");
                    }
                    else if ("valid".equals(doc.attributeValue("type"))){
                        log.debug("Key was VERIFIED by the Authoritative Server.");
                        result = VerifyResult.valid;
                    }
                    else if ("invalid".equals(doc.attributeValue("type"))){
                        log.debug("Key was NOT VERIFIED by the Authoritative Server.");
                        result = VerifyResult.invalid;
                    }
                    else {
                        log.debug("Key was ERRORED by the Authoritative Server.");
                        result = VerifyResult.error;
                    }
                }
                else {
                    log.debug("db:verify answer was: " + doc.asXML());
                }
            }
            catch (DocumentException | RuntimeException e) {
                log.error("An error occurred while connecting to the Authoritative Server:", e);
                // Thrown an error so <remote-connection-failed/> stream error condition is
                // sent to the Originating Server
                writer.write("</stream:stream>");
                writer.flush();
                throw new RemoteConnectionFailedException("Error connecting to the Authoritative Server");
            }

        }
        else {
            // Include the invalid-namespace stream error condition in the response
            writer.write(new StreamError(StreamError.Condition.invalid_namespace).toXML());
            writer.write("</stream:stream>");
            writer.flush();
            // Thrown an error so <remote-connection-failed/> stream error condition is
            // sent to the Originating Server
            throw new RemoteConnectionFailedException("Invalid namespace");
        }
        writer.write("</stream:stream>");
        writer.flush();
        return result;
    }

    /**
     * Verifies the key with the Authoritative Server.
     */
    private VerifyResult verifyKey(String key, StreamID streamID, String recipient, String remoteDomain, Socket socket, boolean skipTLS, boolean directTLS ) throws IOException, XmlPullParserException, RemoteConnectionFailedException {

        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Acting as Receiving Server: Verify key with AS: " + remoteDomain + " for OS: " + recipient + " (id " + streamID + ")]" );

        log.debug( "Verifying key ..." );
        XMPPPacketReader reader;
        Writer writer = null;
        // Set a read timeout
        socket.setSoTimeout(RemoteServerManager.getSocketTimeout());
        VerifyResult result = VerifyResult.error;
        try {
            reader = new XMPPPacketReader();
            reader.setXPPFactory(FACTORY);

            reader.getXPPParser().setInput(new InputStreamReader(socket.getInputStream(), CHARSET));
            // Get a writer for sending the open stream tag
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), CHARSET));
            result = sendVerifyKey(key, streamID, recipient, remoteDomain, writer, reader, socket, skipTLS, directTLS );
        }
        finally {
            try {
                // Close the TCP connection
                socket.close();
            }
            catch (IOException ioe) {
                // Do nothing
            }
        }

        switch ( result ) {
            case valid:
                log.debug( "Successfully verified key!" );
                break;

            case invalid:
                log.debug( "Unable to verify key: AS reports that the key is invalid." );
                break;

            default:
            case decline:
            case error:
                log.debug( "Unable to verify key: An error occurred." );
                break;
        }
        return result;
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
        StreamID streamID = BasicStreamIDFactory.createStreamID( doc.attributeValue("id") );

        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Acting as Authoritative Server: Verify key sent by RS: " + verifyFROM + " (id " + streamID+ ")]" );

        log.debug( "Verifying key... ");

        // TODO If the value of the 'to' address does not match a recognized hostname,
        // then generate a <host-unknown/> stream error condition
        // TODO If the value of the 'from' address does not match the hostname
        // represented by the Receiving Server when opening the TCP connection, then
        // generate an <invalid-from/> stream error condition

        // Verify the received key
        // Created the expected key based on the received ID value and the shared secret
        String expectedKey = AuthFactory.createDigest(streamID.getID(), getSecretkey());
        boolean verified = expectedKey.equals(key);

        // Send the result of the key verification
        StringBuilder sb = new StringBuilder();
        sb.append("<db:verify");
        sb.append(" from=\"").append(verifyTO).append("\"");
        sb.append(" to=\"").append(verifyFROM).append("\"");
        sb.append(" type=\"");
        sb.append(verified ? "valid" : "invalid");
        sb.append("\" id=\"").append(streamID.getID()).append("\"/>");
        connection.deliverRawText(sb.toString());
        log.debug("Verification successful! Key was: " + (verified ? "VALID" : "INVALID"));
        return verified;
    }

    /**
     * Returns the secret key that was randomly generated. When running inside of a cluster
     * the key will be unique to all cluster nodes.
     *
     * @return the secret key that was randomly generated.
     */
    private static String getSecretkey() {
        String key = "secretKey";
        Lock lock = secretKeyCache.getLock(key);
        lock.lock();
        try {
            String secret = secretKeyCache.get(key);
            if (secret == null) {
                secret = StringUtils.randomString(10);
                secretKeyCache.put(key, secret);
            }
            return secret;
        }
        finally {
            lock.unlock();
        }
    }
}

