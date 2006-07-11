/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.oscar;

import org.jivesoftware.util.Log;

import org.jivesoftware.wildfire.gateway.BaseGateway;
import org.jivesoftware.wildfire.gateway.GatewaySession;
import org.jivesoftware.wildfire.gateway.SubscriptionInfo;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

/**
 * @author Daniel Henninger
 */
public class OSCARGateway extends BaseGateway {
    /* Gateway Name String */
    private static String NameString = "oscar";

    @Override
    public String getName() {
        return NameString;
    }

    @Override
    public void setName(String newname) {
        NameString = newname;
    }

    @Override
    public String getDescription() {
        return "OSCAR (AIM/ICQ) Gateway";
    }

    /*@SuppressWarnings("unused"); */
    public void sendPacket(@SuppressWarnings("unused") Packet packet) throws ComponentException {
        // Do nothing
    }

    public void sendMessage(JID jid, String string) throws Exception {
        Message m = new Message();
        m.setTo(jid);
        m.setBody(string);
        this.sendPacket(m);
    }

    @Override
    public String getType() {
        return "oscar";
    }

    @Override
    public String getVersion() {
        return "v1.0";
    }

    @Override
    protected GatewaySession getSessionInstance(SubscriptionInfo info) {
        Log.debug("Getting session instance");       
        return new OSCARGatewaySession(info, this);
    }
}
