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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
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
        Log.debug( "Creating a socket connection to XMPP domain '{}' ...", xmppDomain );

        final Map.Entry<Socket, Boolean> result = createSocket( xmppDomain, () -> {
            Log.debug( "Use DNS to resolve remote hosts for the provided XMPP domain '{}' (default port: {}) ...", xmppDomain, port );
            return DNSUtil.resolveXMPPDomain(xmppDomain, port);
        }, RESOLUTION_TIMEOUT.getValue() );
        if ( result == null ) {
            Log.warn( "Unable to create a socket connection to XMPP domain '{}': Unable to connect to any of its remote hosts.", xmppDomain );
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
     * Note that this method blocks while performing network IO, for at most the provided timeout.
     *
     * @param xmppDomain The XMPP domain of the peer that named the location.
     * @param location The location, as named by the peer.
     * @param defaultPort The port to connect to when the location names none.
     * @param timeout The maximum time to spend resolving the location and connecting to it.
     * @return a connected socket and whether it uses direct TLS, or null when the location is not valid, a DNS override
     *         applies to the XMPP domain, or no connection could be established within the timeout.
     */
    @Nullable
    public static Map.Entry<Socket, Boolean> createSocketToLocation( @Nonnull final String xmppDomain, @Nonnull final String location, final int defaultPort, @Nonnull final Duration timeout )
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
            : List.of( Set.of( new SrvRecord( parsed.host(), parsed.port() == null ? defaultPort : parsed.port(), false ) ) ), timeout );
    }

    /**
     * Creates a socket connection to one of the hosts that the provided resolver returns, trying them in the order and
     * with the concurrency of RFC 8305 "Happy Eyeballs Version 2".
     *
     * @param xmppDomain A description of what is connected to, for logging.
     * @param hostResolver Resolves the hosts to connect to, grouped by priority. Called on another thread.
     * @param timeout The maximum time to spend resolving and connecting.
     * @return a connected socket and whether it uses direct TLS, or null if no connection could be established.
     */
    @Nullable
    private static Map.Entry<Socket, Boolean> createSocket( @Nonnull final String xmppDomain, @Nonnull final Supplier<List<Set<SrvRecord>>> hostResolver, @Nonnull final Duration timeout )
    {
        final Instant deadline = Instant.now().plus(timeout);
        final List<Future<Map.Entry<SocketChannel, Boolean>>> futures = new CopyOnWriteArrayList<>();
        final BlockingQueue<Future<Map.Entry<SocketChannel, Boolean>>> resolvedHostsQueue = new LinkedBlockingQueue<>();
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(MAX_CONNECTION_CONCURRENCY.getValue());
        executor.setRemoveOnCancelPolicy(true);
        final ScheduledExecutorCompletionService<Map.Entry<SocketChannel, Boolean>> completionService = new ScheduledExecutorCompletionService<>(executor, resolvedHostsQueue);

        final Thread r = new Thread(() -> {
            Instant nextJobNotBefore = Instant.EPOCH;

            final List<Set<SrvRecord>> remoteHosts = hostResolver.get();
            for (final Set<SrvRecord> prioritySet : remoteHosts) {
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
        if (result == null) {
            return null;
        } else {
            Log.debug("Successfully created a socket connection to XMPP domain '{}', using: {} ({})", xmppDomain, result.getKey().socket().getRemoteSocketAddress(), result.getValue() ? "directTLS" : "not directTLS" );
            return new AbstractMap.SimpleEntry<>(result.getKey().socket(), result.getValue());
        }
    }
}
