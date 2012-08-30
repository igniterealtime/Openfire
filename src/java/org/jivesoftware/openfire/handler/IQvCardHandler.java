/**
 * $RCSfile$
 * $Revision: 1653 $
 * $Date: 2005-07-20 00:21:40 -0300 (Wed, 20 Jul 2005) $
 *
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

import java.util.Iterator;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * Implements the TYPE_IQ vcard-temp protocol. Clients
 * use this protocol to set and retrieve the vCard information
 * associated with someone's account.
 * <p/>
 * A 'get' query retrieves the vcard for the addressee.
 * A 'set' query sets the vcard information for the sender's account.
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
 * <p/>
 * <h2>Warning</h2>
 * I have noticed incompatibility between vCard XML used by Exodus and Psi.
 * There is a new vCard standard going through the JSF JEP process. We might
 * want to start either standardizing on clients (probably the most practical),
 * sending notices for non-conformance (useful),
 * or attempting to translate between client versions (not likely).
 *
 * @author Iain Shigeoka
 */
public class IQvCardHandler extends IQHandler {

	private static final Logger Log = LoggerFactory.getLogger(IQvCardHandler.class);

    private IQHandlerInfo info;
    private XMPPServer server;
    private UserManager userManager;

    public IQvCardHandler() {
        super("XMPP vCard Handler");
        info = new IQHandlerInfo("vCard", "vcard-temp");
    }

    @Override
	public IQ handleIQ(IQ packet) throws UnauthorizedException, PacketException {
        IQ result = IQ.createResultIQ(packet);
        IQ.Type type = packet.getType();
        if (type.equals(IQ.Type.set)) {
            try {
                User user = userManager.getUser(packet.getFrom().getNode());
                Element vcard = packet.getChildElement();
                if (vcard != null) {
                    VCardManager.getInstance().setVCard(user.getUsername(), vcard);
                }
            }
            catch (UserNotFoundException e) {
                result = IQ.createResultIQ(packet);
                result.setChildElement(packet.getChildElement().createCopy());
                result.setError(PacketError.Condition.item_not_found);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
                result.setError(PacketError.Condition.internal_server_error);
            }
        }
        else if (type.equals(IQ.Type.get)) {
            JID recipient = packet.getTo();
            // If no TO was specified then get the vCard of the sender of the packet
            if (recipient == null) {
                recipient = packet.getFrom();
            }
            // By default return an empty vCard
            result.setChildElement("vCard", "vcard-temp");
            // Only try to get the vCard values of non-anonymous users
            if (recipient != null) {
                if (recipient.getNode() != null && server.isLocal(recipient)) {
                    VCardManager vManager = VCardManager.getInstance();
                    Element userVCard = vManager.getVCard(recipient.getNode());
                    if (userVCard != null) {
                        // Check if the requester wants to ignore some vCard's fields
                        Element filter = packet.getChildElement()
                                .element(QName.get("filter", "vcard-temp-filter"));
                        if (filter != null) {
                            // Create a copy so we don't modify the original vCard
                            userVCard = userVCard.createCopy();
                            // Ignore fields requested by the user
                            for (Iterator toFilter = filter.elementIterator(); toFilter.hasNext();)
                            {
                                Element field = (Element) toFilter.next();
                                Element fieldToRemove = userVCard.element(field.getName());
                                if (fieldToRemove != null) {
                                    fieldToRemove.detach();
                                }
                            }
                        }
                        result.setChildElement(userVCard);
                    }
                }
                else {
                    result = IQ.createResultIQ(packet);
                    result.setChildElement(packet.getChildElement().createCopy());
                    result.setError(PacketError.Condition.item_not_found);
                }
            } else {
                result = IQ.createResultIQ(packet);
                result.setChildElement(packet.getChildElement().createCopy());
                result.setError(PacketError.Condition.item_not_found);
            }
        }
        else {
            result.setChildElement(packet.getChildElement().createCopy());
            result.setError(PacketError.Condition.not_acceptable);
        }
        return result;
    }

    @Override
	public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        userManager = server.getUserManager();
    }

    @Override
	public IQHandlerInfo getInfo() {
        return info;
    }
}
