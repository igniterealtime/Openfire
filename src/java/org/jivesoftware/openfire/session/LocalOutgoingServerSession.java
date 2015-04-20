/**
 * $RCSfile: $
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

package org.jivesoftware.openfire.session;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import javax.net.ssl.SSLHandshakeException;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.DNSUtil;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.SocketConnection;
import org.jivesoftware.openfire.server.OutgoingServerSocketReader;
import org.jivesoftware.openfire.server.RemoteServerConfiguration;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.PacketExtension;
import org.xmpp.packet.Presence;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZInputStream;

/**
 * Server-to-server communication is done using two TCP connections between the servers. One
 * connection is used for sending packets while the other connection is used for receiving packets.
 * The <tt>OutgoingServerSession</tt> represents the connection to a remote server that will only
 * be used for sending packets.<p>
 *
 * Currently only the Server Dialback method is being used for authenticating with the remote
 * server. Use {@link #authenticateDomain(String, String)} to create a new connection to a remote
 * server that will be used for sending packets to the remote server from the specified domain.
 * Only the authenticated domains with the remote server will be able to effectively send packets
 * to the remote server. The remote server will reject and close the connection if a
 * non-authenticated domain tries to send a packet through this connection.<p>
 *
 * Once the connection has been established with the remote server and at least a domain has been
 * authenticated then a new route will be added to the routing table for this connection. For
 * optimization reasons the same outgoing connection will be used even if the remote server has
 * several hostnames. However, different routes will be created in the routing table for each
 * hostname of the remote server.
 *
 * @author Gaston Dombiak
 */
public class LocalOutgoingServerSession extends LocalServerSession implements OutgoingServerSession {

	private static final Logger Log = LoggerFactory.getLogger(LocalOutgoingServerSession.class);

    /**
     * Regular expression to ensure that the hostname contains letters.
     */
    private static Pattern pattern = Pattern.compile("[a-zA-Z]");

    private Collection<String> authenticatedDomains = new HashSet<String>();
    private final Collection<String> hostnames = new HashSet<String>();
    private OutgoingServerSocketReader socketReader;

    /**
     * Creates a new outgoing connection to the specified hostname if no one exists. The port of
     * the remote server could be configured by setting the <b>xmpp.server.socket.remotePort</b>
     * property or otherwise the standard port 5269 will be used. Either a new connection was
     * created or already existed the specified hostname will be authenticated with the remote
     * server. Once authenticated the remote server will start accepting packets from the specified
     * domain.<p>
     *
     * The Server Dialback method is currently the only implemented method for server-to-server
     * authentication. This implies that the remote server will ask the Authoritative Server
     * to verify the domain to authenticate. Most probably this (local) server will act as the
     * Authoritative Server. See {@link IncomingServerSession} for more information.
     *
     * @param domain the local domain to authenticate with the remote server.
     * @param hostname the hostname of the remote server.
     * @return True if the domain was authenticated by the remote server.
     */
    public static boolean authenticateDomain(final String domain, final String hostname) {
        if (hostname == null || hostname.length() == 0 || hostname.trim().indexOf(' ') > -1) {
            // Do nothing if the target hostname is empty, null or contains whitespaces
            return false;
        }
        try {
            // Check if the remote hostname is in the blacklist
            if (!RemoteServerManager.canAccess(hostname)) {
                return false;
            }

            OutgoingServerSession session;
            // Check if a session, that is using server dialback, already exists to the desired
            // hostname (i.e. remote server). If no one exists then create a new session. The same
            // session will be used for the same hostname for all the domains to authenticate
            SessionManager sessionManager = SessionManager.getInstance();
            if (sessionManager == null) {
                // Server is shutting down while we are trying to create a new s2s connection
                return false;
            }
            session = sessionManager.getOutgoingServerSession(hostname);
            if (session == null) {
                // Try locating if the remote server has previously authenticated with this server
                for (IncomingServerSession incomingSession : sessionManager.getIncomingServerSessions(hostname)) {
                    for (String otherHostname : incomingSession.getValidatedDomains()) {
                        session = sessionManager.getOutgoingServerSession(otherHostname);
                        if (session != null) {
                            if (session.isUsingServerDialback()) {
                                // A session to the same remote server but with different hostname
                                // was found. Use this session.
                                break;
                            } else {
                                session = null;
                            }
                        }
                    }
                }
            }
            if (session == null) {
                int port = RemoteServerManager.getPortForServer(hostname);
                session = createOutgoingSession(domain, hostname, port);
                if (session != null) {
                    // Add the validated domain as an authenticated domain
                    session.addAuthenticatedDomain(domain);
                    // Add the new hostname to the list of names that the server may have
                    session.addHostname(hostname);
                    // Notify the SessionManager that a new session has been created
                    sessionManager.outgoingServerSessionCreated((LocalOutgoingServerSession) session);
                    return true;
                } else {
                    Log.warn("Fail to connect to {} for {}", hostname, domain);
                    return false;
                }
            }
            // A session already exists. The session was established using server dialback so
            // it is possible to do piggybacking to authenticate more domains
            if (session.getAuthenticatedDomains().contains(domain) && session.getHostnames().contains(hostname)) {
                // Do nothing since the domain has already been authenticated
                return true;
            }
            // A session already exists so authenticate the domain using that session
            return session.authenticateSubdomain(domain, hostname);
        }
        catch (Exception e) {
            Log.error("Error authenticating domain with remote server: " + hostname, e);
        }
        return false;
    }

