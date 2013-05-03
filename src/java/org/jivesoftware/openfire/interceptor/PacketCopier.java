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

package org.jivesoftware.openfire.interceptor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.dom4j.Element;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.component.ComponentEventListener;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Packet interceptor that notifies of packets activity to components that previously
 * subscribed to the notificator. Notifications to components will be made using
 * a Message sent to the component itself. The Message will include an extension that will
 * contain the intercepted packet as well as extra information such us <tt>incoming</tt>
 * and <tt>processed</tt>.
 *
 * @author Gaston Dombiak
 */
public class PacketCopier implements PacketInterceptor, ComponentEventListener {

	private static final Logger Log = LoggerFactory.getLogger(PacketCopier.class);

    private final static PacketCopier instance = new PacketCopier();


    private Map<String, Subscription> subscribers = new ConcurrentHashMap<String, Subscription>();
    private String serverName;
    private RoutingTable routingTable;

    /**
     * Timer to save queued logs to the XML file.
     */
    private ProcessPacketsTask packetsTask;

    /**
     * Queue that holds the audited packets that will be later saved to an XML file.
     */
    private BlockingQueue<InterceptedPacket> packetQueue = new LinkedBlockingQueue<InterceptedPacket>(10000);

    /**
     * Returns unique instance of this class.
     *
     * @return unique instance of this class.
     */
    public static PacketCopier getInstance() {
        return instance;
    }


    private PacketCopier() {
        // Add the new instance as a listener of component events. We need to react when
        // a component is no longer valid
        InternalComponentManager.getInstance().addListener(this);
        XMPPServer server = XMPPServer.getInstance();
        serverName = server.getServerInfo().getXMPPDomain();
        routingTable = server.getRoutingTable();

        // Add new instance to the PacketInterceptors list
        InterceptorManager.getInstance().addInterceptor(this);

        // Create a new task and schedule it with the new timeout
        packetsTask = new ProcessPacketsTask();
        TaskEngine.getInstance().schedule(packetsTask, 5000, 5000);
    }

    /**
     * Creates new subscription for the specified component with the specified settings.
     *
     * @param componentJID the address of the component connected to the server.
     * @param iqEnabled true if interested in IQ packets of any type.
     * @param messageEnabled true if interested in Message packets.
     * @param presenceEnabled true if interested in Presence packets.
     * @param incoming true if interested in incoming traffic. false means outgoing.
     * @param processed true if want to be notified after packets were processed.
     */
    public void addSubscriber(JID componentJID, boolean iqEnabled, boolean messageEnabled, boolean presenceEnabled,
                              boolean incoming, boolean processed) {
        subscribers.put(componentJID.toString(),
                new Subscription(iqEnabled, messageEnabled, presenceEnabled, incoming, processed));
    }

    /**
     * Removes the subscription of the specified component.
     *
     * @param componentJID the address of the component connected to the server.
     */
    public void removeSubscriber(JID componentJID) {
        subscribers.remove(componentJID.toString());
    }

    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
            throws PacketRejectedException {
        // Queue intercepted packet only if there are subscribers interested
        if (!subscribers.isEmpty()) {
            boolean queue = false;
            Class packetClass = packet.getClass();
            for (Subscription subscription : subscribers.values()) {
                if (subscription.isPresenceEnabled() && packetClass == Presence.class) {
                    queue = true;
                }
                else if (subscription.isMessageEnabled() && packetClass == Message.class) {
                    queue = true;
                }
                else if (subscription.isIQEnabled() && packetClass == IQ.class) {
                    queue = true;
                }
            }
            if (queue) {
                // Queue packet with extra information and let the background thread process it
                packetQueue.add(new InterceptedPacket(packet, incoming, processed));
            }
        }
    }

    public void componentInfoReceived(IQ iq) {
        //Ignore
    }

    public void componentRegistered(JID componentJID) {
        //Ignore
    }

    public void componentUnregistered(JID componentJID) {
        //Remove component from the list of subscribers (if subscribed)
        removeSubscriber(componentJID);
    }

