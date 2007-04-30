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
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * Only those on a whitelist may subscribe and retrieve items.
 *
 * @author Matt Tucker
 */
public class WhitelistAccess extends AccessModel {

    WhitelistAccess() {
    }

    public boolean canSubscribe(Node node, JID owner, JID subscriber) {
        // Let node owners and sysadmins always subcribe to the node
        if (node.isAdmin(owner)) {
            return true;
        }
        // User is in the whitelist if he has an affiliation and it is not of type outcast
        NodeAffiliate nodeAffiliate = node.getAffiliate(owner);
        return nodeAffiliate != null &&
                nodeAffiliate.getAffiliation() != NodeAffiliate.Affiliation.outcast;
    }

    public boolean canAccessItems(Node node, JID owner, JID subscriber) {
        return canSubscribe(node, owner, subscriber);
    }

    public String getName() {
        return "whitelist";
    }

    public PacketError.Condition getSubsriptionError() {
        return PacketError.Condition.not_allowed;
    }

    public Element getSubsriptionErrorDetail() {
        return DocumentHelper.createElement(
                QName.get("closed-node", "http://jabber.org/protocol/pubsub#errors"));
    }

    public boolean isAuthorizationRequired() {
        return false;
    }
}
