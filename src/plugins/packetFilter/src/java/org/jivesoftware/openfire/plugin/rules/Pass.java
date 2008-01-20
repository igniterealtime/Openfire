package org.jivesoftware.openfire.plugin.rules;

import org.xmpp.packet.Packet;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.util.Log;

public class Pass extends AbstractRule implements Rule {

    public String getDisplayName() {
        return "Pass";
    }

    public Packet doAction(Packet packet) throws PacketRejectedException {
        if (doLog()) {
            Log.info("Passing from "+packet.getFrom()+" to "+packet.getTo());
        }
        return null;
    }
}
