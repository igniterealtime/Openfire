/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2026 Ignite Realtime Foundation. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import org.dom4j.Element;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.event.ServerSessionEventDispatcher;
import org.jivesoftware.openfire.nio.NettySessionInitializer;
import org.jivesoftware.openfire.nio.NetworkEntityUnreachableException;
import org.jivesoftware.openfire.server.OutgoingServerSocketReader;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.openfire.streammanagement.OutboundResumeRequest;
import org.jivesoftware.openfire.streammanagement.RequestingStreamManager;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * Once the connection has been established with the remote server, at least a domain has been
 * authenticated, and negotiation of Stream Management has finished, then a new route will be added
 * to the routing table for this connection. For optimization reasons the same outgoing connection
 * will be used even if the remote server has several hostnames. However, different routes will be
 * created in the routing table for each hostname of the remote server.
 *
 * @author Gaston Dombiak
 */
public class LocalOutgoingServerSession extends LocalServerSession implements OutgoingServerSession {

    private static final Logger Log = LoggerFactory.getLogger(LocalOutgoingServerSession.class);


    /**
     * Controls the S2S outgoing session initialise timeout time in seconds
     */
    public static final SystemProperty<Duration> INITIALISE_TIMEOUT_SECONDS = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.server.session.initialise-timeout")
        .setDefaultValue(Duration.ofSeconds(10))
        .setMinValue(Duration.ZERO)
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .build();

    /**
     * Maximum time to wait, after authentication of a new outgoing server session, for the remote server to answer a
     * request to enable Stream Management. The session is not used before it answers or this time has passed. This is
     * also the time to wait for the remote server to answer a request to resume the stream of a detached session on a
     * new connection, after which that connection is closed and another attempt is made, until the detach timeout of
     * the session has passed (see {@link #resumeOnNewConnection()}).
     */
    public static final SystemProperty<Duration> STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.server.session.stream-management-negotiation-timeout")
        .setDefaultValue(Duration.ofSeconds(5))
        .setMinValue(Duration.ZERO)
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDynamic(true)
        .build();

    /**
     * Maximum time to wait for an outgoing server session's teardown to complete before abandoning a stanza re-delivery attempt.
     */
    public static final SystemProperty<Duration> REDELIVERY_TIMEOUT_SECONDS = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.server.session.redelivery-timeout")
        .setDefaultValue(Duration.ofSeconds(30))
        .setMinValue(Duration.ZERO)
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .build();

    /**
     * Runs the attempts to resume the stream of a detached session on a new connection (see {@link #startResumption()}).
     */
    @VisibleForTesting
    static volatile Executor resumptionExecutor = task -> TaskEngine.getInstance().submit(task);

    /**
     * The domain pairs that are authenticated on this session. They are added by the threads that authenticate them, and
     * read by other threads, such as those that route stanzas, the close listener and cluster tasks (OF-3420).
     */
    private final Collection<DomainPair> outgoingDomainPairs = ConcurrentHashMap.newKeySet();

