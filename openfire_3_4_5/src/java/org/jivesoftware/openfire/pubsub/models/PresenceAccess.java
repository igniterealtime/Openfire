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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * Anyone with a presence subscription of both or from may subscribe and retrieve items.
 *
 * @author Matt Tucker
 */
public class PresenceAccess extends AccessModel {

    PresenceAccess() {
    }

    public boolean canSubscribe(Node node, JID owner, JID subscriber) {
        // Let node owners and sysadmins always subcribe to the node
        if (node.isAdmin(owner)) {
            return true;
        }
        // Get the only owner of the node
        JID nodeOwner = node.getOwners().iterator().next();
        // Give access to the owner of the roster :)
        if (nodeOwner.toBareJID().equals(owner.toBareJID())) {
            return true;
        }
        // Get the roster of the node owner
        XMPPServer server = XMPPServer.getInstance();
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
                return false;
            }
        }
        else {
            // Owner of the node is a remote user. This should never happen.
            Log.warn("Node with access model Presence has a remote user as owner: " +
                    node.getNodeID());
            return false;
        }
    }

    public boolean canAccessItems(Node node, JID owner, JID subscriber) {
        return canSubscribe(node, owner, subscriber);
    }

    public String getName() {
        return "presence";
    }

    public PacketError.Condition getSubsriptionError() {
        return PacketError.Condition.not_authorized;
    }

    public Element getSubsriptionErrorDetail() {
        return DocumentHelper.createElement(QName.get("presence-subscription-required",
                "http://jabber.org/protocol/pubsub#errors"));
    }

    public boolean isAuthorizationRequired() {
        return false;
    }
}
