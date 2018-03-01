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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.session.ClientSession;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.Collections;
import java.util.Iterator;

/**
 * This handler manages XEP-0280 Message Carbons.
 *
 * @author Christian Schudt
 */
public final class IQMessageCarbonsHandler extends IQHandler implements ServerFeaturesProvider {

    private static final String NAMESPACE = "urn:xmpp:carbons:2";

    private IQHandlerInfo info;

    public IQMessageCarbonsHandler() {
        super("XEP-0280: Message Carbons");
        info = new IQHandlerInfo("", NAMESPACE);
    }

    @Override
    public IQ handleIQ(IQ packet) {
        Element enable = packet.getChildElement();
        if (XMPPServer.getInstance().isLocal(packet.getFrom())) {
            if (enable.getName().equals("enable")) {
                ClientSession clientSession = sessionManager.getSession(packet.getFrom());
                clientSession.setMessageCarbonsEnabled(true);
                return IQ.createResultIQ(packet);

            } else if (enable.getName().equals("disable")) {
                ClientSession clientSession = sessionManager.getSession(packet.getFrom());
                clientSession.setMessageCarbonsEnabled(false);
                return IQ.createResultIQ(packet);
            } else {
                IQ error = IQ.createResultIQ(packet);
                error.setError(PacketError.Condition.bad_request);
                return error;
            }
        } else {
            // if the request is from a client that is not hosted on this server.
            IQ error = IQ.createResultIQ(packet);
            error.setError(PacketError.Condition.not_allowed);
            return error;
        }
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton(NAMESPACE).iterator();
    }
}
