/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.lockout;

/**
 * Thrown if a username is queried for lockout status that is not locked out.
 *
 * @author Daniel Henninger
 */
public class NotLockedOutException extends Exception {

    public NotLockedOutException() {
        super();
    }

    public NotLockedOutException(String message) {
        super(message);
    }

    public NotLockedOutException(Throwable cause) {
        super(cause);
    }

    public NotLockedOutException(String message, Throwable cause) {
        super(message, cause);
    }
    
}