    /**
     * Authenticates the local domain to the remote domain. Once authenticated the remote domain can be expected to
     * start accepting data from the local domain.
     *
     * An existing connection is re-used when it is authenticated for the domain pair. Otherwise, a new connection is
     * created: a connection is not used to authenticate a domain pair other than those it was authenticated for.
     *
     * DNS will be used to find hosts for the remote domain. When DNS records do not specify a port, port 5269 will be
     * used unless this default is overridden by the <b>xmpp.server.socket.remotePort</b> property.
     *
     * @param domainPair the local and remote domain for which authentication is to be established.
     * @return a {@link DomainAuthResult} indicating whether authentication succeeded, together with a
     *         human-readable diagnostic log of the steps taken during the attempt.
     */
    public static DomainAuthResult authenticateDomain(final DomainPair domainPair)
    {
        final String localDomain = domainPair.getLocal();
        final String remoteDomain = domainPair.getRemote();
        final Logger log = LoggerFactory.getLogger( Log.getName() + "[Authenticate local domain: '" + localDomain + "' to remote domain: '" + remoteDomain + "']" );

        final List<String> diagnosticLog = new ArrayList<>();

        log.debug( "Start domain authentication ..." );
        diagnosticLog.add("DEBUG: Start domain authentication ...");
        if (remoteDomain == null || remoteDomain.isEmpty() || remoteDomain.trim().indexOf(' ') > -1) {
            // Do nothing if the target domain is empty, null or contains whitespaces
            log.warn( "Unable to authenticate: remote domain is invalid." );
            diagnosticLog.add("WARN: Unable to authenticate: remote domain is invalid.");
            return DomainAuthResult.failure(diagnosticLog);
        }
        try {
            // Check if the remote domain is in the blacklist
            if (!RemoteServerManager.canAccess(remoteDomain)) {
                log.info( "Unable to authenticate: Remote domain is not accessible according to our configuration (typical causes: server federation is disabled, or domain is blacklisted)." );
                diagnosticLog.add("INFO: Unable to authenticate: Remote domain is not accessible according to our configuration (typical causes: server federation is disabled, or domain is blacklisted).");
                return DomainAuthResult.failure(diagnosticLog);
            }

            log.debug( "Searching for pre-existing outgoing sessions to the remote domain (if one exists, it will be re-used) ..." );
            diagnosticLog.add("DEBUG: Searching for pre-existing outgoing sessions to the remote domain (if one exists, it will be re-used) ...");
            SessionManager sessionManager = SessionManager.getInstance();
            if (sessionManager == null) {
                // Server is shutting down while we are trying to create a new s2s connection
                log.warn( "Unable to authenticate: a SessionManager instance is not available. This should not occur unless Openfire is starting up or shutting down." );
                diagnosticLog.add("WARN: Unable to authenticate: a SessionManager instance is not available. This should not occur unless Openfire is starting up or shutting down.");
                return DomainAuthResult.failure(diagnosticLog);
            }
            final OutgoingServerSession existing = sessionManager.getOutgoingServerSession(domainPair);
            if (existing != null && existing.checkOutgoingDomainPair(domainPair))
            {
                // Do nothing since the domain has already been authenticated.
                log.debug( "Authentication successful (domain was already authenticated in the pre-existing session)." );
                diagnosticLog.add("DEBUG: Authentication successful (domain was already authenticated in the pre-existing session).");
                //inform all listeners as well.
                ServerSessionEventDispatcher.dispatchEvent(existing, ServerSessionEventDispatcher.EventType.session_created);
                return DomainAuthResult.success(diagnosticLog);
            }
            // OF-3470: each domain pair gets a connection of its own. An existing connection is not used to authenticate
            // another domain pair on it (dialback multiplexing, XEP-0220 section 2.6).
            OutgoingServerSession session = null;
            try {
                log.debug("No existing session is authenticated for this domain pair. Creating a new session ...");
                diagnosticLog.add("DEBUG: No existing session is authenticated for this domain pair. Creating a new session ...");
                int port = RemoteServerManager.getPortForServer(remoteDomain);
                session = createOutgoingSession(domainPair, port, diagnosticLog);
                if (session != null) {
                    log.debug("Created a new session.");
                    diagnosticLog.add("DEBUG: Created a new session.");

                    session.addOutgoingDomainPair(domainPair);
                    try {
                        sessionManager.outgoingServerSessionCreated((LocalOutgoingServerSession) session);
                    } catch (Exception e) {
                        log.debug("Failed to register close listener for newly created session to '{}'. Rolling back route registration to prevent an orphaned route.", remoteDomain, e);
                        diagnosticLog.add("DEBUG: " + fmt("Failed to register close listener for newly created session to '{}'. Rolling back route registration to prevent an orphaned route.", remoteDomain) + " - " + e);
                        // Explicitly remove all routes that were just added, as the close listener that would normally do so may never have been registered.
                        for (DomainPair pair : session.getOutgoingDomainPairs()) {
                            XMPPServer.getInstance().getRoutingTable().removeServerRoute(pair);
                        }
                        throw e;
                    }

                    log.debug("Authentication successful.");
                    diagnosticLog.add("DEBUG: Authentication successful.");
                    //inform all listeners as well.
                    ServerSessionEventDispatcher.dispatchEvent(session, ServerSessionEventDispatcher.EventType.session_created);
                    return DomainAuthResult.success(diagnosticLog);
                } else {
                    log.warn("Unable to authenticate: Fail to create new session.");
                    diagnosticLog.add("WARN: Unable to authenticate: Fail to create new session.");
                    return DomainAuthResult.failure(diagnosticLog);
                }
            } catch (Exception e) {
                if (session != null) {
                    session.close();
                }
                throw e;
            }
        }
        catch (Exception e)
        {
            log.error( "An exception occurred while authenticating to remote domain '{}'!", remoteDomain, e );
            diagnosticLog.add("ERROR: " + fmt("An exception occurred while authenticating to remote domain '{}'!", remoteDomain) + " - " + e);
            return DomainAuthResult.failure(diagnosticLog);
        }
    }

