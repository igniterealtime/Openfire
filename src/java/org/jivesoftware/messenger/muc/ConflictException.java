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
 * Exception used for representing a conflict error in a MUCRoom. There are many reasons why a 
 * conflict error could occur such as: owner tries to remove all the owners of a room. A 409 error
 * code is returned to the user that requested the invalid operation.
 *
 * @author Gaston Dombiak
 */
public class ConflictException extends Exception {

    private static final long serialVersionUID = 1L;

    private Throwable nestedThrowable = null;

    public ConflictException() {
        super();
    }

    public ConflictException(String msg) {
        super(msg);
    }

    public ConflictException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public ConflictException(String msg, Throwable nestedThrowable) {
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
