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

import org.jivesoftware.openfire.session.*;
import org.xmpp.packet.JID;

/**
 * Locator of sessions that know how to talk to Coherence cluster nodes.
 *
 * @author Gaston Dombiak
 */
public class RemoteSessionLocator implements org.jivesoftware.openfire.session.RemoteSessionLocator {

    // TODO Keep a cache for a brief moment so we can reuse same instances (that use their own cache)

    public ClientSession getClientSession(byte[] nodeID, JID address) {
        return new RemoteClientSession(nodeID, address);
    }

    public ComponentSession getComponentSession(byte[] nodeID, JID address) {
        return new RemoteComponentSession(nodeID, address);
    }

    public ConnectionMultiplexerSession getConnectionMultiplexerSession(byte[] nodeID, JID address) {
        return new RemoteConnectionMultiplexerSession(nodeID, address);
    }

    public IncomingServerSession getIncomingServerSession(byte[] nodeID, String streamID) {
        return new RemoteIncomingServerSession(nodeID, streamID);
    }

    public OutgoingServerSession getOutgoingServerSession(byte[] nodeID, JID address) {
        return new RemoteOutgoingServerSession(nodeID, address);
    }
}
