package org.jivesoftware.openfire.plugin.rules;

import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

public class Pass extends AbstractRule implements Rule {

	private static final Logger Log = LoggerFactory.getLogger(Pass.class);
	
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
