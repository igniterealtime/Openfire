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
public class JoinRoom extends Presence {

    /**
     * Creates a new Presence packet that could be sent to a MUC service in order to join
     * an existing MUC room or create a new one.
     *
     * @param from the real full JID of the user that will join or create a MUC room.
     * @param to a full JID where the bare JID is the MUC room address and the resource is the
     *        nickname of the user joining the room.
     */
    public JoinRoom(String from, String to) {
        super();
        setFrom(from);
        setTo(to);
        addChildElement("x", "http://jabber.org/protocol/muc");
    }
}
