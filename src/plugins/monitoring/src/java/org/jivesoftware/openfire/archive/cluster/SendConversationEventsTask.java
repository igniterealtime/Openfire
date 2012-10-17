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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.ConversationEvent;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task that sends cnoversation events to the senior cluster member.
 *
 * @author Gaston Dombiak
 */
public class SendConversationEventsTask implements ClusterTask {
	
	private static final Logger Log = LoggerFactory.getLogger(SendConversationEventsTask.class);
			
    private List<ConversationEvent> events;

    /**
     * Do not use this constructor. It only exists for serialization purposes.
     */
    public SendConversationEventsTask() {
    }

    public SendConversationEventsTask(List<ConversationEvent> events) {
        this.events = events;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin(
        		MonitoringConstants.NAME);
        ConversationManager conversationManager = (ConversationManager)plugin.getModule(ConversationManager.class);
        for (ConversationEvent event : events) {
            try {
                event.run(conversationManager);
            } catch (Exception e) {
                Log.error("Error while processing chat archiving event", e);
            }
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeExternalizableCollection(out, events);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        events = new ArrayList<ConversationEvent>();
        ExternalizableUtil.getInstance().readExternalizableCollection(in, events, getClass().getClassLoader());
    }
}
