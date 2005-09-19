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
 * Thrown when a a user is trying to add or remove a contact from his/her roster that belongs to a
 * shared group.
 *
 * @author Gaston Dombiak
 */
public class SharedGroupException extends Exception {

    public SharedGroupException() {
        super();
    }

    public SharedGroupException(String msg) {
        super(msg);
    }
}
