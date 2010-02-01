/**
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

package org.jivesoftware.openfire.net;

import org.dom4j.Element;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.session.ComponentSession;
import org.jivesoftware.openfire.session.LocalComponentSession;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * Handler of XML stanzas sent by external components connected directly to the server. Received packet will
 * have their FROM attribute overriden to avoid spoofing.<p>
 *
 * This is an implementation of the XEP-114. In the future we will add support for XEP-225 now that
 * we are using MINA things should be easier. Since we are now using MINA incoming traffic is handled
 * by a set of worker threads.
 *
 * @author Gaston Dombiak
 */
public class ComponentStanzaHandler extends StanzaHandler {

	private static final Logger Log = LoggerFactory.getLogger(ComponentStanzaHandler.class);

    public ComponentStanzaHandler(PacketRouter router, String serverName, Connection connection) {
        super(router, serverName, connection);
    }

    boolean processUnknowPacket(Element doc) throws UnauthorizedException {
        String tag = doc.getName();
        if ("handshake".equals(tag)) {
            // External component is trying to authenticate
            if (!((LocalComponentSession) session).authenticate(doc.getStringValue())) {
                session.close();
            }
            return true;
        } else if ("error".equals(tag) && "stream".equals(doc.getNamespacePrefix())) {
            session.close();
            return true;
        } else if ("bind".equals(tag)) {
            // Handle subsequent bind packets
            LocalComponentSession componentSession = (LocalComponentSession) session;
            // Get the external component of this session
            ComponentSession.ExternalComponent component = componentSession.getExternalComponent();
            String initialDomain = component.getInitialSubdomain();
            String extraDomain = doc.attributeValue("name");
            String allowMultiple = doc.attributeValue("allowMultiple");
            if (extraDomain == null || "".equals(extraDomain)) {
                // No new bind domain was specified so return a bad_request error
                Element reply = doc.createCopy();
                reply.add(new PacketError(PacketError.Condition.bad_request).getElement());
                connection.deliverRawText(reply.asXML());
            }
            else if (extraDomain.equals(initialDomain)) {
                // Component is binding initial domain that is already registered
                // Send confirmation that the new domain has been registered
                connection.deliverRawText("<bind/>");
            }
            else if (extraDomain.endsWith(initialDomain)) {
                // Only accept subdomains under the initial registered domain
                if (allowMultiple != null && component.getSubdomains().contains(extraDomain)) {
                    // Domain already in use so return a conflict error
                    Element reply = doc.createCopy();
                    reply.add(new PacketError(PacketError.Condition.conflict).getElement());
                    connection.deliverRawText(reply.asXML());
                }
                else {
                    try {
                        // Get the requested subdomain
                        String subdomain = extraDomain;
                        int index = extraDomain.indexOf(serverName);
                        if (index > -1) {
                            subdomain = extraDomain.substring(0, index -1);
                        }
                        InternalComponentManager.getInstance().addComponent(subdomain, component);
                        // Send confirmation that the new domain has been registered
                        connection.deliverRawText("<bind/>");
                    }
                    catch (ComponentException e) {
                        Log.error("Error binding extra domain: " + extraDomain + " to component: " +
                                component, e);
                        // Return internal server error
                        Element reply = doc.createCopy();
                        reply.add(new PacketError(
                                PacketError.Condition.internal_server_error).getElement());
                        connection.deliverRawText(reply.asXML());
                    }
                }
            }
            else {
                // Return forbidden error since we only allow subdomains of the intial domain
                // to be used by the same external component
                Element reply = doc.createCopy();
                reply.add(new PacketError(PacketError.Condition.forbidden).getElement());
                connection.deliverRawText(reply.asXML());
            }
            return true;
        }
        return false;
    }

    protected void processIQ(IQ packet) throws UnauthorizedException {
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            // Session is not authenticated so return error
            IQ reply = new IQ();
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setID(packet.getID());
            reply.setTo(packet.getFrom());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_authorized);
            session.process(reply);
            return;
        }
        // Keep track of the component that sent an IQ get/set
        if (packet.getType() == IQ.Type.get || packet.getType() == IQ.Type.set) {
            // Handle subsequent bind packets
            LocalComponentSession componentSession = (LocalComponentSession) session;
            // Get the external component of this session
            LocalComponentSession.LocalExternalComponent component =
                    (LocalComponentSession.LocalExternalComponent) componentSession.getExternalComponent();
            component.track(packet);
        }
        super.processIQ(packet);
    }

    protected void processPresence(Presence packet) throws UnauthorizedException {
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            // Session is not authenticated so return error
            Presence reply = new Presence();
            reply.setID(packet.getID());
            reply.setTo(packet.getFrom());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_authorized);
            session.process(reply);
            return;
        }
        super.processPresence(packet);
    }

    protected void processMessage(Message packet) throws UnauthorizedException {
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            // Session is not authenticated so return error
            Message reply = new Message();
            reply.setID(packet.getID());
            reply.setTo(packet.getFrom());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_authorized);
            session.process(reply);
            return;
        }
        super.processMessage(packet);
    }

    void startTLS() throws Exception {
        // TODO Finish implementation. We need to get the name of the CM if we want to validate certificates of the CM that requested TLS
        connection.startTLS(false, "IMPLEMENT_ME", Connection.ClientAuth.disabled);
    }

    String getNamespace() {
        return "jabber:component:accept";
    }

    boolean validateHost() {
        return false;
    }

    boolean validateJIDs() {
        return false;
    }

    boolean createSession(String namespace, String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException {
        if (getNamespace().equals(namespace)) {
            // The connected client is a connection manager so create a ConnectionMultiplexerSession
            session = LocalComponentSession.createSession(serverName, xpp, connection);
            return true;
        }
        return false;
    }
}
