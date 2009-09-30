/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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
public class PropertyClusterEventTask implements ClusterTask {
    private Type event;
    private String key;
    private String value;

    public static PropertyClusterEventTask createPutTask(String key, String value) {
        PropertyClusterEventTask task = new PropertyClusterEventTask();
        task.event = Type.put;
        task.key = key;
        task.value = value;
        return task;
    }

    public static PropertyClusterEventTask createDeteleTask(String key) {
        PropertyClusterEventTask task = new PropertyClusterEventTask();
        task.event = Type.deleted;
        task.key = key;
        return task;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        if (Type.put == event) {
            JiveProperties.getInstance().localPut(key, value);
        }
        else if (Type.deleted == event) {
            JiveProperties.getInstance().localRemove(key);
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeInt(out, event.ordinal());
        ExternalizableUtil.getInstance().writeSafeUTF(out, key);
        ExternalizableUtil.getInstance().writeBoolean(out, value != null);
        if (value != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, value);
        }
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        event = Type.values()[ExternalizableUtil.getInstance().readInt(in)];
        key = ExternalizableUtil.getInstance().readSafeUTF(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            value = ExternalizableUtil.getInstance().readSafeUTF(in);
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
