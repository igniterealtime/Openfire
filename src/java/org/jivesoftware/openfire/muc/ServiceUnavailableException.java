/**
 * $RCSfile$
 * $Revision: 1276 $
 * $Date: 2005-04-21 12:41:22 -0300 (Thu, 21 Apr 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Exception used for representing that the MultiUserChat service is not available at the moment.
 * There are many reasons why a ServiceUnavailableException could occur such as: a user is trying
 * to join a room that has reached the max number of occupants.
 *
 * @author Gaston Dombiak
 */
public class ServiceUnavailableException extends Exception {

    private static final long serialVersionUID = 1L;

    private Throwable nestedThrowable = null;

    public ServiceUnavailableException() {
        super();
    }

    public ServiceUnavailableException(String msg) {
        super(msg);
    }

    public ServiceUnavailableException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public ServiceUnavailableException(String msg, Throwable nestedThrowable) {
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
