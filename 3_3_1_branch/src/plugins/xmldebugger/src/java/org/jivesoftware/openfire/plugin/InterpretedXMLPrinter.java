/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.Packet;

/**
 * Packet interceptor that prints to the stdout XML packets (i.e. XML after
 * it was parsed).<p>
 *
 * If you find in the logs an entry for raw XML, an entry that a session was closed and
 * never find the corresponding interpreted XML for the raw XML then there was an error
 * while parsing the XML that closed the session. 
 *
 * @author Gaston Dombiak.
 */
public class InterpretedXMLPrinter implements PacketInterceptor {

    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
            throws PacketRejectedException {
        if (!processed && incoming) {
            System.out.println("INTERPRETED: " + packet.toXML());
        }
    }
}
