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

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.session.ComponentSession;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.*;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;

/**
 * Surrogate for sessions of external components hosted in some remote cluster node.
 *
 * @author Gaston Dombiak
 */
public class RemoteComponentSession extends RemoteSession implements ComponentSession {

    private ExternalComponent component;

    public RemoteComponentSession(byte[] nodeID, JID address) {
        super(nodeID, address);
        component = new RemoteExternalComponent(address);
    }

    public ExternalComponent getExternalComponent() {
        return component;
    }

    RemoteSessionTask getRemoteSessionTask(RemoteSessionTask.Operation operation) {
        return new ComponentSessionTask(address, operation);
    }

    ClusterTask getDeliverRawTextTask(String text) {
        return new DeliverRawTextTask(this, address, text);
    }

    ClusterTask getProcessPacketTask(Packet packet) {
        return new ProcessPacketTask(this, address, packet);
    }

    private class RemoteExternalComponent implements ExternalComponent {
        private JID address;

        public RemoteExternalComponent(JID address) {
            this.address = address;
        }

        public void setName(String name) {
            RemoteSessionTask task = new SetterTask(address, SetterTask.Type.name, name);
            doClusterTask(task);
        }

        public String getType() {
            ClusterTask task = new ComponentSessionTask(address, RemoteSessionTask.Operation.getType);
            return (String) doSynchronousClusterTask(task);
        }

        public void setType(String type) {
            RemoteSessionTask task = new SetterTask(address, SetterTask.Type.type, type);
            doClusterTask(task);
        }

        public String getCategory() {
            ClusterTask task = new ComponentSessionTask(address, RemoteSessionTask.Operation.getCategory);
            return (String) doSynchronousClusterTask(task);
        }

        public void setCategory(String category) {
            RemoteSessionTask task = new SetterTask(address, SetterTask.Type.catergory, category);
            doClusterTask(task);
        }

        public String getInitialSubdomain() {
            ClusterTask task =
                    new ComponentSessionTask(address, RemoteSessionTask.Operation.getInitialSubdomain);
            return (String) doSynchronousClusterTask(task);
        }

        public Collection<String> getSubdomains() {
            ClusterTask task = new ComponentSessionTask(address, RemoteSessionTask.Operation.getSubdomains);
            return (Collection<String>) doSynchronousClusterTask(task);
        }

        public String getName() {
            ClusterTask task = new ComponentSessionTask(address, RemoteSessionTask.Operation.getName);
            return (String) doSynchronousClusterTask(task);
        }

        public String getDescription() {
            ClusterTask task = new ComponentSessionTask(address, RemoteSessionTask.Operation.getDescription);
            return (String) doSynchronousClusterTask(task);
        }

        public void processPacket(Packet packet) {
            RemoteSessionTask task = new ProcessComponentPacketTask(address, packet);
            doClusterTask(task);
        }

        public void initialize(JID jid, ComponentManager componentManager) throws ComponentException {
            RemoteSessionTask task = new InitializeTask(address, jid);
            doClusterTask(task);
        }

        public void start() {
            RemoteSessionTask task = new ComponentSessionTask(address, RemoteSessionTask.Operation.start);
            doClusterTask(task);
        }

        public void shutdown() {
            RemoteSessionTask task = new ComponentSessionTask(address, RemoteSessionTask.Operation.shutdown);
            doClusterTask(task);
        }
    }

    private static class SetterTask extends ComponentSessionTask {
        private Type type;
        private String value;

        public SetterTask() {
            super();
        }

        protected SetterTask(JID address, Type type, String value) {
            super(address, null);
            this.type = type;
            this.value = value;
        }

        public void run() {
            if (type == Type.name) {
                ((ComponentSession) getSession()).getExternalComponent().setName(value);
            } else if (type == Type.type) {
                ((ComponentSession) getSession()).getExternalComponent().setType(value);
            } else if (type == Type.catergory) {
                ((ComponentSession) getSession()).getExternalComponent().setCategory(value);
            }
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeInt(out, type.ordinal());
            ExternalizableUtil.getInstance().writeBoolean(out, value != null);
            if (value != null) {
                ExternalizableUtil.getInstance().writeSafeUTF(out, value);
            }
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            type = Type.values()[ExternalizableUtil.getInstance().readInt(in)];
            if (ExternalizableUtil.getInstance().readBoolean(in)) {
                value = ExternalizableUtil.getInstance().readSafeUTF(in);
            }
        }

        private static enum Type {
            name,
            type,
            catergory
        }
    }

    private static class ProcessComponentPacketTask extends ComponentSessionTask {
        private Packet packet;

        public ProcessComponentPacketTask() {
            super();
        }

        protected ProcessComponentPacketTask(JID address, Packet packet) {
            super(address, null);
            this.packet = packet;
        }

        public void run() {
            ((ComponentSession) getSession()).getExternalComponent().processPacket(packet);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            if (packet instanceof IQ) {
                ExternalizableUtil.getInstance().writeInt(out, 1);
            } else if (packet instanceof Message) {
                ExternalizableUtil.getInstance().writeInt(out, 2);
            } else if (packet instanceof Presence) {
                ExternalizableUtil.getInstance().writeInt(out, 3);
            }
            ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) packet.getElement());
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            int packetType = ExternalizableUtil.getInstance().readInt(in);
            Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
            switch (packetType) {
                case 1:
                    packet = new IQ(packetElement, true);
                    break;
                case 2:
                    packet = new Message(packetElement, true);
                    break;
                case 3:
                    packet = new Presence(packetElement, true);
                    break;
            }
        }
    }

    private static class InitializeTask extends ComponentSessionTask {
        private JID componentJID;

        public InitializeTask() {
            super();
        }

        protected InitializeTask(JID address, JID componentJID) {
            super(address, null);
            this.componentJID = componentJID;
        }

        public void run() {
            try {
                ((ComponentSession) getSession()).getExternalComponent()
                        .initialize(componentJID, InternalComponentManager.getInstance());
            } catch (ComponentException e) {
                Log.error("Error initializing component", e);
            }
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeSafeUTF(out, componentJID.toString());
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            componentJID = new JID(ExternalizableUtil.getInstance().readSafeUTF(in));
        }
    }
}
