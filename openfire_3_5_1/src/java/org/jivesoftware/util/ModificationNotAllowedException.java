/**
 * $Revision:$
 * $Date:$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */

package org.jivesoftware.util;

/**
 * Exception thrown when a modification was not allowed.
 */
public class ModificationNotAllowedException extends Exception {
    public ModificationNotAllowedException() {
        super();
    }

    public ModificationNotAllowedException(String message) {
        super(message);
    }

    public ModificationNotAllowedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModificationNotAllowedException(Throwable cause) {
        super(cause);
    }
}
