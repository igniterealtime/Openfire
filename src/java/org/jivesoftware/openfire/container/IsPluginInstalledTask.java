/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
public class IsPluginInstalledTask implements ClusterTask<Boolean> {
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

    @Override
    public Boolean getResult() {
        return installed;
    }

    @Override
    public void run() {
        installed = XMPPServer.getInstance().getPluginManager().getPlugin(pluginName) != null;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, pluginName);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        pluginName = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
