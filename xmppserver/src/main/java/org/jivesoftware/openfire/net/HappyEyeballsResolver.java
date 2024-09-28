package org.jivesoftware.openfire.net;

import net.jcip.annotations.GuardedBy;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class HappyEyeballsResolver
{
    private final Duration resolutionDelay;

    private final ThreadPoolExecutor executor;

    private final List<DNSUtil.HostAddress> hostAddresses;

    @GuardedBy("this")
    private final PriorityQueue<IndexedInetAddress> resolvedHosts;

    @GuardedBy("this")
    private final ConcurrentMap<Integer, Integer> resultCountByIndex = new ConcurrentHashMap<>();

    @GuardedBy("this")
    private int preferredNextIndex = 0;

    @GuardedBy("this")
    private boolean preferredNextFamilyIsIpv4;

    public HappyEyeballsResolver(final List<DNSUtil.HostAddress> hostAddresses)
    {
        this(hostAddresses, false, Duration.ofMillis(50));
    }

    public HappyEyeballsResolver(final List<DNSUtil.HostAddress> hostAddresses, final boolean preferIpv4, final Duration resolutionDelay)
    {
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(hostAddresses.size());
        this.hostAddresses = hostAddresses;
        this.resolvedHosts = new PriorityQueue<>(hostAddresses.size()*3, Comparator.comparing(IndexedInetAddress::getIndex));
        this.preferredNextFamilyIsIpv4 = preferIpv4;
        this.resolutionDelay = resolutionDelay;
    }

    public Duration getResolutionDelay()
    {
        return resolutionDelay;
    }

    public synchronized void start() throws ExecutionException, InterruptedException
    {
        for (int i = 0; i < hostAddresses.size(); i++)
        {
            int index = i;
            // Happy Eyeballs dictates that first an AAAA and only then A query is sent out for each host. The Java API
            // that we're using doesn't give us granular control like that: it's requesting both at the same time.
            final Supplier<Set<IndexedInetAddress>> solve = () -> {
                final DNSUtil.HostAddress hostAddress = hostAddresses.get(index);
                try {
                    return IndexedInetAddress.from(index, InetAddress.getAllByName(hostAddress.getHost()), hostAddress.getPort(), hostAddress.isDirectTLS());
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            };
            solve(solve, index);
        }
    }

    // Exposed for unit testing.
    void solve(final Supplier<Set<IndexedInetAddress>> supplier, final int index) {
        CompletableFuture.supplyAsync(supplier, executor)
            .exceptionally(t -> { addException(t, index); return null; })
            .thenAccept(results -> addResults(results, index));
    }

    public synchronized boolean isDone() {
        return executor.getCompletedTaskCount() == hostAddresses.size();
    }

    public void shutdown() {
        if (!isDone()) {
            // Happy Eyeballs tells us to keep resolving for a while, to populate caches.
            new Thread(() -> {
                try {
                    Thread.sleep(Duration.ofSeconds(1).toMillis());
                    executor.shutdown();
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                }
            }).start();
        } else {
            executor.shutdown();
        }
    }

    public void shutdownNow() {
        executor.shutdownNow();
    }

    synchronized private void addResults(final Set<IndexedInetAddress> results, final int index)
    {
        resolvedHosts.addAll(results);
        resultCountByIndex.merge(index, results.size(), Integer::sum);
        notifyAll();
    }

    synchronized private void addException(final Throwable t, final int index)
    {
        notifyAll(); // prevents threads waiting forever, after a lookup fails.
    }

    synchronized private XmppServiceAddress getPreferredImmediately()
    {
        final Iterator<IndexedInetAddress> iterator = resolvedHosts.iterator();
        while (iterator.hasNext()) {
            final IndexedInetAddress resolvedAddress = iterator.next();
            if (resolvedAddress.getIndex() == preferredNextIndex && (resolvedAddress.isIPv6() != preferredNextFamilyIsIpv4)) {
                iterator.remove();

                final int remainingCount = resultCountByIndex.merge(resolvedAddress.getIndex(), -1, Integer::sum);
                assert remainingCount >= 0 : "After completing a test, the amount of remaining tasks cannot be negative (but was for index '"+resolvedAddress.getIndex()+"': " + remainingCount + ").";
                if (remainingCount == 0) {
                    do {
                        preferredNextIndex++;
                    }
                    // If this value was set and is now 0, then all of its addresses have already been used up. Move up the next index!
                    while (resultCountByIndex.containsKey(preferredNextIndex) && resultCountByIndex.get(preferredNextIndex) == 0);
                }
                preferredNextFamilyIsIpv4 = resolvedAddress.isIPv6();
                return XmppServiceAddress.from(resolvedAddress);
            }
        }
        return null;
    }

    synchronized private XmppServiceAddress getAlternativeImmediately()
    {
        IndexedInetAddress result = null;
        final Iterator<IndexedInetAddress> iterator = resolvedHosts.iterator(); // iterates over index order.
        while (iterator.hasNext()) {
            final IndexedInetAddress resolvedAddress = iterator.next();
            // Preferably, use the index.
            if (resolvedAddress.getIndex() == preferredNextIndex) {
                result = resolvedAddress;
                iterator.remove();
                break;
            }

            // If there's not a host for this index yet, prefer the first host for the preferred family.
            if (resolvedAddress.isIPv6() != preferredNextFamilyIsIpv4) {
                result = resolvedAddress;
                iterator.remove();
            }
        }

        // If after iterating over all currently available hosts, there's not a host for the preferred family yet,
        // prefer the first host by index.
        if (result == null && !resolvedHosts.isEmpty()) {
            result = resolvedHosts.poll();
        }

        if (result != null) {
            preferredNextFamilyIsIpv4 = result.isIPv6();
            resultCountByIndex.put(result.getIndex(), resultCountByIndex.get(result.getIndex()) - 1);
            // If this value was set and is now 0, then all of its addresses have already been used up. Move up the next index!
            while (resultCountByIndex.containsKey(preferredNextIndex) && resultCountByIndex.get(preferredNextIndex) == 0) {
                preferredNextIndex++;
            }
            return XmppServiceAddress.from(result);
        }

        return null;
    }

    public synchronized XmppServiceAddress getNext() throws InterruptedException
    {
        final Instant deadline = Instant.now().plus(resolutionDelay);

        XmppServiceAddress result;
        while ((result = getPreferredImmediately()) == null) {
            final Instant now = Instant.now();
            final long sleepTime = Duration.between(now, deadline).toMillis();
            if (sleepTime <= 0) {
                break;
            }
            wait(sleepTime);
        }

        if (result == null) {
            result = getAlternativeImmediately();
        }

        return result;
    }

    public static class XmppServiceAddress
    {
        private final InetSocketAddress socketAddress;
        private final boolean isDirectTLS;

        static XmppServiceAddress from(final IndexedInetAddress address)
        {
            return new XmppServiceAddress(new InetSocketAddress(address.getInetAddress(), address.getPort()), address.isDirectTLS());
        }

        public XmppServiceAddress(final InetSocketAddress socketAddress, final boolean isDirectTLS)
        {
            this.socketAddress = socketAddress;
            this.isDirectTLS = isDirectTLS;
        }

        public InetSocketAddress getSocketAddress()
        {
            return socketAddress;
        }

        public boolean isDirectTLS()
        {
            return isDirectTLS;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            XmppServiceAddress that = (XmppServiceAddress) o;
            return isDirectTLS == that.isDirectTLS && Objects.equals(socketAddress, that.socketAddress);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(socketAddress, isDirectTLS);
        }

        @Override
        public String toString()
        {
            return "XmppServiceAddress{" +
                "socketAddress=" + socketAddress +
                ", isDirectTLS=" + isDirectTLS +
                '}';
        }
    }

    static class IndexedInetAddress {
        final int index;
        final InetAddress inetAddress;
        final int port;
        final boolean isDirectTLS;

        IndexedInetAddress(final int index, final InetAddress inetAddress, final int port, final boolean isDirectTLS)
        {
            this.index = index;
            this.inetAddress = inetAddress;
            this.port = port;
            this.isDirectTLS = isDirectTLS;
        }

        public static Set<IndexedInetAddress> from(final int index, final InetAddress[] addresses, final int port, final boolean isDirectTLS)
        {
            final Set<IndexedInetAddress> result = new HashSet<>();
            for (InetAddress address : addresses) {
                result.add(new IndexedInetAddress(index, address, port, isDirectTLS));
            }
            return result;
        }

        public int getIndex()
        {
            return index;
        }

        public InetAddress getInetAddress()
        {
            return inetAddress;
        }

        public int getPort()
        {
            return port;
        }

        public boolean isDirectTLS()
        {
            return isDirectTLS;
        }

        public boolean isIPv6() {
            return inetAddress instanceof Inet6Address;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IndexedInetAddress that = (IndexedInetAddress) o;
            return index == that.index && port == that.port && isDirectTLS == that.isDirectTLS && Objects.equals(inetAddress, that.inetAddress);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(index, inetAddress, port, isDirectTLS);
        }

        @Override
        public String toString()
        {
            return "IndexedInetAddress{" +
                "index=" + index +
                ", inetAddress=" + inetAddress +
                ", port=" + port +
                ", isDirectTLS=" + isDirectTLS +
                '}';
        }
    }
}
