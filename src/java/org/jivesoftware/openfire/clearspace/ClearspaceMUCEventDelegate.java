package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCEventDelegate;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.dom4j.Element;
import org.dom4j.Attribute;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.Iterator;

/**
 * Handles checking with Clearspace regarding whether a user can join a particular MUC room (based
 * on their permissions with the Clearspace JiveObject (eg. Community/Space) that the room is associated with).
 *
 * In addition, this MUCEventDelegate provides a means to obtain room configuration details from Clearspace
 * in the event that the Clearspace MUC service needs to create a room on-demand (eg. when a user first joins the room).
 *
 * @author Armando Jagucki
 */
public class ClearspaceMUCEventDelegate extends MUCEventDelegate {

    private String csMucDomain;
    private String csComponentAddress;
    private final String GET_ROOM_CONFIG_WARNING ="Clearspace sent an unexpected reply to a get-room-config request.";

    public ClearspaceMUCEventDelegate() {
        String xmppDomain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        csMucDomain = ClearspaceManager.MUC_SUBDOMAIN + "." + xmppDomain;
        csComponentAddress = ClearspaceManager.CLEARSPACE_COMPONENT + "." + xmppDomain;
    }

    public InvitationResult sendingInvitation(MUCRoom room, JID invitee, JID inviter, String reason)
    {
        // Packet should look like:
        // <iq to="clearspace.example.org" from="clearspace-conference.example.org">
        //    <room-invite xmlns="http://jivesoftware.com/clearspace">
        //        <inviter>username@example.org</inviter>
        //        <room>14-1234@clearspace-conference.example.org</roomjid>
        //        <reason>Example Message</reason>
        //        <invitee>anotheruser@example.org</invitee>
        //    </room-invite>
        // </iq>

        IQ query = new IQ();
        query.setFrom(csMucDomain);
        Element cmd = query.setChildElement("invite-check", "http://jivesoftware.com/clearspace");
        Element inviterjidElement = cmd.addElement("inviter");
        inviterjidElement.setText(inviter.toBareJID());
        Element inviteejidElement = cmd.addElement("invitee");
        inviteejidElement.setText(invitee.toBareJID());
        Element roomjidElement = cmd.addElement("room");
        roomjidElement.setText(room.getJID().toBareJID());
        Element messageElement = cmd.addElement("reason");
        messageElement.setText(reason);

        IQ result = ClearspaceManager.getInstance().query(query, 15000);
        if (null != result) {
            if (result.getType() != IQ.Type.error) {
                // No error, that indicates that we were successful and the user is permitted.
                return InvitationResult.HANDLED_BY_DELEGATE;
            }
            else if(result.getError().getType() == PacketError.Type.continue_processing) {
                return InvitationResult.HANDLED_BY_OPENFIRE;
            }
        }

        // No successful return, not allowed.
        return InvitationResult.REJECTED;
    }

    /**
     * Returns true if the user is allowed to join the room. If the userjid is an owner of the room,
     * we will return true immediately.
     *
     * @param room the room the user is attempting to join.
     * @param userjid  the JID of the user attempting to join the room.
     * @return true if the user is allowed to join the room.
     */
    public boolean joiningRoom(MUCRoom room, JID userjid) {
        // Always allow an owner to join the room (especially since they need to join to configure the
        // room on initial creation).
        Collection<String> owners = room.getOwners();
        if (owners != null && owners.contains(userjid.toBareJID())) {
            return true;
        }

        // Packet should look like:
        // <iq to="clearspace.example.org" from="clearspace-conference.example.org">
        //    <join-check xmlns="http://jivesoftware.com/clearspace">
        //        <userjid>username@example.org</userjid>
        //        <roomjid>14-1234@clearspace-conference.example.org</roomjid>
        //    </join-check>
        // </iq>
        IQ query = new IQ();
        query.setFrom(csMucDomain);
        Element cmd = query.setChildElement("join-check", "http://jivesoftware.com/clearspace");
        Element userjidElement = cmd.addElement("userjid");
        userjidElement.setText(userjid.toBareJID());
        Element roomjidElement = cmd.addElement("roomjid");
        roomjidElement.setText(room.getJID().toBareJID());

        IQ result = ClearspaceManager.getInstance().query(query, 15000);
        if (result == null) {
            // No answer was received, assume false for security reasons.
            if (Log.isDebugEnabled()) {
                Log.debug("No answer from Clearspace on join-check in ClearspaceMUCEventDelegate. User: "
                        + userjid.toBareJID() + " Room: " + room.getJID().toBareJID());
            }
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

        IQ iq = new IQ(IQ.Type.get);
        iq.setFrom(csMucDomain);
        iq.setID("get_room_config_" + StringUtils.randomString(3));
        Element child = iq.setChildElement("get-room-config", "http://jivesoftware.com/clearspace");
        Element roomjidElement = child.addElement("roomjid");
        JID roomJid = new JID(roomName + "@" + csMucDomain);
        roomjidElement.setText(roomJid.toBareJID());
        IQ result = ClearspaceManager.getInstance().query(iq, 15000);
        if (result == null) {
            // No answer was received from Clearspace, so return null.
            Log.warn(GET_ROOM_CONFIG_WARNING + " Room: " + roomJid.toBareJID());
            return null;
        }
        else if (result.getType() != IQ.Type.result) {
            // The reply was not a valid result containing the room configuration, so return null.
            Log.warn(GET_ROOM_CONFIG_WARNING + " Room: " + roomJid.toBareJID());
            return null;
        }

        // Setup room configuration based on the configuration values in the result packet.
        Element query = result.getChildElement();
        if (query == null) {
            Log.warn(GET_ROOM_CONFIG_WARNING + " Room: " + roomJid.toBareJID());
            return null;
        }
        Element xElement = query.element("x");
        if (xElement == null) {
            Log.warn(GET_ROOM_CONFIG_WARNING + " Room: " + roomJid.toBareJID());
            return null;
        }
        Iterator fields = xElement.elementIterator("field");
        while (fields.hasNext()) {
            Element field = (Element) fields.next();
            Attribute varAttribute = field.attribute("var");
            if (varAttribute != null) {
                Element value = field.element("value");
                if (value != null) {
                    roomConfig.put(varAttribute.getValue(), value.getText());
                }
            }
        }

        String ownerJid = roomJid.getNode() + "@" + csComponentAddress;
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
        // We never allow destroying a room as a user, but clearspace components are permitted.
        return ClearspaceManager.getInstance().isFromClearspace(userjid);
    }
}
