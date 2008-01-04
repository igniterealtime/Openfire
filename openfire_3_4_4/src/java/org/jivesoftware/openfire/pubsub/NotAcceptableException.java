/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.pubsub;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Exception used for representing that the specified node configuration is not acceptable. A
 * not acceptable error could occur if owner tries to leave the node without owners. A 406 error
 * code is returned to the user that requested the invalid operation.
 *
 * @author Matt Tucker
 */
public class NotAcceptableException extends Exception {
    private static final long serialVersionUID = 1L;

    private Throwable nestedThrowable = null;

    public NotAcceptableException() {
        super();
    }

    public NotAcceptableException(String msg) {
        super(msg);
    }

    public NotAcceptableException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public NotAcceptableException(String msg, Throwable nestedThrowable) {
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
