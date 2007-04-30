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
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.xmpp.packet.JID;

/**
 * Subscribers, publishers and owners may publish items to the node.
 *
 * @author Matt Tucker
 */
public class OnlySubscribers extends PublisherModel {

    public boolean canPublish(Node node, JID entity) {
        NodeAffiliate nodeAffiliate = node.getAffiliate(entity);
        // Deny access if user does not have any relation with the node or is an outcast
        if (nodeAffiliate == null ||
                nodeAffiliate.getAffiliation() == NodeAffiliate.Affiliation.outcast) {
            return false;
        }
        // Grant access if user is an owner of publisher
        if (nodeAffiliate.getAffiliation() == NodeAffiliate.Affiliation.publisher ||
                nodeAffiliate.getAffiliation() == NodeAffiliate.Affiliation.owner) {
            return true;
        }
        // Grant access if at least one subscription of this user was approved
        for (NodeSubscription subscription : nodeAffiliate.getSubscriptions()) {
            if (subscription.isActive()) {
                return true;
            }
        }
        return false;
    }

    public String getName() {
        return "subscribers";
    }
}
