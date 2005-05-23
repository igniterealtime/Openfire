/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

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
