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

package org.jivesoftware.openfire.security;

/**
 * Thrown if a reading is not permitted with the configured SecurityAuditProvider.
 *
 * @author Daniel Henninger
 */
public class AuditWriteOnlyException extends Exception {

    public AuditWriteOnlyException() {
        super();
    }

    public AuditWriteOnlyException(String message) {
        super(message);
    }

    public AuditWriteOnlyException(Throwable cause) {
        super(cause);
    }

    public AuditWriteOnlyException(String message, Throwable cause) {
        super(message, cause);
    }

}