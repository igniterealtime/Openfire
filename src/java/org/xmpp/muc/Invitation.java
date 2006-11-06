/**
 * $RCSfile$
 * $Revision: 2576 $
 * $Date: 2005-02-06 12:04:40 -0800 (Sun, 06 Feb 2005) $
 *
 * Copyright 2004 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
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

package org.xmpp.muc;

import org.dom4j.Element;
import org.xmpp.packet.Message;

/**
 * Represents an invitation to a Multi-User Chat room from a room occupant to a user that is not
 * an occupant of the room. The invitation must be <b>sent to the room</b> and it's the room
 * responsibility to forward the invitation to the invitee. The <b>sender of the invitation must be
 * the real full JID of the inviter</b>.<p>
 *
 * Code example:
 * <pre>
 * // Invite the someone to the room.
 * Invitation invitation = new Invitation("invitee@jabber.org", "Join this excellent room");
 * invitation.setTo("room@conference.jabber.org");
 * invitation.setFrom("inviter@jabber.org/notebook");
 *
 * component.sendPacket(invitation);
 * </pre>
 *
 * @author Gaston Dombiak
 */
public class Invitation extends Message {

    /**
     * Creates a new invitation.
     *
     * @param invitee the XMPP address of the invitee. The room will forward the invitation to this
     *        address.
     * @param reason the reason why the invitation is being sent.
     */
    public Invitation(String invitee, String reason) {
        super();
        Element element = addChildElement("x", "http://jabber.org/protocol/muc#user");
        Element invite = element.addElement("invite");
        invite.addAttribute("to", invitee);
        if (reason != null && reason.length() > 0) {
            invite.addElement("reason").setText(reason);
        }
    }
}
