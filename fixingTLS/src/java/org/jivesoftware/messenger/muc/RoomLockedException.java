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

package org.jivesoftware.messenger.muc;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Exception used for representing that the user can't join a room since it's been locked. A 404 
 * error code is returned to the user that requested the invalid operation.
 *
 * @author Gaston Dombiak
 */
public class RoomLockedException extends Exception {

    private static final long serialVersionUID = 1L;

    private Throwable nestedThrowable = null;

    public RoomLockedException() {
        super();
    }

    public RoomLockedException(String msg) {
        super(msg);
    }

    public RoomLockedException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public RoomLockedException(String msg, Throwable nestedThrowable) {
        super(msg);
        this.nestedThrowable = nestedThrowable;
    }

    public void printStackTrace() {
        super.printStackTrace();
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace();
        }
    }

    public void printStackTrace(PrintStream ps) {
        super.printStackTrace(ps);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(ps);
        }
    }

    public void printStackTrace(PrintWriter pw) {
        super.printStackTrace(pw);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(pw);
        }
    }
}
