/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Representation of a room configuration and its occupants. This information is requested
 * by a cluster node when joining the cluster (requested to the senior member) and also from
 * each cluster node to the new node joining the cluster. Each cluster node (existing and
 * new one) has to merge its local rooms with the new ones.
 *
 * @author Gaston Dombiak
 */
public class RoomInfo implements Externalizable {
    private LocalMUCRoom room;
    private List<OccupantAddedEvent> occupants = new ArrayList<OccupantAddedEvent>();


    /**
     * Do not use this constructor. Needed for Externalizable interface.
     */
    public RoomInfo() {
    }

    public RoomInfo(LocalMUCRoom room, Collection<MUCRole> occupants) {
        this.room = room;
        for (MUCRole occupant : occupants) {
            this.occupants.add(new OccupantAddedEvent(room, occupant));
        }
    }


    public LocalMUCRoom getRoom() {
        return room;
    }

    public List<OccupantAddedEvent> getOccupants() {
        return occupants;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSerializable(out, room);
        ExternalizableUtil.getInstance().writeExternalizableCollection(out, occupants);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        room = (LocalMUCRoom) ExternalizableUtil.getInstance().readSerializable(in);
        ExternalizableUtil.getInstance().readExternalizableCollection(in, occupants, getClass().getClassLoader());
    }
}
