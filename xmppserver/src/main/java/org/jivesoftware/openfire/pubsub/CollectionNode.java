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

package org.jivesoftware.openfire.pubsub;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * A type of node that contains nodes and/or other collections but no published
 * items. Collections provide the foundation entity to provide a means of representing
 * hierarchical node structures.
 *
 * @author Matt Tucker
 */
public class CollectionNode extends Node {

    /**
     * Map that contains the child nodes of this node. The key is the child node ID and the
     * value is the child node. A map is used to ensure uniqueness and in particular
     * a ConcurrentHashMap for concurrency reasons.
     */
    private final Map<UniqueIdentifier, Node> nodes = new ConcurrentHashMap<>();

    /**
     * Policy that defines who may associate leaf nodes with a collection.
     */
    private LeafNodeAssociationPolicy associationPolicy = LeafNodeAssociationPolicy.all;
    /**
     * Users that are allowed to associate leaf nodes with this collection node. This collection
     * is going to be used only when the associationPolicy is {@code whitelist}.
     */
    private Collection<JID> associationTrusted = new ArrayList<>();
    /**
     * Max number of leaf nodes that this collection node might have. A value of -1 means
     * that there is no limit.
     */
    private int maxLeafNodes = -1;

    public CollectionNode( PubSubService.UniqueIdentifier serviceId, CollectionNode parentNode, String nodeID, JID creator, boolean subscriptionEnabled, boolean deliverPayloads, boolean notifyConfigChanges, boolean notifyDelete, boolean notifyRetract, boolean presenceBasedDelivery, AccessModel accessModel, PublisherModel publisherModel, String language, ItemReplyPolicy replyPolicy, LeafNodeAssociationPolicy associationPolicy, int maxLeafNodes)
    {
        super(serviceId, parentNode, nodeID, creator, subscriptionEnabled, deliverPayloads, notifyConfigChanges, notifyDelete, notifyRetract, presenceBasedDelivery, accessModel, publisherModel, language, replyPolicy);
        this.associationPolicy = associationPolicy;
        this.maxLeafNodes = maxLeafNodes;
    }

    public CollectionNode( PubSubService.UniqueIdentifier serviceId, CollectionNode parentNode, String nodeID, JID creator, DefaultNodeConfiguration defaultConfiguration)
    {
        super(serviceId, parentNode, nodeID, creator, defaultConfiguration);
        this.associationPolicy = defaultConfiguration.getAssociationPolicy();
        this.maxLeafNodes = defaultConfiguration.getMaxLeafNodes();
    }

    public CollectionNode() { // to be used only for serialization;
        super();
    }

    @Override
    protected void configure(FormField field) throws NotAcceptableException {
        List<String> values;
        if ("pubsub#leaf_node_association_policy".equals(field.getVariable()) ||
            "pubsub#children_association_policy".equals(field.getVariable())) {
            values = field.getValues();
            if (values.size() > 0)  {
                associationPolicy = LeafNodeAssociationPolicy.valueOf(values.get(0));
            }
        }
        else if ("pubsub#leaf_node_association_whitelist".equals(field.getVariable()) ||
                "pubsub#children_association_whitelist".equals(field.getVariable())) {
            // Get the new list of users that may add leaf nodes to this collection node
            associationTrusted = new ArrayList<>();
            for (String value : field.getValues()) {
                try {
                    addAssociationTrusted(new JID(value));
                }
                catch (Exception e) {
                    // Do nothing
                }
            }
        }
        else if ("pubsub#leaf_nodes_max".equals(field.getVariable()) ||
                "pubsub#children_max".equals(field.getVariable())) {
            values = field.getValues();
            maxLeafNodes = values.size() > 0 ? Integer.parseInt(values.get(0)) : -1;
        }
        else if ("pubsub#children".endsWith(field.getVariable())) {
            values = field.getValues();
            ArrayList<Node> childrenNodes = new ArrayList<>(values.size());

            // Check all nodes for their existence
            for (String nodeId : values)
            {
                Node childNode = getService().getNode(nodeId);

                if (childNode == null)
                {
                    throw new NotAcceptableException("Child node does not exist");
                }
                childrenNodes.add(childNode);
            }
            // Remove any children not in the new list.
            ArrayList<Node> toRemove = new ArrayList<>(nodes.values());
            toRemove.removeAll(childrenNodes);

            for (Node node : toRemove)
            {
                removeChildNode(node);
            }

            // Set the parent on the children.
            for (Node node : childrenNodes)
            {
                node.changeParent(this);
            }
        }
    }

    @Override
    void postConfigure(DataForm completedForm) {
        //Do nothing.
    }

