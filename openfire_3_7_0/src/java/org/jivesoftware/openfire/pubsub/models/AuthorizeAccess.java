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
