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

package org.jivesoftware.util;

/**
 * <p>Flags an exception when something to be created or added already exists.</p>
 * <p>Use this class when it's not worth creating a unique
 * xAlreadyExistsException class, or where the context of
 * the call makes it obvious what type of object was not found.</p>
 *
 * @author Iain Shigeoka
 */
public class AlreadyExistsException extends Exception {

    public AlreadyExistsException() {
    }

    public AlreadyExistsException(String message) {
        super(message);
    }
}
