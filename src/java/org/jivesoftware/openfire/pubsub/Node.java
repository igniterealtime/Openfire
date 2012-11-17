/**
 * $RCSfile: $
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

package org.jivesoftware.openfire.pubsub;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.pubsub.cluster.AffiliationTask;
import org.jivesoftware.openfire.pubsub.cluster.CancelSubscriptionTask;
import org.jivesoftware.openfire.pubsub.cluster.ModifySubscriptionTask;
import org.jivesoftware.openfire.pubsub.cluster.NewSubscriptionTask;
import org.jivesoftware.openfire.pubsub.cluster.RemoveNodeTask;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * A virtual location to which information can be published and from which event
 * notifications and/or payloads can be received (in other pubsub systems, this may
 * be labelled a "topic").
 *
 * @author Matt Tucker
 */
public abstract class Node {

    public static final String PUBSUB_SVC_ID = XMPPServer.getInstance().getPubSubModule().getServiceID();

    /**
     * Reference to the publish and subscribe service.
     */
    protected PubSubService service;
    /**
     * Keeps the Node that is containing this node.
     */
    protected CollectionNode parent;
    /**
     * The unique identifier for a node within the context of a pubsub service.
     */
    protected String nodeID;
    /**
     * Flag that indicates whether to deliver payloads with event notifications.
     */
    protected boolean deliverPayloads;
    /**
     * Policy that defines whether owners or publisher should receive replies to items.
     */
    protected ItemReplyPolicy replyPolicy;
    /**
     * Flag that indicates whether to notify subscribers when the node configuration changes.
     */
    protected boolean notifyConfigChanges;
    /**
     * Flag that indicates whether to notify subscribers when the node is deleted.
     */
    protected boolean notifyDelete;
    /**
     * Flag that indicates whether to notify subscribers when items are removed from the node.
     */
    protected boolean notifyRetract;
    /**
     * Flag that indicates whether to deliver notifications to available users only.
     */
    protected boolean presenceBasedDelivery;
    /**
     * Publisher model that specifies who is allowed to publish items to the node.
     */
    protected PublisherModel publisherModel = PublisherModel.open;
    /**
     * Flag that indicates that subscribing and unsubscribing are enabled.
     */
    protected boolean subscriptionEnabled;
    /**
     * Access model that specifies who is allowed to subscribe and retrieve items.
     */
    protected AccessModel accessModel = AccessModel.open;
    /**
     * The roster group(s) allowed to subscribe and retrieve items.
     */
    protected Collection<String> rosterGroupsAllowed = new ArrayList<String>();
    /**
     * List of multi-user chat rooms to specify for replyroom.
     */
    protected Collection<JID> replyRooms = new ArrayList<JID>();
    /**
     * List of JID(s) to specify for replyto.
     */
    protected Collection<JID> replyTo = new ArrayList<JID>();
    /**
     * The type of payload data to be provided at the node. Usually specified by the
     * namespace of the payload (if any).
     */
    protected String payloadType = "";
    /**
     * The URL of an XSL transformation which can be applied to payloads in order
     * to generate an appropriate message body element.
     */
    protected String bodyXSLT = "";
    /**
     * The URL of an XSL transformation which can be applied to the payload format
     * in order to generate a valid Data Forms result that the client could display
     * using a generic Data Forms rendering engine.
     */
    protected String dataformXSLT = "";
    /**
     * Indicates if the node is present in the database.
     */
    private boolean savedToDB = false;
    /**
     * The datetime when the node was created.
     */
    protected Date creationDate;
    /**
     * The last date when the ndoe's configuration was modified.
     */
    private Date modificationDate;
    /**
     * The JID of the node creator.
     */
    protected JID creator;
    /**
     * A description of the node.
     */
    protected String description = "";
    /**
     * The default language of the node.
     */
    protected String language = "";
    /**
     * The JIDs of those to contact with questions.
     */
    protected Collection<JID> contacts = new ArrayList<JID>();
    /**
     * The name of the node.
     */
    protected String name = "";
    /**
     * Flag that indicates whether new subscriptions should be configured to be active.
     */
    protected boolean subscriptionConfigurationRequired = false;
    /**
     * The JIDs of those who have an affiliation with this node. When subscriptionModel is
     * whitelist then this collection acts as the white list (unless user is an outcast)
     */
    protected Collection<NodeAffiliate> affiliates = new CopyOnWriteArrayList<NodeAffiliate>();
    /**
     * Map that contains the current subscriptions to the node. A user may have more than one
     * subscription. Each subscription is uniquely identified by its ID.
     * Key: Subscription ID, Value: the subscription.
     */
    protected Map<String, NodeSubscription> subscriptionsByID =
            new ConcurrentHashMap<String, NodeSubscription>();
    /**
     * Map that contains the current subscriptions to the node. This map should be used only
     * when node is not configured to allow multiple subscriptions. When multiple subscriptions
     * is not allowed the subscriptions can be searched by the subscriber JID. Otherwise searches
     * should be done using the subscription ID.
     * Key: Subscriber full JID, Value: the subscription.
     */
    protected Map<String, NodeSubscription> subscriptionsByJID =
            new ConcurrentHashMap<String, NodeSubscription>();

    Node(PubSubService service, CollectionNode parent, String nodeID, JID creator) {
        this.service = service;
        this.parent = parent;
        this.nodeID = nodeID;
        this.creator = creator;
        long startTime = System.currentTimeMillis();
        this.creationDate = new Date(startTime);
        this.modificationDate = new Date(startTime);
        // Configure node with default values (get them from the pubsub service)
        DefaultNodeConfiguration defaultConfiguration =
                service.getDefaultNodeConfiguration(!isCollectionNode());
        this.subscriptionEnabled = defaultConfiguration.isSubscriptionEnabled();
        this.deliverPayloads = defaultConfiguration.isDeliverPayloads();
        this.notifyConfigChanges = defaultConfiguration.isNotifyConfigChanges();
        this.notifyDelete = defaultConfiguration.isNotifyDelete();
        this.notifyRetract = defaultConfiguration.isNotifyRetract();
        this.presenceBasedDelivery = defaultConfiguration.isPresenceBasedDelivery();
        this.accessModel = defaultConfiguration.getAccessModel();
        this.publisherModel = defaultConfiguration.getPublisherModel();
        this.language = defaultConfiguration.getLanguage();
        this.replyPolicy = defaultConfiguration.getReplyPolicy();
    }

    /**
     * Adds a new affiliation or updates an existing affiliation of the specified entity JID
     * to become a node owner.
     *
     * @param jid the JID of the user being added as a node owner.
     * @return the newly created or modified affiliation to the node.
     */
    public NodeAffiliate addOwner(JID jid) {
        NodeAffiliate nodeAffiliate = addAffiliation(jid, NodeAffiliate.Affiliation.owner);
        Collection<NodeSubscription> subscriptions = getSubscriptions(jid);
        if (subscriptions.isEmpty()) {
            // User does not have a subscription with the node so create a default one
            createSubscription(null, jid, jid, false, null);
        }
        else {
            // Approve any pending subscription
            for (NodeSubscription subscription : getSubscriptions(jid)) {
                if (subscription.isAuthorizationPending()) {
                    subscription.approved();
                }
            }
        }
        return nodeAffiliate;
    }

    /**
     * Removes the owner affiliation of the specified entity JID. If the user that is
     * no longer an owner was subscribed to the node then his affiliation will be of
     * type {@link NodeAffiliate.Affiliation#none}.
     *
     * @param jid the JID of the user being removed as a node owner.
     */
    public void removeOwner(JID jid) {
        // Get the current affiliation of the specified JID
        NodeAffiliate affiliate = getAffiliate(jid);
        if (affiliate.getSubscriptions().isEmpty()) {
            removeAffiliation(jid, NodeAffiliate.Affiliation.owner);
            removeSubscriptions(jid);
        }
        else {
            // The user has subscriptions so change affiliation to NONE
            addNoneAffiliation(jid);
        }
    }

    /**
     * Adds a new affiliation or updates an existing affiliation of the specified entity JID
     * to become a node publisher.
     *
     * @param jid the JID of the user being added as a node publisher.
     * @return the newly created or modified affiliation to the node.
     */
    public NodeAffiliate addPublisher(JID jid) {
        NodeAffiliate nodeAffiliate = addAffiliation(jid, NodeAffiliate.Affiliation.publisher);
        Collection<NodeSubscription> subscriptions = getSubscriptions(jid);
        if (subscriptions.isEmpty()) {
            // User does not have a subscription with the node so create a default one
            createSubscription(null, jid, jid, false, null);
        }
        else {
            // Approve any pending subscription
            for (NodeSubscription subscription : getSubscriptions(jid)) {
                if (subscription.isAuthorizationPending()) {
                    subscription.approved();
                }
            }
        }
        return nodeAffiliate;
    }

