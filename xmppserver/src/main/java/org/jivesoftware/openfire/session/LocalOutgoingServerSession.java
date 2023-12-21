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

package org.jivesoftware.openfire.session;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.dom4j.Element;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.event.ServerSessionEventDispatcher;
import org.jivesoftware.openfire.nio.NettySessionInitializer;
import org.jivesoftware.openfire.server.OutgoingServerSocketReader;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Server-to-server communication is done using two TCP connections between the servers. One
 * connection is used for sending packets while the other connection is used for receiving packets.
 * The {@code OutgoingServerSession} represents the connection to a remote server that will only
 * be used for sending packets.<p>
 *
 * Currently only the Server Dialback method is being used for authenticating with the remote
 * server. Use {@link #authenticateDomain(DomainPair)} to create a new connection to a remote
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

    private static final Interner<JID> remoteAuthMutex = Interners.newWeakInterner();

    /**
     * Controls the S2S outgoing session initialise timeout time in seconds
     */
    public static final SystemProperty<Duration> INITIALISE_TIMEOUT_SECONDS = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.server.session.initialise-timeout")
        .setDefaultValue(Duration.ofSeconds(10))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .build();

    private OutgoingServerSocketReader socketReader;
    private final Collection<DomainPair> outgoingDomainPairs = new HashSet<>();

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
     * @param domainPair the local and remote domain for which authentication is to be established.
     * @return True if the domain was authenticated by the remote server.
     */
    public static boolean authenticateDomain(final DomainPair domainPair) {
        final String localDomain = domainPair.getLocal();
        final String remoteDomain = domainPair.getRemote();
        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Authenticate local domain: '" + localDomain + "' to remote domain: '" + remoteDomain + "']" );

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
            if (session != null && session.checkOutgoingDomainPair(domainPair))
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
                if ( session.checkOutgoingDomainPair(domainPair) )
                {
                    // Do nothing since the domain has already been authenticated.
                    log.debug( "Authentication successful (domain was already authenticated in the pre-existing session)." );
                    return true;
                }

                // A session already exists so authenticate the domain using that session.
                if ( session.authenticateSubdomain(domainPair) )
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
                try {
                    log.debug("Unable to re-use an existing session. Creating a new session ...");
                    int port = RemoteServerManager.getPortForServer(remoteDomain);
                    session = createOutgoingSession(domainPair, port);
                    if (session != null) {
                        log.debug("Created a new session.");

                        session.addOutgoingDomainPair(domainPair);
                        sessionManager.outgoingServerSessionCreated((LocalOutgoingServerSession) session);
                        log.debug("Authentication successful.");
                        //inform all listeners as well.
                        ServerSessionEventDispatcher.dispatchEvent(session, ServerSessionEventDispatcher.EventType.session_created);
                        return true;
                    } else {
                        log.warn("Unable to authenticate: Fail to create new session.");
                        return false;
                    }
                } catch (Exception e) {
                    if (session != null) {
                        session.close();
                    }
                    throw e;
                }
            }
        }
        catch (Exception e)
        {
            log.error( "An exception occurred while authenticating to remote domain '{}'!", remoteDomain, e );
            return false;
        }
    }

    /**
     * Establishes a new outgoing session to a remote domain. If the remote domain supports TLS and SASL then the new
     * outgoing connection will be encrypted with TLS and authenticated using SASL. However, if TLS or SASL is not
     * supported by the remote domain or if an error occurred while securing or authenticating the connection using SASL
     * then server dialback will be used.
     *
     * @param domainPair the local and remote domain for which a session is to be established.
     * @param port default port to use to establish the connection.
     * @return new outgoing session to a remote domain, or null.
     */
    // package-protected to facilitate unit testing..
    static LocalOutgoingServerSession createOutgoingSession(@Nonnull final DomainPair domainPair, int port) {
        final Logger log = LoggerFactory.getLogger(Log.getName() + "[Create outgoing session for: " + domainPair + "]");

        log.debug("Creating new session...");

        ConnectionListener listener = XMPPServer
            .getInstance()
            .getConnectionManager()
            .getListener(ConnectionType.SOCKET_S2S, false);
        NettySessionInitializer sessionInitialiser = new NettySessionInitializer(domainPair, port);
        try {
            // Wait for the future to give us a session...
            // Set a read timeout so that we don't keep waiting forever
            return (LocalOutgoingServerSession) sessionInitialiser.init(listener).get(INITIALISE_TIMEOUT_SECONDS.getValue().getSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            // This might be RFC6120, section 5.4.2.2 "Failure Case" or even an unrelated problem. Handle 'normally'.
            log.warn("An exception occurred while creating a session. Closing connection.", e);
            sessionInitialiser.stop();
        }

        return null;
    }

    private static boolean configDoesNotRequireTls(ConnectionConfiguration listenerConfiguration) {
        return listenerConfiguration.getTlsPolicy() != Connection.TLSPolicy.required;
    }

    public LocalOutgoingServerSession(String localDomain, Connection connection, OutgoingServerSocketReader socketReader, StreamID streamID) {
        super(localDomain, connection, streamID);
        this.socketReader = socketReader;
        socketReader.setSession(this);
    }

    public LocalOutgoingServerSession(String localDomain, Connection connection, StreamID streamID) {
        super(localDomain, connection, streamID);
    }

    @Override
    boolean canProcess(Packet packet) {
        final DomainPair domainPair = new DomainPair(packet.getFrom().getDomain(), packet.getTo().getDomain());
        boolean processed = true;
        synchronized (remoteAuthMutex.intern(new JID(null, domainPair.getRemote(), null))) {
            if (!checkOutgoingDomainPair(domainPair) && !authenticateSubdomain(domainPair)) {
                // Return error since sender domain was not validated by remote server
                processed = false;
            }
        }
        if (!processed) {
            returnErrorToSenderAsync(packet);
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
    public boolean authenticateSubdomain(@Nonnull final DomainPair domainPair) {
        if (!isUsingServerDialback()) {
            /*
             * We cannot do this reliably; but this code should be unreachable.
             */
            return false;
        }
        ServerDialback method = new ServerDialback(getConnection(), domainPair);
        if (method.authenticateDomain(socketReader, getStreamID().getID())) {
            // Add the validated domain as an authenticated domain
            addOutgoingDomainPair(domainPair);
            return true;
        }
        return false;
    }

    private void returnErrorToSenderAsync(Packet packet) {
        TaskEngine.getInstance().submit(() -> {
            final PacketRouter packetRouter = XMPPServer.getInstance().getPacketRouter();
            if (packet.getError() != null) {
                Log.debug("Possible double bounce: {}", packet.toXML());
            }
            try {
                if (packet instanceof IQ) {
                    if (((IQ) packet).isResponse()) {
                        Log.debug("XMPP specs forbid us to respond with an IQ error to: {}", packet.toXML());
                        return;
                    }
                    IQ reply = new IQ();
                    reply.setID(packet.getID());
                    reply.setTo(packet.getFrom());
                    reply.setFrom(packet.getTo());
                    reply.setChildElement(((IQ) packet).getChildElement().createCopy());
                    reply.setType(IQ.Type.error);
                    reply.setError(PacketError.Condition.remote_server_not_found);
                    packetRouter.route(reply);
                }
                else if (packet instanceof Presence) {
                    if (((Presence)packet).getType() == Presence.Type.error) {
                        Log.debug("Avoid generating an error in response to a stanza that itself is an error (to avoid the chance of entering an endless back-and-forth of exchanging errors). Suppress sending an {} error in response to: {}", PacketError.Condition.remote_server_not_found, packet);
                        return;
                    }
                    Presence reply = new Presence();
                    reply.setID(packet.getID());
                    reply.setTo(packet.getFrom());
                    reply.setFrom(packet.getTo());
                    reply.setType(Presence.Type.error);
                    reply.setError(PacketError.Condition.remote_server_not_found);
                    packetRouter.route(reply);
                }
                else if (packet instanceof Message) {
                    if (((Message)packet).getType() == Message.Type.error){
                        Log.debug("Avoid generating an error in response to a stanza that itself is an error (to avoid the chance of entering an endless back-and-forth of exchanging errors). Suppress sending an {} error in response to: {}", PacketError.Condition.remote_server_not_found, packet);
                        return;
                    }
                    Message reply = new Message();
                    reply.setID(packet.getID());
                    reply.setTo(packet.getFrom());
                    reply.setFrom(packet.getTo());
                    reply.setType(Message.Type.error);
                    reply.setThread(((Message)packet).getThread());
                    reply.setError(PacketError.Condition.remote_server_not_found);
                    packetRouter.route(reply);
                }
            }
            catch (Exception e) {
                Log.error("Error returning error to sender. Original packet: {}", packet, e);
            }
        });
    }

    @Override
    public List<Element> getAvailableStreamFeatures() {
        // Nothing special to add
        return Collections.emptyList();
    }

    @Override
    public void addOutgoingDomainPair(@Nonnull final DomainPair domainPair) {
        XMPPServer.getInstance().getRoutingTable().addServerRoute(domainPair, this);
        outgoingDomainPairs.add(domainPair);
    }

    @Override
    public boolean checkOutgoingDomainPair(@Nonnull final DomainPair domainPair) {
        final boolean result = outgoingDomainPairs.contains(domainPair);
        Log.trace( "Authentication exists for outgoing domain pair {}: {}", domainPair, result );
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
            "address=" + address +
            ", streamID=" + streamID +
            ", status=" + status +
            ", isEncrypted=" + isEncrypted() +
            ", isDetached=" + isDetached() +
            ", authenticationMethod=" + authenticationMethod +
            ", outgoingDomainPairs=" + getOutgoingDomainPairs().stream().map( DomainPair::toString ).collect(Collectors.joining(", ", "{", "}")) +
            '}';
    }
}
