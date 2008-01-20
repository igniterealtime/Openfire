package org.jivesoftware.openfire.plugin.rules;

import org.xmpp.packet.Packet;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;


public class Drop extends AbstractRule implements Rule {

    public String getDisplayName() {
        return "Drop";
    }

    public Packet doAction(Packet packet) throws PacketRejectedException {
        if (doLog()) {
            Log.info("Dropping from "+packet.getFrom()+" to "+packet.getTo());
        }
        throw new PacketRejectedException();
    }
}
