/**
 * $RCSfile: ComponentSocketReader.java,v $
 * $Revision: 3174 $
 * $Date: 2005-12-08 17:41:00 -0300 (Thu, 08 Dec 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import org.dom4j.Element;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.PacketRouter;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.component.ComponentSession;
import org.jivesoftware.wildfire.component.InternalComponentManager;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.PacketError;

import java.io.IOException;
import java.net.Socket;

/**
 * A SocketReader specialized for component connections. This reader will be used when the open
 * stream contains a jabber:component:accept namespace.
 *
 * @author Gaston Dombiak
 */
public class ComponentSocketReader extends SocketReader {

    public ComponentSocketReader(PacketRouter router, String serverName, Socket socket,
            SocketConnection connection) {
        super(router, serverName, socket, connection);
    }

    /**
     * Only <tt>bind<tt> packets will be processed by this class to bind more domains
     * to existing external components. Any other type of packet is unknown and thus
     * rejected generating the connection to be closed.
     *
     * @param doc the unknown DOM element that was received
     * @return false if packet is unknown otherwise true.
     */
    protected boolean processUnknowPacket(Element doc) {
        // Handle subsequent bind packets
        if ("bind".equals(doc.getName())) {
            ComponentSession componentSession = (ComponentSession) session;
            // Get the external component of this session
            ComponentSession.ExternalComponent component = componentSession.getExternalComponent();
            String initialDomain = component.getInitialSubdomain();
            String extraDomain = doc.attributeValue("name");
            if (extraDomain == null || "".equals(extraDomain)) {
                // No new bind domain was specified so return a bad_request error
                Element reply = doc.createCopy();
                reply.add(new PacketError(PacketError.Condition.bad_request).getElement());
                connection.deliverRawText(reply.asXML());
            }
            else if (extraDomain.endsWith(initialDomain)) {
                // Only accept subdomains under the initial registered domain
                if (component.getSubdomains().contains(extraDomain)) {
                    // Domain already in use so return a conflict error
                    Element reply = doc.createCopy();
                    reply.add(new PacketError(PacketError.Condition.conflict).getElement());
                    connection.deliverRawText(reply.asXML());
                }
                else {
                    try {
                        InternalComponentManager.getInstance().addComponent(extraDomain, component);
                        component.addSubdomain(extraDomain);
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
        // This is an unknown packet so return false (and close the connection)
        return false;
    }

    boolean createSession(String namespace) throws UnauthorizedException, XmlPullParserException,
            IOException {
        if ("jabber:component:accept".equals(namespace)) {
            // The connected client is a component so create a ComponentSession
            session = ComponentSession.createSession(serverName, reader, connection);
            return true;
        }
        return false;
    }

    String getNamespace() {
        return "jabber:component:accept";
    }

    boolean validateHost() {
        return false;
    }
}
