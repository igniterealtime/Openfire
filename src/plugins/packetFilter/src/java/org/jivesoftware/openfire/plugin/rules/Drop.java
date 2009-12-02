package org.jivesoftware.openfire.plugin.rules;

import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;


public class Drop extends AbstractRule implements Rule {

	private static final Logger Log = LoggerFactory.getLogger(Drop.class);
	
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
