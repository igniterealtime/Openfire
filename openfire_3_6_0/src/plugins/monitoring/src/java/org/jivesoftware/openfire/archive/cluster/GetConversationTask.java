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
