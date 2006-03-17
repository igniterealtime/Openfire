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

import org.dom4j.Element;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.IQ;

import java.util.*;

/**
 * A type of node that contains published items only. It is NOT a container for
 * other nodes.
 *
 * @author Matt Tucker
 */
public class LeafNode extends Node {

    /**
     * Flag that indicates whether to persist items to storage. Note that when the
     * variable is false then the last published item is the only items being saved
     * to the backend storage.
     */
    private boolean persistPublishedItems;
    /**
     * Maximum number of published items to persist. Note that all nodes are going to persist
     * their published items. The only difference is the number of the last published items
     * to be persisted. Even nodes that are configured to not use persitent items are going
     * to save the last published item.
     */
    private int maxPublishedItems;
    /**
     * The maximum payload size in bytes.
     */
    private int maxPayloadSize;
    /**
     * List of items that were published to the node and that are still active. If the node is
     * not configured to persist items then the last published item will be kept. The list is
     * sorted cronologically.
     */
    protected List<PublishedItem> publishedItems = new ArrayList<PublishedItem>();
    protected Map<String, PublishedItem> itemsByID = new HashMap<String, PublishedItem>();

    // TODO Add checking of max payload size

    LeafNode(PubSubService service, CollectionNode parentNode, String nodeID, JID creator) {
        super(service, parentNode, nodeID, creator);
        // Configure node with default values (get them from the pubsub service)
        DefaultNodeConfiguration defaultConfiguration = service.getDefaultNodeConfiguration(true);
        this.persistPublishedItems = defaultConfiguration.isPersistPublishedItems();
        this.maxPublishedItems = defaultConfiguration.getMaxPublishedItems();
        this.maxPayloadSize = defaultConfiguration.getMaxPayloadSize();
    }

    void configure(FormField field) {
        List<String> values;
        String booleanValue;
        if ("pubsub#persist_items".equals(field.getVariable())) {
            values = field.getValues();
            booleanValue = (values.size() > 0 ? values.get(0) : "1");
            persistPublishedItems = "1".equals(booleanValue);
        }
        else if ("pubsub#max_payload_size".equals(field.getVariable())) {
            values = field.getValues();
            maxPayloadSize = values.size() > 0 ? Integer.parseInt(values.get(0)) : 5120;
        }
    }

    void postConfigure(DataForm completedForm) {
        List<String> values;
        // TODO Remove stored published items based on the new max items
        if (!persistPublishedItems) {
            // Always save the last published item when not configured to use persistent items
            maxPublishedItems = 1;
        }
        else {
            FormField field = completedForm.getField("pubsub#max_items");
            if (field != null) {
                values = field.getValues();
                maxPublishedItems = values.size() > 0 ? Integer.parseInt(values.get(0)) : 50;
            }
        }
    }

    protected void addFormFields(DataForm form, boolean isEditing) {
        super.addFormFields(form, isEditing);

        FormField formField = form.addField();
        formField.setVariable("pubsub#persist_items");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.persist_items"));
        }
        formField.addValue(persistPublishedItems);

