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

package org.jivesoftware.openfire.pubsub.models;

import org.jivesoftware.openfire.pubsub.Node;
import org.xmpp.packet.JID;

/**
 * Policy that defines who is allowed to publish items to the node.
 *
 * @author Matt Tucker
 */
public abstract class PublisherModel {

    public final static PublisherModel open = new OpenPublisher();
    public final static PublisherModel publishers = new OnlyPublishers();
    public final static PublisherModel subscribers = new OnlySubscribers();

    /**
     * Returns the specific subclass of PublisherModel as specified by the publisher
     * model name. If an unknown name is specified then an IllegalArgumentException
     * is going to be thrown.
     *
     * @param name the name of the subsclass.
     * @return the specific subclass of PublisherModel as specified by the access
     *         model name.
     */
    public static PublisherModel valueOf(String name) {
        if ("open".equals(name)) {
            return open;
        }
        else if ("publishers".equals(name)) {
            return publishers;
        }
        else if ("subscribers".equals(name)) {
            return subscribers;
        }
        throw new IllegalArgumentException("Unknown publisher model: " + name);
    }
    /**
     * Returns the name as defined by the JEP-60 spec.
     *
     * @return the name as defined by the JEP-60 spec.
     */
    public abstract String getName();

    /**
     * Returns true if the entity is allowed to publish items to the specified node.
     *
     * @param node       the node that may get a new published item by the specified entity.
     * @param entity     the JID of the entity that wants to publish an item to the node.
     * @return true if the subscriber is allowed to publish items to the specified node.
     */
    public abstract boolean canPublish(Node node, JID entity);

}