    @Override
    protected void addFormFields(DataForm form, boolean isEditing) {
        super.addFormFields(form, isEditing);

        FormField typeField = form.getField("pubsub#node_type");
        typeField.addValue("collection");
        
        // TODO: remove this field during an upgrade to pubsub version since it is replaced by children_association_policy
        FormField formField = form.addField();
        formField.setVariable("pubsub#leaf_node_association_policy");
        if (isEditing) {
            formField.setType(FormField.Type.list_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.children_association_policy"));
            formField.addOption(LocaleUtils.getLocalizedString("pubsub.form.conf.children_association_policy.all"), LeafNodeAssociationPolicy.all.name());
            formField.addOption(LocaleUtils.getLocalizedString("pubsub.form.conf.children_association_policy.owners"), LeafNodeAssociationPolicy.owners.name());
            formField.addOption(LocaleUtils.getLocalizedString("pubsub.form.conf.children_association_policy.whitelist"), LeafNodeAssociationPolicy.whitelist.name());
        }
        formField.addValue(associationPolicy.name());

        formField = form.addField();
        formField.setVariable("pubsub#children_association_policy");
        if (isEditing) {
            formField.setType(FormField.Type.list_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.children_association_policy"));
            formField.addOption(LocaleUtils.getLocalizedString("pubsub.form.conf.children_association_policy.all"), LeafNodeAssociationPolicy.all.name());
            formField.addOption(LocaleUtils.getLocalizedString("pubsub.form.conf.children_association_policy.owners"), LeafNodeAssociationPolicy.owners.name());
            formField.addOption(LocaleUtils.getLocalizedString("pubsub.form.conf.children_association_policy.whitelist"), LeafNodeAssociationPolicy.whitelist.name());
        }
        formField.addValue(associationPolicy.name());

        // TODO: remove this field during an upgrade to pubsub version since it is replaced by children_association_whitelist
        formField = form.addField();
        formField.setVariable("pubsub#leaf_node_association_whitelist");
        if (isEditing) {
            formField.setType(FormField.Type.jid_multi);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.children_association_whitelist"));
        }
        for (JID contact : associationTrusted) {
            formField.addValue(contact.toString());
        }

        formField = form.addField();
        formField.setVariable("pubsub#children_association_whitelist");
        if (isEditing) {
            formField.setType(FormField.Type.jid_multi);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.children_association_whitelist"));
        }
        for (JID contact : associationTrusted) {
            formField.addValue(contact.toString());
        }

        formField = form.addField();
        formField.setVariable("pubsub#leaf_nodes_max");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.children_max"));
        }
        formField.addValue(maxLeafNodes);

        formField = form.addField();
        formField.setVariable("pubsub#children_max");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.children_max"));
        }
        formField.addValue(maxLeafNodes);

        formField = form.addField();
        formField.setVariable("pubsub#children");
        if (isEditing) {
            formField.setType(FormField.Type.text_multi);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.children"));
        }
        for (Node.UniqueIdentifier nodeId : nodes.keySet()) {
            formField.addValue(nodeId.getNodeId());
        }
    }

    /**
     * Adds a child node to the list of child nodes. The new child node may just have been
     * created or just restored from the database. This method will not trigger notifications
     * to node subscribers since the node could be a node that has just been loaded from the
     * database.
     *
     * @param child the node to add to the list of child nodes.
     */
    void addChildNode(Node child) {
        nodes.put(child.getUniqueIdentifier(), child);
    }


    /**
     * Removes a child node from the list of child nodes. This method will not trigger
     * notifications to node subscribers.
     *
     * @param child the node to remove from the list of child nodes.
     */
    void removeChildNode(Node child) {
        nodes.remove(child.getUniqueIdentifier());
    }

    /**
     * Notification that a new node was created and added to this node. Trigger notifications
     * to node subscribers whose subscription type is {@link NodeSubscription.Type#nodes} and
     * have the proper depth.
     *
     * @param child the newly created node that was added to this node.
     */
    void childNodeAdded(Node child) {
        // Build packet to broadcast to subscribers
        Message message = new Message();
        Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
        Element items = event.addElement("items");
        items.addAttribute("node", nodeID);
        Element item = items.addElement("item");
        item.addAttribute("id", child.getUniqueIdentifier().getNodeId());
        if (deliverPayloads) {
            item.add(child.getMetadataForm().getElement());
        }
        // Broadcast event notification to subscribers
        broadcastCollectionNodeEvent(child, message);
    }

