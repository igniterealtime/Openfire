package org.jivesoftware.openfire.labelling;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Created by dwd on 20/03/17.
 */
abstract public class AbstractACDF implements AccessControlDecisionFunction {
    private static final Logger Log = LoggerFactory.getLogger(MessageInterceptor.class);
    private String defaultUserClearance;
    private String defaultPeerClearance;
    private String serverClearance;

    private final String PROP_PEER_PREFIX = "clearance.domain."; // + domain
    private final String PROP_CLEARANCE_USER_DEFAULT = "clearance.user.default";
    private final String PROP_CLEARANCE_PEER_DEFAULT = "clearance.peer.default";
    private final String PROP_CLEARANCE_SERVER = "clearance.server";

    protected AbstractACDF() {
        this.defaultUserClearance = JiveGlobals.getProperty(PROP_CLEARANCE_USER_DEFAULT);
        this.defaultPeerClearance = JiveGlobals.getProperty(PROP_CLEARANCE_PEER_DEFAULT);
        this.serverClearance = JiveGlobals.getProperty(PROP_CLEARANCE_SERVER);
    }

    /**
     * Get a clearance string for a number of possible target objects.
     * @return
     */
    public String getClearance(JID entity) {
        if (XMPPServer.getInstance().isLocal(entity)) {
            User user;
            try {
                user = UserManager.getInstance().getUser(entity.getNode());
            } catch(UserNotFoundException e) {
                return defaultUserClearance;
            }
            String clr = user.getClearance();
            if (clr == null) {
                Log.debug("Using default user clearance");
                return defaultUserClearance;
            }
            return clr;
        } else {
            // Non-user Domain
            String clr = JiveGlobals.getProperty(PROP_PEER_PREFIX + entity.getDomain());
            if (clr == null) {
                if (XMPPServer.getInstance().matchesComponent(new JID(entity.getDomain()))) {
                    clr = serverClearance;
                } else {
                    clr = defaultPeerClearance;
                }
            }
            return clr;
        }
    }
}
