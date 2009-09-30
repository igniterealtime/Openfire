/**
 * $RCSfile: $
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

package org.jivesoftware.openfire.pep;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.handler.IQHandler;
import org.xmpp.packet.IQ;

/**
 * <p>
 * An {@link IQHandler} used to implement XEP-0163: "Personal Eventing via Pubsub"
 * Version 1.0
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
