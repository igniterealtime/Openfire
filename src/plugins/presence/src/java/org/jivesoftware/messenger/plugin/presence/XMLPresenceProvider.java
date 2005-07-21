/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.plugin.presence;

import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * The XMLPresenceProvider provides information about the users presence in XML format.
 * The returned XML will include the last known presence of the user. If the user is offline
 * then the unavailable presence will be recreated with the last known presence status.
 *
 * @author Gaston Dombiak
 */
class XMLPresenceProvider extends PresenceInfoProvider {

    public void sendInfo(HttpServletRequest request, HttpServletResponse response,
            Presence presence) throws IOException {
        response.setContentType("text/xml");
        PrintWriter out = response.getWriter();
        if (presence == null) {
            // Recreate the unavailable presence with the last known status
            String username = request.getParameter("username");
            presence = new Presence(Presence.Type.unavailable);
            XMPPServer server = XMPPServer.getInstance();
            try {
                User user = server.getUserManager().getUser(username);
                String status = server.getPresenceManager().getLastPresenceStatus(user);
                if (status != null) {
                    presence.setStatus(status);
                }
            }
            catch (UserNotFoundException e) {}
            presence.setFrom(server.createJID(username, null));
        }
        out.println(presence.toXML());
        out.flush();
    }

    public void sendUserNotFound(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/xml");
        PrintWriter out = response.getWriter();
        // Send a forbidden presence
        Presence presence = new Presence();
        presence.setError(PacketError.Condition.forbidden);
        String username = request.getParameter("username");
        if (username != null) {
            try {
                presence.setFrom(XMPPServer.getInstance().createJID(username, null));
            }
            catch (Exception e) {}
        }
        String sender = request.getParameter("sender");
        if (sender != null) {
            try {
                presence.setTo(new JID(sender));
            }
            catch (Exception e) {}
        }
        out.println(presence.toXML());
        out.flush();
    }
}