    /**
     * Formats an SLF4J-style message pattern with arguments, producing the interpolated string.
     * Used to populate the diagnostic log alongside SLF4J logger calls.
     */
    private static String fmt(final String pattern, final Object... args) {
        return MessageFormatter.arrayFormat(pattern, args).getMessage();
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
    @VisibleForTesting
    static LocalOutgoingServerSession createOutgoingSession(@Nonnull final DomainPair domainPair, int port) {
        return createOutgoingSession(domainPair, port, new ArrayList<>());
    }

    /**
     * Establishes a new outgoing session to a remote domain, appending diagnostic messages to the
     * supplied log as the attempt progresses.
     *
     * @param domainPair    the local and remote domain for which a session is to be established.
     * @param port          default port to use to establish the connection.
     * @param diagnosticLog mutable list to which human-readable, level-prefixed diagnostic entries are appended.
     * @return new outgoing session to a remote domain, or null.
     */
    private static LocalOutgoingServerSession createOutgoingSession(@Nonnull final DomainPair domainPair, int port, @Nonnull final List<String> diagnosticLog) {
        final Logger log = LoggerFactory.getLogger(Log.getName() + "[Create outgoing session for: " + domainPair + "]");

        log.debug("Creating new session...");
        diagnosticLog.add("DEBUG: Creating new session...");

        ConnectionListener listener = XMPPServer
            .getInstance()
            .getConnectionManager()
            .getListener(ConnectionType.SOCKET_S2S, false);
        NettySessionInitializer sessionInitialiser = new NettySessionInitializer(domainPair, port);
        try {
            // Wait for the future to give us a session...
            // Set a read timeout so that we don't keep waiting forever
            final LocalOutgoingServerSession session = (LocalOutgoingServerSession) sessionInitialiser.init(listener).get(INITIALISE_TIMEOUT_SECONDS.getValue().getSeconds(), TimeUnit.SECONDS);
            if (session != null && !awaitStreamManagementNegotiation(session, log, diagnosticLog)) {
                sessionInitialiser.stop();
                return null;
            }
            return session;
        } catch (NetworkEntityUnreachableException e) {
            Log.warn("Cannot connect to XMPP domain '{}' (from '{}'): Unable to create a socket connection to a host that associated " +
                "to the domain. This is typically caused by an outage (the remote server may be turned off) or a network configuration " +
                "issue (eg: missing or invalid DNS records, restrictive firewalls, etc.).", domainPair.getRemote(), domainPair.getLocal());
            diagnosticLog.add("WARN: " + fmt("Cannot connect to XMPP domain '{}' (from '{}'): Unable to create a socket connection to a host that associated to the domain. This is typically caused by an outage (the remote server may be turned off) or a network configuration issue (eg: missing or invalid DNS records, restrictive firewalls, etc.).", domainPair.getRemote(), domainPair.getLocal()));
            sessionInitialiser.stop();
        } catch (TimeoutException e) {
            Log.warn("Cannot connect to XMPP domain '{}' (from '{}'): The connection attempt timed out after {}. This is typically caused by a " +
                "network configuration issue (eg: missing or invalid DNS records, restrictive firewalls, etc.).",
                domainPair.getRemote(), domainPair.getLocal(), INITIALISE_TIMEOUT_SECONDS.getValue());
            diagnosticLog.add("WARN: " + fmt("Cannot connect to XMPP domain '{}' (from '{}'): The connection attempt timed out after {}. This is typically caused by a network configuration issue (eg: missing or invalid DNS records, restrictive firewalls, etc.).", domainPair.getRemote(), domainPair.getLocal(), INITIALISE_TIMEOUT_SECONDS.getValue()));
            sessionInitialiser.stop();
        } catch (Exception e) {
            // This might be RFC6120, section 5.4.2.2 "Failure Case" or even an unrelated problem. Handle 'normally'.
            log.warn("An exception occurred while creating a session. Closing connection.", e);
            diagnosticLog.add("WARN: An exception occurred while creating a session. Closing connection. - " + e);
            sessionInitialiser.stop();
        }

        return null;
    }

    /**
     * Waits until negotiation of Stream Management on a new, authenticated session has finished, so that the session is
     * not used before then: until the remote server enabled or refused Stream Management, or Openfire decided not to ask
     * for it, for example because the remote server does not offer it. The wait ends early when the connection closes,
     * and ends after {@link #STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT} otherwise.
     *
     * When Stream Management is disabled for servers, and has not been requested on the session, this does not wait.
     *
     * @param session       the new session.
     * @param log           the logger to log the outcome to.
     * @param diagnosticLog mutable list to which human-readable, level-prefixed diagnostic entries are appended.
     * @return false if the connection closed before negotiation finished, otherwise true.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    private static boolean awaitStreamManagementNegotiation(@Nonnull final LocalOutgoingServerSession session, @Nonnull final Logger log, @Nonnull final List<String> diagnosticLog) throws InterruptedException
    {
        final RequestingStreamManager streamManager = session.getStreamManager();
        final CompletableFuture<RequestingStreamManager.EnableRequestStatus> outcome = streamManager.getNegotiationOutcome();
        // OF-2425: after SASL EXTERNAL, the features that tell whether Stream Management is offered may not have arrived yet. Without the setting, there is no need to know.
        if (!outcome.isDone() && !StreamManager.SERVER_ACTIVE.getValue() && streamManager.getEnableRequestStatus() == RequestingStreamManager.EnableRequestStatus.NOT_SENT) {
            log.trace("Not waiting for negotiation of Stream Management on session {}, as it is disabled for servers and was not requested.", session);
            return true;
        }

        final Duration timeout = STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT.getValue();
        final CompletableFuture<Void> closed = session.getConnection().getCloseFuture().toCompletableFuture();
        try {
            CompletableFuture.anyOf(outcome, closed).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.debug("Negotiation of Stream Management did not finish within {} (request status: {}). Using session {} without waiting further.", timeout, streamManager.getEnableRequestStatus(), session);
            diagnosticLog.add("DEBUG: " + fmt("Negotiation of Stream Management did not finish within {} (request status: {}). Using the session without waiting further.", timeout, streamManager.getEnableRequestStatus()));
            return true;
        } catch (ExecutionException e) {
            // Only the close future can complete exceptionally. That also means that the connection closed.
            log.trace("The close future of session {} completed exceptionally.", session, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        if (!outcome.isDone()) {
            log.debug("The connection of session {} closed before negotiation of Stream Management finished (request status: {}). The session is not used.", session, streamManager.getEnableRequestStatus());
            diagnosticLog.add("DEBUG: " + fmt("The connection closed before negotiation of Stream Management finished (request status: {}). The session is not used.", streamManager.getEnableRequestStatus()));
            return false;
        }
        return true;
    }

    private static boolean configDoesNotRequireTls(ConnectionConfiguration listenerConfiguration) {
        return listenerConfiguration.getTlsPolicy() != Connection.TLSPolicy.required;
    }

    public LocalOutgoingServerSession(String localDomain, Connection connection, OutgoingServerSocketReader socketReader, StreamID streamID) {
        super(localDomain, connection, streamID);
        socketReader.setSession(this);
    }

    public LocalOutgoingServerSession(String localDomain, Connection connection, StreamID streamID) {
        super(localDomain, connection, streamID);
    }

    @Override
    boolean canDeliver(@Nonnull final Packet stanza) {
        final DomainPair domainPair = new DomainPair(stanza.getFrom().getDomain(), stanza.getTo().getDomain());
        if (!checkOutgoingDomainPair(domainPair)) {
            // OF-3470: the domain pair is not authenticated on this session, and is not authenticated on it now either.
            Log.debug("Not sending stanza {} on session {}, which is not authenticated for domain pair {}. Returning an error to the sender.", stanza.getID(), getStreamID(), domainPair);
            returnErrorToSenderAsync(stanza);
            return false;
        }
        return true;
    }

    /**
     * Delivers a stanza to the remote server.
     *
     * While the connection of the session is broken and its stream can be resumed, and while the session is detached,
     * the stanza is queued on the stream manager, behind the unacknowledged stanzas, rather than sent (see
     * {@link RequestingStreamManager#queue(Packet, boolean)}), until the stream is resumed and the queued stanzas are
     * sent (see {@link RequestingStreamManager#onResume(JID, long)}). Otherwise, a stanza for a connection that is closed, or
     * for a session that has ended, is routed again once the connection is torn down.
     *
     * @param packet the stanza to deliver.
     * @throws UnauthorizedException if the connection refuses the stanza.
     */
    @Override
    void deliver(Packet packet) throws UnauthorizedException {
        final RequestingStreamManager streamManager = getStreamManager();
        // OF-2425: read once, as detaching the session sets it to null.
        final Connection connection = conn;
        final boolean open = connection != null && !connection.isClosed();
        // OF-2425: as for clients (OF-2921), the lock is only taken while sent stanzas are counted, so that they are numbered in the order in which they are sent.
        // Also while Stream Management can still be requested, so that a stanza is counted exactly when the peer counts it: when it follows <enable/>.
        final RequestingStreamManager.EnableRequestStatus status = streamManager.getEnableRequestStatus();
        if (open && !streamManager.isEnabled() && (status == RequestingStreamManager.EnableRequestStatus.REFUSED || status == RequestingStreamManager.EnableRequestStatus.SKIPPED)) {
            connection.deliver(packet);
            return;
        }
        synchronized (streamManager) {
            // OF-2425: decided under the lock, so that no stanza is sent once stanzas are queued (decision 7).
            if (streamManager.queue(packet, !open)) {
                return;
            }
            if (open) {
                connection.deliver(packet);
                streamManager.sentStanza(packet);
                return;
            }
        }

        // Guard against a double bounce: if re-routing this error stanza fails (i.e. the remote server is still
        // unreachable after teardown), OutgoingSessionPromise would attempt to return an error to the original sender,
        // producing an error in response to an error.
        if (packet.getError() != null) {
            Log.debug("Dropping error stanza to {} on closed connection to avoid a double-bounce: {}", packet.getTo(), packet);
            return;
        }

        // The connection is closed but teardown may still be in progress - the routing table entry for this session
        // might not yet be removed. Waiting for closeFuture guarantees that all ConnectionCloseListeners (including
        // route removal) have finished before we re-route, so the packet router will not hand the stanza straight back
        // to this dying session. OF-3176
        Log.info("Connection to {} is closed; will attempt re-delivery once teardown completes.", packet.getTo());

        final Packet stanza = packet.createCopy(); // Make a defensive copy before scheduling the callback to ensure that the original data gets rescheduled.
        RequestingStreamManager.copyRoutedAgainMarker(packet, stanza); // OF-2425: a stanza that was sent again must not be sent again a second time.
        final long timeoutSeconds = REDELIVERY_TIMEOUT_SECONDS.getValue().toSeconds();
        // OF-2425: a session that was detached and then ended has no connection, and its routes are removed already.
        final CompletableFuture<Void> closeFuture = connection == null ? CompletableFuture.completedFuture(null) : connection.getCloseFuture().toCompletableFuture();
        final CompletableFuture<Void> timeoutFuture = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(timeoutSeconds, TimeUnit.SECONDS)
            .execute(() -> timeoutFuture.completeExceptionally(new TimeoutException("Timed out waiting for connection teardown.")));

        CompletableFuture.anyOf(closeFuture, timeoutFuture)
            .whenCompleteAsync((v, ex) -> {
                final Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof TimeoutException) {
                    // The teardown did not complete within the configured window. We cannot safely re-route.
                    Log.warn("Timed out after {}s waiting for connection teardown to complete for {}; dropping stanza and attempting to notify sender if appropriate. Packet: {}", timeoutSeconds, stanza.getTo(), stanza);
                    returnErrorToSenderAsync(stanza);
                } else if (cause != null) {
                    // Unexpected exception (e.g. CancellationException). Log with stack trace and attempt sender notification when appropriate.
                    Log.warn("Unexpected exception while waiting for connection teardown to complete for {}; dropping stanza and attempting to notify sender if appropriate. Packet: {}", stanza.getTo(), stanza, cause);
                    returnErrorToSenderAsync(stanza);
                } else {
                    // Teardown is complete. The route for this session has been removed. Delegate back to the packet
                    // router, which will either find a concurrently-established session or trigger authenticateDomain()
                    // to create a new one.
                    Log.debug("Connection teardown complete; re-routing stanza to {}.", stanza.getTo());
                    XMPPServer.getInstance().getPacketRouter().route(stanza);
                }
            });
    }

