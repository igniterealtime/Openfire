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
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.*;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Cluster task that will ask a remote cluster node to deliver some packet to a local session.
 *
 * @author Gaston Dombiak
 */
public class ProcessPacketTask implements ClusterTask {
    private SessionType sessionType;
    private JID address;
    private String streamID;
    private Packet packet;

    public ProcessPacketTask() {
        super();
    }

    protected ProcessPacketTask(RemoteSession remoteSession, JID address, Packet packet) {
        if (remoteSession instanceof RemoteClientSession) {
            this.sessionType = SessionType.client;
        }
        else if (remoteSession instanceof RemoteOutgoingServerSession) {
            this.sessionType = SessionType.outgoingServer;
        }
        else if (remoteSession instanceof RemoteComponentSession) {
            this.sessionType = SessionType.component;
        }
        else if (remoteSession instanceof RemoteConnectionMultiplexerSession) {
            this.sessionType = SessionType.connectionManager;
        }
        else {
            Log.error("Invalid RemoteSession was used for task: " + remoteSession);
        }
        this.address = address;
        this.packet = packet;
    }

    protected ProcessPacketTask(String streamID, Packet packet) {
        this.sessionType = SessionType.incomingServer;
        this.streamID = streamID;
        this.packet = packet;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        getSession().process(packet);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeBoolean(out, address != null);
        if (address != null) {
            ExternalizableUtil.getInstance().writeSerializable(out, address);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, streamID != null);
        if (streamID != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, streamID);
        }
        ExternalizableUtil.getInstance().writeInt(out, sessionType.ordinal());
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
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            address = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            streamID = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        sessionType = SessionType.values()[ExternalizableUtil.getInstance().readInt(in)];
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

    Session getSession() {
        if (sessionType == SessionType.client) {
            return XMPPServer.getInstance().getRoutingTable().getClientRoute(address);
        }
        else if (sessionType == SessionType.component) {
            return SessionManager.getInstance().getComponentSession(address.getDomain());
        }
        else if (sessionType == SessionType.connectionManager) {
            return SessionManager.getInstance().getConnectionMultiplexerSession(address);
        }
        else if (sessionType == SessionType.outgoingServer) {
            return SessionManager.getInstance().getOutgoingServerSession(address.getDomain());
        }
        else if (sessionType == SessionType.incomingServer) {
            return SessionManager.getInstance().getIncomingServerSession(streamID);
        }
        Log.error("Found unknown session type: " + sessionType);
        return null;
    }

    public String toString() {
        return super.toString() + " sessionType: " + sessionType + " address: " + address;
    }

    private enum SessionType {
        client,
        outgoingServer,
        incomingServer,
        component,
        connectionManager
    }
}
