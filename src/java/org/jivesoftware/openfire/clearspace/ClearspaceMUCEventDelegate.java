package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.muc.MUCEventDelegate;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.xmpp.packet.JID;
import org.xmpp.packet.IQ;
import org.xmpp.component.ComponentException;
import org.xmpp.component.Component;

import java.util.Map;
import java.util.HashMap;

/**
 * TODO: Comment me
 *
 * @author Armando Jagucki
 */
public class ClearspaceMUCEventDelegate extends MUCEventDelegate {
    /**
     * This event will be triggered when an entity joins an existing room.
     * <p/>
     * Returns true if the user is allowed to join the room.
     *
     * @param roomName the name of the MUC room.
     * @param userjid  the JID of the user attempting to join the room.
     * @return true if the user is allowed to join the room.
     */
    public boolean joiningRoom(String roomName, JID userjid) {
        return false;  // TODO: Implement
    }

    public Map<String, String> getRoomConfig(String roomName) {
        Map<String, String> roomConfig = new HashMap<String, String>();

        // TODO: Ensure the getComponent method gets implemented.
        Component csComponent = ClearspaceManager.getInstance().getComponent();

        // TODO: Get the config by connecting to CS through the component
        InternalComponentManager internalComponentManager = InternalComponentManager.getInstance();
        // TODO: Create query packet asking for the room config and in CS create a handler for that packet
        IQ query = null;
        IQ result;
        try {
            result = internalComponentManager.query(csComponent, query, 15000);
        } catch (ComponentException e) {
            //
        }

        // TODO: Setup roomConfig based on the result packet containing config values
        JID roomJid = new JID(roomName);
        roomConfig.put("muc#roomconfig_roomname", roomJid.getNode());
        roomConfig.put("muc#roomconfig_roomdesc", "");
        roomConfig.put("muc#roomconfig_changesubject", "1");
        roomConfig.put("muc#roomconfig_maxusers", "0");
        roomConfig.put("muc#roomconfig_publicroom", "1");
        roomConfig.put("muc#roomconfig_moderatedroom", "0");
        roomConfig.put("muc#roomconfig_membersonly", "0");
        roomConfig.put("muc#roomconfig_allowinvites", "1");
        roomConfig.put("muc#roomconfig_roomsecret", "");
        roomConfig.put("muc#roomconfig_whois", "anyone");
        roomConfig.put("muc#roomconfig_enablelogging", "0");
        roomConfig.put("x-muc#roomconfig_reservednick", "0");
        roomConfig.put("x-muc#roomconfig_canchangenick", "1");
        roomConfig.put("x-muc#roomconfig_registration", "1");
        roomConfig.put("muc#roomconfig_persistentroom", "1");

        String ownerJid = roomJid.getNode() + "@"
                                + "clearspace." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        roomConfig.put("muc#roomconfig_roomowners", ownerJid);

        return roomConfig;
    }

    /**
     * This event will be triggered when an entity attempts to destroy a room.
     * <p/>
     * Returns true if the user is allowed to destroy the room.
     *
     * @param roomName the name of the MUC room being destroyed.
     * @param userjid  the JID of the user attempting to destroy the room.
     * @return true if the user is allowed to destroy the room.
     */
    public boolean destroyingRoom(String roomName, JID userjid) {
        return false;  // TODO: Implement
    }
}
