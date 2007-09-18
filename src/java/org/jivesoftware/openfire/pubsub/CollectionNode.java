/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.pubsub;

import org.dom4j.Element;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private Map<String, Node> nodes = new ConcurrentHashMap<String, Node>();
    /**
     * Policy that defines who may associate leaf nodes with a collection.
     */
    private LeafNodeAssociationPolicy associationPolicy = LeafNodeAssociationPolicy.all;
    /**
     * Users that are allowed to associate leaf nodes with this collection node. This collection
     * is going to be used only when the associationPolicy is <tt>whitelist</tt>.
     */
    private Collection<JID> associationTrusted = new ArrayList<JID>();
    /**
     * Max number of leaf nodes that this collection node might have. A value of -1 means
     * that there is no limit.
     */
    private int maxLeafNodes = -1;

    public CollectionNode(PubSubService service, CollectionNode parentNode, String nodeID, JID creator) {
        super(service, parentNode, nodeID, creator);
        // Configure node with default values (get them from the pubsub service)
        DefaultNodeConfiguration defaultConfiguration = service.getDefaultNodeConfiguration(false);
        this.associationPolicy = defaultConfiguration.getAssociationPolicy();
        this.maxLeafNodes = defaultConfiguration.getMaxLeafNodes();
    }


    void configure(FormField field) {
        List<String> values;
        if ("pubsub#leaf_node_association_policy".equals(field.getVariable())) {
            values = field.getValues();
            if (values.size() > 0)  {
                associationPolicy = LeafNodeAssociationPolicy.valueOf(values.get(0));
            }
        }
        else if ("pubsub#leaf_node_association_whitelist".equals(field.getVariable())) {
            // Get the new list of users that may add leaf nodes to this collection node
            associationTrusted = new ArrayList<JID>();
            for (String value : field.getValues()) {
                try {
                    addAssociationTrusted(new JID(value));
                }
                catch (Exception e) {
                    // Do nothing
                }
            }
        }
        else if ("pubsub#leaf_nodes_max".equals(field.getVariable())) {
            values = field.getValues();
            maxLeafNodes = values.size() > 0 ? Integer.parseInt(values.get(0)) : -1;
        }
    }

    void postConfigure(DataForm completedForm) {
        //Do nothing.
    }

    protected void addFormFields(DataForm form, boolean isEditing) {
        super.addFormFields(form, isEditing);

        FormField formField = form.addField();
        formField.setVariable("pubsub#leaf_node_association_policy");
        if (isEditing) {
            formField.setType(FormField.Type.list_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.leaf_node_association"));
            formField.addOption(null, LeafNodeAssociationPolicy.all.name());
            formField.addOption(null, LeafNodeAssociationPolicy.owners.name());
            formField.addOption(null, LeafNodeAssociationPolicy.whitelist.name());
        }
        formField.addValue(associationPolicy.name());

        formField = form.addField();
        formField.setVariable("pubsub#leaf_node_association_whitelist");
        if (isEditing) {
            formField.setType(FormField.Type.jid_multi);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.leaf_node_whitelist"));
        }
        for (JID contact : associationTrusted) {
            formField.addValue(contact.toString());
        }

        formField = form.addField();
        formField.setVariable("pubsub#leaf_nodes_max");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.leaf_nodes_max"));
        }
        formField.addValue(maxLeafNodes);
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
        nodes.put(child.getNodeID(), child);
    }


    /**
     * Removes a child node from the list of child nodes. This method will not trigger
     * notifications to node subscribers.
     *
     * @param child the node to remove from the list of child nodes.
     */
    void removeChildNode(Node child) {
        nodes.remove(child.getNodeID());
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
        Element item = event.addElement("items").addElement("item");
        item.addAttribute("id", child.getNodeID());
        if (deliverPayloads) {
            item.add(child.getMetadataForm().getElement());
        }
        // Broadcast event notification to subscribers
        broadcastCollectionNodeEvent(child, message);
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
        event.addElement("delete").addAttribute("node", child.getNodeID());
        // Broadcast event notification to subscribers
        broadcastCollectionNodeEvent(child, message);
    }

    protected void deletingNode() {
        // Update child nodes to use the parent node of this node as the new parent node
        for (Node node : getNodes()) {
            node.changeParent(parent);
        }
    }

    private void broadcastCollectionNodeEvent(Node child, Message notification) {
        // Get affected subscriptions (of this node and all parent nodes)
        Collection<NodeSubscription> subscriptions = new ArrayList<NodeSubscription>();
        subscriptions.addAll(getSubscriptions(child));
        for (CollectionNode parentNode : getParents()) {
            subscriptions.addAll(parentNode.getSubscriptions(child));
        }
        // TODO Possibly use a thread pool for sending packets (based on the jids size)
        for (NodeSubscription subscription : subscriptions) {
            service.sendNotification(subscription.getNode(), notification, subscription.getJID());
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
        Collection<NodeSubscription> subscriptions = new ArrayList<NodeSubscription>();
        for (NodeSubscription subscription : getSubscriptions()) {
            if (subscription.canSendChildNodeEvent(child)) {
                subscriptions.add(subscription);
            }
        }
        return subscriptions;
    }

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
    public boolean isChildNode(Node child) {
        return nodes.containsKey(child.getNodeID());
    }

    /**
     * Returns true if the specified node is a direct child node of this collection node or
     * a descendant of the children nodes.
     *
     * @param child the node to check if it is a descendant of this node.
     * @return true if the specified node is a direct child node of this collection node or
     *         a descendant of the children nodes.
     */
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
     * This collection is going to be used only when the associationPolicy is <tt>whitelist</tt>.
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
     * This collection is going to be used only when the associationPolicy is <tt>whitelist</tt>.
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
}
