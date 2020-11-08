/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.handler;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.PrivateStorage;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.user.UserManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Implements the TYPE_IQ jabber:iq:private protocol. Clients
 * use this protocol to store and retrieve arbitrary application
 * configuration information. Using the server for setting storage
 * allows client configurations to follow users where ever they go.
 * <p>
 * A 'get' query retrieves any stored data.
 * A 'set' query stores new data.
 * </p>
 * <p>
 * Currently an empty implementation to allow usage with normal
 * clients. Future implementation needed.
 * </p>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 *
 * @author Iain Shigeoka
 */
public class IQPrivateHandler extends IQHandler implements ServerFeaturesProvider {

    public static final String NAMESPACE = "jabber:iq:private";

    private IQHandlerInfo info;
    private PrivateStorage privateStorage = null;

    public IQPrivateHandler() {
        super("XMPP Private Storage Handler");
        info = new IQHandlerInfo("query", NAMESPACE);
    }

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException, PacketException {
        IQ replyPacket = IQ.createResultIQ(packet);

        Element child = packet.getChildElement();
        Element dataElement = child.elementIterator().next();

        if ( !UserManager.getInstance().isRegisteredUser( packet.getFrom(), false ) ) {
            replyPacket.setChildElement(packet.getChildElement().createCopy());
            replyPacket.setError(PacketError.Condition.service_unavailable);
            replyPacket.getError().setText( "Service available only to locally registered users." );
            return replyPacket;
        }

        if (dataElement != null) {
            if (IQ.Type.get.equals(packet.getType())) {
                Element dataStored = privateStorage.get(packet.getFrom().getNode(), dataElement);
                dataStored.setParent(null);

                child.remove(dataElement);
                child.setParent(null);
                replyPacket.setChildElement(child);
                child.add(dataStored);
            }
            else {
                if (privateStorage.isEnabled()) {
                    privateStorage.add(packet.getFrom().getNode(), dataElement);
                } else {
                    replyPacket.setChildElement(packet.getChildElement().createCopy());
                    replyPacket.setError(PacketError.Condition.service_unavailable);
                }
            }
        }
        else {
            replyPacket.setChildElement("query", "jabber:iq:private");
        }
        return replyPacket;
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        privateStorage = server.getPrivateStorage();
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Arrays.asList(
            "jabber:iq:private",
            "urn:xmpp:bookmarks-conversion:0"
        ).iterator();
    }
}
