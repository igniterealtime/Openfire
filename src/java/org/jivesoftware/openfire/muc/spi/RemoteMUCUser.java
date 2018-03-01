/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    @Override
    public JID getAddress() {
        return realjid;
    }

    @Override
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
