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
import org.jivesoftware.wildfire.pubsub.models.AccessModel;
import org.jivesoftware.wildfire.pubsub.models.PublisherModel;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A virtual location to which information can be published and from which event
 * notifications and/or payloads can be received (in other pubsub systems, this may
 * be labelled a "topic").
 *
 * @author Matt Tucker
 */
public abstract class Node {

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
     * Flag that indicates whether to send items to new subscribers.
     */
    protected boolean sendItemSubscribe;
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
        this.sendItemSubscribe = defaultConfiguration.isSendItemSubscribe();
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
     */
    public void addOwner(JID jid) {
        addAffiliation(jid, NodeAffiliate.Affiliation.owner);
        Collection<NodeSubscription> subscriptions = getSubscriptions(jid);
        if (subscriptions.isEmpty()) {
            // User does not have a subscription with the node so create a default one
            addSubscription(jid, jid, NodeSubscription.State.subscribed, null);
        }
        else {
            // TODO Approve any pending subscription
        }
    }

    /**
     * Removes the owner affiliation of the specified entity JID. If the user was an owner
     * of this node then the user will not have any affiliation with the node.
     *
     * @param jid the JID of the user being removed as a node owner.
     */
    public void removeOwner(JID jid) {
        removeAffiliation(jid, NodeAffiliate.Affiliation.owner);
        removeSubscriptions(jid);
    }

    /**
     * Adds a new affiliation or updates an existing affiliation of the specified entity JID
     * to become a node publisher.
     *
     * @param jid the JID of the user being added as a node publisher.
     */
    public void addPublisher(JID jid) {
        addAffiliation(jid, NodeAffiliate.Affiliation.publisher);
        Collection<NodeSubscription> subscriptions = getSubscriptions(jid);
        if (subscriptions.isEmpty()) {
            // User does not have a subscription with the node so create a default one
            addSubscription(jid, jid, NodeSubscription.State.subscribed, null);
        }
        else {
            // TODO Approve any pending subscription
        }
    }

    /**
     * Removes the publisher affiliation of the specified entity JID. If the user was a publisher
     * of this node then the user will not have any affiliation with the node.
     *
     * @param jid the JID of the user being removed as a node publisher.
     */
    public void removePublisher(JID jid) {
        removeAffiliation(jid, NodeAffiliate.Affiliation.publisher);
        removeSubscriptions(jid);
    }

    /**
     * Adds a new affiliation or updates an existing affiliation of the specified entity JID
     * to become a none affiliate. Affiliates of type none are allowed to subscribe to the node.
     *
     * @param jid the JID of the user with affiliation "none".
     */
    public void addNoneAffiliation(JID jid) {
        addAffiliation(jid, NodeAffiliate.Affiliation.none);
    }

    /**
     * Sets that the specified entity is an outcast of the node. Outcast entities are not
     * able to publish or subscribe to the node. Existing subscriptions will be deleted.
     *
     * @param jid the JID of the user that is no longer able to publish or subscribe to the node.
     */
    public void addOutcast(JID jid) {
        addAffiliation(jid, NodeAffiliate.Affiliation.outcast);
        // Delete existing subscriptions
        removeSubscriptions(jid);
    }

    /**
     * Removes the banning to subscribe to the node for the specified entity.
     *
     * @param jid the JID of the user that is no longer an outcast.
     */
    public void removeOutcast(JID jid) {
        removeAffiliation(jid, NodeAffiliate.Affiliation.outcast);
    }

    private void addAffiliation(JID jid, NodeAffiliate.Affiliation affiliation) {
        boolean created = false;
        // Get the current affiliation of the specified JID
        NodeAffiliate affiliate = getAffiliate(jid);
        // Check if the user already has the same affiliation
        if (affiliate != null && affiliation == affiliate.getAffiliation()) {
            // Do nothing since the user already has the expected affiliation
            return;
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
            PubSubPersistenceManager.saveAffiliation(service, this, affiliate, created);
        }
    }

    private void removeAffiliation(JID jid, NodeAffiliate.Affiliation affiliation) {
        // Get the current affiliation of the specified JID
        NodeAffiliate affiliate = getAffiliate(jid);
        // Check if the user already has the same affiliation
        if (affiliate != null && affiliation == affiliate.getAffiliation()) {
            // TODO If user has subscriptions then change affiliation to NONE
            removeAffiliation(affiliate);
        }
    }

