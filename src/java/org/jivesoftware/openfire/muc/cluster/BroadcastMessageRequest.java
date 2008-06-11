/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.Message;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that broadcasts a message to local room occupants. When a room occupant sends a
 * message to the room each cluster node will execute this task and broadcast the message
 * to its local room occupants.
 *
 * @author Gaston Dombiak
 */
public class BroadcastMessageRequest extends MUCRoomTask {
    private int occupants;
    private Message message;

    public BroadcastMessageRequest() {
    }

    public BroadcastMessageRequest(LocalMUCRoom room, Message message, int occupants) {
        super(room);
        this.message = message;
        this.occupants = occupants;
    }

    public Message getMessage() {
        return message;
    }

    public int getOccupants() {
        return occupants;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        // Execute the operation considering that we may still be joining the cluster
        execute(new Runnable() {
            public void run() {
                getRoom().broadcast(BroadcastMessageRequest.this);
            }
        });
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) message.getElement());
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        message = new Message(packetElement, true);
    }
}
