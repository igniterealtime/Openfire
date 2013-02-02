/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.util.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Task to be executed by remote nodes to deliver the requested packet to the requested
 * receiver.
 *
 * @author Gaston Dombiak
 */
public class RemotePacketExecution implements ClusterTask {

    private JID recipient;
    private Packet packet;

    public RemotePacketExecution() {
    }

    public RemotePacketExecution(JID recipient, Packet packet) {
        this.recipient = recipient;
        this.packet = packet;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        // Route packet to entity hosted by this node. If delivery fails then the routing table
        // will inform the proper router of the failure and the router will handle the error reply logic
        XMPPServer.getInstance().getRoutingTable().routePacket(recipient, packet, false);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
    	ExternalizableUtil.getInstance().writeSerializable(out, recipient);
        if (packet instanceof IQ) {
            ExternalizableUtil.getInstance().writeInt(out, 1);
        }
        else if (packet instanceof Message) {
            ExternalizableUtil.getInstance().writeInt(out, 2);
        }
        else if (packet instanceof Presence) {
            ExternalizableUtil.getInstance().writeInt(out, 3);
        }
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) packet.getElement());
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        recipient = (JID) ExternalizableUtil.getInstance().readSerializable(in);

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

    public String toString() {
        return super.toString() + " recipient: " + recipient + "packet: " + packet;
    }
}