    /**
     * Removes the publisher affiliation of the specified entity JID. If the user that is
     * no longer a publisher was subscribed to the node then his affiliation will be of
     * type {@link NodeAffiliate.Affiliation#none}.
     *
     * @param jid the JID of the user being removed as a node publisher.
     */
    public void removePublisher(JID jid) {
        // Get the current affiliation of the specified JID
        NodeAffiliate affiliate = getAffiliate(jid);
        if (affiliate.getSubscriptions().isEmpty()) {
            removeAffiliation(jid, NodeAffiliate.Affiliation.publisher);
            removeSubscriptions(jid);
        }
        else {
            // The user has subscriptions so change affiliation to NONE
            addNoneAffiliation(jid);
        }
    }

    /**
     * Adds a new affiliation or updates an existing affiliation of the specified entity JID
     * to become a none affiliate. Affiliates of type none are allowed to subscribe to the node.
     *
     * @param jid the JID of the user with affiliation "none".
     * @return the newly created or modified affiliation to the node.
     */
    public NodeAffiliate addNoneAffiliation(JID jid) {
        return addAffiliation(jid, NodeAffiliate.Affiliation.none);
    }

    /**
     * Sets that the specified entity is an outcast of the node. Outcast entities are not
     * able to publish or subscribe to the node. Existing subscriptions will be deleted.
     *
     * @param jid the JID of the user that is no longer able to publish or subscribe to the node.
     * @return the newly created or modified affiliation to the node.
     */
    public NodeAffiliate addOutcast(JID jid) {
        NodeAffiliate nodeAffiliate = addAffiliation(jid, NodeAffiliate.Affiliation.outcast);
        // Delete existing subscriptions
        removeSubscriptions(jid);
        return nodeAffiliate;
    }

    /**
     * Removes the banning to subscribe to the node for the specified entity.
     *
     * @param jid the JID of the user that is no longer an outcast.
     */
    public void removeOutcast(JID jid) {
        removeAffiliation(jid, NodeAffiliate.Affiliation.outcast);
    }

    private NodeAffiliate addAffiliation(JID jid, NodeAffiliate.Affiliation affiliation) {
        boolean created = false;
        // Get the current affiliation of the specified JID
        NodeAffiliate affiliate = getAffiliate(jid);
        // Check if the user already has the same affiliation
        if (affiliate != null && affiliation == affiliate.getAffiliation()) {
            // Do nothing since the user already has the expected affiliation
            return affiliate;
        }
        else if (affiliate != null) {
            // Update existing affiliation with new affiliation type
            affiliate.setAffiliation(affiliation);
        }
        else {
            // User did not have any affiliation with the node so create a new one
            affiliate = new NodeAffiliate(this, jid);
            affiliate.setAffiliation(affiliation);
            addAffiliate(affiliate);
            created = true;
        }

        if (savedToDB) {
            // Add or update the affiliate in the database
            PubSubPersistenceManager.saveAffiliation(this, affiliate, created);
        }
        
        // Update the other members with the new affiliation
        CacheFactory.doClusterTask(new AffiliationTask(this, jid, affiliation));

        return affiliate;
    }

    private void removeAffiliation(JID jid, NodeAffiliate.Affiliation affiliation) {
        // Get the current affiliation of the specified JID
        NodeAffiliate affiliate = getAffiliate(jid);
        // Check if the current affiliatin of the user is the one to remove
        if (affiliate != null && affiliation == affiliate.getAffiliation()) {
            removeAffiliation(affiliate);
        }
    }

    private void removeAffiliation(NodeAffiliate affiliate) {
        // Remove the existing affiliate from the list in memory
        affiliates.remove(affiliate);
        if (savedToDB) {
            // Remove the affiliate from the database
            PubSubPersistenceManager.removeAffiliation(this, affiliate);
        }
    }

    /**
     * Removes all subscriptions owned by the specified entity.
     *
     * @param owner the owner of the subscriptions to be cancelled.
     */
    private void removeSubscriptions(JID owner) {
        for (NodeSubscription subscription : getSubscriptions(owner)) {
            cancelSubscription(subscription);
        }
    }

    /**
     * Returns the list of subscriptions owned by the specified user. The subscription owner
     * may have more than one subscription based on {@link #isMultipleSubscriptionsEnabled()}.
     * Each subscription may have a different subscription JID if the owner wants to receive
     * notifications in different resources (or even JIDs).
     *
     * @param owner the owner of the subscriptions.
     * @return the list of subscriptions owned by the specified user.
     */
    public Collection<NodeSubscription> getSubscriptions(JID owner) {
        Collection<NodeSubscription> subscriptions = new ArrayList<NodeSubscription>();
        for (NodeSubscription subscription : subscriptionsByID.values()) {
            if (owner.equals(subscription.getOwner())) {
                subscriptions.add(subscription);
            }
        }
        return subscriptions;
    }

    /**
     * Returns all subscriptions to the node.
     *
     * @return all subscriptions to the node.
     */
    Collection<NodeSubscription> getSubscriptions() {
        return subscriptionsByID.values();
    }

    /**
     * Returns all subscriptions to the node. If multiple subscriptions are enabled,
     * this method returns the subscriptions by <tt>subId</tt>, otherwise it returns
     * the subscriptions by {@link JID}.
     *
     * @return All subscriptions to the node.
     */
    public Collection<NodeSubscription> getAllSubscriptions() {
        if (isMultipleSubscriptionsEnabled()) {
            return subscriptionsByID.values();
        } else {
            return subscriptionsByJID.values();
        }
    }

    /**
     * Returns the {@link NodeAffiliate} of the specified {@link JID} or <tt>null</tt>
     * if none was found. Users that have a subscription with the node will ALWAYS
     * have an affiliation even if the affiliation is of type <tt>none</tt>.
     *
     * @param jid the JID of the user to look his affiliation with this node.
     * @return the NodeAffiliate of the specified JID or <tt>null</tt> if none was found.
     */
    public NodeAffiliate getAffiliate(JID jid) {
        for (NodeAffiliate affiliate : affiliates) {
            if (jid.equals(affiliate.getJID())) {
                return affiliate;
            }
        }
        return null;
    }

    /**
     * Returns a collection with the JID of the node owners. Entities that are node owners have
     * an affiliation of {@link NodeAffiliate.Affiliation#owner}. Owners are allowed to purge
     * and delete the node. Moreover, owners may also get The collection can be modified
     * since it represents a snapshot.
     *
     * @return a collection with the JID of the node owners.
     */
    public Collection<JID> getOwners() {
        Collection<JID> jids = new ArrayList<JID>();
        for (NodeAffiliate affiliate : affiliates) {
            if (NodeAffiliate.Affiliation.owner == affiliate.getAffiliation()) {
                jids.add(affiliate.getJID());
            }
        }
        return jids;
    }

    /**
     * Returns a collection with the JID of the enitities with an affiliation of
     * {@link NodeAffiliate.Affiliation#publisher}. When using the publisher model
     * {@link org.jivesoftware.openfire.pubsub.models.OpenPublisher} anyone may publish
     * to the node so this collection may be empty or may not contain the complete list
     * of publishers. The returned collection can be modified since it represents a snapshot.
     *
     * @return a collection with the JID of the enitities with an affiliation of publishers.
     */
    public Collection<JID> getPublishers() {
        Collection<JID> jids = new ArrayList<JID>();
        for (NodeAffiliate affiliate : affiliates) {
            if (NodeAffiliate.Affiliation.publisher == affiliate.getAffiliation()) {
                jids.add(affiliate.getJID());
            }
        }
        return jids;
    }

