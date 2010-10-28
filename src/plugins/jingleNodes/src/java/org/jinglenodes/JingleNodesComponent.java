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
import org.dom4j.DocumentHelper;
import org.jivesoftware.openfire.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.AbstractComponent;
import org.xmpp.jnodes.RelayChannel;
import org.xmpp.jnodes.RelayPublicMask;
import org.xmpp.jnodes.nio.LocalIPResolver;
import org.xmpp.jnodes.smack.JingleChannelIQ;
import org.xmpp.jnodes.smack.JingleTrackerIQ;
import org.xmpp.jnodes.smack.TrackerEntry;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.List;
import java.util.ArrayList;

class JingleNodesComponent extends AbstractComponent {
    private static final Logger Log = LoggerFactory.getLogger(JingleNodesComponent.class);

    private static final String UDP = "udp";
    private static final String PROTOCOL = "protocol";
    private static final String HOST = "host";
    private static final String LOCAL_PORT = "localport";
    private static final String REMOTE_PORT = "remoteport";

    private final JingleNodesPlugin plugin;

    public JingleNodesComponent(final JingleNodesPlugin plugin) {
        this.plugin = plugin;
    }

    public String getName() {
        return "JingleRelayNode";
    }

    public String getDescription() {
        return "Jingle Relay Service";
    }

    @Override
    protected String[] discoInfoFeatureNamespaces() {
        return new String[]{JingleChannelIQ.NAMESPACE, JingleTrackerIQ.NAMESPACE};
    }

    @Override
    protected String discoInfoIdentityCategoryType() {
        return "relay";
    }

    @Override
    protected IQ handleIQGet(IQ iq) throws Exception {
        final IQ reply = IQ.createResultIQ(iq);

        final Element element = iq.getChildElement();
        final String namespace = element.getNamespaceURI();

        if (JingleChannelIQ.NAME.equals(element.getName()) && JingleChannelIQ.NAMESPACE.equals(namespace)
                && UDP.equals(element.attributeValue(PROTOCOL))) {
            final Element childElement = iq.getChildElement().createCopy();
            final RelayChannel channel = plugin.createRelayChannel();

            if (channel != null) {

                childElement.addAttribute(HOST, LocalIPResolver.getLocalIP());
                childElement.addAttribute(LOCAL_PORT, Integer.toString(channel.getPortA()));
                childElement.addAttribute(REMOTE_PORT, Integer.toString(channel.getPortB()));

//                    final RelayPublicMask rpm = new RelayPublicMask(channel);
//                    rpm.discover("stun.xten.com", 3478);
//                    childElement.addAttribute(HOST, rpm.getAddressA().getAddress().getHostAddress());
//                    childElement.addAttribute(LOCAL_PORT, Integer.toString(rpm.getAddressA().getPort()));
//                    childElement.addAttribute(REMOTE_PORT, Integer.toString(rpm.getAddressB().getPort()));


                reply.setChildElement(childElement);

                Log.debug("Created relay channel {}:{}, {}:{}, {}:{}", new Object[]{HOST,
                        LocalIPResolver.getLocalIP(), LOCAL_PORT, Integer.toString(channel.getPortA()), REMOTE_PORT,
                        Integer.toString(channel.getPortB())});

            } else {
                reply.setError(PacketError.Condition.internal_server_error);
            }
            return reply;
        } else if (JingleTrackerIQ.NAME.equals(element.getName()) && JingleTrackerIQ.NAMESPACE.equals(namespace)) {

            final List<TrackerEntry> entries = new ArrayList<TrackerEntry>();
            entries.add(new TrackerEntry(TrackerEntry.Type.relay, TrackerEntry.Policy._roster, plugin.getServiceName() + "." + getDomain(), UDP));

            final String elements = getChildElementXML(entries);

            final Element e = DocumentHelper.parseText(elements).getRootElement();

            reply.setChildElement(e);

            return reply;
        }


        return null; // feature not implemented.
    }

    public String getChildElementXML(final List<TrackerEntry> entries) {
        final StringBuilder str = new StringBuilder();

        str.append("<").append(JingleTrackerIQ.NAME).append(" xmlns='").append(JingleTrackerIQ.NAMESPACE).append("'>");
        for (final TrackerEntry entry : entries) {
            str.append("<").append(entry.getType().toString());
            str.append(" policy='").append(entry.getPolicy().toString()).append("'");
            str.append(" address='").append(entry.getJid()).append("'");
            str.append(" protocol='").append(entry.getProtocol()).append("'");
            if (entry.isVerified()) {
                str.append(" verified='").append(entry.isVerified()).append("'");
            }
            str.append("/>");
        }
        str.append("</").append(JingleTrackerIQ.NAME).append(">");

        return str.toString();
    }

    @Override
    public String getDomain() {
        return XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    }
}
