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

import org.jivesoftware.openfire.commands.AdHocCommandManager;
import org.jivesoftware.openfire.entitycaps.EntityCapabilitiesListener;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A PubSubService is responsible for keeping the hosted nodes by the service, the default
 * configuration to use for newly created nodes and specify the policy to use regarding
 * node management.<p>
 *
 * Implementations of PubSubService are expected to collaborate with a {@link PubSubEngine}
 * that will take care of handling packets sent to the service.<p>
 *
 * The separation between <code>PubSubService</code> and <code>PubSubEngine</code> allows to
 * reuse the handling of packets and at the same time be able to create different pubsub
 * services with different configurations. Examples of different pubsub services are:
 * XEP-60: Publish-Subscribe and XEP-163: Personal Eventing Protocol.
 *
 * @author Matt Tucker
 */
public interface PubSubService
{
    /**
     * Returns the XMPP address of the service.
     *
     * @return the XMPP address of the service.
     */
    JID getAddress();

    /**
     * Returns a String that uniquely identifies this pubsub service. This information is
     * being used when storing node information in the database so it's possible to have
     * nodes with the same ID but under different pubsub services.
     *
     * @return a String that uniquely identifies this pubsub service.
     */
    String getServiceID();

    /**
     * Returns a registry of the presence's show value of users that subscribed to a node of
     * the pubsub service and for which the node only delivers notifications for online users
     * or node subscriptions deliver events based on the user presence show value. Offline
     * users will not have an entry in the map. Note: Key-> bare JID and Value-> Map whose key
     * is full JID of connected resource and value is show value of the last received presence.
     * 
     * @return a registry of the presence's show value of users that subscribed to a node
     *         of the pubsub service.
     * @deprecated Replaced by #getSubscriberPresences. Note, since being deprecated, changes to the return value are no longer applied!
     */
    default Map<String, Map<String, String>> getBarePresences() {
        return getSubscriberPresences().entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().toBareJID(),
                e -> e.getValue().entrySet().stream()
                    .collect( Collectors.toMap(
                        v -> v.getKey().toString(),
                        Map.Entry::getValue
                    ))
            ));
    }

    /**
     * Returns a registry of the presence's show value of users that subscribed to a node of
     * the pubsub service and for which the node only delivers notifications for online users
     * or node subscriptions deliver events based on the user presence show value. Offline
     * users will not have an entry in the map. Note: Key-> bare JID and Value-> Map whose key
     * is full JID of connected resource and value is show value of the last received presence.
     *
     * @return a registry of the presence's show value of users that subscribed to a node
     *         of the pubsub service.
     */
    Map<JID, Map<JID, String>> getSubscriberPresences();

    /**
     * Returns true if the pubsub service allows the specified user to create nodes.
     *
     * @param creator the JID of the entity trying to create a new node.
     * @return true if the pubsub service allows the specified user to create nodes.
     */
    boolean canCreateNode(JID creator);

    /**
     * Returns true if the specified user is a sysadmin of the pubsub service or has
     * admin privileges.
     *
     * @param user the user to check if he has admin privileges.
     * @return true if the specified user is a sysadmin of the pubsub service or has
     *         admin privileges.
     */
    boolean isServiceAdmin(JID user);

    /**
     * Returns true if the pubsub service allows users to create nodes without specifying
     * the node ID. The service will create a random node ID and assigne it to the node.
     *
     * @return true if the pubsub service allows users to create nodes without specifying
     *         the node ID.
     */
    boolean isInstantNodeSupported();

    /**
     * Returns true if the pubsub service supports collection nodes. When collection nodes is
     * supported it is possible to create hierarchy of nodes where a {@link CollectionNode}
     * may only hold children nodes of type {@link CollectionNode} or {@link LeafNode}. On the
     * other hand, {@link LeafNode} can only hold {@link PublishedItem}.
     *
     * @return true if the pubsub service supports collection nodes.
     */
    boolean isCollectionNodesSupported();

    /**
     * Returns the {@link CollectionNode} that acts as the root node of the entire
     * node hierarchy. The returned node does not have a node identifier. If collection
     * nodes is not supported then return {@code null}.
     *
     * @return the CollectionNode that acts as the root node of the entire node hierarchy
     *         or {@code null} if collection nodes is not supported.
     */
    CollectionNode getRootCollectionNode();

    /**
     * Returns the {@link Node} that matches the specified node ID or {@code null} if
     * none was found.
     *
     * @param nodeID the ID that uniquely identifies the node in the pubsub service.
     * @return the Node that matches the specified node ID or {@code null} if none was found.
     */
    default Node getNode(String nodeID) {
        return getNode( new Node.UniqueIdentifier( getUniqueIdentifier(), nodeID));
    };

    /**
     * Returns the {@link Node} that matches the specified node ID or {@code null} if
     * none was found.
     *
     * @param nodeID the ID that uniquely identifies the node.
     * @return the Node that matches the specified node ID or {@code null} if none was found.
     */
    Node getNode(Node.UniqueIdentifier nodeID);

    /**
     * Returns the collection of nodes hosted by the pubsub service. The collection does
     * not support modifications.
     *
     * @return the collection of nodes hosted by the pubsub service.
     */
    Collection<Node> getNodes();

    /**
     * Adds an already persistent node to the service.
     *
     * @param node the persistent node to add to the service.
     */
    void addNode(Node node);

    /**
     * Removes the specified node from the service. Most probably the node was deleted from
     * the database as well.<p>
     *
     * A future version may support unloading of inactive nodes even though they may still
     * exist in the database.
     *
     * @param nodeID the ID that uniquely identifies the node in the pubsub service.
     */
    default void removeNode(String nodeID) {
        removeNode( new Node.UniqueIdentifier(getUniqueIdentifier(), nodeID));
    };

    /**
     * Removes the specified node from the service. Most probably the node was deleted from
     * the database as well.<p>
     *
     * A future version may support unloading of inactive nodes even though they may still
     * exist in the database.
     *
     * @param nodeID the ID that uniquely identifies the node.
     */
    void removeNode(Node.UniqueIdentifier nodeID);

    /**
     * Broadcasts the specified Message containing an event notification to a list
     * of subscribers to the specified node. Each message being sent has to have a unique
     * ID value so that the service can properly track any notification-related errors
     * that may occur.
     *
     * @param node the node that triggered the event notification.
     * @param message the message containing the event notification.
     * @param jids the list of entities to get the event notification.
     */
    void broadcast(Node node, Message message, Collection<JID> jids);

    /**
     * Sends the specified packet.
     *
     * @param packet the packet to send.
     */
    void send(Packet packet);

    /**
     * Sends the specified Message containing an event notification to a specific
     * subscriber of the specified node. The message being sent has to have a unique
     * ID value so that the service can properly track any notification-related errors
     * that may occur.
     *
     * @param node the node that triggered the event notification.
     * @param message the message containing the event notification.
     * @param jid the entity to get the event notification.
     */
    void sendNotification(Node node, Message message, JID jid);

    /**
     * Returns the default node configuration for the specified node type or {@code null}
     * if the specified node type is not supported by the service.
     *
     * @param leafType true when requesting default configuration of leaf nodes
     * @return the default node configuration for the specified node type or {@code null}
     *         if the specified node type is not supported by the service.
     */
    DefaultNodeConfiguration getDefaultNodeConfiguration(boolean leafType);

    /**
     * Returns the show values of the last know presence of all connected resources of the
     * specified subscriber. When the subscriber JID is a bare JID then the answered collection
     * will have many entries one for each connected resource. Moreover, if the user
     * is offline then an empty collectin is returned. Available show status is represented
     * by a {@code online} value. The rest of the possible show values as defined in RFC 3921.
     *
     * @param subscriber the JID of the subscriber. This is not the JID of the affiliate.
     * @return an empty collection when offline. Otherwise, a collection with the show value
     *         of each connected resource.
     */
    Collection<String> getShowPresences(JID subscriber);

    /**
     * Requests the pubsub service to subscribe to the presence of the user. If the service
     * has already subscribed to the user's presence then do nothing.
     *
     * @param node the node that originated the subscription request.
     * @param user the JID of the affiliate to subscribe to his presence.
     */
    void presenceSubscriptionRequired(Node node, JID user);

    /**
     * Requests the pubsub service to unsubscribe from the presence of the user. If the service
     * was not subscribed to the user's presence or any node still requires to be subscribed to
     * the user presence then do nothing.
     *
     * @param node the node that originated the unsubscription request.
     * @param user the JID of the affiliate to unsubscribe from his presence.
     */
    void presenceSubscriptionNotRequired(Node node, JID user);

    /**
     * Returns true if a user may have more than one subscription with the node. When
     * multiple subscriptions is enabled each subscription request, event notification and
     * unsubscription request should include a {@code subid} attribute.
     *
     * @return true if a user may have more than one subscription with the node.
     */
    boolean isMultipleSubscriptionsEnabled();

    /**
     * Returns the ad-hoc commands manager used for this service.
     * 
     * @return the ad-hoc commands manager used for this service.
     */
    AdHocCommandManager getManager();

    /**
     * Returns a value that uniquely identifies this service in the XMPP domain.
     *
     * @return Unique identifier for this service
     */
    default UniqueIdentifier getUniqueIdentifier() {
        return new UniqueIdentifier( getServiceID() );
    }

    /**
     * A unique identifier for an item, in context of all nodes of all services in the system.
     *
     * The property that uniquely identifies a service within the system is its serviceId.
     */
    final class UniqueIdentifier implements Serializable
    {
        private final String serviceId;

        public UniqueIdentifier( final String serviceId ) {
            if ( serviceId == null ) {
                throw new IllegalArgumentException( "Argument 'serviceId' cannot be null." );
            }
            this.serviceId = serviceId;
        }

        public String getServiceId() { return serviceId; }

        public boolean owns( Node.UniqueIdentifier nodeIdentifier )
        {
            return this.equals( nodeIdentifier.getServiceIdentifier() );
        }

        public boolean owns( PublishedItem.UniqueIdentifier itemIdentifier )
        {
            return this.equals( itemIdentifier.getServiceIdentifier() );
        }

        @Override
        public boolean equals( final Object o )
        {
            if ( this == o ) { return true; }
            if ( o == null || getClass() != o.getClass() ) { return false; }
            final UniqueIdentifier that = (UniqueIdentifier) o;
            return serviceId.equals( that.serviceId );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( serviceId );
        }

        @Override
        public String toString()
        {
            return "PubSubService.UniqueIdentifier{" +
                    "serviceId='" + getServiceId() + '\'' +
                    '}';
        }
    }
}
