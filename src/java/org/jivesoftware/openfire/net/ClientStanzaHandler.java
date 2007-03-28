/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.net;

import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

/**
 * Handler of XML stanzas sent by clients connected directly to the server. Received packet will
 * have their FROM attribute overriden to avoid spoofing.<p>
 *
 * By default the hostname specified in the stream header sent by clients will not be validated.
 * When validated the TO attribute of the stream header has to match the server name or a valid
 * subdomain. If the value of the 'to' attribute is not valid then a host-unknown error
 * will be returned. To enable the validation set the system property
 * <b>xmpp.client.validate.host</b> to true.
 *
 * @author Gaston Dombiak
 */
public class ClientStanzaHandler extends StanzaHandler {

    public ClientStanzaHandler(PacketRouter router, String serverName, Connection connection) {
        super(router, serverName, connection);
    }

    /**
     * Only packets of type Message, Presence and IQ can be processed by this class. Any other
     * type of packet is unknown and thus rejected generating the connection to be closed.
     *
     * @param doc the unknown DOM element that was received
     * @return always false.
     */
    boolean processUnknowPacket(Element doc) {
        return false;
    }

    String getNamespace() {
        return "jabber:client";
    }

    boolean validateHost() {
        return JiveGlobals.getBooleanProperty("xmpp.client.validate.host",false);
    }

    boolean validateJIDs() {
        return true;
    }

    boolean createSession(String namespace, String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException {
        if ("jabber:client".equals(namespace)) {
            // The connected client is a regular client so create a ClientSession
            session = ClientSession.createSession(serverName, xpp, connection);
            return true;
        }
        return false;
    }

    protected void processIQ(IQ packet) throws UnauthorizedException {
        // Overwrite the FROM attribute to avoid spoofing
        packet.setFrom(session.getAddress());
        super.processIQ(packet);
    }

    protected void processPresence(Presence packet) throws UnauthorizedException {
        // Overwrite the FROM attribute to avoid spoofing
        packet.setFrom(session.getAddress());
        super.processPresence(packet);
    }

    protected void processMessage(Message packet) throws UnauthorizedException {
        // Overwrite the FROM attribute to avoid spoofing
        packet.setFrom(session.getAddress());
        super.processMessage(packet);
    }
}
