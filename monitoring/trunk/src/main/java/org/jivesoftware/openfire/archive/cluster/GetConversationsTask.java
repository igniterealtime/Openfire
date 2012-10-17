/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.archive.cluster;

import org.jivesoftware.openfire.archive.Conversation;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.cache.ClusterTask;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;

/**
 * Task that will return current conversations taking place in the senior cluster member.
 * All conversations in the cluster are kept in the senior cluster member.
 *
 * @author Gaston Dombiak
 */
public class GetConversationsTask implements ClusterTask {
    private Collection<Conversation> conversations;

    public Object getResult() {
        return conversations;
    }

    public void run() {
        MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin(
        		MonitoringConstants.NAME);
        ConversationManager conversationManager = (ConversationManager)plugin.getModule(ConversationManager.class);
        conversations = conversationManager.getConversations();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        // Do nothing
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Do nothing
    }
}
