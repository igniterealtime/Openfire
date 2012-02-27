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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.dom4j.Element;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

/**
 * A subscription to a node. Entities may subscribe to a node to be notified when new events
 * are published to the node. Published events may contain a {@link PublishedItem}. Only
 * nodes that are configured to not deliver payloads with event notifications and to not
 * persist items will let publishers to publish events without items thus not including
 * items in the notifications sent to subscribers.<p>
 *
 * Node subscriptions may need to be configured by the subscriber or approved by a node owner
 * to become active. The node configuration establishes whether configuration or approval are
 * required. In any case, the subscriber will not get event notifications until the subscription
 * is active.<p>
 *
 * Depending on the node configuration it may be possible for the same subscriber to subscribe
 * multiple times to the node. Each subscription may have a different configuration like for
 * instance different keywords. Keywords can be used as a way to filter the type of
 * {@link PublishedItem} to be notified of. When the same subscriber has subscribed multiple
 * times to the node a single notification is going to be sent to the subscriber instead of
 * sending a notification for each subscription.
 *
 * @author Matt Tucker
 */
public class NodeSubscription {

	private static final Logger Log = LoggerFactory.getLogger(NodeSubscription.class);

    private static final SimpleDateFormat dateFormat;
    private static final FastDateFormat fastDateFormat;
    /**
     * Reference to the publish and subscribe service.
     */
    private PubSubService service;
    /**
     * The node to which this subscription is interested in.
     */
    private Node node;
    /**
     * JID of the entity that will receive the event notifications.
     */
    private JID jid;
    /**
     * JID of the entity that owns this subscription. This JID is the JID of the
     * NodeAffiliate that is subscribed to the node.
     */
    private JID owner;
    /**
     * ID that uniquely identifies the subscription of the user in the node.
     */
    private String id;
    /**
     * Current subscription state.
     */
    private State state;
    /**
     * Flag indicating whether an entity wants to receive or has disabled notifications.
     */
    private boolean deliverNotifications = true;
    /**
     * Flag indicating whether an entity wants to receive digests (aggregations) of
     * notifications or all notifications individually.
     */
    private boolean usingDigest = false;
    /**
     * The minimum number of milliseconds between sending any two notification digests.
     * Default is 24 hours.
     */
    private int digestFrequency = 86400000;
    /**
     * The Date at which a leased subscription will end or has ended. A value of
     * <tt>null</tt> means that the subscription will never expire.
     */
    private Date expire = null;
    /**
     * Flag indicating whether an entity wants to receive an XMPP message body in
     * addition to the payload format.
     */
    private boolean includingBody = false;
    /**
     * The presence states for which an entity wants to receive notifications.
     */
    private Collection<String> presenceStates = new ArrayList<String>();
    /**
     * When subscribing to collection nodes it is possible to be interested in new nodes
     * added to the collection node or new items published in the children nodes. The default
     * value is "nodes".
     */
    private Type type = Type.nodes;
    /**
     * Receive notification from children up to certain depth. Possible values are 1 or 0.
     * Zero means that there is no depth limit.
     */
    private int depth = 1;
    /**
     * Keyword that the event needs to match. When <tt>null</tt> all event are going to
     * be notified to the subscriber.
     */
    private String keyword = null;
    /**
     * Indicates if the subscription is present in the database.
     */
    private boolean savedToDB = false;

    static {
        dateFormat = new SimpleDateFormat("yyyy-MM-DD'T'HH:mm:ss.SSS'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        fastDateFormat = FastDateFormat
                .getInstance(JiveConstants.XMPP_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));
    }

    /**
     * Creates a new subscription of the specified user with the node.
     *
     * @param service the pubsub service hosting the node where this subscription lives.
     * @param node Node to which this subscription is interested in.
     * @param owner the JID of the entity that owns this subscription.
     * @param jid the JID of the user that owns the subscription.
     * @param state the state of the subscription with the node.
     * @param id the id the uniquely identifies this subscriptin within the node.
     */
    NodeSubscription(PubSubService service, Node node, JID owner, JID jid, State state, String id) {
        this.service = service;
        this.node = node;
        this.jid = jid;
        this.owner = owner;
        this.state = state;
        this.id = id;
    }