    /**
     * Establishes a new outgoing session to a remote server. If the remote server supports TLS
     * and SASL then the new outgoing connection will be secured with TLS and authenticated
     * using SASL. However, if TLS or SASL is not supported by the remote server or if an
     * error occured while securing or authenticating the connection using SASL then server
     * dialback method will be used.
     *
     * @param domain the local domain to authenticate with the remote server.
     * @param hostname the hostname of the remote server.
     * @param port default port to use to establish the connection.
     * @return new outgoing session to a remote server.
     */
    private static LocalOutgoingServerSession createOutgoingSession(String domain, String hostname,
            int port) {

        String localDomainName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        boolean useTLS = JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ENABLED, true);
        RemoteServerConfiguration configuration = RemoteServerManager.getConfiguration(hostname);
        if (configuration != null) {
            // TODO Use the specific TLS configuration for this remote server
            //useTLS = configuration.isTLSEnabled();
        }

        // Connect to remote server using XMPP 1.0 (TLS + SASL EXTERNAL or TLS + server dialback or server dialback)
        String realHostname = null;
        int realPort = port;
        Socket socket = null;
        // Get a list of real hostnames to connect to using DNS lookup of the specified hostname
        List<DNSUtil.HostAddress> hosts = DNSUtil.resolveXMPPDomain(hostname, port);
        for (Iterator<DNSUtil.HostAddress> it = hosts.iterator(); it.hasNext();) {
            try {
                socket = new Socket();
                DNSUtil.HostAddress address = it.next();
                realHostname = address.getHost();
                realPort = address.getPort();
                Log.debug("LocalOutgoingServerSession: OS - Trying to connect to " + hostname + ":" + port +
                        "(DNS lookup: " + realHostname + ":" + realPort + ")");
                // Establish a TCP connection to the Receiving Server
                socket.connect(new InetSocketAddress(realHostname, realPort),
                        RemoteServerManager.getSocketTimeout());
                Log.debug("LocalOutgoingServerSession: OS - Plain connection to " + hostname + ":" + port + " successful");
                break;
            }
            catch (Exception e) {
                Log.warn("Error trying to connect to remote server: " + hostname +
                        "(DNS lookup: " + realHostname + ":" + realPort + "): " + e.toString());
                try {
                    if (socket != null) {
                        socket.close();
                    }
                }
                catch (IOException ex) {
                    Log.debug("Additional exception while trying to close socket when connection to remote server failed: " + ex.toString());
                }
            }
        }
        if (!socket.isConnected()) {
            return null;
        }

        SocketConnection connection = null;
        try {
            connection =
                    new SocketConnection(XMPPServer.getInstance().getPacketDeliverer(), socket,
                            false);

            // Send the stream header
            StringBuilder openingStream = new StringBuilder();
            openingStream.append("<stream:stream");
            openingStream.append(" xmlns:db=\"jabber:server:dialback\"");
            openingStream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            openingStream.append(" xmlns=\"jabber:server\"");
            openingStream.append(" from=\"").append(localDomainName).append("\""); // OF-673
            openingStream.append(" to=\"").append(hostname).append("\"");
            openingStream.append(" version=\"1.0\">");
            connection.deliverRawText(openingStream.toString());

            // Set a read timeout (of 5 seconds) so we don't keep waiting forever
            int soTimeout = socket.getSoTimeout();
            socket.setSoTimeout(5000);

            XMPPPacketReader reader = new XMPPPacketReader();
            reader.getXPPParser().setInput(new InputStreamReader(socket.getInputStream(),
                    CHARSET));
            // Get the answer from the Receiving Server
            XmlPullParser xpp = reader.getXPPParser();
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }

