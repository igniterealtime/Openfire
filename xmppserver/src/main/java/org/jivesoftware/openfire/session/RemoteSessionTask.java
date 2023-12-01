/*
 * Copyright (C) 2007-2009 Jive Software, 2021-2023 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public abstract class RemoteSessionTask implements ClusterTask<Object> {
    private static final Logger Log = LoggerFactory.getLogger(RemoteSessionTask.class);

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
            result = getSession().getStreamID();
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
        else if (operation == Operation.getTLSProtocolName) {
            result = getSession().getTLSProtocolName();
        }
        else if (operation == Operation.getCipherSuiteName) {
            result = getSession().getCipherSuiteName();
        }
        else if (operation == Operation.getPeerCertificates) {
            result = getSession().getPeerCertificates();
        }
        else if (operation == Operation.getSoftwareVersion) {
            result = getSession().getSoftwareVersion();
        }
        else if (operation == Operation.close) {
            // Run in another thread so we avoid blocking calls (in hazelcast) 
            final Session session = getSession();
            if (session != null) {
                final Future<?> future = TaskEngine.getInstance().submit( () -> {
                    try {
                        if (session instanceof LocalSession) {
                            // OF-2311: If closed by another cluster node, chances are that the session needs to be closed forcibly.
                            // Chances of the session being resumed are neglectable, while retaining the session in a detached state
                            // causes problems (eg: IQBindHandler could have re-issued the resource to a replacement session).
                            ((LocalSession) session).getStreamManager().formalClose();
                        }
                        session.close();
                    } catch (Exception e) {
                        Log.info("An exception was logged while closing session: {}", session, e);
                    }
                });
                // Wait until the close operation is done or timeout is met
                try {
                    future.get(15, TimeUnit.SECONDS);
                }
                catch (Exception e) {
                    Log.info("An exception was logged while executing RemoteSessionTask to close session: {}", session, e);
                }
            }
        }
        else if (operation == Operation.isClosed) {
            result = getSession().isClosed();
        }
        else if (operation == Operation.isEncrypted) {
            result = getSession().isEncrypted();
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
        else if (operation == Operation.removeDetached) {
            final Session session = getSession();
            if (session instanceof LocalSession) {
                Log.debug("Terminating local session as instructed by another cluster node: {}", session);

                final Future<?> future = TaskEngine.getInstance().submit( () -> {
                    try {
                        SessionManager.getInstance().terminateDetached((LocalSession) session);
                    } catch (Exception e) {
                        Log.info("An exception was logged while closing session: {}", session, e);
                    }
                });
                // Wait until the close operation is done or timeout is met
                try {
                    future.get(15, TimeUnit.SECONDS);
                }
                catch (Exception e) {
                    Log.info("An exception was logged while executing RemoteSessionTask to close session: {}", session, e);
                }
            }
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
        getTLSProtocolName,
        getCipherSuiteName,
        getPeerCertificates,
        getSoftwareVersion,
        close,
        isClosed,
        @Deprecated isSecure, // Replaced with 'isEncrypted', replace in Openfire 4.9 or later.
        isEncrypted,
        getHostAddress,
        getHostName,
        validate,
        removeDetached,
        
        /**
         * Operations of c2s sessions
         */
        isInitialized,
        incrementConflictCount,
        hasRequestedBlocklist,
        
        /**
         * Operations of outgoing server sessions
         */
        getOutgoingDomainPairs,
        getAuthenticationMethod,

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
        getAddress,
        getValidatedDomains
    }
}
