/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.util.cache.ExternalizableUtil;

import javax.annotation.Nonnull;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

/**
 * Incoming server session information to be used when running in a cluster. The session information is shared between
 * cluster nodes and is meant to be used by remote sessions to avoid invocation remote calls and instead use cached
 * information.
 *
 * Note that instances of this class typically contain a snapshot of some of the data of a {@link LocalIncomingServerSession}
 * instance. Updates to that parent instance are not automatically reflected in instances of this class.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class IncomingServerSessionInfo implements Externalizable
{
    private NodeID nodeID;
    private Set<String> validatedDomains;

    public IncomingServerSessionInfo() {
    }

    public NodeID getNodeID() {
        return nodeID;
    }

    public Set<String> getValidatedDomains() {
        return validatedDomains;
    }

    public IncomingServerSessionInfo(@Nonnull final LocalIncomingServerSession serverSession) {
        this.nodeID = XMPPServer.getInstance().getNodeID();
        validatedDomains = new HashSet<>(serverSession.getValidatedDomains());
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSerializable(out, nodeID);
        ExternalizableUtil.getInstance().writeSerializableCollection(out, validatedDomains);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        nodeID = (NodeID) ExternalizableUtil.getInstance().readSerializable(in);
        validatedDomains = new HashSet<>();
        ExternalizableUtil.getInstance().readSerializableCollection(in, validatedDomains, this.getClass().getClassLoader());
    }

    @Override
    public String toString() {
        return "IncomingServerSessionInfo{" +
            "nodeID=" + nodeID +
            ", validatedDomains=" + validatedDomains +
            '}';
    }
}
