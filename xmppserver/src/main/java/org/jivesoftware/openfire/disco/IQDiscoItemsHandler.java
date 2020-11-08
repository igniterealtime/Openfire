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

package org.jivesoftware.openfire.disco;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.resultsetmanagement.ResultSet;
import org.xmpp.resultsetmanagement.ResultSetImpl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * IQDiscoItemsHandler is responsible for handling disco#items requests. This class holds a map with
 * the main entities and the associated DiscoItemsProvider. We are considering the host of the
 * recipient JIDs as main entities. It's the DiscoItemsProvider responsibility to provide the items
 * associated with the JID's name together with any possible requested node.
 * <p>
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
 * then a not-found error will be returned.</p>
 * <p>
 * Publishing of client items is still not supported.
 * </p>
 *
 * @author Gaston Dombiak
 */
public class IQDiscoItemsHandler extends IQHandler implements ServerFeaturesProvider, ClusterEventListener,
        UserItemsProvider {

    private static final Logger Log = LoggerFactory.getLogger(IQDiscoItemsHandler.class);

    public static final String NAMESPACE_DISCO_ITEMS = "http://jabber.org/protocol/disco#items";
    private Map<String,DiscoItemsProvider> entities = new HashMap<>();
    private Map<String, Element> localServerItems = new HashMap<>();
    private Cache<String, ClusteredServerItem> serverItems;
    private Map<String, DiscoItemsProvider> serverNodeProviders = new ConcurrentHashMap<>();
    private IQHandlerInfo info;
    private IQDiscoInfoHandler infoHandler;
    private List<UserItemsProvider> userItemsProviders = new ArrayList<>();

    public IQDiscoItemsHandler() {
        super("XMPP Disco Items Handler");
        info = new IQHandlerInfo("query", NAMESPACE_DISCO_ITEMS);
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
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
        DiscoItemsProvider itemsProvider = null;
        
        if((packet.getTo() == null) || (packet.getTo().asBareJID().equals(packet.getFrom().asBareJID()))) {
            itemsProvider = getProvider(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
        } else {
            itemsProvider = getProvider(packet.getTo().getDomain());
        }

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
            Iterator<DiscoItem> itemsItr = itemsProvider.getItems(name, node, packet.getFrom());
            if (itemsItr != null) {
                reply.setChildElement(iq.createCopy());
                Element queryElement = reply.getChildElement();

                // See if the requesting entity would like to apply 'result set
                // management'
                final Element rsmElement = packet.getChildElement().element(
                        QName.get("set",
                                ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT));

                // apply RSM only if the element exists, and the (total) results
                // set is not empty.
                final boolean applyRSM = rsmElement != null
                        && itemsItr.hasNext();

                if (applyRSM) {
                    if (!ResultSet.isValidRSMRequest(rsmElement))
                    {
                        reply.setError(PacketError.Condition.bad_request);
                        return reply;
                    }
                    
                    // Calculate which results to include.
                    final List<DiscoItem> rsmResults;
                    final List<DiscoItem> allItems = new ArrayList<>();
                    while (itemsItr.hasNext()) {
                        allItems.add(itemsItr.next());
                    }
                    final ResultSet<DiscoItem> rs = new ResultSetImpl<>(
                            allItems);
                    try {
                        rsmResults = rs.applyRSMDirectives(rsmElement);
                    } catch (NullPointerException e) {
                        final IQ itemNotFound = IQ.createResultIQ(packet);
                        itemNotFound.setError(PacketError.Condition.item_not_found);
                        return itemNotFound;
                    }

                    // add the applicable results to the IQ-result
                    for (DiscoItem item : rsmResults) {
                        final Element resultElement = item.getElement();
                        resultElement.setQName(new QName(resultElement
                                .getName(), queryElement.getNamespace()));
                        queryElement.add(resultElement.createCopy());
                    }

                    // overwrite the 'set' element.
                    queryElement.remove(queryElement.element(
                            QName.get("set",
                                    ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT)));
                    queryElement.add(rs.generateSetElementFromResults(rsmResults));
                } else {
                    // don't apply RSM:
                    // Add to the reply all the items provided by the DiscoItemsProvider
                    Element item;
                    while (itemsItr.hasNext()) {
                        item = itemsItr.next().getElement();
                        item.setQName(new QName(item.getName(), queryElement.getNamespace()));
                        queryElement.add(item.createCopy());
                    }
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
            addComponentItem(discoItem.getJID().toString(), discoItem.getNode(), discoItem.getName());

            // Add the new item as a valid entity that could receive info and items disco requests
            String host = discoItem.getJID().getDomain();
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
            removeComponentItem(discoItem.getJID().toString());

            // Remove the item as a valid entity that could receive info and items disco requests
            String host = discoItem.getJID().getDomain();
            infoHandler.removeProvider(host);
            removeProvider(host);
        }
    }

    /**
     * Adds the items provided by the new service that implements the UserItemsProvider interface. This information will
     * be used whenever a disco for items is made against users of the server.
     *
     * @param provider the UserItemsProvider that provides new user items.
     */
    public void addUserItemsProvider( UserItemsProvider provider )
    {
        this.userItemsProviders.add( provider );
    }

    /**
     * Removes the UserItemsProvider
     *
     * @param provider the UserItemsProvider that provides new user items.
     */
    public void removeUserItemsProvider( UserItemsProvider provider )
    {
        this.userItemsProviders.remove( provider );
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
    public void addComponentItem(String jid, String node, String name) {
        Lock lock = serverItems.getLock(jid);
        lock.lock();
        try {
            ClusteredServerItem item = serverItems.get(jid);
            if (item == null) {
                // First time a node registers a server item for this component
                item = new ClusteredServerItem();

                Element element = DocumentHelper.createElement("item");
                element.addAttribute("jid", jid);
                element.addAttribute("node", node);
                element.addAttribute("name", name);
                item.element = element;
            }
            if (item.nodes.add(XMPPServer.getInstance().getNodeID())) {
                // Update the cache with latest info
                serverItems.put(jid, item);
            }
            // Keep track of the new server item added by this JVM
            localServerItems.put(jid, item.element);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Removes a disco item for a component that has been removed from the server.
     *
     * @param jid the jid of the component being removed.
     */
    public void removeComponentItem(String jid) {
        if (serverItems == null) {
            // Safety check
            return;
        }
        Lock lock = serverItems.getLock(jid);
        lock.lock();
        try {
            ClusteredServerItem item = serverItems.get(jid);
            if (item != null && item.nodes.remove(XMPPServer.getInstance().getNodeID())) {
                // Update the cache with latest info
                if (item.nodes.isEmpty()) {
                    serverItems.remove(jid);
                }
                else {
                    serverItems.put(jid, item);
                }
            }
        }
        finally {
            lock.unlock();
        }
        // Remove locally added server item
        localServerItems.remove(jid);
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        serverItems = CacheFactory.createCache("Disco Server Items");
        // Track the implementors of ServerItemsProvider so that we can collect the items
        // provided by the server
        infoHandler = server.getIQDiscoInfoHandler();
        setProvider(server.getServerInfo().getXMPPDomain(), getServerItemsProvider());
        // Listen to cluster events
        ClusterManager.addListener(this);
    }

    @Override
    public void start() throws IllegalStateException {
        super.start();
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton(NAMESPACE_DISCO_ITEMS).iterator();
    }

    @Override
    public void joinedCluster() {
        // The local node joined a cluster.
        //
        // Upon joining a cluster, clustered caches are reset to their clustered equivalent (by the swap from the local
        // cache implementation to the clustered cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.joinedCluster). This means that they now hold data that's
        // available on all other cluster nodes. Data that's available on the local node needs to be added again.
        restoreCacheContent();

        // It does not appear to be needed to invoke any kind of event listeners for the data that was gained by joining
        // the cluster (eg: new server items provided by other cluster nodes now available to the local cluster node):
        // the only cache that's being used in this implementation does not have an associated event listening mechanism
        // when data is added to or removed from it.
    }

    @Override
    public void joinedCluster(byte[] nodeID) {
        // Another node joined a cluster that we're already part of. It is expected that
        // the implementation of #joinedCluster() as executed on the cluster node that just
        // joined will synchronize all relevant data. This method need not do anything.
    }

    @Override
    public void leftCluster() {
        // The local cluster node left the cluster.
        if (XMPPServer.getInstance().isShuttingDown()) {
            // Do not put effort in restoring the correct state if we're shutting down anyway.
            return;
        }

        // Upon leaving a cluster, clustered caches are reset to their local equivalent (by the swap from the clustered
        // cache implementation to the default cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.leftCluster). This means that they now hold no data (as a new cache
        // has been created). Data that's available on the local node needs to be added again.
        restoreCacheContent();

        // It does not appear to be needed to invoke any kind of event listeners for the data that was lost by leaving
        // the cluster (eg: server items provided only by other cluster nodes, now unavailable to the local cluster
        // node): the only cache that's being used in this implementation does not have an associated event listening
        // mechanism when data is added to or removed from it.
    }

    @Override
    public void leftCluster(byte[] nodeID) {
        // Another node left the cluster.
        //
        // If the cluster node leaves in an orderly fashion, it might have broadcasted
        // the necessary events itself. This cannot be depended on, as the cluster node
        // might have disconnected unexpectedly (as a result of a crash or network issue).
        //
        // Determine what data was available only on that node, and remove that.
        //
        // All remaining cluster nodes will be in a race to clean up the
        // same data. The implementation below accounts for that, by only having the
        // senior cluster node to perform the cleanup.
        if (ClusterManager.isSeniorClusterMember()) {
            NodeID leftNode = NodeID.getInstance(nodeID);
            for (Map.Entry<String, ClusteredServerItem> entry : serverItems.entrySet()) {
                String jid = entry.getKey();
                Lock lock = serverItems.getLock(jid);
                lock.lock();
                try {
                    ClusteredServerItem item = entry.getValue();
                    if (item.nodes.remove(leftNode)) {
                        // Update the cache with latest info
                        if (item.nodes.isEmpty()) {
                            serverItems.remove(jid);
                        }
                        else {
                            serverItems.put(jid, item);
                        }
                    }
                }
                finally {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    public void markedAsSeniorClusterMember() {
        // Do nothing
    }

    /**
     * When the local node is joining or leaving a cluster, {@link org.jivesoftware.util.cache.CacheFactory} will swap
     * the implementation used to instantiate caches. This causes the cache content to be 'reset': it will no longer
     * contain the data that's provided by the local node. This method restores data that's provided by the local node
     * in the cache. It is expected to be invoked right after joining ({@link #joinedCluster()} or leaving
     * ({@link #leftCluster()} a cluster.
     */
    private void restoreCacheContent() {
        Log.trace( "Restoring cache content for cache '{}' by adding all server items that are provided by the local cluster node.", serverItems.getName() );
        for (Map.Entry<String, Element> entry : localServerItems.entrySet()) {
            String jid = entry.getKey();
            Element element = entry.getValue();
            Lock lock = serverItems.getLock(jid);
            lock.lock();
            try {
                ClusteredServerItem item = serverItems.get(jid);
                if (item == null) {
                    // First time a node registers a server item for this component
                    item = new ClusteredServerItem();
                    item.element = element;
                }
                if (item.nodes.add(XMPPServer.getInstance().getNodeID())) {
                    // Update the cache with latest info
                    serverItems.put(jid, item);
                }
            }
            finally {
                lock.unlock();
            }
        }
    }

    private DiscoItemsProvider getServerItemsProvider() {
        return new DiscoItemsProvider() {
            @Override
            public Iterator<DiscoItem> getItems(String name, String node, JID senderJID) {
                if (node != null) {
                    // Check if there is a provider for the requested node
                    if (serverNodeProviders.get(node) != null) {
                        return serverNodeProviders.get(node).getItems(name, node, senderJID);
                    }
                    return null;
                }
                if (name == null) {
                    return getServerItems().iterator();
                }
                else {
                    // If addressed to user@domain, add items from UserItemsProviders to
                    // the reply.
                    if ( userItemsProviders.isEmpty()) {
                        // If we didn't find any UserItemsProviders, then answer a not found error
                        return null;
                    }
                    List<DiscoItem> answer = new ArrayList<>();
                    for (UserItemsProvider itemsProvider : userItemsProviders ) {
                        // Check if we have items associated with the requested name
                        Iterator<Element> itemsItr = itemsProvider.getUserItems(name, senderJID);
                        if (itemsItr != null) {
                            // Add to the reply all the items provided by the UserItemsProvider
                            Element item;
                            while (itemsItr.hasNext()) {
                                item = itemsItr.next();
                                JID itemJid = new JID(item.attributeValue("jid"));
                                String itemName = item.attributeValue("name");
                                String itemNode = item.attributeValue("node");
                                String itemAction = item.attributeValue("action");
                                answer.add(new DiscoItem(itemJid, itemName, itemNode, itemAction));
                            }
                        }
                    }
                    return answer.iterator();
                }
            }
        };
    }

    private static class ClusteredServerItem implements Externalizable {
        private Element element;
        private Set<NodeID> nodes = new HashSet<>();

        public ClusteredServerItem() {
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) element);
            ExternalizableUtil.getInstance().writeExternalizableCollection(out, nodes);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            element = (Element) ExternalizableUtil.getInstance().readSerializable(in);
            ExternalizableUtil.getInstance().readExternalizableCollection(in, nodes, getClass().getClassLoader());
        }

        @Override
        public String toString()
        {
            return "ClusteredServerItem{" +
                "nodes=" + nodes.stream().map(NodeID::toString).collect(Collectors.joining(", ")) +
                '}';
        }
    }

    @Override
    public Iterator<Element> getUserItems(String name, JID senderJID) {
        List<Element> answer = new ArrayList<>();
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

    /**
     * Returns all server items.
     * @return A collection of server items.
     */
    public List<DiscoItem> getServerItems() {
        List<DiscoItem> answer = new ArrayList<>();
        for (ClusteredServerItem item : serverItems.values()) {
            answer.add(new DiscoItem(item.element));
        }
        return answer;
    }
}
