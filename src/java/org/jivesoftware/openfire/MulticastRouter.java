/**
 * $RCSfile: $
 * $Revision: 2705 $
 * $Date: 2005-08-22 19:00:05 -0300 (Mon, 22 Aug 2005) $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire;

import org.dom4j.Element;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Router of packets with multiple recipients. Clients may send a single packet with multiple
 * recipients and the server will broadcast the packet to the target receipients. If recipients
 * belong to remote servers, then this server will discover if remote target servers support
 * multicast service. If a remote server supports the multicast service, a single packet will be
 * sent to the remote server. If a remote server doesn't the support multicast
 * processing, the local server sends a copy of the original stanza to each address.<p>
 *
 * The current implementation will only search up to the first level of nodes of remote servers
 * when trying to find out if remote servers have support for multicast service. It is assumed
 * that it is highly unlikely for servers to have a node in the second or third depth level
 * providing the multicast service. Servers should normally provide this service themselves or
 * at least as a first level node.
 *
 * This is an implementation of <a href=http://www.jabber.org/jeps/jep-0033.html>
 * JEP-0033: Extended Stanza Addressing</a>
 *
 * @author Matt Tucker
 */
public class MulticastRouter extends BasicModule implements ServerFeaturesProvider, IQResultListener {

    private static final String NAMESPACE = "http://jabber.org/protocol/address";

    private XMPPServer server;
    /**
     * Router used for delivering packets with multiple recipients.
     */
    private PacketRouter packetRouter;
    /**
     * Router used for discovering if remote servers support multicast service.
     */
    private IQRouter iqRouter;
    /**
     * Cache for a day discovered information of remote servers. The local server will try
     * to discover if remote servers support multicast service.
     */
    private Cache cache;
    /**
     * Packets that include recipients that belong to remote servers are not processed by
     * the main thread since extra work is required. This variable holds the list of packets
     * pending to be sent to remote servers. Note: key=domain, value=collection of packet
     * pending to be sent.
     */
    private Map<String, Collection<Packet>> remotePackets =
            new HashMap<String, Collection<Packet>>();
    /**
     * Keeps the list of nodes discovered in remote servers. This information is used
     * when discovering whether remote servers support multicast service or not.
     * Note: key=domain, value=list of nodes
     */
    private Map<String, Collection<String>> nodes = new ConcurrentHashMap<String, Collection<String>>();
    /**
     * Keeps an association of node and server where the node was discovered. This information
     * is used when discovering whether remote servers support multicast service or not.
     * Note: key=node, value=domain of remote server
     */
    private Map<String, String> roots = new ConcurrentHashMap<String, String>();

    public MulticastRouter() {
        super("Multicast Packet Router");

        String cacheName = "Multicast Service";
        cache = CacheFactory.createCache(cacheName);
    }

    public void route(Packet packet) {
        Set<String> remoteServers = new HashSet<String>();
        List<String> targets = new ArrayList<String>();
        Packet localBroadcast = packet.createCopy();
        Element addresses = getAddresses(localBroadcast);
        String localDomain = "@" + server.getServerInfo().getName();
        // Build the <addresses> element to be included for local users and identify
        // remote domains that should receive the packet too
        for (Iterator it=addresses.elementIterator("address");it.hasNext();) {
            Element address = (Element) it.next();
            // Skip addresses of type noreply since they don't have any address
            if (Type.noreply.toString().equals(address.attributeValue("type"))) {
                continue;
            }
            String jid = address.attributeValue("jid");
            // Only send to local users and if packet has not already been delivered
            if (jid.contains(localDomain) && address.attributeValue("delivered") == null) {
                targets.add(jid);
            }
            else if (!jid.contains(localDomain)) {
                remoteServers.add(new JID(jid).getDomain());
            }
            // Set as delivered
            address.addAttribute("delivered", "true");
            // Remove bcc addresses
            if (Type.bcc.toString().equals(address.attributeValue("type"))) {
                it.remove();
            }
        }
        // Send the packet to local target users
        for (String jid : targets) {
            localBroadcast.setTo(jid);
            packetRouter.route(localBroadcast);
        }

        // Keep a registry of packets that should be sent to remote domains.
        for (String domain : remoteServers) {
            boolean shouldDiscover = false;
            synchronized (domain.intern()) {
                Collection<Packet> packets = remotePackets.get(domain);
                if (packets == null) {
                    packets = new ArrayList<Packet>();
                    remotePackets.put(domain, packets);
                    shouldDiscover = true;
                }
                // Add that this packet should be sent to the requested remote server
                packets.add(packet);
            }
            if (shouldDiscover) {
                // First time a packet is sent to this remote server so start the extra work
                // of discovering if remote server supports multicast service and actually send
                // the packet to the remote server
                sendToRemoteEntity(domain);
            }
        }
        // TODO Add thread that checks every 5 minutes if packets to remote servers were not
        // TODO sent because no disco response was received. So assume that remote server does
        // TODO not support JEP-33 and send pending packets
    }

