package com.reucon.openfire.plugin.archive.xep;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import com.reucon.openfire.plugin.archive.IndexManager;
import com.reucon.openfire.plugin.archive.PersistenceManager;

/**
 * Abstract base class for XEP-specific IQ Handlers.
 */
public abstract class AbstractIQHandler extends IQHandler {

    private final IQHandlerInfo info;

    protected AbstractIQHandler(String moduleName, String elementName, String namespace) {
        super(moduleName);
        this.info = new IQHandlerInfo(elementName, namespace);
    }

    public final IQHandlerInfo getInfo() {
        return info;
    }

    protected PersistenceManager getPersistenceManager(JID jid) {
        return MonitoringPlugin.getInstance().getPersistenceManager(jid);
    }

    protected IndexManager getIndexManager() {
        return MonitoringPlugin.getInstance().getIndexManager();
    }

    protected IQ error(Packet packet, PacketError.Condition condition) {
        IQ reply;

        reply = new IQ(IQ.Type.error, packet.getID());
        reply.setFrom(packet.getTo());
        reply.setTo(packet.getFrom());
        reply.setError(condition);
        return reply;
    }
}
