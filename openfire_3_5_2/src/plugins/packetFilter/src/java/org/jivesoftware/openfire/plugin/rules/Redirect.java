package org.jivesoftware.openfire.plugin.rules;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.PacketFilterUtil;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.Collection;

public class Redirect extends AbstractRule implements Rule {
     public String getDisplayName() {
        return "Redirect";                                                                   
    }

    public Packet doAction(Packet packet) throws PacketRejectedException {
       Message newPacket = null;
        
       if (packet instanceof Message) {

        if (getDestType().equals(Rule.SourceDestType.Group.toString())) {
            Group sendGroup = PacketFilterUtil.getGroup(getDestination());
            ClientSession clientSession;
            for (JID jid : sendGroup.getMembers()) {
                newPacket = (Message) packet.createCopy();
                newPacket.setTo(jid);
                sendPacket(newPacket);
            }
        }

        else {

                JID jid = new JID(getDestination());
                newPacket = (Message) packet.createCopy();
                newPacket.setFrom("test@machintosh.local");
                newPacket.setTo(jid);

                sendPacket(newPacket);
        }

        if (doLog())  {
            Log.info("Redirecting from "+packet.getFrom()+" to "+packet.getTo());
        }
       }

       throw new PacketRejectedException();
        
    }

    public boolean destMustMatch() {
        return false;
    }

    private static void sendPacket(Packet packet) {
        SessionManager sessionManager = XMPPServer.getInstance().getSessionManager();
        ClientSession clientSession;

        clientSession = sessionManager.getSession(packet.getTo());


        Log.info("Sending to "+packet.getTo());
        if (clientSession != null) {
            Log.info("***** Session Not Null ******");
            clientSession.process(packet);
        }
    }
}
