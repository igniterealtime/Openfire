/**
 * $RCSfile$
 * $Revision: 18744 $
 * $Date: 2005-04-11 15:52:02 -0700 (Mon, 11 Apr 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup;

/**
 * Thrown if a user does not have permission to access a particular method.
 *
 * @author Gaston Dombiak
 */
public class UnauthorizedException extends Exception {

    public UnauthorizedException() {
        super();
    }

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(Throwable cause) {
        super(cause);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}