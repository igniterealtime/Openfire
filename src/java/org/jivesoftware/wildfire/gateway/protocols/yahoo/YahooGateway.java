/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.yahoo;

import org.jivesoftware.wildfire.gateway.BaseGateway;
import org.jivesoftware.wildfire.gateway.GatewaySession;
import org.jivesoftware.wildfire.gateway.SubscriptionInfo;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

/**
 * @author Noah Campbell
 */
public class YahooGateway extends BaseGateway {

    /** The YAHOO. */
    private static String YAHOO = "yahoo";

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseGateway#getName()
     */
    @Override
    public String getName() {
        return YAHOO;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseGateway#setName(String)
     */
    @Override
    public void setName(String newname) {
        YAHOO = newname;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseGateway#getDescription()
     */
    @Override
    public String getDescription() {
        return "Yahoo! Gateway (ymsg9)";
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.Endpoint#sendPacket(Packet)
     */
    @SuppressWarnings("unused")
    public void sendPacket(@SuppressWarnings("unused") Packet packet) throws ComponentException {
        // do nothing.
    }
 
    /**
     * @param jid
     * @param string
     * @throws Exception
     */
    public void sendMessage(JID jid, String string) throws Exception {
        Message m = new Message();
        m.setTo(jid);
        m.setBody(string);
        this.sendPacket(m);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseGateway#getType()
     */
    @Override
    public String getType() {
       return "yahoo";
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseGateway#getVersion()
     */
    @Override
    public String getVersion() {
       return "v1.0";
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseGateway#getSessionInstance(org.jivesoftware.wildfire.gateway.SubscriptionInfo)
     */
    @Override
    protected GatewaySession getSessionInstance(SubscriptionInfo info) {
        return new YahooGatewaySession(info, this);
    }

}
