/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.pep;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.handler.IQHandler;
import org.xmpp.packet.IQ;

/**
 * <p>
 * An IQHandler used to implement XEP-0163: "Personal Eventing via Pubsub."
 * </p>
 * 
 * <p>
 * An IQHandler can only handle one namespace in its IQHandlerInfo. However, PEP
 * related packets are seen having a variety of different namespaces. This
 * handler is needed to forward IQ packets with the
 * <i>'http://jabber.org/protocol/pubsub#owner'</i> namespace to IQPEPHandler.
 * </p>
 * 
 * @author Armando Jagucki
 * 
 */
public class IQPEPOwnerHandler extends IQHandler {

    private IQHandlerInfo info;

    public IQPEPOwnerHandler() {
        super("Personal Eventing 'pubsub#owner' Handler");
        info = new IQHandlerInfo("pubsub", "http://jabber.org/protocol/pubsub#owner");
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        return XMPPServer.getInstance().getIQPEPHandler().handleIQ(packet);
    }
}
