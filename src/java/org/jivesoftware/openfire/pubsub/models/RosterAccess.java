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

import java.util.Collection;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.pubsub.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * Anyone in the specified roster group(s) may subscribe and retrieve items.
 *
 * @author Matt Tucker
 */
public class RosterAccess extends AccessModel {

	private static final Logger Log = LoggerFactory.getLogger(RosterAccess.class);

    RosterAccess() {
    }

    @Override
	public boolean canSubscribe(Node node, JID subscriber, JID subJID) {
        // Let node owners and sysadmins always subscribe to the node
        if (node.isAdmin(subscriber)) {
            return true;
        }
        for (JID nodeOwner : node.getOwners()) {
            if (nodeOwner.equals(subscriber)) {
                return true;
            }
        }
        // Check that the subscriber is a local user
        XMPPServer server = XMPPServer.getInstance();
        if (server.isLocal(subscriber)) {
        	Collection<String> nodeGroups = node.getRosterGroupsAllowed();
        	for (Group group : server.getRosterManager().getSharedGroups(subscriber.getNode())) {
        		if (nodeGroups.contains(group.getName())) {
        			if (Log.isDebugEnabled()) {
        				Log.debug("Subscriber (" + subscriber + 
        						") is a member of node group " + group.getName());
        			}
        			return true;
        		}
        	}
        }
        else {
            // Subscriber is a remote user. This should never happen.
            Log.warn("Node with access model Roster has a remote user as subscriber: " +
                    node.getNodeID());
        }
        return false;
    }

    @Override
	public boolean canAccessItems(Node node, JID owner, JID subscriber) {
        return canSubscribe(node, owner, subscriber);
    }

    @Override
	public String getName() {
        return "roster";
    }

    @Override
	public PacketError.Condition getSubsriptionError() {
        return PacketError.Condition.not_authorized;
    }

    @Override
	public Element getSubsriptionErrorDetail() {
        return DocumentHelper.createElement(
                QName.get("not-in-roster-group", "http://jabber.org/protocol/pubsub#errors"));
    }

    @Override
	public boolean isAuthorizationRequired() {
        return false;
    }
}