    /**
     * Notification that a child node configuration was modified. Trigger notifications
     * to node subscribers whose subscription type is {@link NodeSubscription.Type#nodes} and
     * have the proper depth.
     *
     * @param child the deleted node that was removed from this node.
     * @param notification message which will be sent to subscribers.
     */
    void childNodeModified(Node child, Message notification){
        // Broadcast event notification to subscribers
        broadcastCollectionNodeEvent(child, notification);
    }

    /**
     * Notification that a child node was deleted from this node. Trigger notifications
     * to node subscribers whose subscription type is {@link NodeSubscription.Type#nodes} and
     * have the proper depth.
     *
     * @param child the deleted node that was removed from this node.
     */
    void childNodeDeleted(Node child) {
        // Build packet to broadcast to subscribers
        Message message = new Message();
        Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
        event.addElement("delete").addAttribute("node", child.getUniqueIdentifier().getNodeId());
        // Broadcast event notification to subscribers
        broadcastCollectionNodeEvent(child, message);
    }

    @Override
    protected void deletingNode() {
        // Update child nodes to use the parent node of this node as the new parent node
        final CollectionNode parent = getParent();
        for (Node node : getNodes()) {
            node.changeParent(parent);
        }
    }

    private void broadcastCollectionNodeEvent(Node child, Message notification) {
        // Get affected subscriptions (of this node and all parent nodes)
        Collection<NodeSubscription> subscriptions = new ArrayList<>();
        subscriptions.addAll(getSubscriptions(child));
        for (CollectionNode parentNode : getParents()) {
            subscriptions.addAll(parentNode.getSubscriptions(child));
        }
        // TODO Possibly use a thread pool for sending packets (based on the jids size)
        for (NodeSubscription subscription : subscriptions) {
            getService().sendNotification(subscription.getNode(), notification, subscription.getJID());
        }

        // XEP-0136 specifies that all connected resources of the owner of the PEP service should also get a notification
        if ( getService() instanceof PEPService ) {
            final Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(getService().getAddress().getNode());
            for( final ClientSession session : sessions ) {
                getService().sendNotification(child, notification, session.getAddress());
                for (CollectionNode parentNode : getParents()) {
                    getService().sendNotification(parentNode, notification, session.getAddress());
                }
            }
        }
    }

    /**
     * Returns a collection with the subscriptions to this node that should be notified
     * that a new child was added or deleted.
     *
     * @param child the added or deleted child.
     * @return a collection with the subscriptions to this node that should be notified
     *         that a new child was added or deleted.
     */
    private Collection<NodeSubscription> getSubscriptions(Node child) {
        Collection<NodeSubscription> subscriptions = new ArrayList<>();
        for (NodeSubscription subscription : getSubscriptions()) {
            if (subscription.canSendChildNodeEvent(child)) {
                subscriptions.add(subscription);
            }
        }
        return subscriptions;
    }

    @Override
    public boolean isCollectionNode() {
        return true;
    }

    /**
     * Returns true if the specified node is a first-level children of this collection
     * node.
     *
     * @param child the node to check if it is a direct child of this node.
     * @return true if the specified node is a first-level children of this collection
     *         node.
     */
    @Override
    public boolean isChildNode(Node child) {
        return nodes.containsKey(child.getUniqueIdentifier());
    }

