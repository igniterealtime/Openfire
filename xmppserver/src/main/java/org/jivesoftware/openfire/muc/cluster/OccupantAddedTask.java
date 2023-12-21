/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that informs cluster nodes that a XMPP entity is now an occupant of a MUC room.
 *
 * @author Guus der Kinderen
 */
public class OccupantAddedTask implements ClusterTask<Void>
{
    private String subdomain;
    private String roomName;
    private String nickname;
    private JID realJID;
    private NodeID originator;

    public OccupantAddedTask() {}

    public OccupantAddedTask(@Nonnull final String subdomain, @Nonnull final String roomName, @Nonnull final String nickname, @Nonnull final JID realJID, @Nonnull final NodeID originator) {
        this.subdomain = subdomain;
        this.roomName = roomName;
        this.nickname = nickname;
        this.realJID = realJID;
        this.originator = originator;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getNickname() {
        return nickname;
    }

    public JID getRealJID() {
        return realJID;
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
        final MultiUserChatService multiUserChatService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(subdomain);
        ((MultiUserChatServiceImpl) multiUserChatService).getOccupantManager().process(this);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        final ExternalizableUtil externalizableUtil = ExternalizableUtil.getInstance();
        externalizableUtil.writeSafeUTF(out, subdomain);
        externalizableUtil.writeSafeUTF(out, roomName);
        externalizableUtil.writeSafeUTF(out, nickname);
        externalizableUtil.writeSerializable(out, realJID);
        externalizableUtil.writeSerializable(out, originator);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        final ExternalizableUtil externalizableUtil = ExternalizableUtil.getInstance();
        subdomain = externalizableUtil.readSafeUTF(in);
        roomName = externalizableUtil.readSafeUTF(in);
        nickname = externalizableUtil.readSafeUTF(in);
        realJID = (JID) externalizableUtil.readSerializable(in);
        originator = (NodeID) externalizableUtil.readSerializable(in);
    }
}
