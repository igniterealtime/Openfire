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
 * Anyone may publish items to the node.
 *
 * @author Matt Tucker
 */
public class OpenPublisher extends PublisherModel {

    public boolean canPublish(Node node, JID entity) {
        return true;
    }

    public String getName() {
        return "open";
    }
}