    /**
     * Returns the Element that contains the multiple recipients.
     *
     * @param packet packet containing the multiple recipients.
     * @return the Element that contains the multiple recipients.
     */
    private Element getAddresses(Packet packet) {
        if (packet instanceof IQ) {
            return ((IQ) packet).getChildElement().element("addresses");
        }
        else {
            return packet.getElement().element("addresses");
        }
    }

    /**
     * Sends pending packets of the requested domain but first try to discover if remote server
     * supports multicast service. If we already have cached information about the requested
     * domain then just deliver the packet.
     *
     * @param domain the domain that has pending packets to be sent.
     */
    private void sendToRemoteEntity(String domain) {
        // Check if there is cached information about the requested domain
        String multicastService = (String) cache.get(domain);
        if (multicastService != null) {
            sendToRemoteServer(domain, multicastService);
        }
        else {
            // No cached information was found so discover if remote server
            // supports JEP-33 (Extended Stanza Addressing). The reply to the disco
            // request is going to be process in #receivedAnswer(IQ packet)
            IQ iq = new IQ(IQ.Type.get);
            iq.setFrom(server.getServerInfo().getName());
            iq.setTo(domain);
            iq.setChildElement("query", "http://jabber.org/protocol/disco#info");
            // Indicate that we are searching for info of the specified domain
            nodes.put(domain, new CopyOnWriteArrayList<String>());
            // Send the disco#info request to the remote server or component. The reply will be
            // processed by the IQResultListener (interface that this class implements)
            iqRouter.addIQResultListener(iq.getID(), this);
            iqRouter.route(iq);
        }
    }

    /**
     * Actually sends pending packets of the specified domain using the discovered multicast
     * service address. If remote server supports multicast service then a copy of the
     * orignal will be sent to the remote server. However, if no multicast service was found
     * then the local server sends a copy of the original stanza to each address.
     *
     * @param domain domain of the remote server with pending packets.
     * @param multicastService address of the discovered multicast service.
     */
    private void sendToRemoteServer(String domain, String multicastService) {
        Collection<Packet> packets = null;
        // Get the packets to send to the remote entity
        synchronized (domain.intern()) {
            packets = remotePackets.remove(domain);
        }

        if (multicastService != null && multicastService.trim().length() > 0) {
            // Remote server has a multicast service so send pending packets to the
            // multicast service
            for (Packet packet : packets) {
                Element addresses = getAddresses(packet);
                for (Iterator it=addresses.elementIterator("address");it.hasNext();) {
                    Element address = (Element) it.next();
                    String jid = address.attributeValue("jid");
                    if (!jid.contains("@"+domain)) {
                        if (Type.bcc.toString().equals(address.attributeValue("type"))) {
                            it.remove();
                        }
                        else {
                            address.addAttribute("delivered", "true");
                        }
                    }
                }
                // Set that the target of the packet is the multicast service
                packet.setTo(multicastService);
                // Send the packet to the remote entity
                packetRouter.route(packet);
            }
        }
        else {
            // Remote server does not have a multicast service so send pending packets
            // to each address
            for (Packet packet : packets) {
                Element addresses = getAddresses(packet);
                List<String> targets = new ArrayList<String>();

                for (Iterator it=addresses.elementIterator("address");it.hasNext();) {
                    Element address = (Element) it.next();
                    String jid = address.attributeValue("jid");
                    // Keep a list of the remote users that are going to receive the packet
                    if (jid.contains("@"+domain)) {
                        targets.add(jid);
                    }
                    // Set as delivered
                    address.addAttribute("delivered", "true");
                    // Remove bcc addresses
                    if (Type.bcc.toString().equals(address.attributeValue("type"))) {
                        it.remove();
                    }
                }

                // Send the packet to each remote user
                for (String jid : targets) {
                    packet.setTo(jid);
                    packetRouter.route(packet);
                }
            }
        }
    }

