/*
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

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.jivesoftware.openfire.muc.spi.MUCPersistenceManager;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This task updates or deletes a property in a cluster node's muc service property map.
 * {@link org.jivesoftware.openfire.muc.spi.MUCServicePropertyEventListener} of each cluster node will be alerted of the event.
 *
 * @author Daniel Henninger
 */
public class MUCServicePropertyClusterEventTask implements ClusterTask<Void> {
    private Type event;
    private String service;
    private String key;
    private String value;

    public static MUCServicePropertyClusterEventTask createPutTask(String service, String key, String value) {
        MUCServicePropertyClusterEventTask task = new MUCServicePropertyClusterEventTask();
        task.event = Type.put;
        task.service = service;
        task.key = key;
        task.value = value;
        return task;
    }

    public static MUCServicePropertyClusterEventTask createDeleteTask(String service, String key) {
        MUCServicePropertyClusterEventTask task = new MUCServicePropertyClusterEventTask();
        task.event = Type.deleted;
        task.service = service;
        task.key = key;
        return task;
    }

    @Override
    public Void getResult() {
        return null;
    }

    @Override
    public void run() {
        if (Type.put == event) {
            MUCPersistenceManager.setLocalProperty(service, key, value);
        }
        else if (Type.deleted == event) {
            MUCPersistenceManager.deleteLocalProperty(service, key);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeInt(out, event.ordinal());
        ExternalizableUtil.getInstance().writeSafeUTF(out, service);
        ExternalizableUtil.getInstance().writeSafeUTF(out, key);
        ExternalizableUtil.getInstance().writeBoolean(out, value != null);
        if (value != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, value);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        event = Type.values()[ExternalizableUtil.getInstance().readInt(in)];
        service = ExternalizableUtil.getInstance().readSafeUTF(in);
        key = ExternalizableUtil.getInstance().readSafeUTF(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            value = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }

    private static enum Type {
        /**
         * Event triggered when a muc service property was added or updated in the system.
         */
        put,
        /**
         * Event triggered when a muc service property was deleted from the system.
         */
        deleted
    }
}
