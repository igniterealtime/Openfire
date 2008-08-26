/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.archive.cluster;

import org.jivesoftware.openfire.archive.ConversationEvent;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * Task that sends cnoversation events to the senior cluster member.
 *
 * @author Gaston Dombiak
 */
public class SendConversationEventsTask implements ClusterTask {
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
            "monitoring");
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
