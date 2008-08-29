/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.http;

/**
 * An exception which indicates that the maximum waiting time for a client response has been
 * surpassed and an empty response should be returned to the requesting client.
 *
 * @author Alexander Wenckus
 */
class HttpBindTimeoutException extends Exception {
    public HttpBindTimeoutException(String message) {
        super(message);
    }

    public HttpBindTimeoutException() {
        super();
    }
}
