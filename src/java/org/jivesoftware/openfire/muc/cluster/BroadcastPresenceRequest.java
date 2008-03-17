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

package org.jivesoftware.openfire.muc.cluster;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that broadcasts the presence of a room occupant to the occupants of the room
 * being hosted by the cluster node. When a room occupant changes his presence an
 * instance of this class will be sent to each cluster node and when executed a broadcast
 * of the updated presence will be sent to local room occupants.
 *
 * @author Gaston Dombiak
 */
public class BroadcastPresenceRequest extends MUCRoomTask {
    private Presence presence;

    public BroadcastPresenceRequest() {
    }

    public BroadcastPresenceRequest(LocalMUCRoom room, Presence message) {
        super(room);
        this.presence = message;
    }

    public Presence getPresence() {
        return presence;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        getRoom().broadcast(this);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        presence = new Presence(packetElement, true);
    }
}