        formField = form.addField();
        formField.setVariable("pubsub#max_items");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.max_items"));
        }
        formField.addValue(maxPublishedItems);

        formField = form.addField();
        formField.setVariable("pubsub#max_payload_size");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.max_payload_size"));
        }
        formField.addValue(maxPayloadSize);

    }

    void addPublishedItem(PublishedItem item) {
        synchronized (publishedItems) {
            publishedItems.add(item);
            itemsByID.put(item.getID(), item);
        }
    }

    public int getMaxPayloadSize() {
        return maxPayloadSize;
    }

    public boolean isPersistPublishedItems() {
        return persistPublishedItems;
    }

    public int getMaxPublishedItems() {
        return maxPublishedItems;
    }

    /**
     * Returns true if an item element is required to be included when publishing an
     * item to this node. When an item is included then the item will have an item ID
     * that will be included when sending items to node subscribers.<p>
     *
     * Leaf nodes that are transient and do not deliver payloads with event notifications
     * do not require an item element. If a user tries to publish an item to a node
     * that does not require items then an error will be returned.
     *
     * @return true if an item element is required to be included when publishing an
     *         item to this node.
     */
    public boolean isItemRequired() {
        return isPersistPublishedItems() || isDeliverPayloads();
    }

    /**
     * Sends event notifications to subscribers for the new published event. The published
     * event may or may not include an item. When the node is not persistent and does not
     * require payloads then an item is not going to be created nore included in
     * the event notification.<p>
     *
     * When an item is included in the published event then a new {@link PublishedItem} is
     * going to be created and added to the list of published item. Each published item will
     * have a unique ID in the node scope. The new published item will be added to the end
     * of the published list to keep the cronological order. When the max number of published
     * items is exceeded then the oldest published items will be removed.<p>
     *
     * For performance reasons the newly added published items and the deleted items (if any)
     * are saved to the database using a background thread. Sending event notifications to
     * node subscribers may also use another thread to ensure good performance.
     *
     * @param originalIQ the IQ packet used by the publisher to publish the item.
     * @param itemID the ID of the item or null if none was published.
     * @param payload the payload of the new published item or null if none was published.
     */
    public void sendEventNotification(IQ originalIQ, String itemID, Element payload) {
        PublishedItem newItem = null;
        if (isItemRequired()) {
            // Create a published item from the published data and add it to the node (and the db)
            synchronized (publishedItems) {
                // Make sure that the published item has an ID and that it's unique in the node
                if (itemID == null) {
                    itemID = StringUtils.randomString(15);
                }
                while (itemsByID.get(itemID) != null) {
                    itemID = StringUtils.randomString(15);
                }

                // Create a new published item
                newItem = new PublishedItem(this, originalIQ.getFrom(), itemID, new Date());
                newItem.setPayload(payload);

                // Add the published item to the list of items to persist (using another thread)
                while (!publishedItems.isEmpty() && maxPublishedItems >= publishedItems.size()) {
                    PublishedItem removedItem = publishedItems.remove(0);
                    itemsByID.remove(removedItem.getID());
                    // TODO Add removed item to the queue of items to delete from the database
                }
                addPublishedItem(newItem);
                // TODO Add new published item to the queue of items to add to the database
            }
        }

        // Return success operation
        service.send(IQ.createResultIQ(originalIQ));

        // Build event notification packet to broadcast to subscribers
        Message message = new Message();
        Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
        Element items = event.addElement("items");
        items.addAttribute("node", nodeID);
        if (newItem != null) {
            // Add item information to the event notification if an item was published
            Element item = items.addElement("item");
            if (isItemRequired()) {
                item.addAttribute("id", newItem.getID());
            }
            if (deliverPayloads) {
                item.add(newItem.getPayload().createCopy());
            }
        }
        // Broadcast event notification to subscribers and parent node subscribers
        Set<NodeAffiliate> affiliatesToNotify = new HashSet<NodeAffiliate>(affiliates);
        // Get affiliates that are subscribed to a parent in the hierarchy of parent nodes
        for (CollectionNode parentNode : getParents()) {
            for (NodeSubscription subscription : parentNode.getSubscriptions()) {
                affiliatesToNotify.add(subscription.getAffiliate());
            }
        }
        // TODO Use another thread for this (if # of subscribers is > X)????
        for (NodeAffiliate affiliate : affiliatesToNotify) {
            affiliate.sendEventNotification(message, this, newItem);
        }
    }

    public PublishedItem getPublishedItem(String itemID) {
        if (!isItemRequired()) {
            return null;
        }
        synchronized (publishedItems) {
            return itemsByID.get(itemID);
        }
    }

    public List<PublishedItem> getPublishedItems() {
        synchronized (publishedItems) {
            return Collections.unmodifiableList(publishedItems);
        }
    }

    public List<PublishedItem> getPublishedItems(int recentItems) {
        synchronized (publishedItems) {
            int size = publishedItems.size();
            if (recentItems > size) {
                // User requested more items than the one the node has so return the current list
                return Collections.unmodifiableList(publishedItems);
            }
            else {
                // Return the number of recent items the user requested
                List<PublishedItem> recent = publishedItems.subList(size - recentItems, size);
                return new ArrayList<PublishedItem>(recent);
            }
        }
    }

    public PublishedItem getLastPublishedItem() {
        synchronized (publishedItems) {
            if (publishedItems.isEmpty()) {
                return null;
            }
            return publishedItems.get(publishedItems.size()-1);
        }
    }

    void setMaxPayloadSize(int maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    void setPersistPublishedItems(boolean persistPublishedItems) {
        this.persistPublishedItems = persistPublishedItems;
    }

    void setMaxPublishedItems(int maxPublishedItems) {
        this.maxPublishedItems = maxPublishedItems;
    }

    /**
     * Purges items that were published to the node. Only owners can request this operation.
     * This operation is only available for nodes configured to store items in the database. All
     * published items will be deleted with the exception of the last published item.
     */
    public void purge() {
        List<PublishedItem> toDelete = null;
        // Calculate items to delete
        synchronized (publishedItems) {
            if (publishedItems.size() > 1) {
                // Remove all items except the last one
                toDelete = publishedItems.subList(0, publishedItems.size() - 1);
                // Remove items to delete from memory
                publishedItems.removeAll(toDelete);
                // Update fast look up cache of published items
                itemsByID = new HashMap<String, PublishedItem>();
                itemsByID.put(publishedItems.get(0).getID(), publishedItems.get(0));
            }
        }
        if (toDelete != null) {
            // Delete purged items from the database
            for (PublishedItem item : toDelete) {
                PubSubPersistenceManager.removePublishedItem(service, this, item);
            }
            // Broadcast purge notification to subscribers
            // Build packet to broadcast to subscribers
            Message message = new Message();
            Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
            Element items = event.addElement("purge");
            items.addAttribute("node", nodeID);
            // Send notification that the node configuration has changed
            broadcastSubscribers(message, false);
        }
    }

}
