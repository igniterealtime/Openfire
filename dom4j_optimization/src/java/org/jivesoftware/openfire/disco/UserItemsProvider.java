/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.disco;

import org.dom4j.Element;
import org.xmpp.packet.JID;

import java.util.Iterator;

/**
 * <p>
 * A <code>UserItemsProvider</code> is responsible for providing the items associated with a user
 * that are to be discovered during a disco#items query sent to the user.
 * </p>
 * 
 * <p>
 * Examples of when a <code>UserItemsProvider</code> is used include:
 * </p>
 * <ul>
 * <li>For discovering PEP items of a user.</li>
 * <li>For discovering available resources of a user.</li>
 * </ul>
 * </p>
 *
 * @author Armando Jagucki
 */
public interface UserItemsProvider {

    /**
     * Returns an Iterator (of Element) with the target entity's items or null if none. Each Element
     * must include a JID attribute and may include the name and node attributes of the entity. In
     * case that the sender of the disco request is not authorized to discover items an
     * UnauthorizedException will be thrown.
     *
     * @param name the recipient JID's name.
     * @param senderJID the XMPPAddress of user that sent the disco items request.
     * @return an Iterator (of Element) with the target entity's items or null if none.
     */
    public abstract Iterator<Element> getUserItems(String name, JID senderJID);
}
