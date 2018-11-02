package org.jivesoftware.openfire.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
     *
     * @param jids
     *            the jids
     * @return the array list< string>
     */
    public static List<String> convertJIDsToStringList(Collection<JID> jids) {
        List<String> result = new ArrayList<String>();

        for (JID jid : jids) {
            result.add(jid.toBareJID());
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
