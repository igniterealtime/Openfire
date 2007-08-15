/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc.spi;

import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCUser;
import org.xmpp.packet.*;

/**
 * User hosted by another cluster node that is presente in a local room. Remote users are
 * only created when processing unavailable presences sent when the node hosting the actual
 * user went down. Each cluster node remaining in the cluster will create an unavailable
 * presence for each user hosted in the cluster node that went down as a way to indicate
 * the remaining room occupants that the user is offline.
 *
 * @author Gaston Dombiak
 */
public class RemoteMUCUser implements MUCUser {
    /**
     * JID of the user hosted by other cluster node.
     */
    private JID realjid;
    /**
     * Local room that keep a reference to the RemoteMUCRole for this user.
     */
    private LocalMUCRoom room;

    public RemoteMUCUser(JID realjid, LocalMUCRoom room) {
        this.realjid = realjid;
        this.room = room;
    }

    public JID getAddress() {
        return realjid;
    }

    public void process(Packet packet) throws UnauthorizedException, PacketException {
        if (packet instanceof IQ) {
            throw new UnsupportedOperationException("Cannot process IQ packets of remote users: " + packet);
        }
        else if (packet instanceof Message) {
            throw new UnsupportedOperationException("Cannot process Message packets of remote users: " + packet);
        }
        else if (packet instanceof Presence) {
            process((Presence)packet);
        }
    }

    private void process(Presence presence) {
        if (presence.getType() == Presence.Type.unavailable) {
            MUCRole mucRole = room.getOccupantByFullJID(realjid);
            if (mucRole != null) {
                room.leaveRoom(mucRole);
            }
        }
        else {
            throw new UnsupportedOperationException("Cannot process Presence packets of remote users: " + presence);
        }
    }
}
