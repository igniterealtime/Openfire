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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.dom4j.Element;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * A type of node that contains published items only. It is NOT a container for
 * other nodes.
 *
 * @author Matt Tucker
 */
public class LeafNode extends Node {

    private static final String genIdSeed = UUID.randomUUID().toString();
    private static final AtomicLong sequenceCounter = new AtomicLong();

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
     * Flag that indicates whether to send items to new subscribers.
     */
    private boolean sendItemSubscribe;
    /**
     * The last item published to this node.  In a cluster this may have occurred on a different cluster node.
     */
    private PublishedItem lastPublished;

    // TODO Add checking of max payload size. Return <not-acceptable> plus a application specific error condition of <payload-too-big/>.

    public LeafNode(PubSubService service, CollectionNode parentNode, String nodeID, JID creator) {
        super(service, parentNode, nodeID, creator);
        // Configure node with default values (get them from the pubsub service)
        DefaultNodeConfiguration defaultConfiguration = service.getDefaultNodeConfiguration(true);
        this.persistPublishedItems = defaultConfiguration.isPersistPublishedItems();
        this.maxPublishedItems = defaultConfiguration.getMaxPublishedItems();
        this.maxPayloadSize = defaultConfiguration.getMaxPayloadSize();
        this.sendItemSubscribe = defaultConfiguration.isSendItemSubscribe();
    }

    @Override
    protected void configure(FormField field) throws NotAcceptableException {
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
        else if ("pubsub#send_item_subscribe".equals(field.getVariable())) {
            values = field.getValues();
            booleanValue = (values.size() > 0 ? values.get(0) : "1");
            sendItemSubscribe = "1".equals(booleanValue);
        }
    }

