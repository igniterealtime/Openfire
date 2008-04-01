package org.jivesoftware.openfire.muc;

import org.xmpp.packet.JID;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;

import java.util.Map;

/**
 * Gives the implementer the ability to react to, allow, or deny MUC related events.
 *
 * For example:
 *      - Event: a user tries to join a room
 *        Reaction: the delegate decides to allow or deny the user from joining
 *
 * @author Armando Jagucki
 */
public abstract class MUCEventDelegate {

    /**
     * This event will be triggered when an entity joins an existing room.
     *
     * Returns true if the user is allowed to join the room.
     *
     * @param room the MUC room.
     * @param userjid the JID of the user attempting to join the room.
     * @return true if the user is allowed to join the room.
     */
    public abstract boolean joiningRoom(MUCRoom room, JID userjid);

    /**
     * Returns a map containing room configuration variables and values.
     *
     * @param roomName the name of the room the configuration map is associated with.
     * @returna map containing room configuration variables and values, or null if roomName was not valid.
     */
    public abstract Map<String, String> getRoomConfig(String roomName);

    /**
     * This event will be triggered when an entity attempts to destroy a room.
     *
     * Returns true if the user is allowed to destroy the room.
     *
     * @param roomName the name of the MUC room being destroyed.
     * @param userjid the JID of the user attempting to destroy the room.
     * @return true if the user is allowed to destroy the room.
     */
    public abstract boolean destroyingRoom(String roomName, JID userjid);

    /**
     * Loads a delegate provided room configuration for the room specified.
     *
     * @param room the room to load the configuration for.
     * @return true if the room configuration was received from the delegate and applied to the room.
     */
    public boolean loadConfig(MUCRoom room) {
        Map<String, String> roomConfig = getRoomConfig(room.getName());
        if (roomConfig != null) {
            room.setNaturalLanguageName(roomConfig.get("muc#roomconfig_roomname"));
            room.setDescription(roomConfig.get("muc#roomconfig_roomdesc"));
            room.setCanOccupantsChangeSubject("1".equals(roomConfig.get("muc#roomconfig_changesubject")));
            room.setMaxUsers(Integer.parseInt(roomConfig.get("muc#roomconfig_maxusers")));
            room.setPublicRoom("1".equals(roomConfig.get("muc#roomconfig_publicroom")));
            room.setCanOccupantsInvite("1".equals(roomConfig.get("muc#roomconfig_allowinvites")));
            room.setCanAnyoneDiscoverJID("anyone".equals(roomConfig.get("muc#roomconfig_whois")));
            room.setChangeNickname("1".equals(roomConfig.get("x-muc#roomconfig_canchangenick")));
            room.setRegistrationEnabled("1".equals(roomConfig.get("x-muc#roomconfig_registration")));
            room.setPersistent("1".equals(roomConfig.get("muc#roomconfig_persistentroom")));

            room.addFirstOwner(roomConfig.get("muc#roomconfig_roomowners"));

            try {
                room.unlock(room.getRole());
            } catch (ForbiddenException e) {
                return false;
            }
        }
        return roomConfig != null;
    }
}
