/*
 * Copyright (C) 2021-2024 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl;
import org.jivesoftware.openfire.muc.spi.OccupantManager;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

/**
 * Task that is used by a cluster node to inform other cluster nodes of its local occupants. This is intended to be used
 * by a node that is joining a cluster (to send its local occupants to all other nodes), and by nodes in an existing
 * cluster that detect a new node joining (to send their local occupants to the joining node).
 *
 * @author Guus der Kinderen
 */
public class SyncLocalOccupantsAndSendJoinPresenceTask implements ClusterTask<Void>
{
    private static final Logger Log = LoggerFactory.getLogger(SyncLocalOccupantsAndSendJoinPresenceTask.class);

    private String subdomain;
    private Set<OccupantManager.Occupant> occupants = new HashSet<>();
    private NodeID originator;

    public SyncLocalOccupantsAndSendJoinPresenceTask() {}

    public SyncLocalOccupantsAndSendJoinPresenceTask(@Nonnull final String subdomain, @Nonnull final Set<OccupantManager.Occupant> occupants) {
        this.subdomain = subdomain;
        this.occupants = occupants;
        this.originator = XMPPServer.getInstance().getNodeID();
    }

    public Set<OccupantManager.Occupant> getOccupants() {
        return occupants;
    }

    public NodeID getOriginator() {
        return originator;
    }

    @Override
    public Void getResult() {
        return null;
    }

    @Override
    public void run() {
        Log.debug("Going to execute sync occupants task for {} occupants from node {}", occupants.size(), originator);
        final MultiUserChatService multiUserChatService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(subdomain);
        ((MultiUserChatServiceImpl) multiUserChatService).process(this);
        Log.trace("Finished executing sync occupants task for occupants {} from node {}", occupants.size(), originator);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        final ExternalizableUtil externalizableUtil = ExternalizableUtil.getInstance();
        externalizableUtil.writeSafeUTF(out, subdomain);
        externalizableUtil.writeLong(out, occupants.size());
        for (final OccupantManager.Occupant occupant : occupants) {
            externalizableUtil.writeSafeUTF(out, occupant.getRoomName());
            externalizableUtil.writeSafeUTF(out, occupant.getNickname());
            externalizableUtil.writeSerializable(out, occupant.getRealJID());
            // We should not send the lastActive field, as that's used only by the cluster node that the occupant is local to.
        }
        externalizableUtil.writeSerializable(out, originator);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        final ExternalizableUtil externalizableUtil = ExternalizableUtil.getInstance();
        subdomain = externalizableUtil.readSafeUTF(in);
        final long size = externalizableUtil.readLong(in);
        this.occupants = new HashSet<>();
        for (long i=0; i<size; i++) {
            final String roomName = externalizableUtil.readSafeUTF(in);
            final String nickname = externalizableUtil.readSafeUTF(in);
            final JID realJID = (JID) externalizableUtil.readSerializable(in);
            final OccupantManager.Occupant occupant = new OccupantManager.Occupant(roomName, nickname, realJID);
            occupants.add(occupant);
        }
        originator = (NodeID) externalizableUtil.readSerializable(in);
    }
}
