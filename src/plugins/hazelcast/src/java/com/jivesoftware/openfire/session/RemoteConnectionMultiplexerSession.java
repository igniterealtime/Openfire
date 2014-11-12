/**
 * $Revision: $
 * $Date: $
 *
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

package com.jivesoftware.openfire.session;

import org.jivesoftware.openfire.session.ConnectionMultiplexerSession;
import org.jivesoftware.util.cache.ClusterTask;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * Surrogate for connection manager sessions hosted in some remote cluster node.
 *
 * @author Gaston Dombiak
 */
public class RemoteConnectionMultiplexerSession extends RemoteSession implements ConnectionMultiplexerSession {

    public RemoteConnectionMultiplexerSession(byte[] nodeID, JID address) {
        super(nodeID, address);
    }

    RemoteSessionTask getRemoteSessionTask(RemoteSessionTask.Operation operation) {
        return new ConnectionMultiplexerSessionTask(address, operation);
    }

    ClusterTask getDeliverRawTextTask(String text) {
        return new DeliverRawTextTask(this, address, text);
    }

    ClusterTask getProcessPacketTask(Packet packet) {
        return new ProcessPacketTask(this, address, packet);
    }
}