    private void processPackets() {
        List<InterceptedPacket> packets = new ArrayList<InterceptedPacket>(packetQueue.size());
        packetQueue.drainTo(packets);
        for (InterceptedPacket interceptedPacket : packets) {
            for (Map.Entry<String, Subscription> entry : subscribers.entrySet()) {
                boolean notify = false;
                String componentJID = entry.getKey();
                Subscription subscription = entry.getValue();

                if (subscription.isIncoming() == interceptedPacket.isIncoming() &&
                        subscription.isProcessed() == interceptedPacket.isProcessed()) {
                    Class packetClass = interceptedPacket.getPacketClass();
                    if (subscription.isPresenceEnabled() && packetClass == Presence.class) {
                        notify = true;
                    }
                    else if (subscription.isMessageEnabled() && packetClass == Message.class) {
                        notify = true;
                    }
                    else if (subscription.isIQEnabled() && packetClass == IQ.class) {
                        notify = true;
                    }
                }

                if (notify) {
                    try {
                        Message message = new Message();
                        message.setFrom(serverName);
                        message.setTo(componentJID);
                        Element childElement = message.addChildElement("copy",
                                "http://jabber.org/protocol/packet#event");
                        childElement.addAttribute("incoming", subscription.isIncoming() ? "true" : "false");
                        childElement.addAttribute("processed", subscription.isProcessed() ? "true" : "false");
                        childElement.addAttribute("date", XMPPDateTimeFormat.formatOld(interceptedPacket.getCreationDate()));
                        childElement.add(interceptedPacket.getElement().createCopy());
                        // Send message notification to subscribed component
                        routingTable.routePacket(message.getTo(), message, true);
                    }
                    catch (Exception e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                }
            }
        }
    }

    private class ProcessPacketsTask extends TimerTask {
        @Override
		public void run() {
            try {
                // Notify components of intercepted packets
                processPackets();
            }
            catch (Throwable e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    private static class Subscription {
        private boolean presenceEnabled;
        private boolean messageEnabled;
        private boolean iqEnabled;
        private boolean incoming;
        private boolean processed;

        /**
         * Creates a new subscription for the specified Component with the
         * specified configuration.
         *
         * @param iqEnabled true if interested in IQ packets of any type.
         * @param messageEnabled true if interested in Message packets.
         * @param presenceEnabled true if interested in Presence packets.
         * @param incoming true if interested in incoming traffic. false means outgoing.
         * @param processed true if want to be notified after packets were processed.
         */
        public Subscription(boolean iqEnabled, boolean messageEnabled,
                boolean presenceEnabled, boolean incoming, boolean processed) {
            this.incoming = incoming;
            this.iqEnabled = iqEnabled;
            this.messageEnabled = messageEnabled;
            this.presenceEnabled = presenceEnabled;
            this.processed = processed;
        }

        /**
         * Returns true if the component is interested in receiving notifications
         * of intercepted IQ packets.
         *
         * @return true if the component is interested in receiving notifications
         * of intercepted IQ packets.
         */
        public boolean isIQEnabled() {
            return iqEnabled;
        }

        /**
         * Returns true if the component is interested in receiving notifications
         * of intercepted Message packets.
         *
         * @return true if the component is interested in receiving notifications
         * of intercepted Message packets.
         */
        public boolean isMessageEnabled() {
            return messageEnabled;
        }

        /**
         * Returns true if the component is interested in receiving notifications
         * of intercepted Presence packets.
         *
         * @return true if the component is interested in receiving notifications
         * of intercepted Presence packets.
         */
        public boolean isPresenceEnabled() {
            return presenceEnabled;
        }

        /**
         * Returns true if the component wants to be notified of incoming traffic. A false
         * value means that the component is interested in outgoing traffic.
         *
         * @return true if interested in incoming traffic. false means outgoing.
         */
        public boolean isIncoming() {
            return incoming;
        }

        /**
         * Returns true if the component wants to be notified of after packets were
         * processed. Processed has different meaning depending if the packet is incoming
         * or outgoing. An incoming packet that was processed means that the server
         * routed the packet to the recipient and nothing else should be done with the packet.
         * However, an outgoing packet that was processed means that the packet was already
         * sent to the target entity. 
         *
         * @return true if interested in incoming traffic. false means outgoing.
         */
        public boolean isProcessed() {
            return processed;
        }
    }

    private static class InterceptedPacket {
        private Element element;
        private Class packetClass;
        private Date creationDate;
        private boolean incoming;
        private boolean processed;

        public InterceptedPacket(Packet packet, boolean incoming, boolean processed) {
            packetClass = packet.getClass();
            this.element = packet.getElement();
            this.incoming = incoming;
            this.processed = processed;
            creationDate = new Date();
        }


        public Class getPacketClass() {
            return packetClass;
        }

        public Date getCreationDate() {
            return creationDate;
        }

        public Element getElement() {
            return element;
        }

        public boolean isIncoming() {
            return incoming;
        }

        public boolean isProcessed() {
            return processed;
        }
    }
}
