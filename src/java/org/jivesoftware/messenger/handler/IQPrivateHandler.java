/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.spi.IQImpl;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import org.dom4j.Element;


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

    public IQPrivateHandler() {
        super("XMPP Private Storage Handler");
        info = new IQHandlerInfo("query", "jabber:iq:private", IQImpl.class);
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException, PacketException {

        IQ replyPacket = null;
        try {
            Element dataElement = (Element)((XMPPDOMFragment)packet.getChildFragment()).getRootElement().elementIterator().next();
            if (dataElement != null) {
                if (IQ.GET.equals(packet.getType())) {
                    replyPacket = packet.createResult();
                    PayloadFragment frag = new PayloadFragment("jabber:iq:private", "query");
                    frag.addFragment(new XMPPDOMFragment(privateStore.get(packet.getOriginatingSession().getUserID(), dataElement)));
                    replyPacket.setChildFragment(frag);
                }
                else {
                    privateStore.add(packet.getOriginatingSession().getUserID(), dataElement);
                    replyPacket = packet.createResult();
                }
            }
            else {
                replyPacket = packet.createResult();
                PayloadFragment frag = new PayloadFragment("jabber:iq:private", "query");
                replyPacket.setChildFragment(frag);
            }
        }
        catch (UserNotFoundException e) {
            throw new UnauthorizedException(e);
        }
        return replyPacket;
    }

    public PrivateStore privateStore = null;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = super.getTrackInfo();
        trackInfo.getTrackerClasses().put(PrivateStore.class, "privateStore");
        return trackInfo;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:private");
        return features.iterator();
    }
}
