/*
 * Copyright (C) 2018-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.net;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.InetAddresses;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Dns;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Dns.Target;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp.AddressFailure;
import org.jivesoftware.openfire.server.diagnostics.AttemptRecorder;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.ScheduledExecutorCompletionService;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Utility class to generate Socket instances.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class SocketUtil
{
    private final static Logger Log = LoggerFactory.getLogger( SocketUtil.class );

    /**
     * A fixed delay for how long to wait before starting the next connection attempt, as defined in section 5 of
     * RFC 8305 "Happy Eyeballs Version 2: Better Connectivity Using Concurrency".
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc8305#section-5">RFC 8305, section 5</a>
     */
    public static final SystemProperty<Duration> CONNECTION_ATTEMPT_DELAY = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.server.connection-attempt-delay")
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofMillis(250))
        .setMinValue(Duration.ofMillis(100))
        .setMaxValue(Duration.ofSeconds(2))
        .setDynamic(true)
        .build();

    /**
     * The time to wait for a response for the 'preferred IP family' after receiving a response for another family, as
     * defined in section 3 of RFC 8305 "Happy Eyeballs Version 2: Better Connectivity Using Concurrency".
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc8305#section-5">RFC 8305, section 5</a>
     */
    public static final SystemProperty<Duration> RESOLUTION_DELAY = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.server.resolution-delay")
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofMillis(50))
        .setMinValue(Duration.ofMillis(0))
        .setDynamic(true)
        .build();

    /**
     * The maximum amount of time to wait for successful resolution of a host of a target domain.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc8305#section-5">RFC 8305, section 5</a>
     */
    public static final SystemProperty<Duration> RESOLUTION_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.server.resolution-timeout")
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofMinutes(5))
        .setMinValue(Duration.ofMillis(0))
        .setDynamic(true)
        .build();

    /**
     * The maximum amount of worker threads attempting to set up a socket connection to a target remote XMPP domain. A
     * value of '1' will effectively make 'Happy Eyeballs' impossible (as that requires concurrent connection attempts).
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc8305#section-5">RFC 8305, section 5</a>
     */
    public static final SystemProperty<Integer> MAX_CONNECTION_CONCURRENCY = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.server.connection-max-workers")
        .setDefaultValue(8)
        .setMinValue(1)
        .setDynamic(true)
        .build();

    /**
     * Creates a socket connection to an XMPP domain.
     *
     * This implementation uses DNS SRV records to find a list of remote hosts for the XMPP domain (as implemented by
     * {@link DNSUtil#resolveXMPPDomain(String, int)}). It then iteratively tries to create a socket connection to each
     * of them, until one socket connection succeeds.
     *
     * Either the connected Socket instance is returned, or null if no connection could be established.
     *
     * Note that this method blocks while performing network IO. The timeout as defined by
     * {@link RemoteServerManager#getSocketTimeout()} is observed.
     *
     * @param xmppDomain The XMPP domain to connect to.
     * @param port The port to connect to when DNS resolution fails.
     * @return a Socket instance that is connected, or null.
     * @see DNSUtil#resolveXMPPDomain(String, int)
     */
    public static Map.Entry<Socket, Boolean> createSocketToXmppDomain( String xmppDomain, int port )
    {
        return createSocketToXmppDomain( xmppDomain, port, RESOLUTION_TIMEOUT.getValue() );
    }

    /**
     * Creates a socket connection to an XMPP domain, as {@link #createSocketToXmppDomain(String, int)} does, spending at
     * most the provided time on resolving its hosts and connecting to them.
     *
     * @param xmppDomain The XMPP domain to connect to.
     * @param port The port to connect to when DNS resolution fails.
     * @param timeout The maximum time to spend resolving and connecting.
     * @return a Socket instance that is connected, or null.
     */
    public static Map.Entry<Socket, Boolean> createSocketToXmppDomain( @Nonnull final String xmppDomain, final int port, @Nonnull final Duration timeout )
    {
        return createSocketToXmppDomain( xmppDomain, port, timeout, null );
    }

    /**
     * Creates a socket connection to an XMPP domain, as {@link #createSocketToXmppDomain(String, int, Duration)} does,
     * recording what DNS returned and what happened with the connection attempts in the given recorder.
     *
     * @param xmppDomain The XMPP domain to connect to.
     * @param port The port to connect to when DNS resolution fails.
     * @param timeout The maximum time to spend resolving and connecting.
     * @param recorder Records the DNS and TCP milestones of the attempt; may be null.
     * @return a Socket instance that is connected, or null.
     */
    public static Map.Entry<Socket, Boolean> createSocketToXmppDomain( @Nonnull final String xmppDomain, final int port, @Nonnull final Duration timeout, @Nullable final AttemptRecorder recorder )
    {
        Log.debug( "Creating a socket connection to XMPP domain '{}' (timeout: {}) ...", xmppDomain, timeout );

        final boolean overridden = recorder != null && AttemptRecorder.safely(() -> DNSUtil.hasOverride(xmppDomain), false);
        final Map.Entry<Socket, Boolean> result = createSocket( xmppDomain, () -> {
            Log.debug( "Use DNS to resolve remote hosts for the provided XMPP domain '{}' (default port: {}) ...", xmppDomain, port );
            return DNSUtil.resolveXMPPDomain(xmppDomain, port);
        }, timeout, address -> true, recorder, overridden );
        if ( result == null ) {
            Log.warn( "Unable to create a socket connection to XMPP domain '{}': Unable to connect to any of its remote hosts.", xmppDomain );
            if (recorder != null) {
                AttemptRecorder.safely(recorder::loggedWarning);
            }
        }
        return result;
    }

    /**
     * A host and optional port, as named by the 'location' attribute of a Stream Management {@code <enabled/>} element
     * (XEP-0198 § 3), in the form that RFC 6120 § 4.9.3.19 defines.
     *
     * @param host the domain name or IP address, without the square brackets that enclose an IPv6 address.
     * @param port the port, or null when none is given.
     */
    @VisibleForTesting
    record Location(@Nonnull String host, @Nullable Integer port) {}

    /**
     * Parses a location that a peer named for reconnecting to it (XEP-0198 § 3): a domain name or IP address, optionally
     * followed by a colon and a port, in which an IPv6 address is enclosed in square brackets (RFC 6120 § 4.9.3.19).
     *
     * @param location the value of the 'location' attribute.
     * @return the location, or null when it is not in that form.
     */
    @VisibleForTesting
    @Nullable
    static Location parseLocation( @Nonnull final String location )
    {
        final String value = location.trim();
        final String host;
        final String portValue;
        if ( value.startsWith( "[" ) ) {
            final int end = value.indexOf( ']' );
            if ( end < 0 ) {
                return null;
            }
            host = value.substring( 1, end );
            if ( !host.contains( ":" ) || !InetAddresses.isInetAddress( host ) ) {
                return null;
            }
            final String rest = value.substring( end + 1 );
            if ( !rest.isEmpty() && !rest.startsWith( ":" ) ) {
                return null;
            }
            portValue = rest.isEmpty() ? null : rest.substring( 1 );
        } else {
            final int colon = value.indexOf( ':' );
            if ( colon != value.lastIndexOf( ':' ) ) {
                return null; // An IPv6 address that is not enclosed in square brackets.
            }
            host = colon < 0 ? value : value.substring( 0, colon );
            portValue = colon < 0 ? null : value.substring( colon + 1 );
            try {
                if ( host.isEmpty() ) {
                    return null;
                }
                JID.domainprep( host );
            } catch ( IllegalArgumentException e ) {
                return null;
            }
        }

        Integer port = null;
        if ( portValue != null ) {
            try {
                port = Integer.parseInt( portValue );
            } catch ( NumberFormatException e ) {
                return null;
            }
            if ( port < 1 || port > 65535 ) {
                return null;
            }
        }
        return new Location( host, port );
    }

    /**
     * Creates a socket connection to the location that the peer of a stream with an XMPP domain named for reconnecting to
     * it, in the 'location' attribute of a Stream Management {@code <enabled/>} element (XEP-0198 § 3). The location is a
     * domain name or IP address, optionally followed by a port, in the form that RFC 6120 § 4.9.3.19 defines.
     *
     * A location with a port, or an IP address without one, is connected to at that address, on the provided default
     * port when none is given. A domain name without a port is resolved as an XMPP domain (RFC 6120 § 3.2, see
     * {@link DNSUtil#resolveXMPPDomain(String, int)}). A domain name to which a DNS override applies is connected to at
     * the host that the override names, also when the location names a port.
     *
     * The location is not used when a DNS override applies to the XMPP domain (see {@link DNSUtil#DNS_OVERRIDE}), as
     * that override then decides where the XMPP domain is reached.
     *
     * A connection to the location is an alternative to the connection that {@link #createSocketToXmppDomain(String, int)}
     * creates, and has to meet the same requirements: the stream that is negotiated on it is addressed to the XMPP
     * domain, and the identity of the peer is verified against that domain, not against the location.
     *
     * Only the addresses that the location resolves to and that the provided filter accepts are connected to (see
     * {@link #locationAddressFilter(InetAddress)}), so that a peer cannot make Openfire connect to an address that it
     * should not, such as a loopback address.
     *
     * Note that this method blocks while performing network IO, for at most the provided timeout.
     *
     * @param xmppDomain The XMPP domain of the peer that named the location.
     * @param location The location, as named by the peer.
     * @param defaultPort The port to connect to when the location names none.
     * @param timeout The maximum time to spend resolving the location and connecting to it.
     * @param addressFilter Accepts the addresses that the location resolves to that may be connected to.
     * @return a connected socket and whether it uses direct TLS, or null when the location is not valid, a DNS override
     *         applies to the XMPP domain, or no connection could be established within the timeout to an address that
     *         the filter accepts.
     */
    @Nullable
    public static Map.Entry<Socket, Boolean> createSocketToLocation( @Nonnull final String xmppDomain, @Nonnull final String location, final int defaultPort, @Nonnull final Duration timeout, @Nonnull final Predicate<InetAddress> addressFilter )
    {
        final Location parsed = parseLocation( location );
        if ( parsed == null ) {
            Log.debug( "Not connecting to location '{}' of XMPP domain '{}', as it is not a domain name or IP address, optionally followed by a port.", location, xmppDomain );
            return null;
        }
        if ( DNSUtil.hasOverride( xmppDomain ) ) {
            Log.debug( "Not connecting to location '{}' of XMPP domain '{}', as a DNS override applies to that domain.", location, xmppDomain );
            return null;
        }

        // OF-2425: A DNS override for the host of the location applies also when the location names a port.
        final boolean resolveAsXmppDomain = !InetAddresses.isInetAddress( parsed.host() ) && ( parsed.port() == null || DNSUtil.hasOverride( parsed.host() ) );
        Log.debug( "Creating a socket connection to location '{}' of XMPP domain '{}' (resolved as an XMPP domain: {}, timeout: {}) ...", location, xmppDomain, resolveAsXmppDomain, timeout );
        return createSocket( xmppDomain + " (location: " + location + ")", () -> resolveAsXmppDomain
            ? DNSUtil.resolveXMPPDomain( parsed.host(), parsed.port() == null ? defaultPort : parsed.port() )
            : List.of( Set.of( new SrvRecord( parsed.host(), parsed.port() == null ? defaultPort : parsed.port(), false ) ) ), timeout, addressFilter, null, false );
    }

    /**
     * Returns the filter for the addresses that a location that a peer named for reconnecting to it may be connected to
     * (see {@link #createSocketToLocation(String, String, int, Duration, Predicate)}). A peer names that location, so it
     * could otherwise make Openfire connect to an address of the host of Openfire, or of its own network. The filter
     * does not know the addresses of the host of Openfire: one that is not of a kind that is refused, such as a public
     * address of that host, is accepted.
     *
     * Loopback, link-local, unspecified and multicast addresses are never accepted. Private addresses (the IPv4 ranges
     * of RFC 1918 and IPv6 unique local addresses, RFC 4193) are accepted only when the original connection to the peer,
     * on which it named the location, was made to a private address too, as between servers on the same private
     * network. Other addresses are accepted.
     *
     * @param addressOfOriginalConnection The address to which the original connection to the peer was made, or null
     *                                    when it is not known.
     * @return the filter (never null).
     */
    @Nonnull
    public static Predicate<InetAddress> locationAddressFilter( @Nullable final InetAddress addressOfOriginalConnection )
    {
        final boolean privateAllowed = addressOfOriginalConnection != null && isPrivateAddress( addressOfOriginalConnection );
        return address -> {
            if ( address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress() || address.isMulticastAddress() ) {
                return false;
            }
            return privateAllowed || !isPrivateAddress( address );
        };
    }

    /**
     * Checks whether an address is a private address: in one of the IPv4 ranges of RFC 1918 (10.0.0.0/8, 172.16.0.0/12
     * and 192.168.0.0/16), or an IPv6 unique local address (fc00::/7, RFC 4193), or a deprecated IPv6 site-local
     * address (fec0::/10).
     *
     * @param address The address to check.
     * @return true if the address is private.
     */
    static boolean isPrivateAddress( @Nonnull final InetAddress address )
    {
        if ( address.isSiteLocalAddress() ) {
            return true;
        }
        return address instanceof Inet6Address && ( address.getAddress()[0] & 0xfe ) == 0xfc;
    }

    /**
     * Creates a socket connection to one of the hosts that the provided resolver returns, trying them in the order and
     * with the concurrency of RFC 8305 "Happy Eyeballs Version 2".
     *
     * @param xmppDomain A description of what is connected to, for logging.
     * @param hostResolver Resolves the hosts to connect to, grouped by priority. Called on another thread.
     * @param timeout The maximum time to spend resolving and connecting.
     * @param addressFilter Accepts the resolved addresses that may be connected to.
     * @param recorder Records the DNS and TCP milestones of the attempt; may be null.
     * @param overridden Whether the resolver answers from a DNS override configured on this server; read only with a
     *                   recorder.
     * @return a connected socket and whether it uses direct TLS, or null if no connection could be established.
     */
    @Nullable
    private static Map.Entry<Socket, Boolean> createSocket( @Nonnull final String xmppDomain, @Nonnull final Supplier<List<Set<SrvRecord>>> hostResolver, @Nonnull final Duration timeout, @Nonnull final Predicate<InetAddress> addressFilter, @Nullable final AttemptRecorder recorder, final boolean overridden )
    {
        final AtomicBoolean anyAddressResolved = new AtomicBoolean(false);
        final List<ConnectionAttempt> failedAttempts = new CopyOnWriteArrayList<>();
        final Set<SocketChannel> fallbackConnections = ConcurrentHashMap.newKeySet();
        final Map<SocketChannel, Integer> connectedPrioritySets = new ConcurrentHashMap<>();

        final Instant deadline = Instant.now().plus(timeout);
        final List<Future<Map.Entry<SocketChannel, Boolean>>> futures = new CopyOnWriteArrayList<>();
        final BlockingQueue<Future<Map.Entry<SocketChannel, Boolean>>> resolvedHostsQueue = new LinkedBlockingQueue<>();
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(MAX_CONNECTION_CONCURRENCY.getValue());
        executor.setRemoveOnCancelPolicy(true);
        final ScheduledExecutorCompletionService<Map.Entry<SocketChannel, Boolean>> completionService = new ScheduledExecutorCompletionService<>(executor, resolvedHostsQueue);

        final Thread r = new Thread(() -> {
            Instant nextJobNotBefore = Instant.EPOCH;

            final List<Set<SrvRecord>> remoteHosts = hostResolver.get();
            if (recorder != null) {
                AttemptRecorder.safely(() -> recorder.dns(classifyDns(remoteHosts, overridden)));
            }
            for (int index = 0; index < remoteHosts.size(); index++) {
                final Set<SrvRecord> prioritySet = remoteHosts.get(index);
                // Only for the recorder: which priority set an address is in. The sets are tried one after the other, and
                // the addresses within a set alongside each other, so only a failure in an earlier set delayed a
                // connection.
                final int prioritySetIndex = index;
                // Only for the recorder: a connection to the domain name itself, although SRV records are published,
                // means that no server they name could be reached.
                final boolean fallbackDespiteSrvRecords = recorder != null && AttemptRecorder.safely(() -> isFallbackDespiteSrvRecords(remoteHosts, overridden, prioritySet), false);
                if (executor.isTerminating() || executor.isTerminated() || executor.isShutdown()) {
                    Log.trace("Aborting resolution of '{}', as the executor is being shut down (likely cause: we successfully identified a result).", xmppDomain);
                    return;
                }
                if (!Instant.now().isBefore(deadline)) {
                    Log.debug("Aborting resolution of '{}', as it has been taking longer than the maximum amount of time.", xmppDomain);
                    return;
                }
                final boolean preferIpv4 = InetAddress.getLoopbackAddress() instanceof Inet4Address; // Follow the preference of the JVM.
                final HappyEyeballsResolver resolver = new HappyEyeballsResolver(new LinkedList<>(prioritySet), preferIpv4, RESOLUTION_DELAY.getValue());
                try {
                    resolver.start();
                    while (!resolver.isExhausted() && !executor.isTerminating() && !executor.isTerminated() && !executor.isShutdown() && Instant.now().isBefore(deadline)) {
                        final ResolvedServiceAddress resolvedAddress = resolver.getNext(); // Blocks.
                        Log.trace("Next resolved address for '{}': {}", xmppDomain, resolvedAddress);
                        if (resolvedAddress == null) {
                            continue;
                        }
                        anyAddressResolved.set(true);

                        if (!addressFilter.test(resolvedAddress.getInetAddress())) {
                            Log.debug("Skipping address {} of '{}', which may not be connected to.", resolvedAddress, xmppDomain);
                            continue;
                        }

                        if (!JiveGlobals.getBooleanProperty(ConnectionSettings.Server.ENABLE_OLD_SSLPORT, true) && resolvedAddress.isDirectTLS()) {
                            Log.debug("Skipping directTLS address, as we're ourselves not accepting directTLS S2S: {}", resolvedAddress);
                            continue;
                        }

                        if (!JiveGlobals.getBooleanProperty(ConnectionSettings.Server.SOCKET_ACTIVE, true) && !resolvedAddress.isDirectTLS()) {
                            Log.debug("Skipping non directTLS address, as we're ourselves not accepting non direct S2S: {}", resolvedAddress);
                            continue;
                        }

                        final Callable<Map.Entry<SocketChannel, Boolean>> callable = () -> {
                            final int socketTimeout = RemoteServerManager.getSocketTimeout();
                            SocketChannel socketChannel = null;
                            try {
                                socketChannel = SocketChannel.open();

                                Log.debug("Trying to create socket connection to XMPP domain '{}' using resolved address: {}...", xmppDomain, resolvedAddress);
                                socketChannel.configureBlocking(true);
                                socketChannel.socket().connect(resolvedAddress.generateSocketAddress(), socketTimeout);

                                Log.debug("Successfully created socket connection to XMPP domain '{}' using resolved address: {}!", xmppDomain, resolvedAddress);
                                if (fallbackDespiteSrvRecords) {
                                    fallbackConnections.add(socketChannel);
                                }
                                if (recorder != null) {
                                    connectedPrioritySets.put(socketChannel, prioritySetIndex);
                                }

                                return new AbstractMap.SimpleEntry<>(socketChannel, resolvedAddress.isDirectTLS());
                            } catch (Throwable e) {
                                if (e instanceof java.nio.channels.ClosedByInterruptException) {
                                    Log.debug("Socket connection establishment to XMPP domain '{}' using resolved address {} got interrupted. Likely, another connection already succeeded, making this one redundant.", xmppDomain, resolvedAddress);
                                } else {
                                    Log.debug("An exception occurred while trying to create a socket connection to XMPP domain '{}' using resolved address {}", xmppDomain, resolvedAddress, e);
                                }
                                try {
                                    if (socketChannel != null) {
                                        socketChannel.close();
                                    }
                                } catch (IOException ex) {
                                    Log.debug("An additional exception occurred while trying to close a socket when creating a connection to resolved address {} failed.", resolvedAddress, ex);
                                }
                                // Recorded after the socket is closed, and guarded, so that recording cannot leak it.
                                AttemptRecorder.safely(() -> {
                                    final ConnectionAttempt failure = classifyFailure(endpoint(resolvedAddress.generateSocketAddress()), e);
                                    if (failure != null) {
                                        failedAttempts.add(failure.inPrioritySet(prioritySetIndex));
                                    }
                                });
                            }
                            return null;
                        };

                        final Duration delay;
                        if (Instant.now().isBefore(nextJobNotBefore)) {
                            final Duration delta = Duration.between(Instant.now(), nextJobNotBefore);
                            if (delta.isNegative()) {
                                delay = Duration.ZERO;
                            } else {
                                delay = delta;
                            }
                        } else {
                            delay = Duration.ZERO;
                        }

                        Log.trace("Scheduling connection attempt for '{}' to {} after a delay of {}", xmppDomain, resolvedAddress, delay);
                        try {
                            futures.add(completionService.schedule(callable, delay));
                        } catch (RejectedExecutionException e) {
                            // Likely reason: the executor is shutting down because a successful connection was established.
                            Log.debug("Unable to schedule a connection attempt (for '{}' to {} after a delay of {}). Likely cause: teardown of the attempt, because another connection has already been successful", xmppDomain, resolvedAddress, delay, e);
                        }

                        nextJobNotBefore = Instant.now().plus(delay).plus(CONNECTION_ATTEMPT_DELAY.getValue());
                    }

                    Log.trace("Wait for all connection attempts to have finished, before moving to the next priority set.");
                    for (Future<Map.Entry<SocketChannel, Boolean>> entryFuture : futures) {
                        final Duration maxWait = Duration.between(Instant.now(), deadline);
                        if (maxWait.isNegative()) {
                            break;
                        }
                        entryFuture.get(maxWait.toMillis(), TimeUnit.MILLISECONDS);
                    }
                    Log.trace("Done iterating over a priority set for '{}'", xmppDomain);
                } catch (CancellationException e) {
                    Log.debug("DNS resolution for '{}' got cancelled. Stopping...", xmppDomain);
                } catch (InterruptedException e) {
                    Log.debug("DNS resolution for '{}' got interrupted. Stopping...", xmppDomain);
                } catch (Throwable e) {
                    Log.warn("Unexpected exception while setting up a connection to {}", xmppDomain, e);
                    if (recorder != null) {
                        AttemptRecorder.safely(recorder::loggedWarning);
                    }
                } finally {
                    resolver.shutdown();
                }
            }
            Log.trace("Done iterating over all priority sets for '{}'", xmppDomain);
        }, "happy-eyeball-resolving-" + xmppDomain);
        r.start();

        Map.Entry<SocketChannel, Boolean> result = null;
        try {
            while (result == null && r.isAlive()) {
                try {
                    final Duration maxWait = Duration.between(Instant.now(), deadline);
                    if (maxWait.isNegative()) {
                        break;
                    }
                    final long wait = Math.min(maxWait.toMillis(), Duration.ofSeconds(2).toMillis()); // OF-3068: Periodically check if it continues to make sense to wait for the completionService to yield a result.
                    final Future<Map.Entry<SocketChannel, Boolean>> poll = completionService.poll(wait, TimeUnit.MILLISECONDS);
                    result = poll == null ? null : poll.get();
                } catch (ExecutionException e) {
                    Log.debug("Resolution of XMPP domain '{}' threw an exception (that is being ignored).", xmppDomain, e);
                }
            }
        } catch (InterruptedException e) {
            Log.debug("Resolution of XMPP domain '{}' got interrupted. Aborting...", xmppDomain, e);
            // OF-2425: Keep the interrupt, so that the caller does not start another attempt to connect.
            Thread.currentThread().interrupt();
        } finally {
            Log.debug("Finished resolving XMPP domain '{}'", xmppDomain);
            futures.forEach(future -> future.cancel(true));
            executor.shutdownNow();
        }

        r.interrupt();
        if (recorder != null) {
            final Map.Entry<SocketChannel, Boolean> connected = result;
            AttemptRecorder.safely(() -> {
                if (connected != null) {
                    recorder.tcp(summariseConnection(endpoint((InetSocketAddress) connected.getKey().socket().getRemoteSocketAddress()), connectedPrioritySets.getOrDefault(connected.getKey(), 0),
                        fallbackConnections.contains(connected.getKey()), failedAttempts));
                } else if (!anyAddressResolved.get()) {
                    // Nothing to connect to: the failure is DNS, and no TCP connection was attempted.
                    recorder.dns(Dns.noAddress());
                } else {
                    recorder.tcp(summariseConnectionAttempts(failedAttempts));
                }
            });
        }
        if (result == null) {
            return null;
        } else {
            Log.debug("Successfully created a socket connection to XMPP domain '{}', using: {} ({})", xmppDomain, result.getKey().socket().getRemoteSocketAddress(), result.getValue() ? "directTLS" : "not directTLS" );
            return new AbstractMap.SimpleEntry<>(result.getKey().socket(), result.getValue());
        }
    }

    /**
     * A connection attempt to one resolved address that did not succeed.
     *
     * @param endpoint the address and port, as {@link #endpoint(InetSocketAddress)} formats them.
     * @param outcome how it failed: refused, timed out, or failed otherwise.
     * @param reason the error text of another failure; otherwise null.
     * @param prioritySet the index, in the order DNS resolution returned them, of the priority set the address is in.
     */
    record ConnectionAttempt(String endpoint, Tcp.Outcome outcome, String reason, int prioritySet)
    {
        /**
         * A failed attempt to an address in the first priority set.
         *
         * @param endpoint the address and port.
         * @param outcome how it failed.
         * @param reason the error text of another failure; otherwise null.
         */
        ConnectionAttempt(final String endpoint, final Tcp.Outcome outcome, final String reason)
        {
            this(endpoint, outcome, reason, 0);
        }

        /**
         * @param prioritySet the index of the priority set the address is in.
         * @return this attempt, in that priority set.
         */
        ConnectionAttempt inPrioritySet(final int prioritySet)
        {
            return new ConnectionAttempt(endpoint, outcome, reason, prioritySet);
        }
    }

    /**
     * Describes what DNS returned, for the S2S connection test.
     *
     * {@link DNSUtil#resolveXMPPDomain(String, int)} always appends the domain itself on the default port as a
     * fallback (with the lowest possible priority), so the fallback is reported only when there were no SRV records. A
     * connection that reaches the fallback although there were SRV records is reported on the TCP milestone instead
     * (see {@link #summariseConnection}).
     * The targets are capped at {@link AttemptMilestones#MAX_LISTED_VALUES}, as the remote decides how many there are.
     *
     * @param remoteHosts the hosts DNS resolution returned, by priority.
     * @param overridden whether the hosts came from a DNS override configured on this server.
     * @return the DNS milestone.
     */
    static Dns classifyDns(final List<Set<SrvRecord>> remoteHosts, final boolean overridden)
    {
        final List<SrvRecord> all = remoteHosts.stream().flatMap(Set::stream).toList();
        if (overridden) {
            return Dns.override(all.stream().map(SocketUtil::target).toList());
        }
        final List<Target> srv = all.stream().filter(record -> record.getPriority() != Integer.MAX_VALUE).map(SocketUtil::target).toList();
        if (!srv.isEmpty()) {
            return Dns.srvRecords(srv);
        }
        return Dns.fallbackOnly(all.stream().map(SocketUtil::target).toList());
    }

    private static Target target(final SrvRecord record)
    {
        return new Target(record.getHostname() + ':' + record.getPort(), record.isDirectTLS());
    }

    /**
     * Classifies why a connection attempt to one address failed.
     *
     * @param endpoint the address and port.
     * @param e what the attempt threw.
     * @return the failed attempt, or null when the attempt was interrupted because another address connected first.
     */
    static ConnectionAttempt classifyFailure(final String endpoint, final Throwable e)
    {
        if (e instanceof ClosedByInterruptException) {
            return null;
        }
        if (e instanceof SocketTimeoutException) {
            return new ConnectionAttempt(endpoint, Tcp.Outcome.TIMED_OUT, null);
        }
        if (e instanceof ConnectException) {
            // The operating system can give up on an unanswered connection before the socket timeout does. Java then
            // reports it with the same type as a refusal, and only the message tells the two apart.
            final String message = e.getMessage();
            final boolean timedOut = message != null && message.toLowerCase(Locale.ROOT).contains("timed out");
            return new ConnectionAttempt(endpoint, timedOut ? Tcp.Outcome.TIMED_OUT : Tcp.Outcome.REFUSED, null);
        }
        return new ConnectionAttempt(endpoint, Tcp.Outcome.FAILED, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    /**
     * Summarises the failed connection attempts to every resolved address as one TCP milestone: refused or timed out
     * when every address failed that way, otherwise a failure, with what happened at each address. The failures are
     * capped at {@link AttemptMilestones#MAX_LISTED_VALUES}, with the number of the rest.
     *
     * @param failedAttempts the failed attempts, in the order they were made.
     * @return the TCP milestone.
     */
    static Tcp summariseConnectionAttempts(final List<ConnectionAttempt> failedAttempts)
    {
        if (failedAttempts.isEmpty()) {
            return Tcp.noneAttempted();
        }
        return Tcp.allFailed(failures(failedAttempts));
    }

    /**
     * Records a connection that was made, with the addresses of earlier priority sets that failed before it, so that a
     * dead address is not hidden by a working one. A connection to the domain name itself, although the domain publishes
     * SRV records, is recorded as such, as it means that no server the records name could be reached.
     *
     * The priority sets are tried one after the other, and this server moves to the next set only when every address
     * in a set has failed, so a failure in an earlier set delayed the connection. The addresses within a set are tried
     * alongside each other, so a failure beside the address that connected (such as an IPv6 address that this server
     * cannot reach, while the IPv4 address of the same server connects) delayed nothing, and is left out.
     *
     * @param endpoint the address and port connected to, as {@link #endpoint(InetSocketAddress)} formats them.
     * @param connectedPrioritySet the index of the priority set of the address connected to.
     * @param fallbackDespiteSrvRecords whether that address is the domain name itself, tried after the SRV records.
     * @param failedAttempts the addresses that failed, in the order they were tried.
     * @return the TCP milestone.
     */
    static Tcp summariseConnection(final String endpoint, final int connectedPrioritySet, final boolean fallbackDespiteSrvRecords, final List<ConnectionAttempt> failedAttempts)
    {
        final List<ConnectionAttempt> earlier = failedAttempts.stream().filter(attempt -> attempt.prioritySet() < connectedPrioritySet).toList();
        if (fallbackDespiteSrvRecords) {
            return Tcp.connectedToFallback(endpoint, failures(earlier));
        }
        return earlier.isEmpty() ? Tcp.connected(endpoint) : Tcp.connectedAfterFailures(endpoint, failures(earlier));
    }

    /**
     * Whether a priority set is the domain name itself, which {@link DNSUtil#resolveXMPPDomain(String, int)} appends
     * last (with the lowest possible priority), tried although the domain publishes SRV records.
     *
     * @param remoteHosts the hosts DNS resolution returned, by priority.
     * @param overridden whether the hosts came from a DNS override configured on this server.
     * @param prioritySet one of the priority sets.
     * @return true when the set is that fallback and SRV records were published.
     */
    static boolean isFallbackDespiteSrvRecords(final List<Set<SrvRecord>> remoteHosts, final boolean overridden, final Set<SrvRecord> prioritySet)
    {
        return !overridden
            && !prioritySet.isEmpty()
            && prioritySet.stream().allMatch(record -> record.getPriority() == Integer.MAX_VALUE)
            && remoteHosts.stream().flatMap(Set::stream).anyMatch(record -> record.getPriority() != Integer.MAX_VALUE);
    }

    private static List<AddressFailure> failures(final List<ConnectionAttempt> failedAttempts)
    {
        return failedAttempts.stream().map(attempt -> new AddressFailure(attempt.endpoint(), attempt.outcome(), attempt.reason())).toList();
    }

    /**
     * Formats an address and port for reading: the numeric address (IPv6 in brackets) followed by the port.
     *
     * @param address the address.
     * @return the formatted address and port.
     */
    static String endpoint(final InetSocketAddress address)
    {
        if (address.getAddress() == null) {
            return address.getHostString() + ':' + address.getPort();
        }
        final String host = address.getAddress().getHostAddress();
        return (address.getAddress() instanceof Inet6Address ? '[' + host + ']' : host) + ':' + address.getPort();
    }
}