            String serverVersion = xpp.getAttributeValue("", "version");
            String id = xpp.getAttributeValue("", "id");

            // Check if the remote server is XMPP 1.0 compliant
            if (serverVersion != null && decodeVersion(serverVersion)[0] >= 1) {
                // Restore default timeout
                socket.setSoTimeout(soTimeout);
                // Get the stream features
                Element features = reader.parseDocument().getRootElement();
                if (features != null) {
                    // Check if TLS is enabled
                    if (useTLS && features.element("starttls") != null) {
                        // Secure the connection with TLS and authenticate using SASL
                        LocalOutgoingServerSession answer;
                        answer = secureAndAuthenticate(hostname, connection, reader, openingStream,
                                domain);
                        if (answer != null) {
                            // Everything went fine so return the secured and
                            // authenticated connection
                            return answer;
                        }
                    }
                    // Check if we are going to try server dialback (XMPP 1.0)
                    else if (ServerDialback.isEnabled() && features.element("dialback") != null) {
                        Log.debug("LocalOutgoingServerSession: OS - About to try connecting using server dialback XMPP 1.0 with: " + hostname);
                        ServerDialback method = new ServerDialback(connection, domain);
                        OutgoingServerSocketReader newSocketReader = new OutgoingServerSocketReader(reader);
                        if (method.authenticateDomain(newSocketReader, domain, hostname, id)) {
                            Log.debug("LocalOutgoingServerSession: OS - SERVER DIALBACK XMPP 1.0 with " + hostname + " was successful");
                            StreamID streamID = new BasicStreamIDFactory().createStreamID(id);
                            LocalOutgoingServerSession session = new LocalOutgoingServerSession(domain, connection, newSocketReader, streamID);
                            connection.init(session);
                            // Set the hostname as the address of the session
                            session.setAddress(new JID(null, hostname, null));
                            return session;
                        }
                        else {
                            Log.debug("LocalOutgoingServerSession: OS - Error, SERVER DIALBACK with " + hostname + " failed");
                        }
                    }
                }
                else {
                    Log.debug("LocalOutgoingServerSession: OS - Error, <starttls> was not received");
                }
            }
            // Something went wrong so close the connection and try server dialback over
            // a plain connection
            if (connection != null) {
                connection.close();
            }
        }
        catch (SSLHandshakeException e) {
            Log.debug("LocalOutgoingServerSession: Handshake error while creating secured outgoing session to remote " +
                    "server: " + hostname + "(DNS lookup: " + realHostname + ":" + realPort +
                    "):" + e.toString());
            // Close the connection
            if (connection != null) {
                connection.close();
            }
        }
        catch (XmlPullParserException e) {
            Log.warn("Error creating secured outgoing session to remote server: " + hostname +
                    "(DNS lookup: " + realHostname + ":" + realPort + "): " + e.toString());
            // Close the connection
            if (connection != null) {
                connection.close();
            }
        }
        catch (Exception e) {
            Log.error("Error creating secured outgoing session to remote server: " + hostname +
                    "(DNS lookup: " + realHostname + ":" + realPort + "): " + e.toString());
            // Close the connection
            if (connection != null) {
                connection.close();
            }
        }

        if (ServerDialback.isEnabled()) {
            Log.debug("LocalOutgoingServerSession: OS - Going to try connecting using server dialback with: " + hostname);
            // Use server dialback (pre XMPP 1.0) over a plain connection 
            return new ServerDialback().createOutgoingSession(domain, hostname, port);
        }
        return null;
    }

    private static LocalOutgoingServerSession secureAndAuthenticate(String hostname,
            SocketConnection connection, XMPPPacketReader reader, StringBuilder openingStream,
            String domain) throws Exception {
    	final Logger log = LoggerFactory.getLogger(LocalOutgoingServerSession.class.getName()+"['"+hostname+"']");
        Element features;
        log.debug("Indicating we want TLS to " + hostname);
        connection.deliverRawText("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");

        MXParser xpp = reader.getXPPParser();
        // Wait for the <proceed> response
        Element proceed = reader.parseDocument().getRootElement();
        if (proceed != null && proceed.getName().equals("proceed")) {
            log.debug("Negotiating TLS...");
            try {
                boolean needed = JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_CERTIFICATE_VERIFY, true) &&
                        		 JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_CERTIFICATE_CHAIN_VERIFY, true) &&
                        		 !JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, false);
                connection.startTLS(true, hostname, needed ? Connection.ClientAuth.needed : Connection.ClientAuth.wanted);
            } catch(Exception e) {
                log.debug("Got an exception whilst negotiating TLS: " + e.getMessage());
                throw e;
            }
            log.debug("TLS negotiation was successful.");
            if (!SASLAuthentication.verifyCertificates(connection.getPeerCertificates(), hostname)) {
                log.debug("X.509/PKIX failure on outbound session");
                if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
                    log.debug("Will continue with dialback.");
                } else {
                    log.warn("No TLS auth, but TLS auth required.");
                    return null;
                }
            }

            // TLS negotiation was successful so initiate a new stream
            connection.deliverRawText(openingStream.toString());

            // Reset the parser to use the new secured reader
            xpp.setInput(new InputStreamReader(connection.getTLSStreamHandler().getInputStream(), CHARSET));
            // Skip new stream element
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }
            // Get the stream ID 
            String id = xpp.getAttributeValue("", "id");
            // Get new stream features
            features = reader.parseDocument().getRootElement();
            if (features != null) {
                // Check if we can use stream compression
                String policyName = JiveGlobals.getProperty(ConnectionSettings.Server.COMPRESSION_SETTINGS, Connection.CompressionPolicy.disabled.toString());
                Connection.CompressionPolicy compressionPolicy = Connection.CompressionPolicy.valueOf(policyName);
                if (Connection.CompressionPolicy.optional == compressionPolicy) {
                    // Verify if the remote server supports stream compression
                    Element compression = features.element("compression");
                    if (compression != null) {
                        boolean zlibSupported = false;
                        Iterator it = compression.elementIterator("method");
                        while (it.hasNext()) {
                            Element method = (Element) it.next();
                            if ("zlib".equals(method.getTextTrim())) {
                                zlibSupported = true;
                            }
                        }
                        if (zlibSupported) {
                            log.debug("Suppressing request to perform compression; unsupported in this version.");
                            zlibSupported = false;
                        }
                        if (zlibSupported) {
                            log.debug("Requesting stream compression (zlib).");
                            connection.deliverRawText("<compress xmlns='http://jabber.org/protocol/compress'><method>zlib</method></compress>");
                            // Check if we are good to start compression
                            Element answer = reader.parseDocument().getRootElement();
                            if ("compressed".equals(answer.getName())) {
                                // Server confirmed that we can use zlib compression
                                connection.addCompression();
                                connection.startCompression();
                                log.debug("Stream compression was successful.");
                                // Stream compression was successful so initiate a new stream
                                connection.deliverRawText(openingStream.toString());
                                // Reset the parser to use stream compression over TLS
                                ZInputStream in = new ZInputStream(
                                        connection.getTLSStreamHandler().getInputStream());
                                in.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
                                xpp.setInput(new InputStreamReader(in, CHARSET));
                                // Skip the opening stream sent by the server
                                for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;)
                                {
                                    eventType = xpp.next();
                                }
                                // Get new stream features
                                features = reader.parseDocument().getRootElement();
                                if (features == null) {
                                    log.debug("Error, EXTERNAL SASL was not offered.");
                                    return null;
                                }
                            }
                            else {
                                log.debug("Stream compression was rejected by " + hostname);
                            }
                        }
                        else {
                            log.debug("Stream compression found but zlib method is not supported by " + hostname);
                        }
                    }
                    else {
                        log.debug("Stream compression not supported by " + hostname);
                    }
                }

                // Bookkeeping: determine what functionality the remote server offers.
                boolean saslEXTERNALoffered = false;
                if (features != null) {
                    if (features.element("mechanisms") != null) {
                        Iterator<Element> it = features.element("mechanisms").elementIterator();
                        while (it.hasNext()) {
                            Element mechanism = it.next();
                            if ("EXTERNAL".equals(mechanism.getTextTrim())) {
                            	saslEXTERNALoffered = true;
                            	break;
                            }
                        }
                    }
                }
                final boolean dialbackOffered = features.element("dialback") != null;
                
                log.debug("Offering dialback functionality: {}",dialbackOffered);
                log.debug("Offering EXTERNAL SASL: {}", saslEXTERNALoffered);
                
                LocalOutgoingServerSession result = null;
            	// first, try SASL
            	if (saslEXTERNALoffered) {
            		result = attemptSASLexternal(connection, xpp, reader, domain, hostname, id, openingStream);
            	}
            	if (result == null) {
            		// SASL unavailable or failed, try dialback.
            		result = attemptDialbackOverTLS(connection, reader, domain, hostname, id);
            	}
                
                return result;
            }
            else {
                log.debug("Cannot create outgoing server session, as neither SASL mechanisms nor SERVER DIALBACK were offered by " + hostname);
                return null;
            }
        }
        else {
            log.debug("Error, <proceed> was not received!");
            return null;
        }
    }

    private static LocalOutgoingServerSession attemptDialbackOverTLS(Connection connection, XMPPPacketReader reader, String domain, String hostname, String id) {
    	final Logger log = LoggerFactory.getLogger(LocalOutgoingServerSession.class.getName()+"['"+hostname+"']");
        if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
            log.debug("Trying to connecting using dialback over TLS.");
            ServerDialback method = new ServerDialback(connection, domain);
            OutgoingServerSocketReader newSocketReader = new OutgoingServerSocketReader(reader);
            if (method.authenticateDomain(newSocketReader, domain, hostname, id)) {
                log.debug("Dialback over TLS was successful.");
                StreamID streamID = new BasicStreamIDFactory().createStreamID(id);
                LocalOutgoingServerSession session = new LocalOutgoingServerSession(domain, connection, newSocketReader, streamID);
                connection.init(session);
                // Set the hostname as the address of the session
                session.setAddress(new JID(null, hostname, null));
                return session;
            }
            else {
                log.debug("Dialback over TLS failed");
                return null;
            }
        }
        else {
            log.debug("Skipping server dialback attempt as it has been disabled by local configuration.");
            return null;
        }    	
    }
    
    private static LocalOutgoingServerSession attemptSASLexternal(SocketConnection connection, MXParser xpp, XMPPPacketReader reader, String domain, String hostname, String id, StringBuilder openingStream) throws DocumentException, IOException, XmlPullParserException {
    	final Logger log = LoggerFactory.getLogger(LocalOutgoingServerSession.class.getName()+"['"+hostname+"']");
        log.debug("Starting EXTERNAL SASL.");
        if (doExternalAuthentication(domain, connection, reader)) {
            log.debug("EXTERNAL SASL was successful.");
            // SASL was successful so initiate a new stream
            connection.deliverRawText(openingStream.toString());
            
            // Reset the parser
            //xpp.resetInput();
            //             // Reset the parser to use the new secured reader
            xpp.setInput(new InputStreamReader(connection.getTLSStreamHandler().getInputStream(), CHARSET));
            // Skip the opening stream sent by the server
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }

            // SASL authentication was successful so create new OutgoingServerSession
            id = xpp.getAttributeValue("", "id");
            StreamID streamID = new BasicStreamIDFactory().createStreamID(id);
            LocalOutgoingServerSession session = new LocalOutgoingServerSession(domain,
                    connection, new OutgoingServerSocketReader(reader), streamID);
            connection.init(session);
            // Set the hostname as the address of the session
            session.setAddress(new JID(null, hostname, null));
            // Set that the session was created using TLS+SASL (no server dialback)
            session.usingServerDialback = false;
            return session;
        }
        else {
            log.debug("EXTERNAL SASL failed.");
            return null;
        }  	
    }
    
    private static boolean doExternalAuthentication(String domain, SocketConnection connection,
            XMPPPacketReader reader) throws DocumentException, IOException, XmlPullParserException {

        StringBuilder sb = new StringBuilder();
        sb.append("<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"EXTERNAL\">");
        sb.append(StringUtils.encodeBase64(domain));
        sb.append("</auth>");
        connection.deliverRawText(sb.toString());

        Element response = reader.parseDocument().getRootElement();
        return response != null && "success".equals(response.getName());
    }

    public LocalOutgoingServerSession(String serverName, Connection connection,
            OutgoingServerSocketReader socketReader, StreamID streamID) {
        super(serverName, connection, streamID);
        this.socketReader = socketReader;
        socketReader.setSession(this);
    }

    @Override
	boolean canProcess(Packet packet) {
        String senderDomain = packet.getFrom().getDomain();
        if (!getAuthenticatedDomains().contains(senderDomain)) {
            synchronized (senderDomain.intern()) {
                if (!getAuthenticatedDomains().contains(senderDomain) &&
                        !authenticateSubdomain(senderDomain, packet.getTo().getDomain())) {
                    // Return error since sender domain was not validated by remote server
                    returnErrorToSender(packet);
                    return false;
                }
            }
        }
        return true;
    }

    @Override
	void deliver(Packet packet) throws UnauthorizedException {
        if (!conn.isClosed()) {
            conn.deliver(packet);
        }
    }

    public boolean authenticateSubdomain(String domain, String hostname) {
        if (!usingServerDialback) {
            // Using SASL so just assume that the domain was validated
            // (note: this may not be correct)
            addAuthenticatedDomain(domain);
            addHostname(hostname);
            return true;
        }
        ServerDialback method = new ServerDialback(getConnection(), domain);
        if (method.authenticateDomain(socketReader, domain, hostname, getStreamID().getID())) {
            // Add the validated domain as an authenticated domain
            addAuthenticatedDomain(domain);
            addHostname(hostname);
            return true;
        }
        return false;
    }

    private void returnErrorToSender(Packet packet) {
        RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();
        if (packet.getError() != null) {
            Log.debug("Possible double bounce: " + packet.toXML());
        }
        try {
            if (packet instanceof IQ) {
            	if (((IQ) packet).isResponse()) {
            		Log.debug("XMPP specs forbid us to respond with an IQ error to: " + packet.toXML());
            		return;
            	}
                IQ reply = new IQ();
                reply.setID(packet.getID());
                reply.setTo(packet.getFrom());
                reply.setFrom(packet.getTo());
                reply.setChildElement(((IQ) packet).getChildElement().createCopy());
                reply.setType(IQ.Type.error);
                reply.setError(PacketError.Condition.remote_server_not_found);
                routingTable.routePacket(reply.getTo(), reply, true);
            }
            else if (packet instanceof Presence) {
                if (((Presence)packet).getType() == Presence.Type.error) {
                    Log.debug("Double-bounce of presence: " + packet.toXML());
                    return;
                }
                Presence reply = new Presence();
                reply.setID(packet.getID());
                reply.setTo(packet.getFrom());
                reply.setFrom(packet.getTo());
                reply.setType(Presence.Type.error);
                reply.setError(PacketError.Condition.remote_server_not_found);
                routingTable.routePacket(reply.getTo(), reply, true);
            }
            else if (packet instanceof Message) {
                if (((Message)packet).getType() == Message.Type.error){
                    Log.debug("Double-bounce of message: " + packet.toXML());
                    return;
                }
                Message reply = new Message();
                reply.setID(packet.getID());
                reply.setTo(packet.getFrom());
                reply.setFrom(packet.getTo());
                reply.setType(Message.Type.error);
                reply.setThread(((Message)packet).getThread());
                reply.setError(PacketError.Condition.remote_server_not_found);
                routingTable.routePacket(reply.getTo(), reply, true);
            }
        }
        catch (Exception e) {
            Log.error("Error returning error to sender. Original packet: " + packet, e);
        }
    }

    public Collection<String> getAuthenticatedDomains() {
        return Collections.unmodifiableCollection(authenticatedDomains);
    }

    public void addAuthenticatedDomain(String domain) {
        authenticatedDomains.add(domain);
    }

    public Collection<String> getHostnames() {
        synchronized (hostnames) {
            return Collections.unmodifiableCollection(hostnames);
        }
    }

    public void addHostname(String hostname) {
        synchronized (hostnames) {
            hostnames.add(hostname);
        }
        // Add a new route for this new session
        XMPPServer.getInstance().getRoutingTable().addServerRoute(new JID(null, hostname, null, true), this);
    }

    @Override
	public String getAvailableStreamFeatures() {
        // Nothing special to add
        return null;
    }
}
