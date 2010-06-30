package org.jivesoftware.openfire.plugin.rules;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

public class Reject extends AbstractRule implements Rule {

	private static final Logger Log = LoggerFactory.getLogger(Reject.class);
	
    @Override
	public String getDisplayName() {
        return "Reject";
    }

    @Override
	public Packet doAction(Packet packet) throws PacketRejectedException {
        SessionManager sessionManager = SessionManager.getInstance();
        ClientSession clientSession = sessionManager.getSession(packet.getFrom());
        Packet rejectPacket;
        String pfFrom = JiveGlobals.getProperty("pf.From", "packetfilter");


        if (packet instanceof Message) {
            Message in = (Message) packet.createCopy();
            if (clientSession != null && in.getBody() != null) {

                in.setFrom(new JID(pfFrom));
                String rejectMessage = JiveGlobals.getProperty("pf.rejectMessage", "Your message was rejected by the packet filter");
                in.setBody(rejectMessage);
                in.setType(Message.Type.error);
                in.setTo(packet.getFrom());
                String rejectSubject = JiveGlobals.getProperty("pf.rejectSubject", "Rejected");
                in.setSubject(rejectSubject);
                clientSession.process(in);

            }

        } else if (packet instanceof Presence) {
            rejectPacket = new Presence();
            rejectPacket.setTo(packet.getFrom());
            rejectPacket.setError(PacketError.Condition.forbidden);

        } else if (packet instanceof IQ) {
            rejectPacket = new IQ();
            rejectPacket.setTo(packet.getFrom());
            rejectPacket.setError(PacketError.Condition.forbidden);

        }
        if (doLog()) {
            Log.info("Rejecting packet from " + packet.getFrom() + " to " + packet.getTo());
        }
        throw new PacketRejectedException();
    }

    
}
