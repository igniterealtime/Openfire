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

package org.jivesoftware.messenger.container;

/**
 * Thrown by an event listener when an event is received that the
 * listener does not wish to receive. This is a flag to the sender
 * to no longer notifyEvent the listener of that event type.
 *
 * @author Iain Shigeoka
 */
public class UnknownEventException extends Exception {
    /**
     * Create an exception without a message.
     */
    public UnknownEventException() {
        super();
    }

    /**
     * Create an exception with a message.
     *
     * @param msg The message to include with the exception
     */
    public UnknownEventException(String msg) {
        super(msg);
    }
}
