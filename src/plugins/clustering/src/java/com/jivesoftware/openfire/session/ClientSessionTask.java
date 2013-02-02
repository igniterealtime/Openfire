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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.ClientRoute;
import org.jivesoftware.openfire.spi.RoutingTableImpl;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Class that defines possible remote operations that could be performed
 * on remote client sessions.
 *
 * @author Gaston Dombiak
 */
public class ClientSessionTask extends RemoteSessionTask {
    private JID address;

    public ClientSessionTask() {
        super();
    }

    protected ClientSessionTask(JID address, Operation operation) {
        super(operation);
        this.address = address;
    }

    Session getSession() {
        return XMPPServer.getInstance().getRoutingTable().getClientRoute(address);
    }

    public void run() {
        super.run();

        ClientSession session = (ClientSession) getSession();
        if (session instanceof RemoteClientSession) {
            // The session is being hosted by other cluster node so log this unexpected case
            Cache<String, ClientRoute> usersCache = CacheFactory.createCache(RoutingTableImpl.C2S_CACHE_NAME);
            ClientRoute route = usersCache.get(address.toString());
            NodeID nodeID = route.getNodeID();

            Log.warn("Found remote session instead of local session. JID: " + address + " found in Node: " +
                    nodeID.toByteArray() + " and local node is: " + XMPPServer.getInstance().getNodeID().toByteArray());
        }
        if (operation == Operation.isInitialized) {
            if (session instanceof RemoteClientSession) {
                // Something is wrong since the session shoud be local instead of remote
                // Assume some default value
                result = true;
            }
            else {
                result = session.isInitialized();
            }
        }
        else if (operation == Operation.incrementConflictCount) {
            if (session instanceof RemoteClientSession) {
                // Something is wrong since the session shoud be local instead of remote
                // Assume some default value
                result = 2;
            }
            else {
                result = session.incrementConflictCount();
            }
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, address);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        address = (JID) ExternalizableUtil.getInstance().readSerializable(in);
    }

    public String toString() {
        return super.toString() + " operation: " + operation + " address: " + address;
    }
}
