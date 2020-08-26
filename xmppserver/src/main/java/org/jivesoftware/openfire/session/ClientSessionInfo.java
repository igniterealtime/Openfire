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

package org.jivesoftware.openfire.session;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.Presence;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Client session information to be used when running in a cluster. The session
 * information is shared between cluster nodes and is meant to be used by remote
 * sessions to avoid invocation remote calls and instead use cached information.
 * This optimization should give an important boost to the application specifically
 * while users are logging in.<p>
 *
 * Session information is stored after a user authenticated and bound a resource.
 *
 * @author Gaston Dombiak
 */
public class ClientSessionInfo implements Externalizable {
    private Presence presence;
    private String defaultList;
    private String activeList;
    private boolean offlineFloodStopped;
    private boolean messageCarbonsEnabled;
    private boolean hasRequestedBlocklist;
    private NodeID nodeID;

    public ClientSessionInfo() {
    }

    public ClientSessionInfo(LocalClientSession session) {
        presence = session.getPresence();
        defaultList = session.getDefaultList() != null ? session.getDefaultList().getName() : null;
        activeList = session.getActiveList() != null ? session.getActiveList().getName() : null;
        offlineFloodStopped = session.isOfflineFloodStopped();
        messageCarbonsEnabled = session.isMessageCarbonsEnabled();
        hasRequestedBlocklist=session.hasRequestedBlocklist();
        nodeID = XMPPServer.getInstance().getNodeID();
    }

    public Presence getPresence() {
        return presence;
    }

    public String getDefaultList() {
        return defaultList;
    }

    public String getActiveList() {
        return activeList;
    }

    public boolean isOfflineFloodStopped() {
        return offlineFloodStopped;
    }
    
    public boolean hasRequestedBlocklist() {
        return hasRequestedBlocklist;
    }
    
    public boolean isMessageCarbonsEnabled() { return messageCarbonsEnabled; }

    public NodeID getNodeID() {
        return nodeID;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        ExternalizableUtil.getInstance().writeBoolean(out, defaultList != null);
        if (defaultList != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, defaultList);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, activeList != null);
        if (activeList != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, activeList);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, offlineFloodStopped);
        ExternalizableUtil.getInstance().writeBoolean(out, messageCarbonsEnabled);    
        ExternalizableUtil.getInstance().writeBoolean(out, hasRequestedBlocklist);
        ExternalizableUtil.getInstance().writeSerializable(out, nodeID);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        presence = new Presence(packetElement, true);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            defaultList = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            activeList = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        offlineFloodStopped = ExternalizableUtil.getInstance().readBoolean(in);
        messageCarbonsEnabled = ExternalizableUtil.getInstance().readBoolean(in);
        hasRequestedBlocklist = ExternalizableUtil.getInstance().readBoolean(in);
        nodeID = (NodeID) ExternalizableUtil.getInstance().readSerializable(in);
    }
}
