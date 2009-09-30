/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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

import org.jivesoftware.util.cache.ExternalizableUtil;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * Representation of a service configuration. This information is requested by a cluster node when
 * joining the cluster (requested to the senior member) and also from each cluster node to the new node
 * joining the cluster. Each cluster node (existing and new one) has to merge its local services with the
 * new ones.  It does not include database stored configuration options, as they are handled via database
 * reads.
 *
 * @author Daniel Henninger
 */
public class ServiceInfo implements Externalizable {
    private String subdomain;
    private String description;
    private Boolean isHidden;
    private List<RoomInfo> rooms = new ArrayList<RoomInfo>();

    /**
     * Do not use this constructor. Needed for Externalizable interface.
     */
    public ServiceInfo() {
    }

    public ServiceInfo(MultiUserChatService service) {
        this.subdomain = service.getServiceName();
        this.description = service.getDescription();
        this.isHidden = service.isHidden();
        rooms = new ArrayList<RoomInfo>();
        // Get rooms that have occupants and include them in the reply
        for (MUCRoom room : service.getChatRooms()) {
            LocalMUCRoom localRoom = (LocalMUCRoom) room;
            if (!room.getOccupants().isEmpty()) {
                rooms.add(new RoomInfo(localRoom, localRoom.getOccupants()));
            }
        }
    }

    public String getSubdomain() {
        return subdomain;
    }

    public String getDescription() {
        return description;
    }

    public Boolean isHidden() {
        return isHidden;
    }

    public List<RoomInfo> getRooms() {
        return rooms;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, subdomain);
        ExternalizableUtil.getInstance().writeSafeUTF(out, description);
        ExternalizableUtil.getInstance().writeBoolean(out, isHidden);
        ExternalizableUtil.getInstance().writeExternalizableCollection(out, rooms);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        subdomain = ExternalizableUtil.getInstance().readSafeUTF(in);
        description = ExternalizableUtil.getInstance().readSafeUTF(in);
        isHidden = ExternalizableUtil.getInstance().readBoolean(in);
        ExternalizableUtil.getInstance().readExternalizableCollection(in, rooms, getClass().getClassLoader());
    }
}
