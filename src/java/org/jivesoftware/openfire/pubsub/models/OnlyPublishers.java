/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.pubsub.models;

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.xmpp.packet.JID;

/**
 * Publishers and owners may publish items to the node.
 *
 * @author Matt Tucker
 */
public class OnlyPublishers extends PublisherModel {

    public boolean canPublish(Node node, JID entity) {
        NodeAffiliate nodeAffiliate = node.getAffiliate(entity);
        return nodeAffiliate != null && (
                nodeAffiliate.getAffiliation() == NodeAffiliate.Affiliation.publisher ||
                        nodeAffiliate.getAffiliation() == NodeAffiliate.Affiliation.owner);
    }

    public String getName() {
        return "publishers";
    }
}