    /**
     * Returns the node that holds this subscription.
     *
     * @return the node that holds this subscription.
     */
    public Node getNode() {
        return node;
    }

    /**
     * Returns the ID that uniquely identifies the subscription of the user in the node.
     *
     * @return the ID that uniquely identifies the subscription of the user in the node.
     */
    public String getID() {
        return id;
    }

    /**
     * Returns the JID that is going to receive the event notifications. This JID can be the
     * owner JID or a full JID if the owner wants to receive the notification at a particular
     * resource.<p>
     *
     * Moreover, since subscriber and owner are separated it should be theorically possible to
     * have a different owner JID (e.g. gato@server1.com) and a subscriber JID
     * (e.g. gato@server2.com). Note that letting this case to happen may open the pubsub service
     * to get spam or security problems. However, the pubsub service should avoid this case to
     * happen.
     *
     * @return the JID that is going to receive the event notifications.
     */
    public JID getJID() {
        return jid;
    }

    /**
     * Retuns the JID of the entity that owns this subscription. The owner entity will have
     * a {@link NodeAffiliate} for the owner JID. The owner may have more than one subscription
     * with the node based on what this message
     * {@link org.jivesoftware.openfire.pubsub.Node#isMultipleSubscriptionsEnabled()}.
     *
     * @return he JID of the entity that owns this subscription.
     */
    public JID getOwner() {
        return owner;
    }

    /**
     * Returns the current subscription state. Subscriptions with status of pending should be
     * authorized by a node owner.
     *
     * @return the current subscription state.
     */
    public State getState() {
        return state;
    }

    /**
     * Returns true if configuration is required by the node and is still pending to
     * be configured by the subscriber. Otherwise return false. Once a subscription is
     * configured it might need to be approved by a node owner to become active.
     *
     * @return true if configuration is required by the node and is still pending to
     *         be configured by the subscriber.
     */
    public boolean isConfigurationPending() {
        return state == State.unconfigured;
    }

    /**
     * Returns true if the subscription needs to be approved by a node owner to become
     * active. Until the subscription is not activated the subscriber will not receive
     * event notifications.
     *
     * @return true if the subscription needs to be approved by a node owner to become active.
     */
    public boolean isAuthorizationPending() {
        return state == State.pending;
    }

    /**
     * Returns whether an entity wants to receive or has disabled notifications.
     *
     * @return true when notifications should be sent to the subscriber.
     */
    public boolean shouldDeliverNotifications() {
        return deliverNotifications;
    }

    /**
     * Returns whether an entity wants to receive digests (aggregations) of
     * notifications or all notifications individually.
     *
     * @return true when an entity wants to receive digests (aggregations) of notifications.
     */
    public boolean isUsingDigest() {
        return usingDigest;
    }

    /**
     * Returns the minimum number of milliseconds between sending any two notification digests.
     * Default is 24 hours.
     *
     * @return the minimum number of milliseconds between sending any two notification digests.
     */
    public int getDigestFrequency() {
        return digestFrequency;
    }

    /**
     * Returns the Date at which a leased subscription will end or has ended. A value of
     * <tt>null</tt> means that the subscription will never expire.
     *
     * @return the Date at which a leased subscription will end or has ended. A value of
     *         <tt>null</tt> means that the subscription will never expire.
     */
    public Date getExpire() {
        return expire;
    }

    /**
     * Returns whether an entity wants to receive an XMPP message body in
     * addition to the payload format.
     *
     * @return true when an entity wants to receive an XMPP message body in
     *         addition to the payload format
     */
    public boolean isIncludingBody() {
        return includingBody;
    }

    /**
     * The presence states for which an entity wants to receive notifications. When the owner
     * is in any of the returned presence states then he is allowed to receive notifications.
     *
     * @return the presence states for which an entity wants to receive notifications.
     *         (e.g. available, away, etc.)
     */
    public Collection<String> getPresenceStates() {
        return presenceStates;
    }

