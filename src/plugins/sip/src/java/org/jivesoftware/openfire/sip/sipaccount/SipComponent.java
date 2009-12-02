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

package org.jivesoftware.openfire.sip.sipaccount;

import java.sql.SQLException;

import org.dom4j.Element;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

/**
 * Implements an openfire Component and SessionEventListener. Controls every
 * registration inf request and process user's SIP presence informations.
 *
 * @author Thiago Rocha Camargo
 */
public class SipComponent implements Component, SessionEventListener {

	private static final Logger Log = LoggerFactory.getLogger(SipComponent.class);
	
    ComponentManager componentManager = null;

    /**
     * Namespace of the packet extension.
     */
    public static final String NAMESPACE = "http://www.jivesoftware.com/protocol/sipark";

    public static final String PROPNAME = "plugin.sipark.serviceName";

    public static final String NAME = "sipark";

    public SipComponent() {
        this.componentManager = ComponentManagerFactory.getComponentManager();
        SessionEventDispatcher.addListener(this);
    }

    public void initialize(JID jid, ComponentManager componentManager) {
    }

    public void start() {
    }

    public void shutdown() {
        SessionEventDispatcher.removeListener(this);
    }

    // Component Interface

    public void processPacket(Packet packet) {
        Log.debug(packet.toXML());
        if (packet instanceof IQ) {
            // Handle disco packets
            IQ iq = (IQ)packet;
            // Ignore IQs of type ERROR or RESULT
            if (IQ.Type.error == iq.getType() || IQ.Type.result == iq.getType()) {
                return;
            }
            processIQ(iq);
        }
    }

    private void processIQ(IQ iq) {
        IQ reply = IQ.createResultIQ(iq);
        String namespace = iq.getChildElement().getNamespaceURI();
        Element childElement = iq.getChildElement().createCopy();
        reply.setChildElement(childElement);

        if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            if (iq.getTo().getNode() == null) {
                // Return service identity and features
                Element identity = childElement.addElement("identity");
                identity.addAttribute("category", "component");
                identity.addAttribute("type", "generic");
                identity.addAttribute("name", "SIP Controller");
                childElement.addElement("feature").addAttribute("var", "http://jabber.org/protocol/disco#info");
                childElement.addElement("feature").addAttribute("var", "http://www.jivesoftware.com/protocol/sipark");

            }
        }
        else if (NAMESPACE.equals(namespace)) {
            if (iq.getTo().getNode() == null && iq.getFrom() != null) {

                SipAccount sipAccount = SipAccountDAO.getAccountByUser(iq.getFrom().toBareJID().split("@")[0]);

                if (iq.getChildElement().element("status") == null) {
                    if (sipAccount != null && sipAccount.isEnabled()) {
                        // There is a SIP mapping for this user so return mapping information
                        Element registration = childElement.addElement("registration");
                        registration.addElement("jid")
                                .setText(sipAccount.getUsername() + "@" + componentManager.getServerName());
                        registration.addElement("username").setText(sipAccount.getSipUsername());
                        registration.addElement("authUsername").setText(sipAccount.getAuthUsername());
                        registration.addElement("displayPhoneNum").setText(sipAccount.getDisplayName());
                        registration.addElement("password").setText(sipAccount.getPassword());
                        registration.addElement("server").setText(sipAccount.getServer());
                        registration.addElement("stunServer").setText(sipAccount.getStunServer());
                        registration.addElement("stunPort").setText(sipAccount.getStunPort());
                        registration.addElement("useStun").setText(String.valueOf(sipAccount.isUseStun()));
                        registration.addElement("voicemail").setText(sipAccount.getVoiceMailNumber());
                        registration.addElement("enabled").setText(String.valueOf(sipAccount.isEnabled()));
                        registration.addElement("outboundproxy").setText(sipAccount.getOutboundproxy());
                        registration.addElement("promptCredentials").setText(String.valueOf(sipAccount.isPromptCredentials()));
                    }
                    else {
                        // No SIP mapping was found
                        reply.getChildElement().addAttribute("type", "unregistered");
                    }
                }
                else {
                    if (sipAccount != null) {
                        Element status = iq.getChildElement().element("status");
                        if (!status.getTextTrim().equals("")) {
                            sipAccount.setStatus(SipRegisterStatus.valueOf(status.getTextTrim()));
                            try {
                                SipAccountDAO.update(sipAccount);
                            }
                            catch (SQLException e) {
                                Log.error(e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        }
        else {
            // Answer an error since the server can't handle the requested
            // namespace
            reply.setError(PacketError.Condition.service_unavailable);
        }
        try {
            componentManager.sendPacket(this, reply);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        Log.debug("PACKET SENT: " + reply.toXML());
    } // Other Methods

    public String getDescription() {
        return "SIP Admin Plugin";
    }

    public String getName() {
        return "SIPAdmin";
    }

    public void anonymousSessionCreated(Session arg0) {
        // Ignore

    }

    public void anonymousSessionDestroyed(Session arg0) {
        // Ignore

    }

    public void sessionCreated(Session arg0) {
        // Ignore

    }

    /**
     * Sets the user SIP presence to Unregistered if his jabber account is disconnected.
     *
     * @param session the destroyed session
     */
    public void sessionDestroyed(Session session) {

        String username = session.getAddress().toBareJID().split("@")[0];

        SipAccount sipAccount = SipAccountDAO.getAccountByUser(username);
        if (sipAccount != null) {
            try {
                sipAccount.setStatus(SipRegisterStatus.Unregistered);
                SipAccountDAO.update(sipAccount);
            }
            catch (SQLException e) {
                Log.error(e.getMessage(), e);
            }
        }

    }

    public void resourceBound(Session session) {
    	// Do nothing.
    }
}
