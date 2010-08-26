/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

    @Override
	public IQ handleIQ(IQ packet) throws UnauthorizedException {
        // Just answer that the session has been activated
        IQ reply = IQ.createResultIQ(packet);
        reply.setChildElement(packet.getChildElement().createCopy());
        return reply;
    }

    @Override
	public IQHandlerInfo getInfo() {
        return info;
    }
}
