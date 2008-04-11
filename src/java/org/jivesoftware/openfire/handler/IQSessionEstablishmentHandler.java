/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.handler;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.xmpp.packet.IQ;

/**
 * Activate client sessions once resource binding has been done. Clients need to active their
 * sessions in order to engage in instant messaging and presence activities. The server may
 * deny sessions activations if the max number of sessions in the server has been reached or
 * if a user does not have permissions to create sessions.<p>
 *
 * Current implementation does not check any of the above conditions. However, future versions
 * may add support for those checkings.
 *
 * @author Gaston Dombiak
 */
public class IQSessionEstablishmentHandler extends IQHandler {

    private IQHandlerInfo info;

    public IQSessionEstablishmentHandler() {
        super("Session Establishment handler");
        info = new IQHandlerInfo("session", "urn:ietf:params:xml:ns:xmpp-session");
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        // Just answer that the session has been activated
        IQ reply = IQ.createResultIQ(packet);
        reply.setChildElement(packet.getChildElement().createCopy());
        return reply;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }
}