    private void removeAffiliation(NodeAffiliate affiliate) {
        // Remove the existing affiliate from the list in memory
        affiliates.remove(affiliate);
        if (savedToDB) {
            // Remove the affiliate from the database
            PubSubPersistenceManager.removeAffiliation(service, this, affiliate);
        }
    }

    private NodeSubscription addSubscription(JID owner, JID jid, NodeSubscription.State subscribed,
            DataForm options) {
        // Generate a subscription ID (override even if one was sent by the client)
        String id = StringUtils.randomString(40);
        NodeSubscription subscription = new NodeSubscription(service, this, owner, jid, subscribed, id);
        // Configure the subscription with the specified configuration (if any)
        if (options != null) {
            subscription.configure(options);
        }
        addSubscription(subscription);

        if (savedToDB) {
            // Add the new subscription to the database
            PubSubPersistenceManager.saveSubscription(service, this, subscription, true);
        }
        return subscription;
    }

    private void removeSubscriptions(JID owner) {
        for (NodeSubscription subscription : getSubscriptions(owner)) {
            // Remove the existing subscription from the list in memory
            subscriptionsByID.remove(subscription.getID());
            if (savedToDB) {
                // Remove the subscription from the database
                PubSubPersistenceManager.removeSubscription(service, this, subscription);
            }
        }
    }

    /**
     * Returns the list of subscriptions owned by the specified user. The subscription owner
     * may have more than one subscription based on {@link #isMultipleSubscriptionsEnabled()}.
     * Each subscription may have a different subscription JID if the owner wants to receive
     * notifications in different resources (or even JIDs).
     *
     * @param owner the owner of the subscriptions.
     */
    Collection<NodeSubscription> getSubscriptions(JID owner) {
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

    public Collection<JID> getOwners() {
        Collection<JID> jids = new ArrayList<JID>();
        for (NodeAffiliate affiliate : affiliates) {
            if (NodeAffiliate.Affiliation.owner == affiliate.getAffiliation()) {
                jids.add(affiliate.getJID());
            }
        }
        return jids;
    }

    public Collection<JID> getPublishers() {
        Collection<JID> jids = new ArrayList<JID>();
        for (NodeAffiliate affiliate : affiliates) {
            if (NodeAffiliate.Affiliation.publisher == affiliate.getAffiliation()) {
                jids.add(affiliate.getJID());
            }
        }
        return jids;
    }

    public void configure(DataForm completedForm) throws NotAcceptableException {
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
                    catch (Exception e) {}
                }
            }

            // Answer a not-acceptable error if all the current owners will be removed
            if (ownersSent && owners.isEmpty()) {
                throw new NotAcceptableException();
            }