    /**
     * Does not authenticate the domain pair: a session is not used to authenticate a domain pair other than those it was
     * authenticated for. A new session is created for it instead, by {@link #authenticateDomain(DomainPair)}.
     *
     * @param domainPair the local and remote domain for which authentication was requested.
     * @return false
     * @deprecated A session is no longer used to authenticate more domain pairs (dialback multiplexing).
     */
    @Override
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public boolean authenticateSubdomain(@Nonnull final DomainPair domainPair) {
        Log.debug("Not authenticating domain pair {} on session {}: a session is not used to authenticate more domain pairs.", domainPair, getStreamID());
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

    /**
     * Sets the session to detached mode, indicating that the underlying connection has been closed. From then on,
     * stanzas that are delivered to the session are queued rather than sent. The routes of the session are kept.
     */
    @Override
    public void setDetached() {
        // OF-2425: Started before the connection is released, so that no stanza that is delivered meanwhile is sent instead of queued.
        getStreamManager().startQueueing();
        super.setDetached();
    }

    /**
     * Sets the session to detached mode, as {@link #setDetached()} does, but only when the session still uses the
     * provided connection, which must be closed (see {@link LocalSession#setDetachedIfConnectionIs(Connection)}).
     * Stanzas are queued from the moment at which the session is detached.
     *
     * @param connection the closed connection that the session must still use to be detached.
     * @return true if the session is detached, false if it no longer uses the provided connection.
     */
    @Override
    public boolean setDetachedIfConnectionIs(@Nonnull final Connection connection) {
        if (!super.setDetachedIfConnectionIs(connection)) {
            return false;
        }
        // OF-2425: Started after detaching, as the stream manager lock is not taken under the session lock. Until then, stanzas are queued as the connection is closed.
        getStreamManager().startQueueing();
        return true;
    }

    /**
     * Starts an attempt to resume the stream of this session, which is detached, on a new connection, on another thread
     * (see {@link #resumeOnNewConnection()}).
     */
    public void startResumption()
    {
        Log.debug("Starting an attempt to resume the stream of detached session {} on a new connection.", this);
        resumptionExecutor.execute(this::resumeOnNewConnection);
    }

    /**
     * Attempts to resume the stream of this session, which is detached, on a new connection (XEP-0198 § 5).
     *
     * A new connection is opened from this cluster node to the remote domain of this session, and the domain pair of the
     * local and remote domain for which this session was created is authenticated on it, as for a new session. The
     * remote server is then asked to resume the stream of this session, instead of enabling Stream Management (see
     * {@link OutboundResumeRequest#send(LocalOutgoingServerSession, Collection)}). The session of the new connection is
     * not made a route, and no event is dispatched for it: when the stream is resumed, the connection is attached to
     * this session (see {@link #resumeWithConnectionOf(LocalOutgoingServerSession, long)}).
     *
     * When the remote domain is not accessible, no connection can be authenticated, the remote server does not offer the
     * version of Stream Management of this stream, or does not answer within
     * {@link #STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT}, the new connection is closed, and this session is ended, as when it
     * is not resumed in time (see {@link SessionManager#terminateDetached(LocalSession)}). When the remote server answers,
     * the processing of that answer takes care of this session.
     */
    @VisibleForTesting
    void resumeOnNewConnection()
    {
        final DomainPair domainPair = new DomainPair(getServerName(), getAddress().getDomain());
        NettySessionInitializer initialiser = null;
        boolean endSession = true;
        try {
            if (!RemoteServerManager.canAccess(domainPair.getRemote())) {
                Log.debug("Not resuming detached session {}, as remote domain '{}' is not accessible according to the configuration.", this, domainPair.getRemote());
                return;
            }
            final int port = RemoteServerManager.getPortForServer(domainPair.getRemote());
            final ConnectionListener listener = XMPPServer.getInstance().getConnectionManager().getListener(ConnectionType.SOCKET_S2S, false);
            initialiser = new NettySessionInitializer(domainPair, port, this);
            final LocalSession provider = initialiser.init(listener).get(INITIALISE_TIMEOUT_SECONDS.getValue().getSeconds(), TimeUnit.SECONDS);
            if (!(provider instanceof LocalOutgoingServerSession connectionProvider)) {
                Log.debug("Unable to resume detached session {}, as no new connection for {} could be authenticated: {}", this, domainPair, provider);
                return;
            }
            endSession = awaitResumption(connectionProvider);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.debug("Interrupted while resuming detached session {} on a new connection for {}.", this, domainPair, e);
        } catch (Exception e) {
            Log.debug("Unable to resume detached session {} on a new connection for {}.", this, domainPair, e);
        } finally {
            if (endSession) {
                if (initialiser != null) {
                    initialiser.stop();
                }
                Log.debug("Ending detached session {}, as its stream was not resumed on a new connection.", this);
                sessionManager.terminateDetached(this);
            }
        }
    }

    /**
     * Waits for the remote server to answer the request to resume the stream of this session on the connection of
     * another session, and gives up on that request when it is not answered in time, or not sent.
     *
     * @param connectionProvider the session of the new connection.
     * @return true when the request was given up, after which the caller ends this session; false when the remote server
     * answered it, in which case the processing of that answer takes care of this session.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    private boolean awaitResumption(@Nonnull final LocalOutgoingServerSession connectionProvider) throws InterruptedException
    {
        final RequestingStreamManager streamManager = connectionProvider.getStreamManager();
        // OF-2425: A connection that this session took over already is no longer the connection of the provider.
        final Connection connection = connectionProvider.getConnection();
        final CompletableFuture<Void> closed = connection == null ? CompletableFuture.completedFuture(null) : connection.getCloseFuture().toCompletableFuture();
        final Duration timeout = STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT.getValue();
        try {
            CompletableFuture.anyOf(streamManager.getResumeRequest().getFinished(), closed).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.debug("The remote server did not answer the request to resume the stream of session {} on the connection of session {} within {} (request status: {}).", this, connectionProvider, timeout, streamManager.getResumeRequest().getStatus());
        } catch (ExecutionException e) {
            // Only the close future can complete exceptionally. That also means that the connection closed.
            Log.trace("The close future of the connection of session {} completed exceptionally.", connectionProvider, e);
        }
        return streamManager.getResumeRequest().abandon();
    }

    /**
     * Resumes the stream of this session, which is detached, with the connection of another session, of which the
     * remote server resumed the stream with {@code <resumed/>}: this session takes over that connection, and sends the
     * stanzas that the remote server did not receive, followed by the stanzas that were queued while it was detached
     * (see {@link RequestingStreamManager#onResume(JID, long)}). The other session is not used after this.
     *
     * This session is first taken from the detached sessions, so that it is not ended while it is resumed. When that
     * fails, because it was ended meanwhile, nothing is changed. Once resumed, this session reports the authentication
     * method of the connection that it took over, and a close listener is registered on that connection, as for a new
     * session.
     *
     * @param connectionProvider the session of the connection on which the stream is resumed.
     * @param h the number of stanzas that the remote server reports to have handled, which the caller validated.
     * @return true if the stream is resumed, false if this session could not be taken from the detached sessions.
     */
    public boolean resumeWithConnectionOf(@Nonnull final LocalOutgoingServerSession connectionProvider, final long h)
    {
        // OF-3420: Claimed before the connection is moved, so that SessionManager#terminateDetached does not end a session that is resumed.
        if (!sessionManager.removeDetached(this)) {
            Log.debug("Not resuming session {} on the connection of session {}, as it is no longer detached.", this, connectionProvider);
            return false;
        }
        final AuthenticationMethod method = connectionProvider.getAuthenticationMethod();
        Log.debug("Resuming session {} on the connection of session {}, authenticated with {} (h: {}).", this, connectionProvider, method, h);
        if (method != null) {
            // OF-2425: The method was cleared when the former connection closed (LocalServerSession#setStatus).
            setAuthenticationMethod(method);
        }
        reattach(connectionProvider, h);
        sessionManager.outgoingServerSessionCreated(this);
        return true;
    }

    /**
     * Get XEP-0198 Stream manager for session. On this type of session, Openfire asks the peer for Stream Management.
     *
     * @return The StreamManager for the session.
     */
    @Override
    public RequestingStreamManager getStreamManager() {
        // OF-2425: StreamManager#forSession creates a RequestingStreamManager for this type of session.
        return (RequestingStreamManager) super.getStreamManager();
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
