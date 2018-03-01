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
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.xmpp.packet.JID;

/**
 * Publishers and owners may publish items to the node.
 *
 * @author Matt Tucker
 */
public class OnlyPublishers extends PublisherModel {

    @Override
    public boolean canPublish(Node node, JID entity) {
        NodeAffiliate nodeAffiliate = node.getAffiliate(entity);
        return nodeAffiliate != null && (
                nodeAffiliate.getAffiliation() == NodeAffiliate.Affiliation.publisher ||
                        nodeAffiliate.getAffiliation() == NodeAffiliate.Affiliation.owner);
    }

    @Override
    public String getName() {
        return "publishers";
    }
}