    /**
     * Returns if the owner has subscribed to receive notification of new items only
     * or of new nodes only. When subscribed to a Leaf Node then only <tt>items</tt>
     * is available.
     *
     * @return whether the owner has subscribed to receive notification of new items only
     *         or of new nodes only.
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns 1 when the subscriber wants to receive notifications only from first-level
     * children of the collection. A value of 0 means that the subscriber wants to receive
     * notifications from all descendents.
     *
     * @return 1 when the subscriber wants to receive notifications only from first-level
     *         children of the collection or 0 when notififying only from all descendents.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Returns the keyword that the event needs to match. When <tt>null</tt> all event
     * are going to be notified to the subscriber.
     *
     * @return the keyword that the event needs to match. When <tt>null</tt> all event
     *         are going to be notified to the subscriber.
     */
    public String getKeyword() {
        return keyword;
    }

    void setShouldDeliverNotifications(boolean deliverNotifications) {
        this.deliverNotifications = deliverNotifications;
    }

    void setUsingDigest(boolean usingDigest) {
        this.usingDigest = usingDigest;
    }

    void setDigestFrequency(int digestFrequency) {
        this.digestFrequency = digestFrequency;
    }

    void setExpire(Date expire) {
        this.expire = expire;
    }

    void setIncludingBody(boolean includingBody) {
        this.includingBody = includingBody;
    }

    void setPresenceStates(Collection<String> presenceStates) {
        this.presenceStates = presenceStates;
    }

    void setType(Type type) {
        this.type = type;
    }

    void setDepth(int depth) {
        this.depth = depth;
    }

    void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    void setSavedToDB(boolean savedToDB) {
        this.savedToDB = savedToDB;
    }

    /**
     * Configures the subscription based on the sent {@link DataForm} included in the IQ
     * packet sent by the subscriber. If the subscription was pending of configuration
     * then the last published item is going to be sent to the subscriber.<p>
     *
     * The originalIQ parameter may be <tt>null</tt> when using this API internally. When no
     * IQ packet was sent then no IQ result will be sent to the sender. The rest of the
     * functionality is the same.
     *
     * @param originalIQ the IQ packet sent by the subscriber to configure his subscription or
     *        null when using this API internally.
     * @param options the data form containing the new subscription configuration.
     */
    public void configure(IQ originalIQ, DataForm options) {
        boolean wasUnconfigured = isConfigurationPending();
        // Change the subscription configuration based on the completed form
        configure(options);
        if (originalIQ != null) {
            // Return success response
            service.send(IQ.createResultIQ(originalIQ));
        }

        if (wasUnconfigured) {
            // If subscription is pending then send notification to node owners
            // asking to approve the now configured subscription
            if (isAuthorizationPending()) {
                sendAuthorizationRequest();
            }

            // Send last published item (if node is leaf node and subscription status is ok)
            if (node.isSendItemSubscribe() && isActive()) {
                PublishedItem lastItem = node.getLastPublishedItem();
                if (lastItem != null) {
                    sendLastPublishedItem(lastItem);
                }
            }
        }
    }

