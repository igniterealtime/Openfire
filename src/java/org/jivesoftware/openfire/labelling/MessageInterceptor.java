package org.jivesoftware.openfire.labelling;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.PacketExtension;

import java.util.Set;

/**
 * Created by dwd on 16/03/17.
 */
public class MessageInterceptor implements PacketInterceptor {
    private static final Logger Log = LoggerFactory.getLogger(MessageInterceptor.class);

    @Override
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
        Log.debug("Packet intercept pre: " + packet.toString());
        if (processed) {
            return; // Ignore these.
        }
        if (!(packet instanceof Message)) {
            return;
        }
        AccessControlDecisionFunction acdf = XMPPServer.getInstance().getAccessControlDecisionFunction();
        if (acdf == null) {
            return;
        }
        Log.debug("Packet intercept: " + packet.toString());
        Message msg = (Message)packet;
        try {
            boolean rewritten = false;
            boolean need_rewrite = false;
            SecurityLabel secLabel = (SecurityLabel)packet.getExtension("securitylabel", "urn:xmpp:sec-label:0");
            if (secLabel == null) {
                Log.debug("Label is default");
                need_rewrite = true;
            }
            if (msg.getType() != Message.Type.error) {
                Log.debug("Sender");
                acdf.check(acdf.getClearance(packet.getFrom()), secLabel, null);
            } else {
                Log.debug("Skipped originator and server checks for bounced message");
            }
            Log.debug("Recipient");
            SecurityLabel newlabel = acdf.check(acdf.getClearance(packet.getTo()), secLabel, packet.getTo());
            packet.deleteExtension("securitylabel", "urn:xmpp:sec-label:0");
            if (newlabel != null) {
                packet.addExtension(newlabel);
            }
        } catch (Exception e) {
            Log.info("ACDF rejection: ", e);
            if (incoming) {
                if (msg.getType() != Message.Type.error) {
                    Message error = new Message(); // Don't copy; it might introduce an invalid label.
                    error.setTo(msg.getFrom());
                    error.setFrom(msg.getTo());
                    error.setID(msg.getID());
                    error.setError(PacketError.Condition.forbidden);
                    error.setType(Message.Type.error);
                    XMPPServer.getInstance().getMessageRouter().route(error);
                }
            }
            throw new PacketRejectedException(e);
        }
    }
}
