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

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

/**
 * A DefaultNodeConfiguration keeps the default configuration values for leaf or collection
 * nodes of a particular publish-subscribe service. New nodes created for the service 
 * will be initialized with the values defined in the default configuration.
 *
 * @author Matt Tucker
 */
public class DefaultNodeConfiguration implements Cacheable {

    /**
     * Flag indicating whether this default configutation belongs to a leaf node or not.
     */
    private boolean leaf;
    /**
     * Flag that indicates whether to deliver payloads with event notifications.
     */
    private boolean deliverPayloads;
    /**
     * The maximum payload size in bytes.
     */
    private int maxPayloadSize;
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
     * Flag that indicates whether to notify subscribers when the node configuration changes.
     */
    private boolean notifyConfigChanges;
    /**
     * Flag that indicates whether to notify subscribers when the node is deleted.
     */
    private boolean notifyDelete;
    /**
     * Flag that indicates whether to notify subscribers when items are removed from the node.
     */
    private boolean notifyRetract;
    /**
     * Flag that indicates whether to deliver notifications to available users only.
     */
    private boolean presenceBasedDelivery;
    /**
     * Flag that indicates whether to send items to new subscribers.
     */
    private boolean sendItemSubscribe = false;
    /**
     * Publisher model that specifies who is allowed to publish items to the node.
     */
    private PublisherModel publisherModel = PublisherModel.open;
    /**
     * Flag that indicates that subscribing and unsubscribing are enabled.
     */
    private boolean subscriptionEnabled;
    /**
     * Access model that specifies who is allowed to subscribe and retrieve items.
     */
    private AccessModel accessModel = AccessModel.open;
    /**
     * The default language of the node.
     */
    private String language = "";
    /**
     * Policy that defines whether owners or publisher should receive replies to items.
     */
    private Node.ItemReplyPolicy replyPolicy = Node.ItemReplyPolicy.owner;
    /**
     * Policy that defines who may associate leaf nodes with a collection.
     */
    private CollectionNode.LeafNodeAssociationPolicy associationPolicy =
            CollectionNode.LeafNodeAssociationPolicy.all;
    /**
     * Max number of leaf nodes that this collection node might have. A value of -1 means
     * that there is no limit.
     */
    private int maxLeafNodes = -1;

    public DefaultNodeConfiguration(boolean isLeafType) {
        this.leaf = isLeafType;
    }

    /**
     * Returns true if this default configutation belongs to a leaf node.
     *
     * @return true if this default configutation belongs to a leaf node.
     */
    public boolean isLeaf() {
        return leaf;
    }

    /**
     * Returns true if payloads are going to be delivered with event notifications.
     *
     * @return true if payloads are going to be delivered with event notifications.
     */
    public boolean isDeliverPayloads() {
        return deliverPayloads;
    }

    /**
     * Returns the maximum payload size in bytes.
     *
     * @return the maximum payload size in bytes.
     */
    public int getMaxPayloadSize() {
        return maxPayloadSize;
    }

    /**
     * Returns true if items are going to be persisted in a storage. Note that when the
     * variable is false then the last published item is the only items being saved
     * to the backend storage.
     *
     * @return true if items are going to be persisted in a storage.
     */
    public boolean isPersistPublishedItems() {
        return persistPublishedItems;
    }

    /**
     * Returns the maximum number of published items to persist. Note that all nodes are going
     * to persist their published items. The only difference is the number of the last published
     * items to be persisted. Even nodes that are configured to not use persitent items are going
     * to save the last published item.
     *
     * @return the maximum number of published items to persist.
     */
    public int getMaxPublishedItems() {
        return maxPublishedItems;
    }

    /**
     * Returns true if subscribers are going to be notified when node configuration changes.
     *
     * @return true if subscribers are going to be notified when node configuration changes.
     */
    public boolean isNotifyConfigChanges() {
        return notifyConfigChanges;
    }

