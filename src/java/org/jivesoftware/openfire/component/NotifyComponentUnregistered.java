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

package org.jivesoftware.openfire.component;

import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that will be executed on other cluster nodes to trigger the event that a component was
 * removed from a cluster node.
 *
 * @author Gaston Dombiak
 */
public class NotifyComponentUnregistered implements ClusterTask<Void> {
    private JID componentJID;

    public NotifyComponentUnregistered() {
    }

    public NotifyComponentUnregistered(JID componentJID) {
        this.componentJID = componentJID;
    }

    @Override
    public Void getResult() {
        return null;
    }

    @Override
    public void run() {
        final InternalComponentManager manager = InternalComponentManager.getInstance();
        manager.removeComponentInfo(componentJID);
        manager.notifyComponentUnregistered(componentJID);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSerializable(out, componentJID);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        componentJID = (JID) ExternalizableUtil.getInstance().readSerializable(in);
    }
}
