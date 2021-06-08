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
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.spi.LocalMUCRole;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import javax.annotation.Nonnull;
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
public class BroadcastPresenceRequest extends MUCRoomTask<Void> {
    private static final Logger Log = LoggerFactory.getLogger( BroadcastPresenceRequest.class );

    private Presence presence;

    private JID userAddressSender;

    private boolean isJoinPresence;

    public BroadcastPresenceRequest() {
    }

    public BroadcastPresenceRequest(@Nonnull final LocalMUCRoom room, @Nonnull final MUCRole sender, @Nonnull final Presence presence, final boolean isJoinPresence) {
        super(room);
        this.userAddressSender = sender.getUserAddress();
        this.presence = presence;
        this.isJoinPresence = isJoinPresence;

        if (!presence.getFrom().asBareJID().equals(room.getJID())) {
            // At this point, the 'from' address of the to-be broadcasted stanza can be expected to be the role-address
            // of the subject, or more broadly: it's bare JID representation should match that of the room. If that's not
            // the case then there's a bug in Openfire. Catch this here, as otherwise, privacy-sensitive data is leaked.
            // See: OF-2152
            throw new IllegalArgumentException("Broadcasted presence stanza's 'from' JID " + presence.getFrom() + " does not match room JID: " + room.getJID());
        }
    }

    public Presence getPresence() {
        return presence;
    }

    public JID getUserAddressSender() {
        return userAddressSender;
    }

    public boolean isJoinPresence() {
        return isJoinPresence;
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
                    getRoom().broadcast( BroadcastPresenceRequest.this );
                }
                catch ( Exception e )
                {
                    Log.warn( "An unexpected exception occurred while trying to broadcast a presence update from {} in the room {}", presence.getFrom(), getRoom().getJID(), e );
                }
            }
        });
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        ExternalizableUtil.getInstance().writeSerializable(out, userAddressSender);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        presence = new Presence(packetElement, true);
        userAddressSender = (JID) ExternalizableUtil.getInstance().readSerializable(in);
    }
}
