package org.jivesoftware.openfire.plugin.rest.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jivesoftware.openfire.group.Group;
import org.xmpp.packet.JID;

/**
 * The Class MUCRoomUtils.
 */
public class MUCRoomUtils {

    /**
     * Instantiates a new MUC room utils.
     */
    private MUCRoomUtils() {
        throw new AssertionError();
    }

    /**
     * Convert jids to string list.
     * In case the jid is not bare (=it is a group jid) exclude it
     *
     * @param jids
     *            the jids
     * @return the array list< string>
     */
    public static List<String> convertJIDsToStringList(Collection<JID> jids) {
        List<String> result = new ArrayList<String>();

        for (JID jid : jids) {
            if (jid.getResource() == null) result.add(jid.toBareJID());
        }
        return result;
    }

    /**
     * Convert groups to string list
     * @param groups
     * 			the groups
     * @return the array list of the group names
     */
    public static List<String> convertGroupsToStringList(Collection<Group> groups) {
        List<String> result = new ArrayList<String>();
        for (Group group : groups) {
            result.add(group.getName());
        }
        return result;
    }

    /**
     * Convert strings to jids.
     *
     * @param jids
     *            the jids
     * @return the list<jid>
     */
    public static List<JID> convertStringsToJIDs(List<String> jids) {
        List<JID> result = new ArrayList<JID>();

        for (String jidString : jids) {
            result.add(new JID(jidString));
        }
        return result;
    }
}
