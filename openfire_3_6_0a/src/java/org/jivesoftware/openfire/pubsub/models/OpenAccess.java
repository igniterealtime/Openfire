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
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.dom4j.Element;

/**
 * Anyone may subscribe and retrieve items.
 *
 * @author Matt Tucker
 */
public class OpenAccess extends AccessModel {

    OpenAccess() {
    }

    public boolean canSubscribe(Node node, JID owner, JID subscriber) {
        return true;
    }

    public boolean canAccessItems(Node node, JID owner, JID subscriber) {
        return true;
    }

    public String getName() {
        return "open";
    }

    public PacketError.Condition getSubsriptionError() {
        // Return nothing since users can always subscribe to the node
        return null;
    }

    public Element getSubsriptionErrorDetail() {
        // Return nothing since users can always subscribe to the node
        return null;
    }

    public boolean isAuthorizationRequired() {
        return false;
    }
}
