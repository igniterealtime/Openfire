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

    public RemoteOutgoingServerSession(byte[] nodeID, JID address) {
        super(nodeID, address);
    }

    public Collection<String> getAuthenticatedDomains() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getAuthenticatedDomains);
        return (Collection<String>) doSynchronousClusterTask(task);
    }

    public void addAuthenticatedDomain(String domain) {
        doClusterTask(new AddAuthenticatedDomainTask(address, domain));
    }

    public Collection<String> getHostnames() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getHostnames);
        return (Collection<String>) doSynchronousClusterTask(task);
    }

    public void addHostname(String hostname) {
        doClusterTask(new AddHostnameTask(address, hostname));
    }

    public boolean authenticateSubdomain(String domain, String hostname) {
        ClusterTask task = new AuthenticateSubdomainTask(address, domain, hostname);
        return (Boolean) doSynchronousClusterTask(task);
    }

    public boolean isUsingServerDialback() {
        if (usingServerDialback == -1) {
            ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.isUsingServerDialback);
            usingServerDialback = (Boolean) doSynchronousClusterTask(task) ? 1 : 0;
        }
        return usingServerDialback == 1;
    }

    RemoteSessionTask getRemoteSessionTask(RemoteSessionTask.Operation operation) {
        return new OutgoingServerSessionTask(address, operation);
    }

    ClusterTask getDeliverRawTextTask(String text) {
        return new DeliverRawTextTask(this, address, text);
    }

    ClusterTask getProcessPacketTask(Packet packet) {
        return new ProcessPacketTask(this, address, packet);
    }

    private static class AddAuthenticatedDomainTask extends OutgoingServerSessionTask {
        private String domain;

        public AddAuthenticatedDomainTask() {
            super();
        }

        protected AddAuthenticatedDomainTask(JID address, String domain) {
            super(address, null);
            this.domain = domain;
        }

        public void run() {
            ((OutgoingServerSession) getSession()).addAuthenticatedDomain(domain);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeSafeUTF(out, domain);
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            domain = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }

    private static class AddHostnameTask extends OutgoingServerSessionTask {
        private String hostname;

        public AddHostnameTask() {
            super();
        }

        protected AddHostnameTask(JID address, String hostname) {
            super(address, null);
            this.hostname = hostname;
        }

        public void run() {
            ((OutgoingServerSession) getSession()).addHostname(hostname);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeSafeUTF(out, hostname);
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            hostname = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }

    private static class AuthenticateSubdomainTask extends OutgoingServerSessionTask {
        private String domain;
        private String hostname;

        public AuthenticateSubdomainTask() {
            super();
        }

        protected AuthenticateSubdomainTask(JID address, String domain, String hostname) {
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
}
