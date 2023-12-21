/*
 * Copyright (C) 2007-2009 Jive Software, 2021 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Cluster task that will ask a remote cluster node to deliver some raw text to a local session.
 *
 * @author Gaston Dombiak
 */
public class DeliverRawTextTask implements ClusterTask<Void> {
    private static final Logger Log = LoggerFactory.getLogger(DeliverRawTextTask.class);

    private SessionType sessionType;
    private JID address;
    private StreamID streamID;
    private String text;

    public DeliverRawTextTask() {
        super();
    }

    DeliverRawTextTask(RemoteSession remoteSession, JID address, String text) {
        if (remoteSession instanceof RemoteClientSession) {
            this.sessionType = SessionType.client;
        }
        else if (remoteSession instanceof RemoteOutgoingServerSession) {
            Log.error("OutgoingServerSession used with DeliverRawTextTask; should be using DeliverRawTextServerTask: " + remoteSession);
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

    DeliverRawTextTask(StreamID streamID, String text) {
        this.sessionType = SessionType.incomingServer;
        this.streamID = streamID;
        this.text = text;
    }

    public Void getResult() {
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
            ExternalizableUtil.getInstance().writeSerializable(out, address);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, streamID != null);
        if (streamID != null) {
            ExternalizableUtil.getInstance().writeSafeUTF( out, streamID.getID() );
        }
    }

    public void readExternal(ObjectInput in) throws IOException {
        text = ExternalizableUtil.getInstance().readSafeUTF(in);
        sessionType = SessionType.values()[ExternalizableUtil.getInstance().readInt(in)];
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            address = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            streamID = BasicStreamIDFactory.createStreamID( ExternalizableUtil.getInstance().readSafeUTF(in) );
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
            Log.error("Trying to write raw data to a server session across the cluster: " + address.toString());
            return null;
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
