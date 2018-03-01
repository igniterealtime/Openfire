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

package org.jivesoftware.util;

import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This task updates or deletes a property in a cluster node's property map.
 * {@link PropertyEventListener} of each cluster node will be alerted of the event.
 *
 * @author Gaston Dombiak
 */
public class PropertyClusterEventTask implements ClusterTask<Void> {
    private Type event;
    private String key;
    private String value;
    private boolean isEncrypted;

    public static PropertyClusterEventTask createPutTask(String key, String value, boolean isEncrypted) {
        PropertyClusterEventTask task = new PropertyClusterEventTask();
        task.event = Type.put;
        task.key = key;
        task.value = value;
        task.isEncrypted = isEncrypted;
        return task;
    }

    public static PropertyClusterEventTask createDeleteTask(String key) {
        PropertyClusterEventTask task = new PropertyClusterEventTask();
        task.event = Type.deleted;
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
            JiveProperties.getInstance().localPut(key, value, isEncrypted);
        }
        else if (Type.deleted == event) {
            JiveProperties.getInstance().localRemove(key);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeInt(out, event.ordinal());
        ExternalizableUtil.getInstance().writeSafeUTF(out, key);
        ExternalizableUtil.getInstance().writeBoolean(out, value != null);
        if (value != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, value);
            ExternalizableUtil.getInstance().writeBoolean(out, isEncrypted);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        event = Type.values()[ExternalizableUtil.getInstance().readInt(in)];
        key = ExternalizableUtil.getInstance().readSafeUTF(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            value = ExternalizableUtil.getInstance().readSafeUTF(in);
            isEncrypted = ExternalizableUtil.getInstance().readBoolean(in);
        }
    }

    private static enum Type {
        /**
         * Event triggered when a system property was added or updated in the system.
         */
        put,
        /**
         * Event triggered when a system property was deleted from the system.
         */
        deleted
    }
}
