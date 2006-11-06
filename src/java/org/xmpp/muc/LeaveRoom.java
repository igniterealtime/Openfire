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

import org.xmpp.packet.Presence;

/**
 * Initial presence sent when joining an existing room or creating a new room. The JoinRoom presence
 * indicates the posibility of the sender to speak MUC.<p>
 *
 * Code example:
 * <pre>
 * // Join an existing room or create a new one.
 * JoinRoom joinRoom = new JoinRoom("john@jabber.org/notebook", "room@conference.jabber.org/nick");
 *
 * component.sendPacket(joinRoom);
 * </pre>
 *
 * @author Gaston Dombiak
 */
public class LeaveRoom extends Presence {

    /**
     * Creates a new Presence packet that could be sent to a MUC service in order to leave the room.
     *
     * @param from the full JID of the user that wants to leave the room.
     * @param to the room JID. That is the room address plus the nickname of the user as a resource.
     */
    public LeaveRoom(String from, String to) {
        super();
        setFrom(from);
        setTo(to);
        setType(Type.unavailable);
    }
}
