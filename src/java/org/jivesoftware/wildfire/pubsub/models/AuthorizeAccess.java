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

package org.jivesoftware.wildfire.pubsub.models;

import org.jivesoftware.wildfire.pubsub.Node;
import org.jivesoftware.wildfire.pubsub.NodeSubscription;
import org.jivesoftware.wildfire.pubsub.NodeAffiliate;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.dom4j.Element;
import org.dom4j.DocumentHelper;
import org.dom4j.QName;

/**
 * Subscription requests must be approved and only subscribers may retrieve items.
 *
 * @author Matt Tucker
 */
public class AuthorizeAccess extends AccessModel {

    AuthorizeAccess() {
    }

    public boolean canSubscribe(Node node, JID owner, JID subscriber) {
        return true;
    }

    public boolean canAccessItems(Node node, JID owner, JID subscriber) {
        NodeAffiliate nodeAffiliate = node.getAffiliate(owner);
        if  (nodeAffiliate == null) {
            // This is an unknown entity to the node so deny access
            return false;
        }
        // Any subscription of this entity that was approved will give him access
        // to retrieve the node items
        for (NodeSubscription subscription : nodeAffiliate.getSubscriptions()) {
            if (subscription.isApproved()) {
                return true;
            }
        }
        // No approved subscription was found so deny access
        return false;
    }

    public String getName() {
        return "authorize";
    }

    public PacketError.Condition getSubsriptionError() {
        return PacketError.Condition.not_authorized;
    }

    public Element getSubsriptionErrorDetail() {
        return DocumentHelper.createElement(QName.get("not-subscribed",
                "http://jabber.org/protocol/pubsub#errors"));
    }

    public boolean isAuthorizationRequired() {
        return true;
    }
}
