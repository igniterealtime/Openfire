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

import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;

/**
 * Surrogate for outgoing server sessions hosted in some remote cluster node.
 *
 * @author Gaston Dombiak
 */
public class RemoteOutgoingServerSession extends RemoteSession implements OutgoingServerSession {

    private AuthenticationMethod authenticationMethod;
    private final DomainPair pair;

    public RemoteOutgoingServerSession(byte[] nodeID, DomainPair address) {
        super(nodeID, new JID(null, address.getRemote(), null, true));
        this.pair = address;
    }

    @Override
    public Collection<DomainPair> getOutgoingDomainPairs()
    {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getOutgoingDomainPairs);
        return (Collection<DomainPair>) doSynchronousClusterTask(task);
    }

    @Override
    public void addOutgoingDomainPair(@Nonnull final DomainPair domainPair)
    {
        doClusterTask(new AddOutgoingDomainPair(domainPair));
    }

    @Override
    public boolean authenticateSubdomain(@Nonnull final DomainPair domainPair) {
        ClusterTask task = new AuthenticateSubdomainTask(domainPair);
        return (Boolean) doSynchronousClusterTask(task);
    }

    @Override
    public AuthenticationMethod getAuthenticationMethod() {
        if (authenticationMethod == null) {
            ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getAuthenticationMethod);
            authenticationMethod = (AuthenticationMethod) doSynchronousClusterTask(task);
        }
        return authenticationMethod;
    }

    @Override
    public boolean checkOutgoingDomainPair(@Nonnull final DomainPair domainPair) {
        ClusterTask task = new CheckOutgoingDomainPairTask(domainPair);
        return (Boolean)doSynchronousClusterTask(task);
    }

    @Override
    RemoteSessionTask getRemoteSessionTask(RemoteSessionTask.Operation operation) {
        return new OutgoingServerSessionTask(pair, operation);
    }

    @Override
    ClusterTask getDeliverRawTextTask(String text) {
        return new DeliverRawTextServerTask(pair, text);
    }

    @Override
    ClusterTask getProcessPacketTask(Packet packet) {
        return new ProcessPacketTask(this, address, packet);
    }

    private static class DeliverRawTextServerTask extends OutgoingServerSessionTask {
        private String text;

        public DeliverRawTextServerTask() {
            super();
        }

        protected DeliverRawTextServerTask(DomainPair address, String text) {
            super(address, null);
            this.text = text;
        }

        @Override
        public void run() {
            getSession().deliverRawText(text);
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeSafeUTF(out, text);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            text = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }

    private static class AddOutgoingDomainPair extends OutgoingServerSessionTask {

        public AddOutgoingDomainPair() {
            super();
        }

        protected AddOutgoingDomainPair(DomainPair address) {
            super(address, null);
        }

        @Override
        public void run() {
            ((OutgoingServerSession) getSession()).addOutgoingDomainPair(domainPair);
        }
    }

    private static class AuthenticateSubdomainTask extends OutgoingServerSessionTask {

        public AuthenticateSubdomainTask() {
            super();
        }

        protected AuthenticateSubdomainTask(DomainPair address) {
            super(address, null);
        }

        @Override
        public void run() {
            result = ((OutgoingServerSession) getSession()).authenticateSubdomain(domainPair);
        }
    }

    private static class CheckOutgoingDomainPairTask extends OutgoingServerSessionTask {

        public CheckOutgoingDomainPairTask() {
            super();
        }

        protected CheckOutgoingDomainPairTask(DomainPair address) {
            super(address, null);
        }

        @Override
        public void run() {
            result = ((OutgoingServerSession) getSession()).checkOutgoingDomainPair(this.domainPair);
        }
    }
}
