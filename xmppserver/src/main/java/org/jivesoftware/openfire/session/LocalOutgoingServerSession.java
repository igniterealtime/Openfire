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

package org.jivesoftware.openfire.session;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.event.ServerSessionEventDispatcher;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.SocketConnection;
import org.jivesoftware.openfire.net.SocketUtil;
import org.jivesoftware.openfire.server.OutgoingServerSocketReader;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.*;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Server-to-server communication is done using two TCP connections between the servers. One
 * connection is used for sending packets while the other connection is used for receiving packets.
 * The {@code OutgoingServerSession} represents the connection to a remote server that will only
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

    private OutgoingServerSocketReader socketReader;
    private Collection<DomainPair> outgoingDomainPairs = new HashSet<>();

    /**
     * Authenticates the local domain to the remote domain. Once authenticated the remote domain can be expected to
     * start accepting data from the local domain.
     *
     * This implementation will attempt to re-use an existing connection. An connection is deemed re-usable when it is either:
     * <ul>
     *     <li>authenticated to the remote domain itself, or:</li>
     *     <li>authenticated to a sub- or superdomain of the remote domain AND offers dialback.</li>
     * </ul>
     *
     * When no re-usable connection exists, a new connection will be created.
     *
     * DNS will be used to find hosts for the remote domain. When DNS records do not specify a port, port 5269 will be
     * used unless this default is overridden by the <b>xmpp.server.socket.remotePort</b> property.
     *
     * @param localDomain the local domain to authenticate with the remote server.
     * @param remoteDomain the remote server, to which the local domain intends to send data.
     * @return True if the domain was authenticated by the remote server.
     */
    public static boolean authenticateDomain(final String localDomain, final String remoteDomain) {
        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Authenticate local domain: '" + localDomain + "' to remote domain: '" + remoteDomain + "']" );
        final DomainPair domainPair = new DomainPair(localDomain, remoteDomain);

        log.debug( "Start domain authentication ..." );
        if (remoteDomain == null || remoteDomain.length() == 0 || remoteDomain.trim().indexOf(' ') > -1) {
            // Do nothing if the target domain is empty, null or contains whitespaces
            log.warn( "Unable to authenticate: remote domain is invalid." );
            return false;
        }
        try {
            // Check if the remote domain is in the blacklist
            if (!RemoteServerManager.canAccess(remoteDomain)) {
                log.info( "Unable to authenticate: Remote domain is not accessible according to our configuration (typical causes: server federation is disabled, or domain is blacklisted)." );
                return false;
            }

            log.debug( "Searching for pre-existing outgoing sessions to the remote domain (if one exists, it will be re-used) ..." );
            OutgoingServerSession session;
            SessionManager sessionManager = SessionManager.getInstance();
            if (sessionManager == null) {
                // Server is shutting down while we are trying to create a new s2s connection
                log.warn( "Unable to authenticate: a SessionManager instance is not available. This should not occur unless Openfire is starting up or shutting down." );
                return false;
            }
            session = sessionManager.getOutgoingServerSession(domainPair);
            if (session != null && session.checkOutgoingDomainPair(localDomain, remoteDomain))
            {
                // Do nothing since the domain has already been authenticated.
                log.debug( "Authentication successful (domain was already authenticated in the pre-existing session)." );
                //inform all listeners as well.
                ServerSessionEventDispatcher.dispatchEvent(session, ServerSessionEventDispatcher.EventType.session_created);
                return true;
            }
            if (session != null && !session.isUsingServerDialback() )
            {
                log.debug( "Dialback was not used for '{}'. This session cannot be re-used.", domainPair );
                session = null;
            }

            if (session == null)
            {
                log.debug( "There are no pre-existing outgoing sessions to the remote domain itself. Searching for pre-existing outgoing sessions to super- or subdomains of the remote domain (if one exists, it might be re-usable) ..." );

                for ( IncomingServerSession incomingSession : sessionManager.getIncomingServerSessions( remoteDomain ) )
                {
                    // These are the remote domains that are allowed to send data to the local domain - expected to be sub- or superdomains of remoteDomain
                    for ( String otherRemoteDomain : incomingSession.getValidatedDomains() )
                    {
                        // See if there's an outgoing session to any of the (other) domains hosted by the remote domain.
                        session = sessionManager.getOutgoingServerSession( new DomainPair(localDomain, otherRemoteDomain) );
                        if (session != null)
                        {
                            log.debug( "An outgoing session to a different domain ('{}') hosted on the remote domain was found.", otherRemoteDomain );

                            // As this sub/superdomain is different from the original remote domain, we need to check if it supports dialback.
                            if ( session.isUsingServerDialback() )
                            {
                                log.debug( "Dialback was used for '{}'. This session can be re-used.", otherRemoteDomain );
                                break;
                            }
                            else
                            {
                                log.debug( "Dialback was not used for '{}'. This session cannot be re-used.", otherRemoteDomain );
                                session = null;
                            }
                        }
                    }
                }

                if (session == null) {
                    log.debug( "There are no pre-existing session to other domains hosted on the remote domain." );
                }
            }

            if ( session != null )
            {
                log.debug( "A pre-existing session can be re-used. The session was established using server dialback so it is possible to do piggybacking to authenticate more domains." );
                if ( session.checkOutgoingDomainPair(localDomain, remoteDomain) )
                {
                    // Do nothing since the domain has already been authenticated.
                    log.debug( "Authentication successful (domain was already authenticated in the pre-existing session)." );
                    return true;
                }

                // A session already exists so authenticate the domain using that session.
                if ( session.authenticateSubdomain( localDomain, remoteDomain ) )
                {
                    log.debug( "Authentication successful (domain authentication was added using a pre-existing session)." );
                    return true;
                }
                else
                {
                    log.warn( "Unable to authenticate: Unable to add authentication to pre-exising session." );
                    return false;
                }
            }
            else
            {
                log.debug( "Unable to re-use an existing session. Creating a new session ..." );
                int port = RemoteServerManager.getPortForServer(remoteDomain);
                session = createOutgoingSession(localDomain, remoteDomain, port);
                if (session != null) {
                    log.debug( "Created a new session." );

                    session.addOutgoingDomainPair(localDomain, remoteDomain);
                    sessionManager.outgoingServerSessionCreated((LocalOutgoingServerSession) session);
                    log.debug( "Authentication successful." );
                    //inform all listeners as well.
                    ServerSessionEventDispatcher.dispatchEvent(session, ServerSessionEventDispatcher.EventType.session_created);
                    return true;
                } else {
                    log.warn( "Unable to authenticate: Fail to create new session." );
                    return false;
                }
            }
        }
        catch (Exception e)
        {
            log.error( "An exception occurred while authenticating remote domain!", e );
            return false;
        }
    }

    /**
     * Establishes a new outgoing session to a remote domain. If the remote domain supports TLS and SASL then the new
     * outgoing connection will be secured with TLS and authenticated  using SASL. However, if TLS or SASL is not
     * supported by the remote domain or if an error occurred while securing or authenticating the connection using SASL
     * then server dialback will be used.
     *
     * @param localDomain the local domain to authenticate with the remote domain.
     * @param remoteDomain the remote domain.
     * @param port default port to use to establish the connection.
     * @return new outgoing session to a remote domain, or null.
     */
    private static LocalOutgoingServerSession createOutgoingSession(String localDomain, String remoteDomain, int port) {
        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Create outgoing session for: " + localDomain + " to " + remoteDomain + "]" );

        log.debug( "Creating new session..." );

        // Connect to remote server using XMPP 1.0 (TLS + SASL EXTERNAL or TLS + server dialback or server dialback)
        log.debug( "Creating plain socket connection to a host that belongs to the remote XMPP domain." );
        final Map.Entry<Socket, Boolean> socketToXmppDomain = SocketUtil.createSocketToXmppDomain( remoteDomain, port );

        if ( socketToXmppDomain == null ) {
            log.info( "Unable to create new session: Cannot create a plain socket connection with any applicable remote host." );
            return null;
        }
        Socket socket = socketToXmppDomain.getKey();
        boolean directTLS = socketToXmppDomain.getValue();

        SocketConnection connection = null;
        try {
            final SocketAddress socketAddress = socket.getRemoteSocketAddress();
            log.debug( "Opening a new connection to {} {}.", socketAddress, directTLS ? "using directTLS" : "that is initially not encrypted" );
            connection = new SocketConnection(XMPPServer.getInstance().getPacketDeliverer(), socket, false);
            if (directTLS) {
                try {
                    connection.startTLS( true, true );
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
                        connection = new SocketConnection(XMPPServer.getInstance().getPacketDeliverer(), socket, false);
                        directTLS = false;
                        Log.info( "Re-established connection to {}. Proceeding without directTLS.", socketAddress );
                    } else {
                        // Do not retry as non-DirectTLS, rethrow the exception.
                        throw ex;
                    }
                }
            }

            log.debug( "Send the stream header and wait for response..." );
            StringBuilder openingStream = new StringBuilder();
            openingStream.append("<stream:stream");
            openingStream.append(" xmlns:db=\"jabber:server:dialback\"");
            openingStream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            openingStream.append(" xmlns=\"jabber:server\"");
            openingStream.append(" from=\"").append(localDomain).append("\""); // OF-673
            openingStream.append(" to=\"").append(remoteDomain).append("\"");
            openingStream.append(" version=\"1.0\">");
            connection.deliverRawText(openingStream.toString());

            // Set a read timeout (of 5 seconds) so we don't keep waiting forever
            int soTimeout = socket.getSoTimeout();
            socket.setSoTimeout(5000);

            XMPPPacketReader reader = new XMPPPacketReader();

            final InputStream inputStream;
            if (directTLS) {
                inputStream = connection.getTLSStreamHandler().getInputStream();
            } else {
                inputStream = socket.getInputStream();
            }
            reader.getXPPParser().setInput(new InputStreamReader( inputStream, StandardCharsets.UTF_8 ));

            // Get the answer from the Receiving Server
            XmlPullParser xpp = reader.getXPPParser();
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }

            String serverVersion = xpp.getAttributeValue("", "version");
            String id = xpp.getAttributeValue("", "id");
            log.debug( "Got a response (stream ID: {}, version: {}). Check if the remote server is XMPP 1.0 compliant...", id, serverVersion );

            if (serverVersion != null && decodeVersion(serverVersion)[0] >= 1) {
                log.debug( "The remote server is XMPP 1.0 compliant (or at least reports to be)." );

                // Restore default timeout
                socket.setSoTimeout(soTimeout);

                log.debug( "Processing stream features of the remote domain..." );
                Element features = reader.parseDocument().getRootElement();
                if (features != null) {
                    if (directTLS) {
                        log.debug( "We connected to the remote server using direct TLS. Authenticate the connection with SASL..." );
                        LocalOutgoingServerSession answer = authenticate(remoteDomain, connection, reader, openingStream, localDomain, features, id);
                        if (answer != null) {
                            log.debug( "Successfully authenticated the connection with SASL)!" );
                            // Everything went fine so return the secured and
                            // authenticated connection
                            log.debug( "Successfully created new session!" );
                            return answer;
                        }
                        log.debug( "Unable to authenticate the connection with SASL." );
                    } else {
                        log.debug( "Check if both us as well as the remote server have enabled STARTTLS and/or dialback ..." );
                        final boolean useTLS = JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ENABLED, true);
                        if (useTLS && features.element("starttls") != null) {
                            log.debug( "Both us and the remote server support the STARTTLS feature. Secure and authenticate the connection with TLS & SASL..." );
                            LocalOutgoingServerSession answer = secureAndAuthenticate(remoteDomain, connection, reader, openingStream, localDomain);
                            if (answer != null) {
                                log.debug( "Successfully secured/authenticated the connection with TLS/SASL)!" );
                                // Everything went fine so return the secured and
                                // authenticated connection
                                log.debug( "Successfully created new session!" );
                                return answer;
                            }
                            log.debug( "Unable to secure and authenticate the connection with TLS & SASL." );
                        }
                        else if (connection.getTlsPolicy() == Connection.TLSPolicy.required) {
                            log.debug("I have no StartTLS yet I must TLS");
                            connection.close();
                            return null;
                        }
                        // Check if we are going to try server dialback (XMPP 1.0)
                        else if (ServerDialback.isEnabled() && features.element("dialback") != null) {
                            log.debug( "Both us and the remote server support the 'dialback' feature. Authenticate the connection with dialback..." );
                            ServerDialback method = new ServerDialback(connection, localDomain);
                            OutgoingServerSocketReader newSocketReader = new OutgoingServerSocketReader(reader);
                            if (method.authenticateDomain(newSocketReader, localDomain, remoteDomain, id)) {
                                log.debug( "Successfully authenticated the connection with dialback!" );
                                StreamID streamID = new BasicStreamIDFactory().createStreamID(id);
                                LocalOutgoingServerSession session = new LocalOutgoingServerSession(localDomain, connection, newSocketReader, streamID);
                                connection.init(session);
                                // Set the hostname as the address of the session
                                session.setAddress(new JID(null, remoteDomain, null));
                                log.debug( "Successfully created new session!" );
                                return session;
                            }
                            else {
                                log.debug( "Unable to authenticate the connection with dialback." );
                            }
                        }
                    }
                }
                else {
                    log.debug( "Error! No data from the remote server (expected a 'feature' element).");
                }
            } else {
                log.debug( "The remote server is not XMPP 1.0 compliant." );
            }

            log.debug( "Something went wrong so close the connection and try server dialback over a plain connection" );
            if (connection.getTlsPolicy() == Connection.TLSPolicy.required) {
                log.debug("I have no StartTLS yet I must TLS");
                connection.close();
                return null;
            }
            connection.close();
        }
        catch (SSLHandshakeException e)
        {
            // When not doing direct TLS but startTLS, this a failure as described in RFC3620, section 5.4.3.2 "STARTTLS Failure".
            log.info( "{} negotiation failed. Closing connection (without sending any data such as <failure/> or </stream>).", (directTLS ? "Direct TLS" : "StartTLS" ), e );

            // The receiving entity is expected to close the socket *without* sending any more data (<failure/> nor </stream>).
            // It is probably (see OF-794) best if we, as the initiating entity, therefor don't send any data either.
            if (connection != null) {
                connection.forceClose();
            }
        }
        catch (Exception e)
        {
            // This might be RFC3620, section 5.4.2.2 "Failure Case" or even an unrelated problem. Handle 'normally'.
            log.warn( "An exception occurred while creating an encrypted session. Closing connection.", e );

            if (connection != null) {
                connection.close();
            }
        }

        if (ServerDialback.isEnabled())
        {
            log.debug( "Unable to create a new session. Going to try connecting using server dialback as a fallback." );

            // Use server dialback (pre XMPP 1.0) over a plain connection
            final LocalOutgoingServerSession outgoingSession = new ServerDialback().createOutgoingSession( localDomain, remoteDomain, port );
            if ( outgoingSession != null) { // TODO this success handler behaves differently from a similar success handler above. Shouldn't those be the same?
                log.debug( "Successfully created new session (using dialback as a fallback)!" );
                return outgoingSession;
            } else {
                log.warn( "Unable to create a new session: Dialback (as a fallback) failed." );
                return null;
            }
        }
        else
        {
            log.warn( "Unable to create a new session: exhausted all options (not trying dialback as a fallback, as server dialback is disabled by configuration." );
            return null;
        }
    }

    private static LocalOutgoingServerSession secureAndAuthenticate(String remoteDomain, SocketConnection connection, XMPPPacketReader reader, StringBuilder openingStream, String localDomain) throws Exception {
        final Logger log = LoggerFactory.getLogger(Log.getName() + "[Secure connection for: " + localDomain + " to: " + remoteDomain + "]" );
        Element features;

        log.debug( "Securing and authenticating connection ...");

        log.debug( "Indicating we want TLS and wait for response." );
        connection.deliverRawText( "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>" );

        MXParser xpp = reader.getXPPParser();
        // Wait for the <proceed> response
        Element proceed = reader.parseDocument().getRootElement();
        if (proceed != null && proceed.getName().equals("proceed")) {
            log.debug( "Received 'proceed' from remote server. Negotiating TLS..." );
            try {
//                boolean needed = JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_CERTIFICATE_VERIFY, true) &&
//                        		 JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_CERTIFICATE_CHAIN_VERIFY, true) &&
//                        		 !JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, false);
                connection.startTLS(true, false);
            } catch(Exception e) {
                log.debug("TLS negotiation failed: " + e.getMessage());
                throw e;
            }
            log.debug( "TLS negotiation was successful. Connection secured. Proceeding with authentication..." );
            if (!SASLAuthentication.verifyCertificates(connection.getPeerCertificates(), remoteDomain, true)) {
                if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
                    log.debug( "SASL authentication failed. Will continue with dialback." );
                } else {
                    log.warn( "Unable to authenticated the connection: SASL authentication failed (and dialback is not available)." );
                    return null;
                }
            }

            log.debug( "TLS negotiation was successful so initiate a new stream." );
            connection.deliverRawText( openingStream.toString() );

            // Reset the parser to use the new secured reader
            xpp.setInput(new InputStreamReader(connection.getTLSStreamHandler().getInputStream(), StandardCharsets.UTF_8));
            // Skip new stream element
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }
            // Get the stream ID
            String id = xpp.getAttributeValue("", "id");
            // Get new stream features
            features = reader.parseDocument().getRootElement();
            if (features != null) {
                return authenticate( remoteDomain, connection, reader, openingStream, localDomain, features, id );
            }
            else {
                log.debug( "Failed to secure and authenticate connection: neither SASL mechanisms nor SERVER DIALBACK were offered by the remote host." );
                return null;
            }
        }
        else {
            log.debug( "Failed to secure and authenticate connection: <proceed> was not received!" );
            return null;
        }
    }

    private static LocalOutgoingServerSession authenticate( final String remoteDomain,
                                                            final SocketConnection connection,
                                                            final XMPPPacketReader reader,
                                                            final StringBuilder openingStream,
                                                            final String localDomain,
                                                            final Element features,
                                                            final String id ) throws DocumentException, IOException, XmlPullParserException
    {
        final Logger log = LoggerFactory.getLogger(Log.getName() + "[Authenticate connection for: " + localDomain + " to: " + remoteDomain + "]" );

        MXParser xpp = reader.getXPPParser();

        // Bookkeeping: determine what functionality the remote server offers.
        boolean saslEXTERNALoffered = false;
        if (features.element("mechanisms") != null) {
            Iterator<Element> it = features.element( "mechanisms").elementIterator();
            while (it.hasNext()) {
                Element mechanism = it.next();
                if ("EXTERNAL".equals(mechanism.getTextTrim())) {
                    saslEXTERNALoffered = true;
                    break;
                }
            }
        }
        final boolean dialbackOffered = features.element("dialback") != null;

        log.debug("Remote server is offering dialback: {}, EXTERNAL SASL: {}", dialbackOffered, saslEXTERNALoffered );

        LocalOutgoingServerSession result = null;

        // first, try SASL
        if (saslEXTERNALoffered) {
            log.debug( "Trying to authenticate with EXTERNAL SASL." );
            result = attemptSASLexternal(connection, xpp, reader, localDomain, remoteDomain, id, openingStream);
            if (result == null) {
                log.debug( "Failed to authenticate with EXTERNAL SASL." );
            } else {
                log.debug( "Successfully authenticated with EXTERNAL SASL." );
            }
        }

        // SASL unavailable or failed, try dialback.
        if (result == null) {
            log.debug( "Trying to authenticate with dialback." );
            result = attemptDialbackOverTLS(connection, reader, localDomain, remoteDomain, id);
            if (result == null) {
                log.debug( "Failed to authenticate with dialback." );
            } else {
                log.debug( "Successfully authenticated with dialback." );
            }
        }

        if ( result != null ) {
            log.debug( "Successfully secured and authenticated connection!" );
            return result;
        } else {
            log.warn( "Unable to secure and authenticate connection: Exhausted all options." );
            return null;
        }
    }

    private static LocalOutgoingServerSession attemptDialbackOverTLS(Connection connection, XMPPPacketReader reader, String localDomain, String remoteDomain, String id) {
        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Dialback over TLS for: " + localDomain + " to: " + remoteDomain + " (Stream ID: " + id + ")]" );

        if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
            log.debug("Trying to connecting using dialback over TLS.");
            ServerDialback method = new ServerDialback(connection, localDomain);
            OutgoingServerSocketReader newSocketReader = new OutgoingServerSocketReader(reader);
            if (method.authenticateDomain(newSocketReader, localDomain, remoteDomain, id)) {
                log.debug("Dialback over TLS was successful.");
                StreamID streamID = new BasicStreamIDFactory().createStreamID(id);
                LocalOutgoingServerSession session = new LocalOutgoingServerSession(localDomain, connection, newSocketReader, streamID);
                connection.init(session);
                // Set the hostname as the address of the session
                session.setAddress(new JID(null, remoteDomain, null));
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
    
    private static LocalOutgoingServerSession attemptSASLexternal(SocketConnection connection, MXParser xpp, XMPPPacketReader reader, String localDomain, String remoteDomain, String id, StringBuilder openingStream) throws DocumentException, IOException, XmlPullParserException {
        final Logger log = LoggerFactory.getLogger( Log.getName() + "[EXTERNAL SASL for: " + localDomain + " to: " + remoteDomain + " (Stream ID: " + id + ")]" );

        log.debug("Starting EXTERNAL SASL.");
        if (doExternalAuthentication(localDomain, connection, reader)) {
            log.debug("EXTERNAL SASL was successful.");
            // SASL was successful so initiate a new stream
            connection.deliverRawText(openingStream.toString());
            
            // Reset the parser
            //xpp.resetInput();
            //             // Reset the parser to use the new secured reader
            xpp.setInput(new InputStreamReader(connection.getTLSStreamHandler().getInputStream(), StandardCharsets.UTF_8));
            // Skip the opening stream sent by the server
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }

            // SASL authentication was successful so create new OutgoingServerSession
            id = xpp.getAttributeValue("", "id");
            StreamID streamID = new BasicStreamIDFactory().createStreamID(id);
            LocalOutgoingServerSession session = new LocalOutgoingServerSession(localDomain, connection, new OutgoingServerSocketReader(reader), streamID);
            connection.init(session);
            // Set the hostname as the address of the session
            session.setAddress(new JID(null, remoteDomain, null));
            // Set that the session was created using TLS+SASL (no server dialback)
            session.usingServerDialback = false;
            return session;
        }
        else {
            log.debug("EXTERNAL SASL failed.");
            return null;
        }  	
    }
    
    private static boolean doExternalAuthentication(String localDomain, SocketConnection connection,
            XMPPPacketReader reader) throws DocumentException, IOException, XmlPullParserException {

        StringBuilder sb = new StringBuilder();
        sb.append("<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"EXTERNAL\">");
        sb.append(StringUtils.encodeBase64(localDomain));
        sb.append("</auth>");
        connection.deliverRawText(sb.toString());

        Element response = reader.parseDocument().getRootElement();
        return response != null && "success".equals(response.getName());
    }

    public LocalOutgoingServerSession(String localDomain, Connection connection, OutgoingServerSocketReader socketReader, StreamID streamID) {
        super(localDomain, connection, streamID);
        this.socketReader = socketReader;
        socketReader.setSession(this);
    }

    @Override
    boolean canProcess(Packet packet) {
        final String senderDomain = packet.getFrom().getDomain();
        final String recipDomain = packet.getTo().getDomain();
        boolean processed = true;
        if (!checkOutgoingDomainPair(senderDomain, recipDomain)) {
            synchronized (("Auth::" + senderDomain).intern()) {
                if (!checkOutgoingDomainPair(senderDomain, recipDomain) &&
                        !authenticateSubdomain(senderDomain, packet.getTo().getDomain())) {
                    // Return error since sender domain was not validated by remote server
                    processed = false;
                }
            }
        }
        if (!processed) {
            returnErrorToSender(packet);
        }
        return processed;
    }

    @Override
    void deliver(Packet packet) throws UnauthorizedException {
        if (!conn.isClosed()) {
            conn.deliver(packet);
        }
    }

    @Override
    public boolean authenticateSubdomain(String localDomain, String remoteDomain) {
        if (!usingServerDialback) {
            /*
             * We cannot do this reliably; but this code should be unreachable.
             */
            return false;
        }
        ServerDialback method = new ServerDialback(getConnection(), localDomain);
        if (method.authenticateDomain(socketReader, localDomain, remoteDomain, getStreamID().getID())) {
            // Add the validated domain as an authenticated domain
            addOutgoingDomainPair(localDomain, remoteDomain);
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

    @Override
    public String getAvailableStreamFeatures() {
        // Nothing special to add
        return null;
    }

    @Override
    public void addOutgoingDomainPair(String localDomain, String remoteDomain) {
        final DomainPair domainPair = new DomainPair(localDomain, remoteDomain);
        outgoingDomainPairs.add(domainPair);
        XMPPServer.getInstance().getRoutingTable().addServerRoute(domainPair, this);
    }

    @Override
    public boolean checkOutgoingDomainPair(String localDomain, String remoteDomain) {
        final DomainPair pair = new DomainPair(localDomain, remoteDomain);
        final boolean result =  outgoingDomainPairs.contains(pair);
        Log.trace( "Authentication exists for outgoing domain pair {}: {}", pair, result );
        return result;
    }

    @Override
    public Collection<DomainPair> getOutgoingDomainPairs() {
        return outgoingDomainPairs;
    }

    @Override
    public String toString()
    {
        return this.getClass().getSimpleName() +"{" +
            "address=" + getAddress() +
            ", streamID=" + getStreamID() +
            ", status=" + getStatus() +
            (getStatus() == STATUS_AUTHENTICATED ? " (authenticated)" : "" ) +
            (getStatus() == STATUS_CONNECTED ? " (connected)" : "" ) +
            (getStatus() == STATUS_CLOSED ? " (closed)" : "" ) +
            ", isSecure=" + isSecure() +
            ", isDetached=" + isDetached() +
            ", isUsingServerDialback=" + isUsingServerDialback() +
            ", outgoingDomainPairs=" + getOutgoingDomainPairs().stream().map( DomainPair::toString ).collect(Collectors.joining(", ", "{", "}")) +
            '}';
    }
}
