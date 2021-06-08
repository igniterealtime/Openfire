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
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ComponentStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should
 * be created and how to build and configure a {@link NIOConnection}.
 *
 * @author Gaston Dombiak
 */
public class ComponentConnectionHandler extends ConnectionHandler {

    /**
     * Enable / disable backup delivery of stanzas to the XMPP server itself when a stanza failed to be delivered on a
     * component connection. When disabled, stanzas that can not be delivered on the connection are discarded.
     */
    public static final SystemProperty<Boolean> BACKUP_PACKET_DELIVERY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.component.backup-packet-delivery.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public ComponentConnectionHandler(ConnectionConfiguration configuration) {
        super(configuration);
    }

    @Override
    NIOConnection createNIOConnection(IoSession session) {
        final PacketDeliverer backupDeliverer = BACKUP_PACKET_DELIVERY_ENABLED.getValue() ? XMPPServer.getInstance().getPacketDeliverer() : null;
        return new NIOConnection(session, backupDeliverer, configuration);
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
