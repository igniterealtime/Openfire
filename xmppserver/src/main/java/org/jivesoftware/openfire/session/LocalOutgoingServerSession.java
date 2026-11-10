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
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.dom4j.Element;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.event.ServerSessionEventDispatcher;
import org.jivesoftware.openfire.nio.NettySessionInitializer;
import org.jivesoftware.openfire.nio.NetworkEntityUnreachableException;
import org.jivesoftware.openfire.server.OutgoingServerSocketReader;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionType;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
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

    private static final Interner<JID> remoteAuthMutex = Interners.newWeakInterner();

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
     * The time to wait after the first failed attempt to resume the stream of a detached session on a new connection,
     * before the next attempt. It doubles after each failed attempt, up to {@link #RESUMPTION_RETRY_MAXIMUM_DELAY}.
     */
    private static final Duration RESUMPTION_RETRY_INITIAL_DELAY = Duration.ofSeconds(1);

    /**
     * The longest time to wait after a failed attempt to resume the stream of a detached session, before the next attempt.
     */
    private static final Duration RESUMPTION_RETRY_MAXIMUM_DELAY = Duration.ofSeconds(30);

    /**
     * Runs a task once the provided time has passed: the next attempt to resume the stream of a detached session on a
     * new connection, after an attempt failed (see {@link #resumeOnNewConnection()}).
     */
    @VisibleForTesting
    static volatile BiConsumer<Runnable, Duration> resumptionRetryScheduler = (task, delay) -> TaskEngine.getInstance().schedule(new TimerTask() {
        @Override
        public void run() {
            task.run();
        }
    }, delay);

    /**
     * Runs what follows the outcome of an attempt to verify a domain pair again after the stream of a session was resumed
     * (see {@link #verifyAgain(DomainPair)}).
     */
    @VisibleForTesting
    static volatile Executor verificationExecutor = task -> TaskEngine.getInstance().submit(task);

    private OutgoingServerSocketReader socketReader;
    /**
     * The domain pairs that are authenticated on this session. They are added by the threads that authenticate them, and
     * read by other threads, such as those that route stanzas, the close listener and cluster tasks (OF-3420).
     */
    private final Collection<DomainPair> outgoingDomainPairs = ConcurrentHashMap.newKeySet();

    /**
     * The stream ID of the stream header that the remote server sent on the current connection of this session, with
     * which a dialback key for that connection is generated (XEP-0185). This is the stream ID of this session, until its
     * stream is resumed on the connection of another session. Set by the thread of the connection on which the stream
     * is resumed, and read by other threads.
     */
    private volatile StreamID connectionStreamID;

    /**
     * The outcomes of the dialback requests that this session sent to verify domain pairs again after its stream was
     * resumed, by domain pair, which complete when the remote server answers (see {@link #processDialbackResult(Element)}).
     * An outcome stays registered until a later request for the same domain pair replaces it, so that an answer that
     * arrives late is not taken for the answer to the dialback request for the domain pair of the connection.
     */
    private final Map<DomainPair, CompletableFuture<Boolean>> dialbackResults = new ConcurrentHashMap<>();

    /**
     * The domain pairs that this session gave up, as they were not verified again after its stream was resumed (see
     * {@link #giveUp(DomainPair, RequestingStreamManager.VerificationAttempt, boolean)}). This session is not used to
     * authenticate them again.
     */
    private final Collection<DomainPair> givenUpDomainPairs = ConcurrentHashMap.newKeySet();

    /**
     * The moment from which the time that this session is kept while it is detached is measured (see
     * {@link #getDetachedSince()}), or null when it has never been detached.
     */
    private volatile Instant detachedSince;

    /**
     * The number of attempts to resume the stream of this session that failed since it was most recently detached.
     * Reset when the first attempt starts, and otherwise changed only by the thread that runs an attempt, as one attempt
     * runs at a time.
     */
    private volatile int failedResumptionAttempts;

    /**
     * Authenticates the local domain to the remote domain. Once authenticated the remote domain can be expected to
     * start accepting data from the local domain.
     *
     * This implementation will attempt to re-use an existing connection. A connection is deemed re-usable when it is either:
     * <ul>
     *     <li>authenticated to the remote domain itself, or:</li>
     *     <li>authenticated to a sub- or superdomain of the remote domain AND offers dialback AND is not detached AND
     *     did not give up the domain pair after its stream was resumed.</li>
     * </ul>
     *
     * When no re-usable connection exists, a new connection will be created.
     *
     * A session that is detached, while its stream can be resumed, stays the route for the domain pairs that it has, so
     * a domain pair that it has is authenticated already. A detached session has no connection, so it cannot be used to
     * authenticate another domain pair: for such a domain pair, a new connection is created.
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
            OutgoingServerSession session;
            SessionManager sessionManager = SessionManager.getInstance();
            if (sessionManager == null) {
                // Server is shutting down while we are trying to create a new s2s connection
                log.warn( "Unable to authenticate: a SessionManager instance is not available. This should not occur unless Openfire is starting up or shutting down." );
                diagnosticLog.add("WARN: Unable to authenticate: a SessionManager instance is not available. This should not occur unless Openfire is starting up or shutting down.");
                return DomainAuthResult.failure(diagnosticLog);
            }
            session = sessionManager.getOutgoingServerSession(domainPair);
            if (session != null && session.checkOutgoingDomainPair(domainPair))
            {
                // Do nothing since the domain has already been authenticated.
                log.debug( "Authentication successful (domain was already authenticated in the pre-existing session)." );
                diagnosticLog.add("DEBUG: Authentication successful (domain was already authenticated in the pre-existing session).");
                //inform all listeners as well.
                ServerSessionEventDispatcher.dispatchEvent(session, ServerSessionEventDispatcher.EventType.session_created);
                return DomainAuthResult.success(diagnosticLog);
            }
            if (session != null && !session.isUsingServerDialback() )
            {
                log.debug( "Dialback was not used for '{}'. This session cannot be re-used.", domainPair );
                diagnosticLog.add("DEBUG: " + fmt("Dialback was not used for '{}'. This session cannot be re-used.", domainPair));
                session = null;
            }

            if (session == null)
            {
                log.debug( "There are no pre-existing outgoing sessions to the remote domain itself. Searching for pre-existing outgoing sessions to super- or subdomains of the remote domain (if one exists, it might be re-usable) ..." );
                diagnosticLog.add("DEBUG: There are no pre-existing outgoing sessions to the remote domain itself. Searching for pre-existing outgoing sessions to super- or subdomains of the remote domain (if one exists, it might be re-usable) ...");

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
                            diagnosticLog.add("DEBUG: " + fmt("An outgoing session to a different domain ('{}') hosted on the remote domain was found.", otherRemoteDomain));

                            // OF-2425: a detached session has no connection on which to authenticate another domain, and keeps only the domain pairs that it has (decision 7).
                            if ( session.isDetached() )
                            {
                                log.debug( "The session for '{}' is detached, and waits to be resumed. It cannot be used to authenticate another domain pair, and is not re-used.", otherRemoteDomain );
                                diagnosticLog.add("DEBUG: " + fmt("The session for '{}' is detached, and waits to be resumed. It cannot be used to authenticate another domain pair, and is not re-used.", otherRemoteDomain));
                                session = null;
                            }
                            // OF-2425: the remote server did not accept this domain pair again on the connection of that session (decision 2).
                            else if ( session instanceof LocalOutgoingServerSession localSession && localSession.isGivenUp(domainPair) )
                            {
                                log.debug( "The session for '{}' gave up domain pair {}, as it was not verified again after its stream was resumed. It is not re-used.", otherRemoteDomain, domainPair );
                                diagnosticLog.add("DEBUG: " + fmt("The session for '{}' gave up domain pair {}, as it was not verified again after its stream was resumed. It is not re-used.", otherRemoteDomain, domainPair));
                                session = null;
                            }
                            // As this sub/superdomain is different from the original remote domain, we need to check if it supports dialback.
                            else if ( session.isUsingServerDialback() )
                            {
                                log.debug( "Dialback was used for '{}'. This session can be re-used.", otherRemoteDomain );
                                diagnosticLog.add("DEBUG: " + fmt("Dialback was used for '{}'. This session can be re-used.", otherRemoteDomain));
                                break;
                            }
                            else
                            {
                                log.debug( "Dialback was not used for '{}'. This session cannot be re-used.", otherRemoteDomain );
                                diagnosticLog.add("DEBUG: " + fmt("Dialback was not used for '{}'. This session cannot be re-used.", otherRemoteDomain));
                                session = null;
                            }
                        }
                    }
                }

                if (session == null) {
                    log.debug( "There are no pre-existing session to other domains hosted on the remote domain." );
                    diagnosticLog.add("DEBUG: There are no pre-existing session to other domains hosted on the remote domain.");
                }
            }

            if ( session != null )
            {
                log.debug( "A pre-existing session can be re-used. The session was established using server dialback so it is possible to do piggybacking to authenticate more domains." );
                diagnosticLog.add("DEBUG: A pre-existing session can be re-used. The session was established using server dialback so it is possible to do piggybacking to authenticate more domains.");
                if ( session.checkOutgoingDomainPair(domainPair) )
                {
                    // Do nothing since the domain has already been authenticated.
                    log.debug( "Authentication successful (domain was already authenticated in the pre-existing session)." );
                    diagnosticLog.add("DEBUG: Authentication successful (domain was already authenticated in the pre-existing session).");
                    return DomainAuthResult.success(diagnosticLog);
                }

                // A session already exists so authenticate the domain using that session.
                if ( session.authenticateSubdomain(domainPair) )
                {
                    log.debug( "Authentication successful (domain authentication was added using a pre-existing session)." );
                    diagnosticLog.add("DEBUG: Authentication successful (domain authentication was added using a pre-existing session).");
                    return DomainAuthResult.success(diagnosticLog);
                }
                else
                {
                    log.warn( "Unable to authenticate: Unable to add authentication to pre-exising session." );
                    diagnosticLog.add("WARN: Unable to authenticate: Unable to add authentication to pre-exising session.");
                    return DomainAuthResult.failure(diagnosticLog);
                }
            }
            else
            {
                try {
                    log.debug("Unable to re-use an existing session. Creating a new session ...");
                    diagnosticLog.add("DEBUG: Unable to re-use an existing session. Creating a new session ...");
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
        this.socketReader = socketReader;
        this.connectionStreamID = streamID;
        socketReader.setSession(this);
    }

    public LocalOutgoingServerSession(String localDomain, Connection connection, StreamID streamID) {
        super(localDomain, connection, streamID);
        this.connectionStreamID = streamID;
    }

    @Override
    boolean canDeliver(@Nonnull final Packet stanza) {
        final DomainPair domainPair = new DomainPair(stanza.getFrom().getDomain(), stanza.getTo().getDomain());
        boolean processed = true;
        synchronized (remoteAuthMutex.intern(new JID(null, domainPair.getRemote(), null))) {
            if (!checkOutgoingDomainPair(domainPair) && !authenticateSubdomain(domainPair)) {
                // Return error since sender domain was not validated by remote server
                processed = false;
            }
        }
        if (!processed) {
            returnErrorToSenderAsync(stanza);
        }
        return processed;
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

    @Override
    public boolean authenticateSubdomain(@Nonnull final DomainPair domainPair) {
        if (!isUsingServerDialback()) {
            /*
             * We cannot do this reliably; but this code should be unreachable.
             */
            return false;
        }
        final Connection connection = getConnection();
        if (connection == null || connection.isClosed()) {
            Log.debug("Unable to authenticate domain pair {} on session {}, as the session has no open connection.", domainPair, this);
            return false;
        }
        ServerDialback method = new ServerDialback(connection, domainPair);
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

    /**
     * Sets the session to detached mode, indicating that the underlying connection has been closed. From then on,
     * stanzas that are delivered to the session are queued rather than sent. The routes of the session are kept.
     *
     * The time that the session is kept while detached is measured from this moment, unless the session was resumed
     * before, and the remote server did not acknowledge anything on the resumed stream (see
     * {@link #getDetachedSince()}).
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

    @Override
    protected void beforeDetached() {
        // OF-2425: Set before the session is registered as detached, as the cleanup task measures from it (decision 7).
        if (detachedSince == null || getStreamManager().isAcknowledgedSinceResumption()) {
            detachedSince = Instant.now();
        } else {
            Log.debug("Session '{}' ({}) is detached again before its resumed stream was acknowledged. It is kept while detached for as long as when it was detached at {}.", address, streamID, detachedSince);
        }
    }

    /**
     * Returns the moment from which the time that this session is kept while it is detached is measured, which also
     * limits the attempts to resume its stream (see {@link SessionManager#getDetachTimeout(LocalSession)}).
     *
     * This is the moment at which the session was most recently detached, unless it was resumed before that, and the
     * remote server did not acknowledge anything on the resumed stream before it broke again (see
     * {@link RequestingStreamManager#isAcknowledgedSinceResumption()}). It is then the moment at which it was detached
     * before it was resumed, so that a stream that is resumed and breaks again before it works is not kept for longer
     * than one detach timeout in total.
     *
     * @return the moment from which the time that this session is detached is measured, or null if it was never detached.
     */
    public Instant getDetachedSince()
    {
        return detachedSince;
    }

    /**
     * Starts an attempt to resume the stream of this session, which is detached, on a new connection, on another thread
     * (see {@link #resumeOnNewConnection()}).
     */
    public void startResumption()
    {
        Log.debug("Starting an attempt to resume the stream of detached session {} on a new connection.", this);
        failedResumptionAttempts = 0;
        resumptionExecutor.execute(this::resumeOnNewConnection);
    }

    /**
     * The outcome of an attempt to resume the stream of a detached session on a new connection.
     */
    private enum ResumptionOutcome {
        /**
         * The remote server answered the request to resume the stream, and the processing of that answer takes care of
         * the session.
         */
        ANSWERED,

        /**
         * The attempt failed in a way that another attempt may not, such as a connection that could not be opened or
         * authenticated, or a request that the remote server did not answer in time.
         */
        TRY_AGAIN,

        /**
         * The attempt failed in a way that another attempt would too, such as a remote domain that may not be accessed,
         * or a remote server that does not offer the version of Stream Management of the stream.
         */
        GIVE_UP
    }

    /**
     * Attempts to resume the stream of this session, which is detached, on a new connection (XEP-0198 § 5).
     *
     * A new connection is opened from this cluster node to the remote domain of this session, and the domain pair of the
     * local and remote domain for which this session was created is authenticated on it, as for a new session. The
     * remote server is then asked to resume the stream of this session, instead of enabling Stream Management (see
     * {@link RequestingStreamManager#requestResume(LocalOutgoingServerSession, Collection)}). The session of the new
     * connection is not made a route, and no event is dispatched for it: when the stream is resumed, the connection is
     * attached to this session (see {@link #resumeWithConnectionOf(LocalOutgoingServerSession, long)}).
     *
     * When no connection can be opened or authenticated, or the remote server does not answer within
     * {@link #STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT}, the new connection is closed, and another attempt is made after a
     * delay that doubles after each failed attempt (see {@link #scheduleNextResumptionAttempt()}). When the remote domain
     * is not accessible, or the remote server does not offer the version of Stream Management of this stream, the new
     * connection is closed, and this session is ended, as when it is not resumed in time (see
     * {@link SessionManager#terminateDetached(LocalSession)}). When the remote server answers, the processing of that
     * answer takes care of this session: {@code <failed/>} ends it, without another attempt. Before the new connection
     * is closed after a failed attempt, the request to resume the stream on it is given up (see
     * {@link NettySessionInitializer#abandonResume()}), so that an answer that arrives later is ignored. An attempt is
     * not made for a session that is no longer detached.
     */
    @VisibleForTesting
    void resumeOnNewConnection()
    {
        if (!isDetached()) {
            Log.debug("Not attempting to resume session {}, as it is no longer detached: it was resumed or ended.", this);
            return;
        }
        final DomainPair domainPair = getMainDomainPair();
        NettySessionInitializer initialiser = null;
        ResumptionOutcome outcome = ResumptionOutcome.TRY_AGAIN;
        try {
            if (!RemoteServerManager.canAccess(domainPair.getRemote())) {
                Log.debug("Not resuming detached session {}, as remote domain '{}' is not accessible according to the configuration.", this, domainPair.getRemote());
                outcome = ResumptionOutcome.GIVE_UP;
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
            outcome = awaitResumption(connectionProvider);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.debug("Interrupted while resuming detached session {} on a new connection for {}.", this, domainPair, e);
            outcome = ResumptionOutcome.GIVE_UP;
        } catch (Exception e) {
            Log.debug("Unable to resume detached session {} on a new connection for {}.", this, domainPair, e);
        } finally {
            // OF-2425: Given up before the connection is closed or another attempt is made, so that a late answer on this connection is ignored.
            if (outcome != ResumptionOutcome.ANSWERED && initialiser != null && !initialiser.abandonResume()) {
                Log.debug("The remote server answered the request to resume detached session {} after the attempt failed (outcome: {}). The processing of that answer takes care of the session.", this, outcome);
                outcome = ResumptionOutcome.ANSWERED;
            }
            if (outcome != ResumptionOutcome.ANSWERED) {
                if (initialiser != null) {
                    initialiser.stop();
                }
                if (outcome != ResumptionOutcome.TRY_AGAIN || !scheduleNextResumptionAttempt()) {
                    Log.debug("Ending detached session {}, as its stream was not resumed on a new connection (outcome: {}, failed attempts: {}).", this, outcome, failedResumptionAttempts);
                    sessionManager.terminateDetached(this);
                }
            }
        }
    }

    /**
     * Schedules another attempt to resume the stream of this session after an attempt failed, unless that attempt would
     * start after the session is to be ended: once the detach timeout of this session (see
     * {@link SessionManager#getDetachTimeout(LocalSession)}) has passed since {@link #getDetachedSince()}. The first
     * attempt is made after {@link #RESUMPTION_RETRY_INITIAL_DELAY}, and the delay doubles after each failed attempt, up
     * to {@link #RESUMPTION_RETRY_MAXIMUM_DELAY}. When the detach timeout is negative, attempts are made until the
     * session is resumed or ended otherwise.
     *
     * @return true if another attempt is scheduled, false if the caller is to end this session.
     */
    private boolean scheduleNextResumptionAttempt()
    {
        final int failed = ++failedResumptionAttempts;
        final Duration delay = RESUMPTION_RETRY_INITIAL_DELAY.multipliedBy(1L << Math.min(failed - 1, 30));
        final Duration nextDelay = delay.compareTo(RESUMPTION_RETRY_MAXIMUM_DELAY) > 0 ? RESUMPTION_RETRY_MAXIMUM_DELAY : delay;
        final Duration timeout = sessionManager.getDetachTimeout(this);
        final Instant since = detachedSince;
        if (!timeout.isNegative() && since != null && !Instant.now().plus(nextDelay).isBefore(since.plus(timeout))) {
            Log.debug("Not attempting again to resume detached session {} after {} failed attempt(s), as the next attempt, in {}, would start after the session is to be ended: {} after {}.", this, failed, nextDelay, timeout, since);
            return false;
        }
        Log.debug("Attempting again to resume detached session {} in {}, after {} failed attempt(s).", this, nextDelay, failed);
        resumptionRetryScheduler.accept(this::resumeOnNewConnection, nextDelay);
        return true;
    }

    /**
     * Waits for the remote server to answer the request to resume the stream of this session on the connection of
     * another session, and gives up on that request when it is not answered in time, or not sent.
     *
     * @param connectionProvider the session of the new connection.
     * @return {@link ResumptionOutcome#ANSWERED} when the remote server answered the request, in which case the processing
     * of that answer takes care of this session; otherwise, the request was given up: {@link ResumptionOutcome#GIVE_UP}
     * when it was not sent, as the stream cannot be resumed on the new connection, or {@link ResumptionOutcome#TRY_AGAIN}
     * when it was not answered in time, or the connection closed first.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    @Nonnull
    private ResumptionOutcome awaitResumption(@Nonnull final LocalOutgoingServerSession connectionProvider) throws InterruptedException
    {
        final RequestingStreamManager streamManager = connectionProvider.getStreamManager();
        // OF-2425: A connection that this session took over already is no longer the connection of the provider.
        final Connection connection = connectionProvider.getConnection();
        final CompletableFuture<Void> closed = connection == null ? CompletableFuture.completedFuture(null) : connection.getCloseFuture().toCompletableFuture();
        final Duration timeout = STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT.getValue();
        try {
            CompletableFuture.anyOf(streamManager.getResumeRequestFinished(), closed).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.debug("The remote server did not answer the request to resume the stream of session {} on the connection of session {} within {} (request status: {}).", this, connectionProvider, timeout, streamManager.getResumeRequestStatus());
        } catch (ExecutionException e) {
            // Only the close future can complete exceptionally. That also means that the connection closed.
            Log.trace("The close future of the connection of session {} completed exceptionally.", connectionProvider, e);
        }
        // OF-2425: A request that was not sent, while the handler finished with it, cannot be sent on another connection either.
        final boolean notSent = streamManager.getResumeRequestFinished().isDone() && streamManager.getResumeRequestStatus() == RequestingStreamManager.ResumeRequestStatus.NOT_SENT;
        if (!streamManager.abandonResume()) {
            return ResumptionOutcome.ANSWERED;
        }
        return notSent ? ResumptionOutcome.GIVE_UP : ResumptionOutcome.TRY_AGAIN;
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
     * Only the domain pair for which this session was created is authenticated on the new connection. The stanzas of its
     * other domain pairs are held until each is verified again with dialback on that connection (decision 2 of OF-2425,
     * see {@link #verifyAgain(DomainPair)}).
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
        final List<DomainPair> otherDomainPairs = getOtherDomainPairs();
        // OF-2425: held before the stanzas are sent on the new connection, on which only the main domain pair is authenticated (decision 2).
        getStreamManager().holdUntilVerified(otherDomainPairs);
        connectionStreamID = connectionProvider.getConnectionStreamID();
        reattach(connectionProvider, h);
        sessionManager.outgoingServerSessionCreated(this);
        for (final DomainPair domainPair : otherDomainPairs) {
            verifyAgain(domainPair);
        }
        return true;
    }

    /**
     * Returns the domain pair for which this session was created: its local domain and the domain of the remote server,
     * which is authenticated on every connection of this session.
     *
     * @return the main domain pair of this session.
     */
    @Nonnull
    private DomainPair getMainDomainPair()
    {
        return new DomainPair(getServerName(), getAddress().getDomain());
    }

    /**
     * Returns the domain pairs of this session other than the one for which it was created (see
     * {@link #getMainDomainPair()}), which were authenticated on a connection of this session with dialback.
     *
     * @return the other domain pairs of this session.
     */
    @Nonnull
    private List<DomainPair> getOtherDomainPairs()
    {
        final DomainPair main = getMainDomainPair();
        return outgoingDomainPairs.stream().filter(domainPair -> !domainPair.equals(main)).toList();
    }

    /**
     * Returns the stream ID of the stream header that the remote server sent on the current connection of this session.
     * A dialback key for a domain pair that is authenticated on that connection is generated with it (XEP-0185).
     *
     * This is the stream ID of this session ({@link #getStreamID()}), which identifies the session, also in log
     * messages. When the stream of this session is resumed on the connection of another session, it is the stream ID of
     * the stream header on that connection.
     *
     * @return the stream ID of the stream header on the current connection (never null).
     */
    @Nonnull
    public StreamID getConnectionStreamID()
    {
        return connectionStreamID;
    }

    /**
     * Verifies a domain pair of this session again with dialback, on the connection on which its stream is resumed,
     * after which its held stanzas are sent (see {@link RequestingStreamManager#releaseVerified}). The dialback key is
     * generated with the stream ID of the stream header on that connection ({@link #getConnectionStreamID()}), with which
     * the remote server verifies it (XEP-0185, XEP-0220 § 2.1.1). The answer of the remote server is passed to
     * {@link #processDialbackResult(Element)}.
     *
     * When the remote server refuses the domain pair, or it cannot be verified, as the connection is not authenticated
     * with dialback or dialback is disabled, the domain pair is given up: it is removed from this session, with its
     * route, and its held stanzas are handled as when this session closes without being resumed (see
     * {@link #giveUp(DomainPair, RequestingStreamManager.VerificationAttempt, boolean)}), so that its other stanzas go
     * out over a new connection. When the remote server does not answer within the socket timeout
     * ({@link RemoteServerManager#getSocketTimeout()}), or the connection closes first, the domain pair is given up only
     * while this session still uses that connection: otherwise its stanzas stay held, to be verified again when the
     * stream is resumed next, or handled when this session ends.
     *
     * @param domainPair the domain pair to verify again.
     */
    private void verifyAgain(@Nonnull final DomainPair domainPair)
    {
        final Connection connection = getConnection();
        if (connection == null || connection.isClosed()) {
            Log.debug("Not verifying domain pair {} of resumed session {} again now, as the session has no open connection. Its stanzas stay held.", domainPair, this);
            return;
        }
        final RequestingStreamManager.VerificationAttempt attempt = getStreamManager().startVerification(domainPair, connection);
        if (attempt == null) {
            Log.debug("Not verifying domain pair {} of resumed session {} again, as its stanzas are not held.", domainPair, this);
            return;
        }
        if (!isUsingServerDialback() || (!ServerDialback.isEnabled() && !ServerDialback.isEnabledForSelfSigned())) {
            Log.debug("Unable to verify domain pair {} of resumed session {} again, as its connection is not authenticated with dialback (method: {}), or dialback is disabled.", domainPair, this, getAuthenticationMethod());
            verificationExecutor.execute(() -> giveUp(domainPair, attempt, true));
            return;
        }

        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        dialbackResults.put(domainPair, result);
        connection.getCloseFuture().thenRun(() -> result.completeExceptionally(new IllegalStateException("The connection closed.")));
        final String streamId = connectionStreamID.getID();
        Log.debug("Verifying domain pair {} of resumed session {} again with dialback, using the stream ID '{}' of the connection.", domainPair, this, streamId);
        new ServerDialback(connection, domainPair).createAndSendDialbackKey(streamId);
        result.orTimeout(RemoteServerManager.getSocketTimeout(), TimeUnit.MILLISECONDS).whenCompleteAsync((valid, failure) -> {
            if (Boolean.TRUE.equals(valid)) {
                getStreamManager().releaseVerified(domainPair, attempt, new JID(null, getServerName(), null, true));
            } else {
                Log.debug("Domain pair {} of resumed session {} was not verified again: {}", domainPair, this, failure == null ? "refused by the remote server" : failure.toString());
                giveUp(domainPair, attempt, failure == null);
            }
        }, verificationExecutor);
    }

    /**
     * Gives up a domain pair of this session that was not verified again after its stream was resumed: the domain pair
     * is removed from this session, with its route, and its held stanzas are handled as when this session closes
     * without being resumed (see {@link RequestingStreamManager#giveUpUnverified}). The mutex with which stanzas for the
     * domain pair are routed (see {@link OutgoingSessionPromise#getMutex(DomainPair)}) is held until its held stanzas are
     * routed again, so that a stanza for the domain pair that is routed meanwhile follows them, but not while errors are
     * returned to the senders of stanzas.
     *
     * @param domainPair the domain pair that was not verified.
     * @param attempt the attempt that did not verify it.
     * @param refused whether the remote server refused the domain pair, or it cannot be verified on the connection.
     */
    private void giveUp(@Nonnull final DomainPair domainPair, @Nonnull final RequestingStreamManager.VerificationAttempt attempt, final boolean refused)
    {
        getStreamManager().giveUpUnverified(domainPair, attempt, refused, () -> {
            givenUpDomainPairs.add(domainPair);
            outgoingDomainPairs.remove(domainPair);
            XMPPServer.getInstance().getRoutingTable().removeServerRoute(domainPair);
        }, OutgoingSessionPromise.getInstance().getMutex(domainPair), XMPPServer.getInstance().getPacketRouter(), new JID(null, getServerName(), null, true));
    }

    /**
     * Checks whether this session gave up a domain pair, as it was not verified again after the stream of this session
     * was resumed. Such a domain pair gets a connection of its own.
     *
     * @param domainPair the domain pair.
     * @return true if this session gave up the domain pair.
     */
    public boolean isGivenUp(@Nonnull final DomainPair domainPair)
    {
        return givenUpDomainPairs.contains(domainPair);
    }

    /**
     * Processes an answer of the remote server to a dialback request with which this session verifies a domain pair
     * again after its stream was resumed (XEP-0220 § 2.1.1): a {@code <db:result/>} of which the 'from' attribute is
     * the remote domain and the 'to' attribute the local domain of that domain pair. The domain pair is verified when
     * its type is 'valid', and refused otherwise.
     *
     * @param result the {@code <db:result/>} that the remote server sent.
     * @return true if the element answers such a request, false if it is to be processed otherwise.
     */
    public boolean processDialbackResult(@Nonnull final Element result)
    {
        final String from = result.attributeValue("from");
        final String to = result.attributeValue("to");
        if (from == null || to == null) {
            return false;
        }
        final DomainPair domainPair = new DomainPair(to, from);
        final CompletableFuture<Boolean> outcome = dialbackResults.get(domainPair);
        if (outcome == null) {
            return false;
        }
        final String type = result.attributeValue("type");
        Log.debug("The remote server answered the dialback request for domain pair {} of resumed session {} with type '{}'.", domainPair, this, type);
        outcome.complete("valid".equals(type));
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
