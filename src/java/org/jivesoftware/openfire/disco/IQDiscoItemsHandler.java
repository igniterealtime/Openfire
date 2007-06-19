/**
 * $RCSfile$
 * $Revision: 1701 $
 * $Date: 2005-07-26 02:23:45 -0300 (Tue, 26 Jul 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.disco;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IQDiscoItemsHandler is responsible for handling disco#items requests. This class holds a map with
 * the main entities and the associated DiscoItemsProvider. We are considering the host of the
 * recipient JIDs as main entities. It's the DiscoItemsProvider responsibility to provide the items
 * associated with the JID's name together with any possible requested node.<p>
 * <p/>
 * For example, let's have in the entities map the following entries: "localhost" and
 * "conference.localhost". Associated with each entry we have different DiscoItemsProvider. Now we
 * receive a disco#items request for the following JID: "room@conference.localhost" which is a disco
 * request for a MUC room. So IQDiscoItemsHandler will look for the DiscoItemsProvider associated
 * with the JID's host which in this case is "conference.localhost". Once we have located the
 * provider we will delegate to the provider the responsibility to provide the items specific to
 * the JID's name which in this case is "room". Depending on the implementation, the items could be
 * the list of existing occupants if that information is publicly available. Finally, after we have
 * collected all the items provided by the provider we will add them to the reply. On the other
 * hand, if no provider was found or the provider has no information for the requested name/node
 * then a not-found error will be returned.<p>
 * <p/>
 * Publishing of client items is still not supported.
 *
 * @author Gaston Dombiak
 */
public class IQDiscoItemsHandler extends IQHandler implements ServerFeaturesProvider {

    private Map<String,DiscoItemsProvider> entities = new HashMap<String,DiscoItemsProvider>();
    private List<Element> serverItems = new ArrayList<Element>();
    private Map<String, DiscoItemsProvider> serverNodeProviders = new ConcurrentHashMap<String, DiscoItemsProvider>();
    private IQHandlerInfo info;
    private IQDiscoInfoHandler infoHandler;

    public IQDiscoItemsHandler() {
        super("XMPP Disco Items Handler");
        info = new IQHandlerInfo("query", "http://jabber.org/protocol/disco#items");
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public IQ handleIQ(IQ packet) {
        // Create a copy of the sent pack that will be used as the reply
        // we only need to add the requested items to the reply if any otherwise add 
        // a not found error
        IQ reply = IQ.createResultIQ(packet);
        
        // TODO Implement publishing client items
        if (IQ.Type.set == packet.getType()) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.feature_not_implemented);
            return reply;
        }

