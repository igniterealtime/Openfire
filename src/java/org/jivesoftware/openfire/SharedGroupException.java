/**
 * $RCSfile$
 * $Revision: 771 $
 * $Date: 2005-01-02 15:11:44 -0300 (Sun, 02 Jan 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

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
