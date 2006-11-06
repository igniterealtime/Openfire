/**
 * $RCSfile$
 * $Revision: 2591 $
 * $Date: 2005-03-27 08:24:56 -0800 (Sun, 27 Mar 2005) $
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
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

/**
 * DestroyRoom is a packet that when sent will ask the server to destroy a given room. The room to
 * destroy must be specified in the TO attribute of the IQ packet. The server will send a presence
 * unavailable together with the alternate room and reason for the destruction to all the room
 * occupants before destroying the room.<p>
 * 
 * When destroying a room it is possible to provide an alternate room which may be replacing the
 * room about to be destroyed. It is also possible to provide a reason for the room destruction.
 */
public class DestroyRoom extends IQ {

    /**
     * Creates a new DestroyRoom with the reason for the destruction and an alternate room JID.
     *
     * @param alternateJID JID of the alternate room or <tt>null</tt> if none.
     * @param reason       reason for the destruction or <tt>null</tt> if none.
     */
    public DestroyRoom(JID alternateJID, String reason) {
        super();
        setType(Type.set);
        Element query = setChildElement("query", "http://jabber.org/protocol/muc#owner");
        Element destroy = query.addElement("destroy");
        if (alternateJID != null) {
            destroy.addAttribute("jid", alternateJID.toString());
        }
        if (reason != null) {
            destroy.addElement("reason").setText(reason);
        }
    }
}
