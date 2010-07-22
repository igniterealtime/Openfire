/**
 * $Revision $
 * $Date $
 *
 * Copyright (C) 2005-2010 Jive Software. All rights reserved.
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

package org.jinglenodes;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.jnodes.RelayChannel;
import org.xmpp.jnodes.smack.JingleChannelIQ;
import org.xmpp.jnodes.nio.LocalIPResolver;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

class JingleNodesComponent implements Component {
    private static final Logger Log = LoggerFactory.getLogger(JingleNodesComponent.class);

    private final ComponentManager componentManager;
    private static final String UDP = "udp";
    private static final String PROTOCOL = "protocol";
    private static final String HOST = "host";
    private static final String LOCAL_PORT = "localport";
    private static final String REMOTE_PORT = "remoteport";

    private final JingleNodesPlugin plugin;

    public JingleNodesComponent(final ComponentManager componentManager, final JingleNodesPlugin plugin) {
        this.componentManager = componentManager;
        this.plugin = plugin;
    }

    public String getName() {
        return "JingleRelayNode";
    }

    public String getDescription() {
        return "Jingle Relay Service";
    }

    public void processPacket(Packet packet) {
    	if (Log.isDebugEnabled()) {
    		Log.debug("Processing packet: {}", packet.toXML());
    	}
        if (packet instanceof IQ) {
            // Handle disco packets
            IQ iq = (IQ) packet;
            // Ignore IQs of type ERROR or RESULT
            if (IQ.Type.error == iq.getType() || IQ.Type.result == iq.getType()) {
                return;
            }
            processIQ(iq);
        }
    }

    private void processIQ(final IQ iq) {
        final IQ reply = IQ.createResultIQ(iq);
        final Element element = iq.getChildElement();

        if (element != null) {
            final String namespace = element.getNamespaceURI();

            if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
                if (iq.getTo().getNode() == null) {
                    // Return service identity and features
                    Element identity = element.addElement("identity");
                    identity.addAttribute("category", "component");
                    identity.addAttribute("type", "relay");
                    identity.addAttribute("name", getName());
                    element.addElement("feature").addAttribute("var", "http://jabber.org/protocol/disco#info");
                    element.addElement("feature").addAttribute("var", JingleChannelIQ.NAMESPACE);
                }
            } else if (JingleChannelIQ.NAME.equals(element.getName()) && JingleChannelIQ.NAMESPACE.equals(namespace) && UDP.equals(element.attributeValue(PROTOCOL))) {
                final Element childElement = iq.getChildElement().createCopy();
                final RelayChannel channel = plugin.createRelayChannel();

                if (channel != null) {
                    childElement.addAttribute(HOST, LocalIPResolver.getLocalIP());
                    childElement.addAttribute(LOCAL_PORT, Integer.toString(channel.getPortA()));
                    childElement.addAttribute(REMOTE_PORT, Integer.toString(channel.getPortB()));
                    reply.setChildElement(childElement);
                } else {
                    reply.setError(PacketError.Condition.internal_server_error);
                }
            } else {
                reply.setError(PacketError.Condition.feature_not_implemented);
            }
        } else {
            reply.setError(PacketError.Condition.feature_not_implemented);
        }

        try {
            componentManager.sendPacket(this, reply);
            if (Log.isDebugEnabled()) {
            	Log.debug("Packet sent: {}", reply.toXML());
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    public void initialize(final JID jid, final ComponentManager componentManager) throws ComponentException {

    }

    public void start() {

    }

    public void shutdown() {

    }
}
