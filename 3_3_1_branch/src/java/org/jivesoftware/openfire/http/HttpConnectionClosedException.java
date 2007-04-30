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
 * This exception is thrown when an action attempted on the connection to the client but the
 * connection has been closed.
 *
 * @author Alexander Wenckus
 */
public class HttpConnectionClosedException extends Exception {
    public HttpConnectionClosedException(String message) {
        super(message);
    }
}
