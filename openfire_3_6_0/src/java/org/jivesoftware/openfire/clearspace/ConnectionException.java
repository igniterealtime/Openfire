/**
 * $RCSfile$
 * $Revision: 691 $
 * $Date: 2004-12-13 15:06:54 -0300 (Mon, 13 Dec 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.clearspace;

/**
 * Thrown when an exception occurs connecting to CS.
 */
public class ConnectionException extends Exception {

    public enum ErrorType {AUTHENTICATION, PAGE_NOT_FOUND, UPDATE_STATE, UNKNOWN_HOST, SERVICE_NOT_AVAIBLE, OTHER};

    private ErrorType errorType;

    public ConnectionException(String s, Throwable throwable, ErrorType errorType) {
        super(s, throwable);
        this.errorType = errorType;
    }

    public ConnectionException(String s, ErrorType errorType) {
        super(s);
        this.errorType = errorType;
    }

    public ConnectionException(Throwable throwable, ErrorType errorType) {
        super(throwable);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

}
