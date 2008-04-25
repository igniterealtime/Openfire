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

import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.cache.ClusterTask;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that will return the number of current conversations taking place in the senior cluster member.
 * All conversations in the cluster are kept in the senior cluster member.
 *
 * @author Gaston Dombiak
 */
public class GetConversationCountTask implements ClusterTask {
    private int conversationCount;

    public Object getResult() {
        return conversationCount;
    }

    public void run() {
        MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin(
            "monitoring");
        ConversationManager conversationManager = (ConversationManager)plugin.getModule(ConversationManager.class);
        conversationCount = conversationManager.getConversationCount();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        // Do nothing
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Do nothing
    }
}