            for (FormField field : completedForm.getFields()) {
                if ("FORM_TYPE".equals(field.getVariable())) {
                    // Ignore this variable
                    continue;
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
                else if ("pubsub#send_item_subscribe".equals(field.getVariable())) {
                    values = field.getValues();
                    booleanValue = (values.size() > 0 ? values.get(0) : "1");
                    sendItemSubscribe = "1".equals(booleanValue);
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
                        rosterGroupsAllowed.add(value);
                    }
                }
                else if ("pubsub#contact".equals(field.getVariable())) {
                    // Get the new list of users that may be contacted with questions
                    contacts = new ArrayList<JID>();
                    for (String value : field.getValues()) {
                        try {
                            contacts.add(new JID(value));
                        }
                        catch (Exception e) {}
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
                            replyRooms.add(new JID(value));
                        }
                        catch (Exception e) {}
                    }
                }
                else if ("pubsub#replyto".equals(field.getVariable())) {
                    // Get the new list of JID(s) to specify for replyto
                    replyTo = new ArrayList<JID>();
                    for (String value : field.getValues()) {
                        try {
                            replyTo.add(new JID(value));
                        }
                        catch (Exception e) {}
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
                oldOwners.remove(owners);
                for (JID jid : oldOwners) {
                    removeOwner(jid);
                }

                // Calculate new owners and add them to the DB
                owners.remove(getOwners());
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
                    catch (Exception e) {}
                }
                // Calculate publishers to remove and remove them from the DB
                Collection<JID> oldPublishers = getPublishers();
                oldPublishers.remove(publishers);
                for (JID jid : oldPublishers) {
                    removePublisher(jid);
                }

                // Calculate new publishers and add them to the DB
                publishers.remove(getPublishers());
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

    private void nodeConfigurationChanged() {
        if (!notifyConfigChanges || !savedToDB) {
            // Do nothing if node was just created and configure or if notification
            // of config changes is disabled
            return;
        }

        // Build packet to broadcast to subscribers
        Message message = new Message();
        Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
        Element items = event.addElement("items");
        items.addAttribute("node", nodeID);
        Element item = items.addElement("item");
        item.addAttribute("id", "configuration");
        if (deliverPayloads) {
            item.add(getConfigurationChangeForm().getElement());
        }
        // Send notification that the node configuration has changed
        broadcastSubscribers(message, false);
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
        // Add the form fields and configure them for edition
        addFormFields(form, true);
        return form;
    }

    protected void addFormFields(DataForm form, boolean isEditing) {
        FormField formField = form.addField();
        formField.setVariable("FORM_TYPE");
        formField.setType(FormField.Type.hidden);
        formField.addValue("http://jabber.org/protocol/pubsub#node_config");

        formField = form.addField();
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
        formField.setVariable("pubsub#send_item_subscribe");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(
                    LocaleUtils.getLocalizedString("pubsub.form.conf.send_item_subscribe"));
        }
        formField.addValue(sendItemSubscribe);

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
        // Add the form fields and configure them for notification
        // (i.e. no label or options are included)
        addFormFields(form, false);
        return form;
    }

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
        return true;
    }

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

    public String getNodeID() {
        return nodeID;
    }

    public String getName() {
        return name;
    }

    public boolean isDeliverPayloads() {
        return deliverPayloads;
    }

    public ItemReplyPolicy getReplyPolicy() {
        return replyPolicy;
    }

    public boolean isNotifyConfigChanges() {
        return notifyConfigChanges;
    }

    public boolean isNotifyDelete() {
        return notifyDelete;
    }

    public boolean isNotifyRetract() {
        return notifyRetract;
    }

    public boolean isPresenceBasedDelivery() {
        return presenceBasedDelivery;
    }

    public boolean isSendItemSubscribe() {
        return sendItemSubscribe;
    }

    public PublisherModel getPublisherModel() {
        return publisherModel;
    }

    public boolean isSubscriptionEnabled() {
        return subscriptionEnabled;
    }

    public boolean isSubscriptionConfigurationRequired() {
        return subscriptionConfigurationRequired;
    }

    public AccessModel getAccessModel() {
        return accessModel;
    }

    public Collection<String> getRosterGroupsAllowed() {
        return rosterGroupsAllowed;
    }

    public Collection<JID> getReplyRooms() {
        return replyRooms;
    }

    public Collection<JID> getReplyTo() {
        return replyTo;
    }

    public String getPayloadType() {
        return payloadType;
    }

    public String getBodyXSLT() {
        return bodyXSLT;
    }

    public String getDataformXSLT() {
        return dataformXSLT;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public JID getCreator() {
        return creator;
    }

    public String getDescription() {
        return description;
    }

    public String getLanguage() {
        return language;
    }

    public Collection<JID> getContacts() {
        return contacts;
    }

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

    void setDeliverPayloads(boolean deliverPayloads) {
        this.deliverPayloads = deliverPayloads;
    }

    void setReplyPolicy(ItemReplyPolicy replyPolicy) {
        this.replyPolicy = replyPolicy;
    }

    void setNotifyConfigChanges(boolean notifyConfigChanges) {
        this.notifyConfigChanges = notifyConfigChanges;
    }

    void setNotifyDelete(boolean notifyDelete) {
        this.notifyDelete = notifyDelete;
    }

    void setNotifyRetract(boolean notifyRetract) {
        this.notifyRetract = notifyRetract;
    }

    void setPresenceBasedDelivery(boolean presenceBasedDelivery) {
        this.presenceBasedDelivery = presenceBasedDelivery;
    }

    void setSendItemSubscribe(boolean sendItemSubscribe) {
        this.sendItemSubscribe = sendItemSubscribe;
    }

    void setPublisherModel(PublisherModel publisherModel) {
        this.publisherModel = publisherModel;
    }

    void setSubscriptionEnabled(boolean subscriptionEnabled) {
        this.subscriptionEnabled = subscriptionEnabled;
    }

    void setSubscriptionConfigurationRequired(boolean subscriptionConfigurationRequired) {
        this.subscriptionConfigurationRequired = subscriptionConfigurationRequired;
    }

    void setAccessModel(AccessModel accessModel) {
        this.accessModel = accessModel;
    }

    void setReplyRooms(Collection<JID> replyRooms) {
        this.replyRooms = replyRooms;
    }

    void setReplyTo(Collection<JID> replyTo) {
        this.replyTo = replyTo;
    }

    void setPayloadType(String payloadType) {
        this.payloadType = payloadType;
    }

    void setBodyXSLT(String bodyXSLT) {
        this.bodyXSLT = bodyXSLT;
    }

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

    void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    void setModificationDate(Date modificationDate) {
        this.modificationDate = modificationDate;
    }

    void setDescription(String description) {
        this.description = description;
    }

    void setLanguage(String language) {
        this.language = language;
    }

    void setName(String name) {
        this.name = name;
    }

    void setRosterGroupsAllowed(Collection<String> rosterGroupsAllowed) {
        this.rosterGroupsAllowed = rosterGroupsAllowed;
    }

    void setContacts(Collection<JID> contacts) {
        this.contacts = contacts;
    }

    public void saveToDB() {
        // Make the room persistent
        if (!savedToDB) {
            PubSubPersistenceManager.createNode(service, this);
            // Set that the now is now in the DB
            setSavedToDB(true);
            // Save the existing node affiliates to the DB
            for (NodeAffiliate affialiate : affiliates) {
                PubSubPersistenceManager.saveAffiliation(service, this, affialiate, true);
            }
            // Add new subscriptions to the database
            for (NodeSubscription subscription : subscriptionsByID.values()) {
                PubSubPersistenceManager.saveSubscription(service, this, subscription, true);
            }
        }
        else {
            PubSubPersistenceManager.updateNode(service, this);
        }
    }

    void addAffiliate(NodeAffiliate affiliate) {
        affiliates.add(affiliate);
    }

    void addSubscription(NodeSubscription subscription) {
        subscriptionsByID.put(subscription.getID(), subscription);
    }

    /**
     * Returns the subscription whose subscription JID matches the specified JID or <tt>null</tt>
     * if none was found. Accessing subscriptions by subscription JID and not by subscription ID
     * is only possible when the node does not allow multiple subscriptions from the same entity.
     * If the node allows multiple subscriptions and this message is sent then an
     * IllegalStateException exception is going to be thrown.
     *
     * @param subscriptionJID the JID of the entity that receives event notifications.
     * @return the subscription whose subscription JID matches the specified JID or <tt>null</tt>
     *         if none was found.
     * @throws IllegalStateException If this message was used when the node supports multiple
     *         subscriptions.
     */
    NodeSubscription getSubscription(JID subscriptionJID) {
        // Check that node does not support multiple subscriptions
        if (isMultipleSubscriptionsEnabled()) {
            throw new IllegalStateException(
                    "Multiple subscriptions is enabled so subscriptions should be retrieved using subID.");
        }
        // TODO implement this
        return null;
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
        // TODO Should we lock the object to prevent simultaneous edition, publishing, etc.????
        // Delete node from the database
        if (PubSubPersistenceManager.removeNode(service, this)) {
            // Remove this node from the parent node (if any)
            if (parent != null) {
                parent.removeChildNode(this);
            }
            // TODO Update child nodes to use the root node or the parent node of this node as the new parent node
            for (Node node : getNodes()) {
                //node.set
            }
            // Broadcast delete notification to subscribers (if enabled)
            if (notifyDelete) {
                // Build packet to broadcast to subscribers
                Message message = new Message();
                Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
                Element items = event.addElement("delete");
                items.addAttribute("node", nodeID);
                // Send notification that the node was deleted
                broadcastSubscribers(message, true);
            }
            // Clear collections in memory (clear them after broadcast was sent)
            affiliates.clear();
            subscriptionsByID.clear();
            return true;
        }
        return false;
    }

    /**
     * Sends the list of affiliated entities with the node to the owner that sent the IQ
     * request.
     *
     * @param iqRequest IQ request sent by an owner of the node.
     */
    public void sendAffiliatedEntities(IQ iqRequest) {
        IQ reply = IQ.createResultIQ(iqRequest);
        Element childElement = iqRequest.getChildElement().createCopy();
        reply.setChildElement(childElement);

        for (NodeSubscription subscription : subscriptionsByID.values()) {
            Element entity = childElement.addElement("entity");
            entity.addAttribute("jid", subscription.getJID().toString());
            entity.addAttribute("affiliation", subscription.getAffiliate().getAffiliation().name());
            entity.addAttribute("subscription", subscription.getState().name());
            entity.addAttribute("subid", subscription.getID());
        }
    }

    protected void broadcastSubscribers(Message message, boolean includeAll) {
        Collection<JID> jids = new ArrayList<JID>();
        for (NodeSubscription subscription : subscriptionsByID.values()) {
            if (includeAll || subscription.isApproved()) {
                jids.add(subscription.getJID());
            }
        }
        // Broadcast packet to subscribers
        service.broadcast(this, message, jids);
    }

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

        notification.setTo(subscriberJID);
        service.send(notification);

        if (headers != null) {
            // Remove the added child element that includes subscription IDs information
            notification.getElement().remove(headers);
        }
    }

    /**
     * Creates a new subscription and possibly a new affiliate if the owner of the subscription
     * does not have any existing affiliation with the node. The new subscription might require
     * to be authorized by a node owner to be active. If new subscriptions are required to be
     * configured before being active then the subscription state would be "unconfigured".
     *
     * @param owner the JID of the affiliate.
     * @param subscriber the JID where event notifications are going to be sent.
     * @param authorizationRequired true if the new subscriptions needs to be authorized by
     *        a node owner.
     * @param options the data form with the subscription configuration or null if subscriber
     *        didn't provide a configuration.
     */
    void createSubscription(IQ originalIQ, JID owner, JID subscriber, boolean authorizationRequired,
            DataForm options) {
        // Create a new affiliation if required
        if (getAffiliate(owner) == null) {
            addNoneAffiliation(owner);
        }
        // Figure out subscription status
        NodeSubscription.State subState = NodeSubscription.State.subscribed;
        if (authorizationRequired) {
            // Node owner needs to authorize subscription request so status is pending
            subState = NodeSubscription.State.pending;
        }
        else if (isSubscriptionConfigurationRequired()) {
            // User has to configure the subscription to make it active
            subState = NodeSubscription.State.unconfigured;
        }
        // Create new subscription
        NodeSubscription subscription = addSubscription(owner, subscriber, subState, options);

        // Reply with subscription and affiliation status indicating if subscription
        // must be configured
        subscription.sendSubscriptionState(originalIQ);

        // Send last published item (if node is leaf node and subscription status is ok)
        if (isSendItemSubscribe()) {
            PublishedItem lastItem = getLastPublishedItem();
            if (lastItem != null) {
                subscription.sendLastPublishedItem(lastItem);
            }
        }
    }

    /**
     * Cancels an existing subscription to the node. If the subscriber does not have any
     * other subscription to the node and his affiliation was of type <tt>none</tt> then
     * remove the existing affiliation too.
     *
     * @param subscription the subscription to cancel.
     */
    void cancelSubscription(NodeSubscription subscription) {
        // Remove subscription from memory
        subscriptionsByID.remove(subscription.getID());
        // Check if user has affiliation of type "none" and there are no more subscriptions
        NodeAffiliate affiliate = subscription.getAffiliate();
        if (affiliate != null && affiliate.getAffiliation() == NodeAffiliate.Affiliation.none &&
                getSubscriptions(subscription.getOwner()).isEmpty()) {
            // Remove affiliation of type "none"
            removeAffiliation(affiliate);
        }
        if (savedToDB) {
            // Remove the subscription from the database
            PubSubPersistenceManager.removeSubscription(service, this, subscription);
        }
    }

    /**
     * Returns the {@link PublishedItem} whose ID matches the specified item ID or <tt>null</tt>
     * if none was found. Item ID may or may not exist and it depends on the node's configuration.
     * When the node is configured to not include payloads in event notifications and
     * published items are not persistent then item ID is not used. In this case a <tt>null</tt>
     * value will always be returned.
     *
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
     * @return a list of PublishedItem with the most recent N items published to
     *         the node.
     */
    public List<PublishedItem> getPublishedItems(int recentItems) {
        return Collections.emptyList();
    }

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
        publisher;
    }
}