    /**
     * Returns true if subscribers are going to be notified when node is deleted.
     *
     * @return true if subscribers are going to be notified when node is deleted.
     */
    public boolean isNotifyDelete() {
        return notifyDelete;
    }

    /**
     * Returns true if subscribers are going to be notified when items are removed from the node.
     *
     * @return true if subscribers are going to be notified when items are removed from the node.
     */
    public boolean isNotifyRetract() {
        return notifyRetract;
    }

    /**
     * Returns true if notifications are going to be delivered only to available users.
     *
     * @return true if notifications are going to be delivered only to available users.
     */
    public boolean isPresenceBasedDelivery() {
        return presenceBasedDelivery;
    }

    /**
     * Returns true if new subscribers are going to receive new items once subscribed.
     *
     * @return true if new subscribers are going to receive new items once subscribed.
     */
    public boolean isSendItemSubscribe() {
        return sendItemSubscribe;
    }

    /**
     * Returnes the publisher model that specifies who is allowed to publish items to the node.
     *
     * @return the publisher model that specifies who is allowed to publish items to the node.
     */
    public PublisherModel getPublisherModel() {
        return publisherModel;
    }

    /**
     * Returns true if subscribing and unsubscribing are enabled.
     *
     * @return true if subscribing and unsubscribing are enabled.
     */
    public boolean isSubscriptionEnabled() {
        return subscriptionEnabled;
    }

    /**
     * Returns the access model that specifies who is allowed to subscribe and retrieve items.
     *
     * @return the access model that specifies who is allowed to subscribe and retrieve items.
     */
    public AccessModel getAccessModel() {
        return accessModel;
    }

    /**
     * Returns the default language of the node.
     *
     * @return the default language of the node.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Returns the policy that defines whether owners or publisher should receive
     * replies to items.
     *
     * @return the policy that defines whether owners or publisher should receive 
     *        replies to items.
     */
    public Node.ItemReplyPolicy getReplyPolicy() {
        return replyPolicy;
    }

