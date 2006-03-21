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

package org.jivesoftware.wildfire.pubsub;

import org.xmpp.packet.JID;
import org.xmpp.forms.FormField;
import org.xmpp.forms.DataForm;
import org.jivesoftware.util.LocaleUtils;

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

    // TODO Send event notification when a new child node is added (section 9.2)
    // TODO Add checking that max number of leaf nodes has been reached
    // TODO Add checking that verifies that user that is associating leaf node with collection node is allowed

    CollectionNode(PubSubService service, CollectionNode parentNode, String nodeID, JID creator) {
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
                    associationTrusted.add(new JID(value));
                }
                catch (Exception e) {}
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

    void addChildNode(Node child) {
        nodes.put(child.getNodeID(), child);
    }

    void removeChildNode(Node child) {
        // TODO Send notification to subscribers?
        nodes.remove(child.getNodeID());
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
        return associationTrusted;
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
        whitelist;
    }
}
