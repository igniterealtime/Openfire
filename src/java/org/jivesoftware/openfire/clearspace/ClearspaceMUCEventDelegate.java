package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCEventDelegate;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.dom4j.Element;
import org.dom4j.DocumentHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles checking with Clearspace regarding whether a user can join a particular chatroom (based
 * on their permissions with the document/whatever the chatroom is associated with), as well as setting
 * up room configurations.
 *
 * @author Armando Jagucki
 */
public class ClearspaceMUCEventDelegate extends MUCEventDelegate {

    private String csMucDomain;

    public ClearspaceMUCEventDelegate() {
        csMucDomain = ClearspaceManager.MUC_SUBDOMAIN+"@"+XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    }

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
        // Packet should look like:
        // <iq to="clearspace.example.org" from="clearspace-conference.example.org">
        //    <join-check xmlns="http://jivesoftware.com/clearspace">
        //        <userjid>username@example.org</userjid>
        //        <roomjid>DOC-1234@clearspace-conference.example.org</roomjid>
        //    </join-check>
        // </iq>

        IQ query = new IQ();
        query.setFrom(csMucDomain);
        Element cmd = DocumentHelper.createElement("join-check");
        cmd.addElement("userjid").addText(userjid.toBareJID());
        cmd.addElement("roomjid").addText(roomName+"@"+csMucDomain);
        query.setChildElement(cmd);

        IQ result = ClearspaceManager.getInstance().query(query, 15000);
        if (result == null) {
            // No answer was received, assume false for security reasons.
            return false;
        }

        if (result.getType() != IQ.Type.error) {
            // No error, that indicates that we were successful and the user is permitted.
            return true;
        }

        // No successful return, not allowed.
        return false;
    }

    public Map<String, String> getRoomConfig(String roomName) {
        Map<String, String> roomConfig = new HashMap<String, String>();

        // TODO: Create query packet asking for the room config and in CS create a handler for that packet
        IQ query = null;
        IQ result = ClearspaceManager.getInstance().query(query, 15000);
        if (result == null) {
            // TODO No answer was received from Clearspace so return null
            return null;
        }
        // TODO Check that the IQ is of type RESULT (and not ERROR) otherwise return null

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
        if (userjid.getNode() == null && ClearspaceManager.getInstance().isClearspaceDomain(userjid.getDomain())) {
            // This is a Clearspace domain, and therefore is permitted to destroy what it wants.
            return true;
        }
        // We never allow destroying a room as a user, so return false.
        return false;
    }
}
