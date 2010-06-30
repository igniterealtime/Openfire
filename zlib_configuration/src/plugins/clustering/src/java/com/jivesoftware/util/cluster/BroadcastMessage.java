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

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.Message;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that will broadcast a message to local connected client sessions.
 *
 * @author Gaston Dombiak
 */
public class BroadcastMessage implements ClusterTask {

    private Message packet;

    public BroadcastMessage() {
    }


    public BroadcastMessage(Message packet) {
        this.packet = packet;
    }

    public Object getResult() {
        // Not used since we are using #execute and not #query when using InvocationService
        return null;
    }

    public void run() {
        // Broadcast message to client sessions connected to this node
        XMPPServer.getInstance().getRoutingTable().broadcastPacket(packet, true);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) packet.getElement());
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        packet = new Message(packetElement, true);
    }
}
