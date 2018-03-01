/*
 * Copyright (C) 2007-2009 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin.session;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.session.IncomingServerSession;
import org.jivesoftware.util.cache.ClusterTask;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.util.Collection;

/**
 * Surrogate for incoming server sessions hosted in some remote cluster node.
 *
 * @author Gaston Dombiak
 */
public class RemoteIncomingServerSession extends RemoteSession implements IncomingServerSession {

    private String localDomain;
    private long usingServerDialback = -1;

    public RemoteIncomingServerSession(byte[] nodeID, StreamID streamID) {
        super(nodeID, null);
        this.streamID = streamID;
    }

    public boolean isUsingServerDialback() {
        if (usingServerDialback == -1) {
            ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.isUsingServerDialback);
            usingServerDialback = (Boolean) doSynchronousClusterTask(task) ? 1 : 0;
        }
        return usingServerDialback == 1;
    }

    public JID getAddress() {
        if (address == null) {
            RemoteSessionTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getAddress);
            address = (JID) doSynchronousClusterTask(task);
        }
        return address;
    }

    public Collection<String> getValidatedDomains() {
        // Content is stored in a clustered cache so that even in the case of the node hosting
        // the sessions is lost we can still have access to this info to be able to perform
        // proper clean up logic {@link ClusterListener#cleanupNode(NodeCacheKey)
        return SessionManager.getInstance().getValidatedDomains(streamID);
    }

    public String getLocalDomain() {
        if (localDomain == null) {
            RemoteSessionTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getLocalDomain);
            localDomain = (String) doSynchronousClusterTask(task);
        }
        return localDomain;
    }

    RemoteSessionTask getRemoteSessionTask(RemoteSessionTask.Operation operation) {
        return new IncomingServerSessionTask(operation, streamID);
    }

    ClusterTask getDeliverRawTextTask(String text) {
        return new DeliverRawTextTask(streamID, text);
    }

    ClusterTask getProcessPacketTask(Packet packet) {
        return new ProcessPacketTask(streamID, packet);
    }
}
