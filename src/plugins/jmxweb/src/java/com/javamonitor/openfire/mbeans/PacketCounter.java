package com.javamonitor.openfire.mbeans;

import java.util.concurrent.atomic.AtomicLong;

import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Counts the amount of stanzas (broken down into distinct types) that have been
 * processed by this server instance.
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PacketCounter implements PacketCounterMBean {
    /**
     * The intercepter that is used to count individual Packet objects
     */
    private PacketInterceptor interceptor;

    private final AtomicLong stanza = new AtomicLong();

    private final AtomicLong message = new AtomicLong();

    private final AtomicLong presence = new AtomicLong();

    private final AtomicLong iq = new AtomicLong();

    private final AtomicLong iqGet = new AtomicLong();

    private final AtomicLong iqSet = new AtomicLong();

    private final AtomicLong iqResult = new AtomicLong();

    private final AtomicLong iqError = new AtomicLong();

    /**
     * Resets all counters, and starts counting.
     */
    public void start() {
        // Register a packet listener so that we can track packet traffic.
        interceptor = new PacketInterceptor() {
            public void interceptPacket(final Packet packet,
                    final Session session, final boolean incoming,
                    final boolean processed) {

                if (!processed) {
                    // don't count packets twice!
                    return;
                }

                stanza.incrementAndGet();

                if (packet instanceof Message) {
                    message.incrementAndGet();
                }

                if (packet instanceof Presence) {
                    presence.incrementAndGet();
                }

                if (packet instanceof IQ) {
                    iq.incrementAndGet();

                    switch (((IQ) packet).getType()) {
                    case get:
                        iqGet.incrementAndGet();
                        break;
                    case set:
                        iqSet.incrementAndGet();
                        break;
                    case result:
                        iqResult.incrementAndGet();
                        break;
                    case error:
                        iqError.incrementAndGet();
                        break;
                    }
                }
            }
        };

        // reset counters
        stanza.set(0);
        message.set(0);
        presence.set(0);
        iq.set(0);
        iqGet.set(0);
        iqSet.set(0);
        iqResult.set(0);
        iqError.set(0);

        // register listener
        InterceptorManager.getInstance().addInterceptor(interceptor);

    }

    /**
     * Stops counting.
     */
    public void stop() {
        InterceptorManager.getInstance().removeInterceptor(interceptor);
    }

    /**
     * @see com.javamonitor.openfire.mbeans.PacketCounterMBean#getIQCount()
     */
    public long getIQCount() {
        return iq.get();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.PacketCounterMBean#getIQErrorCount()
     */
    public long getIQErrorCount() {
        return iqError.get();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.PacketCounterMBean#getIQGetCount()
     */
    public long getIQGetCount() {
        return iqGet.get();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.PacketCounterMBean#getIQResultCount()
     */
    public long getIQResultCount() {
        return iqResult.get();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.PacketCounterMBean#getIQSetCount()
     */
    public long getIQSetCount() {
        return iqSet.get();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.PacketCounterMBean#getMessageCount()
     */
    public long getMessageCount() {
        return message.get();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.PacketCounterMBean#getPresenceCount()
     */
    public long getPresenceCount() {
        return presence.get();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.PacketCounterMBean#getStanzaCount()
     */
    public long getStanzaCount() {
        return stanza.get();
    }
}
