/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.plugin.presence;

import org.xmpp.packet.Presence;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * The TextPresenceProvider provides the user's presence status in plain-text format.
 * The returned text is the last known presence status of the user. If the user is offline
 * then the unavailable presence will be recreated with the last known presence status.
 *
 * @author Greg Unrein
 */
class TextPresenceProvider extends PresenceInfoProvider {

    public void sendInfo(HttpServletRequest request, HttpServletResponse response,
            Presence presence) throws IOException {
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        if (presence == null) {
            // Recreate the unavailable presence with the last known status
            JID targetJID = new JID(request.getParameter("jid"));
            presence = new Presence(Presence.Type.unavailable);
            XMPPServer server = XMPPServer.getInstance();
            try {
                User user = server.getUserManager().getUser(targetJID.getNode());
                String status = server.getPresenceManager().getLastPresenceStatus(user);
                if (status != null) {
                    presence.setStatus(status);
                }
                else {
                    presence.setStatus(JiveGlobals.getProperty("plugin.presence.unavailable.status",
                                                               "Unavailable"));
                }
            }
            catch (UserNotFoundException e) {}
            presence.setFrom(targetJID);
        }
        out.println(presence.getStatus());
        out.flush();
    }

    public void sendUserNotFound(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        // Send a forbidden presence
        Presence presence = new Presence();
        presence.setError(PacketError.Condition.forbidden);
        try {
            presence.setFrom(new JID(request.getParameter("jid")));
        }
        catch (Exception e) {}
        try {
            presence.setTo(new JID(request.getParameter("req_jid")));
        }
        catch (Exception e) {}
        out.println(presence.getStatus());
        out.flush();
    }
}
