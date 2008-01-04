/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.container;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that will be run in each cluster node to learn if the specified plugin
 * is installed or not.
 *
 * @author Gaston Dombiak
 */
public class IsPluginInstalledTask implements ClusterTask {
    private String pluginName;
    private boolean installed;

    /**
     * Do not use this constructor. It exists for deserialization purposes.
     */
    public IsPluginInstalledTask() {
    }

    public IsPluginInstalledTask(String pluginName) {
        this.pluginName = pluginName;
    }

    public Object getResult() {
        return installed;
    }

    public void run() {
        installed = XMPPServer.getInstance().getPluginManager().getPlugin(pluginName) != null;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, pluginName);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        pluginName = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
