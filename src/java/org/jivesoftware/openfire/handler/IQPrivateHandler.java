/**
 * $RCSfile$
 * $Revision: 824 $
 * $Date: 2005-01-08 00:16:59 -0300 (Sat, 08 Jan 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.handler;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.PrivateStorage;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.xmpp.packet.IQ;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implements the TYPE_IQ jabber:iq:private protocol. Clients
 * use this protocol to store and retrieve arbitrary application
 * configuration information. Using the server for setting storage
 * allows client configurations to follow users where ever they go.
 * <p/>
 * A 'get' query retrieves any stored data.
 * A 'set' query stores new data.
 * <p/>
 * Currently an empty implementation to allow usage with normal
 * clients. Future implementation needed.
 * <p/>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <p/>
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 *
 * @author Iain Shigeoka
 */
public class IQPrivateHandler extends IQHandler implements ServerFeaturesProvider {

    private IQHandlerInfo info;
    private PrivateStorage privateStorage = null;

    public IQPrivateHandler() {
        super("XMPP Private Storage Handler");
        info = new IQHandlerInfo("query", "jabber:iq:private");
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException, PacketException {
        IQ replyPacket;
        Element child = packet.getChildElement();
        Element dataElement = (Element) child.elementIterator().next();

        if (dataElement != null) {
            if (IQ.Type.get.equals(packet.getType())) {
                replyPacket = IQ.createResultIQ(packet);
                Element dataStored = privateStorage.get(packet.getFrom().getNode(), dataElement);
                dataStored.setParent(null);

                child.remove(dataElement);
                child.setParent(null);
                replyPacket.setChildElement(child);
                child.add(dataStored);
            }
            else {
                privateStorage.add(packet.getFrom().getNode(), dataElement);
                replyPacket = IQ.createResultIQ(packet);
            }
        }
        else {
            replyPacket = IQ.createResultIQ(packet);
            replyPacket.setChildElement("query", "jabber:iq:private");
        }
        return replyPacket;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        privateStorage = server.getPrivateStorage();
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator<String> getFeatures() {
        List<String> features = new ArrayList<String>();
        features.add("jabber:iq:private");
        return features.iterator();
    }
}
