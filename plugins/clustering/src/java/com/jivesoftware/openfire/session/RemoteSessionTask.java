/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.openfire.session;

import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.UnknownHostException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Operations to be executed in a remote session hosted in a remote cluster node.
 *
 * @author Gaston Dombiak
 */
public abstract class RemoteSessionTask implements ClusterTask {
    protected Object result;
    protected Operation operation;

    public RemoteSessionTask() {
    }

    protected RemoteSessionTask(Operation operation) {
        this.operation = operation;
    }

    abstract Session getSession();

    public Object getResult() {
        return result;
    }

    public void run() {
        if (operation == Operation.getStreamID) {
            result = getSession().getStreamID().getID();
        }
        else if (operation == Operation.getServerName) {
            result = getSession().getServerName();
        }
        else if (operation == Operation.getCreationDate) {
            result = getSession().getCreationDate();
        }
        else if (operation == Operation.getLastActiveDate) {
            result = getSession().getLastActiveDate();
        }
        else if (operation == Operation.getNumClientPackets) {
            result = getSession().getNumClientPackets();
        }
        else if (operation == Operation.getNumServerPackets) {
            result = getSession().getNumServerPackets();
        }
        else if (operation == Operation.close) {
            // Run in another thread so we avoid blocking calls (in coherence) 
            final Session session = getSession();
            if (session != null) {
                final Future<?> future = TaskEngine.getInstance().submit(new Runnable() {
                    public void run() {
                        session.close();
                    }
                });
                // Wait until the close operation is done or timeout is met
                try {
                    future.get(15, TimeUnit.SECONDS);
                }
                catch (Exception e) {
                    // Ignore
                }
            }
        }
        else if (operation == Operation.isClosed) {
            result = getSession().isClosed();
        }
        else if (operation == Operation.isSecure) {
            result = getSession().isSecure();
        }
        else if (operation == Operation.getHostAddress) {
            try {
                result = getSession().getHostAddress();
            } catch (UnknownHostException e) {
                Log.error("Error getting address of session: " + getSession(), e);
            }
        }
        else if (operation == Operation.getHostName) {
            try {
                result = getSession().getHostName();
            } catch (UnknownHostException e) {
                Log.error("Error getting address of session: " + getSession(), e);
            }
        }
        else if (operation == Operation.validate) {
            result = getSession().validate();
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeBoolean(out, operation != null);
        if (operation != null) {
            ExternalizableUtil.getInstance().writeInt(out, operation.ordinal());
        }
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            operation = Operation.values()[ExternalizableUtil.getInstance().readInt(in)];
        }
    }

    public enum Operation {
        /**
         * Basic session operations
         */
        getStreamID,
        getServerName,
        getCreationDate,
        getLastActiveDate,
        getNumClientPackets,
        getNumServerPackets,
        close,
        isClosed,
        isSecure,
        getHostAddress,
        getHostName,
        validate,
        
        /**
         * Operations of c2s sessions
         */
        isInitialized,
        incrementConflictCount,
        
        /**
         * Operations of outgoing server sessions
         */
        getAuthenticatedDomains,
        getHostnames,
        isUsingServerDialback,

        /**
         * Operations of external component sessions
         */
        getType,
        getCategory,
        getInitialSubdomain,
        getSubdomains,
        getName,
        getDescription,
        start,
        shutdown,

        /**
         * Operations of incoming server sessions
         */
        getLocalDomain,
        getAddress
    }
}
