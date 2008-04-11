/**
 * $RCSfile$
 * $Revision: 1368 $
 * $Date: 2005-05-23 14:45:49 -0300 (Mon, 23 May 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

/**
 * Thrown when something failed verifying the key of a Originating Server with an Authoritative
 * Server in a dialback operation.
 *
 * @author Gaston Dombiak
 */
public class RemoteConnectionFailedException extends Exception {

    public RemoteConnectionFailedException() {
        super();
    }

    public RemoteConnectionFailedException(String msg) {
        super(msg);
    }
}
