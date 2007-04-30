/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.http;

/**
 *
 */
public class HttpBindException extends Exception {
    private BoshBindingError error;

    public HttpBindException(String message, BoshBindingError error) {
        super(message);
        this.error = error;
    }

    public BoshBindingError getBindingError() {
        return error;
    }

    public boolean shouldCloseSession() {
        return error.getErrorType() == BoshBindingError.Type.terminal;
    }
}
