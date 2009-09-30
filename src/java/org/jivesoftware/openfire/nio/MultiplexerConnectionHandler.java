/**
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

package org.jivesoftware.openfire.nio;

import org.apache.mina.common.IoSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.multiplex.MultiplexerPacketDeliverer;
import org.jivesoftware.openfire.net.MultiplexerStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;

/**
 * ConnectionHandler that knows which subclass of {@link org.jivesoftware.openfire.net.StanzaHandler} should
 * be created and how to build and configure a {@link org.jivesoftware.openfire.nio.NIOConnection}.
 *
 * @author Gaston Dombiak
 */
public class MultiplexerConnectionHandler extends ConnectionHandler {

    public MultiplexerConnectionHandler(String serverName) {
        super(serverName);
    }

    NIOConnection createNIOConnection(IoSession session) {
        return new NIOConnection(session, new MultiplexerPacketDeliverer());
    }

    StanzaHandler createStanzaHandler(NIOConnection connection) {
        return new MultiplexerStanzaHandler(XMPPServer.getInstance().getPacketRouter(), serverName, connection);
    }

    int getMaxIdleTime() {
        return JiveGlobals.getIntProperty("xmpp.multiplex.idle", 5 * 60 * 1000) / 1000;
    }
}
