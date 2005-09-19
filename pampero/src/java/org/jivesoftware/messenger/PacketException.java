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

package org.jivesoftware.messenger;

/**
 * Represents a runtime packet exception typically from a malformed
 * packet. Uncaught Packet exceptions will cause the originating session
 * to close.
 *
 * @author Iain Shigeoka
 */
public class PacketException extends RuntimeException {

    public PacketException() {
    }

    public PacketException(String s) {
        super(s);
    }

}
