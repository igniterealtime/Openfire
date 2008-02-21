/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.user;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Thrown when User cannot be found.
 *
 * @author Iain Shigeoka
 */
public class UserNotFoundException extends Exception {

    private Throwable nestedThrowable = null;

    public UserNotFoundException() {
        super();
    }

    public UserNotFoundException(String msg) {
        super(msg);
    }

    public UserNotFoundException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public UserNotFoundException(String msg, Throwable nestedThrowable) {
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