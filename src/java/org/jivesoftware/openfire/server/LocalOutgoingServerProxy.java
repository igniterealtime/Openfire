/**
 * 
 */
package org.jivesoftware.openfire.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.spi.RoutingTableImpl;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * @author dwd
 *
 */
public class LocalOutgoingServerProxy implements RoutableChannelHandler {
    private static final Logger log = LoggerFactory.getLogger(LocalOutgoingServerProxy.class);
    private JID domain;
    private ServerSession session;
    private Queue<Packet> packets;
    private static ExecutorService pool = createPool();
    private long failureTimestamp = -1;
    private boolean isTrying;
    
    private static ExecutorService createPool() {
        // Create a pool of threads that will process queued session requests.
        int maxThreads = JiveGlobals.getIntProperty(ConnectionSettings.Server.QUEUE_MAX_THREADS, 20);
        if (maxThreads < 10) {
            // Ensure that the max number of threads in the pool is at least 10
            maxThreads = 10;
        }
        ExecutorService pool = Executors.newFixedThreadPool(maxThreads);
        return pool;
    }
    
    public LocalOutgoingServerProxy(final JID domain) {
        this.domain = domain;
        this.session = null;
        this.packets = null;
    }

    public LocalOutgoingServerProxy(final String domain) {
        this.domain = new JID(domain);
        this.session = null;
        this.packets = null;
    }

    public LocalOutgoingServerProxy(final JID domain, ServerSession session) {
        this.domain = domain;
        this.session = null;
        this.packets = null;
    }

    public LocalOutgoingServerProxy(final String domain, ServerSession session) {
        this.domain = new JID(domain);
        this.session = null;
        this.packets = null;
    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.ChannelHandler#process(org.xmpp.packet.Packet)
     */
    @Override
    public synchronized void process(final Packet packet) throws UnauthorizedException,
            PacketException {
        if (this.session != null) {
            this.session.process(packet);
            return;
        }
        if (packets == null) {
            packets = new LinkedBlockingQueue<Packet>();
            log.info("Queued packet for {}.", domain.toString());
        }
        packets.add(packet.createCopy());
        if (isTrying == false) {
            final String fromDomain = packet.getFrom().getDomain().toString();
            final String toDomain = packet.getTo().getDomain().toString();
            if ((failureTimestamp == -1) || ((System.currentTimeMillis() - failureTimestamp) >= 5000)) {
                isTrying = true;
                log.debug("Spinning up new session to {}", domain.toString());
                pool.execute(new Runnable() {
                    public void run() {
                        log.debug("Initiating connection thread for {} -> {} ({})", fromDomain, toDomain, domain.toString());
                        try {
                            ServerSession s = LocalOutgoingServerSession.authenticateDomain(fromDomain, toDomain); // Long-running.
                            if (s != null) {
                                sessionReady(s);
                            } else {
                                sessionFailed();
                            }
                        } catch(Exception e) {
                            log.debug("Session for {} failed with:", domain.toString(), e);
                            sessionFailed();
                        }
                        log.debug("Finished connection thread for {}", domain.toString());
                        return;
                    }
                });
            } else {
                sessionFailed();
            }
        } else {
            // Session creation in progress.
            packets.add(packet);
        }
    }
    
    protected synchronized void sessionReady(ServerSession session) {
        isTrying = false;
        log.debug("Spun up new session to {}", domain.toString());
        int sent = 0;
        this.session = session;
        while (!this.packets.isEmpty()) {
            Packet packet = this.packets.remove();
            this.session.process(packet);
            sent = sent + 1;
        }
        this.packets = null;
        log.debug("Done, sent {} pending stanzas.", sent);
    }
    
    protected synchronized void sessionFailed() {
        isTrying = false;
        log.debug("Failed to spin up new session to {}", domain.toString());
        while (!this.packets.isEmpty()) {
            Packet packet = this.packets.remove();
            LocalSession.returnErrorToSender(packet);
        }
        this.packets = null;
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.openfire.RoutableChannelHandler#getAddress()
     */
    @Override
    public JID getAddress() {
        return this.domain;
    }

    public ServerSession getSession() {
        return this.session;
    }
}
