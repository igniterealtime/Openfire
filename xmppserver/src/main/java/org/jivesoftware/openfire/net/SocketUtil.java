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
        return createSocketToXmppDomain( xmppDomain, port, null );
    }

    /**
     * Creates a socket connection to an XMPP domain, as {@link #createSocketToXmppDomain(String, int)}, recording what
     * DNS returned and what happened with the connection attempts in the given recorder.
     *
     * @param xmppDomain The XMPP domain to connect to.
     * @param port The port to connect to when DNS resolution fails.
     * @param recorder Records the DNS and TCP milestones of the attempt; may be null.
     * @return a Socket instance that is connected, or null.
     */
    public static Map.Entry<Socket, Boolean> createSocketToXmppDomain( String xmppDomain, int port, @Nullable AttemptRecorder recorder )
    {
        Log.debug( "Creating a socket connection to XMPP domain '{}' ...", xmppDomain );
        final AtomicBoolean anyAddressResolved = new AtomicBoolean(false);
        final List<ConnectionAttempt> failedAttempts = new CopyOnWriteArrayList<>();
        // Only for the recorder: attempts that have started and neither connected nor failed. Those still here when no
        // address connected were cancelled by the resolution deadline.
        final Set<ConnectionAttempt> attemptsUnderWay = ConcurrentHashMap.newKeySet();
        final Set<SocketChannel> fallbackConnections = ConcurrentHashMap.newKeySet();
        final Map<SocketChannel, Integer> connectedPrioritySets = new ConcurrentHashMap<>();

        final Instant deadline = Instant.now().plus(RESOLUTION_TIMEOUT.getValue());
        final List<Future<Map.Entry<SocketChannel, Boolean>>> futures = new CopyOnWriteArrayList<>();
        final BlockingQueue<Future<Map.Entry<SocketChannel, Boolean>>> resolvedHostsQueue = new LinkedBlockingQueue<>();
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(MAX_CONNECTION_CONCURRENCY.getValue());
        executor.setRemoveOnCancelPolicy(true);
        final ScheduledExecutorCompletionService<Map.Entry<SocketChannel, Boolean>> completionService = new ScheduledExecutorCompletionService<>(executor, resolvedHostsQueue);

        final Thread r = new Thread(() -> {
            Instant nextJobNotBefore = Instant.EPOCH;

            Log.debug( "Use DNS to resolve remote hosts for the provided XMPP domain '{}' (default port: {}) ...", xmppDomain, port );
            final List<Set<SrvRecord>> remoteHosts = DNSUtil.resolveXMPPDomain(xmppDomain, port);
            final boolean overridden = recorder != null && AttemptRecorder.safely(() -> DNSUtil.hasDnsOverride(xmppDomain), false);
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
                            final ConnectionAttempt underWay = recorder == null ? null : AttemptRecorder.safely(() -> ConnectionAttempt.cutOff(endpoint(resolvedAddress.generateSocketAddress()), prioritySetIndex), null);
                            if (underWay != null) {
                                attemptsUnderWay.add(underWay);
                            }
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
                                if (underWay != null) {
                                    attemptsUnderWay.remove(underWay);
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
                                    // An interrupted attempt stays under way: it was cancelled, either because another
                                    // address connected (when it does not count) or by the deadline (when it timed out).
                                    if (failure != null) {
                                        failedAttempts.add(failure.inPrioritySet(prioritySetIndex));
                                        if (underWay != null) {
                                            attemptsUnderWay.remove(underWay);
                                        }
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
                    recorder.tcp(summariseConnectionAttempts(failedAttempts, attemptsUnderWay));
                }
            });
        }
        if (result == null) {
            Log.warn( "Unable to create a socket connection to XMPP domain '{}': Unable to connect to any of its remote hosts.", xmppDomain );
            if (recorder != null) {
                AttemptRecorder.safely(recorder::loggedWarning);
            }
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

        /**
         * An attempt that had started when the resolution deadline cancelled it: it did not connect in time.
         *
         * @param endpoint the address and port.
         * @param prioritySet the index of the priority set the address is in.
         * @return the attempt, as timed out.
         */
        static ConnectionAttempt cutOff(final String endpoint, final int prioritySet)
        {
            return new ConnectionAttempt(endpoint, Tcp.Outcome.TIMED_OUT, null, prioritySet);
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
        return summariseConnectionAttempts(failedAttempts, List.of());
    }

    /**
     * Summarises the connection attempts as {@link #summariseConnectionAttempts(List)} does, also counting the attempts
     * that were still under way when the resolution deadline cancelled them, as timed out. Without them, a deadline
     * shorter than the socket timeout would leave no failure recorded, which reads as no connection attempted.
     *
     * @param failedAttempts the failed attempts, in the order they were made.
     * @param cutOffAttempts the attempts that the deadline cancelled after they started.
     * @return the TCP milestone.
     */
    static Tcp summariseConnectionAttempts(final List<ConnectionAttempt> failedAttempts, final Collection<ConnectionAttempt> cutOffAttempts)
    {
        final List<ConnectionAttempt> all = new ArrayList<>(failedAttempts);
        all.addAll(cutOffAttempts);
        if (all.isEmpty()) {
            return Tcp.noneAttempted();
        }
        return Tcp.allFailed(failures(all));
    }

    /**
     * Describes a connection that failed after the address was chosen, such as the connection that the session uses,
     * which is made again after the probe connection that chose the address.
     *
     * @param address the address and port connected to.
     * @param cause what the connection attempt threw.
     * @return the TCP milestone, or null when the attempt was interrupted rather than failed.
     */
    public static Tcp connectionFailed(final InetSocketAddress address, final Throwable cause)
    {
        final ConnectionAttempt failure = classifyFailure(endpoint(address), cause);
        return failure == null ? null : summariseConnectionAttempts(List.of(failure));
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