    public void receivedAnswer(IQ packet) {
        // Look for the root node being discovered
        String domain = packet.getFrom().toString();
        boolean isRoot = true;
        if (!nodes.containsKey(domain)) {
            domain = roots.get(domain);
            isRoot = false;
        }

        // Check if this is a disco#info response
        if ("http://jabber.org/protocol/disco#info"
                .equals(packet.getChildElement().getNamespaceURI())) {

            // Check if the node supports JEP-33
            boolean supports = false;
            for (Iterator it = packet.getChildElement().elementIterator("feature"); it.hasNext();) {
                if (NAMESPACE.equals(((Element)it.next()).attributeValue("var"))) {
                    supports = true;
                    break;
                }
            }

            if (supports) {
                // JEP-33 is supported by the entity
                Collection<String> items = nodes.remove(domain);
                for (String item : items) {
                    roots.remove(item);
                }
                String multicastService = packet.getFrom().toString();
                cache.put(domain, multicastService);
                sendToRemoteServer(domain, multicastService);
            }
            else {
                if (isRoot && IQ.Type.error != packet.getType()) {
                    // Discover node items with the hope that a sub-item supports JEP-33
                    IQ iq = new IQ(IQ.Type.get);
                    iq.setFrom(server.getServerInfo().getName());
                    iq.setTo(packet.getFrom());
                    iq.setChildElement("query", "http://jabber.org/protocol/disco#items");
                    // Send the disco#items request to the remote server or component. The reply will be
                    // processed by the IQResultListener (interface that this class implements)
                    iqRouter.addIQResultListener(iq.getID(), this);
                    iqRouter.route(iq);
                }
                else if (!isRoot) {
                    // Process the disco#info response of an item that does not support JEP-33
                    roots.remove(packet.getFrom().toString());
                    Collection<String> items = nodes.get(domain);
                    if (items != null) {
                        items.remove(packet.getFrom().toString());
                        if (items.isEmpty()) {
                            nodes.remove(domain);
                            cache.put(domain, "");
                            sendToRemoteServer(domain, "");
                        }
                    }
                }
                else {
                    // Root domain does not support disco#info
                    nodes.remove(domain);
                    cache.put(domain, "");
                    sendToRemoteServer(domain, "");
                }
            }

        }
        else {
            // This is a disco#items response
            Collection<Element> items = packet.getChildElement().elements("item");

            if (IQ.Type.error == packet.getType() || items.isEmpty()) {
                // Root domain does not support disco#items
                nodes.remove(domain);
                cache.put(domain, "");
                sendToRemoteServer(domain, "");
            }
            else {
                // Keep the list of items found in the requested domain
                List<String> jids = new ArrayList<String>();
                for (Element item : items) {
                    String jid = item.attributeValue("jid");
                    jids.add(jid);
                    // Add that this item was found for the following domain
                    roots.put(jid, domain);
                }
                nodes.put(domain, new CopyOnWriteArrayList<String>(jids));

                // Send disco#info to each discovered item
                for (Element item : items) {
                    // Discover if remote server supports JEP-33 (Extended Stanza Addressing)
                    IQ iq = new IQ(IQ.Type.get);
                    iq.setFrom(server.getServerInfo().getName());
                    iq.setTo(item.attributeValue("jid"));
                    Element child = iq.setChildElement("query", "http://jabber.org/protocol/disco#info");
                    if (item.attributeValue("node") != null) {
                        child.addAttribute("node", item.attributeValue("node"));
                    }
                    // Send the disco#info request to the discovered item. The reply will be
                    // processed by the IQResultListener (interface that this class implements)
                    iqRouter.addIQResultListener(iq.getID(), this);
                    iqRouter.route(iq);
                }
            }
        }
    }

    public Iterator<String> getFeatures() {
        ArrayList<String> features = new ArrayList<String>();
        features.add(NAMESPACE);
        return features.iterator();
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        this.packetRouter = server.getPacketRouter();
        this.iqRouter = server.getIQRouter();
    }

    /**
     * Enumarion of the possible semantics of a particular address.
     */
    private enum Type {
        /**
         * These addressees should receive 'blind carbon copies' of the stanza. This means that
         * the server MUST remove these addresses before the stanza is delivered to anyone other
         * than the given bcc addressee or the multicast service of the bcc addressee.
         */
        bcc,
        /**
         * These addressees are the secondary recipients of the stanza.
         */
        cc,
        /**
         * This address type contains no actual address information. Instead, it means that the
         * receiver SHOULD NOT reply to the message. This is useful when broadcasting messages
         * to many receivers.
         */
        noreply,
        /**
         * This is the JID of a Multi-User Chat room to which responses should be sent. When a
         * user wants to reply to this stanza, the client SHOULD join this room first. Clients
         * SHOULD respect this request unless an explicit override occurs. There MAY be more than
         * one replyto or replyroom on a stanza, in which case the reply stanza MUST be routed
         * to all of the addresses.
         */
        replyroom,
        /**
         * This is the address to which all replies are requested to be sent. Clients SHOULD
         * respect this request unless an explicit override occurs. There MAY be more than one
         * replyto or replyroom on a stanza, in which case the reply stanza MUST be routed to all
         * of the addresses.
         */
        replyto,
        /**
         * These addressees are the primary recipients of the stanza.
         */
        to;
    }
}
