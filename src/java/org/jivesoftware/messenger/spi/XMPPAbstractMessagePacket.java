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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.XMPPPacket;

/**
 * <p>Implements a generic message packet without dealing with the underlying
 * fragment storage.</p>
 *
 * @author Iain Shigeoka
 */
abstract public class XMPPAbstractMessagePacket implements Message {

    /**
     * <p>Implement the valid message types.</p>
     *
     * @param type The type of message or null for the default (normal) type
     * @return The packet type
     */
    public XMPPPacket.Type typeFromString(String type) {
        if (CHAT.toString().equals(type)) {
            return CHAT;
        }
        else if (GROUP_CHAT.toString().equals(type)) {
            return GROUP_CHAT;
        }
        else if (HEADLINE.toString().equals(type)) {
            return HEADLINE;
        }
        else if (ERROR.toString().equals(type)) {
            return ERROR;
        }
        else {
            return NORMAL;
        }
    }
}
