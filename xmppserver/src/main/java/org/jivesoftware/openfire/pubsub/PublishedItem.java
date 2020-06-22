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

import java.io.Serializable;
import java.io.StringReader;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pep.PEPServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * A published item to a node. Once an item was published to a node, node subscribers will be
 * notified of the new published item. The item publisher may be allowed to delete published
 * items. After a published item was deleted node subscribers will get an event notification.<p>
 *
 * Published items may be persisted to the database depending on the node configuration.
 * Actually, even when the node is configured to not persist items the last published
 * item is going to be persisted to the database. The reason for this is that the node
 * may need to send the last published item to new subscribers.
 *
 * @author Matt Tucker
 */
public class PublishedItem implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(PublishedItem.class);

    private static final int POOL_SIZE = 50;
    /**
     * Pool of SAX Readers. SAXReader is not thread safe so we need to have a pool of readers.
     */
    private static BlockingQueue<SAXReader> xmlReaders = new LinkedBlockingQueue<>(POOL_SIZE);

    private static final long serialVersionUID = 7012925993623144574L;
    
    static {
        // Initialize the pool of sax readers
        for (int i=0; i<POOL_SIZE; i++) {
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            xmlReaders.add(xmlReader);
        }    	
    }
    
    /**
     * JID of the entity that published the item to the node. This is the full JID
     * of the publisher.
     */
    private JID publisher;
    /**
     * The node where the item was published.
     */
    private volatile transient LeafNode node;
    /**
     * The id for the node where the item was published.
     */
    private String nodeId;
    /**
     * The id for the service hosting the node for this item
     */
    private String serviceId;
    /**
     * ID that uniquely identifies the published item in the node.
     */
    private String id;
    /**
     * The datetime when the items was published.
     */
    private Date creationDate;
    /**
     * The optional payload is included when publishing the item. This value
     * is created from the payload XML and cached as/when needed.
     */
    private volatile transient Element payload;
    /**
     * XML representation of the payload (for serialization)
     */
    private String payloadXML;
    
    /**
     * Creates a published item
     * @param node The node the published item is created in
     * @param publisher The JID of the account creating the item
     * @param id The unique ID of the item
     * @param creationDate The date it was created
     */
    PublishedItem(LeafNode node, JID publisher, String id, Date creationDate) {
        this.node = node;
        this.nodeId = node.getUniqueIdentifier().getNodeId();
        this.serviceId = node.getUniqueIdentifier().getServiceIdentifier().getServiceId();
        this.publisher = publisher;
        this.id = id;
        this.creationDate = creationDate;
    }

    /**
     * Returns the id for the {@link LeafNode} where this item was published.
     *
     * @return the ID for the leaf node where this item was published.
     */
    public String getNodeID() {
        return nodeId;
    }

    /**
     * Returns the {@link LeafNode} where this item was published.
     *
     * @return the leaf node where this item was published.
     */
    public LeafNode getNode() {
        if (node == null) {
            synchronized (this) {
                if (node == null) {
                    if (XMPPServer.getInstance().getPubSubModule().getServiceID().equals(serviceId))
                    {
                        node = (LeafNode) XMPPServer.getInstance().getPubSubModule().getNode(nodeId);
                    }
                    else
                    {
                        PEPServiceManager serviceMgr = XMPPServer.getInstance().getIQPEPHandler().getServiceManager();
                        JID service = new JID( serviceId );
                        node = serviceMgr.hasCachedService(service) ? (LeafNode) serviceMgr.getPEPService(service).getNode(nodeId) : null;
                    }
                }
            }
        }
        return node;
    }

    /**
     * Returns the ID that uniquely identifies the published item in the node.
     *
     * @return the ID that uniquely identifies the published item in the node.
     */
    public String getID() {
        return id;
    }

    /**
     * Returns the JID of the entity that published the item to the node.
     *
     * @return the JID of the entity that published the item to the node.
     */
    public JID getPublisher() {
        return publisher;
    }

    /**
     * Returns the datetime when the items was published.
     *
     * @return the datetime when the items was published.
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Returns the payload included when publishing the item. A published item may or may not
     * have a payload. Transient nodes that are configured to not broadcast payloads may allow
     * published items to have no payload.
     *
     * @return the payload included when publishing the item or {@code null} if none was found.
     */
    public Element getPayload() {
        if (payload == null && payloadXML != null) {
            synchronized (this) {
                if (payload == null) {
                    // payload initialized as XML string from DB
                    SAXReader xmlReader = null;
                    try {
                        xmlReader = xmlReaders.take();
                        payload = xmlReader.read(new StringReader(payloadXML)).getRootElement(); 
                    } catch (Exception ex) {
                         log.error("Failed to parse payload XML", ex);
                    } finally {
                        if (xmlReader != null) {
                            xmlReaders.add(xmlReader);
                        }
                    }
                }
            }
        }
        return payload;
    }

    /**
     * Returns a textual representation of the payload or {@code null} if no payload
     * was specified with the item.
     *
     * @return a textual representation of the payload or null if no payload was specified
     *         with the item.
     */
    public String getPayloadXML() {
        return payloadXML;
    }

    /**
     * Sets the payload included when publishing the item. A published item may or may not
     * have a payload. Transient nodes that are configured to not broadcast payloads may allow
     * published items to have no payload.
     *
     * @param payloadXML the payload included when publishing the item or {@code null}
     *        if none was found.
     */
    void setPayloadXML(String payloadXML) {
        this.payloadXML = payloadXML;
        this.payload = null; // will be recreated only if needed
    }

    /**
     * Sets the payload included when publishing the item. A published item may or may not
     * have a payload. Transient nodes that are configured to not broadcast payloads may allow
     * published items to have no payload.
     *
     * @param payload the payload included when publishing the item or {@code null}
     *        if none was found.
     */
    void setPayload(Element payload) {
        this.payload = payload;
        // Update XML representation of the payload
        if (payload == null) {
            payloadXML = null;
        } else {
            payloadXML = payload.asXML();
        }
    }

    /**
     * Returns true if payload contains the specified keyword. If the item has no payload
     * or keyword is {@code null} then return true.
     *
     * @param keyword the keyword to look for in the payload.
     * @return true if payload contains the specified keyword.
     */
    boolean containsKeyword(String keyword) {
        if (getPayloadXML() == null || keyword == null) {
            return true;
        }
        return payloadXML.contains(keyword);
    }

    /**
     * Returns true if the user that is trying to delete an item is allowed to delete it.
     * Only the publisher or node admins (i.e. owners and sysadmins) are allowed to delete items.
     *
     * @param user the full JID of the user trying to delete the item.
     * @return true if the user that is trying to delete an item is allowed to delete it.
     */
    public boolean canDelete(JID user) {
        return publisher.equals(user)
            || publisher.toBareJID().equals(user.toBareJID())
            || getNode().isAdmin(user);
    }

    /**
     * Returns a string that uniquely identifies this published item in the following format: <i>nodeId:itemId</i>
     *
     * Note that this method generates a value that's unique only in context of the node. This allows for different
     * services to have items with identical keys. Use {@link #getUniqueIdentifier()} to obtain an identifier that is
     * unique for the entire XMPP domain.
     *
     * @return Unique identifier for this item
     * @deprecated Replaced by {@link #getUniqueIdentifier()} which generates more unique values.
     */
    @Deprecated
    public String getItemKey() {
        return getItemKey(serviceId, nodeId, id);
    }

    /**
     * Returns a string that uniquely identifies this published item in the following format: <i>nodeId:itemId</i>
     *
     * Note that this method generates a value that's unique only in context of the node. This allows for different
     * services to have items with identical keys. Use {@link #getUniqueIdentifier(LeafNode, String)} to obtain an
     * identifier that is unique for the entire XMPP domain.
     *
     * @param node Node for the published item
     * @param itemId Id for the published item (unique within the node)
     * @return Unique identifier for this item
     * @deprecated Replaced by {@link #getUniqueIdentifier(LeafNode, String)} which generates more unique values.
     */
    @Deprecated
    public static String getItemKey(LeafNode node, String itemId) {
        return getItemKey(node.getUniqueIdentifier().getServiceIdentifier().getServiceId(), node.getUniqueIdentifier().getNodeId(), itemId);
    }

    /**
     * Returns a string that uniquely identifies this published item
     * in the following format: {@code serviceId:nodeId:itemId}. This matches the primary key of the ofPubSubItem table.
     * @param serviceId Service id for the published item
     * @param nodeId Node id for the published item
     * @param itemId Id for the published item (unique within the node)
     * @return Unique identifier for this item
     * @deprecated Replaced by {@link #getUniqueIdentifier(String, String, String)} which generates more unique values.
     */
    @Deprecated
    public static String getItemKey(String serviceId, String nodeId, String itemId) {
    	return new StringBuilder(serviceId).append(nodeId)
    		.append(':').append(itemId).toString();
    }

    /**
     * Returns a value that uniquely identifies this published item in the XMPP domain.
     *
     * @return Unique identifier for this item
     */
    public UniqueIdentifier getUniqueIdentifier() {
        return getUniqueIdentifier( getNode(), id );
    }

    /**
     * Returns a value that uniquely identifies this published item in the XMPP domain.
     *
     * @param node Node for the published item
     * @param itemId Id for the published item (unique within the node)
     * @return Unique identifier for this item
     */
    public static UniqueIdentifier getUniqueIdentifier(LeafNode node, String itemId)
    {
        return getUniqueIdentifier( node.getUniqueIdentifier().getServiceIdentifier().getServiceId(), node.getUniqueIdentifier().getNodeId(), itemId );
    }

    /**
     * Returns a value that uniquely identifies this published item in the XMPP domain.
     *
     * @param serviceId Id of the service that contains the node.
     * @param nodeId Node id for the published item
     * @param itemId Id for the published item (unique within the node)
     * @return Unique identifier for this item
     */
    public static UniqueIdentifier getUniqueIdentifier(String serviceId, String nodeId, String itemId)
    {
        return new UniqueIdentifier( serviceId, nodeId, itemId );
    }

    /**
     * A unique identifier for an item, in context of all nodes of all services in the system.
     *
     * The properties that uniquely identify an item are its node, and its itemId.
     */
    public final static class UniqueIdentifier implements Serializable
    {
        private final String serviceId;
        private final String nodeId;
        private final String itemId;

        public UniqueIdentifier( final String serviceId, final String nodeId, final String itemId ) {
            if ( serviceId == null ) {
                throw new IllegalArgumentException( "Argument 'serviceId' cannot be null." );
            }
            if ( nodeId == null ) {
                throw new IllegalArgumentException( "Argument 'nodeId' cannot be null." );
            }
            if ( itemId == null ) {
                throw new IllegalArgumentException( "Argument 'itemId' cannot be null." );
            }
            this.serviceId = serviceId;
            this.nodeId = nodeId;
            this.itemId = itemId;
        }

        public UniqueIdentifier( final Node.UniqueIdentifier nodeIdentifier, final String itemId ) {
            if ( nodeIdentifier == null ) {
                throw new IllegalArgumentException( "Argument 'nodeIdentifier' cannot be null." );
            }
            if ( itemId == null ) {
                throw new IllegalArgumentException( "Argument 'itemId' cannot be null." );
            }
            this.serviceId = nodeIdentifier.getServiceIdentifier().getServiceId();
            this.nodeId = nodeIdentifier.getNodeId();
            this.itemId = itemId;
        }

        public PubSubService.UniqueIdentifier getServiceIdentifier()
        {
            return new PubSubService.UniqueIdentifier( serviceId );
        }

        public Node.UniqueIdentifier getNodeIdentifier()
        {
            return new Node.UniqueIdentifier( serviceId, nodeId );
        }

        public String getItemId() { return itemId; };

        @Override
        public boolean equals( final Object o )
        {
            if ( this == o ) { return true; }
            if ( o == null || getClass() != o.getClass() ) { return false; }
            final UniqueIdentifier that = (UniqueIdentifier) o;
            return serviceId.equals(that.serviceId) &&
                nodeId.equals(that.nodeId) &&
                itemId.equals(that.itemId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(serviceId, nodeId, itemId);
        }

        @Override
        public String toString()
        {
            return "UniqueIdentifier{" +
                "serviceId='" + serviceId + '\'' +
                ", nodeId='" + nodeId + '\'' +
                ", itemId='" + itemId + '\'' +
                '}';
        }
    }
}