    void configure(DataForm options) {
        List<String> values;
        String booleanValue;

        boolean wasUsingPresence = !presenceStates.isEmpty();

        // Remove this field from the form
        options.removeField("FORM_TYPE");
        // Process and remove specific collection node fields
        FormField collectionField = options.getField("pubsub#subscription_type");
        if (collectionField != null) {
            values = collectionField.getValues();
            if (values.size() > 0)  {
                type = Type.valueOf(values.get(0));
            }
            options.removeField("pubsub#subscription_type");
        }
        collectionField = options.getField("pubsub#subscription_depth");
        if (collectionField != null) {
            values = collectionField.getValues();
            depth = "all".equals(values.get(0)) ? 0 : 1;
            options.removeField("pubsub#subscription_depth");
        }
        // If there are more fields in the form then process them and set that
        // the subscription has been configured
        for (FormField field : options.getFields()) {
            boolean fieldExists = true;
            if ("pubsub#deliver".equals(field.getVariable())) {
                values = field.getValues();
                booleanValue = (values.size() > 0 ? values.get(0) : "1");
                deliverNotifications = "1".equals(booleanValue);
            }
            else if ("pubsub#digest".equals(field.getVariable())) {
                values = field.getValues();
                booleanValue = (values.size() > 0 ? values.get(0) : "1");
                usingDigest = "1".equals(booleanValue);
            }
            else if ("pubsub#digest_frequency".equals(field.getVariable())) {
                values = field.getValues();
                digestFrequency = values.size() > 0 ? Integer.parseInt(values.get(0)) : 86400000;
            }
            else if ("pubsub#expire".equals(field.getVariable())) {
                values = field.getValues();
                synchronized (dateFormat) {
                    try {
                        expire = dateFormat.parse(values.get(0));
                    }
                    catch (ParseException e) {
                        Log.error("Error parsing date", e);
                    }
                }
            }
            else if ("pubsub#include_body".equals(field.getVariable())) {
                values = field.getValues();
                booleanValue = (values.size() > 0 ? values.get(0) : "1");
                includingBody = "1".equals(booleanValue);
            }
            else if ("pubsub#show-values".equals(field.getVariable())) {
                // Get the new list of presence states for which an entity wants to
                // receive notifications
                presenceStates = new ArrayList<String>();
                for (String value : field.getValues()) {
                    try {
                        presenceStates.add(value);
                    }
                    catch (Exception e) {
                        // Do nothing
                    }
                }
            }
            else if ("x-pubsub#keywords".equals(field.getVariable())) {
                values = field.getValues();
                keyword = values.isEmpty() ? null : values.get(0);
            }
            else {
                fieldExists = false;
            }
            if (fieldExists) {
                // Subscription has been configured so set the next state
                if (node.getAccessModel().isAuthorizationRequired() && !node.isAdmin(owner)) {
                    state = State.pending;
                }
                else {
                    state = State.subscribed;
                }
            }
        }
        if (savedToDB) {
            // Update the subscription in the backend store
            PubSubPersistenceManager.saveSubscription(node, this, false);
        }
        // Check if the service needs to subscribe or unsubscribe from the owner presence
        if (!node.isPresenceBasedDelivery() && wasUsingPresence != !presenceStates.isEmpty()) {
            if (presenceStates.isEmpty()) {
                service.presenceSubscriptionNotRequired(node, owner);
            }
            else {
                service.presenceSubscriptionRequired(node, owner);
            }
        }
    }

    /**
     * Returns a data form with the subscription configuration. The data form can be used to
     * edit the subscription configuration.
     *
     * @return data form used by the subscriber to edit the subscription configuration.
     */
    public DataForm getConfigurationForm() {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("pubsub.form.subscription.title"));
        List<String> params = new ArrayList<String>();
        params.add(node.getNodeID());
        form.addInstruction(LocaleUtils.getLocalizedString("pubsub.form.subscription.instruction", params));
        // Add the form fields and configure them for edition
        FormField formField = form.addField();
        formField.setVariable("FORM_TYPE");
        formField.setType(FormField.Type.hidden);
        formField.addValue("http://jabber.org/protocol/pubsub#subscribe_options");

        formField = form.addField();
        formField.setVariable("pubsub#deliver");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.subscription.deliver"));
        formField.addValue(deliverNotifications);

        formField = form.addField();
        formField.setVariable("pubsub#digest");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.subscription.digest"));
        formField.addValue(usingDigest);

        formField = form.addField();
        formField.setVariable("pubsub#digest_frequency");
        formField.setType(FormField.Type.text_single);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.subscription.digest_frequency"));
        formField.addValue(digestFrequency);

        formField = form.addField();
        formField.setVariable("pubsub#expire");
        formField.setType(FormField.Type.text_single);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.subscription.expire"));
        if (expire != null) {
            formField.addValue(fastDateFormat.format(expire));
        }

