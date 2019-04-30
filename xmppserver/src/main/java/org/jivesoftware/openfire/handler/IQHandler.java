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

import org.jivesoftware.openfire.ChannelHandler;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

/**
 * Base class whose main responsibility is to handle IQ packets. Subclasses may
 * only need to specify the IQHandlerInfo (i.e. name and namespace of the packets
 * to handle) and actually handle the IQ packet. Simplifies creation of simple
 * TYPE_IQ message handlers.
 *
 * @author Gaston Dombiak
 */
public abstract class IQHandler extends BasicModule implements ChannelHandler {

    private static final Logger Log = LoggerFactory.getLogger(IQHandler.class);

    protected PacketDeliverer deliverer;
    protected SessionManager sessionManager;

    /**
     * Create a basic module with the given name.
     *
     * @param moduleName The name for the module or null to use the default
     */
    public IQHandler(String moduleName) {
        super(moduleName);
    }

    @Override
    public void process(Packet packet) throws PacketException {
        IQ iq = (IQ) packet;
        try {
            IQ reply = handleIQ(iq);
            if (reply != null) {
                deliverer.deliver(reply);
            }
        }
        catch (org.jivesoftware.openfire.auth.UnauthorizedException e) {
            if (iq != null) {
                try {
                    IQ response = IQ.createResultIQ(iq);
                    response.setChildElement(iq.getChildElement().createCopy());
                    response.setError(PacketError.Condition.not_authorized);
                    sessionManager.getSession(iq.getFrom()).process(response);
                }
                catch (Exception de) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), de);
                    sessionManager.getSession(iq.getFrom()).close();
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            try {
                IQ response = IQ.createResultIQ(iq);
                response.setChildElement(iq.getChildElement().createCopy());
                response.setError(PacketError.Condition.internal_server_error);
                sessionManager.getSession(iq.getFrom()).process(response);
            }
            catch (Exception e1) {
                // Do nothing
            }
        }
    }

    /**
     * Handles the received IQ packet.
     *
     * @param packet the IQ packet to handle.
     * @return the response to send back.
     * @throws UnauthorizedException if the user that sent the packet is not
     *      authorized to request the given operation.
     */
    public abstract IQ handleIQ(IQ packet) throws UnauthorizedException;

    /**
     * Returns the handler information to help generically handle IQ packets.
     * IQHandlers that aren't local server iq handlers (e.g. chatbots, transports, etc)
     * return {@code null}.
     *
     * @return The IQHandlerInfo for this handler
     */
    public abstract IQHandlerInfo getInfo();

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        deliverer = server.getPacketDeliverer();
        sessionManager = server.getSessionManager();
    }
}
