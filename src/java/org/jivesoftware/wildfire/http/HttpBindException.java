/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.wildfire.http;

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
