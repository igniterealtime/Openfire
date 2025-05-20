/*
 * Copyright (C) 2018-2025 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.ScheduledExecutorCompletionService;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        final Instant deadline = Instant.now().plus(RESOLUTION_TIMEOUT.getValue());
        final List<Future<Map.Entry<SocketChannel, Boolean>>> futures = new ArrayList<>();
        final BlockingQueue<Future<Map.Entry<SocketChannel, Boolean>>> resolvedHostsQueue = new LinkedBlockingQueue<>();
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(MAX_CONNECTION_CONCURRENCY.getValue());
        executor.setRemoveOnCancelPolicy(true);
        final ScheduledExecutorCompletionService<Map.Entry<SocketChannel, Boolean>> completionService = new ScheduledExecutorCompletionService<>(executor, resolvedHostsQueue);

        final Thread r = new Thread(() -> {
            Instant nextJobNotBefore = Instant.EPOCH;

            Log.debug( "Use DNS to resolve remote hosts for the provided XMPP domain '{}' (default port: {}) ...", xmppDomain, port );
            final List<Set<SrvRecord>> remoteHosts = DNSUtil.resolveXMPPDomain(xmppDomain, port);
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
                    while (!resolver.isDone() && !executor.isTerminating() && !executor.isTerminated() && !executor.isShutdown() && Instant.now().isBefore(deadline)) {
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
        } finally {
            Log.debug("Finished resolving XMPP domain '{}'", xmppDomain);
            futures.forEach(future -> future.cancel(true));
            executor.shutdownNow();
        }

        r.interrupt();
        if (result == null) {
            Log.warn( "Unable to create a socket connection to XMPP domain '{}': Unable to connect to any of its remote hosts.", xmppDomain );
            return null;
        } else {
            Log.debug("Successfully created a socket connection to XMPP domain '{}', using: {} ({})", xmppDomain, result.getKey().socket().getRemoteSocketAddress(), result.getValue() ? "directTLS" : "not directTLS" );
            return new AbstractMap.SimpleEntry<>(result.getKey().socket(), result.getValue());
        }
    }
}