        // Look for a DiscoItemsProvider associated with the requested entity.
        // We consider the host of the recipient JID of the packet as the entity. It's the 
        // DiscoItemsProvider responsibility to provide the items associated with the JID's name  
        // together with any possible requested node.  
        DiscoItemsProvider itemsProvider = getProvider(packet.getTo() == null ?
                XMPPServer.getInstance().getServerInfo().getName() : packet.getTo().getDomain());
        if (itemsProvider != null) {
            // Get the JID's name
            String name = packet.getTo() == null ? null : packet.getTo().getNode();
            if (name == null || name.trim().length() == 0) {
                name = null;
            }
            // Get the requested node
            Element iq = packet.getChildElement();
            String node = iq.attributeValue("node");

            // Check if we have items associated with the requested name and node
            Iterator<Element> itemsItr = itemsProvider.getItems(name, node, packet.getFrom());
            if (itemsItr != null) {
                reply.setChildElement(iq.createCopy());
                Element queryElement = reply.getChildElement();

                // Add to the reply all the items provided by the DiscoItemsProvider
                Element item;
                while (itemsItr.hasNext()) {
                    item = itemsItr.next();
                    item.setQName(new QName(item.getName(), queryElement.getNamespace()));
                    queryElement.add(item.createCopy());
                }
            }
            else {
                // If the DiscoItemsProvider has no items for the requested name and node 
                // then answer a not found error
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(PacketError.Condition.item_not_found);
            }
        }
        else {
            // If we didn't find a DiscoItemsProvider then answer a not found error
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.item_not_found);
        }

        return reply;
    }

    /**
     * Returns the DiscoItemsProvider responsible for providing the items related to a given entity
     * or null if none was found.
     *
     * @param name the name of the identity.
     * @return the DiscoItemsProvider responsible for providing the items related to a given entity
     *         or null if none was found.
     */
    private DiscoItemsProvider getProvider(String name) {
        return entities.get(name);
    }

    /**
     * Sets that a given DiscoItemsProvider will provide the items related to a given entity. This
     * message must be used when new modules (e.g. MUC) are implemented and need to provide
     * the items related to them.
     *
     * @param name     the name of the entity.
     * @param provider the DiscoItemsProvider that will provide the entity's items.
     */
    protected void setProvider(String name, DiscoItemsProvider provider) {
        entities.put(name, provider);
    }

    /**
     * Removes the DiscoItemsProvider related to a given entity.
     *
     * @param name the name of the entity.
     */
    protected void removeProvider(String name) {
        entities.remove(name);
    }

    /**
     * Adds the items provided by the new service that implements the ServerItemsProvider
     * interface. This information will be used whenever a disco for items is made against
     * the server (i.e. the packet's target is the server).
     * Example of item is: &lt;item jid='conference.localhost' name='Public chatrooms'/&gt;
     *
     * @param provider the ServerItemsProvider that provides new server items.
     */
    public void addServerItemsProvider(ServerItemsProvider provider) {
        DiscoServerItem discoItem;
        Iterator<DiscoServerItem> items = provider.getItems();
        if (items == null) {
            // Do nothing
            return;
        }
        while (items.hasNext()) {
            discoItem = items.next();
            // Add the element to the list of items related to the server
            addComponentItem(discoItem.getJID(), discoItem.getNode(), discoItem.getName());

            // Add the new item as a valid entity that could receive info and items disco requests
            String host = new JID(discoItem.getJID()).getDomain();
            infoHandler.setProvider(host, discoItem.getDiscoInfoProvider());
            setProvider(host, discoItem.getDiscoItemsProvider());
        }
    }

    /**
     * Removes the provided items as a service of the service.
     *
     * @param provider The provider that is being removed.
     */
    public void removeServerItemsProvider(ServerItemsProvider provider) {
        DiscoServerItem discoItem;
        Iterator<DiscoServerItem> items = provider.getItems();
        if (items == null) {
            // Do nothing
            return;
        }
        while (items.hasNext()) {
            discoItem = items.next();
            // Remove the item from the server items list
            removeComponentItem(discoItem.getJID());

            // Remove the item as a valid entity that could receive info and items disco requests
            String host = new JID(discoItem.getJID()).getDomain();
            infoHandler.removeProvider(host);
            removeProvider(host);
        }

    }

    /**
     * Sets the DiscoItemsProvider to use when a disco#items packet is sent to the server itself
     * and the specified node. For instance, if node matches "http://jabber.org/protocol/offline"
     * then a special DiscoItemsProvider should be use to return information about offline messages.
     *
     * @param node the node that the provider will handle.
     * @param provider the DiscoItemsProvider that will handle disco#items packets sent with the
     *        specified node.
     */
    public void setServerNodeInfoProvider(String node, DiscoItemsProvider provider) {
        serverNodeProviders.put(node, provider);
    }

    /**
     * Removes the DiscoItemsProvider to use when a disco#items packet is sent to the server itself
     * and the specified node.
     *
     * @param node the node that the provider was handling.
     */
    public void removeServerNodeInfoProvider(String node) {
        serverNodeProviders.remove(node);
    }

    /**
     * Registers a new disco item for a component. The jid attribute of the item will match the jid
     * of the component and the name should be the name of the component discovered using disco.
     *
     * @param jid the jid of the component.
     * @param name the discovered name of the component.
     */
    public void addComponentItem(String jid, String name) {
        addComponentItem(jid, null, name);
    }

    /**
     * Registers a new disco item for a component. The jid attribute of the item will match the jid
     * of the component and the name should be the name of the component discovered using disco.
     *
     * @param jid the jid of the component.
     * @param node the node that complements the jid address.
     * @param name the discovered name of the component.
     */
    public synchronized void addComponentItem(String jid, String node, String name) {
        // A component may send his disco#info many times and we only want to have one item
        // for the component so remove any element under the requested jid
        removeComponentItem(jid);

        // Create a new element based on the provided DiscoItem
        Element element = DocumentHelper.createElement("item");
        element.addAttribute("jid", jid);
        element.addAttribute("node", node);
        element.addAttribute("name", name);
        // Add the element to the list of items related to the server
        serverItems.add(element);
    }

    /**
     * Removes a disco item for a component that has been removed from the server.
     *
     * @param jid the jid of the component being removed.
     */
    public synchronized void removeComponentItem(String jid) {
        for (Iterator<Element> it = serverItems.iterator(); it.hasNext();) {
            if (jid.equals(it.next().attributeValue("jid"))) {
                it.remove();
            }
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        // Track the implementors of ServerItemsProvider so that we can collect the items
        // provided by the server
        infoHandler = server.getIQDiscoInfoHandler();
        setProvider(server.getServerInfo().getName(), getServerItemsProvider());
    }

    public void start() throws IllegalStateException {
        super.start();
        for (ServerItemsProvider provider : XMPPServer.getInstance().getServerItemsProviders()) {
            addServerItemsProvider(provider);
        }
    }

    public Iterator<String> getFeatures() {
        List<String> features = new ArrayList<String>();
        features.add("http://jabber.org/protocol/disco#items");
        // TODO Comment out this line when publishing of client items is implemented
        //features.add("http://jabber.org/protocol/disco#publish");
        return features.iterator();
    }

    private DiscoItemsProvider getServerItemsProvider() {
        return new DiscoItemsProvider() {
            public Iterator<Element> getItems(String name, String node, JID senderJID) {
                if (node != null) {
                    // Check if there is a provider for the requested node
                    if (serverNodeProviders.get(node) != null) {
                        return serverNodeProviders.get(node).getItems(name, node, senderJID);
                    }
                    return null;
                }
                if (name == null) {
                    return serverItems.iterator();
                }
                else {
                    List<Element> answer = new ArrayList<Element>();
                    try {
                        User user = UserManager.getInstance().getUser(name);
                        RosterItem item = user.getRoster().getRosterItem(senderJID);
                        // If the requesting entity is subscribed to the account's presence then
                        // answer the user's "available resources"
                        if (item.getSubStatus() == RosterItem.SUB_FROM ||
                                item.getSubStatus() == RosterItem.SUB_BOTH) {
                            for (Session session : SessionManager.getInstance().getSessions(name)) {
                                Element element = DocumentHelper.createElement("item");
                                element.addAttribute("jid", session.getAddress().toString());
                                answer.add(element);
                            }
                        }
                        return answer.iterator();
                    }
                    catch (UserNotFoundException e) {
                        return answer.iterator();
                    }
                }
            }
        };
    }
}