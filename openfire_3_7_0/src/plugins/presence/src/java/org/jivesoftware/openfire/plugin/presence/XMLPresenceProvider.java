/**
 * $RCSfile$
 * $Revision: 1680 $
 * $Date: 2005-07-22 17:49:46 -0300 (Fri, 22 Jul 2005) $
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

package org.jivesoftware.openfire.plugin.presence;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
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
        try {
            presence.setFrom(new JID(request.getParameter("jid")));
        }
        catch (Exception e) {}
        try {
            presence.setTo(new JID(request.getParameter("req_jid")));
        }
        catch (Exception e) {}
        out.println(presence.toXML());
        out.flush();
    }
}
