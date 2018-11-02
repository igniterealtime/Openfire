package org.xmpp.jnodes.smack;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverItems;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.xmpp.jnodes.RelayChannel;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SmackServiceNode implements ConnectionListener, PacketListener {

    private final XMPPConnection connection;
    private final ConcurrentHashMap<String, RelayChannel> channels = new ConcurrentHashMap<String, RelayChannel>();
    private final Map<String, TrackerEntry> trackerEntries = Collections.synchronizedMap(new LinkedHashMap<String, TrackerEntry>());
    private long timeout = 60000;
    private final static ExecutorService executorService = Executors.newCachedThreadPool(); 
    private final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(1);
    private final AtomicInteger ids = new AtomicInteger(0);

    static {
        ProviderManager.getInstance().addIQProvider(JingleChannelIQ.NAME, JingleChannelIQ.NAMESPACE, new JingleNodesProvider());
        ProviderManager.getInstance().addIQProvider(JingleTrackerIQ.NAME, JingleTrackerIQ.NAMESPACE, new JingleTrackerProvider());
    }

    public SmackServiceNode(final XMPPConnection connection, final long timeout) {
        this.connection = connection;
        this.timeout = timeout;
        setup();
    }

    public SmackServiceNode(final String server, final int port, final long timeout) {
        final ConnectionConfiguration conf = new ConnectionConfiguration(server, port, server);
        conf.setSASLAuthenticationEnabled(false);
        conf.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
        connection = new XMPPConnection(conf);
        this.timeout = timeout;
    }

    public void connect(final String user, final String password) throws XMPPException {
        connect(user, password, false, Roster.SubscriptionMode.accept_all);
    }

    public void connect(final String user, final String password, final boolean tryCreateAccount, final Roster.SubscriptionMode mode) throws XMPPException {
        connection.connect();
        connection.addConnectionListener(this);
        if (tryCreateAccount) {
            try {
                connection.getAccountManager().createAccount(user, password);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    // Do Nothing
                }
            } catch (final XMPPException e) {
                // Do Nothing as account may exists
            }
        }
        connection.login(user, password);
        connection.getRoster().setSubscriptionMode(mode);
        setup();
    }

    private void setup() {
        scheduledExecutor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                for (final RelayChannel c : channels.values()) {
                    final long current = System.currentTimeMillis();
                    final long da = current - c.getLastReceivedTimeA();
                    final long db = current - c.getLastReceivedTimeB();

                    if (da > timeout || db > timeout) {
                        removeChannel(c);
                    }
                }
            }
        }, timeout, timeout, TimeUnit.MILLISECONDS);

        connection.addPacketListener(this, new PacketFilter() {
            public boolean accept(Packet packet) {
                return packet instanceof JingleChannelIQ || packet instanceof JingleTrackerIQ;
            }
        });
    }

    public void connectionClosed() {
        closeAllChannels();
        scheduledExecutor.shutdownNow();
    }

    private void closeAllChannels() {
        for (final RelayChannel c : channels.values()) {
            removeChannel(c);
        }
    }

    private void removeChannel(final RelayChannel c) {
        channels.remove(c.getAttachment());
        c.close();
    }

    public void connectionClosedOnError(Exception e) {
        closeAllChannels();
    }

    public void reconnectingIn(int i) {

    }

    public void reconnectionSuccessful() {

    }

    public void reconnectionFailed(Exception e) {

    }

    protected IQ createUdpChannel(final JingleChannelIQ iq) {

        try {
            final RelayChannel rc = RelayChannel.createLocalRelayChannel("0.0.0.0", 10000, 40000);
            final int id = ids.incrementAndGet();
            final String sId = String.valueOf(id);
            rc.setAttachment(sId);

            channels.put(sId, rc);

            final JingleChannelIQ result = new JingleChannelIQ();
            result.setType(IQ.Type.RESULT);
            result.setTo(iq.getFrom());
            result.setFrom(iq.getTo());
            result.setPacketID(iq.getPacketID());
            result.setHost(rc.getIp());
            result.setLocalport(rc.getPortA());
            result.setRemoteport(rc.getPortB());
            result.setId(sId);

            return result;

        } catch (IOException e) {
            e.printStackTrace();
            return JingleChannelIQ.createEmptyError();
        }

    }

    public void processPacket(final Packet packet) {

        System.out.println("Received: " + packet.toXML());
        if (packet instanceof JingleChannelIQ) {
            final JingleChannelIQ request = (JingleChannelIQ) packet;
            if (request.isRequest()) {
                connection.sendPacket(createUdpChannel(request));
            }
        } else if (packet instanceof JingleTrackerIQ) {
            final JingleTrackerIQ iq = (JingleTrackerIQ) packet;
            if (iq.isRequest()) {
                final JingleTrackerIQ result = createKnownNodes();
                result.setPacketID(packet.getPacketID());
                result.setFrom(packet.getTo());
                result.setTo(packet.getFrom());
                connection.sendPacket(result);
            }
        }

    }

    public XMPPConnection getConnection() {
        return connection;
    }

    public static JingleChannelIQ getChannel(final XMPPConnection xmppConnection, final String serviceNode) {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return null;
        }

        final JingleChannelIQ iq = new JingleChannelIQ();
        iq.setFrom(xmppConnection.getUser());
        iq.setTo(serviceNode);

        PacketCollector collector = xmppConnection.createPacketCollector(new PacketIDFilter(iq.getPacketID()));
        xmppConnection.sendPacket(iq);
        JingleChannelIQ result = (JingleChannelIQ) collector.nextResult(Math.round(SmackConfiguration.getPacketReplyTimeout() * 1.5));
        collector.cancel();

        return result;
    }

    public static JingleTrackerIQ getServices(final XMPPConnection xmppConnection, final String serviceNode) {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return null;
        }

        final JingleTrackerIQ iq = new JingleTrackerIQ();
        iq.setFrom(xmppConnection.getUser());
        iq.setTo(serviceNode);

        PacketCollector collector = xmppConnection.createPacketCollector(new PacketIDFilter(iq.getPacketID()));
        xmppConnection.sendPacket(iq);
        Packet result = collector.nextResult(Math.round(SmackConfiguration.getPacketReplyTimeout() * 1.5));
        collector.cancel();

        return result instanceof JingleTrackerIQ ? (JingleTrackerIQ) result : null;
    }

    private static void deepSearch(final XMPPConnection xmppConnection, final int maxEntries, final String startPoint, final MappedNodes mappedNodes, final int maxDepth, final int maxSearchNodes, final String protocol, final ConcurrentHashMap<String, String> visited) {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return;
        }
        if (mappedNodes.getRelayEntries().size() > maxEntries || maxDepth <= 0) {
            return;
        }
        if (startPoint.equals(xmppConnection.getUser())) {
            return;
        }
        if (visited.size() > maxSearchNodes) {
            return;
        }

        JingleTrackerIQ result = getServices(xmppConnection, startPoint);
        visited.put(startPoint, startPoint);
        if (result != null && result.getType().equals(IQ.Type.RESULT)) {
            for (final TrackerEntry entry : result.getEntries()) {
                if (entry.getType().equals(TrackerEntry.Type.tracker)) {
                    mappedNodes.getTrackerEntries().put(entry.getJid(), entry);
                    deepSearch(xmppConnection, maxEntries, entry.getJid(), mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);
                } else if (entry.getType().equals(TrackerEntry.Type.relay)) {
                    if (protocol == null || protocol.equals(entry.getProtocol())) {
                        mappedNodes.getRelayEntries().put(entry.getJid(), entry);
                    }
                }
            }
        }
    }

    public static MappedNodes aSyncSearchServices(final XMPPConnection xmppConnection, final int maxEntries, final int maxDepth, final int maxSearchNodes, final String protocol, final boolean searchBuddies) {
        final MappedNodes mappedNodes = new MappedNodes();
        final Runnable bgTask = new Runnable(){
            @Override
            public void run() {
                searchServices(new ConcurrentHashMap<String, String>(), xmppConnection, maxEntries, maxDepth, maxSearchNodes, protocol, searchBuddies, mappedNodes);
            }
        };
        executorService.submit(bgTask);
        return mappedNodes;
    }

    public static MappedNodes searchServices(final XMPPConnection xmppConnection, final int maxEntries, final int maxDepth, final int maxSearchNodes, final String protocol, final boolean searchBuddies) {
        return searchServices(new ConcurrentHashMap<String, String>(), xmppConnection, maxEntries, maxDepth, maxSearchNodes, protocol, searchBuddies, new MappedNodes());
    }

    private static MappedNodes searchServices(final ConcurrentHashMap<String, String> visited, final XMPPConnection xmppConnection, final int maxEntries, final int maxDepth, final int maxSearchNodes, final String protocol, final boolean searchBuddies, final MappedNodes mappedNodes) {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return null;
        }

        searchDiscoItems(xmppConnection, maxEntries, xmppConnection.getServiceName(), mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);

        // Request to Server
        deepSearch(xmppConnection, maxEntries, xmppConnection.getHost(), mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);

        // Request to Buddies
        if (xmppConnection.getRoster() != null && searchBuddies) {
            for (final RosterEntry re : xmppConnection.getRoster().getEntries()) {
                for (final Iterator<Presence> i = xmppConnection.getRoster().getPresences(re.getUser()); i.hasNext();) {
                    final Presence presence = i.next();
                    if (presence.isAvailable()) {
                        deepSearch(xmppConnection, maxEntries, presence.getFrom(), mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);
                    }
                }
            }
        }

        return mappedNodes;
    }

    private static void searchDiscoItems(final XMPPConnection xmppConnection, final int maxEntries, final String startPoint, final MappedNodes mappedNodes, final int maxDepth, final int maxSearchNodes, final String protocol, final ConcurrentHashMap<String, String> visited) {
        final DiscoverItems items = new DiscoverItems();
        items.setTo(startPoint);
        PacketCollector collector = xmppConnection.createPacketCollector(new PacketIDFilter(items.getPacketID()));
        xmppConnection.sendPacket(items);
        DiscoverItems result = (DiscoverItems) collector.nextResult(Math.round(SmackConfiguration.getPacketReplyTimeout() * 1.5));

        if (result != null) {
            final Iterator<DiscoverItems.Item> i = result.getItems();
            for (DiscoverItems.Item item = i.hasNext() ? i.next() : null; item != null; item = i.hasNext() ? i.next() : null) {
                deepSearch(xmppConnection, maxEntries, item.getEntityID(), mappedNodes, maxDepth, maxSearchNodes, protocol, visited);
            }
        }
        collector.cancel();
    }

    public static class MappedNodes {
        final Map<String, TrackerEntry> relayEntries = Collections.synchronizedMap(new LinkedHashMap<String, TrackerEntry>());
        final Map<String, TrackerEntry> trackerEntries = Collections.synchronizedMap(new LinkedHashMap<String, TrackerEntry>());

        public Map<String, TrackerEntry> getRelayEntries() {
            return relayEntries;
        }

        public Map<String, TrackerEntry> getTrackerEntries() {
            return trackerEntries;
        }
    }

    ConcurrentHashMap<String, RelayChannel> getChannels() {
        return channels;
    }

    public JingleTrackerIQ createKnownNodes() {

        final JingleTrackerIQ iq = new JingleTrackerIQ();
        iq.setType(IQ.Type.RESULT);

        for (final TrackerEntry entry : trackerEntries.values()) {
            if (!entry.getPolicy().equals(TrackerEntry.Policy._roster)) {
                iq.addEntry(entry);
            }
        }

        return iq;
    }

    public void addTrackerEntry(final TrackerEntry entry) {
        trackerEntries.put(entry.getJid(), entry);
    }

    public void addEntries(final MappedNodes entries) {
        for (final TrackerEntry t : entries.getRelayEntries().values()) {
            addTrackerEntry(t);
        }
        for (final TrackerEntry t : entries.getTrackerEntries().values()) {
            addTrackerEntry(t);
        }
    }

    public Map<String, TrackerEntry> getTrackerEntries() {
        return trackerEntries;
    }

    public TrackerEntry getPreferedRelay() {
        for (final TrackerEntry trackerEntry : trackerEntries.values()) {
            if (TrackerEntry.Type.relay.equals(trackerEntry.getType())) {
                return trackerEntry;
            }
        }
        return null;
    }
}