    @Override
    void postConfigure(DataForm completedForm) {
        List<String> values;
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

    @Override
    protected void addFormFields(DataForm form, boolean isEditing) {
        super.addFormFields(form, isEditing);

        FormField typeField = form.getField("pubsub#node_type");
        typeField.addValue("leaf");
        
        FormField formField = form.addField();
        formField.setVariable("pubsub#send_item_subscribe");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(
                    LocaleUtils.getLocalizedString("pubsub.form.conf.send_item_subscribe"));
        }
        formField.addValue(sendItemSubscribe);

        formField = form.addField();
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

    @Override
    protected void deletingNode() {
    }

    public synchronized void setLastPublishedItem(PublishedItem item)
    {
        if ((lastPublished == null) || (item != null) && item.getCreationDate().after(lastPublished.getCreationDate()))
            lastPublished = item;
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
        return isPersistPublishedItems() || isPayloadDelivered();
    }

    /**
     * Publishes the list of items to the node. Event notifications will be sent to subscribers
     * for the new published event. The published event may or may not include an item. When the
     * node is not persistent and does not require payloads then an item is not going to be created
     * nore included in the event notification.<p>
     *
     * When an affiliate has many subscriptions to the node, the affiliate will get a
     * notification for each set of items that affected the same list of subscriptions.<p>
     *
     * When an item is included in the published event then a new {@link PublishedItem} is
     * going to be created and added to the list of published item. Each published item will
     * have a unique ID in the node scope. The new published item will be added to the end
     * of the published list to keep the cronological order. When the max number of published
     * items is exceeded then the oldest published items will be removed.<p>
     *
     * For performance reasons the newly added published items and the deleted items (if any)
     * are saved to the database using a background thread. Sending event notifications to
     * node subscribers may also use another thread to ensure good performance.<p>
     *
     * @param publisher the full JID of the user that sent the new published event.
     * @param itemElements list of dom4j elements that contain info about the published items.
     */
    public void publishItems(JID publisher, List<Element> itemElements) {
        List<PublishedItem> newPublishedItems = new ArrayList<>();
        if (isItemRequired()) {
            String itemID;
            Element payload;
            PublishedItem newItem;
            for (Element item : itemElements) {
                itemID = item.attributeValue("id");
                List entries = item.elements();
                payload = entries.isEmpty() ? null : (Element) entries.get(0);
                
                // Make sure that the published item has a unique ID if NOT assigned by publisher
                if (itemID == null) {
                    itemID = genIdSeed + sequenceCounter.getAndIncrement();
                }

                // Create a new published item
                newItem = new PublishedItem(this, publisher, itemID, new Date(CacheFactory.getClusterTime()));
                newItem.setPayload(payload);
                // Add the new item to the list of published items
                newPublishedItems.add(newItem);
                setLastPublishedItem(newItem);
                // Add the new published item to the queue of items to add to the database. The
                // queue is going to be processed by another thread
                if (isPersistPublishedItems()) {
                    PubSubPersistenceManager.savePublishedItem(newItem);
                }
            }
        }

        // Build event notification packet to broadcast to subscribers
        Message message = new Message();
        Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
        // Broadcast event notification to subscribers and parent node subscribers
        Set<NodeAffiliate> affiliatesToNotify = new HashSet<>(affiliates);
        // Get affiliates that are subscribed to a parent in the hierarchy of parent nodes
        for (CollectionNode parentNode : getParents()) {
            for (NodeSubscription subscription : parentNode.getSubscriptions()) {
                affiliatesToNotify.add(subscription.getAffiliate());
            }
        }
        // TODO Use another thread for this (if # of subscribers is > X)????
        for (NodeAffiliate affiliate : affiliatesToNotify) {
            affiliate.sendPublishedNotifications(message, event, this, newPublishedItems);
        }
    }

    /**
     * Deletes the list of published items from the node. Event notifications may be sent to
     * subscribers for the deleted items. When an affiliate has many subscriptions to the node,
     * the affiliate will get a notification for each set of items that affected the same list
     * of subscriptions.<p>
     *
     * For performance reasons the deleted published items are saved to the database
     * using a background thread. Sending event notifications to node subscribers may
     * also use another thread to ensure good performance.<p>
     *
     * @param toDelete list of items that were deleted from the node.
     */
    public void deleteItems(List<PublishedItem> toDelete) {
        // Remove deleted items from the database
        for (PublishedItem item : toDelete) {
            PubSubPersistenceManager.removePublishedItem(item);
            if (lastPublished != null && lastPublished.getID().equals(item.getID())) {
                lastPublished = null;
            }
        }
        if (isNotifiedOfRetract()) {
            // Broadcast notification deletion to subscribers
            // Build packet to broadcast to subscribers
            Message message = new Message();
            Element event =
                    message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
            // Send notification that items have been deleted to subscribers and parent node
            // subscribers
            Set<NodeAffiliate> affiliatesToNotify = new HashSet<>(affiliates);
            // Get affiliates that are subscribed to a parent in the hierarchy of parent nodes
            for (CollectionNode parentNode : getParents()) {
                for (NodeSubscription subscription : parentNode.getSubscriptions()) {
                    affiliatesToNotify.add(subscription.getAffiliate());
                }
            }
            // TODO Use another thread for this (if # of subscribers is > X)????
            for (NodeAffiliate affiliate : affiliatesToNotify) {
                affiliate.sendDeletionNotifications(message, event, this, toDelete);
            }
        }
    }

    /**
     * Sends an IQ result with the list of items published to the node. Item ID and payload
     * may be included in the result based on the node configuration.
     *
     * @param originalRequest the IQ packet sent by a subscriber (or anyone) to get the node items.
     * @param publishedItems the list of published items to send to the subscriber.
     * @param forceToIncludePayload true if the item payload should be include if one exists. When
     *        false the decision is up to the node.
     */
    void sendPublishedItems(IQ originalRequest, List<PublishedItem> publishedItems,
            boolean forceToIncludePayload) {
        IQ result = IQ.createResultIQ(originalRequest);
        Element pubsubElem = result.setChildElement("pubsub", "http://jabber.org/protocol/pubsub");
        Element items = pubsubElem.addElement("items");
        items.addAttribute("node", getNodeID());
        
        for (PublishedItem publishedItem : publishedItems) {
            Element item = items.addElement("item");
            if (isItemRequired()) {
                item.addAttribute("id", publishedItem.getID());
            }
            if ((forceToIncludePayload || isPayloadDelivered()) &&
                    publishedItem.getPayload() != null) {
                item.add(publishedItem.getPayload().createCopy());
            }
        }
        // Send the result
        service.send(result);
    }

    @Override
    public PublishedItem getPublishedItem(String itemID) {
        if (!isItemRequired()) {
            return null;
        }
        synchronized (this) {
            if (lastPublished != null && lastPublished.getID().equals(itemID)) {
                return lastPublished;
            }
        }
        return PubSubPersistenceManager.getPublishedItem(this, itemID);
    }

    @Override
    public List<PublishedItem> getPublishedItems() {
        return getPublishedItems(getMaxPublishedItems());
    }

    @Override
    public synchronized List<PublishedItem> getPublishedItems(int recentItems) {
        List<PublishedItem> publishedItems = PubSubPersistenceManager.getPublishedItems(this, recentItems);
        if (lastPublished != null) {
            // The persistent items may not contain the last item, if it wasn't persisted anymore (e.g. if node configuration changed).
            // Therefore check, if the last item has been persisted.
            boolean persistentItemsContainsLastItem = false;
            for (PublishedItem publishedItem : publishedItems) {
                if (publishedItem.getID().equals(lastPublished.getID())) {
                    persistentItemsContainsLastItem = true;
                    break;
                }
            }
            if (!persistentItemsContainsLastItem) {
                // And if not, include the last item.
                publishedItems.add(0, lastPublished);
                // Recheck the collection size, it might have one more element now (the last item).
                // Remove it, if it exceeds the max items.
                if (publishedItems.size() > recentItems) {
                    publishedItems.remove(publishedItems.size() - 1);
                }
            }
        }
        return publishedItems;
    }

    @Override
    public synchronized PublishedItem getLastPublishedItem() {
        if (lastPublished == null){
            lastPublished = PubSubPersistenceManager.getLastPublishedItem(this);
        }
        return lastPublished;
    }

    /**
     * Returns true if the last published item is going to be sent to new subscribers.
     *
     * @return true if the last published item is going to be sent to new subscribers.
     */
    @Override
    public boolean isSendItemSubscribe() {
        return sendItemSubscribe;
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

    void setSendItemSubscribe(boolean sendItemSubscribe) {
        this.sendItemSubscribe = sendItemSubscribe;
    }

    /**
     * Purges items that were published to the node. Only owners can request this operation.
     * This operation is only available for nodes configured to store items in the database. All
     * published items will be deleted with the exception of the last published item.
     */
    public void purge() {
        PubSubPersistenceManager.purgeNode(this);
        // Broadcast purge notification to subscribers
        // Build packet to broadcast to subscribers
        Message message = new Message();
        Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
        Element items = event.addElement("purge");
        items.addAttribute("node", nodeID);
        // Send notification that the node configuration has changed
        broadcastNodeEvent(message, false);
    }
}
