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
 * Thrown if a service encounters an Entry class that does not
 * follow the Entry class requirements.
 *
 * @author Iain Shigeoka
 */
public class UnuseableEntryException extends RuntimeException {

    /**
     * Creates an exception without a message.
     */
    public UnuseableEntryException() {
        super();
    }

    /**
     * Create an exception with a message.
     *
     * @param msg the message to include with the exception.
     */
    public UnuseableEntryException(String msg) {
        super(msg);
    }
}
