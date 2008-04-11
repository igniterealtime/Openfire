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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

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
        // Let node owners and sysadmins always get node items
        if (node.isAdmin(owner)) {
            return true;
        }
        NodeAffiliate nodeAffiliate = node.getAffiliate(owner);
        if  (nodeAffiliate == null) {
            // This is an unknown entity to the node so deny access
            return false;
        }
        // Any subscription of this entity that was approved will give him access
        // to retrieve the node items
        for (NodeSubscription subscription : nodeAffiliate.getSubscriptions()) {
            if (subscription.isActive()) {
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
