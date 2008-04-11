/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

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
