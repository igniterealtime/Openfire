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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that returns the specified conversation or <tt>null</tt> if not found.
 *
 * @author Gaston Dombiak
 */
public class GetConversationTask implements ClusterTask {
    private long conversationID;
    private Conversation conversation;

    public GetConversationTask() {
    }

    public GetConversationTask(long conversationID) {
        this.conversationID = conversationID;
    }

    public Object getResult() {
        return conversation;
    }

    public void run() {
        MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin(
            "monitoring");
        ConversationManager conversationManager = (ConversationManager)plugin.getModule(ConversationManager.class);
        try {
            conversation = conversationManager.getConversation(conversationID);
        } catch (NotFoundException e) {
            // Ignore. The requester of this task will throw this exception in his JVM
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeLong(out, conversationID);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        conversationID = ExternalizableUtil.getInstance().readLong(in);
    }
}
