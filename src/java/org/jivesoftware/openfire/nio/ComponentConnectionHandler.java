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

package org.jivesoftware.openfire.nio;

import org.apache.mina.core.session.IoSession;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ComponentStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should
 * be created and how to build and configure a {@link NIOConnection}.
 *
 * @author Gaston Dombiak
 */
public class ComponentConnectionHandler extends ConnectionHandler {

    public ComponentConnectionHandler(ConnectionConfiguration configuration) {
        super(configuration);
    }

    @Override
    NIOConnection createNIOConnection(IoSession session) {
        return new NIOConnection(session, XMPPServer.getInstance().getPacketDeliverer(), configuration );
    }

    @Override
    StanzaHandler createStanzaHandler(NIOConnection connection) {
        return new ComponentStanzaHandler(XMPPServer.getInstance().getPacketRouter(), connection);
    }

    @Override
    int getMaxIdleTime() {
        return JiveGlobals.getIntProperty("xmpp.component.idle", 6 * 60 * 1000) / 1000;
    }
}
