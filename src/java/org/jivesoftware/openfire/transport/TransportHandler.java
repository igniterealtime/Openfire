/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.transport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.openfire.Channel;
import org.jivesoftware.openfire.ChannelHandler;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

/**
 * Routes packets to the appropriate transport gateway or drops the packet.
 *
 * @author Iain Shigeoka
 */
public class TransportHandler extends BasicModule implements ChannelHandler {

    private static final Logger Log = LoggerFactory.getLogger(TransportHandler.class);

    private Map<String, Channel<Packet>> transports = new ConcurrentHashMap<>();

    private PacketDeliverer deliverer;

    public TransportHandler() {
        super("Transport handler");
    }

    public void addTransport(Channel<Packet> transport) {
        transports.put(transport.getName(), transport);
    }

    @Override
    public void process(Packet packet) throws UnauthorizedException, PacketException {
        boolean handled = false;
        String host = packet.getTo().getDomain();
        for (Channel<Packet> channel : transports.values()) {
            if (channel.getName().equalsIgnoreCase(host)) {
                channel.add(packet);
                handled = true;
            }
        }
        if (!handled) {
            JID recipient = packet.getTo();
            JID sender = packet.getFrom();
            packet.setError(PacketError.Condition.remote_server_timeout);
            packet.setFrom(recipient);
            packet.setTo(sender);
            try {
                deliverer.deliver(packet);
            }
            catch (PacketException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        deliverer = server.getPacketDeliverer();
    }
}
