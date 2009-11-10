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

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Cluster task that will ask a remote cluster node to deliver some raw text to a local session.
 *
 * @author Gaston Dombiak
 */
public class DeliverRawTextTask implements ClusterTask {
    private SessionType sessionType;
    private JID address;
    private String streamID;
    private String text;

    public DeliverRawTextTask() {
        super();
    }

    protected DeliverRawTextTask(RemoteSession remoteSession, JID address, String text) {
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
        this.text = text;
    }

    public DeliverRawTextTask(String streamID, String text) {
        this.sessionType = SessionType.incomingServer;
        this.streamID = streamID;
        this.text = text;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        getSession().deliverRawText(text);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, text);
        ExternalizableUtil.getInstance().writeInt(out, sessionType.ordinal());
        ExternalizableUtil.getInstance().writeBoolean(out, address != null);
        if (address != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, address.toString());
        }
        ExternalizableUtil.getInstance().writeBoolean(out, streamID != null);
        if (streamID != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, streamID);
        }
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        text = ExternalizableUtil.getInstance().readSafeUTF(in);
        sessionType = SessionType.values()[ExternalizableUtil.getInstance().readInt(in)];
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            address = new JID(ExternalizableUtil.getInstance().readSafeUTF(in));
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            streamID = ExternalizableUtil.getInstance().readSafeUTF(in);
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