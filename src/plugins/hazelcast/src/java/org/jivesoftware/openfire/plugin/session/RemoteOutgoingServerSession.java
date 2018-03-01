/*
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

package org.jivesoftware.openfire.plugin.session;

import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.OutgoingServerSession;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

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

    private long usingServerDialback = -1;
    private final DomainPair pair;

    public RemoteOutgoingServerSession(byte[] nodeID, DomainPair address) {
        super(nodeID, new JID(null, address.getRemote(), null, true));
        this.pair = address;
    }

    public Collection<DomainPair> getOutgoingDomainPairs()
    {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getOutgoingDomainPairs);
        return (Collection<DomainPair>) doSynchronousClusterTask(task);
    }

    public void addOutgoingDomainPair( String local, String remote )
    {
        doClusterTask(new AddOutgoingDomainPair(pair, local, remote ));
    }

    public boolean authenticateSubdomain(String domain, String hostname) {
        ClusterTask task = new AuthenticateSubdomainTask(pair, domain, hostname);
        return (Boolean) doSynchronousClusterTask(task);
    }

    public boolean isUsingServerDialback() {
        if (usingServerDialback == -1) {
            ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.isUsingServerDialback);
            usingServerDialback = (Boolean) doSynchronousClusterTask(task) ? 1 : 0;
        }
        return usingServerDialback == 1;
    }

    public boolean checkOutgoingDomainPair(String localDomain, String remoteDomain) {
        ClusterTask task = new CheckOutgoingDomainPairTask(pair, localDomain, remoteDomain);
        return (Boolean)doSynchronousClusterTask(task);
    }

    RemoteSessionTask getRemoteSessionTask(RemoteSessionTask.Operation operation) {
        return new OutgoingServerSessionTask(pair, operation);
    }

    ClusterTask getDeliverRawTextTask(String text) {
        return new DeliverRawTextServerTask(pair, text);
    }

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

        public void run() {
            getSession().deliverRawText(text);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeSafeUTF(out, text);
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            text = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }

    private static class AddOutgoingDomainPair extends OutgoingServerSessionTask {
        private String local;
        private String remote;

        public AddOutgoingDomainPair() {
            super();
        }

        protected AddOutgoingDomainPair(DomainPair address, String local, String remote) {
            super(address, null);
            this.local = local;
            this.remote = remote;
        }

        public void run() {
            ((OutgoingServerSession) getSession()).addOutgoingDomainPair(local, remote);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeSafeUTF(out, local);
            ExternalizableUtil.getInstance().writeSafeUTF(out, remote);
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            local = ExternalizableUtil.getInstance().readSafeUTF(in);
            remote = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }

    private static class AuthenticateSubdomainTask extends OutgoingServerSessionTask {
        private String domain;
        private String hostname;

        public AuthenticateSubdomainTask() {
            super();
        }

        protected AuthenticateSubdomainTask(DomainPair address, String domain, String hostname) {
            super(address, null);
            this.domain = domain;
            this.hostname = hostname;
        }

        public void run() {
            result = ((OutgoingServerSession) getSession()).authenticateSubdomain(domain, hostname);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeSafeUTF(out, domain);
            ExternalizableUtil.getInstance().writeSafeUTF(out, hostname);
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            domain = ExternalizableUtil.getInstance().readSafeUTF(in);
            hostname = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }

    private static class CheckOutgoingDomainPairTask extends OutgoingServerSessionTask {
        private String local;
        private String remote;

        public CheckOutgoingDomainPairTask() {
            super();
        }

        protected CheckOutgoingDomainPairTask(DomainPair address, String local, String remote) {
            super(address, null);
            this.local = local;
            this.remote = remote;
        }

        public void run() {
            result = ((OutgoingServerSession) getSession()).checkOutgoingDomainPair(local, remote);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeSafeUTF(out, local);
            ExternalizableUtil.getInstance().writeSafeUTF(out, remote);
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            local = ExternalizableUtil.getInstance().readSafeUTF(in);
            remote = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }
}
