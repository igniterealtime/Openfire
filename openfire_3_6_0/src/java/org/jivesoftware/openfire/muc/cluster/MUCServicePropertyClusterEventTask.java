/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
public class MUCServicePropertyClusterEventTask implements ClusterTask {
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

    public Object getResult() {
        return null;
    }

    public void run() {
        if (Type.put == event) {
            MUCPersistenceManager.setLocalProperty(service, key, value);
        }
        else if (Type.deleted == event) {
            MUCPersistenceManager.deleteLocalProperty(service, key);
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeInt(out, event.ordinal());
        ExternalizableUtil.getInstance().writeSafeUTF(out, service);
        ExternalizableUtil.getInstance().writeSafeUTF(out, key);
        ExternalizableUtil.getInstance().writeBoolean(out, value != null);
        if (value != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, value);
        }
    }

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
