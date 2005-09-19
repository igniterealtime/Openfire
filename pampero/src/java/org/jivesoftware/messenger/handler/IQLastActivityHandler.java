package org.jivesoftware.messenger.handler;

import org.dom4j.Element;
import org.jivesoftware.messenger.IQHandlerInfo;
import org.jivesoftware.messenger.PresenceManager;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.roster.RosterItem;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Implements the TYPE_IQ jabber:iq:last protocol (last activity). Allows users to find out
 * the number of seconds another user has been offline. This information is only available to
 * those users that already subscribed to the users presence. Otherwhise, a <tt>forbidden</tt>
 * error will be returned.
 *
 * @author Gaston Dombiak
 */
public class IQLastActivityHandler extends IQHandler implements ServerFeaturesProvider {

    private IQHandlerInfo info;
    private PresenceManager presenceManager;

    public IQLastActivityHandler() {
        super("XMPP Last Activity Handler");
        info = new IQHandlerInfo("query", "jabber:iq:last");
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        IQ reply = IQ.createResultIQ(packet);
        Element lastActivity = reply.setChildElement("query", "jabber:iq:last");
        String sender = packet.getFrom().getNode();
        String username = packet.getTo().getNode();

        // Check if any of the usernames is null
        if (sender == null || username == null) {
            reply.setError(PacketError.Condition.forbidden);
            return reply;
        }

        try {
            User user = UserManager.getInstance().getUser(username);
            RosterItem item = user.getRoster().getRosterItem(packet.getFrom());
            // Check that the user requesting this information is subscribed to the user's presence
            if (item.getSubStatus() == RosterItem.SUB_FROM ||
                    item.getSubStatus() == RosterItem.SUB_BOTH) {
                if (sessionManager.getSessions(username).isEmpty()) {
                    // The user is offline so answer the user's "last available time and the
                    // status message of the last unavailable presence received from the user"
                    long lastActivityTime = presenceManager.getLastActivity(user);
                    lastActivity.addAttribute("seconds", String.valueOf(lastActivityTime));
                    String lastStatus = presenceManager.getLastPresenceStatus(user);
                    if (lastStatus != null && lastStatus.length() > 0) {
                        lastActivity.setText(lastStatus);
                    }
                }
                else {
                    // The user is online so answer seconds=0
                    lastActivity.addAttribute("seconds", "0");
                }
            }
            else {
                reply.setError(PacketError.Condition.forbidden);
            }
        }
        catch (UserNotFoundException e) {
            reply.setError(PacketError.Condition.forbidden);
        }
        return reply;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:last");
        return features.iterator();
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        presenceManager = server.getPresenceManager();
    }
}