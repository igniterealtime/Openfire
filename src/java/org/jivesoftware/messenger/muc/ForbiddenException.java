/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.muc;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Exception used for representing that the requested operation is forbidden for the user in 
 * the MUCRoom. There are many reasons why a forbidden error could occur such as: a banned user
 * tries to join a room where he/she is an outcast. A 403 error code is returned to the user that 
 * requested the invalid operation.
 *
 * @author Gaston Dombiak
 */
public class ForbiddenException extends Exception {

    private static final long serialVersionUID = 1L;

    private Throwable nestedThrowable = null;

    public ForbiddenException() {
        super();
    }

    public ForbiddenException(String msg) {
        super(msg);
    }

    public ForbiddenException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public ForbiddenException(String msg, Throwable nestedThrowable) {
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
