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

package org.jivesoftware.openfire.pubsub.models;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * Anyone with a presence subscription of both or from may subscribe and retrieve items.
 *
 * @author Matt Tucker
 */
public class PresenceAccess extends AccessModel {

    private static final Logger Log = LoggerFactory.getLogger(PresenceAccess.class);

    PresenceAccess() {
    }

    @Override
    public boolean canSubscribe(Node node, JID owner, JID subscriber) {
        // Let node owners and sysadmins always subcribe to the node
        if (node.isAdmin(owner)) {
            return true;
        }
        XMPPServer server = XMPPServer.getInstance();
        for (JID nodeOwner : node.getOwners()) {
            // Give access to the owner of the roster :)
            if (nodeOwner.equals(owner)) {
                return true;
            }
            // Check that the node owner is a local user
            if (server.isLocal(nodeOwner)) {
                try {
                    Roster roster = server.getRosterManager().getRoster(nodeOwner.getNode());
                    RosterItem item = roster.getRosterItem(owner);
                    // Check that the subscriber is subscribe to the node owner's presence
                    return item != null && (RosterItem.SUB_BOTH == item.getSubStatus() ||
                            RosterItem.SUB_FROM == item.getSubStatus());
                }
                catch (UserNotFoundException e) {
                    // Do nothing
                }
            }
            else {
                // Owner of the node is a remote user. This should never happen.
                Log.warn("Node with access model Presence has a remote user as owner: {}",node.getUniqueIdentifier());
            }
        }
        return false;
    }

    @Override
    public boolean canAccessItems(Node node, JID owner, JID subscriber) {
        return canSubscribe(node, owner, subscriber);
    }

    @Override
    public String getName() {
        return "presence";
    }

    @Override
    public PacketError.Condition getSubsriptionError() {
        return PacketError.Condition.not_authorized;
    }

    @Override
    public Element getSubsriptionErrorDetail() {
        return DocumentHelper.createElement(QName.get("presence-subscription-required",
                "http://jabber.org/protocol/pubsub#errors"));
    }

    @Override
    public boolean isAuthorizationRequired() {
        return false;
    }
}