    /**
     * Changes the node configuration based on the completed data form. Only owners or
     * sysadmins are allowed to change the node configuration. The completed data form
     * cannot remove all node owners. An exception is going to be thrown if the new form
     * tries to leave the node without owners.
     *
     * @param completedForm the completed data form.
     * @throws NotAcceptableException if completed data form tries to leave the node without owners.
     */
    public void configure(DataForm completedForm) throws NotAcceptableException {
        boolean wasPresenceBased = isPresenceBasedDelivery();

        if (DataForm.Type.cancel.equals(completedForm.getType())) {
            // Existing node configuration is applied (i.e. nothing is changed)
        }
        else if (DataForm.Type.submit.equals(completedForm.getType())) {
            List<String> values;
            String booleanValue;

            // Get the new list of owners
            FormField ownerField = completedForm.getField("pubsub#owner");
            boolean ownersSent = ownerField != null;
            List<JID> owners = new ArrayList<JID>();
            if (ownersSent) {
                for (String value : ownerField.getValues()) {
                    try {
                        owners.add(new JID(value));
                    }
                    catch (Exception e) {
                        // Do nothing
                    }
                }
            }

            // Answer a not-acceptable error if all the current owners will be removed
            if (ownersSent && owners.isEmpty()) {
                throw new NotAcceptableException();
            }

            for (FormField field : completedForm.getFields()) {
                if ("FORM_TYPE".equals(field.getVariable())) {
                    // Do nothing
                }
                else if ("pubsub#deliver_payloads".equals(field.getVariable())) {
                    values = field.getValues();
                    booleanValue = (values.size() > 0 ? values.get(0) : "1");
                    deliverPayloads = "1".equals(booleanValue);
                }
                else if ("pubsub#notify_config".equals(field.getVariable())) {
                    values = field.getValues();
                    booleanValue = (values.size() > 0 ? values.get(0) : "1");
                    notifyConfigChanges = "1".equals(booleanValue);
                }
                else if ("pubsub#notify_delete".equals(field.getVariable())) {
                    values = field.getValues();
                    booleanValue = (values.size() > 0 ? values.get(0) : "1");
                    notifyDelete = "1".equals(booleanValue);
                }
                else if ("pubsub#notify_retract".equals(field.getVariable())) {
                    values = field.getValues();
                    booleanValue = (values.size() > 0 ? values.get(0) : "1");
                    notifyRetract = "1".equals(booleanValue);
                }
                else if ("pubsub#presence_based_delivery".equals(field.getVariable())) {
                    values = field.getValues();
                    booleanValue = (values.size() > 0 ? values.get(0) : "1");
                    presenceBasedDelivery = "1".equals(booleanValue);
                }
                else if ("pubsub#subscribe".equals(field.getVariable())) {
                    values = field.getValues();
                    booleanValue = (values.size() > 0 ? values.get(0) : "1");
                    subscriptionEnabled = "1".equals(booleanValue);
                }
                else if ("pubsub#subscription_required".equals(field.getVariable())) {
                    // TODO Replace this variable for the one defined in the JEP (once one is defined)
                    values = field.getValues();
                    booleanValue = (values.size() > 0 ? values.get(0) : "1");
                    subscriptionConfigurationRequired = "1".equals(booleanValue);
                }
                else if ("pubsub#type".equals(field.getVariable())) {
                    values = field.getValues();
                    payloadType = values.size() > 0 ? values.get(0) : " ";
                }
                else if ("pubsub#body_xslt".equals(field.getVariable())) {
                    values = field.getValues();
                    bodyXSLT = values.size() > 0 ? values.get(0) : " ";
                }
                else if ("pubsub#dataform_xslt".equals(field.getVariable())) {
                    values = field.getValues();
                    dataformXSLT = values.size() > 0 ? values.get(0) : " ";
                }
                else if ("pubsub#access_model".equals(field.getVariable())) {
                    values = field.getValues();
                    if (values.size() > 0)  {
                        accessModel = AccessModel.valueOf(values.get(0));
                    }
                }
                else if ("pubsub#publish_model".equals(field.getVariable())) {
                    values = field.getValues();
                    if (values.size() > 0)  {
                        publisherModel = PublisherModel.valueOf(values.get(0));
                    }
                }
                else if ("pubsub#roster_groups_allowed".equals(field.getVariable())) {
                    // Get the new list of roster group(s) allowed to subscribe and retrieve items
                    rosterGroupsAllowed = new ArrayList<String>();
                    for (String value : field.getValues()) {
                        addAllowedRosterGroup(value);
                    }
                }
                else if ("pubsub#contact".equals(field.getVariable())) {
                    // Get the new list of users that may be contacted with questions
                    contacts = new ArrayList<JID>();
                    for (String value : field.getValues()) {
                        try {
                            addContact(new JID(value));
                        }
                        catch (Exception e) {
                            // Do nothing
                        }
                    }
                }
                else if ("pubsub#description".equals(field.getVariable())) {
                    values = field.getValues();
                    description = values.size() > 0 ? values.get(0) : " ";
                }
                else if ("pubsub#language".equals(field.getVariable())) {
                    values = field.getValues();
                    language = values.size() > 0 ? values.get(0) : " ";
                }
                else if ("pubsub#title".equals(field.getVariable())) {
                    values = field.getValues();
                    name = values.size() > 0 ? values.get(0) : " ";
                }
                else if ("pubsub#itemreply".equals(field.getVariable())) {
                    values = field.getValues();
                    if (values.size() > 0)  {
                        replyPolicy = ItemReplyPolicy.valueOf(values.get(0));
                    }
                }
                else if ("pubsub#replyroom".equals(field.getVariable())) {
                    // Get the new list of multi-user chat rooms to specify for replyroom
                    replyRooms = new ArrayList<JID>();
                    for (String value : field.getValues()) {
                        try {
                            addReplyRoom(new JID(value));
                        }
                        catch (Exception e) {
                            // Do nothing
                        }
                    }
                }
                else if ("pubsub#replyto".equals(field.getVariable())) {
                    // Get the new list of JID(s) to specify for replyto
                    replyTo = new ArrayList<JID>();
                    for (String value : field.getValues()) {
                        try {
                            addReplyTo(new JID(value));
                        }
                        catch (Exception e) {
                            // Do nothing
                        }
                    }
                }
                else {
                    // Let subclasses be configured by specified fields
                    configure(field);
                }
            }

            // Set new list of owners of the node
            if (ownersSent) {
                // Calculate owners to remove and remove them from the DB
                Collection<JID> oldOwners = getOwners();
                oldOwners.removeAll(owners);
                for (JID jid : oldOwners) {
                    removeOwner(jid);
                }

                // Calculate new owners and add them to the DB
                owners.removeAll(getOwners());
                for (JID jid : owners) {
                    addOwner(jid);
                }
            }
            // TODO Before removing owner or admin check if user was changed from admin to owner or vice versa. This way his susbcriptions are not going to be deleted.
            // Set the new list of publishers
            FormField publisherField = completedForm.getField("pubsub#publisher");
            if (publisherField != null) {
                // New list of publishers was sent to update publishers of the node
                List<JID> publishers = new ArrayList<JID>();
                for (String value : publisherField.getValues()) {
                    try {
                        publishers.add(new JID(value));
                    }
                    catch (Exception e) {
                        // Do nothing
                    }
                }
                // Calculate publishers to remove and remove them from the DB
                Collection<JID> oldPublishers = getPublishers();
                oldPublishers.removeAll(publishers);
                for (JID jid : oldPublishers) {
                    removePublisher(jid);
                }

                // Calculate new publishers and add them to the DB
                publishers.removeAll(getPublishers());
                for (JID jid : publishers) {
                    addPublisher(jid);
                }
            }
            // Let subclasses have a chance to finish node configuration based on
            // the completed form
            postConfigure(completedForm);

            // Update the modification date to reflect the last time when the node's configuration
            // was modified
            modificationDate = new Date();

            // Notify subscribers that the node configuration has changed
            nodeConfigurationChanged();
        }
        // Store the new or updated node in the backend store
        saveToDB();

        // Check if we need to subscribe or unsubscribe from affiliate presences
        if (wasPresenceBased != isPresenceBasedDelivery()) {
            if (isPresenceBasedDelivery()) {
                addPresenceSubscriptions();
            }
            else {
                cancelPresenceSubscriptions();
            }
        }
    }

    /**
     * Configures the node with the completed form field. Fields that are common to leaf
     * and collection nodes are handled in {@link #configure(org.xmpp.forms.DataForm)}.
     * Subclasses should implement this method in order to configure the node with form
     * fields specific to the node type.
     *
     * @param field the form field specific to the node type.
     */
    abstract void configure(FormField field);

    /**
     * Node configuration was changed based on the completed form. Subclasses may implement
     * this method to finsh node configuration based on the completed form.
     *
     * @param completedForm the form completed by the node owner.
     */
    abstract void postConfigure(DataForm completedForm);

    /**
     * The node configuration has changed. If this is the first time the node is configured
     * after it was created (i.e. is not yet persistent) then do nothing. Otherwise, send
     * a notification to the node subscribers informing that the configuration has changed.
     */
    private void nodeConfigurationChanged() {
        if (!isNotifiedOfConfigChanges() || !savedToDB) {
            // Do nothing if node was just created and configure or if notification
            // of config changes is disabled
            return;
        }

        // Build packet to broadcast to subscribers
        Message message = new Message();
        Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
        Element config = event.addElement("configuration");
        config.addAttribute("node", nodeID);

        if (deliverPayloads) {
            config.add(getConfigurationChangeForm().getElement());
        }
        // Send notification that the node configuration has changed
        broadcastNodeEvent(message, false);
    }

