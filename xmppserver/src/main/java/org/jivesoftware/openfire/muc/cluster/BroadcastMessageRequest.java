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

package org.jivesoftware.openfire.muc.cluster;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Message;

import javax.annotation.Nonnull;
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
public class BroadcastMessageRequest extends MUCRoomTask<Void> {
    private static final Logger Log = LoggerFactory.getLogger( BroadcastMessageRequest.class );
    private int occupants;
    private Message message;

    public BroadcastMessageRequest() {
    }

    public BroadcastMessageRequest(@Nonnull final LocalMUCRoom room, @Nonnull final Message message, @Nonnull final int occupants) {
        super(room);
        this.message = message;
        this.occupants = occupants;

        if (!message.getFrom().asBareJID().equals(room.getJID())) {
            // At this point, the 'from' address of the to-be broadcasted stanza can be expected to be the role-address
            // of the subject, or more broadly: it's bare JID representation should match that of the room. If that's not
            // the case then there's a bug in Openfire. Catch this here, as otherwise, privacy-sensitive data is leaked.
            // See: OF-2152
            throw new IllegalArgumentException("Broadcasted presence stanza's 'from' JID " + message.getFrom() + " does not match room JID: " + room.getJID());
        }
    }

    public Message getMessage() {
        return message;
    }

    public int getOccupants() {
        return occupants;
    }

    @Override
    public Void getResult() {
        return null;
    }

    @Override
    public void run() {
        // Execute the operation considering that we may still be joining the cluster
        execute(new Runnable() {
            @Override
            public void run() {
                try
                {
                    getRoom().broadcast( BroadcastMessageRequest.this );
                }
                catch ( Exception e )
                {
                    Log.warn( "An unexpected exception occurred while trying to broadcast a message from {} in the room {}", message.getFrom(), getRoom().getJID() );
                }
            }
        });
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) message.getElement());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        message = new Message(packetElement, true);
    }
}