    /**
     * Returns the policy that defines who may associate leaf nodes with a collection.
     *
     * @return the policy that defines who may associate leaf nodes with a collection.
     */
    public CollectionNode.LeafNodeAssociationPolicy getAssociationPolicy() {
        return associationPolicy;
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
     * Sets if payloads are going to be delivered with event notifications.
     *
     * @param deliverPayloads true if payloads are going to be delivered with event notifications.
     */
    public void setDeliverPayloads(boolean deliverPayloads) {
        this.deliverPayloads = deliverPayloads;
    }

    /**
     * Sets the maximum payload size in bytes.
     *
     * @param maxPayloadSize the maximum payload size in bytes.
     */
    public void setMaxPayloadSize(int maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    /**
     * Sets if items are going to be persisted in a storage. Note that when the
     * variable is false then the last published item is the only items being saved
     * to the backend storage.
     *
     * @param persistPublishedItems true if items are going to be persisted in a storage.
     */
    public void setPersistPublishedItems(boolean persistPublishedItems) {
        this.persistPublishedItems = persistPublishedItems;
    }

    /**
     * Sets the maximum number of published items to persist. Note that all nodes are going
     * to persist their published items. The only difference is the number of the last published
     * items to be persisted. Even nodes that are configured to not use persitent items are going
     * to save the last published item.
     *
     * @param maxPublishedItems the maximum number of published items to persist.
     */
    public void setMaxPublishedItems(int maxPublishedItems) {
        this.maxPublishedItems = maxPublishedItems;
    }

    /**
     * Sets if subscribers are going to be notified when node configuration changes.
     *
     * @param notifyConfigChanges true if subscribers are going to be notified when node
     *        configuration changes.
     */
    public void setNotifyConfigChanges(boolean notifyConfigChanges) {
        this.notifyConfigChanges = notifyConfigChanges;
    }

    /**
     * Sets if subscribers are going to be notified when node is deleted.
     *
     * @param notifyDelete true if subscribers are going to be notified when node is deleted.
     */
    public void setNotifyDelete(boolean notifyDelete) {
        this.notifyDelete = notifyDelete;
    }

    /**
     * Sets if subscribers are going to be notified when items are removed from the node.
     *
     * @param notifyRetract true if subscribers are going to be notified when items are removed
     *        from the node.
     */
    public void setNotifyRetract(boolean notifyRetract) {
        this.notifyRetract = notifyRetract;
    }

    /**
     * Sets if notifications are going to be delivered only to available users.
     *
     * @param presenceBasedDelivery true if notifications are going to be delivered only to
     *        available users.
     */
    public void setPresenceBasedDelivery(boolean presenceBasedDelivery) {
        this.presenceBasedDelivery = presenceBasedDelivery;
    }

    /**
     * Sets if new subscribers are going to receive new items once subscribed.
     *
     * @param sendItemSubscribe true if new subscribers are going to receive new items
     *        once subscribed.
     */
    public void setSendItemSubscribe(boolean sendItemSubscribe) {
        this.sendItemSubscribe = sendItemSubscribe;
    }

    /**
     * Sets the publisher model that specifies who is allowed to publish items to the node.
     *
     * @param publisherModel the publisher model that specifies who is allowed to publish
     *        items to the node.
     */
    public void setPublisherModel(PublisherModel publisherModel) {
        this.publisherModel = publisherModel;
    }

    /**
     * Sets if subscribing and unsubscribing are enabled.
     *
     * @param subscriptionEnabled true if subscribing and unsubscribing are enabled.
     */
    public void setSubscriptionEnabled(boolean subscriptionEnabled) {
        this.subscriptionEnabled = subscriptionEnabled;
    }

    /**
     * Sets the access model that specifies who is allowed to subscribe and retrieve items.
     *
     * @param accessModel the access model that specifies who is allowed to subscribe and
     *        retrieve items.
     */
    public void setAccessModel(AccessModel accessModel) {
        this.accessModel = accessModel;
    }

    /**
     * Sets the default language of the node.
     *
     * @param language the default language of the node.
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Sets the policy that defines whether owners or publisher should receive replies to items.
     *
     * @param replyPolicy the policy that defines whether owners or publisher should receive
     *        replies to items.
     */
    public void setReplyPolicy(Node.ItemReplyPolicy replyPolicy) {
        this.replyPolicy = replyPolicy;
    }

    /**
     * Sets the policy that defines who may associate leaf nodes with a collection.
     *
     * @param associationPolicy the policy that defines who may associate leaf nodes
     *        with a collection.
     */
    public void setAssociationPolicy(CollectionNode.LeafNodeAssociationPolicy associationPolicy) {
        this.associationPolicy = associationPolicy;
    }

    /**
     * Sets the max number of leaf nodes that this collection node might have. A value of
     * -1 means that there is no limit.
     *
     * @param maxLeafNodes the max number of leaf nodes that this collection node might have.
     */
    public void setMaxLeafNodes(int maxLeafNodes) {
        this.maxLeafNodes = maxLeafNodes;
    }


    public DataForm getConfigurationForm() {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("pubsub.form.default.title"));
        form.addInstruction(LocaleUtils.getLocalizedString("pubsub.form.default.instruction"));
        // Add the form fields and configure them for edition

        FormField formField = form.addField();
        formField.setVariable("FORM_TYPE");
        formField.setType(FormField.Type.hidden);
        formField.addValue("http://jabber.org/protocol/pubsub#node_config");

        formField = form.addField();
        formField.setVariable("pubsub#subscribe");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.subscribe"));
        formField.addValue(subscriptionEnabled);

        formField = form.addField();
        formField.setVariable("pubsub#deliver_payloads");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.deliver_payloads"));
        formField.addValue(deliverPayloads);

        formField = form.addField();
        formField.setVariable("pubsub#notify_config");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.notify_config"));
        formField.addValue(notifyConfigChanges);

        formField = form.addField();
        formField.setVariable("pubsub#notify_delete");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.notify_delete"));
        formField.addValue(notifyDelete);

        formField = form.addField();
        formField.setVariable("pubsub#notify_retract");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.notify_retract"));
        formField.addValue(notifyRetract);

        formField = form.addField();
        formField.setVariable("pubsub#presence_based_delivery");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.presence_based"));
        formField.addValue(presenceBasedDelivery);

        if (leaf) {
            formField = form.addField();
            formField.setVariable("pubsub#send_item_subscribe");
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(
                    LocaleUtils.getLocalizedString("pubsub.form.conf.send_item_subscribe"));
            formField.addValue(sendItemSubscribe);

            formField = form.addField();
            formField.setVariable("pubsub#persist_items");
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.persist_items"));
            formField.addValue(persistPublishedItems);

            formField = form.addField();
            formField.setVariable("pubsub#max_items");
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.max_items"));
            formField.addValue(maxPublishedItems);

            formField = form.addField();
            formField.setVariable("pubsub#max_payload_size");
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.max_payload_size"));
            formField.addValue(maxPayloadSize);
        }

        formField = form.addField();
        formField.setVariable("pubsub#access_model");
        formField.setType(FormField.Type.list_single);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.access_model"));
        formField.addOption(null, AccessModel.authorize.getName());
        formField.addOption(null, AccessModel.open.getName());
        formField.addOption(null, AccessModel.presence.getName());
        formField.addOption(null, AccessModel.roster.getName());
        formField.addOption(null, AccessModel.whitelist.getName());
        formField.addValue(accessModel.getName());

        formField = form.addField();
        formField.setVariable("pubsub#publish_model");
        formField.setType(FormField.Type.list_single);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.publish_model"));
        formField.addOption(null, PublisherModel.publishers.getName());
        formField.addOption(null, PublisherModel.subscribers.getName());
        formField.addOption(null, PublisherModel.open.getName());
        formField.addValue(publisherModel.getName());

        formField = form.addField();
        formField.setVariable("pubsub#language");
        formField.setType(FormField.Type.text_single);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.language"));
        formField.addValue(language);

        formField = form.addField();
        formField.setVariable("pubsub#itemreply");
        formField.setType(FormField.Type.list_single);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.itemreply"));
        if (replyPolicy != null) {
            formField.addValue(replyPolicy.name());
        }

        if (!leaf) {
            formField = form.addField();
            formField.setVariable("pubsub#leaf_node_association_policy");
            formField.setType(FormField.Type.list_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.leaf_node_association"));
            formField.addOption(null, CollectionNode.LeafNodeAssociationPolicy.all.name());
            formField.addOption(null, CollectionNode.LeafNodeAssociationPolicy.owners.name());
            formField.addOption(null, CollectionNode.LeafNodeAssociationPolicy.whitelist.name());
            formField.addValue(associationPolicy.name());

            formField = form.addField();
            formField.setVariable("pubsub#leaf_nodes_max");
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.leaf_nodes_max"));
            formField.addValue(maxLeafNodes);
        }

        return form;
    }

    /**
     * Returns the approximate size of the Object in bytes. The size should be
     * considered to be a best estimate of how much memory the Object occupies
     * and may be based on empirical trials or dynamic calculations.<p>
     *
     * @return the size of the Object in bytes.
     */
    @Override
    public int getCachedSize() throws CannotCalculateSizeException
    {
        int size = CacheSizes.sizeOfObject(); // overhead of the class itself.
        size += (9 * CacheSizes.sizeOfBoolean()); // nine boolean properties.
        size += (3 * CacheSizes.sizeOfInt()); // three int properties.
        size += (4 * CacheSizes.sizeOfObject()); // 4 references to other classes / enums
        size += CacheSizes.sizeOfString( language );
        return size;
    }
}
