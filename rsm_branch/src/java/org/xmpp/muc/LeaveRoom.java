/**
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.xmpp.muc;

import org.xmpp.packet.Presence;

/**
 * Initial presence sent when joining an existing room or creating a new room. The JoinRoom presence
 * indicates the posibility of the sender to speak MUC.<p>
 *
 * Code example:
 * <pre>
 * // Join an existing room or create a new one.
 * JoinRoom joinRoom = new JoinRoom("john@jabber.org/notebook", "room@conference.jabber.org/nick");
 *
 * component.sendPacket(joinRoom);
 * </pre>
 *
 * @author Gaston Dombiak
 */
public class LeaveRoom extends Presence {

    /**
     * Creates a new Presence packet that could be sent to a MUC service in order to leave the room.
     *
     * @param from the full JID of the user that wants to leave the room.
     * @param to the room JID. That is the room address plus the nickname of the user as a resource.
     */
    public LeaveRoom(String from, String to) {
        super();
        setFrom(from);
        setTo(to);
        setType(Type.unavailable);
    }
}