    /**
     * Returns true if the specified node is a direct child node of this collection node or
     * a descendant of the children nodes.
     *
     * @param child the node to check if it is a descendant of this node.
     * @return true if the specified node is a direct child node of this collection node or
     *         a descendant of the children nodes.
     */
    @Override
    public boolean isDescendantNode(Node child) {
        if (isChildNode(child)) {
            return true;
        }
        for (Node node : getNodes()) {
            if (node.isDescendantNode(child)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<Node> getNodes() {
        return nodes.values();
    }

    /**
     * Returns the policy that defines who may associate leaf nodes with a collection.
     *
     * @return the policy that defines who may associate leaf nodes with a collection.
     */
    public LeafNodeAssociationPolicy getAssociationPolicy() {
        return associationPolicy;
    }

    /**
     * Returns the users that are allowed to associate leaf nodes with this collection node.
     * This collection is going to be used only when the associationPolicy is {@code whitelist}.
     *
     * @return the users that are allowed to associate leaf nodes with this collection node.
     */
    public Collection<JID> getAssociationTrusted() {
        return Collections.unmodifiableCollection(associationTrusted);
    }

    /**
     * Adds a new trusted user that is allowed to associate leaf nodes with this collection node.
     * The new user is not going to be added to the database. Instead it is just kept in memory.
     *
     * @param user the new trusted user that is allowed to associate leaf nodes with this
     *        collection node.
     */
    void addAssociationTrusted(JID user) {
        associationTrusted.add(user);
    }

    /**
     * Returns the max number of leaf nodes that this collection node might have. A value of
     * -1 means that there is no limit.
     *
     * @return the max number of leaf nodes that this collection node might have.
     */
    public int getMaxLeafNodes() {
        return maxLeafNodes;
    }

    /**
     * Sets the policy that defines who may associate leaf nodes with a collection.
     *
     * @param associationPolicy the policy that defines who may associate leaf nodes
     *        with a collection.
     */
    void setAssociationPolicy(LeafNodeAssociationPolicy associationPolicy) {
        this.associationPolicy = associationPolicy;
    }

    /**
     * Sets the users that are allowed to associate leaf nodes with this collection node.
     * This collection is going to be used only when the associationPolicy is {@code whitelist}.
     *
     * @param associationTrusted the users that are allowed to associate leaf nodes with this
     *        collection node.
     */
    void setAssociationTrusted(Collection<JID> associationTrusted) {
        this.associationTrusted = associationTrusted;
    }

    /**
     * Sets the max number of leaf nodes that this collection node might have. A value of
     * -1 means that there is no limit.
     *
     * @param maxLeafNodes the max number of leaf nodes that this collection node might have.
     */
    void setMaxLeafNodes(int maxLeafNodes) {
        this.maxLeafNodes = maxLeafNodes;
    }

    /**
     * Returns true if the specified user is allowed to associate a leaf node with this
     * node. The decision is taken based on the association policy that the node is
     * using.
     *
     * @param user the user trying to associate a leaf node with this node.
     * @return true if the specified user is allowed to associate a leaf node with this
     *         node.
     */
    public boolean isAssociationAllowed(JID user) {
        if (associationPolicy == LeafNodeAssociationPolicy.all) {
            // Anyone is allowed to associate leaf nodes with this node
            return true;
        }
        else if (associationPolicy == LeafNodeAssociationPolicy.owners) {
            // Only owners or sysadmins are allowed to associate leaf nodes with this node
            return isAdmin(user);
        }
        else {
            // Owners, sysadmins and a whitelist of usres are allowed to
            // associate leaf nodes with this node
            return isAdmin(user) || associationTrusted.contains(user);
        }
    }

    /**
     * Returns true if the max number of leaf nodes associated with this node has
     * reached to the maximum allowed.
     *
     * @return true if the max number of leaf nodes associated with this node has
     *         reached to the maximum allowed.
     */
    public boolean isMaxLeafNodeReached() {
        if (maxLeafNodes < 0) {
            // There is no maximum limit
            return false;
        }
        // Count number of child leaf nodes
        int counter = 0;
        for (Node node : getNodes()) {
            if (!node.isCollectionNode()) {
                counter = counter + 1;
            }
        }
        // Compare count with maximum allowed
        return counter >= maxLeafNodes;
    }

    /**
     * Policy that defines who may associate leaf nodes with a collection.
     */
    public static enum LeafNodeAssociationPolicy {

        /**
         * Anyone may associate leaf nodes with the collection.
         */
        all,
        /**
         * Only collection node owners may associate leaf nodes with the collection.
         */
        owners,
        /**
         * Only those on a whitelist may associate leaf nodes with the collection.
         */
        whitelist
    }

    @Override
    public int getCachedSize() throws CannotCalculateSizeException
    {
        int size = super.getCachedSize(); // parent.
        size += CacheSizes.sizeOfMap( nodes );
        size += CacheSizes.sizeOfInt(); // associationPolicy
        size += CacheSizes.sizeOfCollection( associationTrusted );
        size += CacheSizes.sizeOfInt(); // maxLeafNodes
        return size;
    }

    @Override
    public void writeExternal( ObjectOutput out ) throws IOException
    {
        super.writeExternal( out );

        final ExternalizableUtil util = ExternalizableUtil.getInstance();
        util.writeSerializableMap( out, nodes );
        util.writeSafeUTF( out, associationPolicy.name() );
        util.writeSerializableCollection( out, associationTrusted );
        util.writeInt( out, maxLeafNodes );
    }

    @Override
    public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException
    {
        super.readExternal( in );

        final ExternalizableUtil util = ExternalizableUtil.getInstance();
        util.readSerializableMap( in, nodes, getClass().getClassLoader() );
        associationPolicy = LeafNodeAssociationPolicy.valueOf( util.readSafeUTF( in ) );
        util.readSerializableCollection( in, associationTrusted, getClass().getClassLoader() );
        maxLeafNodes = util.readInt( in );
    }
}