        formField = form.addField();
        formField.setVariable("pubsub#include_body");
        formField.setType(FormField.Type.boolean_type);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.subscription.include_body"));
        formField.addValue(includingBody);

        formField = form.addField();
        formField.setVariable("pubsub#show-values");
        formField.setType(FormField.Type.list_multi);
        formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.subscription.show-values"));
        formField.addOption(null, Presence.Show.away.name());
        formField.addOption(null, Presence.Show.chat.name());
        formField.addOption(null, Presence.Show.dnd.name());
        formField.addOption(null, "online");
        formField.addOption(null, Presence.Show.xa.name());
        for (String value : presenceStates) {
            formField.addValue(value);
        }

        if (node.isCollectionNode()) {
            formField = form.addField();
            formField.setVariable("pubsub#subscription_type");
            formField.setType(FormField.Type.list_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.subscription.subscription_type"));
            formField.addOption(null, Type.items.name());
            formField.addOption(null, Type.nodes.name());
            formField.addValue(type);

            formField = form.addField();
            formField.setVariable("pubsub#subscription_depth");
            formField.setType(FormField.Type.list_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.subscription.subscription_depth"));
            formField.addOption(null, "1");
            formField.addOption(null, "all");
            formField.addValue(depth == 1 ? "1" : "all");
        }

        if (!node.isCollectionNode() || type == Type.items) {
            formField = form.addField();
            formField.setVariable("x-pubsub#keywords");
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.subscription.keywords"));
            if (keyword != null) {
                formField.addValue(keyword);
            }
        }

        return form;
    }

    /**
     * Returns true if an event notification can be sent to the subscriber for the specified
     * published item based on the subsription configuration and subscriber status.
     *
     * @param leafNode the node that received the publication.
     * @param publishedItem the published item to send or null if the publication didn't
     *        contain an item.
     * @return true if an event notification can be sent to the subscriber for the specified
     *         published item.
     */
    public boolean canSendPublicationEvent(LeafNode leafNode, PublishedItem publishedItem) {
        if (!canSendEvents()) {
            return false;
        }
        // Check that any defined keyword was matched (applies only if an item was published)
        if (publishedItem != null && !isKeywordMatched(publishedItem)) {
            return false;
        }
        // Check special conditions when subscribed to collection node
        if (node.isCollectionNode()) {
            // Check if not subscribe to items
            if (Type.items != type) {
                return false;
            }
            // Check if published node is a first-level child of the subscribed node
            if (getDepth() == 1 && !node.isChildNode(leafNode)) {
                return false;
            }
            // Check if published node is a descendant child of the subscribed node
            if (getDepth() == 0 && !node.isDescendantNode(leafNode)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if an event notification can be sent to the subscriber of the collection
     * node for a newly created node that was associated to the collection node or a child
     * node that was deleted. The subscription has to be of type {@link Type#nodes}.
     *
     * @param originatingNode the node that was added or deleted from the collection node.
     * @return true if an event notification can be sent to the subscriber of the collection
     *         node.
     */
    boolean canSendChildNodeEvent(Node originatingNode) {
        // Check that this is a subscriber to a collection node
        if (!node.isCollectionNode()) {
            return false;
        }

        if (!canSendEvents()) {
            return false;
        }
        // Check that subscriber is using type "nodes"
        if (Type.nodes != type) {
            return false;
        }
        // Check if added/deleted node is a first-level child of the subscribed node
        if (getDepth() == 1 && !node.isChildNode(originatingNode)) {
            return false;
        }
        // Check if added/deleted node is a descendant child of the subscribed node
        if (getDepth() == 0 && !node.isDescendantNode(originatingNode)) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if node events such as configuration changed or node purged can be
     * sent to the subscriber.
     *
     * @return true if node events such as configuration changed or node purged can be
     *         sent to the subscriber.
     */
    boolean canSendNodeEvents() {
        return canSendEvents();
    }

    /**
     * Returns true if events in general can be sent. This method checks basic
     * conditions common to all type of event notifications (e.g. item was published,
     * node configuration has changed, new child node was added to collection node, etc.).
     *
     * @return true if events in general can be sent.
     */
    private boolean canSendEvents() {
        // Check if the subscription is active
        if (!isActive()) {
            return false;
        }
        // Check if delivery of notifications is disabled
        if (!shouldDeliverNotifications()) {
            return false;
        }
        // Check if delivery is subject to presence-based policy
        if (!getPresenceStates().isEmpty()) {
            Collection<String> shows = service.getShowPresences(jid);
            if (shows.isEmpty() || Collections.disjoint(getPresenceStates(), shows)) {
                return false;
            }
        }
        // Check if node is only sending events when user is online
        if (node.isPresenceBasedDelivery()) {
            // Check that user is online
            if (service.getShowPresences(jid).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the published item matches the keyword filter specified in
     * the subscription. If no keyword was specified then answer true.
     *
     * @param publishedItem the published item to send.
     * @return true if the published item matches the keyword filter specified in
     *         the subscription.
     */
    boolean isKeywordMatched(PublishedItem publishedItem) {
        // Check if keyword was defined and it was not matched
        if (keyword != null && keyword.length() > 0 && !publishedItem.containsKeyword(keyword)) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if the subscription is active. A subscription is considered active if it
     * has not expired, it has been approved and configured.
     *
     * @return true if the subscription is active.
     */
    public boolean isActive() {
        // Check if subscription is approved and configured (if required)
        if (state != State.subscribed) {
            return false;
        }
        // Check if the subscription has expired
        if (expire != null && new Date().after(expire)) {
            // TODO This checking does not replace the fact that we need to implement expiration.
            // TODO A thread that checks expired subscriptions and removes them is needed.
            return false;
        }
        return true;
    }

    /**
     * Sends the current subscription status to the user that tried to create a subscription to
     * the node. The subscription status is sent to the subsciber after the subscription was
     * created or if the subscriber tries to subscribe many times and the node does not support
     * multpiple subscriptions.
     *
     * @param originalRequest the IQ packet sent by the subscriber to create the subscription.
     */
    void sendSubscriptionState(IQ originalRequest) {
        IQ result = IQ.createResultIQ(originalRequest);
        Element child = result.setChildElement("pubsub", "http://jabber.org/protocol/pubsub");
        Element entity = child.addElement("subscription");
        if (!node.isRootCollectionNode()) {
            entity.addAttribute("node", node.getNodeID());
        }
        entity.addAttribute("jid", getJID().toString());
        if (node.isMultipleSubscriptionsEnabled()) {
            entity.addAttribute("subid", getID());
        }
        entity.addAttribute("subscription", getState().name());
        Element subscribeOptions = entity.addElement("subscribe-options");
        if (node.isSubscriptionConfigurationRequired() && isConfigurationPending()) {
            subscribeOptions.addElement("required");
        }
        // Send the result
        service.send(result);
    }

    /**
     * Sends an event notification for the last published item to the subscriber. If
     * the subscription has not yet been authorized or is pending to be configured then
     * no notification is going to be sent.<p>
     *
     * Depending on the subscription configuration the event notification may or may not have
     * a payload, may not be sent if a keyword (i.e. filter) was defined and it was not matched.
     *
     * @param publishedItem the last item that was published to the node.
     */
    void sendLastPublishedItem(PublishedItem publishedItem) {
        // Check if the published item can be sent to the subscriber
        if (!canSendPublicationEvent(publishedItem.getNode(), publishedItem)) {
            return;
        }
        // Send event notification to the subscriber
        Message notification = new Message();
        Element event = notification.getElement()
                .addElement("event", "http://jabber.org/protocol/pubsub#event");
        Element items = event.addElement("items");
        items.addAttribute("node", node.getNodeID());
        Element item = items.addElement("item");
        if (((LeafNode) node).isItemRequired()) {
            item.addAttribute("id", publishedItem.getID());
        }
        if (node.isPayloadDelivered() && publishedItem.getPayload() != null) {
            item.add(publishedItem.getPayload().createCopy());
        }
        // Add a message body (if required)
        if (isIncludingBody()) {
            notification.setBody(LocaleUtils.getLocalizedString("pubsub.notification.message.body"));
        }
        // Include date when published item was created
        notification.getElement().addElement("delay", "urn:xmpp:delay")
                .addAttribute("stamp", fastDateFormat.format(publishedItem.getCreationDate()));
        // Send the event notification to the subscriber
        service.sendNotification(node, notification, jid);
    }

    /**
     * Returns true if the specified user is allowed to modify or cancel the subscription. Users
     * that are allowed to modify/cancel the subscription are: the entity that is recieving the
     * notifications, the owner of the subscriptions or sysadmins of the pubsub service.
     *
     * @param user the user that is trying to cancel the subscription.
     * @return true if the specified user is allowed to modify or cancel the subscription.
     */
    boolean canModify(JID user) {
        return user.equals(getJID()) || user.equals(getOwner()) || service.isServiceAdmin(user);
    }

    /**
     * Returns the {@link NodeAffiliate} that owns this subscription. Users that have a
     * subscription with the node will ALWAYS have an affiliation even if the
     * affiliation is of type <tt>none</tt>.
     *
     * @return the NodeAffiliate that owns this subscription.
     */
    public NodeAffiliate getAffiliate() {
        return node.getAffiliate(getOwner());
    }

    @Override
	public String toString() {
        return super.toString() + " - JID: " + getJID() + " - State: " + getState().name();
    }

    /**
     * The subscription has been approved by a node owner. The subscription is now active so
     * the subscriber is now allowed to get event notifications.
     */
    void approved() {
        if (state == State.subscribed) {
            // Do nothing
            return;
        }
        state = State.subscribed;

        if (savedToDB) {
            // Update the subscription in the backend store
            PubSubPersistenceManager.saveSubscription(node, this, false);
        }

        // Send last published item (if node is leaf node and subscription status is ok)
        if (node.isSendItemSubscribe() && isActive()) {
            PublishedItem lastItem = node.getLastPublishedItem();
            if (lastItem != null) {
                sendLastPublishedItem(lastItem);
            }
        }
    }

    /**
     * Sends an request to authorize the pending subscription to the specified owner.
     *
     * @param owner the JID of the user that will get the authorization request.
     */
    public void sendAuthorizationRequest(JID owner) {
        Message authRequest = new Message();
        authRequest.addExtension(node.getAuthRequestForm(this));
        authRequest.setTo(owner);
        authRequest.setFrom(service.getAddress());
        // Send authentication request to node owners
        service.send(authRequest);
    }

    /**
     * Sends an request to authorize the pending subscription to all owners. The first
     * answer sent by a owner will be processed. Rest of the answers will be discarded.
     */
    public void sendAuthorizationRequest() {
        Message authRequest = new Message();
        authRequest.addExtension(node.getAuthRequestForm(this));
        // Send authentication request to node owners
        service.broadcast(node, authRequest, node.getOwners());
    }

    /**
     * Subscriptions to a node may exist in several states. Delivery of event notifications
     * varies according to the subscription state of the user with the node.
     */
    public static enum State {

        /**
         * The node will never send event notifications or payloads to users in this state. Users
         * with subscription state none and affiliation none are going to be deleted.
         */
        none,
        /**
         * An entity has requested to subscribe to a node and the request has not yet been
         * approved by a node owner. The node will not send event notifications or payloads
         * to the entity while it is in this state.
         */
        pending,
        /**
         * An entity has subscribed but its subscription options have not yet been configured.
         * The node will send event notifications or payloads to the entity while it is in this
         * state. Default subscription configuration is going to be assumed.
         */
        unconfigured,
        /**
         * An entity is subscribed to a node. The node will send all event notifications
         * (and, if configured, payloads) to the entity while it is in this state.
         */
        subscribed
    }

    public static enum Type {

        /**
         * Receive notification of new items only.
         */
        items,
        /**
         * Receive notification of new nodes only.
         */
        nodes
    }
}
