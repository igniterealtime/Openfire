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

    @Override
    public boolean canSubscribe(Node node, JID owner, JID subscriber) {
        return true;
    }

    @Override
    public boolean canAccessItems(Node node, JID owner, JID subscriber) {
        return true;
    }

    @Override
    public String getName() {
        return "open";
    }

    @Override
    public PacketError.Condition getSubsriptionError() {
        // Return nothing since users can always subscribe to the node
        return null;
    }

    @Override
    public Element getSubsriptionErrorDetail() {
        // Return nothing since users can always subscribe to the node
        return null;
    }

    @Override
    public boolean isAuthorizationRequired() {
        return false;
    }
}
