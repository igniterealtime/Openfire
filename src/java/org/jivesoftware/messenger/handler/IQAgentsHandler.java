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

import org.jivesoftware.messenger.chat.ChatServer;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.IQ;
import org.jivesoftware.messenger.IQHandlerInfo;
import org.jivesoftware.messenger.PayloadFragment;
import org.jivesoftware.messenger.XMPPDOMFragment;
import java.util.ArrayList;
import java.util.Iterator;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;


/**
 * Implements the TYPE_IQ jabber:iq:agents protocol. Clients
 * use this protocol to retrieve the list of active agents
 * (server-side services) that are active in this domain.
 * <p/>
 * A 'get' query retrieves a list of server agents.
 * A 'set' query never sent from client to server.
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
public class IQAgentsHandler extends IQHandler implements ServerFeaturesProvider {

    private Element agentList = null;
    public ChatServer chatServer = null;
    private IQHandlerInfo info;

    public IQAgentsHandler() {
        super("XMPP WorkgroupAgent handler");
        info = new IQHandlerInfo("query", "jabber:iq:agents");
    }

    public IQ handleIQ(IQ packet) {
        IQ reply = packet.createResult();
        if (agentList != null) {
            PayloadFragment frag = new PayloadFragment("jabber:iq:agents", "query");
            frag.addFragment(new XMPPDOMFragment((Element)agentList.clone()));
            reply.setChildFragment(frag);
        }
        return reply;
    }

    protected void serviceAdded(Object service) {
        if (service instanceof ChatServer) {
            agentList = DocumentHelper.createElement("agent");
            agentList.addAttribute("jid", ((ChatServer)service).getChatServerName());
            Element name = DocumentHelper.createElement("name");
            name.setText("Messenger Groupchat Service");
            agentList.add(name);
            Element description = DocumentHelper.createElement("description");
            description.setText("The Jive Messenger Group Chat Server");
            agentList.add(description);
            Element serviceElement = DocumentHelper.createElement("service");
            serviceElement.setText("groupchat");
            agentList.add(serviceElement);
        }
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo info = super.getTrackInfo();
        info.getTrackerClasses().put(ChatServer.class, "chatServer");
        return info;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:agents");
        return features.iterator();
    }
}
