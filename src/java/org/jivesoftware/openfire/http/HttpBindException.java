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
    private boolean shouldCloseSession;
    private int httpError;

    public HttpBindException(String message, boolean shouldCloseSession, int httpError) {
        super(message);
        this.shouldCloseSession = shouldCloseSession;
        this.httpError = httpError;
    }

    public int getHttpError() {
        return httpError;
    }

    public boolean shouldCloseSession() {
        return shouldCloseSession;
    }
}
