/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.util;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Exception thrown during application or component initialization failure.
 */
public class InitializationException extends Exception {
    private Throwable nestedThrowable = null;

    public InitializationException() {
        super();
    }

    public InitializationException(String msg) {
        super(msg);
    }

    public InitializationException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public InitializationException(String msg, Throwable nestedThrowable) {
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
