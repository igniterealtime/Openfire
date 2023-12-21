/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
import org.dom4j.Namespace;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.session.ComponentSession;
import org.jivesoftware.openfire.session.LocalComponentSession;
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
 * have their FROM attribute overridden to avoid spoofing.<p>
 *
 * This is an implementation of the XEP-114.
 *
 * @author Gaston Dombiak
 */
public class ComponentStanzaHandler extends StanzaHandler {

    private static final Logger Log = LoggerFactory.getLogger(ComponentStanzaHandler.class);

    public ComponentStanzaHandler(PacketRouter router, Connection connection) {
        super(router, connection);
    }

    @Override
    boolean processUnknowPacket(Element doc) throws UnauthorizedException {
        String tag = doc.getName();
        if ("handshake".equals(tag)) {
            // External component is trying to authenticate
            if (!((LocalComponentSession) session).authenticate(doc.getStringValue())) {
                Log.debug( "Closing session that failed to authenticate: {}", session );
                session.close();
            }
            return true;
        } else if ("error".equals(tag) && "stream".equals(doc.getNamespacePrefix())) {
            Log.debug( "Closing session because of received stream error {}. Affected session: {}", doc.asXML(), session );
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
                        final String subdomain;
                        int index = extraDomain.indexOf( XMPPServer.getInstance().getServerInfo().getXMPPDomain() );
                        if (index > -1) {
                            subdomain = extraDomain.substring(0, index -1);
                        } else {
                            subdomain = extraDomain;
                        }
                        InternalComponentManager.getInstance().addComponent(subdomain, component);
                        componentSession.getConnection().registerCloseListener( handback -> InternalComponentManager.getInstance().removeComponent( subdomain, (ComponentSession.ExternalComponent) handback ), component );
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

    @Override
    protected void processIQ(IQ packet) throws UnauthorizedException {
        if (!session.isAuthenticated()) {
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

    @Override
    protected void processPresence(Presence packet) throws UnauthorizedException {
        if (!session.isAuthenticated()) {
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

    @Override
    protected void processMessage(Message packet) throws UnauthorizedException {
        if (!session.isAuthenticated()) {
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

    @Override
    void startTLS() throws Exception {
        connection.startTLS(false, false);
    }

    @Override
    Namespace getNamespace() {
        return new Namespace("", "jabber:component:accept");
    }

    @Override
    boolean validateHost() {
        return false;
    }

    @Override
    boolean validateJIDs() {
        return false;
    }

    @Override
    void createSession(String serverName, XmlPullParser xpp, Connection connection) throws XmlPullParserException
    {
        // The connected client is a connection manager so create a ConnectionMultiplexerSession
        session = LocalComponentSession.createSession(serverName, xpp, connection);
    }
}