    /**
     * Returns the data form to be included in the authorization request to be sent to
     * node owners when a new subscription needs to be approved.
     *
     * @param subscription the new subscription that needs to be approved.
     * @return the data form to be included in the authorization request.
     */
    DataForm getAuthRequestForm(NodeSubscription subscription) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("pubsub.form.authorization.title"));
        form.addInstruction(
                LocaleUtils.getLocalizedString("pubsub.form.authorization.instruction"));

        FormField formField = form.addField();
        formField.setVariable("FORM_TYPE");
        formField.setType(FormField.Type.hidden);
        formField.addValue("http://jabber.org/protocol/pubsub#subscribe_authorization");

        formField = form.addField();
        formField.setVariable("pubsub#subid");
        formField.setType(FormField.Type.hidden);
        formField.addValue(subscription.getID());

        formField = form.addField();
        formField.setVariable("pubsub#node");
        formField.setType(FormField.Type.text_single);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.authorization.node"));
        formField.addValue(getNodeID());

        formField = form.addField();
        formField.setVariable("pubsub#subscriber_jid");
        formField.setType(FormField.Type.jid_single);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.authorization.subscriber"));
        formField.addValue(subscription.getJID().toString());

        formField = form.addField();
        formField.setVariable("pubsub#allow");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.authorization.allow"));
        formField.addValue(Boolean.FALSE);

        return form;
    }


    /**
     * Returns a data form used by the owner to edit the node configuration.
     *
     * @return data form used by the owner to edit the node configuration.
     */
    public DataForm getConfigurationForm() {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("pubsub.form.conf.title"));
        List<String> params = new ArrayList<String>();
        params.add(getNodeID());
        form.addInstruction(LocaleUtils.getLocalizedString("pubsub.form.conf.instruction", params));

        FormField formField = form.addField();
        formField.setVariable("FORM_TYPE");
        formField.setType(FormField.Type.hidden);
        formField.addValue("http://jabber.org/protocol/pubsub#node_config");

        // Add the form fields and configure them for edition
        addFormFields(form, true);
        return form;
    }

    /**
     * Adds the required form fields to the specified form. When editing is true the field type
     * and a label is included in each fields. The form being completed will contain the current
     * node configuration. This information can be used for editing the node or for notifing that
     * the node configuration has changed.
     *
     * @param form the form containing the node configuration.
     * @param isEditing true when the form will be used to edit the node configuration.
     */
    protected void addFormFields(DataForm form, boolean isEditing) {
        FormField formField = form.addField();
        formField.setVariable("pubsub#title");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.short_name"));
        }
        formField.addValue(name);

        formField = form.addField();
        formField.setVariable("pubsub#description");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.description"));
        }
        formField.addValue(description);

        formField = form.addField();
        formField.setVariable("pubsub#subscribe");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.subscribe"));
        }
        formField.addValue(subscriptionEnabled);

        formField = form.addField();
        formField.setVariable("pubsub#subscription_required");
        // TODO Replace this variable for the one defined in the JEP (once one is defined)
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.subscription_required"));
        }
        formField.addValue(subscriptionConfigurationRequired);

        formField = form.addField();
        formField.setVariable("pubsub#deliver_payloads");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.deliver_payloads"));
        }
        formField.addValue(deliverPayloads);

        formField = form.addField();
        formField.setVariable("pubsub#notify_config");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.notify_config"));
        }
        formField.addValue(notifyConfigChanges);

        formField = form.addField();
        formField.setVariable("pubsub#notify_delete");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.notify_delete"));
        }
        formField.addValue(notifyDelete);

        formField = form.addField();
        formField.setVariable("pubsub#notify_retract");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.notify_retract"));
        }
        formField.addValue(notifyRetract);

        formField = form.addField();
        formField.setVariable("pubsub#presence_based_delivery");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.presence_based"));
        }
        formField.addValue(presenceBasedDelivery);

        formField = form.addField();
        formField.setVariable("pubsub#type");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.type"));
        }
        formField.addValue(payloadType);

        formField = form.addField();
        formField.setVariable("pubsub#body_xslt");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.body_xslt"));
        }
        formField.addValue(bodyXSLT);

        formField = form.addField();
        formField.setVariable("pubsub#dataform_xslt");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.dataform_xslt"));
        }
        formField.addValue(dataformXSLT);

        formField = form.addField();
        formField.setVariable("pubsub#access_model");
        if (isEditing) {
            formField.setType(FormField.Type.list_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.access_model"));
            formField.addOption(null, AccessModel.authorize.getName());
            formField.addOption(null, AccessModel.open.getName());
            formField.addOption(null, AccessModel.presence.getName());
            formField.addOption(null, AccessModel.roster.getName());
            formField.addOption(null, AccessModel.whitelist.getName());
        }
        formField.addValue(accessModel.getName());

        formField = form.addField();
        formField.setVariable("pubsub#publish_model");
        if (isEditing) {
            formField.setType(FormField.Type.list_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.publish_model"));
            formField.addOption(null, PublisherModel.publishers.getName());
            formField.addOption(null, PublisherModel.subscribers.getName());
            formField.addOption(null, PublisherModel.open.getName());
        }
        formField.addValue(publisherModel.getName());

        formField = form.addField();
        formField.setVariable("pubsub#roster_groups_allowed");
        if (isEditing) {
            formField.setType(FormField.Type.list_multi);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.roster_allowed"));
        }
        for (String group : rosterGroupsAllowed) {
            formField.addValue(group);
        }

        formField = form.addField();
        formField.setVariable("pubsub#contact");
        if (isEditing) {
            formField.setType(FormField.Type.jid_multi);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.contact"));
        }
        for (JID contact : contacts) {
            formField.addValue(contact.toString());
        }

        formField = form.addField();
        formField.setVariable("pubsub#language");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.language"));
        }
        formField.addValue(language);

        formField = form.addField();
        formField.setVariable("pubsub#owner");
        if (isEditing) {
            formField.setType(FormField.Type.jid_multi);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.owner"));
        }
        for (JID owner : getOwners()) {
            formField.addValue(owner.toString());
        }

        formField = form.addField();
        formField.setVariable("pubsub#publisher");
        if (isEditing) {
            formField.setType(FormField.Type.jid_multi);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.publisher"));
        }
        for (JID owner : getPublishers()) {
            formField.addValue(owner.toString());
        }

        formField = form.addField();
        formField.setVariable("pubsub#itemreply");
        if (isEditing) {
            formField.setType(FormField.Type.list_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.itemreply"));
        }
        if (replyPolicy != null) {
            formField.addValue(replyPolicy.name());
        }

        formField = form.addField();
        formField.setVariable("pubsub#replyroom");
        if (isEditing) {
            formField.setType(FormField.Type.jid_multi);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.replyroom"));
        }
        for (JID owner : getReplyRooms()) {
            formField.addValue(owner.toString());
        }

        formField = form.addField();
        formField.setVariable("pubsub#replyto");
        if (isEditing) {
            formField.setType(FormField.Type.jid_multi);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.replyto"));
        }
        for (JID owner : getReplyTo()) {
            formField.addValue(owner.toString());
        }
    }

    /**
     * Returns a data form with the node configuration. The returned data form is used for
     * notifying node subscribers that the node configuration has changed. The data form is
     * ony going to be included if node is configure to include payloads in event
     * notifications.
     *
     * @return a data form with the node configuration.
     */
    private DataForm getConfigurationChangeForm() {
        DataForm form = new DataForm(DataForm.Type.result);
        FormField formField = form.addField();
        formField.setVariable("FORM_TYPE");
        formField.setType(FormField.Type.hidden);
        formField.addValue("http://jabber.org/protocol/pubsub#node_config");
        // Add the form fields and configure them for notification
        // (i.e. no label or options are included)
        addFormFields(form, false);
        return form;
    }

    /**
     * Returns a data form containing the node configuration that is going to be used for
     * service discovery.
     *
     * @return a data form with the node configuration.
     */
    public DataForm getMetadataForm() {
        DataForm form = new DataForm(DataForm.Type.result);
        FormField formField = form.addField();
        formField.setVariable("FORM_TYPE");
        formField.setType(FormField.Type.hidden);
        formField.addValue("http://jabber.org/protocol/pubsub#meta-data");
        // Add the form fields
        addFormFields(form, true);
        return form;
    }

    /**
     * Returns true if this node is the root node of the pubsub service.
     *
     * @return true if this node is the root node of the pubsub service.
     */
    public boolean isRootCollectionNode() {
        return service.getRootCollectionNode() == this;
    }

    /**
     * Returns true if a user may have more than one subscription with the node. When
     * multiple subscriptions is enabled each subscription request, event notification and
     * unsubscription request should include a <tt>subid</tt> attribute. By default multiple
     * subscriptions is enabled.
     *
     * @return true if a user may have more than one subscription with the node.
     */
    public boolean isMultipleSubscriptionsEnabled() {
        return service.isMultipleSubscriptionsEnabled();
    }

    /**
     * Returns true if this node is a node container. Node containers may only contain nodes
     * but are not allowed to get items published.
     *
     * @return true if this node is a node container.
     */
    public boolean isCollectionNode() {
        return false;
    }

    /**
     * Returns true if the specified node is a first-level children of this node.
     *
     * @param child the node to check if it is a direct child of this node.
     * @return true if the specified node is a first-level children of this collection
     *         node.
     */
    public boolean isChildNode(Node child) {
        return false;
    }

    /**
     * Returns true if the specified node is a direct child node of this node or
     * a descendant of the children nodes.
     *
     * @param child the node to check if it is a descendant of this node.
     * @return true if the specified node is a direct child node of this node or
     *         a descendant of the children nodes.
     */
    public boolean isDescendantNode(Node child) {
        return false;
    }

    /**
     * Returns true if the specified user is allowed to administer the node. Node
     * administrator are allowed to retrieve the node configuration, change the node
     * configuration, purge the node, delete the node and get the node affiliations and
     * subscriptions.
     *
     * @param user the user to check if he is an admin.
     * @return true if the specified user is allowed to administer the node.
     */
    public boolean isAdmin(JID user) {
        if (getOwners().contains(user) || service.isServiceAdmin(user)) {
            return true;
        }
        // Check if we should try again but using the bare JID
        if (user.getResource() != null) {
            user = new JID(user.toBareJID());
            return isAdmin(user);
        }
        return false;
    }

    /**
     * Returns the {@link PubSubService} to which this node belongs.
     *
     * @return the pubsub service.
     */
    public PubSubService getService() {
        return service;
    }

    /**
     * Returns the unique identifier for a node within the context of a pubsub service.
     *
     * @return the unique identifier for a node within the context of a pubsub service.
     */
    public String getNodeID() {
        return nodeID;
    }

    /**
     * Returns the name of the node. The node may not have a configured name. The node's
     * name can be changed by submiting a completed data form.
     *
     * @return the name of the node.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns true if event notifications will include payloads. Payloads are included when
     * publishing new items. However, new items may not always include a payload depending
     * on the node configuration. Nodes can be configured to not deliver payloads for performance
     * reasons.
     *
     * @return true if event notifications will include payloads.
     */
    public boolean isPayloadDelivered() {
        return deliverPayloads;
    }

    public ItemReplyPolicy getReplyPolicy() {
        return replyPolicy;
    }

    /**
     * Returns true if subscribers will be notified when the node configuration changes.
     *
     * @return true if subscribers will be notified when the node configuration changes.
     */
    public boolean isNotifiedOfConfigChanges() {
        return notifyConfigChanges;
    }

    /**
     * Returns true if subscribers will be notified when the node is deleted.
     *
     * @return true if subscribers will be notified when the node is deleted.
     */
    public boolean isNotifiedOfDelete() {
        return notifyDelete;
    }

    /**
     * Returns true if subscribers will be notified when items are removed from the node.
     *
     * @return true if subscribers will be notified when items are removed from the node.
     */
    public boolean isNotifiedOfRetract() {
        return notifyRetract;
    }

    /**
     * Returns true if notifications are going to be delivered to available users only.
     *
     * @return true if notifications are going to be delivered to available users only.
     */
    public boolean isPresenceBasedDelivery() {
        return presenceBasedDelivery;
    }

    /**
     * Returns true if notifications to the specified user will be delivered when the
     * user is online.
     *
     * @param user the JID of the affiliate that has to be subscribed to the node.
     * @return true if notifications are going to be delivered when the user is online.
     */
    public boolean isPresenceBasedDelivery(JID user) {
        Collection<NodeSubscription> subscriptions = getSubscriptions(user);
        if (!subscriptions.isEmpty()) {
            if (presenceBasedDelivery) {
                // Node sends notifications only to only users so return true
                return true;
            }
            else {
                // Check if there is a subscription configured to only send notifications
                // based on the user presence
                for (NodeSubscription subscription : subscriptions) {
                    if (!subscription.getPresenceStates().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        // User is not subscribed to the node so presence subscription is not required
        return false;
    }

    /**
     * Returns the JID of the affiliates that are receiving notifications based on their
     * presence status.
     *
     * @return the JID of the affiliates that are receiving notifications based on their
     *         presence status.
     */
    Collection<JID> getPresenceBasedSubscribers() {
        Collection<JID> affiliatesJID = new ArrayList<JID>();
        if (presenceBasedDelivery) {
            // Add JID of all affiliates that are susbcribed to the node
            for (NodeAffiliate affiliate : affiliates) {
                if (!affiliate.getSubscriptions().isEmpty()) {
                    affiliatesJID.add(affiliate.getJID());
                }
            }
        }
        else {
            // Add JID of those affiliates that have a subscription that only wants to be
            // notified based on the subscriber presence
            for (NodeAffiliate affiliate : affiliates) {
                Collection<NodeSubscription> subscriptions = affiliate.getSubscriptions();
                for (NodeSubscription subscription : subscriptions) {
                    if (!subscription.getPresenceStates().isEmpty()) {
                        affiliatesJID.add(affiliate.getJID());
                        break;
                    }
                }
            }
        }
        return affiliatesJID;
    }

    /**
     * Returns true if the last published item is going to be sent to new subscribers.
     *
     * @return true if the last published item is going to be sent to new subscribers.
     */
    public boolean isSendItemSubscribe() {
        return false;
    }

    /**
     * Returns the publisher model that specifies who is allowed to publish items to the node.
     *
     * @return the publisher model that specifies who is allowed to publish items to the node.
     */
    public PublisherModel getPublisherModel() {
        return publisherModel;
    }

    /**
     * Returns true if users are allowed to subscribe and unsubscribe.
     *
     * @return true if users are allowed to subscribe and unsubscribe.
     */
    public boolean isSubscriptionEnabled() {
        return subscriptionEnabled;
    }

    /**
     * Returns true if new subscriptions should be configured to be active. Inactive
     * subscriptions will not get event notifications. However, subscribers will be
     * notified when a node is deleted no matter the subscription status.
     *
     * @return true if new subscriptions should be configured to be active.
     */
    public boolean isSubscriptionConfigurationRequired() {
        return subscriptionConfigurationRequired;
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
     * Returns the roster group(s) allowed to subscribe and retrieve items. This information
     * is going to be used only when using the
     * {@link org.jivesoftware.openfire.pubsub.models.RosterAccess} access model.
     *
     * @return the roster group(s) allowed to subscribe and retrieve items.
     */
    public Collection<String> getRosterGroupsAllowed() {
        return Collections.unmodifiableCollection(rosterGroupsAllowed);
    }

    /**
     * Adds a new roster group that is allowed to subscribe and retrieve items.
     * The new roster group is not going to be added to the database. Instead it is just
     * kept in memory.
     *
     * @param groupName the new roster group that is allowed to subscribe and retrieve items.
     */
    void addAllowedRosterGroup(String groupName) {
        rosterGroupsAllowed.add(groupName);
    }

    public Collection<JID> getReplyRooms() {
        return Collections.unmodifiableCollection(replyRooms);
    }

    void addReplyRoom(JID roomJID) {
        replyRooms.add(roomJID);
    }

    public Collection<JID> getReplyTo() {
        return Collections.unmodifiableCollection(replyTo);
    }

    void addReplyTo(JID entity) {
        replyTo.add(entity);
    }

    /**
     * Returns the type of payload data to be provided at the node. Usually specified by the
     * namespace of the payload (if any).
     *
     * @return the type of payload data to be provided at the node.
     */
    public String getPayloadType() {
        return payloadType;
    }

    /**
     * Returns the URL of an XSL transformation which can be applied to payloads in order
     * to generate an appropriate message body element.
     *
     * @return the URL of an XSL transformation which can be applied to payloads.
     */
    public String getBodyXSLT() {
        return bodyXSLT;
    }

    /**
     * Returns the URL of an XSL transformation which can be applied to the payload format
     * in order to generate a valid Data Forms result that the client could display
     * using a generic Data Forms rendering engine.
     *
     * @return the URL of an XSL transformation which can be applied to the payload format.
     */
    public String getDataformXSLT() {
        return dataformXSLT;
    }

    /**
     * Returns the datetime when the node was created.
     *
     * @return the datetime when the node was created.
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Returns the last date when the ndoe's configuration was modified.
     *
     * @return the last date when the ndoe's configuration was modified.
     */
    public Date getModificationDate() {
        return modificationDate;
    }

    /**
     * Returns the JID of the node creator. This is usually the sender's full JID of the
     * IQ packet used for creating the node.
     *
     * @return the JID of the node creator.
     */
    public JID getCreator() {
        return creator;
    }

    /**
     * Returns the description of the node. This information is really optional and can be
     * modified by submiting a completed data form with the new node configuration.
     *
     * @return the description of the node.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the default language of the node. This information is really optional and can be
     * modified by submiting a completed data form with the new node configuration.
     *
     * @return the default language of the node.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Returns the JIDs of those to contact with questions. This information is not used by
     * the pubsub service. It is meant to be "discovered" by users and redirect any question
     * to the returned people to contact.
     *
     * @return the JIDs of those to contact with questions.
     */
    public Collection<JID> getContacts() {
        return Collections.unmodifiableCollection(contacts);
    }

    /**
     * Adds a new user as a candidate to answer questions about the node.
     *
     * @param user the JID of the new user.
     */
    void addContact(JID user) {
        contacts.add(user);
    }

    /**
     * Returns the list of nodes contained by this node. Only {@link CollectionNode} may
     * contain other nodes.
     *
     * @return the list of nodes contained by this node.
     */
    public Collection<Node> getNodes() {
        return Collections.emptyList();
    }

    /**
     * Returns the collection node that is containing this node. The only node that
     * does not have a parent node is the root collection node.
     *
     * @return the collection node that is containing this node.
     */
    public CollectionNode getParent() {
        return parent;
    }

    /**
     * Returns the complete hierarchy of parents of this node.
     *
     * @return the complete hierarchy of parents of this node.
     */
    public Collection<CollectionNode> getParents() {
        Collection<CollectionNode> parents = new ArrayList<CollectionNode>();
        CollectionNode myParent = parent;
        while (myParent != null) {
            parents.add(myParent);
            myParent = myParent.getParent();
        }
        return parents;
    }

    /**
     * Sets whether event notifications will include payloads. Payloads are included when
     * publishing new items. However, new items may not always include a payload depending
     * on the node configuration. Nodes can be configured to not deliver payloads for performance
     * reasons.
     *
     * @param deliverPayloads true if event notifications will include payloads.
     */
    void setPayloadDelivered(boolean deliverPayloads) {
        this.deliverPayloads = deliverPayloads;
    }

    void setReplyPolicy(ItemReplyPolicy replyPolicy) {
        this.replyPolicy = replyPolicy;
    }

    /**
     * Sets whether subscribers will be notified when the node configuration changes.
     *
     * @param notifyConfigChanges true if subscribers will be notified when the node
     *        configuration changes.
     */
    void setNotifiedOfConfigChanges(boolean notifyConfigChanges) {
        this.notifyConfigChanges = notifyConfigChanges;
    }

    /**
     * Sets whether subscribers will be notified when the node is deleted.
     *
     * @param notifyDelete true if subscribers will be notified when the node is deleted.
     */
    void setNotifiedOfDelete(boolean notifyDelete) {
        this.notifyDelete = notifyDelete;
    }

    /**
     * Sets whether subscribers will be notified when items are removed from the node.
     *
     * @param notifyRetract true if subscribers will be notified when items are removed from
     *        the node.
     */
    void setNotifiedOfRetract(boolean notifyRetract) {
        this.notifyRetract = notifyRetract;
    }

    void setPresenceBasedDelivery(boolean presenceBasedDelivery) {
        this.presenceBasedDelivery = presenceBasedDelivery;
    }

    /**
     * Sets the publisher model that specifies who is allowed to publish items to the node.
     *
     * @param publisherModel the publisher model that specifies who is allowed to publish items
     *        to the node.
     */
    void setPublisherModel(PublisherModel publisherModel) {
        this.publisherModel = publisherModel;
    }

    /**
     * Sets whether users are allowed to subscribe and unsubscribe.
     *
     * @param subscriptionEnabled true if users are allowed to subscribe and unsubscribe.
     */
    void setSubscriptionEnabled(boolean subscriptionEnabled) {
        this.subscriptionEnabled = subscriptionEnabled;
    }

    /**
     * Sets whether new subscriptions should be configured to be active. Inactive
     * subscriptions will not get event notifications. However, subscribers will be
     * notified when a node is deleted no matter the subscription status.
     *
     * @param subscriptionConfigurationRequired true if new subscriptions should be
     *        configured to be active.
     */
    void setSubscriptionConfigurationRequired(boolean subscriptionConfigurationRequired) {
        this.subscriptionConfigurationRequired = subscriptionConfigurationRequired;
    }

    /**
     * Sets the access model that specifies who is allowed to subscribe and retrieve items.
     *
     * @param accessModel the access model that specifies who is allowed to subscribe and
     *        retrieve items.
     */
    void setAccessModel(AccessModel accessModel) {
        this.accessModel = accessModel;
    }

    /**
     * Sets the roster group(s) allowed to subscribe and retrieve items. This information
     * is going to be used only when using the
     * {@link org.jivesoftware.openfire.pubsub.models.RosterAccess} access model.
     *
     * @param rosterGroupsAllowed the roster group(s) allowed to subscribe and retrieve items.
     */
    void setRosterGroupsAllowed(Collection<String> rosterGroupsAllowed) {
        this.rosterGroupsAllowed = rosterGroupsAllowed;
    }

    void setReplyRooms(Collection<JID> replyRooms) {
        this.replyRooms = replyRooms;
    }

    void setReplyTo(Collection<JID> replyTo) {
        this.replyTo = replyTo;
    }

    /**
     * Sets the type of payload data to be provided at the node. Usually specified by the
     * namespace of the payload (if any).
     *
     * @param payloadType the type of payload data to be provided at the node.
     */
    void setPayloadType(String payloadType) {
        this.payloadType = payloadType;
    }

    /**
     * Sets the URL of an XSL transformation which can be applied to payloads in order
     * to generate an appropriate message body element.
     *
     * @param bodyXSLT the URL of an XSL transformation which can be applied to payloads.
     */
    void setBodyXSLT(String bodyXSLT) {
        this.bodyXSLT = bodyXSLT;
    }

    /**
     * Sets the URL of an XSL transformation which can be applied to the payload format
     * in order to generate a valid Data Forms result that the client could display
     * using a generic Data Forms rendering engine.
     *
     * @param dataformXSLT the URL of an XSL transformation which can be applied to the
     *        payload format.
     */
    void setDataformXSLT(String dataformXSLT) {
        this.dataformXSLT = dataformXSLT;
    }

    void setSavedToDB(boolean savedToDB) {
        this.savedToDB = savedToDB;
        if (savedToDB && parent != null) {
            // Notify the parent that he has a new child :)
            parent.addChildNode(this);
        }
    }

    /**
     * Sets the datetime when the node was created.
     *
     * @param creationDate the datetime when the node was created.
     */
    void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Sets the last date when the ndoe's configuration was modified.
     *
     * @param modificationDate the last date when the ndoe's configuration was modified.
     */
    void setModificationDate(Date modificationDate) {
        this.modificationDate = modificationDate;
    }

    /**
     * Sets the description of the node. This information is really optional and can be
     * modified by submiting a completed data form with the new node configuration.
     *
     * @param description the description of the node.
     */
    void setDescription(String description) {
        this.description = description;
    }

    /**
     * Sets the default language of the node. This information is really optional and can be
     * modified by submiting a completed data form with the new node configuration.
     *
     * @param language the default language of the node.
     */
    void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Sets the name of the node. The node may not have a configured name. The node's
     * name can be changed by submiting a completed data form.
     *
     * @param name the name of the node.
     */
    void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the JIDs of those to contact with questions. This information is not used by
     * the pubsub service. It is meant to be "discovered" by users and redirect any question
     * to the returned people to contact.
     *
     * @param contacts the JIDs of those to contact with questions.
     */
    void setContacts(Collection<JID> contacts) {
        this.contacts = contacts;
    }

    /**
     * Saves the node configuration to the backend store.
     */
    public void saveToDB() {
        // Make the room persistent
        if (!savedToDB) {
            PubSubPersistenceManager.createNode(this);
            // Set that the node is now in the DB
            setSavedToDB(true);
            // Save the existing node affiliates to the DB
            for (NodeAffiliate affialiate : affiliates) {
                PubSubPersistenceManager.saveAffiliation(this, affialiate, true);
            }
            // Add new subscriptions to the database
            for (NodeSubscription subscription : subscriptionsByID.values()) {
                PubSubPersistenceManager.saveSubscription(this, subscription, true);
            }
            // Add the new node to the list of available nodes
            service.addNode(this);
            // Notify the parent (if any) that a new node has been added
            if (parent != null) {
                parent.childNodeAdded(this);
            }
        }
        else {
            PubSubPersistenceManager.updateNode(this);
        }
    }

    public void addAffiliate(NodeAffiliate affiliate) {
        affiliates.add(affiliate);
    }

    public void addSubscription(NodeSubscription subscription)
    {
        subscriptionsByID.put(subscription.getID(), subscription);
        subscriptionsByJID.put(subscription.getJID().toString(), subscription);
    }

    /**
     * Returns the subscription whose subscription JID matches the specified JID or <tt>null</tt>
     * if none was found. Accessing subscriptions by subscription JID and not by subscription ID
     * is only possible when the node does not allow multiple subscriptions from the same entity.
     * If the node allows multiple subscriptions and this message is sent then an
     * IllegalStateException exception is going to be thrown.
     *
     * @param subscriberJID the JID of the entity that receives event notifications.
     * @return the subscription whose subscription JID matches the specified JID or <tt>null</tt>
     *         if none was found.
     * @throws IllegalStateException If this message was used when the node supports multiple
     *         subscriptions.
     */
    public NodeSubscription getSubscription(JID subscriberJID) {
        // Check that node does not support multiple subscriptions
        if (isMultipleSubscriptionsEnabled() && (getSubscriptions(subscriberJID).size() > 1)) {
            throw new IllegalStateException("Multiple subscriptions is enabled so subscriptions " +
                    "should be retrieved using subID.");
        }
        return subscriptionsByJID.get(subscriberJID.toString());
    }

    /**
     * Returns the subscription whose subscription ID matches the specified ID or <tt>null</tt>
     * if none was found. Accessing subscriptions by subscription ID is always possible no matter
     * if the node allows one or multiple subscriptions for the same entity. Even when users can
     * only subscribe once to the node a subscription ID is going to be internally created though
     * never returned to the user.
     *
     * @param subscriptionID the ID of the subscription.
     * @return the subscription whose subscription ID matches the specified ID or <tt>null</tt>
     *         if none was found.
     */
    NodeSubscription getSubscription(String subscriptionID) {
        return subscriptionsByID.get(subscriptionID);
    }


    /**
     * Deletes this node from memory and the database. Subscribers are going to be notified
     * that the node has been deleted after the node was successfully deleted.
     *
     * @return true if the node was successfully deleted.
     */
    public boolean delete() {
        // Delete node from the database
        if (PubSubPersistenceManager.removeNode(this)) {
            // Remove this node from the parent node (if any)
            if (parent != null) {
                parent.removeChildNode(this);
            }
            deletingNode();
            // Broadcast delete notification to subscribers (if enabled)
            if (isNotifiedOfDelete()) {
                // Build packet to broadcast to subscribers
                Message message = new Message();
                Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
                Element items = event.addElement("delete");
                items.addAttribute("node", nodeID);
                // Send notification that the node was deleted
                broadcastNodeEvent(message, true);
            }
            // Notify the parent (if any) that the node has been removed from the parent node
            if (parent != null) {
                parent.childNodeDeleted(this);
            }
            // Remove presence subscription when node was deleted.
            cancelPresenceSubscriptions();
            // Remove the node from memory
            service.removeNode(getNodeID());
            CacheFactory.doClusterTask(new RemoveNodeTask(this));
            // Clear collections in memory (clear them after broadcast was sent)
            affiliates.clear();
            subscriptionsByID.clear();
            subscriptionsByJID.clear();
            return true;
        }
        return false;
    }

    /**
     * Notification message indicating that the node is being deleted. Subclasses should
     * implement this method to delete any subclass specific information.
     */
    protected abstract void deletingNode();

    /**
     * Changes the parent node of this node. The node ID of the node will not be modified
     * based on the new parent so pubsub implementations where node ID has a semantic
     * meaning will end up affecting the meaning of the node hierarchy and possibly messing
     * up the meaning of the hierarchy.<p>
     *
     * No notifications are sent due to the new parent adoption process.
     *
     * @param newParent the new parent node of this node.
     */
    protected void changeParent(CollectionNode newParent) {
        if (parent != null) {
            // Remove this node from the current parent node
            parent.removeChildNode(this);
        }
        // Set the new parent of this node
        parent = newParent;
        if (parent != null) {
            // Add this node to the new parent node
            parent.addChildNode(this);
        }
        if (savedToDB) {
            PubSubPersistenceManager.updateNode(this);
        }
    }

    /**
     * Unsubscribe from affiliates presences if node is only sending notifications to
     * only users or only unsubscribe from those subscribers that configured their
     * subscription to send notifications based on their presence show value.
     */
    private void addPresenceSubscriptions() {
        for (NodeAffiliate affiliate : affiliates) {
            if (affiliate.getAffiliation() != NodeAffiliate.Affiliation.outcast &&
                    (isPresenceBasedDelivery() || (!affiliate.getSubscriptions().isEmpty()))) {
                service.presenceSubscriptionRequired(this, affiliate.getJID());
            }
        }
    }

    /**
     * Unsubscribe from affiliates presences if node is only sending notifications to
     * only users or only unsubscribe from those subscribers that configured their
     * subscription to send notifications based on their presence show value.
     */
    private void cancelPresenceSubscriptions() {
        for (NodeSubscription subscription : getSubscriptions()) {
            if (isPresenceBasedDelivery() || !subscription.getPresenceStates().isEmpty()) {
                service.presenceSubscriptionNotRequired(this, subscription.getOwner());
            }
        }
    }

    /**
     * Sends the list of affiliations with the node to the owner that sent the IQ
     * request.
     *
     * @param iqRequest IQ request sent by an owner of the node.
     */
    void sendAffiliations(IQ iqRequest) {
        IQ reply = IQ.createResultIQ(iqRequest);
        Element childElement = iqRequest.getChildElement().createCopy();
        reply.setChildElement(childElement);
        Element affiliations = childElement.element("affiliations");

        for (NodeAffiliate affiliate : affiliates) {
            if (affiliate.getAffiliation() == NodeAffiliate.Affiliation.none) {
                continue;
            }
            Element entity = affiliations.addElement("affiliation");
            entity.addAttribute("jid", affiliate.getJID().toString());
            entity.addAttribute("affiliation", affiliate.getAffiliation().name());
        }
        // Send reply
        service.send(reply);
    }

    /**
     * Sends the list of subscriptions with the node to the owner that sent the IQ
     * request.
     *
     * @param iqRequest IQ request sent by an owner of the node.
     */
    void sendSubscriptions(IQ iqRequest) {
        IQ reply = IQ.createResultIQ(iqRequest);
        Element childElement = iqRequest.getChildElement().createCopy();
        reply.setChildElement(childElement);
        Element subscriptions = childElement.element("subscriptions");

        for (NodeAffiliate affiliate : affiliates) {
            for (NodeSubscription subscription : affiliate.getSubscriptions()) {
                if (subscription.isAuthorizationPending()) {
                    continue;
                }
                Element entity = subscriptions.addElement("subscription");
                entity.addAttribute("jid", subscription.getJID().toString());
                //entity.addAttribute("affiliation", affiliate.getAffiliation().name());
                entity.addAttribute("subscription", subscription.getState().name());
                if (isMultipleSubscriptionsEnabled()) {
                    entity.addAttribute("subid", subscription.getID());
                }
            }
        }
        // Send reply
        service.send(reply);
    }

    /**
     * Broadcasts a node event to subscribers of the node.
     *
     * @param message the message containing the node event.
     * @param includeAll true if all subscribers will be notified no matter their
     *        subscriptions status or configuration.
     */
    protected void broadcastNodeEvent(Message message, boolean includeAll) {
        Collection<JID> jids = new ArrayList<JID>();
        for (NodeSubscription subscription : subscriptionsByID.values()) {
            if (includeAll || subscription.canSendNodeEvents()) {
                jids.add(subscription.getJID());
            }
        }
        // Broadcast packet to subscribers
        service.broadcast(this, message, jids);
    }

    /**
     * Sends an event notification to the specified subscriber. The event notification may
     * include information about the affected subscriptions.
     *
     * @param subscriberJID the subscriber JID that will get the notification.
     * @param notification the message to send to the subscriber.
     * @param subIDs the list of affected subscription IDs or null when node does not
     *        allow multiple subscriptions.
     */
    protected void sendEventNotification(JID subscriberJID, Message notification,
            Collection<String> subIDs) {
        Element headers = null;
        if (subIDs != null) {
            // Notate the event notification with the ID of the affected subscriptions
            headers = notification.addChildElement("headers", "http://jabber.org/protocol/shim");
            for (String subID : subIDs) {
                Element header = headers.addElement("header");
                header.addAttribute("name", "pubsub#subid");
                header.setText(subID);
            }
        }

        service.sendNotification(this, notification, subscriberJID);

        if (headers != null) {
            // Remove the added child element that includes subscription IDs information
            notification.getElement().remove(headers);
        }
    }

    /**
     * Creates a new subscription and possibly a new affiliate if the owner of the subscription
     * does not have any existing affiliation with the node. The new subscription might require
     * to be authorized by a node owner to be active. If new subscriptions are required to be
     * configured before being active then the subscription state would be "unconfigured".<p>
     *
     * The originalIQ parameter may be <tt>null</tt> when using this API internally. When no
     * IQ packet was sent then no IQ result will be sent to the sender. The rest of the
     * functionality is the same.
     *
     * @param originalIQ the IQ packet sent by the entity to subscribe to the node or
     *        null when using this API internally.
     * @param owner the JID of the affiliate.
     * @param subscriber the JID where event notifications are going to be sent.
     * @param authorizationRequired true if the new subscriptions needs to be authorized by
     *        a node owner.
     * @param options the data form with the subscription configuration or null if subscriber
     *        didn't provide a configuration.
     */
    public void createSubscription(IQ originalIQ, JID owner, JID subscriber,
            boolean authorizationRequired, DataForm options) {
        // Create a new affiliation if required
        if (getAffiliate(owner) == null) {
            addNoneAffiliation(owner);
        }
        // Figure out subscription status
        NodeSubscription.State subState = NodeSubscription.State.subscribed;
        if (isSubscriptionConfigurationRequired()) {
            // User has to configure the subscription to make it active
            subState = NodeSubscription.State.unconfigured;
        }
        else if (authorizationRequired && !isAdmin(owner)) {
            // Node owner needs to authorize subscription request so status is pending
            subState = NodeSubscription.State.pending;
        }
        // Generate a subscription ID (override even if one was sent by the client)
        String id = StringUtils.randomString(40);
        // Create new subscription
        NodeSubscription subscription = new NodeSubscription(this, owner, subscriber, subState, id);
        // Configure the subscription with the specified configuration (if any)
        if (options != null) {
            subscription.configure(options);
        }
        addSubscription(subscription);

        if (savedToDB) {
            // Add the new subscription to the database
            PubSubPersistenceManager.saveSubscription(this, subscription, true);
        }

        if (originalIQ != null) {
            // Reply with subscription and affiliation status indicating if subscription
            // must be configured (only when subscription was made through an IQ packet)
            subscription.sendSubscriptionState(originalIQ);
        }

        // If subscription is pending then send notification to node owners asking to approve
        // new subscription
        if (subscription.isAuthorizationPending()) {
            subscription.sendAuthorizationRequest();
        }

        // Update the other members with the new subscription
        CacheFactory.doClusterTask(new NewSubscriptionTask(subscription));

        // Send last published item (if node is leaf node and subscription status is ok)
        if (isSendItemSubscribe() && subscription.isActive()) {
            PublishedItem lastItem = getLastPublishedItem();
            if (lastItem != null) {
                subscription.sendLastPublishedItem(lastItem);
            }
        }

        // Check if we need to subscribe to the presence of the owner
        if (isPresenceBasedDelivery() && getSubscriptions(subscription.getOwner()).size() == 1) {
            if (subscription.getPresenceStates().isEmpty()) {
                // Subscribe to the owner's presence since the node is only sending events to
                // online subscribers and this is the first subscription of the user and the
                // subscription is not filtering notifications based on presence show values.
                service.presenceSubscriptionRequired(this, owner);
            }
        }
    }

    /**
     * Cancels an existing subscription to the node. If the subscriber does not have any
     * other subscription to the node and his affiliation was of type <tt>none</tt> then
     * remove the existing affiliation too.
     *
     * @param subscription the subscription to cancel.
     * @param sendToCluster True to forward cancel order to cluster peers
     */
    public void cancelSubscription(NodeSubscription subscription, boolean sendToCluster) {
        // Remove subscription from memory
        subscriptionsByID.remove(subscription.getID());
        subscriptionsByJID.remove(subscription.getJID().toString());
        // Check if user has affiliation of type "none" and there are no more subscriptions
        NodeAffiliate affiliate = subscription.getAffiliate();
        if (affiliate != null && affiliate.getAffiliation() == NodeAffiliate.Affiliation.none &&
                getSubscriptions(subscription.getOwner()).isEmpty()) {
            // Remove affiliation of type "none"
            removeAffiliation(affiliate);
        }
        if (savedToDB) {
            // Remove the subscription from the database
            PubSubPersistenceManager.removeSubscription(subscription);
        }
        if (sendToCluster) {
            CacheFactory.doClusterTask(new CancelSubscriptionTask(subscription));
        }

        // Check if we need to unsubscribe from the presence of the owner
        if (isPresenceBasedDelivery() && getSubscriptions(subscription.getOwner()).isEmpty()) {
            service.presenceSubscriptionNotRequired(this, subscription.getOwner());
        }
    }

    /**
     * Cancels an existing subscription to the node. If the subscriber does not have any
     * other subscription to the node and his affiliation was of type <tt>none</tt> then
     * remove the existing affiliation too.
     *
     * @param subscription the subscription to cancel.
     */
    public void cancelSubscription(NodeSubscription subscription) {
        cancelSubscription(subscription, ClusterManager.isClusteringEnabled());
    }

    /**
     * Returns the {@link PublishedItem} whose ID matches the specified item ID or <tt>null</tt>
     * if none was found. Item ID may or may not exist and it depends on the node's configuration.
     * When the node is configured to not include payloads in event notifications and
     * published items are not persistent then item ID is not used. In this case a <tt>null</tt>
     * value will always be returned.
     *
     * @param itemID the ID of the item to retrieve.
     * @return the PublishedItem whose ID matches the specified item ID or null if none was found.
     */
    public PublishedItem getPublishedItem(String itemID) {
        return null;
    }

    /**
     * Returns the list of {@link PublishedItem} that were published to the node. The
     * returned collection cannot be modified. Collection nodes does not support publishing
     * of items so an empty list will be returned in that case.
     *
     * @return the list of PublishedItem that were published to the node.
     */
    public List<PublishedItem> getPublishedItems() {
        return Collections.emptyList();
    }

    /**
     * Returns a list of {@link PublishedItem} with the most recent N items published to
     * the node. The returned collection cannot be modified. Collection nodes does not
     * support publishing of items so an empty list will be returned in that case.
     *
     * @param recentItems number of recent items to retrieve.
     * @return a list of PublishedItem with the most recent N items published to
     *         the node.
     */
    public List<PublishedItem> getPublishedItems(int recentItems) {
        return Collections.emptyList();
    }

    /**
     * Returns a list with the subscriptions to the node that are pending to be approved by
     * a node owner. If the node is not using the access model
     * {@link org.jivesoftware.openfire.pubsub.models.AuthorizeAccess} then the result will
     * be an empty collection.
     *
     * @return a list with the subscriptions to the node that are pending to be approved by
     *         a node owner.
     */
    public Collection<NodeSubscription> getPendingSubscriptions() {
        if (accessModel.isAuthorizationRequired()) {
            List<NodeSubscription> pendingSubscriptions = new ArrayList<NodeSubscription>();
            for (NodeSubscription subscription : subscriptionsByID.values()) {
                if (subscription.isAuthorizationPending()) {
                    pendingSubscriptions.add(subscription);
                }
            }
            return pendingSubscriptions;
        }
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return super.toString() + " - ID: " + getNodeID();
    }

    /**
     * Returns the last {@link PublishedItem} that was published to the node or <tt>null</tt> if
     * the node does not have published items. Collection nodes does not support publishing
     * of items so a <tt>null</tt> will be returned in that case.
     *
     * @return the PublishedItem that was published to the node or <tt>null</tt> if
     *         the node does not have published items.
     */
    public PublishedItem getLastPublishedItem() {
        return null;
    }

    /**
     * Approves or cancels a subscriptions that was pending to be approved by a node owner.
     * Subscriptions that were not approved will be deleted. Approved subscriptions will be
     * activated (i.e. will be able to receive event notifications) as long as the subscriber
     * is not required to configure the subscription.
     *
     * @param subscription the subscriptions that was approved or rejected.
     * @param approved true when susbcription was approved. Otherwise the subscription was rejected.
     */
    public void approveSubscription(NodeSubscription subscription, boolean approved) {
        if (!subscription.isAuthorizationPending()) {
            // Do nothing if the subscription is no longer pending
            return;
        }
        if (approved) {
            // Mark that the subscription to the node has been approved
            subscription.approved();
            CacheFactory.doClusterTask(new ModifySubscriptionTask(subscription));
        }
        else  {
            // Cancel the subscription to the node
            cancelSubscription(subscription);
        }
    }

    /**
     * Policy that defines whether owners or publisher should receive replies to items.
     */
    public static enum ItemReplyPolicy {

        /**
         * Statically specify a replyto of the node owner(s).
         */
        owner,
        /**
         * Dynamically specify a replyto of the item publisher.
         */
        publisher
    }
}
