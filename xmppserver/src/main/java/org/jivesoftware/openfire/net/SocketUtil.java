/*
 * Copyright (C) 2018-2024 Ignite Realtime Foundation. All rights reserved.
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

    Socket solve(ScheduledThreadPoolExecutor e, List<Callable<Socket>> solvers, Duration delay) throws InterruptedException
    {
        final ScheduledExecutorCompletionService<Socket> cs = new ScheduledExecutorCompletionService<>(e);
        final int n = solvers.size();
        final List<Future<Socket>> futures = new ArrayList<>(n);
        Socket result = null;
        try {
            solvers.forEach(solver -> futures.add(cs.schedule(solver, delay)));
            for (int i = n; i > 0; i--) {
                try {
                    final Socket r = cs.take().get();
                    if (r != null) {
                        result = r;
                        break;
                    }
                } catch (ExecutionException ignore) {
                }
            }
        } finally {
            futures.forEach(future -> future.cancel(true));
        }
        return result;
    }

    /**
     * Creates a socket connection to an XMPP domain.
     *
     * This implementation uses DNS SRV records to find a list of remote hosts for the XMPP domain (as implemented by
     * {@link DNSUtil#resolveXMPPDomain(String, int)}. It then iteratively tries to create a socket connection to each
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
        final List<Future<Map.Entry<Socket, Boolean>>> futures = new ArrayList<>();
        final BlockingQueue<Future<Map.Entry<Socket, Boolean>>> resolvedHostsQueue = new LinkedBlockingQueue<>();
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(8); // TODO make configurable
        executor.setRemoveOnCancelPolicy(true);
        final ScheduledExecutorCompletionService<Map.Entry<Socket, Boolean>> completionService = new ScheduledExecutorCompletionService<>(executor, resolvedHostsQueue);

        final Thread r = new Thread(() -> {
            Instant nextJobNotBefore = Instant.EPOCH;

            Log.debug( "Use DNS to resolve remote hosts for the provided XMPP domain '{}' (default port: {}) ...", xmppDomain, port );
            final List<Set<DNSUtil.WeightedHostAddress>> remoteHosts = DNSUtil.resolveXMPPDomain(xmppDomain, port);
            for (final Set<DNSUtil.WeightedHostAddress> prioritySet : remoteHosts) {
                if (executor.isTerminating() || executor.isTerminated() || executor.isShutdown()) {
                    Log.trace("Aborting resolution, as the executor is being shut down (likely cause: we successfully identified a result).");
                    return;
                }
                final boolean preferIpv4 = InetAddress.getLoopbackAddress() instanceof Inet4Address; // Follow the preference of the JVM.
                final HappyEyeballsResolver resolver = new HappyEyeballsResolver(new LinkedList<>(prioritySet), preferIpv4, RESOLUTION_DELAY.getValue());
                try {
                    resolver.start();
                    while (!resolver.isDone() && !executor.isTerminating() && !executor.isTerminated() && !executor.isShutdown() && Instant.now().isBefore(deadline)) {
                        final HappyEyeballsResolver.XmppServiceAddress resolvedAddress = resolver.getNext(); // Blocks.
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

                        final Callable<Map.Entry<Socket, Boolean>> callable = () -> {
                            final int socketTimeout = RemoteServerManager.getSocketTimeout();
//                            SocketChannel socketChannel = null;
                            Socket socket = null; // TODO migrate to SocketChannel so that a connection attempt can be interrupted (when another one was already successful).
                            try {
//                                socketChannel = SocketChannel.open();
//                                socketChannel.connect(resolvedAddress.getSocketAddress())

                                // (re)initialize the socket.
                                socket = new Socket();

                                Log.debug("Trying to create socket connection to XMPP domain '{}' using remote address: {} (blocks up to {} ms) ...", xmppDomain, resolvedAddress.getSocketAddress(), socketTimeout);
                                socket.connect(resolvedAddress.getSocketAddress(), socketTimeout);
                                Log.debug("Successfully created socket connection to XMPP domain '{}' using remote address: {}!", xmppDomain, resolvedAddress.getSocketAddress());

                                return new AbstractMap.SimpleEntry<>(socket, resolvedAddress.isDirectTLS());
                            } catch (Exception e) {
                                Log.debug("An exception occurred while trying to create a socket connection to XMPP domain '{}' using remote address {}", xmppDomain, resolvedAddress.getSocketAddress(), e);
                                Log.warn("Unable to create a socket connection to XMPP domain '{}' using remote address: {}. Cause: {} (a full stacktrace is logged on debug level)", xmppDomain, resolvedAddress.getSocketAddress(), e.getMessage());
                                try {
                                    if (socket != null) {
                                        socket.close();
                                    }
                                } catch (IOException ex) {
                                    Log.debug("An additional exception occurred while trying to close a socket when creating a connection to {} failed.", resolvedAddress.getSocketAddress(), ex);
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
                    Log.trace("Done iterating over a priority set for '{}'", xmppDomain);
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

        Map.Entry<Socket, Boolean> result = null;
        try {
            while (result == null && r.isAlive() && Instant.now().isBefore(deadline)) {
                try {
                    result = completionService.poll(10, TimeUnit.SECONDS).get();
                } catch (ExecutionException e) {
                    Log.debug("Resolution of XMPP domain '{}' threw an exception (that is being ignored).", xmppDomain, e);
                }
            }
        } catch (InterruptedException e) {
            Log.debug("Resolution of XMPP domain '{}' got interrupted. Aborting...", xmppDomain, e);
        } finally {
            Log.debug("Finished resolving XMPP domain '{}'", xmppDomain);
            futures.forEach(future -> future.cancel(true));
            executor.shutdown();
        }

        r.interrupt();
        if (result == null) {
            Log.warn( "Unable to create a socket connection to XMPP domain '{}': Unable to connect to any of its remote hosts.", xmppDomain );
        } else {
            Log.debug("Successfully created a socket connection to XMPP domain '{}', using: {} ({})", xmppDomain, result.getKey().getRemoteSocketAddress(), result.getValue() ? "directTLS" : "not directTLS" );
        }
        return result;
    }
}
