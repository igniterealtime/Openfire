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

package org.jivesoftware.messenger.group;

/**
 * Thrown when attempting to create a group that already exists.
 *
 * @author Iain Shigeoka
 */
public class GroupAlreadyExistsException extends Exception {


    public GroupAlreadyExistsException() {
        super();
    }

    public GroupAlreadyExistsException(String message) {
        super(message);
    }

    public GroupAlreadyExistsException(Throwable cause) {
        super(cause);
    }

    public GroupAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}