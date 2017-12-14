/*
 * Copyright (C) 2005-2015 Jive Software. All rights reserved.
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

import org.jivesoftware.openfire.OfflineMessageStrategy;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Fallback method used by {@link org.jivesoftware.openfire.nio.NIOConnection} when a 
 * connection fails to send a {@link Packet} (likely because it was closed). Message packets
 * will be stored offline for later retrieval. IQ and Presence packets are dropped.<p>
 *
 * @author Tom Evans
 */
public class OfflinePacketDeliverer implements PacketDeliverer {

    private static final Logger Log = LoggerFactory.getLogger(OfflinePacketDeliverer.class);

    private OfflineMessageStrategy messageStrategy;

    public OfflinePacketDeliverer() {
        this.messageStrategy = XMPPServer.getInstance().getOfflineMessageStrategy();
    }

    @Override
    public void deliver(Packet packet) throws UnauthorizedException, PacketException {
        
        if (packet instanceof Message) {
            messageStrategy.storeOffline((Message) packet);
        }
        else if (packet instanceof Presence) {
            // presence packets are dropped silently
        }
        else if (packet instanceof IQ) {
            // IQ packets are logged before being dropped
            Log.warn(LocaleUtils.getLocalizedString("admin.error.routing") + "\n" + packet.toString());
        }
    }

}
